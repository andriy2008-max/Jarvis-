package com.andriy.jarvis;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.content.Intent;
import java.util.ArrayList;

/**
 * One-shot microphone activation from a deliberate in-app shake, for at most
 * three seconds of input. No audio is stored or sent to an AI unless the wake
 * phrase is heard and the following separate command explicitly asks AI.
 */
public final class JarvisShakeWakeProbe implements RecognitionListener {
    public interface Callback {
        void onPartial(String words);
        void onFinished(boolean wakeDetected, String reason);
    }
    public static final long WINDOW_MS = 3000L;
    private static final long FINAL_RESULT_GRACE_MS = 1300L;

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Callback callback;
    private boolean active;
    private boolean heardWake;
    private Runnable windowEnd;
    private Runnable lastResultTimeout;

    public JarvisShakeWakeProbe(Context context) {
        this.context = context;
    }
    public boolean active() { return active; }

    public boolean start(Callback listener) {
        if (active || listener == null) return false;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED || !SpeechRecognizer.isRecognitionAvailable(context))
            return false;
        callback = listener;
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(this);
            active = true;
            heardWake = false;
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uk-UA");
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, WINDOW_MS);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, WINDOW_MS);
            recognizer.startListening(intent);
            windowEnd = () -> {
                if (!active) return;
                try { recognizer.stopListening(); }
                catch (Exception error) { finish(false,"Сервіс розпізнавання недоступний."); return; }
                lastResultTimeout = () -> finish(false,
                    "Не почув «Джарвіс» за 3 секунди. Мікрофон вимкнено.");
                main.postDelayed(lastResultTimeout, FINAL_RESULT_GRACE_MS);
            };
            main.postDelayed(windowEnd, WINDOW_MS);
            return true;
        } catch (Exception error) {
            finish(false,"Не вдалося ввімкнути розпізнавання після струшування.");
            return false;
        }
    }

    public void close() {
        if (!active && recognizer == null) return;
        Callback cb = callback;
        callback = null;
        cleanup();
    }

    private void finish(boolean success, String message) {
        if (!active) return;
        Callback cb = callback;
        callback = null;
        cleanup();
        if (cb != null) cb.onFinished(success,message);
    }

    private void cleanup() {
        active = false;
        if (windowEnd != null) main.removeCallbacks(windowEnd);
        if (lastResultTimeout != null) main.removeCallbacks(lastResultTimeout);
        windowEnd = null;
        lastResultTimeout = null;
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    private void check(String phrase) {
        if (!active || phrase == null) return;
        if (callback != null) callback.onPartial(phrase);
        if (JarvisShakeGate.containsWakeWord(phrase))
            finish(true,"Почув «Джарвіс». Кажи команду.");
    }
    @Override public void onReadyForSpeech(Bundle params) {}
    @Override public void onBeginningOfSpeech() {}
    @Override public void onRmsChanged(float rmsdB) {}
    @Override public void onBufferReceived(byte[] buffer) {}
    @Override public void onEndOfSpeech() {}
    @Override public void onError(int error) {
        finish(false,"Не почув «Джарвіс» за короткий запис.");
    }
    @Override public void onResults(Bundle results) {
        ArrayList<String> list = results == null ? null :
            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) check(list.get(0));
        if (active) finish(false,"Не почув «Джарвіс» за короткий запис.");
    }
    @Override public void onPartialResults(Bundle results) {
        ArrayList<String> list = results == null ? null :
            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (list != null && !list.isEmpty()) check(list.get(0));
    }
    @Override public void onEvent(int eventType, Bundle params) {}
}
