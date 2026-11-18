package com.andriy.jarvis;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.os.SystemClock;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

/** Private bounded journal. No API accepts a command, reply, token or exception message. */
public final class JarvisDiagnostics {
    public enum Event { PROCESS_START, CREATE, START, RESUME, PAUSE, STOP, DESTROY,
        NEW_INTENT, CONFIG_CHANGE, USER_FINISH, LOW_MEMORY, TRIM_MEMORY,
        COMMAND_TYPE, SOURCE_WATCH, SOURCE_PIXEL, COMMAND_BEGIN, COMMAND_END, COMMAND_TIMEOUT, COMMAND_INTERRUPTED,
        WATCH_ROUTE, PIXEL_ROUTE, MIC_START, MIC_READY, MIC_END, MIC_RESULT, MIC_ERROR,
        MIC_CANCEL, TTS_START, TTS_END, TTS_ERROR, EXTERNAL_LAUNCH,
        LOCAL_DONE, LOCAL_ERROR, PIXEL_DONE, PIXEL_ERROR, ACCESSIBILITY_ERROR,
        CALLBACK_REJECTED, JAVA_EXCEPTION, UNCAUGHT, SPOTIFY_STAGE, AUDIO_FOCUS }
    private static Context app;
    private static File journal;
    private static final Object LOCK = new Object();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(1,1,0L,
        TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(256),r -> {
            Thread t=new Thread(r,"dimon-diagnostics"); t.setDaemon(true); return t;
        },new ThreadPoolExecutor.DiscardPolicy());
    private static volatile String last = "";
    private static long commandId;
    private JarvisDiagnostics() {}

    static void init(Context context) {
        app=context.getApplicationContext();
        journal=new File(app.getNoBackupFilesDir(),"dimon-work.log");
        Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            // Persist only known code identifiers, not messages or raw stack traces.
            String report=last+"\n"+safeError(Event.UNCAUGHT,error);
            AtomicFile crash=new AtomicFile(new File(app.getNoBackupFilesDir(),"dimon-crash.log"));
            FileOutputStream out=null;
            try { out=crash.startWrite(); out.write(report.getBytes(StandardCharsets.UTF_8));
                crash.finishWrite(out); } catch(Exception ignored) { if(out!=null)crash.failWrite(out); }
            // Preserve Android's crash handling. Never hide an exception or restart in a loop.
            if(previous!=null) previous.uncaughtException(thread,error);
            else { android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10); }
        });
        event(Event.PROCESS_START,android.os.Process.myPid());
    }
    static synchronized long beginCommand() {
        long id=++commandId; event(Event.COMMAND_BEGIN,id); return id;
    }
    public static void event(Event event) { event(event,0); }
    public static void event(Event event,long code) {
        String row=System.currentTimeMillis()+" "+SystemClock.elapsedRealtime()+" "+event.name()+" "+code;
        last=row;
        // OS-owned process summary survives a native crash/LMK even if IO has not drained.
        if(app!=null && Build.VERSION.SDK_INT>=30) try {
            ActivityManager am=app.getSystemService(ActivityManager.class);
            if(am!=null) am.setProcessStateSummary(row.getBytes(StandardCharsets.UTF_8));
        } catch(RuntimeException ignored) {}
        append(row);
    }
    public static void error(Event event,Throwable error) { append(safeError(event,error)); }
    private static String safeError(Event event,Throwable error) {
        StringBuilder b=new StringBuilder(event.name());
        // Exception class names are identifiers; never include getMessage()/toString().
        b.append(" ").append(error instanceof OutOfMemoryError?"OUT_OF_MEMORY":
            error instanceof SecurityException?"SECURITY":
            error instanceof IllegalStateException?"ILLEGAL_STATE":"EXCEPTION");
        for(StackTraceElement frame:error.getStackTrace()) {
            if(frame.getClassName().startsWith("com.andriy.jarvis.")) {
                b.append("\n at ").append(frame.getClassName()).append(".")
                    .append(frame.getMethodName()).append(":").append(frame.getLineNumber());
                if(b.length()>1800)break;
            }
        }
        return b.toString();
    }
    private static void append(String row) {
        if(journal==null)return;
        IO.execute(()->{ synchronized(LOCK) {
            try {
                if(journal.length()>65536) {
                    File old=new File(journal.getPath()+".1");
                    old.delete(); journal.renameTo(old);
                }
                try(FileOutputStream out=new FileOutputStream(journal,true)) {
                    out.write((row+"\n").getBytes(StandardCharsets.UTF_8));
                }
            } catch(IOException ignored) {}
        }});
    }
    static String report(Context context) {
        StringBuilder b=new StringBuilder("Дімон — журнал роботи\n");
        try { android.content.pm.PackageInfo info=context.getPackageManager()
            .getPackageInfo(context.getPackageName(),0);
            b.append(info.packageName).append(" ").append(info.versionName)
                .append(" code=").append(Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode)
                .append(" SDK=").append(Build.VERSION.SDK_INT).append("\n");
        } catch(Exception ignored) {}
        b.append("Причини завершення (числові коди Android; 4=Java crash, 5=native, 6=ANR, 3=memory, 10=user):\n");
        if(Build.VERSION.SDK_INT>=30) try {
            ActivityManager am=context.getSystemService(ActivityManager.class);
            java.util.List<ApplicationExitInfo> exits=am==null?java.util.Collections.emptyList():
                am.getHistoricalProcessExitReasons(context.getPackageName(),0,5);
            long cleared=context.getSharedPreferences("dimon_diagnostics",0).getLong("cleared",0);
            boolean any=false;
            for(ApplicationExitInfo e:exits) {
                if(e.getTimestamp()<=cleared)continue;
                any=true;
                b.append("at=").append(e.getTimestamp()).append(" reason=").append(e.getReason())
                    .append(" status=").append(e.getStatus()).append(" importance=").append(e.getImportance())
                    .append(" pssKB=").append(e.getPss()).append(" rssKB=").append(e.getRss()).append("\n");
                byte[] summary=e.getProcessStateSummary();
                if(summary!=null) {
                    String s=new String(summary,StandardCharsets.UTF_8);
                    if(s.matches("[0-9]+ [0-9]+ [A-Z_]+ -?[0-9]+")) b.append("last=").append(s).append("\n");
                }
            }
            if(!any)b.append("Android не надав записів після очищення. Це не доказ відсутності збою.\n");
        } catch(RuntimeException error) { b.append("Причина завершення недоступна.\n"); }
        else b.append("Потрібен Android 11+.\n");
        synchronized(LOCK) {
            for(String name:new String[]{"dimon-crash.log","dimon-work.log.1","dimon-work.log"}) {
                File f=new File(context.getNoBackupFilesDir(),name);
                try(FileInputStream in=new FileInputStream(f)) {
                    byte[] bytes=new byte[66000];int n=in.read(bytes);
                    if(n>0)b.append(new String(bytes,0,n,StandardCharsets.UTF_8)).append("\n");
                } catch(IOException ignored) {}
            }
        }
        return b.toString();
    }
    static void clear(Context context,Runnable completed) {
        IO.execute(()->{ synchronized(LOCK) {
            for(String name:new String[]{"dimon-crash.log","dimon-work.log.1","dimon-work.log"})
                new File(context.getNoBackupFilesDir(),name).delete();
            context.getSharedPreferences("dimon_diagnostics",0).edit()
                .putLong("cleared",System.currentTimeMillis()).commit();
        } new android.os.Handler(android.os.Looper.getMainLooper()).post(completed); });
    }
}
