package com.andriy.jarvis;

import android.app.KeyguardManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.WindowManager;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Optional rooted-Pixel unlock using a one-time PIN from an unlocked, paired
 * Galaxy Watch. Android provides no public API for remote verification of the
 * actual phone credential. The system lock screen still verifies the PIN.
 *
 * OFF by default. Never records, persists or logs a PIN. The watch must send
 * a fresh challenge bound to its Data Layer node. No attempt is made with
 * a password, pattern, an unpaired node or when owner has not opted in.
 */
public final class JarvisRemoteUnlock {
    public static final String REQUIRED = "JARVIS_PIN_REQUIRED:";
    public static final String RETRY = "JARVIS_PIN_RETRY:";
    public static final String NOT_ENABLED =
        "Для екранної команди розблокуй Pixel. На Pixel можна окремо " +
        "увімкнути експериментальний root-режим розблокування з Watch7.";
    private static final String PREFS = "jarvis_root_unlock_v1";
    private static final String ENABLED = "owner_opt_in";
    private static final int RELOCK_ALARM_ID = 81749;
    private static final long EXPIRES_MS = 60000L;
    private static final long AUTO_RELOCK_MS = 120000L;
    private static final int MAX_ATTEMPTS = 3;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Pending pending;
    private static boolean verifying;

    public interface Completion { void done(boolean success,String message); }
    public interface Resume {
        void run(String action,String originalText,Completion completion);
    }
    private static final class Pending {
        final String node,action,originalText,challenge;
        final long until;
        int attempts;
        Pending(String node,String action,String originalText,String challenge) {
            this.node=node;this.action=action;this.originalText=originalText;
            this.challenge=challenge;this.until=SystemClock.elapsedRealtime()+EXPIRES_MS;
        }
    }
    private JarvisRemoteUnlock() {}

