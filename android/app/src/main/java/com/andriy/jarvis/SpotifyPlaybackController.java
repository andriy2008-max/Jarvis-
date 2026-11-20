package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/**
 * Spotify user-requested, ordered automation. Every stage re-reads the actual
 * accessible Spotify screen; song selection uses the owner-calibrated first row.
 * Only works while the user has enabled a screen-control session.
 */
public final class SpotifyPlaybackController {
    public interface Listener {
        void onProgress(String text);
        void onFinished(boolean success, String description);
    }

    private static final String PACKAGE = "com.spotify.music";
    private static final SpotifyResultCursor RESULT_CURSOR=new SpotifyResultCursor();
    private static final long POLL_MS = 430L;
    private static final long STAGE_TIMEOUT_MS = 20000L;
    private static final int MAX_LIBRARY_SCROLLS = 4;
    private static final int MAX_NAMED_SCROLLS = 16;
    private static final long NAMED_FIND_TIMEOUT_MS = 52000L;

    private enum Stage {
        WAIT_APP, OPEN_LIBRARY, OPEN_COLLECTION, WAIT_PLAYLIST_PAGE,
        CHOOSE_RAP, PLAY, VERIFY_PLAY,
        OPEN_SEARCH_TAB, FOCUS_SEARCH, VERIFY_SEARCH_EDITOR, TYPE_SEARCH, SUBMIT_SEARCH,
        COORD_SELECT_FIRST, VERIFY_TRACK_PLAY
    }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::tick;
    private SpotifyMusicCommands.Kind requested;
    private int resultOrdinal;
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
    private boolean playlistRowRetried;
    private boolean coordinatePlaylistPlayUsed;
    private int searchPromptAttempts;
    private int calibratedPromptTaps;
    private boolean calibratedPromptPending;

    public SpotifyPlaybackController(Context context) {
        this.context = context;
    }

    public boolean isRunning() { return active; }

    public void start(SpotifyMusicCommands.Kind kind, Listener callback) {
        start(kind,null,callback);
    }

    public void start(SpotifyMusicCommands.Kind kind,String voice,Listener callback) {
        if (kind == null) return;
        if(kind==SpotifyMusicCommands.Kind.NEXT_RESULT) {
            nextSong(callback);return;
        }
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
        // Named songs follow the explicit text-driven sequence, even with a saved URI.
        boolean allowDirect=!bypassDirectOnce && kind!=SpotifyMusicCommands.Kind.TRACK;
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
        resultOrdinal=0;
        if(kind==SpotifyMusicCommands.Kind.TRACK &&
            !namedTrack.equals(RESULT_CURSOR.query(SystemClock.elapsedRealtime())))
            RESULT_CURSOR.begin(namedTrack,SystemClock.elapsedRealtime());
        scrolled = 0;
        playlistPlayRetried = false;
        playlistRowRetried = false;
        coordinatePlaylistPlayUsed = false;
        searchPromptAttempts = 0;
        calibratedPromptTaps = 0;
        calibratedPromptPending = false;
        listener = callback;
        active = true;
        move(Stage.WAIT_APP, "Режим «Телефон»: очікую відкриття Spotify...");
        handler.post(poll);
    }

