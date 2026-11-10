package com.andriy.jarvis;

import android.media.MediaPlayer;
import android.os.SystemClock;
import java.io.File;

/**
 * Animation follows real WAV playback position/energy. Android system TTS
 * uses UtteranceProgressListener.onRangeStart for word timing when supported.
 */
public final class JarvisSpeechMouth {
    private static MediaPlayer player;
    private static JarvisWavEnvelope envelope;
    private static boolean systemTtsActive;
    private static long wordPulseUntilMs;

    private JarvisSpeechMouth() {}

    public static synchronized void startWav(MediaPlayer playing, File wav) {
        player = playing;
        envelope = JarvisWavEnvelope.fromFile(wav);
        systemTtsActive = false;
        wordPulseUntilMs = 0L;
    }

    public static synchronized void stopWav(MediaPlayer stopped) {
        if (player == stopped) {
            player = null;
            envelope = null;
        }
    }

    public static synchronized void startSystemTts() {
        player = null;
        envelope = null;
        systemTtsActive = true;
        wordPulseUntilMs = 0L;
    }

    public static synchronized void systemWord(int length) {
        if (systemTtsActive) {
            long ms = Math.min(470L, Math.max(105L, length * 55L));
            wordPulseUntilMs = SystemClock.uptimeMillis() + ms;
        }
    }

    public static synchronized void stopSystemTts() {
        systemTtsActive = false;
        wordPulseUntilMs = 0L;
    }

    /** 0 = shut, 1–3 = mouth height proportional to the audio envelope. */
    public static synchronized int height() {
        if (player != null && envelope != null) {
            try {
                if (player.isPlaying())
                    return envelope.mouthAt(player.getCurrentPosition());
            } catch (IllegalStateException ignored) { }
            return 0;
        }
        return systemTtsActive && SystemClock.uptimeMillis() < wordPulseUntilMs ? 3 : 0;
    }
}
