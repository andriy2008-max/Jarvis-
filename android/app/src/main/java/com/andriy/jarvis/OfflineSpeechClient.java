package com.andriy.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Owns ONNX inference off the UI thread and WAV playback for two local voices. */
public final class OfflineSpeechClient {
    private final OfflineVoicePackManager packages;
    private final File audioCache;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private MediaPlayer player;
    private File playingFile;
    // Access these only on the single-thread inference executor.
    private OfflineOnnxSynthesizer cachedSynthesizer;
    private long cachedManifestModified;
    private long cachedManifestBytes;
    private volatile boolean closed;

    public OfflineSpeechClient(Context context, OfflineVoicePackManager packages) {
        this.packages = packages;
        this.audioCache = context.getApplicationContext().getCacheDir();
    }

    public boolean isInstalled() { return packages != null && packages.hasInstalledFiles(); }

    public boolean speak(String text, String voice, NeuralSpeechClient.Listener callback) {
        if (closed || !isInstalled()) return false;
        final int id = generation.incrementAndGet();
        main.post(this::release);
        final File directory = packages.installedDirectory();
        final String speaker = VoiceRanking.profile(voice);
        executor.execute(() -> {
            File wav = null;
            final long startedAt = SystemClock.elapsedRealtime();
            try {
                if (!active(id)) return;
                // Keep ONNX sessions warm across replies to avoid loading ~145 MB
                // from disk for every short command. Replace after model upgrade.
                File manifest = new File(directory, "manifest.json");
                if (!manifest.isFile())
                    throw new Exception("Офлайн-модель більше не встановлена.");
                if (cachedSynthesizer == null ||
                    cachedManifestModified != manifest.lastModified() ||
                    cachedManifestBytes != manifest.length()) {
                    discardCachedSynthesizer();
                    cachedSynthesizer = new OfflineOnnxSynthesizer(directory);
                    cachedManifestModified = manifest.lastModified();
                    cachedManifestBytes = manifest.length();
                }
                wav = cachedSynthesizer.synthesize(text, speaker, audioCache, () -> {
                    if (!active(id)) throw new java.util.concurrent.CancellationException();
                    if (SystemClock.elapsedRealtime() - startedAt > 65000L)
                        throw new Exception("Офлайн-озвучення надто повільне на цьому пристрої.");
                });
                if (!active(id)) { wav.delete(); return; }
                final File ready = wav;
                main.post(() -> {
                    if (!active(id)) { ready.delete(); return; }
                    play(ready, id, callback);
                });
            } catch (Exception | LinkageError error) {
                // If ONNX actually failed, reopen model sessions on next request.
                // Cancellation leaves the healthy cache intact.
                if (!(error instanceof java.util.concurrent.CancellationException))
                    discardCachedSynthesizer();
                if (wav != null) wav.delete();
                final String reason = error.getMessage() == null ?
                    "Цю ONNX-модель не вдалося запустити на телефоні." : error.getMessage();
                main.post(() -> {
                    if (active(id)) callback.onFailure(reason);
                });
            }
        });
        return true;
    }

    private void play(File file, int id, NeuralSpeechClient.Listener callback) {
        release();
        try {
            playingFile = file;
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(mp -> {
                if (!active(id)) return;
                try {
                    mp.start();
                    callback.onStart();
                } catch (Exception e) {
                    release();
                    if (active(id)) callback.onFailure("Не вдалося відтворити офлайн-WAV.");
                }
            });
            player.setOnCompletionListener(mp -> {
                release();
                if (active(id)) callback.onDone();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                release();
                if (active(id)) callback.onFailure("Помилка відтворення офлайн-WAV.");
                return true;
            });
            player.prepareAsync();
        } catch (Exception error) {
            release();
            if (active(id)) callback.onFailure("Не вдалося відкрити офлайн-WAV.");
        }
    }

    private boolean active(int id) { return !closed && generation.get() == id; }

    public void stop() {
        generation.incrementAndGet();
        main.post(this::release);
    }

    private void release() {
        if (player != null) {
            try { player.reset(); player.release(); } catch (Exception ignored) {}
            player = null;
        }
        if (playingFile != null) {
            playingFile.delete();
            playingFile = null;
        }
    }

    private void discardCachedSynthesizer() {
        if (cachedSynthesizer != null) {
            try { cachedSynthesizer.close(); } catch (Exception ignored) {}
            cachedSynthesizer = null;
        }
        cachedManifestModified = 0;
        cachedManifestBytes = 0;
    }

    public void close() {
        if (closed) return;
        closed = true;
        stop();
        // Close sessions on the same executor that owns them: no concurrent
        // ORT session shutdown while the native runtime is synthesizing.
        executor.execute(this::discardCachedSynthesizer);
        executor.shutdown();
    }
}
