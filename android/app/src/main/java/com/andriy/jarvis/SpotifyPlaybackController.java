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

    private enum Stage { WAIT_APP, OPEN_LIBRARY, OPEN_COLLECTION, CHOOSE_RAP, SHUFFLE, PLAY }

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::tick;
    private SpotifyMusicCommands.Kind requested;
    private Listener listener;
    private Stage stage;
    private long stageStart;
    private int scrolled;
    private boolean active;
    private JarvisPhoneModeManager.Lease temporaryControl;

    public SpotifyPlaybackController(Context context) {
        this.context = context;
    }

    public boolean isRunning() { return active; }

    public void start(SpotifyMusicCommands.Kind kind, Listener callback) {
        if (kind == null) return;
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
        try {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(launch);
        } catch (Exception error) {
            JarvisPhoneModeManager.releaseForCommand(lease);
            if (callback != null) callback.onFinished(false,
                "Android не дозволив відкрити Spotify.");
            return;
        }
        if (kind == SpotifyMusicCommands.Kind.OPEN) {
            if (callback != null) callback.onFinished(true, "Відкриваю Spotify.");
            return;
        }
        temporaryControl = lease;
        requested = kind;
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
        if (age > STAGE_TIMEOUT_MS) {
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
                        : "Не знайшов плейлист «Укр муз» у бібліотеці.";
                    break;
                case CHOOSE_RAP:
                    reason = "Не знайшов жанрову кнопку «Реп» у вподобаних піснях. " +
                        "Spotify може не показувати цей фільтр у твоєму акаунті.";
                    break;
                case SHUFFLE:
                    reason = "Не знайшов кнопку перемішування на сторінці добірки. " +
                        "Значок може не мати доступного опису у Spotify.";
                    break;
                default:
                    reason = "Не знайшов велику зелену кнопку відтворення поряд із перемішуванням.";
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
                        : "Шукаю плейлист «Укр муз»...");
                break;
            case OPEN_COLLECTION:
                SpotifyUiLabels.Target item = requested == SpotifyMusicCommands.Kind.RAP
                    ? SpotifyUiLabels.Target.LIKED_SONGS
                    : SpotifyUiLabels.Target.UKR_MUSIC;
                if (successful(ScreenControlAccessibilityService.spotifyControl(item,null))) {
                    move(requested == SpotifyMusicCommands.Kind.RAP ?
                        Stage.CHOOSE_RAP : Stage.SHUFFLE,
                        requested == SpotifyMusicCommands.Kind.RAP
                            ? "Чекаю кнопку жанру «Реп»..."
                            : "Чекаю кнопки перемішування в «Укр муз»...");
                } else if (requested == SpotifyMusicCommands.Kind.UKRAINIAN &&
                           age >= 5500L + scrolled * 2600L &&
                           scrolled < MAX_LIBRARY_SCROLLS &&
                           ScreenControlAccessibilityService.spotifyScrollLibrary()) {
                    scrolled++;
                    if (listener != null) listener.onProgress(
                        "Переглядаю бібліотеку Spotify: " + scrolled + "/" + MAX_LIBRARY_SCROLLS);
                }
                break;
            case CHOOSE_RAP:
                if (successful(ScreenControlAccessibilityService.spotifyControl(
                    SpotifyUiLabels.Target.RAP_FILTER,
                    SpotifyUiLabels.Target.LIKED_SONGS)))
                    move(Stage.SHUFFLE, "Знайшов «Реп». Чекаю кнопку перемішування...");
                break;
            case SHUFFLE:
                if (successful(ScreenControlAccessibilityService.spotifyControl(
                    SpotifyUiLabels.Target.SHUFFLE, pageTarget())))
                    move(Stage.PLAY, "Перемішування увімкнено. Чекаю зелену кнопку Play...");
                break;
            case PLAY:
                if (successful(ScreenControlAccessibilityService.spotifyControl(
                    SpotifyUiLabels.Target.PLAY, pageTarget())))
                    finish(true, requested == SpotifyMusicCommands.Kind.RAP
                        ? "Натиснув відтворення «Реп» у Spotify."
                        : "Натиснув відтворення плейлиста «Укр муз» у Spotify.");
                break;
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
        active = false;
        Listener cb = listener;
        listener = null;
        stage = null;
        JarvisPhoneModeManager.Lease lease = temporaryControl;
        temporaryControl = null;
        // Always restore Jarvis' normal face, even on errors/cancellation.
        // User-started or watch-owned sessions remain active.
        JarvisPhoneModeManager.releaseForCommand(lease);
        if (cb != null) cb.onFinished(success, summary);
    }
}
