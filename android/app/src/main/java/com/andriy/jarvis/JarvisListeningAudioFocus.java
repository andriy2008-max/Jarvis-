package com.andriy.jarvis;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

/**
 * Temporarily request audio focus while the user deliberately dictates.
 * This asks media apps (TikTok, Spotify, YouTube, etc.) to pause; it never
 * changes the media volume or dispatches media-play/pause keys. Android and the
 * other app ultimately decide whether playback actually stops.
 *
 * Passive, always-armed wake recognition must NOT acquire this focus, or
 * the user's media would be suppressed while Jarvis was just standing by.
 */
public final class JarvisListeningAudioFocus {
    private static final String TAG = "JARVIS_LISTEN_FOCUS";
    private final AudioManager audio;
    private AudioFocusRequest request;
    private boolean acquired;
    private final AudioManager.OnAudioFocusChangeListener listener = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS)
            acquired = false;
    };

    public JarvisListeningAudioFocus(Context context) {
        audio=(AudioManager)context.getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
    }

    /** Returns false when Android rejects the focus request. Recording may
     * continue, but callers must not claim other audio was paused. */
    public boolean begin() {
        if(acquired) return true;
        if(audio==null) return false;
        try {
            int result;
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if(request==null) {
                    AudioAttributes attributes=new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                    request=new AudioFocusRequest.Builder(
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                        .setAudioAttributes(attributes)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(listener)
                        .build();
                }
                result=audio.requestAudioFocus(request);
            } else {
                result=audio.requestAudioFocus(listener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
            }
            acquired=result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
            if(!acquired) Log.w(TAG,"Audio focus not granted");
            return acquired;
        } catch (RuntimeException error) {
            Log.w(TAG,"Audio focus unavailable",error);
            acquired=false;
            return false;
        }
    }

    /** Release focus before running the recognized command so other apps can
     * continue according to their own playback policy. */
    public void end() {
        if(audio==null)return;
        try {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O) {
                if(request!=null)audio.abandonAudioFocusRequest(request);
            } else {
                audio.abandonAudioFocus(listener);
            }
        } catch(RuntimeException error) {
            Log.w(TAG,"Could not release audio focus",error);
        }
        acquired=false;
    }
}
