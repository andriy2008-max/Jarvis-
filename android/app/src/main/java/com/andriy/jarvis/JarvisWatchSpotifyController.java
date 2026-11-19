package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Watch-only, user-triggered Spotify search. No phone Data Layer, Spotify
 * credentials, screen coordinates or external AI-generated UI actions.
 */
public final class JarvisWatchSpotifyController {
    private static final String PACKAGE="com.spotify.music";
    private static final long TOTAL_TIMEOUT_MS=57000L;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    private JarvisWatchSpotifyController() {}

    public static void play(Context context,SpotifyMusicCommands.Kind kind,
                            String title,JarvisWatchLocalController.Callback completion) {
        JarvisWatchSpotifyTrace.mark(context,"PLAY_ENTERED");
        if(kind!=SpotifyMusicCommands.Kind.TRACK &&
           kind!=SpotifyMusicCommands.Kind.PLAYLIST &&
           kind!=SpotifyMusicCommands.Kind.RAP &&
           kind!=SpotifyMusicCommands.Kind.UKRAINIAN) {
            completion.finished(false,"Команда Spotify не підтримується на Watch7.");
            return;
        }
        if(title==null || title.trim().isEmpty() || title.length()>75) {
            completion.finished(false,"Назва пісні або плейлиста не розпізнана.");
            return;
        }
        JarvisWatchSpotifyTrace.mark(context,"CHECK_ACCESSIBILITY");
        if(!JarvisWatchScreenService.available(context)) {
            JarvisWatchSpotifyTrace.mark(context,"ACCESSIBILITY_UNAVAILABLE");
            completion.finished(false,"Увімкни на годиннику службу "+
                "«Дімон — екран годинника» у спеціальних можливостях.");
            return;
        }
        JarvisWatchSpotifyTrace.mark(context,"CHECK_SPOTIFY_INSTALL");
        Intent intent=context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if(intent==null) {
            JarvisWatchSpotifyTrace.mark(context,"SPOTIFY_LAUNCHER_NOT_FOUND");
            completion.finished(false,"Установи Spotify на Galaxy Watch7 та увійди в акаунт.");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            JarvisWatchSpotifyTrace.mark(context,"START_ACTIVITY_REQUESTED");
            JarvisDiagnostics.event(JarvisDiagnostics.Event.EXTERNAL_LAUNCH,1);
            context.startActivity(intent);
            JarvisWatchSpotifyTrace.mark(context,"START_ACTIVITY_RETURNED");
        } catch(RuntimeException failure) {
            android.util.Log.e("DIMON_WATCH_SPOTIFY","Failed to launch Spotify",failure);
            JarvisWatchSpotifyTrace.mark(context,"START_ACTIVITY_FAILED");
            completion.finished(false,"Wear OS не вдалося відкрити Spotify.");
            return;
        }
        Session session=new Session(context.getApplicationContext(),kind,title,completion);
        session.status("Відкриваю Spotify на годиннику…");
        // Do not begin tapping until Spotify actually owns the foreground.
        // This also distinguishes failed app launch from a UI automation failure.
        MAIN.postDelayed(()->session.waitForSpotify(SystemClock.elapsedRealtime()+13000L),
            900L);
    }

    private static final class Session {
        final Context context;
        final SpotifyMusicCommands.Kind kind;
        final String title;
        final JarvisWatchLocalController.Callback callback;
        final long deadline=SystemClock.elapsedRealtime()+TOTAL_TIMEOUT_MS;
        long searchStart=SystemClock.elapsedRealtime();
        long stageStart=SystemClock.elapsedRealtime();
        int revealAttempts;
        long lastRevealAt;
        int queryRetries;
        boolean finished;
        final JarvisCallbackGate steps=new JarvisCallbackGate();

        Session(Context context,SpotifyMusicCommands.Kind kind,String title,
                JarvisWatchLocalController.Callback callback) {
            this.context=context;this.kind=kind;this.title=title;
            this.callback=callback;
            MAIN.postDelayed(()->done(false,"Час очікування Spotify минув."),TOTAL_TIMEOUT_MS);
        }

        void status(String detail) {
            if(finished || !callback.active())return;
            Toast.makeText(context,detail,Toast.LENGTH_SHORT).show();
        }

