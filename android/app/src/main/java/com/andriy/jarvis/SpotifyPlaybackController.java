package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Spotify user-requested, ordered automation. Every stage re-reads the actual
 * accessible Spotify screen and stops instead of tapping unknown coordinates.
 * Only works while the user has enabled a screen-control session.
 */
public final class SpotifyPlaybackController {
    public interface Listener {
        void onProgress(String text);
        void onFinished(boolean success, String description);
    }

    private static final String PACKAGE = "com.spotify.music";
    private static final long POLL_MS = 430L;
    private static final long STAGE_TIMEOUT_MS = 20000L;
    private static final int MAX_LIBRARY_SCROLLS = 4;
    private static final int MAX_NAMED_SCROLLS = 16;
    private static final long NAMED_FIND_TIMEOUT_MS = 52000L;

    private enum Stage { WAIT_APP, OPEN_LIBRARY, OPEN_COLLECTION, CHOOSE_RAP, PLAY }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::tick;
    private SpotifyMusicCommands.Kind requested;
    private String namedPlaylist;
    private Listener listener;
    private Stage stage;
    private long stageStart;
    private int scrolled;
    private boolean active;
    private JarvisPhoneModeManager.Lease temporaryControl;
    private SpotifyAppRemoteBridge directBridge;
    private boolean bypassDirectOnce;

    public SpotifyPlaybackController(Context context) {
        this.context = context;
    }

    public boolean isRunning() { return active; }

    public void start(SpotifyMusicCommands.Kind kind, Listener callback) {
        start(kind,null,callback);
    }

