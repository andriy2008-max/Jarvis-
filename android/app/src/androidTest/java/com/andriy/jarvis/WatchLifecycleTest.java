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
                awaitResumed(scenario, false);
                // Preserve launcher action/categories: onNewIntent calls setIntent(), and
                // ActivityScenario filters lifecycle events using Intent.filterEquals().
                Intent reopen=Intent.makeMainActivity(new ComponentName(app,MainActivity.class));
                reopen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT|
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
                app.startActivity(reopen);
                awaitResumed(scenario, true);
                scenario.onActivity(a->{ assertFalse(a.isFinishing());assertFalse(a.isDestroyed()); });
            }
        }
    }
    private void awaitResumed(ActivityScenario<MainActivity> scenario, boolean resumed) {
        long deadline=SystemClock.elapsedRealtime()+10000;
        do {
            inst.waitForIdleSync();
            if((scenario.getState()==androidx.lifecycle.Lifecycle.State.RESUMED)==resumed)return;
            SystemClock.sleep(50);
        } while(SystemClock.elapsedRealtime()<deadline);
        fail("Expected resumed="+resumed+", actual state="+scenario.getState());
    }
    @Test public void backgroundSyncDoesNotBlockLocalCommands() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{
                JarvisWearPhoneLink link=new JarvisWearPhoneLink(a);
                try {
                    Field requesting=JarvisWearPhoneLink.class.getDeclaredField("requesting");
                    Field type=JarvisWearPhoneLink.class.getDeclaredField("requestingType");
                    requesting.setAccessible(true);type.setAccessible(true);
                    requesting.set(link,true);type.set(link,JarvisWearProtocol.SYNC);
                    assertTrue(link.busy());assertFalse(link.userRequestBusy());
                    type.set(link,JarvisWearProtocol.COMMAND);
                    assertTrue(link.userRequestBusy());
                    requesting.set(link,false);
                    assertFalse(link.userRequestBusy());
                } catch(Exception error) {throw new AssertionError(error);}
                finally {link.close();}
            });
        }
    }
    @Test public void malformedPhoneReplyReleasesPendingCommand() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{
                JarvisWearPhoneLink link=new JarvisWearPhoneLink(a);
                try {
                    Field pending=JarvisWearPhoneLink.class.getDeclaredField("pendingId");
                    Field node=JarvisWearPhoneLink.class.getDeclaredField("pendingNode");
                    Field type=JarvisWearPhoneLink.class.getDeclaredField("pendingType");
                    Field callback=JarvisWearPhoneLink.class.getDeclaredField("pendingCallback");
                    for(Field field:new Field[]{pending,node,type,callback})
                        field.setAccessible(true);
                    pending.set(link,"pending-test-id");
                    node.set(link,"phone-node");
                    type.set(link,JarvisWearProtocol.ASSISTANT);
                    final int[] responses={0};
                    callback.set(link,(JarvisWearPhoneLink.Callback)(ok,detail)->{
                        assertFalse("Malformed reply must fail",ok);
                        responses[0]++;
                    });
                    com.google.android.gms.wearable.MessageEvent malformed=
                        (com.google.android.gms.wearable.MessageEvent)
                        java.lang.reflect.Proxy.newProxyInstance(
                            com.google.android.gms.wearable.MessageEvent.class.getClassLoader(),
                            new Class[]{com.google.android.gms.wearable.MessageEvent.class},
                            (proxy,method,args)->{
                                if(method.getName().equals("getPath"))return JarvisWearProtocol.REPLY;
                                if(method.getName().equals("getSourceNodeId"))return "phone-node";
                                if(method.getName().equals("getData"))return "{not-json".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                if(method.getReturnType()==int.class)return 0;
                                return null;
                            });
                    Method receive=JarvisWearPhoneLink.class.getDeclaredMethod(
                        "onReply",com.google.android.gms.wearable.MessageEvent.class);
                    receive.setAccessible(true);
                    receive.invoke(link,malformed);
                    assertEquals("Malformed reply must notify once",1,responses[0]);
                    assertFalse("Malformed reply must clear pending state",link.busy());
                } catch(Exception error) {throw new AssertionError(error);}
                finally {link.close();}
            });
        }
    }

    @Test public void gestureCallbackExceptionsAreContained() {
        try(ActivityScenario<MainActivity> scenario=ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(a->{
                JarvisWatchScreenService.safeGestureCompletion(
                    (ok,detail)->{throw new IllegalStateException("private callback text");},
                    true,"done");
                assertFalse("Callback failure cannot destroy watch activity",a.isFinishing());
                assertFalse(a.isDestroyed());
            });
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
