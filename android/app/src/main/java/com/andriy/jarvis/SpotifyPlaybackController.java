package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
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

    private enum Stage {
        WAIT_APP, OPEN_LIBRARY, OPEN_COLLECTION, CHOOSE_RAP, PLAY, VERIFY_PLAY,
        OPEN_SEARCH, VERIFY_SEARCH_LINK, OPEN_SEARCH_TAB,
        FOCUS_SEARCH, TYPE_SEARCH, SELECT_TRACK
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::tick;
    private SpotifyMusicCommands.Kind requested;
    private String namedPlaylist;
    private String namedTrack;
    private Listener listener;
    private Stage stage;
    private long stageStart;
    private int scrolled;
    private boolean active;
    private JarvisPhoneModeManager.Lease temporaryControl;
    private SpotifyAppRemoteBridge directBridge;
    private boolean bypassDirectOnce;
    private boolean playlistPlayRetried;

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
        if (kind == SpotifyMusicCommands.Kind.OPEN) {
            active = true;
            listener = callback;
            requested = kind;
            final Listener owner = callback;
            JarvisWearPhoneSession.launchApp(context,"Spotify",(ok,detail) -> {
                if (active && listener == owner) finish(ok,detail);
            });
            return;
        }
        // Direct API was unavailable; screen automation needs an unlocked phone.
        JarvisPhoneModeManager.Lease lease = JarvisPhoneModeManager.acquireForCommand(context);
        if (!lease.granted) {
            if (callback != null) callback.onFinished(false,lease.message);
            return;
        }
        try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } catch (Exception error) {
            JarvisPhoneModeManager.releaseForCommand(lease);
            if (callback != null) callback.onFinished(false,"Android не дозволив відкрити Spotify.");
            return;
        }
        temporaryControl = lease;
        requested = kind;
        namedPlaylist = named == null ? null : named.name;
        this.namedTrack = namedTrack;
        scrolled = 0;
        playlistPlayRetried = false;
        listener = callback;
        active = true;
        move(Stage.WAIT_APP, "Режим «Телефон»: очікую відкриття Spotify...");
        handler.post(poll);
    }

    public void cancel() {
        if (!active) return;
        finish(false, "Послідовність Spotify скасовано.");
    }

    /** Avoid brittle nav tabs by using Spotify's own in-app search URI.
     * Restrict ACTION_VIEW to com.spotify.music: never launch a browser.
     * If the installed Spotify build rejects the scheme, fall back to
     * verified visible Search tab controls. */
    private boolean openDirectSpotifySearch(String title) {
        if(title==null || title.trim().isEmpty() || title.length()>100)return false;
        Intent search=new Intent(Intent.ACTION_VIEW,
            Uri.parse("spotify:search:"+Uri.encode(title.trim())));
        search.setPackage(PACKAGE);
        search.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if(search.resolveActivity(context.getPackageManager())==null)
            return false;
        try {
            context.startActivity(search);
            return true;
        } catch (RuntimeException rejected) {
            return false;
        }
    }

    private void move(Stage next, String message) {
        stage = next;
        stageStart = SystemClock.elapsedRealtime();
        if (listener != null) listener.onProgress(message);
    }

    private void tick() {
        if (!active || stage == null) return;
        if (!JarvisPhoneScreenGuard.available(context)) {
            finish(false,JarvisPhoneScreenGuard.MESSAGE); return;
        }
        if (!ScreenControlAccessibilityService.isSessionActive()) {
            finish(false, "Керування екраном вимкнулося. Послідовність зупинена.");
            return;
        }
        long age = SystemClock.elapsedRealtime() - stageStart;
        long limit = requested == SpotifyMusicCommands.Kind.PLAYLIST &&
            stage == Stage.OPEN_COLLECTION ? NAMED_FIND_TIMEOUT_MS :
            requested == SpotifyMusicCommands.Kind.TRACK &&
            stage == Stage.SELECT_TRACK ? NAMED_FIND_TIMEOUT_MS : STAGE_TIMEOUT_MS;
        if (age > limit) {
            String reason;
            switch (stage) {
                case WAIT_APP:
                    reason = "Spotify не відкрився або екран заблокований.";
                    break;
                case OPEN_LIBRARY:
                    reason = "Не знайшов вкладку «Моя бібліотека» у Spotify.";
                    break;
                case OPEN_SEARCH:
                case VERIFY_SEARCH_LINK:
                case OPEN_SEARCH_TAB:
                case FOCUS_SEARCH:
                    reason = "Не вдалося відкрити пошук Spotify. Спробуй вручну відкрити вкладку «Пошук» та повторити команду.";
                    break;
                case TYPE_SEARCH:
                    reason = "Не вдалося ввести назву пісні в пошук Spotify.";
                    break;
                case SELECT_TRACK:
                    reason = "Не знайшов точну назву пісні «"+namedTrack+
                        "» у результатах Spotify. Спробуй додати виконавця.";
                    break;
                case VERIFY_PLAY:
                    reason = "Натиснув Play, але Spotify не підтвердив відтворення. " +
                        "Перевір, чи музика грає та чи не обмежений акаунт.";
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
                if(requested==SpotifyMusicCommands.Kind.TRACK)
                    move(Stage.OPEN_SEARCH,"Відкриваю пошук Spotify для пісні «"+namedTrack+"»...");
                else
                    move(Stage.OPEN_LIBRARY,"Відкриваю «Моя бібліотека»...");
                break;
            case OPEN_SEARCH:
                if(openDirectSpotifySearch(namedTrack))
                    move(Stage.VERIFY_SEARCH_LINK,
                        "Відкриваю Spotify одразу з пошуком «"+namedTrack+"»...");
                else
                    move(Stage.OPEN_SEARCH_TAB,
                        "Прямий перехід недоступний. Шукаю вкладку «Пошук»...");
                break;
            case VERIFY_SEARCH_LINK:
                if(ScreenControlAccessibilityService.spotifySearchQueryVisible(namedTrack))
                    move(Stage.SELECT_TRACK,"Пошук відкритий. Обираю точний результат...");
                else if(age>=4300L)
                    move(Stage.OPEN_SEARCH_TAB,
                        "Spotify не підтвердив прямий пошук. Відкриваю вкладку вручну...");
                break;
            case OPEN_SEARCH_TAB:
                if(ScreenControlAccessibilityService.spotifySearchQueryVisible(namedTrack))
                    move(Stage.SELECT_TRACK,"Знайшов потрібний пошуковий запит...");
                else if(successful(ScreenControlAccessibilityService.spotifySearchTab()))
                    move(Stage.FOCUS_SEARCH,"Відкриваю поле пошуку пісні...");
                break;
            case FOCUS_SEARCH:
                if(successful(ScreenControlAccessibilityService.spotifyFocusSearch()))
                    move(Stage.TYPE_SEARCH,"Вводжу назву пісні «"+namedTrack+"»...");
                break;
            case TYPE_SEARCH:
                if(successful(ScreenControlAccessibilityService.spotifyTypeTrack(namedTrack)))
                    move(Stage.SELECT_TRACK,"Пошук пісні «"+namedTrack+
                        "». Очікую відповідний трек...");
                break;
            case SELECT_TRACK:
                if(age>=1250L &&
                    successful(ScreenControlAccessibilityService.spotifySelectTrack(namedTrack)))
                    finish(true,"Натиснув пісню «"+namedTrack+
                        "» у результатах Spotify на телефоні. Перевіряй відтворення.");
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
                // Opening a playlist is NOT playing it. Only stop when a
                // large visible Pause has been observed, never merely because
                // ACTION_CLICK/dispatchGesture accepted a press.
                if(age<1050L)break;
                ScreenControlAccessibilityService.SpotifyStep result =
                    requested == SpotifyMusicCommands.Kind.PLAYLIST
                        ? ScreenControlAccessibilityService.spotifyNamedPlaylistControl(
                            SpotifyUiLabels.Target.PLAY,namedPlaylist,false)
                        : ScreenControlAccessibilityService.spotifyPlaylistPlay(pageTarget());
                if(result==ScreenControlAccessibilityService.SpotifyStep.ALREADY)
                    finish(true,"Spotify показує Pause: плейлист уже відтворюється.");
                else if(result==ScreenControlAccessibilityService.SpotifyStep.CLICKED)
                    move(Stage.VERIFY_PLAY,
                        "Натиснув Play. Перевіряю початок відтворення плейлиста...");
                break;
            }
            case VERIFY_PLAY: {
                // Do not tap again: a second press could toggle Play -> Pause.
                ScreenControlAccessibilityService.SpotifyStep state=
                    ScreenControlAccessibilityService.spotifyPlaylistPlaybackState(
                        requested==SpotifyMusicCommands.Kind.PLAYLIST
                            ? null : pageTarget(),
                        requested==SpotifyMusicCommands.Kind.PLAYLIST
                            ? namedPlaylist : null);
                if(state==ScreenControlAccessibilityService.SpotifyStep.ALREADY) {
                    finish(true,"Spotify показує Pause: плейлист почав відтворюватися.");
                } else if(!playlistPlayRetried && age>=2300L) {
                    // ACTION_CLICK can report success without Spotify
                    // actually acting. Retry once as a real touch ONLY when
                    // the explicit large Play still exists on THIS playlist.
                    // Never tap an unlabeled button again: it may be Pause.
                    playlistPlayRetried=true;
                    ScreenControlAccessibilityService.SpotifyStep retried=
                        ScreenControlAccessibilityService.spotifyRetryExplicitPlaylistPlay(
                            requested==SpotifyMusicCommands.Kind.PLAYLIST
                                ? null : pageTarget(),
                            requested==SpotifyMusicCommands.Kind.PLAYLIST
                                ? namedPlaylist : null);
                    if(retried==ScreenControlAccessibilityService.SpotifyStep.ALREADY)
                        finish(true,"Spotify підтвердив відтворення плейлиста.");
                    else if(retried==ScreenControlAccessibilityService.SpotifyStep.CLICKED)
                        move(Stage.VERIFY_PLAY,
                            "Повторно натиснув видиму Play на сторінці плейлиста. Чекаю Pause...");
                } else if(age>=4800L) {
                    finish(false,"Не вдалося підтвердити відтворення плейлиста у Spotify. " +
                        "Музику не вважаю запущеною. Перевір Pixel та кнопку Play.");
                }
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
        namedTrack = null;
        JarvisPhoneModeManager.Lease lease = temporaryControl;
        temporaryControl = null;
        // Always restore Jarvis' normal face, even on errors/cancellation.
        // User-started or watch-owned sessions remain active.
        JarvisPhoneModeManager.releaseForCommand(lease, success);
        if (cb != null) cb.onFinished(success, summary);
    }
}