    /** Repeat without a new dictated title: the same last search query is
     * reopened, then the next row is tapped. Each utterance advances by one.
     * Both phone routes share the in-memory cursor, never the Watch cursor. */
    private void nextSong(Listener callback) {
        if(active) {
            if(callback!=null)callback.onFinished(false,
                "Spotify ще виконує попередню команду.");
            return;
        }
        long now=SystemClock.elapsedRealtime();
        String title=RESULT_CURSOR.query(now);
        int index=RESULT_CURSOR.next(now);
        if(title==null || index<0) {
            if(callback!=null)callback.onFinished(false,
                "Немає попереднього списку пісень або він застарів. "+
                "Спочатку скажи «Увімкни пісню [назва]».");
            return;
        }
        start(SpotifyMusicCommands.Kind.TRACK,
            "увімкни пісню "+title,callback);
        if(active && requested==SpotifyMusicCommands.Kind.TRACK)
            resultOrdinal=index;
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

    /** Never let an invalidated Spotify Accessibility node or a changing
     * Compose hierarchy crash the entire Jarvis process on the UI thread. */
    private void tick() {
        if(!active)return;
        try {
            tickStep();
        } catch(RuntimeException failure) {
            Stage failedStage=stage;
            android.util.Log.e("JARVIS_SPOTIFY",
                "Spotify step failed: "+(failedStage==null?"unknown":failedStage.name()),
                failure);
            if(!active)return;
            String stageName=failedStage==null?"unknown":failedStage.name();
            // Do not include track titles or other private screen data in logs.
            try {
                finish(false,"Spotify: помилка доступу до екрана на етапі "+
                    stageName+" ("+failure.getClass().getSimpleName()+
                    "). Послідовність зупинено; повторних натискань не робив.");
            } catch(RuntimeException cleanupFailure) {
                android.util.Log.e("JARVIS_SPOTIFY",
                    "Spotify cleanup failed",cleanupFailure);
                handler.removeCallbacks(poll);
                active=false;
                stage=null;
                listener=null;
            }
        }
    }

    private void tickStep() {
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
            stage == Stage.OPEN_COLLECTION ? NAMED_FIND_TIMEOUT_MS : STAGE_TIMEOUT_MS;
        if (age > limit) {
            String reason;
            switch (stage) {
                case WAIT_APP:
                    reason = requested == SpotifyMusicCommands.Kind.TRACK
                        ? "Spotify не підтверджено на передньому плані. " +
                          "Android не дозволив відкрити екран застосунку або " +
                          "служба спеціальних можливостей не бачить його вікно. " +
                          "Відкрий Spotify на Pixel і повтори команду."
                        : "Spotify не відкрився або екран заблокований.";
                    break;
                case OPEN_LIBRARY:
                    reason = "Не знайшов вкладку «Моя бібліотека» у Spotify.";
                    break;
                case OPEN_SEARCH_TAB:
                case FOCUS_SEARCH:
                case VERIFY_SEARCH_EDITOR:
                    reason = "Не вдалося натиснути рядок «Що ви хочете послухати?» у Spotify. "+
                        "Перевір, чи він видно на вкладці «Пошук».";
                    break;
                case TYPE_SEARCH:
                    reason = "Не вдалося ввести назву пісні в пошук Spotify.";
                    break;
                case SUBMIT_SEARCH:
                    reason = "Не вдалося підтвердити пошук пісні клавішею Пошук/Enter.";
                    break;
                case COORD_SELECT_FIRST:
                    reason = "Spotify не показав доступний результат №"+
                        (resultOrdinal+1)+" або заблокував натискання. "+
                        "Повтори команду після відкриття списку пісень.";
                    break;
                case VERIFY_TRACK_PLAY:
                    reason = "Натискання виконано, але назва пісні не з'явилася у мініпрогравачі Spotify.";
                    break;
                case VERIFY_PLAY:
                    reason = "Натиснув Play, але Spotify не підтвердив відтворення. " +
                        "Перевір, чи музика грає та чи не обмежений акаунт.";
                    break;
                case WAIT_PLAYLIST_PAGE:
                    reason = "Spotify не перейшов із бібліотеки на сторінку плейлиста «" +
                        namedPlaylist + "», хоча прийняв натискання.";
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
        // Named songs require Spotify foreground identity even during typing.
        boolean appIdentityEnough=requested==SpotifyMusicCommands.Kind.TRACK ||
            (requested==SpotifyMusicCommands.Kind.PLAYLIST &&
             (stage==Stage.WAIT_APP || stage==Stage.OPEN_LIBRARY));
        boolean spotifySearchIme=requested==SpotifyMusicCommands.Kind.TRACK &&
            (stage==Stage.VERIFY_SEARCH_EDITOR || stage==Stage.TYPE_SEARCH ||
             stage==Stage.SUBMIT_SEARCH) &&
            ScreenControlAccessibilityService.spotifyOwnsInputMethod();
        if (!(spotifySearchIme ||
              (appIdentityEnough ? ScreenControlAccessibilityService.spotifyCoordinateReady()
                : ScreenControlAccessibilityService.spotifyVisible()))) {
            handler.postDelayed(poll, POLL_MS);
            return;
        }
        switch (stage) {
            case WAIT_APP:
                if(requested==SpotifyMusicCommands.Kind.TRACK) {
                    if(age>=1500L)
                        move(Stage.OPEN_SEARCH_TAB,"Натискаю «Пошук» у Spotify...");
                } else if(requested==SpotifyMusicCommands.Kind.PLAYLIST) {
                    // Allow Spotify's foreground window identity even if it
                    // hides its accessible tree on the home screen.
                    if(age>=1500L &&
                        ScreenControlAccessibilityService.spotifyCoordinateReady())
                        move(Stage.OPEN_LIBRARY,
                            "Відкриваю «Моя бібліотека» за координатами...");
                } else
                    move(Stage.OPEN_LIBRARY,"Відкриваю «Моя бібліотека»...");
                break;
            case OPEN_SEARCH_TAB:
                if(successful(ScreenControlAccessibilityService.spotifySearchTab()))
                    move(Stage.FOCUS_SEARCH,"Натискаю «Що ви хочете послухати?»...");
                break;
            case FOCUS_SEARCH:
                // The screenshot shows Spotify's visible Search box despite
                // inaccessible Compose labels. Do not poll ACTION_CLICK
                // forever: after normal lookup fails, use the user-calibrated
                // Pixel point with a strict Spotify-only, portrait guard.
                if(calibratedPromptPending)break;
                if(ScreenControlAccessibilityService.spotifySearchEditorReady()) {
                    move(Stage.TYPE_SEARCH,"Вводжу назву пісні «"+namedTrack+"»...");
                } else if(successful(ScreenControlAccessibilityService.spotifyFocusSearch())) {
                    searchPromptAttempts++;
                    move(Stage.VERIFY_SEARCH_EDITOR,
                        "Перевіряю відкриття поля Spotify перед введенням назви…");
                } else if(age>=1800L && calibratedPromptTaps<2) {
                    tapPixelSearchPrompt(age);
                } else if(age>=4500L && calibratedPromptTaps>=2) {
                    finish(false,"Дві спроби натиснути поле Spotify не відкрили "+
                        "пошук. Зупинено без випадкових дій.");
                }
                break;
            case VERIFY_SEARCH_EDITOR:
                if(ScreenControlAccessibilityService.spotifySearchEditorReady()) {
                    move(Stage.TYPE_SEARCH,"Вводжу назву пісні «"+namedTrack+"»...");
                } else if(age>=2600L) {
                    if(searchPromptAttempts<3)
                        move(Stage.FOCUS_SEARCH,
                            "Повторно натискаю рядок пошуку Spotify…");
                    else if(calibratedPromptTaps<2)
                        move(Stage.FOCUS_SEARCH,
                            "Spotify не відкрив поле; пробую координату з Pixel…");
                    else
                        finish(false,"Spotify не відкрив поле після перевірених "+
                            "натискань. Дімон не вводив текст у сторонню програму.");
                }
                break;
            case TYPE_SEARCH: {
                ScreenControlAccessibilityService.SpotifyStep typed=
                    ScreenControlAccessibilityService.spotifyTypeTrack(namedTrack);
                if(!successful(typed))
                    typed=ScreenControlAccessibilityService.spotifyTypeViaInputMethod(namedTrack);
                if(successful(typed))
                    move(Stage.SUBMIT_SEARCH,"Натискаю Пошук/Enter на клавіатурі...");
                break;
            }
            case SUBMIT_SEARCH:
                if(successful(ScreenControlAccessibilityService.spotifySubmitTrackSearch(namedTrack)))
                    move(Stage.COORD_SELECT_FIRST,"Очікую результати пошуку Spotify...");
                break;
            case COORD_SELECT_FIRST: {
                // Spotify's first result moves when a "More releases" section,
                // filters, or a mini-player changes the layout. Do not assume
                // Y=510 or that the foreground app is already displaying songs.
                if(age<1100L)break;
                ScreenControlAccessibilityService.SpotifyStep selected=
                    ScreenControlAccessibilityService.spotifyTapTrackResultAtIndex(
                        namedTrack,resultOrdinal,age,(ok,detail)->{
                            if(!active || stage!=Stage.COORD_SELECT_FIRST)return;
                            if(!ok) {
                                finish(false,"Spotify не зміг натиснути першу пісню: "+detail);
                                return;
                            }
                            RESULT_CURSOR.selected(resultOrdinal,
                                SystemClock.elapsedRealtime());
                            move(Stage.VERIFY_TRACK_PLAY,
                                "Натиснув результат №"+(resultOrdinal+1)+
                                ". Перевіряю відтворення Spotify...");
                            handler.postDelayed(poll,650L);
                        });
                if(selected==ScreenControlAccessibilityService.SpotifyStep.WAIT)
                    handler.postDelayed(poll,2000L);
                else if(selected==ScreenControlAccessibilityService.SpotifyStep.BLOCKED &&
                        active && stage==Stage.COORD_SELECT_FIRST)
                    finish(false,"Spotify не дозволив вибрати першу пісню.");
                // CLICKED: gesture completion callback owns the next stage;
                // no repeated taps while it is pending.
                return;
            }
            case VERIFY_TRACK_PLAY:
                if(resultOrdinal>0) {
                    // Follow-up rows can be different recordings or artists.
                    // We only know that Android accepted the row tap; the
                    // mini-player will not necessarily show the original title.
                    if(age>=1600L)finish(true,"Натиснув результат №"+
                        (resultOrdinal+1)+" зі списку «"+namedTrack+
                        "». Скажи «Інша пісня» для наступного.");
                } else if(ScreenControlAccessibilityService.spotifyMiniPlayerHasTrack(namedTrack)) {
                    finish(true,"Spotify показує пісню «"+namedTrack+
                        "» у мініпрогравачі. Перевір, чи є звук. "+
                        "Скажи «Інша пісня» для наступного результату.");
                } else if(age>=6500L) {
                    finish(false,"Перший результат натиснуто, але Spotify не "+
                        "підтвердив його появу в мініпрогравачі. "+
                        "Можна сказати «Інша пісня» для другого результату.");
                }
                break;
            case WAIT_PLAYLIST_PAGE: {
                ScreenControlAccessibilityService.SpotifyStep ready=
                    ScreenControlAccessibilityService.spotifyNamedPlaylistDetailReady(
                        namedPlaylist);
                if(ready==ScreenControlAccessibilityService.SpotifyStep.ALREADY) {
                    move(Stage.PLAY,"Плейлист відкрито. Запускаю велику кнопку Play...");
                } else if(!playlistRowRetried && age>=2600L) {
                    // Spotify Compose may acknowledge ACTION_CLICK without
                    // leaving My Library. Retry the exact named row once,
                    // using its visible label, not guessed screen coordinates.
                    playlistRowRetried=true;
                    ScreenControlAccessibilityService.SpotifyStep again=
                        ScreenControlAccessibilityService.spotifyRetryNamedPlaylistEntry(
                            namedPlaylist);
                    if(again==ScreenControlAccessibilityService.SpotifyStep.ALREADY)
                        move(Stage.PLAY,"Відкрився плейлист. Натискаю Play...");
                    else if(again==ScreenControlAccessibilityService.SpotifyStep.CLICKED)
                        move(Stage.WAIT_PLAYLIST_PAGE,
                            "Повторно відкриваю плейлист дотиком. Чекаю сторінку...");
                } else if(age>=8500L) {
                    finish(false,"Не вдалося відкрити сторінку плейлиста «"+
                        namedPlaylist+"». Play не натискав.");
                    return;
                }
                break;
            }
            case OPEN_LIBRARY:
                if(requested==SpotifyMusicCommands.Kind.PLAYLIST) {
                    if(!ScreenControlAccessibilityService.spotifyCoordinateReady()) {
                        finish(false,"Spotify не відкритий для натискання «Моя бібліотека».");
                        return;
                    }
                    ScreenControlAccessibilityService.spotifyTapCalibrated(665,2224,
                        (ok,detail)->{
                            if(!active || stage!=Stage.OPEN_LIBRARY)return;
                            if(!ok) {
                                finish(false,"Не відкрив «Моя бібліотека»: "+detail);
                                return;
                            }
                            move(Stage.OPEN_COLLECTION,
                                "Відкрив «Моя бібліотека». Шукаю плейлист «"+
                                namedPlaylist+"» прокручуванням...");
                            handler.postDelayed(poll,800L);
                        });
                    return;
                }
                if (successful(ScreenControlAccessibilityService.spotifyControl(
                    SpotifyUiLabels.Target.LIBRARY, null)))
                    move(Stage.OPEN_COLLECTION, requested == SpotifyMusicCommands.Kind.RAP
                        ? "Чекаю «Пісні, що сподобалися»..."
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
                    // A successful click merely opens the library row. Do
                    // not infer that the destination page has rendered.
                    if(requested==SpotifyMusicCommands.Kind.PLAYLIST &&
                        found==ScreenControlAccessibilityService.SpotifyStep.CLICKED)
                        move(Stage.WAIT_PLAYLIST_PAGE,
                            "Натиснув плейлист. Очікую його сторінку...");
                    else
                        move(requested == SpotifyMusicCommands.Kind.RAP ?
                            Stage.CHOOSE_RAP : Stage.PLAY,
                            requested == SpotifyMusicCommands.Kind.RAP
                                ? "Чекаю кнопку жанру «Реп»..."
                                : "Сторінка плейлиста відкрита. Натискаю Play...");
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
                if(requested==SpotifyMusicCommands.Kind.PLAYLIST) {
                    // User-calibrated green Play. A verified exact playlist
                    // header must remain visible; do not tap a random page.
                    if(ScreenControlAccessibilityService.spotifyPlaylistPlaybackState(
                        null,namedPlaylist)==ScreenControlAccessibilityService.SpotifyStep.ALREADY) {
                        finish(true,"Потрібний плейлист уже відтворюється.");
                        return;
                    }
                    if(ScreenControlAccessibilityService.spotifyNamedPlaylistDetailReady(
                        namedPlaylist)!=ScreenControlAccessibilityService.SpotifyStep.ALREADY) {
                        finish(false,"Не можу підтвердити сторінку плейлиста «"+
                            namedPlaylist+"». Play не натискав.");
                        return;
                    }
                    ScreenControlAccessibilityService.spotifyTapNamedPlaylistPlay(
                        namedPlaylist,(ok,detail)->{
                            if(!active || stage!=Stage.PLAY)return;
                            if(!ok) {
                                finish(false,"Не вдалося натиснути Play (977,1291): "+detail);
                                return;
                            }
                            coordinatePlaylistPlayUsed=true;
                            move(Stage.VERIFY_PLAY,
                                "Натиснув Play за координатами (977,1291). "+
                                "Перевіряю, чи музика грає...");
                            handler.postDelayed(poll,POLL_MS);
                        });
                    return;
                }
                ScreenControlAccessibilityService.SpotifyStep result =
                    ScreenControlAccessibilityService.spotifyPlaylistPlay(pageTarget());
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
                } else if(coordinatePlaylistPlayUsed) {
                    // Never tap the calibrated point again: after playback
                    // starts, the same location may be Pause.
                    if(age>=5000L)
                        finish(false,"Play натиснуто на плейлисті «"+namedPlaylist+
                            "», але Spotify не підтвердив відтворення. "+
                            "Повторно не натискав, щоб не поставити на паузу.");
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

    /** Only after the Spotify Search tab was opened, and only in the
     * user-confirmed phone layout. A callback, not merely dispatchGesture(),
     * owns verification; the keyboard must actually open next. */
    private void tapPixelSearchPrompt(long focusAgeMs) {
        if(calibratedPromptPending || !active || stage!=Stage.FOCUS_SEARCH)return;
        final int previous=calibratedPromptTaps++;
        calibratedPromptPending=true;
        if(listener!=null)listener.onProgress(
            "Натискаю білий рядок пошуку Spotify по центру Pixel ("+
            calibratedPromptTaps+"/2)…");
        ScreenControlAccessibilityService.spotifyTapPhoneSearchPrompt(
            previous,focusAgeMs,(ok,detail)->{
                if(!active || stage!=Stage.FOCUS_SEARCH)return;
                calibratedPromptPending=false;
                if(ok) {
                    searchPromptAttempts++;
                    move(Stage.VERIFY_SEARCH_EDITOR,
                        "Натиснув рядок Spotify. Перевіряю появу клавіатури…");
                } else if(calibratedPromptTaps>=2) {
                    finish(false,"Spotify не прийняв натискання поля: "+detail);
                } else {
                    // Retry only after the next poll; never dispatch two
                    // gestures concurrently or behind a different app.
                    stageStart=SystemClock.elapsedRealtime()-1800L;
                    if(listener!=null)listener.onProgress(
                        "Перша спроба натискання Spotify не спрацювала.");
                }
            });
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
        coordinatePlaylistPlayUsed = false;
        calibratedPromptPending = false;
        JarvisPhoneModeManager.Lease lease = temporaryControl;
        temporaryControl = null;
        // Always restore Jarvis' normal face, even on errors/cancellation.
        // User-started or watch-owned sessions remain active.
        JarvisPhoneModeManager.releaseForCommand(lease, success);
        if (cb != null) cb.onFinished(success, summary);
    }
}
