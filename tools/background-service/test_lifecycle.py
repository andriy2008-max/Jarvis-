"""Run the production Service/Receiver against a small deterministic Android shell.
These tests verify lifecycle decisions, not real OS process scheduling or Wear delivery.
"""
import pathlib
import subprocess
import tempfile
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
JAVA = ROOT / 'android/app/src/main/java/com/andriy/jarvis'
STUBS = {
'android/content/SharedPreferences.java': '''package android.content; import java.util.*;
public class SharedPreferences { Map<String,Object> data=new HashMap<>();
public boolean getBoolean(String k,boolean d){return (boolean)data.getOrDefault(k,d);}
public String getString(String k,String d){return (String)data.getOrDefault(k,d);}
public SharedPreferences edit(){return this;} public SharedPreferences putBoolean(String k,boolean v){data.put(k,v);return this;}
public SharedPreferences putString(String k,String v){data.put(k,v);return this;}
public SharedPreferences remove(String k){data.remove(k);return this;} public boolean commit(){return true;} public void apply(){} }''',
'android/content/Context.java': '''package android.content; import android.content.pm.*;
public class Context { public static final int MODE_PRIVATE=0; public static SharedPreferences prefs=new SharedPreferences();
public static boolean watch,denyStart; public static int starts,stops;
public SharedPreferences getSharedPreferences(String n,int m){return prefs;}
public PackageManager getPackageManager(){return new PackageManager();}
public void startForegroundService(Intent i){if(denyStart)throw new IllegalStateException();starts++;}
public boolean stopService(Intent i){stops++;return true;}
public <T> T getSystemService(Class<T> c){try{return c.getDeclaredConstructor().newInstance();}catch(Exception e){throw new RuntimeException(e);}} }''',
'android/content/Intent.java': '''package android.content; public class Intent {
public static final String ACTION_BOOT_COMPLETED="boot",ACTION_MY_PACKAGE_REPLACED="update";
public static final int FLAG_ACTIVITY_NEW_TASK=1,FLAG_ACTIVITY_CLEAR_TOP=2; String action;
public Intent(Context c,Class<?> cls){} public Intent(String a){action=a;} public String getAction(){return action;}
public Intent addFlags(int f){return this;} }''',
'android/content/BroadcastReceiver.java': 'package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager {public static final String FEATURE_WATCH="watch"; public boolean hasSystemFeature(String s){return android.content.Context.watch;}}',
'android/content/pm/ServiceInfo.java': 'package android.content.pm; public class ServiceInfo {public static final int FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE=16;}',
'android/os/Build.java': 'package android.os; public class Build {public static class VERSION {public static int SDK_INT=35;}}',
'android/os/IBinder.java': 'package android.os; public interface IBinder {}',
'android/app/Service.java': '''package android.app; import android.content.*; import android.os.*;
public class Service extends Context {public static final int START_NOT_STICKY=2,START_STICKY=1;
public static boolean denyForeground;public int stopped,foregroundType,foregroundCalls;
public void startForeground(int id,Notification n,int type){if(denyForeground)throw new SecurityException();foregroundType=type;foregroundCalls++;}
public void startForeground(int id,Notification n){startForeground(id,n,0);} public void stopSelf(){stopped++;}
public int onStartCommand(Intent i,int f,int id){return 0;} public void onTaskRemoved(Intent i){} public void onDestroy(){} public IBinder onBind(Intent i){return null;}}''',
'android/app/Notification.java': '''package android.app; import android.content.*; public class Notification {
public static String CATEGORY_SERVICE="service"; public static class Builder { public Builder(Context c,String id){}
public Builder setSmallIcon(int i){return this;} public Builder setContentTitle(String s){return this;}
public Builder setContentText(String s){return this;} public Builder setContentIntent(PendingIntent p){return this;}
public Builder setOngoing(boolean b){return this;} public Builder setOnlyAlertOnce(boolean b){return this;}
public Builder setCategory(String s){return this;} public Notification build(){return new Notification();}}}''',
'android/app/NotificationChannel.java': 'package android.app; public class NotificationChannel {public NotificationChannel(String id,String name,int importance){}}',
'android/app/NotificationManager.java': 'package android.app; public class NotificationManager {public static int IMPORTANCE_LOW=2; public void createNotificationChannel(NotificationChannel c){}}',
'android/app/PendingIntent.java': 'package android.app; import android.content.*; public class PendingIntent {public static int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2; public static PendingIntent getActivity(Context c,int id,Intent i,int flags){return new PendingIntent();}}',
'com/andriy/jarvis/MainActivity.java': 'package com.andriy.jarvis; public class MainActivity {}',
'com/andriy/jarvis/R.java': 'package com.andriy.jarvis; public class R {public static class drawable {public static int ic_launcher=1;}}',
'com/andriy/jarvis/LifecycleTest.java': '''package com.andriy.jarvis;
import android.content.*; import android.app.*; import android.os.*;
public class LifecycleTest {
static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
public static void main(String[] args){
Context c=new Context(); JarvisBackgroundRestoreReceiver boot=new JarvisBackgroundRestoreReceiver();
boot.onReceive(c,new Intent(Intent.ACTION_BOOT_COMPLETED));check(Context.starts==0,"never-opened install must not autostart");
JarvisBackgroundService.startIfEnabled(c);check(Context.starts==1,"visible phone launch starts keeper");
JarvisBackgroundService s=new JarvisBackgroundService();
check(s.onStartCommand(new Intent("start"),0,1)==Service.START_STICKY,"sticky recovery required");
check(JarvisBackgroundService.isRunning() && s.foregroundType==16,"must enter connected-device foreground");
s.onTaskRemoved(new Intent("task"));check(s.stopped==0 && JarvisBackgroundService.isRunning(),"swipe must not stop keeper");
s.onDestroy();check(!JarvisBackgroundService.isRunning(),"status cannot claim dead service is active");
check(s.onStartCommand(null,0,2)==Service.START_STICKY,"null restart intent must restore keeper");
boot.onReceive(c,new Intent(Intent.ACTION_BOOT_COMPLETED));check(Context.starts==2,"boot restores activated phone");
boot.onReceive(c,new Intent(Intent.ACTION_MY_PACKAGE_REPLACED));check(Context.starts==3,"update restores activated phone");
boot.onReceive(c,null);boot.onReceive(c,new Intent("unrelated"));check(Context.starts==3,"unrelated broadcasts ignored");
JarvisBackgroundService.setEnabled(c,false);check(Context.stops==1,"explicit stop must stop service");
check(s.onStartCommand(null,0,3)==Service.START_NOT_STICKY,"disabled preference survives stale restart");
boot.onReceive(c,new Intent(Intent.ACTION_BOOT_COMPLETED));check(Context.starts==3,"disabled preference survives reboot");
Context.watch=true;JarvisBackgroundService.setEnabled(c,true);check(Context.starts==3,"watch must not run phone keeper");
check(s.onStartCommand(null,0,4)==Service.START_NOT_STICKY,"watch service fails closed");
Context.watch=false;Service.denyForeground=true;
check(s.onStartCommand(null,0,5)==Service.START_NOT_STICKY && !JarvisBackgroundService.isRunning(),"foreground denial must not crash or claim success");
Service.denyForeground=false;Context.denyStart=true;JarvisBackgroundService.startIfEnabled(c);
check(JarvisBackgroundService.status(c).contains("Android"),"background launch denial must be visible");
Context.denyStart=false;Build.VERSION.SDK_INT=28;
check(s.onStartCommand(null,0,6)==Service.START_STICKY && s.foregroundType==0,"pre-29 uses two-argument foreground API");
System.out.println("PASS background lifecycle: swipe, process recreation, reboot, update, opt-out, watch exclusion, denial, API28");
}}'''
}

