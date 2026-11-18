package com.andriy.jarvis;
import android.app.Instrumentation;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.*;
import org.junit.runner.RunWith;
import java.lang.reflect.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class WatchLifecycleTest {
    private final Instrumentation inst=InstrumentationRegistry.getInstrumentation();
    private void dispatch(MainActivity a,String command) {
        try {
            Method m=MainActivity.class.getDeclaredMethod("processCommand",String.class);
            m.setAccessible(true);m.invoke(a,command);
            assertFalse(a.isFinishing());assertFalse(a.isDestroyed());
        } catch(Exception e) { throw new AssertionError(e); }
    }
    @Test public void twentyCommandsAndRecreation() throws Exception {
        Context app=inst.getTargetContext();
        assertTrue("This test requires a Wear OS image",app.getPackageManager()
            .hasSystemFeature(PackageManager.FEATURE_WATCH));
        // Mutate test APK preferences only. Never installed production preferences.
        app.getSharedPreferences("com.andriy.jarvis.MainActivity",0).edit().putBoolean("voiceEnabled",false)
            .putBoolean("watch_session_notification_permission_asked",true).commit();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            String[] commands={"Увімкни пісню","заново","Відкрий Spotify",
                "Дімон, увімкни пісню Пачка сигарет","Увімкни плейлист Test",
                "Увімкни реп","Увімкни українське","Увімкни музику",
                "Де я","Знайди дорогу до Києва","Скажи перевірка",
                "Увімкни пісню Обійми","Увімкни пісню Yesterday",
                "Відкрий Spotify","Скажи другий раз","Увімкни реп",
                "Де я","Увімкни плейлист Jazz","Скажи третій раз","Відкрий Spotify"};
            int pid=android.os.Process.myPid();
            for(int i=0;i<commands.length;i++) {
                String c=commands[i];scenario.onActivity(a->dispatch(a,c));
                inst.waitForIdleSync();
                assertEquals(pid,android.os.Process.myPid());
                if(i%5==4)scenario.recreate();
            }
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);
            scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);
            scenario.onActivity(a->{assertFalse(a.isFinishing());assertFalse(a.isDestroyed());});
        }
    }
    @Test public void repeatedRecognitionResultsDispatchOnlyOnce() throws Exception {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{
                try {
                    Field voice=MainActivity.class.getDeclaredField("voiceEnabled");voice.setAccessible(true);voice.set(a,false);
                    Field rec=MainActivity.class.getDeclaredField("recognizer");rec.setAccessible(true);
                    android.speech.SpeechRecognizer real=(android.speech.SpeechRecognizer)rec.get(a);
                    if(real!=null)real.destroy();rec.set(a,null);
                    Method listener=MainActivity.class.getDeclaredMethod("ownedRecognizerListener",android.speech.SpeechRecognizer.class);
                    listener.setAccessible(true);
                    Field counter=JarvisDiagnostics.class.getDeclaredField("commandId");counter.setAccessible(true);
                    long before=counter.getLong(null);
                    for(int i=0;i<20;i++) {
                        for(String field:new String[]{"manualCapture","manualStopRequested","foreground"}) {
                            Field f=MainActivity.class.getDeclaredField(field);f.setAccessible(true);f.set(a,true);
                        }
                        android.speech.RecognitionListener owned=(android.speech.RecognitionListener)listener.invoke(a,new Object[]{null});
                        Bundle result=new Bundle();java.util.ArrayList<String> words=new java.util.ArrayList<>();
                        words.add("Скажи перевірка "+i);result.putStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION,words);
                        owned.onResults(result);owned.onResults(result);owned.onError(7);
                    }
                    assertEquals("One command per final result, duplicate terminal callbacks ignored",20,counter.getLong(null)-before);
                } catch(Exception e) {throw new AssertionError(e);}
            });
        }
    }
    @Test public void externalSettingsAndReturn() {
        Context app=inst.getTargetContext();
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            for(int i=0;i<3;i++) {
                scenario.onActivity(a->a.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)));
                inst.waitForIdleSync();
                app.startActivity(new Intent(app,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|Intent.FLAG_ACTIVITY_SINGLE_TOP));
                inst.waitForIdleSync();
                scenario.onActivity(a->{ assertFalse(a.isFinishing());assertFalse(a.isDestroyed()); });
            }
        }
    }
    @Test public void reportExcludesExceptionSecrets() throws Exception {
        String secret="sk-proj-private-message-password";
        JarvisDiagnostics.error(JarvisDiagnostics.Event.JAVA_EXCEPTION,new IllegalStateException(secret));
        SystemClock.sleep(300);
        String report=JarvisDiagnostics.report(inst.getTargetContext());
        assertFalse(report.contains(secret));
        assertTrue(report.contains("ILLEGAL_STATE"));
    }
    @Test public void diagnosticsScreenReopens() {
        try(ActivityScenario<JarvisDiagnosticsActivity> s=ActivityScenario.launch(JarvisDiagnosticsActivity.class)) {
            s.recreate(); s.onActivity(a->assertFalse(a.isFinishing()));
        }
    }
}
