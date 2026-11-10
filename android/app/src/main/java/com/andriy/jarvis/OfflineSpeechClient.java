package com.andriy.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;

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
            try {
                if (!active(id)) return;
                // Model initialization and synthesis are CPU-intensive; both run in background.
                try (OfflineOnnxSynthesizer synth = new OfflineOnnxSynthesizer(directory)) {
                    wav = synth.synthesize(text, speaker, audioCache, () -> {
                        if (!active(id)) throw new java.util.concurrent.CancellationException();
                    });
                }
                if (!active(id)) { wav.delete(); return; }
                final File ready = wav;
                main.post(() -> {
                    if (!active(id)) { ready.delete(); return; }
                    play(ready, id, callback);
                });
            } catch (Exception | LinkageError error) {
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

    public void close() {
        closed = true;
        stop();
        executor.shutdownNow();
    }
}