    public void start(SpotifyMusicCommands.Kind kind,String voice,Listener callback) {
        if (kind == null) return;
        SpotifyMusicCommands.NamedPlaylist named = kind == SpotifyMusicCommands.Kind.PLAYLIST
            ? SpotifyMusicCommands.namedPlaylist(voice) : null;
        String namedTrack=kind==SpotifyMusicCommands.Kind.TRACK
            ? SpotifyMusicCommands.namedTrack(voice) : null;
        if(kind==SpotifyMusicCommands.Kind.TRACK && namedTrack==null) {
            if(callback!=null)callback.onFinished(false,
                "Назви пісню після команди «Увімкни пісню».");
            return;
        }
        if (kind == SpotifyMusicCommands.Kind.PLAYLIST && named == null) {
            if (callback!=null) callback.onFinished(false,"Назви плейлист після команди «Увімкни плейлист».");
            return;
        }
        if (active) {
            if (callback != null) callback.onFinished(false,
                "Spotify вже виконує попередню команду. Скажи «Скасуй Spotify».");
            return;
        }
        // Check Spotify installation BEFORE activating any Accessibility session.
        Intent launch = context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if (launch == null) {
            if (callback != null) callback.onFinished(false,
                "Не знайшов Spotify на телефоні. Спочатку встанови його та увійди в акаунт.");
            return;
        }
        // Direct App Remote is tried before requesting Accessibility.
        // An explicit user-saved URI is required; never guess IDs from names.
        boolean allowDirect=!bypassDirectOnce;
        bypassDirectOnce=false;
        String alias=kind==SpotifyMusicCommands.Kind.TRACK
            ? namedTrack : named==null?null:named.name;
        String directUri=allowDirect ? SpotifyDirectSettings.resolve(context,kind,
            alias) : null;
        boolean resumeDirect=allowDirect &&
            kind==SpotifyMusicCommands.Kind.OPEN &&
            !SpotifyDirectSettings.clientId(context).isEmpty();
        if(kind==SpotifyMusicCommands.Kind.TRACK && directUri==null) {
            if(callback!=null)callback.onFinished(false,
                "Пісню «"+namedTrack+"» ще не прив'язано до Spotify-посилання. " +
                "Відкрий 🎵 Spotify · прямий запуск у Джарвісі.");
            return;
        }
        if(directUri!=null || resumeDirect) {
            active=true;
            listener=callback;
            requested=kind;
            namedPlaylist=alias;
            final SpotifyMusicCommands.Kind fallbackKind=kind;
            final String fallbackVoice=voice;
            directBridge=new SpotifyAppRemoteBridge(context,
                SpotifyDirectSettings.clientId(context),directUri,resumeDirect,(ok,detail)->{
                    if(!active)return;
                    directBridge=null;
                    if(ok) {
                        finish(true,"Spotify прийняв команду прямого відтворення без натискання Play.");
                    } else if(fallbackKind==SpotifyMusicCommands.Kind.TRACK) {
                        finish(false,detail+" Пісню не запускав, інші кнопки не натискав.");
                    } else {
                        active=false;
                        listener=null;
                        requested=null;
                        namedPlaylist=null;
                        if(callback!=null)callback.onProgress(
                            detail+" Пробую через інтерфейс Spotify...");
                        // Do not reconnect indefinitely after an App Remote error.
                        bypassDirectOnce=true;
                        start(fallbackKind,fallbackVoice,callback);
                    }
                });
            directBridge.connect();
            return;
        }
        JarvisPhoneModeManager.Lease lease = null;
        if (kind != SpotifyMusicCommands.Kind.OPEN) {
            // An explicit voice request grants permission to use the
            // ALREADY user-enabled Android Accessibility service for this task.
            // A manual/watch session is borrowed and NEVER closed by this task.
            lease = JarvisPhoneModeManager.acquireForCommand(context);
            if (!lease.granted) {
                if (callback != null) callback.onFinished(false, lease.message);
                return;
            }
        }
        long openFeedback = kind == SpotifyMusicCommands.Kind.OPEN && JarvisPhoneMode.isActive()
            ? JarvisControlFeedback.begin() : 0L;
        try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } catch (Exception error) {
            JarvisControlFeedback.finish(openFeedback,false,SystemClock.uptimeMillis());
            JarvisPhoneModeManager.releaseForCommand(lease);
            if (callback != null) callback.onFinished(false,
                "Android не дозволив відкрити Spotify.");
            return;
        }
        if (kind == SpotifyMusicCommands.Kind.OPEN) {
            JarvisControlFeedback.finish(openFeedback,true,SystemClock.uptimeMillis());
            if (callback != null) callback.onFinished(true, "Відкриваю Spotify.");
            return;
        }
        temporaryControl = lease;
        requested = kind;
        namedPlaylist = named == null ? null : named.name;
        scrolled = 0;
        listener = callback;
        active = true;
        move(Stage.WAIT_APP, "Режим «Телефон»: очікую відкриття Spotify...");
        handler.post(poll);
    }

    public void cancel() {
        if (!active) return;
        finish(false, "Послідовність Spotify скасовано.");
    }

    private void move(Stage next, String message) {
        stage = next;
        stageStart = SystemClock.elapsedRealtime();
        if (listener != null) listener.onProgress(message);
    }

    private void tick() {
        if (!active || stage == null) return;
        if (!ScreenControlAccessibilityService.isSessionActive()) {
            finish(false, "Керування екраном вимкнулося. Послідовність зупинена.");
            return;
        }
        long age = SystemClock.elapsedRealtime() - stageStart;
        long limit = requested == SpotifyMusicCommands.Kind.PLAYLIST &&
            stage == Stage.OPEN_COLLECTION ? NAMED_FIND_TIMEOUT_MS : STAGE_TIMEOUT_MS;
        if (age > limit) {
            String reason;
            switch (stage) {
                case WAIT_APP:
                    reason = "Spotify не відкрився або екран заблокований.";
                    break;
                case OPEN_LIBRARY:
                    reason = "Не знайшов вкладку «Моя бібліотека» у Spotify.";
                    break;
                case OPEN_COLLECTION:
                    reason = requested == SpotifyMusicCommands.Kind.RAP
                        ? "Не знайшов «Пісні, що сподобалися» в бібліотеці."
                        : requested == SpotifyMusicCommands.Kind.PLAYLIST
                            ? "Не знайшов плейлист «"+namedPlaylist+"» у Моїй бібліотеці."
                            : "Не знайшов плейлист «Укр муз» у бібліотеці.";
                    break;
                case CHOOSE_RAP:
                    reason = "Не знайшов жанрову кнопку «Реп» у вподобаних піснях. " +
                        "Spotify може не показувати цей фільтр у твоєму акаунті.";
                    break;
                default:
                    reason = "Не знайшов велику зелену кнопку Play на сторінці плейлиста.";
            }
            finish(false, reason + " Нічого навмання не натискав.");
            return;
        }
        if (!ScreenControlAccessibilityService.spotifyVisible()) {
            handler.postDelayed(poll, POLL_MS);
            return;
        }
        switch (stage) {
            case WAIT_APP:
                move(Stage.OPEN_LIBRARY, "Відкриваю «Моя бібліотека»...");
                break;
            case OPEN_LIBRARY:
                if (successful(ScreenControlAccessibilityService.spotifyControl(
                    SpotifyUiLabels.Target.LIBRARY, null)))
                    move(Stage.OPEN_COLLECTION, requested == SpotifyMusicCommands.Kind.RAP
                        ? "Чекаю «Пісні, що сподобалися»..."
                        : requested == SpotifyMusicCommands.Kind.PLAYLIST
                            ? "Шукаю плейлист «"+namedPlaylist+"» у бібліотеці..."
                            : "Шукаю плейлист «Укр муз»...");
                break;
            case OPEN_COLLECTION: {
                ScreenControlAccessibilityService.SpotifyStep found;
                if (requested == SpotifyMusicCommands.Kind.PLAYLIST) {
                    found = ScreenControlAccessibilityService.spotifyOpenNamedPlaylist(namedPlaylist);
                } else {
                    SpotifyUiLabels.Target item = requested == SpotifyMusicCommands.Kind.RAP
                        ? SpotifyUiLabels.Target.LIKED_SONGS : SpotifyUiLabels.Target.UKR_MUSIC;
                    found = ScreenControlAccessibilityService.spotifyControl(item,null);
                }
                if (successful(found)) {
                    move(requested == SpotifyMusicCommands.Kind.RAP ?
                        Stage.CHOOSE_RAP : Stage.PLAY,
                        requested == SpotifyMusicCommands.Kind.RAP
                            ? "Чекаю кнопку жанру «Реп»..."
                            : "Плейлист відкрито. Натискаю велику зелену Play...");
                } else {
                    boolean canScroll = requested == SpotifyMusicCommands.Kind.UKRAINIAN ||
                        requested == SpotifyMusicCommands.Kind.PLAYLIST;
                    int limitScroll = requested == SpotifyMusicCommands.Kind.PLAYLIST ?
                        MAX_NAMED_SCROLLS : MAX_LIBRARY_SCROLLS;
                    long scrollAfter = requested == SpotifyMusicCommands.Kind.PLAYLIST ?
                        2200L + scrolled * 2500L : 5500L + scrolled * 2600L;
                    if (canScroll && age >= scrollAfter && scrolled < limitScroll &&
                        ScreenControlAccessibilityService.spotifyScrollLibrary()) {
                        scrolled++;
                        if (listener != null) listener.onProgress(
                            "Переглядаю бібліотеку Spotify: "+scrolled+"/"+limitScroll);
                    }
                }
                break;
            }
            case CHOOSE_RAP:
                if (successful(ScreenControlAccessibilityService.spotifyControl(
                    SpotifyUiLabels.Target.RAP_FILTER,
                    SpotifyUiLabels.Target.LIKED_SONGS)))
                    move(Stage.PLAY, "Знайшов «Реп». Натискаю Play без перемішування...");
                break;
            case PLAY: {
                ScreenControlAccessibilityService.SpotifyStep result =
                    requested == SpotifyMusicCommands.Kind.PLAYLIST
                        ? ScreenControlAccessibilityService.spotifyNamedPlaylistControl(
                            SpotifyUiLabels.Target.PLAY,namedPlaylist,false)
                        : ScreenControlAccessibilityService.spotifyControl(
                            SpotifyUiLabels.Target.PLAY,pageTarget());
                if (successful(result))
                    finish(true, requested == SpotifyMusicCommands.Kind.RAP
                        ? "Натиснув відтворення «Реп» у Spotify."
                        : requested == SpotifyMusicCommands.Kind.PLAYLIST
                            ? "Натиснув Play плейлиста «"+namedPlaylist+"» у Spotify."
                            : "Натиснув відтворення плейлиста «Укр муз» у Spotify.");
                break;
            }
        }
        if (active) handler.postDelayed(poll, POLL_MS);
    }

    private SpotifyUiLabels.Target pageTarget() {
        return requested == SpotifyMusicCommands.Kind.RAP
            ? SpotifyUiLabels.Target.LIKED_SONGS : SpotifyUiLabels.Target.UKR_MUSIC;
    }

    private static boolean successful(ScreenControlAccessibilityService.SpotifyStep result) {
        return result == ScreenControlAccessibilityService.SpotifyStep.CLICKED ||
            result == ScreenControlAccessibilityService.SpotifyStep.ALREADY;
    }

    private void finish(boolean success, String summary) {
        handler.removeCallbacks(poll);
        if(directBridge!=null) {
            directBridge.cancel();
            directBridge=null;
        }
        active = false;
        Listener cb = listener;
        listener = null;
        stage = null;
        namedPlaylist = null;
        JarvisPhoneModeManager.Lease lease = temporaryControl;
        temporaryControl = null;
        // Always restore Jarvis' normal face, even on errors/cancellation.
        // User-started or watch-owned sessions remain active.
        JarvisPhoneModeManager.releaseForCommand(lease, success);
        if (cb != null) cb.onFinished(success, summary);
    }
}
