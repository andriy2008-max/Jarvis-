package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Phone-side, foreground-consent-gated remote screen control.
 * Data Layer already enforces matching package and signing certificates.
 * Each fresh session is additionally bound to the actual paired watch node.
 */
public final class JarvisWearPhoneSession {
    public interface Result { void reply(boolean ok, String description, String token, boolean ended); }

    private static final Handler main = new Handler(Looper.getMainLooper());
    private static String node = "";
    private static String session = "";
    private static long deadline;
    private static Runnable expiry;
    private static JarvisSequenceExecutor sequences;
    private static SpotifyPlaybackController spotify;
    private static DeviceActions devices;
    // One user-initiated remote Spotify task at a time, tied to its watch node.
    private static String automaticSpotifyNode = "";
    private static boolean automaticSpotifyBusy;

    static {
        JarvisPhoneMode.subscribe(() -> {
            if (!JarvisPhoneMode.isActive()) {
                if (expiry != null) main.removeCallbacks(expiry);
                expiry = null;
                node = "";
                session = "";
                deadline = 0L;
            }
        });
    }

    private JarvisWearPhoneSession() {}

    public static void receive(Context context, String sourceNode, JSONObject request, Result result) {
        main.post(() -> receiveOnMain(context.getApplicationContext(),sourceNode,request,result));
    }

