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

    private JarvisWearPhoneSession() {}

    public static void receive(Context context, String sourceNode, JSONObject request, Result result) {
        main.post(() -> receiveOnMain(context.getApplicationContext(),sourceNode,request,result));
    }

    private static void receiveOnMain(Context context,String sourceNode,JSONObject request,Result out) {
        String action = request.optString("action","");
        String suppliedToken = request.optString("session","");
        String command = request.optString("text","");
        if (!JarvisWearProtocol.validAction(action) || sourceNode == null ||
            sourceNode.trim().isEmpty()) {
            out.reply(false,"Неприпустима команда Wear OS.","",true); return;
        }
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            out.reply(false,"Ця команда призначена для телефона.","",true); return;
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
            String started = ScreenControlAccessibilityService.start();
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
            String status = ScreenControlAccessibilityService.perform(screen);
            boolean successful = status != null &&
                !(status.startsWith("Не ") || status.startsWith("Спочатку") ||
                  status.contains("заборонен") || status.contains("Уточни"));
            out.reply(successful,status == null ? "Не вдалося виконати команду." : status,
                session,false);
            return;
        }
        JarvisCommandSequence.Plan plan = JarvisCommandSequence.parse(command);
        if (plan != null && plan.multiple()) {
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
        SpotifyMusicCommands.Kind music = SpotifyMusicCommands.parse(command);
        if (music != null) {
            if (spotify == null) spotify = new SpotifyPlaybackController(context);
            spotify.start(music,new SpotifyPlaybackController.Listener() {
                @Override public void onProgress(String detail) { }
                @Override public void onFinished(boolean success,String detail) {
                    out.reply(success,detail,session,false);
                }
            });
            return;
        }
        String normalized = CommandPatterns.normalize(command);
        String app = AssistantIntents.requestedApp(normalized);
        if (app != null) {
            String launched = launchApp(context,app);
            out.reply(launched.startsWith("Відкриваю"),launched,session,false);
            return;
        }
        out.reply(false,"У режимі «Телефон» доступні «Натисни», «Зажми», «Напиши», " +
            "«Свайпни», «Назад» і відкриття програм. Інші команди виконує звичайний Джарвіс.",
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
            if (!session.isEmpty() && SystemClock.elapsedRealtime() >= deadline)
                endSession();
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
