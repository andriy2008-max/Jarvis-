package com.andriy.jarvis;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.List;

/**
 * Best-effort communication audio routing for Android SpeechRecognizer.
 * The platform recognition service can still select a different microphone:
 * audio routing is a request, not a claim of a guaranteed source.
 *
 * No Bluetooth scanning, pairing, audio storage or privileged permissions.
 * Only an already connected headset with a capture endpoint is considered.
 */
public final class JarvisHeadsetAudioRouter {
    private static final String TAG = "JARVIS_HEADSET";
    private final AudioManager audio;
    private boolean acquired;
    private boolean callbackRegistered;
    private boolean selectedByUs;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final AudioDeviceCallback devices = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] changed) {
            if (acquired) chooseDevice();
        }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] changed) {
            if (acquired) chooseDevice();
        }
    };

    public JarvisHeadsetAudioRouter(Context context) {
        audio = (AudioManager) context.getApplicationContext()
            .getSystemService(Context.AUDIO_SERVICE);
    }

    /** Select once per logical microphone session; individual recognition
     * segments reuse it instead of repeatedly reconnecting Bluetooth.
     */
    public void begin() {
        if (acquired) return;
        if (audio == null) return;
        acquired = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                audio.registerAudioDeviceCallback(devices,main);
                callbackRegistered = true;
            } catch (RuntimeException error) {
                Log.w(TAG,"Device monitoring unavailable",error);
            }
        }
        chooseDevice();
    }

    /** Restore automatic system routing when microphone capture has ended. */
    public void end() {
        if (!acquired && !selectedByUs) return;
        acquired = false;
        if (callbackRegistered && audio != null) {
            try { audio.unregisterAudioDeviceCallback(devices); }
            catch (RuntimeException ignored) {}
            callbackRegistered = false;
        }
        clearSelection();
    }

    private void clearSelection() {
        if (audio != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            selectedByUs) {
            try { audio.clearCommunicationDevice(); }
            catch (RuntimeException ignored) {}
        }
        selectedByUs = false;
    }

    private void chooseDevice() {
        if (!acquired || audio == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            // Pre-Android 12 cannot reliably force the route of a third-party
            // recognition service. Leave the microphone to Android's policy.
            return;
        }
        try {
            if (selectedByUs) clearSelection();
            AudioDeviceInfo choice = null;
            List<AudioDeviceInfo> outputs = audio.getAvailableCommunicationDevices();
            if (outputs == null) return;
            AudioDeviceInfo[] inputs = audio.getDevices(AudioManager.GET_DEVICES_INPUTS);
            int best = -1;
            for (AudioDeviceInfo output : outputs) {
                if (output == null || !output.isSink()) continue;
                int type = output.getType();
                if (!hasInputFor(type,inputs)) continue;
                int priority = priority(type);
                if (priority > best) { choice = output; best = priority; }
            }
            if (choice != null) {
                selectedByUs = audio.setCommunicationDevice(choice);
                Log.i(TAG,selectedByUs
                    ? "Requested connected headset communication route"
                    : "Headset routing rejected; system microphone remains available");
            } else {
                Log.i(TAG,"No connected capture headset; using Android system microphone");
            }
        } catch (SecurityException | IllegalArgumentException error) {
            Log.w(TAG,"Android did not allow headset routing",error);
        } catch (RuntimeException error) {
            Log.w(TAG,"Headset routing unavailable on device",error);
        }
    }

    private static int priority(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return 50;
            case AudioDeviceInfo.TYPE_USB_HEADSET: return 45;
            case AudioDeviceInfo.TYPE_BLE_HEADSET: return 40;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return 35;
            default: return -1;
        }
    }

    private static boolean hasInputFor(int outputType, AudioDeviceInfo[] inputs) {
        if (priority(outputType) < 0 || inputs == null) return false;
        for (AudioDeviceInfo input : inputs) {
            if (input != null && input.isSource() && input.getType() == outputType)
                return true;
        }
        return false;
    }
}
