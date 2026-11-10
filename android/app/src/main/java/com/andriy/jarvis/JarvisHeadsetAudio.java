package com.andriy.jarvis;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

/**
 * Best-effort routing for user-initiated voice capture. Android SpeechRecognizer
 * is a separate recognition service and is not guaranteed to honor the route.
 * This app only requests the platform communication route and never claims to
 * have forcibly chosen the recognition service's microphone.
 *
 * On API 31+ the matching input is selected by Android when an output
 * communication device is chosen. On older Android use system/default routing
 * instead of deprecated, asynchronous SCO switching.
 */
public final class JarvisHeadsetAudio {
    private static final String TAG = "JarvisHeadsetAudio";
    private static final Set<Object> clients =
        Collections.newSetFromMap(new IdentityHashMap<>());
    private static AudioManager manager;
    private static AudioDeviceCallback deviceCallback;
    private static Handler handler;

    private JarvisHeadsetAudio() {}

    public static synchronized void acquire(Context context, Object owner) {
        if (owner == null || context == null || clients.contains(owner)) return;
        clients.add(owner);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        if (manager == null)
            manager = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return;
        if (handler == null) handler = new Handler(Looper.getMainLooper());
        if (deviceCallback == null) {
            deviceCallback = new AudioDeviceCallback() {
                @Override public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { refresh(); }
                @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { refresh(); }
            };
            manager.registerAudioDeviceCallback(deviceCallback, handler);
        }
        chooseRoute();
    }

    public static synchronized void release(Object owner) {
        if (owner == null || !clients.remove(owner) || !clients.isEmpty()) return;
        if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { manager.clearCommunicationDevice(); }
            catch (Exception failure) { Log.w(TAG,"Could not clear communication route",failure); }
            if (deviceCallback != null) {
                try { manager.unregisterAudioDeviceCallback(deviceCallback); }
                catch (Exception ignored) {}
            }
        }
        deviceCallback = null;
        manager = null;
        handler = null;
    }

    private static synchronized void refresh() {
        if (clients.isEmpty()) return;
        chooseRoute();
    }

    private static void chooseRoute() {
        if (manager == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            AudioDeviceInfo chosen = null;
            int best = 0;
            for (AudioDeviceInfo device : manager.getAvailableCommunicationDevices()) {
                int rank = rank(device.getType());
                if (rank > best) { chosen = device; best = rank; }
            }
            if (chosen == null) {
                manager.clearCommunicationDevice(); // internal phone mic/default
            } else if (!manager.setCommunicationDevice(chosen)) {
                Log.w(TAG,"Requested headset communication route refused by Android");
            }
        } catch (SecurityException | IllegalArgumentException failure) {
            Log.w(TAG,"Headset routing not permitted, use Android default microphone",failure);
        } catch (RuntimeException failure) {
            Log.w(TAG,"Headset routing unavailable; use Android default",failure);
        }
    }

    /** Bluetooth voice-ready devices outrank wired input-capable headsets. */
    static int rank(int type) {
        if (type == AudioDeviceInfo.TYPE_BLE_HEADSET) return 50;
        if (type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return 45;
        if (type == AudioDeviceInfo.TYPE_WIRED_HEADSET) return 40;
        if (type == AudioDeviceInfo.TYPE_USB_HEADSET) return 35;
        if (type == AudioDeviceInfo.TYPE_USB_DEVICE) return 30;
        return 0; // Never force A2DP-only, speakers or a built-in earpiece.
    }
}
