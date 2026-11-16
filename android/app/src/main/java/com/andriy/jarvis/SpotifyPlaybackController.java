package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Ordered music commands. Missing controls are re-read after 4s, then every 2s.
 * Accepted actions are never blindly repeated. Cancellation releases the lease. */
public final class SpotifyPlaybackController {
    public interface Listener {
        void onProgress(String text);
        void onFinished(boolean success, String description);
    }
    private enum Stage { WAIT_APP, LIBRARY, WAIT_LIBRARY, COLLECTION, DETAIL, RAP,
        SEARCH_TAB, SEARCH_FIELD, TYPE, SUBMIT, SONG }
    private static SpotifyPlaybackController owner;
    // Shared by activity, overlay and watch entry points; never persisted/logged.
    private static String lastQuery;
    private static final java.util.Set<String> played = new java.util.HashSet<>();
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::tick;
    private Listener listener;
    private JarvisPhoneModeManager.Lease lease;
    private SpotifyMusicCommands.Kind kind;
    private Stage stage;
    private String query, playlist;
    private boolean active, searchWasOpen, searchRetried, fieldTapped, retry;
    private long stageAt;
    private int scrolls;
    private SpotifyAppRemoteBridge direct;

    public SpotifyPlaybackController(Context context) { this.context=context.getApplicationContext(); }
    public boolean isRunning() { return active; }
    public void start(SpotifyMusicCommands.Kind kind, Listener cb) { start(kind,null,cb); }
    public void start(SpotifyMusicCommands.Kind requested,String voice,Listener cb) {
        if(requested==null)return;
        if(owner!=null && owner.active) {
            report(cb,false,"Spotify вже виконує команду. Скажи «Скасуй Spotify».");return;
        }
        boolean next=requested==SpotifyMusicCommands.Kind.NEXT_RESULT;
        query=next?lastQuery:SpotifyMusicCommands.namedTrack(voice);
        SpotifyMusicCommands.NamedPlaylist named=SpotifyMusicCommands.namedPlaylist(voice);
        playlist=named==null?null:named.name;
        if((next || requested==SpotifyMusicCommands.Kind.TRACK) && query==null) {
            report(cb,false,"Спочатку скажи «Увімкни пісню» та її назву.");return;
        }
        if(requested==SpotifyMusicCommands.Kind.PLAYLIST && playlist==null) {
            report(cb,false,"Назви плейлист після «Увімкни плейлист».");return;
        }
        Intent launch=context.getPackageManager().getLaunchIntentForPackage("com.spotify.music");
        if(launch==null) {report(cb,false,"Не знайшов Spotify на телефоні.");return;}
        listener=cb; kind=next?SpotifyMusicCommands.Kind.TRACK:requested;
        if(kind==SpotifyMusicCommands.Kind.OPEN) {
            active=true;owner=this;
            // Keep the existing explicitly configured resume capability.
            String client=SpotifyDirectSettings.clientId(context);
            if(!client.isEmpty()) {
                direct=new SpotifyAppRemoteBridge(context,client,null,true,(ok,detail)->{
                    if(!active)return;
                    if(ok)finish(true,detail);
                    else JarvisWearPhoneSession.launchApp(context,"Spotify",(opened,info)->{
                        if(active)finish(opened,info);
                    });
                });
                progress("Відкриваю Spotify та відновлюю музику...");
                direct.connect();
            } else JarvisWearPhoneSession.launchApp(context,"Spotify",(ok,detail)->{
                if(active)finish(ok,detail);
            });
            return;
        }
        lease=JarvisPhoneModeManager.acquireForCommand(context);
        if(!lease.granted) {report(cb,false,lease.message);lease=null;return;}
        try {context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));}
        catch(RuntimeException e) {JarvisPhoneModeManager.releaseForCommand(lease);lease=null;
            report(cb,false,"Android не дозволив відкрити Spotify.");return;}
        if(kind==SpotifyMusicCommands.Kind.TRACK && !next) {lastQuery=query;played.clear();}
        if(kind!=SpotifyMusicCommands.Kind.TRACK) {lastQuery=null;played.clear();}
        active=true;owner=this;searchRetried=false;fieldTapped=false;scrolls=0;
        move(Stage.WAIT_APP,"Відкриваю Spotify. Очікую готовий екран...");
        handler.postDelayed(poll,4000L);
    }
    public void cancel() {
        SpotifyPlaybackController running=owner;
        if(running!=null && running.active)running.finish(false,"Команду Spotify скасовано.");
    }
    private void report(Listener cb,boolean ok,String text) {
        JarvisActionNotice.show(context,text);
        if(cb!=null)cb.onFinished(ok,text);
    }
    private void progress(String text) {
        JarvisActionNotice.show(context,text);
        if(listener!=null)listener.onProgress(text);
    }
    private void move(Stage next,String text) {
        stage=next;stageAt=SystemClock.elapsedRealtime();retry=false;progress(text);
    }
    private static boolean accepted(ScreenControlAccessibilityService.SpotifyStep step) {
        return step==ScreenControlAccessibilityService.SpotifyStep.CLICKED ||
            step==ScreenControlAccessibilityService.SpotifyStep.ALREADY;
    }
    private void tick() {
        if(!active)return;
        try {
            if(!JarvisPhoneScreenGuard.available(context) ||
               !ScreenControlAccessibilityService.isSessionActive()) {
                finish(false,"Керування зупинено. Розблокуй телефон і перевір спеціальні можливості.");return;
            }
            Stage before=stage;
            if(ScreenControlAccessibilityService.spotifyVisible())step();
            if(!active)return;
            long delay;
            if(stage!=before) delay=stage==Stage.SEARCH_FIELD?2000L:600L;
            else {
                delay=retry?2000L:4000L;retry=true;
                progress("Spotify: очікую "+stageLabel()+". Перевірю ще через "+delay/1000+" с. Скасування: «Скасуй Spotify».");
            }
            handler.postDelayed(poll,delay);
        } catch(RuntimeException e) {
            android.util.Log.e("JARVIS_SPOTIFY","Screen changed during "+stage,e);
            finish(false,"Spotify: екран змінився під час дії. Повтори команду.");
        }
    }
    private String stageLabel() {
        switch(stage) {
            case WAIT_APP:return "відкриття застосунку";
            case WAIT_LIBRARY:case LIBRARY:return "вкладку «Моя бібліотека»";
            case COLLECTION:return "плейлист у бібліотеці";
            case DETAIL:return "сторінку плейлиста";
            case RAP:return "фільтр «Реп»";
            case SEARCH_TAB:return "вкладку «Пошук»";
            case SEARCH_FIELD:case TYPE:return "поле пошуку";
            case SUBMIT:return "готовність пошуку";
            default:return "рядок пісні";
        }
    }
    private void step() {
        switch(stage) {
            case WAIT_APP:
                move(kind==SpotifyMusicCommands.Kind.TRACK?Stage.SEARCH_TAB:Stage.LIBRARY,
                    kind==SpotifyMusicCommands.Kind.TRACK?"Натискаю вкладку «Пошук»...":"Натискаю «Моя бібліотека», навіть якщо вона вже вибрана...");break;
            case SEARCH_TAB:
                searchWasOpen=ScreenControlAccessibilityService.spotifySearchPageOpen();
                if(accepted(ScreenControlAccessibilityService.spotifySearchTab()))
                    move(Stage.SEARCH_FIELD,"Шукаю поле «Що ви хочете послухати?»...");
                break;
            case SEARCH_FIELD:
                if(ScreenControlAccessibilityService.spotifySearchEditorReady()) {
                    move(Stage.TYPE,"Вводжу назву пісні...");break;
                }
                if(!fieldTapped && accepted(ScreenControlAccessibilityService.spotifyFocusSearch())) {
                    fieldTapped=true;break;
                }
                // The second tab press is only needed when entering Search
                // from another tab and its field has not appeared after 2s.
                if(!fieldTapped && !searchWasOpen && !searchRetried &&
                    SystemClock.elapsedRealtime()-stageAt>=2000L) {
                    if(accepted(ScreenControlAccessibilityService.spotifySearchTab())) {
                        searchRetried=true;progress("Поле не з'явилося. Повторно натискаю «Пошук».");
                    }
                } else if(fieldTapped) {
                    // Re-focus a visible placeholder if the accepted click did
                    // not open an editor. Never repeat the navigation tab here.
                    ScreenControlAccessibilityService.spotifyFocusSearch();
                }
                break;
            case TYPE:
                ScreenControlAccessibilityService.SpotifyStep typed=ScreenControlAccessibilityService.spotifyTypeTrack(query);
                if(!accepted(typed))typed=ScreenControlAccessibilityService.spotifyTypeViaInputMethod(query);
                if(accepted(typed))move(Stage.SUBMIT,"Назву введено. Натискаю Пошук/Enter...");
                break;
            case SUBMIT:
                if(accepted(ScreenControlAccessibilityService.spotifySubmitTrackSearch(query)))
                    move(Stage.SONG,played.isEmpty()?"Очікую першу пісню в результатах...":"Шукаю наступну пісню в цих результатах...");
                break;
            case LIBRARY:
                if(accepted(ScreenControlAccessibilityService.spotifyControl(SpotifyUiLabels.Target.LIBRARY,null)))
                    move(Stage.WAIT_LIBRARY,"Очікую сторінку Моєї бібліотеки...");
                break;
            case WAIT_LIBRARY:
                if(ScreenControlAccessibilityService.spotifyLibraryReady())
                    move(Stage.COLLECTION,"Шукаю потрібний плейлист саме в Моїй бібліотеці...");
                else if(SystemClock.elapsedRealtime()-stageAt>=4000L)
                    ScreenControlAccessibilityService.spotifyControl(SpotifyUiLabels.Target.LIBRARY,null);
                break;
            case COLLECTION:
                ScreenControlAccessibilityService.SpotifyStep found=kind==SpotifyMusicCommands.Kind.PLAYLIST
                    ?ScreenControlAccessibilityService.spotifyOpenNamedPlaylist(playlist)
                    :ScreenControlAccessibilityService.spotifyControl(page(),null);
                if(accepted(found))move(Stage.DETAIL,"Натиснув плейлист. Очікую його сторінку...");
                else if(SystemClock.elapsedRealtime()-stageAt>=4000L && scrolls<20 &&
                    ScreenControlAccessibilityService.spotifyScrollLibrary()) {
                    scrolls++;progress("Прокручую Мою бібліотеку: "+scrolls);
                }
                break;
            case DETAIL:
                if(ScreenControlAccessibilityService.spotifyCollectionReady(page(),playlist))
                    move(kind==SpotifyMusicCommands.Kind.RAP?Stage.RAP:Stage.SONG,
                        kind==SpotifyMusicCommands.Kind.RAP?"Обираю фільтр «Реп»...":"Плейлист відкритий. Шукаю рядок пісні...");
                else if(kind==SpotifyMusicCommands.Kind.PLAYLIST && SystemClock.elapsedRealtime()-stageAt>=4000L)
                    ScreenControlAccessibilityService.spotifyRetryNamedPlaylistEntry(playlist);
                break;
            case RAP:
                if(accepted(ScreenControlAccessibilityService.spotifyControl(SpotifyUiLabels.Target.RAP_FILTER,page())))
                    move(Stage.SONG,"Фільтр «Реп» вибрано. Натискаю пісню зі списку...");
                break;
            case SONG:
                if(SystemClock.elapsedRealtime()-stageAt<2000L)break;
                String key=ScreenControlAccessibilityService.spotifySelectSongRow(
                    kind==SpotifyMusicCommands.Kind.TRACK?query:null,page(),playlist,
                    kind==SpotifyMusicCommands.Kind.TRACK?played:java.util.Collections.emptySet());
                if(key!=null) {
                    if(kind==SpotifyMusicCommands.Kind.TRACK)played.add(key);
                    finish(true,"Натиснув "+(played.size()>1?"наступну":"першу")+" пісню в списку Spotify. Перевір відтворення.");
                } else if(kind==SpotifyMusicCommands.Kind.TRACK && !played.isEmpty() &&
                    ScreenControlAccessibilityService.spotifySearchQueryVisible(query)) {
                    if(ScreenControlAccessibilityService.spotifyScrollSongResults())
                        progress("Шукаю наступний результат нижче...");
                }
                break;
        }
    }
    private SpotifyUiLabels.Target page() {
        return kind==SpotifyMusicCommands.Kind.PLAYLIST || kind==SpotifyMusicCommands.Kind.TRACK?null:
            kind==SpotifyMusicCommands.Kind.RAP?SpotifyUiLabels.Target.LIKED_SONGS:SpotifyUiLabels.Target.UKR_MUSIC;
    }
    private void finish(boolean ok,String text) {
        handler.removeCallbacks(poll);active=false;if(owner==this)owner=null;
        if(direct!=null){direct.cancel();direct=null;}
        JarvisPhoneModeManager.releaseForCommand(lease,ok);lease=null;
        Listener cb=listener;listener=null;report(cb,ok,text);
    }
}
