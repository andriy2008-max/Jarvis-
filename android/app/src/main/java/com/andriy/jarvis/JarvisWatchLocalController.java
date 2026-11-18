package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;

/**
 * Watch-only app launch and on-demand screen actions. Ordinary voice commands
 * and all API/phone automation remain with the existing phone dispatcher.
 * A new explicit utterance is required for every click, swipe or text insert.
 */
public final class JarvisWatchLocalController {
    public interface Callback { void finished(boolean ok,String detail); }
    private static final String PREFS="jarvis_watch_local";
    private static final String LAST_PACKAGE="last_app_pkg";
    private static final long UI_TIMEOUT_MS=14000L;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static boolean running;

    private JarvisWatchLocalController() {}

    public static void execute(Context context,JarvisWatchLocalCommands.Request request,
                               Callback callback) {
        if(context==null || request==null || callback==null)return;
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            callback.finished(false,"Це керування доступне лише на годиннику.");
            return;
        }
        if(running) {
            callback.finished(false,"Годинник ще виконує попередню дію.");
            return;
        }
        running=true;
        Context watch=context.getApplicationContext();
        Callback done=(ok,detail)->{
            if(!running)return;
            running=false;
            callback.finished(ok,detail);
        };
        try {
        if(request.opensApp())launch(watch,request.app,done);
        else if(request.usesMaps()) {
            JarvisWatchMapsController.execute(watch,request.maps,
                (ok,detail)->{
                    if(ok)watch.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                        .edit().putString(LAST_PACKAGE,
                            JarvisWatchMapsController.MAPS_PACKAGE).apply();
                    done.finished(ok,detail);
                });
        }
        else if(request.playsSpotify()) {
            // Launch from the visible Watch Activity rather than from the
            // application context to avoid Wear OS background-start limits.
            JarvisWatchSpotifyController.play(context,request.music,request.musicTitle,
                (ok,detail)->{
                    if(ok)watch.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
                        .edit().putString(LAST_PACKAGE,"com.spotify.music").apply();
                    done.finished(ok,detail);
                });
        } else handleScreen(watch,request.screen,done);
        } catch(RuntimeException failure) {
            android.util.Log.e("DIMON_WATCH_CONTROL",
                "On-watch action failed before it could finish",failure);
            done.finished(false,"Дімон не зміг завершити дію на годиннику. "+
                "Перевір доступ до програми та повтори.");
        }
    }

    private static void launch(Context context,String name,Callback done) {
        PackageManager pm=context.getPackageManager();
        Intent launcher=new Intent(Intent.ACTION_MAIN);
        launcher.addCategory(Intent.CATEGORY_LAUNCHER);
        List<AppNameMatcher.App> apps=new ArrayList<>();
        try{
            for(ResolveInfo found:pm.queryIntentActivities(launcher,0)) {
                if(found.activityInfo==null)continue;
                CharSequence label=found.loadLabel(pm);
                if(label==null || label.toString().trim().isEmpty())continue;
                apps.add(new AppNameMatcher.App(found.activityInfo.packageName,
                    found.activityInfo.name,label.toString()));
            }
            AppNameMatcher.Result matched=AppNameMatcher.match(name,apps);
            if(matched.ambiguous()) {
                done.finished(false,"Знайдено кілька програм на годиннику. Уточни назву.");
                return;
            }
            if(!matched.found()) {
                done.finished(false,"Програма «"+name+"» не встановлена на годиннику.");
                return;
            }
            Intent intent=new Intent(Intent.ACTION_MAIN);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            intent.setClassName(matched.app.packageName,matched.app.activityName);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            context.startActivity(intent);
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
                .putString(LAST_PACKAGE,matched.app.packageName).apply();
            // Launching from the visible watch Activity can succeed without
            // Accessibility. Never claim the target finished loading.
            done.finished(true,"Відкриваю «"+matched.app.label+"» на годиннику.");
        }catch(RuntimeException ex) {
            done.finished(false,"Wear OS не дозволила відкрити програму.");
        }
    }

    private static void handleScreen(Context context,ScreenControlCommands.Action action,
                                     Callback done) {
        if(action==null) {
            done.finished(false,"Невідома команда керування годинником.");return;
        }
        if(!JarvisWatchScreenService.available(context)) {
            done.finished(false,"На годиннику ввімкни Налаштування → "+
                "Спеціальні можливості → Установлені програми → "+
                "Дімон — екран годинника.");
            return;
        }
        String pkg=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
            .getString(LAST_PACKAGE,"");
        if(pkg.isEmpty() || pkg.equals(context.getPackageName())) {
            done.finished(false,"Спочатку скажи «Відкрий [програму] на годиннику».");
            return;
        }
        // Bring back the last watch app after Jarvis was temporarily opened
        // to capture a new voice command. This preserves its existing task.
        Intent relaunch=context.getPackageManager().getLaunchIntentForPackage(pkg);
        if(relaunch==null) {
            done.finished(false,"Остання програма більше не доступна на годиннику.");
            return;
        }
        relaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try{context.startActivity(relaunch);}
        catch(RuntimeException ex) {
            done.finished(false,"Wear OS не дозволила повернутися до програми.");
            return;
        }
        awaitReady(pkg,action,SystemClock.elapsedRealtime()+UI_TIMEOUT_MS,done);
    }

    private static void awaitReady(String pkg,ScreenControlCommands.Action action,
                                   long expires,Callback done) {
        if(!running)return;
        if(SystemClock.elapsedRealtime()>expires) {
            done.finished(false,"Кнопка або поле не з'явилися. Нічого навмання не натискав.");
            return;
        }
        if(!JarvisWatchScreenService.foreground(pkg)) {
            MAIN.postDelayed(()->awaitReady(pkg,action,expires,done),650L);
            return;
        }
        JarvisWatchScreenService.Result result=
            JarvisWatchScreenService.perform(pkg,action,(ok,detail)->{
                if(!running)return;
                done.finished(ok,detail);
            });
        switch(result) {
            case DONE:
                done.finished(true,"Команду виконано в програмі на годиннику.");
                return;
            case RUNNING:
                // Android gesture callback will complete the operation.
                return;
            case BLOCKED:
                done.finished(false,"Wear OS відхилила цю дію.");
                return;
            case WAIT:
            default:
                MAIN.postDelayed(()->awaitReady(pkg,action,expires,done),1200L);
        }
    }
}