    private static void receiveOnMain(Context context,String sourceNode,JSONObject request,Result originalOut) {
        String action = request.optString("action","");
        final long feedback = JarvisWearProtocol.COMMAND.equals(action) ||
            JarvisWearProtocol.SPOTIFY.equals(action) ? JarvisControlFeedback.begin() : 0L;
        Result out = (ok, detail, token, ended) -> {
            JarvisControlFeedback.finish(feedback,ok,SystemClock.uptimeMillis());
            originalOut.reply(ok,detail,token,ended);
        };
        String suppliedToken = request.optString("session","");
        String command = request.optString("text","");
        if (!JarvisWearProtocol.validAction(action) || sourceNode == null ||
            sourceNode.trim().isEmpty()) {
            out.reply(false,"Неприпустима команда Wear OS.","",true); return;
        }
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            out.reply(false,"Ця команда призначена для телефона.","",true); return;
        }
        if (JarvisWearProtocol.SYNC.equals(action)) {
            // Join a manually enabled phone mode, never activate it by polling.
            if (!JarvisPhoneMode.isActive() ||
                !ScreenControlAccessibilityService.isSessionActive() ||
                JarvisPhoneModeManager.automatic()) {
                out.reply(true,"Режим телефона не активний.","",true);
                return;
            }
            if (!session.isEmpty() && !node.equals(sourceNode) &&
                SystemClock.elapsedRealtime() < deadline) {
                out.reply(false,"Інший годинник керує телефоном.","",true);
                return;
            }
            if (session.isEmpty() || !node.equals(sourceNode)) {
                node = sourceNode;
                session = UUID.randomUUID().toString();
            }
            extend();
            out.reply(true,"Режим телефона активний.",session,false);
            return;
        }
        // A watch voice command can temporarily use the phone's already
        // user-approved Accessibility service without entering manual Phone mode.
        if (JarvisWearProtocol.SPOTIFY.equals(action)) {
            if (!JarvisWearProtocol.validCommand(command)) {
                out.reply(false,"Невірна голосова команда Spotify.","",false);
                return;
            }
            SpotifyMusicCommands.Kind music = SpotifyMusicCommands.parse(command);
            if (music == null) {
                out.reply(false,"Команда не належить до Spotify.","",false);
                return;
            }
            if (automaticSpotifyBusy) {
                out.reply(false,"Spotify вже виконує команду з годинника.","",false);
                return;
            }
            automaticSpotifyBusy = true;
            automaticSpotifyNode = sourceNode;
            if (spotify == null) spotify = new SpotifyPlaybackController(context);
            spotify.start(music,command,new SpotifyPlaybackController.Listener() {
                @Override public void onProgress(String progress) { }
                @Override public void onFinished(boolean ok, String detail) {
                    automaticSpotifyBusy = false;
                    automaticSpotifyNode = "";
                    out.reply(ok, detail,"",false);
                }
            });
            return;
        }
        if (JarvisWearProtocol.CANCEL_SPOTIFY.equals(action)) {
            if (!automaticSpotifyBusy || !sourceNode.equals(automaticSpotifyNode)) {
                out.reply(false,"Немає твоєї активної команди Spotify.","",false);
                return;
            }
            if (spotify != null) spotify.cancel();
            out.reply(true,"Скасував керування Spotify.","",false);
            return;
        }
        if (JarvisWearProtocol.ENTER.equals(action)) {
            if (!ScreenControlAccessibilityService.isConnected()) {
                out.reply(false,"На телефоні потрібно вручну увімкнути «Джарвіс — керування екраном» у Спеціальних можливостях.","",true);
                return;
            }
            if (!session.isEmpty() && SystemClock.elapsedRealtime() < deadline &&
                !node.equals(sourceNode)) {
                out.reply(false,"Режим «Телефон» уже використовується іншим пристроєм.","",false);
                return;
            }
            // The watch already provides the user-facing microphone; do not
            // display a redundant Accessibility mic over the phone screen.
            String started = JarvisPhoneModeManager.enter(context, true);
            if (!ScreenControlAccessibilityService.isSessionActive()) {
                out.reply(false,started,"",true); return;
            }
            node = sourceNode;
            session = UUID.randomUUID().toString();
            JarvisPhoneMode.setActive(true);
            extend();
            out.reply(true,"Підключено до телефона. Команди керування готові.",session,false);
            return;
        }
        if (session.isEmpty() || !node.equals(sourceNode) ||
            !session.equals(suppliedToken) ||
            SystemClock.elapsedRealtime() > deadline ||
            !ScreenControlAccessibilityService.isSessionActive() ||
            !JarvisPhoneMode.isActive()) {
            if (!session.isEmpty() && SystemClock.elapsedRealtime() > deadline)
                endSession();
            out.reply(false,"Сеанс керування завершився. Скажи «Телефон» ще раз.","",true);
            return;
        }
        if (JarvisWearProtocol.EXIT.equals(action)) {
            endSession();
            out.reply(true,"Режим телефона вимкнено.","",true);
            return;
        }
        if (!JarvisWearProtocol.validCommand(command)) {
            out.reply(false,"Команда порожня або занадто довга.",session,false);
            return;
        }
        extend();
        if (sequences != null && sequences.isRunning()) {
            out.reply(false,"Телефон ще виконує послідовність. Повтори пізніше.",session,false);
            return;
        }
        JarvisCommandSequence.Plan plan = JarvisCommandSequence.parse(command);
        if (plan != null && (!plan.valid() || plan.multiple())) {
            if (!plan.valid()) {
                out.reply(false,plan.error,session,false);
                return;
            }
            if (sequences == null) sequences = new JarvisSequenceExecutor(context);
            final String token = session;
            sequences.start(plan,new JarvisSequenceExecutor.Listener() {
                @Override public void onProgress(int step,int total,String description) { }
                @Override public void onFinished(boolean success,String message) {
                    out.reply(success,message,token,false);
                }
            });
            return;
        }
        ScreenControlCommands.Action screen = ScreenControlCommands.parse(command);
        if (screen != null) {
            if (screen.kind == ScreenControlCommands.Kind.ENABLE ||
                screen.kind == ScreenControlCommands.Kind.SETTINGS ||
                screen.kind == ScreenControlCommands.Kind.STATUS) {
                out.reply(true,"Керування екраном телефона активне.",session,false);
                return;
            }
            if (screen.kind == ScreenControlCommands.Kind.DISABLE) {
                endSession();
                out.reply(true,"Режим телефона вимкнено.","",true);
                return;
            }
            final String token = session;
            ScreenControlAccessibilityService.perform(screen,
                (ok,status) -> out.reply(ok,status,token,false));
            return;
        }
        SpotifyMusicCommands.Kind music = SpotifyMusicCommands.parse(command);
        if (music != null) {
            if (spotify == null) spotify = new SpotifyPlaybackController(context);
            spotify.start(music,command,new SpotifyPlaybackController.Listener() {
                @Override public void onProgress(String detail) { }
                @Override public void onFinished(boolean success,String detail) {
                    out.reply(success,detail,session,false);
                }
            });
            return;
        }
        String normalized = CommandPatterns.normalizeControlVerb(command);
        DeviceCommands.Action device = DeviceCommands.parse(normalized);
        if (device != null) {
            if (devices == null) devices = new DeviceActions(context);
            String detail = devices.perform(device);
            out.reply(JarvisControlFeedback.successfulReply(detail),detail,session,false);
            return;
        }
        Integer timer = CommandPatterns.timerSeconds(normalized);
        if (timer != null) {
            boolean ok = timer > 0 && TimerScheduler.start(context,timer);
            out.reply(ok,ok ? "Таймер на телефоні встановлено на " + timer + " секунд."
                : "Не вдалося запустити таймер на телефоні. Перевір дозвіл точних будильників.",session,false);
            return;
        }
        if (normalized.equals("скасуй таймер") || normalized.equals("зупини таймер") ||
            normalized.equals("вимкни таймер")) {
            boolean ok = TimerScheduler.cancel(context);
            out.reply(ok,ok ? "Таймер телефона скасовано." : "На телефоні немає таймера.",session,false);
            return;
        }
        String app = AssistantIntents.requestedApp(normalized);
        if (app != null) {
            String launched = launchApp(context,app);
            out.reply(launched.startsWith("Відкриваю"),launched,session,false);
            return;
        }
        out.reply(false,"У режимі «Телефон» доступні «Натисни», «Зажми», «Напиши», " +
            "«Свайпни», «Назад», програми, гучність, музика, ліхтарик і таймер телефона.",
            session,false);
    }

    private static String launchApp(Context context,String voiceName) {
        PackageManager pm = context.getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppNameMatcher.App> apps = new ArrayList<>();
        try {
            for (ResolveInfo activity : pm.queryIntentActivities(launcher,0)) {
                if (activity.activityInfo == null) continue;
                String label = String.valueOf(activity.loadLabel(pm));
                if (label == null || label.isEmpty()) continue;
                apps.add(new AppNameMatcher.App(activity.activityInfo.packageName,
                    activity.activityInfo.name,label));
            }
            AppNameMatcher.Result match = AppNameMatcher.match(voiceName,apps);
            if (match.ambiguous()) return "Знайшов кілька програм. Уточни назву.";
            if (!match.found()) return "Не знайшов програму «" + voiceName + "» на телефоні.";
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(match.app.packageName,match.app.activityName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return "Відкриваю «" + match.app.label + "» на телефоні.";
        } catch (Exception error) {
            return "Не вдалося відкрити програму на телефоні. Розблокуй телефон та спробуй ще.";
        }
    }

    private static void extend() {
        deadline = SystemClock.elapsedRealtime() + JarvisWearProtocol.SESSION_IDLE_MS;
        if (expiry != null) main.removeCallbacks(expiry);
        expiry = () -> {
            if (!session.isEmpty() && SystemClock.elapsedRealtime() >= deadline) {
                if ((sequences != null && sequences.isRunning()) ||
                    (spotify != null && spotify.isRunning())) extend();
                else endSession();
            }
        };
        main.postDelayed(expiry,JarvisWearProtocol.SESSION_IDLE_MS + 100);
    }

    private static void endSession() {
        if (expiry != null) main.removeCallbacks(expiry);
        expiry = null;
        node = "";
        session = "";
        deadline = 0L;
        if (sequences != null) sequences.cancel();
        if (spotify != null) spotify.cancel();
        ScreenControlAccessibilityService.stop();
        JarvisPhoneMode.setActive(false);
    }
}
