package com.andriy.jarvis;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Floating 8-bit Jarvis face from a Quick Settings tile.
 * Explicit tap-to-listen ONLY, no background mic capture or app launch.
 * This is its own speech session, not a proxy for the MainActivity UI.
 */
public final class FloatingJarvisService extends Service {
    public static final String ACTION_SHOW = "com.andriy.jarvis.FLOAT_SHOW";
    public static final String ACTION_CLOSE = "com.andriy.jarvis.FLOAT_CLOSE";
    private static final String CHANNEL = "jarvis_floating_control";
    private static final int NOTIFICATION = 81740;
    private static volatile boolean showing;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private WindowManager windows;
    private JarvisOrbView face;
    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean recording;
    private boolean aiBusy;
    private boolean screenModeStartedHere;
    private boolean ttsReady;
    private boolean closing;
    private TextToSpeech tts;
    private OfflineVoicePackManager packs;
    private OfflineSpeechClient offline;
    private NeuralSpeechClient neural;
    private AiChatClient ai;

    public static boolean isShowing() { return showing; }

    @Override public void onCreate() {
        super.onCreate();
        packs = new OfflineVoicePackManager(this);
        offline = new OfflineSpeechClient(this, packs);
        neural = new NeuralSpeechClient(this);
        ai = new AiChatClient(this);
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS && tts != null) {
                tts.setLanguage(Locale.forLanguageTag("uk-UA"));
                tts.setPitch(.92f);
                tts.setSpeechRate(.94f);
                ttsReady = true;
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_CLOSE.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!ACTION_SHOW.equals(intent.getAction()) || closing) return START_NOT_STICKY;
        if (!Settings.canDrawOverlays(this) ||
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Надай дозволи плаваючого вікна та мікрофона Джарвісу.",
                Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null && Build.VERSION.SDK_INT >= 26)
                manager.createNotificationChannel(new NotificationChannel(
                    CHANNEL, "Плаваючий Джарвіс", NotificationManager.IMPORTANCE_LOW));
            Intent close = new Intent(this, FloatingJarvisService.class);
            close.setAction(ACTION_CLOSE);
            PendingIntent closeAction = PendingIntent.getService(this, 8160, close,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification notification = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("Плаваючий Джарвіс")
                .setContentText("Натисни обличчя для команди. Утримуй його, щоб закрити.")
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Закрити", closeAction).build())
                .build();
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFICATION, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIFICATION, notification);
            }
            showFace();
        } catch (Exception error) {
            Toast.makeText(this, "Не вдалося відкрити плаваючий Джарвіс. Перевір дозволи Android.",
                Toast.LENGTH_LONG).show();
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void showFace() {
        if (face != null) return;
        windows = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        if (windows == null) throw new IllegalStateException("No WindowManager");
        face = new JarvisOrbView(this);
        face.setOnClickListener(v -> toggleListening());
        face.setOnLongClickListener(v -> {
            Toast.makeText(this, "Плаваючий Джарвіс закрито.", Toast.LENGTH_SHORT).show();
            stopSelf();
            return true;
        });
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(
            dp(112), dp(112), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        p.x = dp(8);
        p.y = 0;
        windows.addView(face, p);
        showing = true;
        try {
            android.service.quicksettings.TileService.requestListeningState(this,
                new android.content.ComponentName(this, JarvisQuickTileService.class));
        } catch (Exception ignored) { }
    }

    private void setFace(String state) {
        if (face != null) face.setState(state);
    }

    private void message(String text) {
        ui.post(() -> Toast.makeText(this, text, Toast.LENGTH_LONG).show());
    }

    private void toggleListening() {
        if (closing) return;
        if (aiBusy) {
            message("Джарвіс ще опрацьовує попередню команду.");
            return;
        }
        if (recording) {
            if (recognizer != null && listening) {
                listening = false;
                setFace("thinking");
                recognizer.stopListening();
            }
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED) {
            message("Дозволь Джарвісу користуватися мікрофоном.");
            return;
        }
        try {
            if (recognizer == null) {
                if (SpeechRecognizer.isRecognitionAvailable(this))
                    recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                else if (Build.VERSION.SDK_INT >= 31 &&
                         SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
                    recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                else {
                    message("На телефоні немає розпізнавача голосу.");
                    return;
                }
                recognizer.setRecognitionListener(new RecognitionListener() {
                    @Override public void onReadyForSpeech(Bundle params) {
                        setFace("listening");
                    }
                    @Override public void onBeginningOfSpeech() {}
                    @Override public void onRmsChanged(float rms) {}
                    @Override public void onBufferReceived(byte[] buffer) {}
                    @Override public void onEndOfSpeech() {
                        listening = false;
                        if (recording) setFace("thinking");
                    }
                    @Override public void onError(int error) {
                        recording = false;
                        listening = false;
                        setFace("idle");
                        if (!closing) message("Не почув команди. Натисни обличчя ще раз.");
                    }
                    @Override public void onResults(Bundle results) {
                        recording = false;
                        listening = false;
                        setFace("thinking");
                        java.util.ArrayList<String> list =
                            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (list == null || list.isEmpty() || list.get(0).trim().isEmpty()) {
                            setFace("idle");
                            message("Не почув команди. Спробуй ще раз.");
                            return;
                        }
                        executeCommand(list.get(0));
                    }
                    @Override public void onPartialResults(Bundle results) {}
                    @Override public void onEvent(int type, Bundle params) {}
                });
            }
            stopSpeech();
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            // Recognition engines may ignore these hints; don't claim
            // that silence timing is guaranteed by Android.
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L);
            recording = true;
            listening = true;
            setFace("listening");
            recognizer.startListening(i);
        } catch (Exception exception) {
            recording = false;
            listening = false;
            setFace("idle");
            message("Не вдалося ввімкнути мікрофон. Повтори натискання.");
        }
    }

    private void executeCommand(String raw) {
        if (closing) return;
        String normalized = CommandPatterns.normalize(raw);
        String speech = CommandPatterns.spokenText(raw);
        if (speech != null) {
            reply(speech.isEmpty() ? "Назви слово або речення після команди «Скажи»." : speech);
            return;
        }

        ScreenControlCommands.Action action = ScreenControlCommands.parse(raw);
        if (action != null) {
            if (!getResources().getBoolean(R.bool.screen_control_available)) {
                reply("У Джарвіс Lite керування чужими екранами вимкнено.");
                return;
            }
            switch (action.kind) {
                case ENABLE:
                    screenModeStartedHere = true;
                    reply(ScreenControlAccessibilityService.startFromBubble());
                    return;
                case DISABLE:
                    ScreenControlAccessibilityService.stop();
                    screenModeStartedHere = false;
                    reply("Керування екраном вимкнено.");
                    return;
                case SETTINGS:
                    reply("Дозвіл керування екраном вмикається вручну в основному Джарвісі.");
                    return;
                case STATUS:
                    reply(ScreenControlAccessibilityService.isSessionActive()
                        ? "Керування екраном активне." : "Увімкни керування екраном голосом.");
                    return;
                default:
                    reply(ScreenControlAccessibilityService.perform(action));
                    return;
            }
        }

        if (normalized.equals("зупинись") || normalized.equals("закрий джарвіса") ||
            normalized.equals("закрий плаваючого джарвіса")) {
            stopSelf();
            return;
        }
        if (normalized.equals("скасуй таймер") || normalized.equals("зупини таймер")) {
            reply(TimerScheduler.cancel(this) ? "Таймер скасовано." : "Активного таймера немає.");
            return;
        }
        if (normalized.contains("скільки залишилося таймеру") ||
            normalized.contains("скільки до кінця таймера")) {
            long left = TimerScheduler.remainingSeconds(this);
            reply(left < 0 ? "Таймер не запущено." : "Залишилося " + left + " секунд.");
            return;
        }
        Integer timer = CommandPatterns.timerSeconds(normalized);
        if (timer != null) {
            if (timer <= 0) reply("Назви тривалість таймера до 24 годин.");
            else reply(TimerScheduler.start(this, timer)
                ? "Таймер встановлено на " + timer + " секунд."
                : "Дозволь точні будильники Джарвісу в налаштуваннях Android.");
            return;
        }
        if (normalized.contains("котра година") || normalized.contains("яка година")) {
            reply("Зараз " + DateFormat.getTimeInstance(DateFormat.SHORT,
                Locale.forLanguageTag("uk-UA")).format(new Date()) + ".");
            return;
        }
        if (normalized.contains("яка сьогодні дата") || normalized.contains("який сьогодні день")) {
            reply("Сьогодні " + DateFormat.getDateInstance(DateFormat.FULL,
                Locale.forLanguageTag("uk-UA")).format(new Date()) + ".");
            return;
        }
        if (normalized.equals("голос дмитра") || normalized.equals("голос дмитро")) {
            getSharedPreferences("MainActivity", MODE_PRIVATE).edit()
                .putString("jarvisVoiceProfile", VoiceRanking.DMYTRO).apply();
            reply("Обрано голос Дмитра.");
            return;
        }
        if (normalized.equals("голос лади") || normalized.equals("голос лада")) {
            getSharedPreferences("MainActivity", MODE_PRIVATE).edit()
                .putString("jarvisVoiceProfile", VoiceRanking.LADA).apply();
            reply("Обрано голос Лади.");
            return;
        }
        DeviceCommands.Action device = DeviceCommands.parse(normalized);
        if (device != null) {
            reply(performQuickDeviceAction(device));
            return;
        }
        if (ai != null && ai.isConfigured()) {
            aiBusy = true;
            ai.send(raw, (response, error) -> {
                aiBusy = false;
                if (closing) return;
                reply(error != null ? error : response);
            });
        } else {
            reply("Цю команду я поки не виконую в плаваючому вікні. " +
                "Можеш ставити таймер, керувати музикою, говорити «Скажи», " +
                "або підключити ШІ у налаштуваннях основного Джарвіса.");
        }
    }

    private String performQuickDeviceAction(DeviceCommands.Action action) {
        AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return "Аудіокерування недоступне.";
        int key = -1;
        switch (action.kind) {
            case MUSIC_PLAY: key = KeyEvent.KEYCODE_MEDIA_PLAY; break;
            case MUSIC_PAUSE: key = KeyEvent.KEYCODE_MEDIA_PAUSE; break;
            case MUSIC_NEXT: key = KeyEvent.KEYCODE_MEDIA_NEXT; break;
            case MUSIC_PREVIOUS: key = KeyEvent.KEYCODE_MEDIA_PREVIOUS; break;
            case VOLUME_PERCENT:
                int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                int amount = Math.round(max * action.percent / 100f);
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    Math.max(0, Math.min(max, amount)), AudioManager.FLAG_SHOW_UI);
                return "Медіагучність: " + action.percent + " відсотків.";
            case VOLUME_MUTE:
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0,
                    AudioManager.FLAG_SHOW_UI);
                return "Музику вимкнено.";
            case VOLUME_UNMUTE:
                audio.setStreamVolume(AudioManager.STREAM_MUSIC,
                    Math.max(1, audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) / 2),
                    AudioManager.FLAG_SHOW_UI);
                return "Медіазвук увімкнено.";
            default:
                return "Для цієї команди відкрий основний Джарвіс.";
        }
        try {
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
            audio.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
            return "Команду медіапрогравачу надіслано.";
        } catch (SecurityException denied) {
            return "Android не дозволив керувати відтворенням.";
        }
    }

    private String selectedVoice() {
        return VoiceRanking.profile(getSharedPreferences("MainActivity", MODE_PRIVATE)
            .getString("jarvisVoiceProfile", VoiceRanking.DMYTRO));
    }

    private void reply(String text) {
        if (closing || text == null || text.isEmpty()) { setFace("idle"); return; }
        // Keep a visible response even when TTS is switched off.
        message(text);
        boolean enabled = getSharedPreferences("MainActivity", MODE_PRIVATE)
            .getBoolean("voiceEnabled", true);
        if (!enabled) { setFace("idle"); return; }
        stopSpeech();
        setFace("speaking");
        if (offline != null && offline.isInstalled()) {
            if (offline.speak(text, selectedVoice(), new NeuralSpeechClient.Listener() {
                @Override public void onStart() { setFace("speaking"); }
                @Override public void onDone() { setFace("idle"); }
                @Override public void onFailure(String reason) { remoteOrSystem(text); }
            })) return;
        }
        remoteOrSystem(text);
    }

    private void remoteOrSystem(String text) {
        if (neural != null && neural.isConfigured()) {
            if (neural.speak(text, selectedVoice(), new NeuralSpeechClient.Listener() {
                @Override public void onStart() { setFace("speaking"); }
                @Override public void onDone() { setFace("idle"); }
                @Override public void onFailure(String reason) { speakSystem(text); }
            })) return;
        }
        speakSystem(text);
    }

    private void speakSystem(String text) {
        if (ttsReady && tts != null) {
            setFace("speaking");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                "jarvis-orb-" + SystemClock.uptimeMillis());
            ui.postDelayed(() -> setFace("idle"), Math.min(10000, 800 + text.length() * 65L));
        } else {
            setFace("idle");
        }
    }

    private void stopSpeech() {
        if (offline != null) offline.stop();
        if (neural != null) neural.stop();
        if (tts != null) tts.stop();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onDestroy() {
        closing = true;
        showing = false;
        recording = false;
        listening = false;
        if (screenModeStartedHere) ScreenControlAccessibilityService.stop();
        if (recognizer != null) {
            try { recognizer.cancel(); recognizer.destroy(); }
            catch (Exception ignored) { }
            recognizer = null;
        }
        if (face != null && windows != null) {
            try { windows.removeView(face); } catch (Exception ignored) { }
            face = null;
        }
        if (ai != null) ai.close();
        if (offline != null) offline.close();
        if (neural != null) neural.close();
        if (packs != null) packs.close();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        try {
            android.service.quicksettings.TileService.requestListeningState(this,
                new android.content.ComponentName(this, JarvisQuickTileService.class));
        } catch (Exception ignored) { }
        super.onDestroy();
    }
}
