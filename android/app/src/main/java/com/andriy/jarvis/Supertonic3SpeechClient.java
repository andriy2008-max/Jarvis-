package com.andriy.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import ai.onnxruntime.OrtEnvironment;

/** Local ONNX Supertonic 3: Ukrainian + English, same selected voice, no API calls. */
public final class Supertonic3SpeechClient {
    private final Supertonic3PackManager pack;
    private final File audioCache;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicInteger generation = new AtomicInteger();
    private SupertonicModel model;
    private SupertonicStyle male, female;
    private OrtEnvironment env;
    private MediaPlayer player;
    private File playingFile;
    private volatile boolean closed;

    public Supertonic3SpeechClient(Context context, Supertonic3PackManager pack) {
        this.pack = pack;
        audioCache = context.getApplicationContext().getCacheDir();
    }

    public boolean isInstalled() { return pack != null && pack.hasInstalledFiles(); }

    public boolean speak(String text, String voice, NeuralSpeechClient.Listener callback) {
        if (closed || !isInstalled()) return false;
        final int id = generation.incrementAndGet();
        main.post(this::releasePlayer);
        executor.execute(() -> {
            File wav = null;
            final long began = SystemClock.elapsedRealtime();
            try {
                if (!active(id)) return;
                if (text == null || text.trim().isEmpty() || text.length() > 1200)
                    throw new Exception("Завеликий текст для Supertonic 3.");
                if (!isInstalled()) throw new Exception("Модель Supertonic 3 не встановлена.");
                if (env == null) env = OrtEnvironment.getEnvironment();
                File dir = pack.directory();
                if (model == null) model = SupertonicHelper.loadTextToSpeech(
                    new File(dir, "onnx").getAbsolutePath(), false, env);
                boolean isFemale = "F1".equalsIgnoreCase(voice);
                if (isFemale && female == null)
                    female = SupertonicHelper.loadVoiceStyle(Collections.singletonList(
                        new File(dir, "voice_styles/F1.json").getAbsolutePath()), false, env);
                if (!isFemale && male == null)
                    male = SupertonicHelper.loadVoiceStyle(Collections.singletonList(
                        new File(dir, "voice_styles/M1.json").getAbsolutePath()), false, env);
                SupertonicStyle style = isFemale ? female : male;
                // The upstream "na" language tag handles code-switched uk/en text.
                SupertonicResult result = model.call(text, "na", style, 8, 1.05f, 0.18f, env);
                if (SystemClock.elapsedRealtime() - began > 65000L)
                    throw new Exception("Занадто повільне озвучення Supertonic на телефоні.");
                if (!active(id)) return;
                wav = File.createTempFile("jarvis-supertonic-", ".wav", audioCache);
                SupertonicHelper.writeWavFile(wav.getAbsolutePath(), result.wav, model.sampleRate);
                if (!active(id)) { wav.delete(); return; }
                final File ready = wav;
                main.post(() -> {
                    if (!active(id)) { ready.delete(); return; }
                    play(ready, id, callback);
                });
            } catch (Exception | LinkageError | OutOfMemoryError e) {
                discardModel();
                if (wav != null) wav.delete();
                String reason = e.getMessage() == null ? "ONNX-рушій недоступний." : e.getMessage();
                main.post(() -> {
                    if (active(id)) callback.onFailure(reason);
                });
            }
        });
        return true;
    }

    private boolean active(int id) { return !closed && generation.get() == id; }

    private void play(File file, int id, NeuralSpeechClient.Listener callback) {
        releasePlayer();
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
                    JarvisSpeechMouth.startWav(mp, file);
                    mp.start();
                    callback.onStart();
                } catch (Exception e) {
                    releasePlayer();
                    if (active(id)) callback.onFailure("Помилка відтворення Supertonic.");
                }
            });
            player.setOnCompletionListener(mp -> {
                releasePlayer();
                if (active(id)) callback.onDone();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                releasePlayer();
                if (active(id)) callback.onFailure("Помилка аудіоплеєра.");
                return true;
            });
            player.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            if (active(id)) callback.onFailure("Не вдалося відкрити WAV Supertonic.");
        }
    }

    public void stop() {
        generation.incrementAndGet();
        main.post(this::releasePlayer);
    }

    private void releasePlayer() {
        if (player != null) {
            JarvisSpeechMouth.stopWav(player);
            try { player.reset(); player.release(); } catch (Exception ignored) { }
            player = null;
        }
        if (playingFile != null) {
            playingFile.delete();
            playingFile = null;
        }
    }

    private void discardModel() {
        if (male != null) { try { male.close(); } catch (Exception ignored) { } male = null; }
        if (female != null) { try { female.close(); } catch (Exception ignored) { } female = null; }
        if (model != null) { try { model.close(); } catch (Exception ignored) { } model = null; }
    }

    public void close() {
        if (closed) return;
        closed = true;
        stop();
        executor.execute(this::discardModel);
        executor.shutdown();
    }
}