        void done(boolean ok,String detail) {
            if(finished || !callback.active())return;
            finished=true;
            steps.cancel();
            JarvisWatchSpotifyTrace.finish(context,ok);
            Toast.makeText(context,detail,Toast.LENGTH_SHORT).show();
            callback.finished(ok,detail);
        }

        void waitForSpotify(long expires) {
            if(finished || !callback.active())return;
            if(JarvisWatchScreenService.foreground(PACKAGE)) {
                JarvisWatchSpotifyTrace.mark(context,"SPOTIFY_FOREGROUND");
                run(JarvisWatchScreenService.SpotifyStep.OPEN_SEARCH);
                return;
            }
            if(SystemClock.elapsedRealtime()>=expires) {
                JarvisWatchSpotifyTrace.mark(context,"SPOTIFY_NOT_FOREGROUND");
                done(false,"Spotify не з'явився на екрані Watch7. "+
                    "Скажи «Відкрий Spotify на годиннику» для перевірки.");
                return;
            }
            MAIN.postDelayed(()->waitForSpotify(expires),2000L);
        }

        void run(JarvisWatchScreenService.SpotifyStep step) {
            if(finished || !callback.active())return;
            JarvisWatchSpotifyTrace.mark(context,"STEP_"+step.name());
            long now=SystemClock.elapsedRealtime();
            if(now>deadline) {
                done(false,"Spotify не завершив пошук на годиннику. "+
                    "Перевір екран, інтернет і авторизацію.");
                return;
            }
            // If Spotify opens on the player, reveal the new Wear OS menu once,
            // but never swipe blindly before the app's own window is visible.
            if(step==JarvisWatchScreenService.SpotifyStep.OPEN_SEARCH &&
               revealAttempts<3 && now-searchStart>=3800L &&
               (lastRevealAt==0 || now-lastRevealAt>=2400L) &&
               JarvisWatchScreenService.foreground(PACKAGE)) {
                // The original short swipe sometimes left Search below the
                // Spotify navigation fold. Retry only while Search is absent.
                revealAttempts++;
                lastRevealAt=now;
                run(JarvisWatchScreenService.SpotifyStep.REVEAL_NAV);
                return;
            }
            if(step==JarvisWatchScreenService.SpotifyStep.REVEAL_NAV) {
                perform(step,JarvisWatchScreenService.SpotifyStep.OPEN_SEARCH);
                return;
            }
            perform(step,next(step));
        }

        private JarvisWatchScreenService.SpotifyStep next(
            JarvisWatchScreenService.SpotifyStep step) {
            switch(step) {
                case OPEN_SEARCH:
                    return JarvisWatchScreenService.SpotifyStep.FOCUS_SEARCH;
                case FOCUS_SEARCH:
                    return JarvisWatchScreenService.SpotifyStep.SET_QUERY;
                case SET_QUERY:
                    return JarvisWatchScreenService.SpotifyStep.VERIFY_QUERY;
                case VERIFY_QUERY:
                    return JarvisWatchScreenService.SpotifyStep.SUBMIT_QUERY;
                case SUBMIT_QUERY:
                    return JarvisWatchScreenService.SpotifyStep.SELECT_RESULT;
                case SELECT_RESULT:
                    return kind==SpotifyMusicCommands.Kind.TRACK ? null :
                        JarvisWatchScreenService.SpotifyStep.PLAY_PLAYLIST;
                case PLAY_PLAYLIST:
                    return JarvisWatchScreenService.SpotifyStep.VERIFY_PLAYLIST;
                default:return null;
            }
        }