    public static boolean enabled(Context context) {
        return !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH) &&
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getBoolean(ENABLED,false);
    }
    public static void setEnabled(Context context,boolean enabled) {
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit()
            .putBoolean(ENABLED,enabled).apply();
        if (!enabled) pending=null;
    }
    public static boolean needsUi(String action,String raw) {
        if (JarvisWearProtocol.TAXI.equals(action) ||
            JarvisWearProtocol.SPOTIFY.equals(action) ||
            JarvisWearProtocol.ENTER.equals(action) ||
            JarvisWearProtocol.COMMAND.equals(action)) return true;
        if (!JarvisWearProtocol.ASSISTANT.equals(action)) return false;
        if (FinanceVoiceCommands.parse(raw)!=null ||
            SongRecognitionCommands.matches(raw) ||
            JarvisTaxiCommands.destination(raw)!=null ||
            SpotifyMusicCommands.parse(raw)!=null) return true;
        JarvisCommandSequence.Plan plan=JarvisCommandSequence.parse(raw);
        if (plan!=null && plan.multiple()) return true;
        if (ScreenControlCommands.parse(raw)!=null) return true;
        return AssistantIntents.requestedApp(CommandPatterns.normalize(raw))!=null;
    }

    /** Executed only on the phone main looper, before a screen-dependent job. */
    public static String begin(Context context,String node,String action,String raw) {
        if (JarvisPhoneScreenGuard.available(context)) return null;
        if (!enabled(context)) return NOT_ENABLED;
        if (pending!=null && SystemClock.elapsedRealtime()<pending.until)
            return "Попередній запит на розблокування ще очікує. " +
                "Спочатку заверши або скасуй його.";
        pending=new Pending(node,action,raw,randomChallenge());
        return REQUIRED+pending.challenge;
    }

    public static void cancel(Context context,String node,String challenge) {
        if (pending!=null && pending.node.equals(node) &&
            pending.challenge.equals(challenge)) pending=null;
    }

    /**
     * The PIN is carried once in an already paired Wear Data Layer message.
     * It is never copied to an Intent, preferences, logging or AI prompt.
     * Root keyevents contain only separate digit keycodes (not PIN text).
     */
    public static void complete(Context context,String node,String response,
                                Resume resume,Completion completion) {
        Pending job=pending;
        int split=response==null?-1:response.indexOf(':');
        if (split<0 || job==null || verifying ||
            !job.node.equals(node) ||
            !job.challenge.equals(response.substring(0,split)) ||
            SystemClock.elapsedRealtime()>job.until) {
            completion.done(false,"Запит розблокування минув. Повтори голосову команду.");
            return;
        }
        String rawPin=response.substring(split+1);
        if (!rawPin.matches("[0-9]{4,12}")) {
            completion.done(false,"Дозволено тільки PIN телефона від 4 до 12 цифр.");
            return;
        }
        if (job.attempts>=MAX_ATTEMPTS) {
            pending=null;
            completion.done(false,"Перевищено кількість спроб. Розблокуй Pixel вручну.");
            return;
        }
        if (!enabled(context)) {
            pending=null;
            completion.done(false,"Root-розблокування вимкнено на телефоні.");
            return;
        }
        job.attempts++;
        verifying=true;
        char[] digits=rawPin.toCharArray();
        // Do not retain the original response or PIN beyond this async task.
        new Thread(() -> {
            boolean unlocked=unlockSystemKeyguard(context,digits);
            Arrays.fill(digits,'\0');
            MAIN.post(() -> {
                verifying=false;
                if (job!=pending) {
                    // An unexpected cancellation must not leave Pixel unlocked.
                    if (unlocked) relock(context);
                    completion.done(false,"Запит розблокування скасовано.");
                    return;
                }
                if (!unlocked) {
                    if (job.attempts>=MAX_ATTEMPTS) {
                        pending=null;
                        completion.done(false,
                            "Не вдалося розблокувати Pixel. Перевір PIN вручну.");
                    } else {
                        completion.done(false,RETRY+job.challenge);
                    }
                    return;
                }
                pending=null;
                // OS-managed fallback survives ordinary app process death.
                scheduleSystemRelock(context);
                final boolean[] completed={false};
                final Runnable[] failSafe={null};
                failSafe[0]=() -> {
                    if (completed[0]) return;
                    completed[0]=true;
                    finishAfterRelock(context,false,"Час виконання минув.",completion);
                };
                MAIN.postDelayed(failSafe[0],AUTO_RELOCK_MS);
                try {
                    resume.run(job.action,job.originalText,(ok,detail) -> MAIN.post(() -> {
                        if (completed[0]) return;
                        completed[0]=true;
                        MAIN.removeCallbacks(failSafe[0]);
                        finishAfterRelock(context,ok,detail,completion);
                    }));
                } catch (RuntimeException error) {
                    if (!completed[0]) {
                        completed[0]=true;
                        MAIN.removeCallbacks(failSafe[0]);
                        finishAfterRelock(context,false,
                            "Не вдалося виконати команду після розблокування.",completion);
                    }
                }
            });
        },"Jarvis-one-time-root-unlock").start();
    }

    /** Verify the Android lock state before reporting a completed remote job. */
    private static void finishAfterRelock(Context context,boolean commandOk,
                                           String result,Completion completion) {
        relock(context);
        MAIN.postDelayed(() -> {
            KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
            if (keyguard!=null && keyguard.isDeviceLocked()) {
                cancelSystemRelock(context);
                completion.done(commandOk,result+" Pixel повторно заблоковано.");
                return;
            }
            // Some OEMs accept the Accessibility action but do not lock.
            // Try the rooted sleep action once and re-check before reporting.
            rootScript("input keyevent 223\n",2500);
            MAIN.postDelayed(() -> {
                KeyguardManager actual=context.getSystemService(KeyguardManager.class);
                boolean locked=actual!=null && actual.isDeviceLocked();
                if (locked) cancelSystemRelock(context);
                completion.done(commandOk && locked,result+" "+
                    (locked?"Pixel повторно заблоковано.":
                        "УВАГА: автоматичне блокування Pixel не підтверджено. " +
                        "Заблокуй телефон вручну."));
            },500L);
        },600L);
    }

    private static PendingIntent lockAlarm(Context context) {
        Intent intent=new Intent(context,JarvisRelockReceiver.class);
        return PendingIntent.getBroadcast(context,RELOCK_ALARM_ID,intent,
            PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleSystemRelock(Context context) {
        AlarmManager alarm=context.getSystemService(AlarmManager.class);
        if (alarm == null) return;
        try {
            alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime()+AUTO_RELOCK_MS,lockAlarm(context));
        } catch (RuntimeException ignored) {
            // The in-process fail-safe still attempts a lock.
        }
    }

    private static void cancelSystemRelock(Context context) {
        AlarmManager alarm=context.getSystemService(AlarmManager.class);
        if(alarm!=null)alarm.cancel(lockAlarm(context));
    }

    /** Called by a system alarm after process loss; no credentials required. */
    public static void fallbackRelock(Context context) {
        KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
        if(keyguard==null || !keyguard.isDeviceLocked()) relock(context);
    }

    private static String randomChallenge() {
        byte[] bytes=new byte[16];
        new SecureRandom().nextBytes(bytes);
        StringBuilder out=new StringBuilder(32);
        for(byte b:bytes)out.append(String.format("%02x",b&0xff));
        return out.toString();
    }

    private static boolean unlockSystemKeyguard(Context context,char[] pin) {
        KeyguardManager keyguard=context.getSystemService(KeyguardManager.class);
        if (keyguard==null) return false;
        if (JarvisPhoneScreenGuard.available(context)) return true;
        // An extinguished display may still be in a trusted/unlocked state.
        // Wake it before deciding whether system PIN entry is necessary.
        if (!keyguard.isDeviceLocked()) {
            if (!rootScript("input keyevent 224\ninput keyevent 82\n",3500))
                return false;
            for(int i=0;i<5;i++) {
                if (JarvisPhoneScreenGuard.available(context)) return true;
                try { Thread.sleep(200L); }
                catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
            }
            return JarvisPhoneScreenGuard.available(context);
        }
        Point screen=new Point();
        WindowManager wm=context.getSystemService(WindowManager.class);
        if(wm==null)return false;
        wm.getDefaultDisplay().getRealSize(screen);
        int x=screen.x/2;
        int top=Math.max(120,screen.y/3);
        int bottom=Math.max(top+80,screen.y*4/5);
        StringBuilder script=new StringBuilder();
        script.append("input keyevent 224\n"); // wake
        script.append("input swipe ").append(x).append(' ').append(bottom)
            .append(' ').append(x).append(' ').append(top).append(" 270\n");
        script.append("input keyevent 82\n"); // reveal PIN keyboard if needed
        for(char digit:pin) {
            // KEYCODE_0=7, KEYCODE_9=16: PIN never appears in a shell argument.
            script.append("input keyevent ").append(7+digit-'0').append('\n');
        }
        script.append("input keyevent 66\n"); // ENTER
        if(!rootScript(script.toString(),8500))return false;
        for(int i=0;i<8;i++) {
            if(JarvisPhoneScreenGuard.available(context))return true;
            try { Thread.sleep(220L); }
            catch(InterruptedException e){Thread.currentThread().interrupt();return false;}
        }
        return JarvisPhoneScreenGuard.available(context);
    }

    /** Native Accessibility lock action, then root sleep fallback. */
    private static boolean relock(Context context) {
        if (ScreenControlAccessibilityService.lockDevice()) return true;
        return rootScript("input keyevent 223\n",2500);
    }

    private static boolean rootScript(String script,long maxMs) {
        Process process=null;
        try {
            process=new ProcessBuilder("su","-c","sh").redirectErrorStream(true).start();
            try(OutputStreamWriter input=new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8)) {
                input.write(script);
                input.flush();
            }
            if(!process.waitFor(maxMs,TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue()==0;
        } catch(Exception error) {
            if(process!=null)process.destroyForcibly();
            return false;
        }
    }
}
