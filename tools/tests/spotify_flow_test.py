"""Exercise the real Spotify controller with a deterministic clock and UI responses.
No APK, credentials, network or Spotify account needed. Run with Python 3 + JDK 17.
The fake UI tests ordering/cancellation, not Spotify's actual accessibility tree.
"""
import os
from pathlib import Path
import shlex
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / 'android/app/src/main/java/com/andriy/jarvis'
SOURCES = {
'android/os/SystemClock.java': '''package android.os; public class SystemClock {public static long now; public static long elapsedRealtime(){return now;}}''',
'android/os/Looper.java': '''package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}''',
'android/os/Handler.java': '''package android.os; import java.util.*; public class Handler {
static class Task {long at;Runnable r;Task(long at,Runnable r){this.at=at;this.r=r;}}
static List<Task> q=new ArrayList<>(); public Handler(Looper l){}
public void postDelayed(Runnable r,long delay){q.add(new Task(SystemClock.now+delay,r));}
public void removeCallbacks(Runnable r){q.removeIf(t->t.r==r);}
public static void advance(long duration){long end=SystemClock.now+duration;int count=0;
while(true){Task next=q.stream().filter(t->t.at<=end).min(Comparator.comparingLong(t->t.at)).orElse(null);
if(next==null)break;if(++count>1000)throw new AssertionError("busy polling");q.remove(next);SystemClock.now=next.at;next.r.run();}SystemClock.now=end;}
}''',
'android/content/Intent.java': '''package android.content; public class Intent {public static final int FLAG_ACTIVITY_NEW_TASK=1;public Intent addFlags(int f){return this;}}''',
'android/content/pm/PackageManager.java': '''package android.content.pm; public class PackageManager {public android.content.Intent getLaunchIntentForPackage(String p){return new android.content.Intent();}}''',
'android/content/Context.java': '''package android.content; public class Context {public Context getApplicationContext(){return this;}public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}public void startActivity(Intent i){}}''',
'android/util/Log.java': '''package android.util;public class Log {public static int e(String t,String s,Throwable e){throw new AssertionError(e);}}''',
'com/andriy/jarvis/Fakes.java': '''package com.andriy.jarvis;
import android.content.Context;import java.util.*;
class JarvisActionNotice {static List<String> messages=new ArrayList<>();static void show(Context c,String s){messages.add(s);}}
class JarvisPhoneScreenGuard {static boolean available(Context c){return true;}}
class JarvisPhoneModeManager {static int acquired,released;static class Lease {boolean granted=true;String message="";}
static Lease acquireForCommand(Context c){acquired++;return new Lease();}static void releaseForCommand(Lease l){releaseForCommand(l,false);}static void releaseForCommand(Lease l,boolean ok){if(l!=null)released++;}}
class SpotifyDirectSettings {static String clientId(Context c){return "";}}
class SpotifyAppRemoteBridge {interface Result{void done(boolean ok,String s);}SpotifyAppRemoteBridge(Context c,String id,String uri,boolean resume,Result cb){}void connect(){}void cancel(){}}
class JarvisWearPhoneSession {interface Result{void done(boolean ok,String s);}static void launchApp(Context c,String app,Result cb){cb.done(true,"opened");}}
class ScreenControlAccessibilityService {
enum SpotifyStep {WAIT,CLICKED,ALREADY,BLOCKED}
static boolean visible=true,searchOpen,placeholder=true,editor,library=true,collection=true,rows=true,filter;
static int tabs,libraryClicks,focuses,types,submits,rowClicks;static String last="";
static List<Long> focusTimes=new ArrayList<>();
static void reset(){visible=true;searchOpen=false;placeholder=true;editor=false;library=true;collection=true;rows=true;filter=false;tabs=libraryClicks=focuses=types=submits=rowClicks=0;last="";focusTimes.clear();}
static boolean isSessionActive(){return true;}
static boolean spotifyVisible(){return visible;}
static boolean spotifySearchPageOpen(){return searchOpen;}
static boolean spotifySearchEditorReady(){return editor;}
static SpotifyStep spotifySearchTab(){tabs++;searchOpen=true;return SpotifyStep.CLICKED;}
static SpotifyStep spotifyFocusSearch(){focuses++;focusTimes.add(android.os.SystemClock.now);if(!placeholder)return SpotifyStep.WAIT;editor=true;return SpotifyStep.CLICKED;}
static SpotifyStep spotifyTypeTrack(String title){if(!editor)throw new AssertionError("typed before editor");types++;return SpotifyStep.CLICKED;}
static SpotifyStep spotifyTypeViaInputMethod(String title){throw new AssertionError("unexpected fallback");}
static SpotifyStep spotifySubmitTrackSearch(String title){if(types==0)throw new AssertionError("submitted before typing");submits++;return SpotifyStep.CLICKED;}
static SpotifyStep spotifyControl(SpotifyUiLabels.Target t,SpotifyUiLabels.Target p){if(t==SpotifyUiLabels.Target.LIBRARY)libraryClicks++;if(t==SpotifyUiLabels.Target.RAP_FILTER)filter=true;return SpotifyStep.CLICKED;}
static boolean spotifyLibraryReady(){return library;}
static SpotifyStep spotifyOpenNamedPlaylist(String name){if(!library)throw new AssertionError("looked outside library");return SpotifyStep.CLICKED;}
static boolean spotifyCollectionReady(SpotifyUiLabels.Target p,String name){return collection;}
static SpotifyStep spotifyRetryNamedPlaylistEntry(String name){return SpotifyStep.WAIT;}
static boolean spotifyScrollLibrary(){return false;}
static boolean spotifySearchQueryVisible(String q){return editor;}
static boolean spotifyScrollSongResults(){return false;}
static String spotifySelectSongRow(String query,SpotifyUiLabels.Target page,String name,Set<String> excluded){
if(!rows)return null;if(query!=null&&submits==0)throw new AssertionError("selected before search submission");
if(page==SpotifyUiLabels.Target.LIKED_SONGS&&!filter)throw new AssertionError("rap filter not selected");
for(String key:new String[]{"first song","second song","third song"})if(!excluded.contains(key)){rowClicks++;last=key;return key;}return null;}
}
''',
'com/andriy/jarvis/SpotifyFlowHarness.java': '''package com.andriy.jarvis;
import android.os.Handler;import android.content.Context;
public class SpotifyFlowHarness {
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
static class Result implements SpotifyPlaybackController.Listener {boolean done,ok;public void onProgress(String s){}public void onFinished(boolean success,String s){done=true;ok=success;}}
static SpotifyPlaybackController start(String voice,Result r){SpotifyPlaybackController c=new SpotifyPlaybackController(new Context());c.start(SpotifyMusicCommands.parse(voice),voice,r);return c;}
static void reset(){ScreenControlAccessibilityService.reset();}
public static void main(String[] args){
reset();ScreenControlAccessibilityService.searchOpen=true;Result r=new Result();start("увімкни пісню Skyfall",r);Handler.advance(30000);
check(r.done&&r.ok,"selected-search flow completes");check(ScreenControlAccessibilityService.tabs==1,"selected Search clicked exactly once");check(ScreenControlAccessibilityService.types==1&&ScreenControlAccessibilityService.submits==1,"type and Enter once");check(ScreenControlAccessibilityService.rowClicks==1,"first result once");
// A different controller/entry point must retain the previous search results.
ScreenControlAccessibilityService.editor=false;r=new Result();start("не та пісня",r);Handler.advance(30000);
check(r.ok&&ScreenControlAccessibilityService.last.equals("second song"),"next result across controller instances");
ScreenControlAccessibilityService.editor=false;r=new Result();start("інша",r);Handler.advance(30000);check(ScreenControlAccessibilityService.last.equals("third song"),"third result after second correction");
reset();ScreenControlAccessibilityService.placeholder=false;r=new Result();SpotifyPlaybackController waiting=start("увімкни пісню Океан",r);Handler.advance(30000);
check(!r.done&&ScreenControlAccessibilityService.tabs==2,"unselected Search retried once when placeholder missing");
check(ScreenControlAccessibilityService.types==0&&ScreenControlAccessibilityService.rowClicks==0,"no typing or row taps before field");
check(ScreenControlAccessibilityService.focusTimes.get(1)-ScreenControlAccessibilityService.focusTimes.get(0)==4000,"first missing-field retry after four seconds");
check(ScreenControlAccessibilityService.focusTimes.get(2)-ScreenControlAccessibilityService.focusTimes.get(1)==2000,"later retries every two seconds");
ScreenControlAccessibilityService.placeholder=true;Handler.advance(20000);check(r.ok,"late placeholder recovers without another command");
reset();ScreenControlAccessibilityService.searchOpen=true;ScreenControlAccessibilityService.placeholder=false;r=new Result();waiting=start("увімкни пісню Rain",r);Handler.advance(20000);
check(ScreenControlAccessibilityService.tabs==1,"selected Search is not clicked again while waiting");waiting.cancel();int taps=ScreenControlAccessibilityService.tabs;Handler.advance(20000);
check(r.done&&!r.ok&&ScreenControlAccessibilityService.tabs==taps,"cancel removes pending retries");
reset();r=new Result();start("увімкни плейлист Рок",r);Handler.advance(30000);check(r.ok&&ScreenControlAccessibilityService.libraryClicks==1&&ScreenControlAccessibilityService.rowClicks==1,"playlist always taps Library then a song");
reset();r=new Result();start("увімкни реп",r);Handler.advance(30000);check(r.ok&&ScreenControlAccessibilityService.filter&&ScreenControlAccessibilityService.rowClicks==1,"rap taps filtered song");
reset();ScreenControlAccessibilityService.library=false;r=new Result();waiting=start("увімкни плейлист Рок",r);Handler.advance(20000);check(!r.done&&ScreenControlAccessibilityService.rowClicks==0,"library must actually open before finding playlist");waiting.cancel();
check(JarvisPhoneModeManager.acquired==JarvisPhoneModeManager.released,"all automatic leases released");check(!JarvisActionNotice.messages.isEmpty(),"phone progress emitted independently of caller");
System.out.println("PASS Spotify controller: selected/unselected tabs, late field, first/next results, library gating, rap, cancellation, leases");
}}
'''
}

def main():
    with tempfile.TemporaryDirectory(prefix='jarvis-flow-') as temp:
        folder = Path(temp)
        paths=[]
        for name, source in SOURCES.items():
            file=folder/name
            file.parent.mkdir(parents=True,exist_ok=True)
            file.write_text(source)
            paths.append(str(file))
        paths += [str(MAIN/f'{name}.java') for name in ('SpotifyPlaybackController','SpotifyMusicCommands','CommandPatterns','SpotifyUiLabels','ScreenTargetMatcher','ScreenControlCommands')]
        compiler=shlex.split(os.environ.get('JARVIS_TEST_JAVAC','javac'))
        subprocess.run(compiler+['-encoding','UTF-8','-d',str(folder/'classes')]+paths,check=True)
        subprocess.run([os.environ.get('JARVIS_TEST_JAVA','java'),'-cp',str(folder/'classes'),'com.andriy.jarvis.SpotifyFlowHarness'],check=True)

if __name__=='__main__': main()