        private void perform(JarvisWatchScreenService.SpotifyStep step,
                             JarvisWatchScreenService.SpotifyStep next) {
            final long stepToken=steps.begin();
            JarvisWatchScreenService.Result result;
            try {
                result=JarvisWatchScreenService.spotifyStep(step,title,
                    kind!=SpotifyMusicCommands.Kind.TRACK,
                    kind==SpotifyMusicCommands.Kind.RAP ||
                    kind==SpotifyMusicCommands.Kind.UKRAINIAN,(ok,detail)->{
                    if(finished || !callback.active() || !steps.finish(stepToken))return;
                    if(!ok) {
                        done(false,"Wear OS скасувала натискання у Spotify.");
                        return;
                    }
                    advance(step,next);
                });
            } catch(RuntimeException failure) {
                // Some Wear OS versions invalidate accessibility nodes during
                // rapid Spotify/IME window transitions. Report a controlled
                // failure instead of crashing the watch's assistant process.
                android.util.Log.e("DIMON_WATCH_SPOTIFY",
                    "Failed Spotify stage "+step,failure);
                done(false,"Spotify змінив екран під час пошуку. "+
                    "Дімон залишився активним; спробуй ще раз.");
                return;
            }
            switch(result) {
                case DONE:
                    if(steps.finish(stepToken))advance(step,next);
                    break;
                case RUNNING:
                    // Gesture callback advances exactly once on completion.
                    break;
                case WAIT:
                    steps.finish(stepToken);
                    if(step==JarvisWatchScreenService.SpotifyStep.PLAY_PLAYLIST &&
                       SystemClock.elapsedRealtime()-stageStart>12000L) {
                        done(false,"Плейлист відкрито, але кнопку відтворення "+
                            "на годиннику не знайдено.");
                    } else if(step==JarvisWatchScreenService.SpotifyStep.VERIFY_PLAYLIST &&
                              SystemClock.elapsedRealtime()-stageStart>9000L) {
                        done(false,"Натиснув відтворення плейлиста, але Spotify "+
                            "не підтвердив запуск музики.");
                    } else if(step==JarvisWatchScreenService.SpotifyStep.VERIFY_QUERY &&
                              SystemClock.elapsedRealtime()-stageStart>8500L) {
                        if(++queryRetries<=2) {
                            // ACTION_SET_TEXT is idempotent. Retry only if the
                            // actual Spotify search field still lacks the text.
                            MAIN.postDelayed(()->run(JarvisWatchScreenService.SpotifyStep.SET_QUERY),2000L);
                        } else done(false,"Клавіатура Spotify відкрилася, але "+
                            "назва пісні не з'явилася в полі пошуку Watch7.");
                    } else {
                        MAIN.postDelayed(()->run(step),2000L);
                    }
                    break;
                case BLOCKED:
                default:
                    done(false,"Spotify не дозволив цю дію на годиннику.");
            }
        }

        private void advance(JarvisWatchScreenService.SpotifyStep step,
                             JarvisWatchScreenService.SpotifyStep next) {
            if(finished || !callback.active())return;
            if(step==JarvisWatchScreenService.SpotifyStep.SELECT_RESULT &&
               kind==SpotifyMusicCommands.Kind.TRACK) {
                // Accessibility confirms the click, not audio output. Avoid a
                // false claim that Spotify actually began playing.
                done(true,"Вибрано пісню «"+title+"» у Spotify на Watch7. "+
                    "Перевір відтворення.");
                return;
            }
            if(step==JarvisWatchScreenService.SpotifyStep.VERIFY_PLAYLIST) {
                done(true,"Spotify показує відтворення плейлиста «"+title+
                    "» на Watch7.");
                return;
            }
            if(next==null) {
                done(false,"Не вдалося завершити сценарій Spotify.");return;
            }
            if(step==JarvisWatchScreenService.SpotifyStep.OPEN_SEARCH)
                status("Відкрито пошук Spotify.");
            if(step==JarvisWatchScreenService.SpotifyStep.SET_QUERY)
                status("Шукаю «"+title+"»…");
            if(step==JarvisWatchScreenService.SpotifyStep.SELECT_RESULT)
                status("Плейлист знайдено. Запускаю відтворення…");
            stageStart=SystemClock.elapsedRealtime();
            long delay=step==JarvisWatchScreenService.SpotifyStep.SUBMIT_QUERY?
                1600L:step==JarvisWatchScreenService.SpotifyStep.REVEAL_NAV?
                1100L:step==JarvisWatchScreenService.SpotifyStep.SET_QUERY?
                1100L:step==JarvisWatchScreenService.SpotifyStep.SELECT_RESULT?
                1200L:650L;
            MAIN.postDelayed(()->run(next),delay);
        }
    }
}