class LifecycleTests(unittest.TestCase):
    def test_production_service_lifecycle(self):
        with tempfile.TemporaryDirectory() as tmp:
            base=pathlib.Path(tmp)
            for name,source in STUBS.items():
                f=base/name; f.parent.mkdir(parents=True,exist_ok=True);f.write_text(source)
            sources=[str(p) for p in base.rglob('*.java')]+[str(JAVA/n) for n in ('JarvisBackgroundService.java','JarvisBackgroundRestoreReceiver.java')]
            subprocess.run(['javac','-encoding','UTF-8','-d',str(base/'classes'),*sources],check=True)
            subprocess.run(['java','-cp',str(base/'classes'),'com.andriy.jarvis.LifecycleTest'],check=True)
    def test_android_registration(self):
        root=ET.parse(ROOT/'android/app/src/main/AndroidManifest.xml').getroot()
        a='{http://schemas.android.com/apk/res/android}'
        service=next(x for x in root.findall('./application/service') if x.get(a+'name')=='.JarvisBackgroundService')
        self.assertEqual(service.get(a+'stopWithTask'),'false')
        self.assertEqual(service.get(a+'exported'),'false')
        self.assertEqual(service.get(a+'foregroundServiceType'),'connectedDevice')
        permissions={x.get(a+'name') for x in root.findall('uses-permission')}
        self.assertTrue({'android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE','android.permission.CHANGE_NETWORK_STATE','android.permission.WAKE_LOCK'} <= permissions)

if __name__=='__main__':unittest.main()
