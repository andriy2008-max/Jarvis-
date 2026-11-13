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
 * Requests an audio device for Android SpeechRecognizer. A recognizer service
 * runs outside Jarvis and may disregard a communication-device request:
 * routing is best-effort, not proof of which microphone recorded speech.
 * Requests are released as soon as the logical listening session ends.
 */
public final class JarvisHeadsetAudioRouter {
    private static final String TAG="JARVIS_MIC";
    public static final String PREFS="jarvis_microphone_source";
    public static final String KEY_SOURCE="source";

    private final AudioManager audio;
    private final Context context;
    private boolean acquired;
    private boolean selectedByUs;
    private boolean callbackRegistered;
    private String problem="";
    private JarvisMicrophoneChoice.Mode mode=JarvisMicrophoneChoice.Mode.AUTO;
    private Runnable onRouteLost;
    private final Handler main=new Handler(Looper.getMainLooper());

    private final AudioDeviceCallback devices=new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] changed) {
            recheck();
        }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] changed) {
            recheck();
        }
    };

    public JarvisHeadsetAudioRouter(Context context) {
        this.context=context.getApplicationContext();
        audio=(AudioManager)this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    public static JarvisMicrophoneChoice.Mode selected(Context context) {
        String raw=context.getApplicationContext()
            .getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(KEY_SOURCE,"auto");
        return JarvisMicrophoneChoice.fromStored(raw);
    }

    public static void save(Context context,JarvisMicrophoneChoice.Mode chosen) {
        context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .edit().putString(KEY_SOURCE,JarvisMicrophoneChoice.stored(chosen)).apply();
    }

    public void setOnRouteLost(Runnable callback) { onRouteLost=callback; }
    public String problem() { return problem; }

    /** Never start recognition from the phone when a required headset could
     * not be requested. The recognition service may still override the route. */
    public boolean begin() {
        if (acquired) return mode==JarvisMicrophoneChoice.Mode.AUTO || selectedByUs;
        mode=selected(context);
        problem="";
        if (audio==null) {
            problem="Не вдалося відкрити аудіосистему Android.";
            return mode==JarvisMicrophoneChoice.Mode.AUTO;
        }
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.S) {
            if (mode==JarvisMicrophoneChoice.Mode.AUTO) return true;
            problem="Вибір мікрофона потребує Android 12 або новішого.";
            return false;
        }
        acquired=true;
        try {
            audio.registerAudioDeviceCallback(devices,main);
            callbackRegistered=true;
        } catch(RuntimeException error) {
            Log.w(TAG,"Audio device change callback unavailable",error);
        }
        if (!chooseDevice()) {
            end();
            return false;
        }
        return true;
    }

    /** Restore default routing when the microphone is no longer used. */
    public void end() {
        if (!acquired && !selectedByUs && !callbackRegistered) return;
        acquired=false;
        if (audio!=null && callbackRegistered) {
            try { audio.unregisterAudioDeviceCallback(devices); }
            catch(RuntimeException ignored) {}
        }
        callbackRegistered=false;
        clearSelection();
    }

    private void recheck() {
        if (!acquired) return;
        if (!chooseDevice() && onRouteLost!=null) onRouteLost.run();
    }

    private void clearSelection() {
        if(audio!=null && Build.VERSION.SDK_INT>=Build.VERSION_CODES.S && selectedByUs) {
            try { audio.clearCommunicationDevice(); }
            catch(RuntimeException ignored) {}
        }
        selectedByUs=false;
    }

    private boolean chooseDevice() {
        if (!acquired || audio==null) return false;
        if (Build.VERSION.SDK_INT<Build.VERSION_CODES.S)
            return mode==JarvisMicrophoneChoice.Mode.AUTO;
        try {
            clearSelection();
            List<AudioDeviceInfo> outputs=audio.getAvailableCommunicationDevices();
            AudioDeviceInfo[] inputs=audio.getDevices(AudioManager.GET_DEVICES_INPUTS);
            AudioDeviceInfo choice=null;
            int best=-1;
            if (outputs!=null) for(AudioDeviceInfo output:outputs) {
                if (output==null || !output.isSink()) continue;
                int type=output.getType(), score=-1;
                if(mode==JarvisMicrophoneChoice.Mode.PHONE) {
                    if(hasBuiltinMic(inputs)) {
                        if(type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) score=50;
                        else if(type==AudioDeviceInfo.TYPE_BUILTIN_EARPIECE) score=40;
                    }
                } else {
                    score=headsetPriority(type);
                    if(!hasInputFor(type,inputs))score=-1;
                }
                if(score>best) {best=score;choice=output;}
            }
            if(mode==JarvisMicrophoneChoice.Mode.AUTO && choice==null) {
                // No headset: leave Android's default input selection intact.
                problem="";
                return true;
            }
            if(choice==null) {
                problem=mode==JarvisMicrophoneChoice.Mode.HEADSET
                    ? "Гарнітура з мікрофоном не підключена або недоступна Android."
                    : "Не вдалося вибрати вбудований мікрофон телефона.";
                return false;
            }
            selectedByUs=audio.setCommunicationDevice(choice);
            if(!selectedByUs) {
                problem="Android відхилив вибір мікрофона. Перевір підключення та повтори.";
                return mode==JarvisMicrophoneChoice.Mode.AUTO;
            }
            problem="";
            Log.i(TAG,"Requested microphone source: "+mode);
            return true;
        } catch(SecurityException | IllegalArgumentException error) {
            problem="Android не дозволив змінити джерело мікрофона.";
            Log.w(TAG,problem,error);
        } catch(RuntimeException error) {
            problem="Не вдалося налаштувати мікрофон Android.";
            Log.w(TAG,problem,error);
        }
        return mode==JarvisMicrophoneChoice.Mode.AUTO;
    }

    private static boolean hasBuiltinMic(AudioDeviceInfo[] inputs) {
        if(inputs==null)return false;
        for(AudioDeviceInfo input:inputs) if(input!=null && input.isSource() &&
            input.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC)return true;
        return false;
    }

    private static int headsetPriority(int type) {
        switch(type) {
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return 50;
            case AudioDeviceInfo.TYPE_USB_HEADSET: return 45;
            case AudioDeviceInfo.TYPE_BLE_HEADSET: return 40;
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return 35;
            default: return -1;
        }
    }

    private static boolean hasInputFor(int outputType,AudioDeviceInfo[] inputs) {
        if(headsetPriority(outputType)<0 || inputs==null)return false;
        for(AudioDeviceInfo input:inputs) if(input!=null && input.isSource() &&
            input.getType()==outputType)return true;
        return false;
    }
}
