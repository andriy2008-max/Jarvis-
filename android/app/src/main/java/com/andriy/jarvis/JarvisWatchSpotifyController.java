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
        if(kind!=SpotifyMusicCommands.Kind.TRACK &&
           kind!=SpotifyMusicCommands.Kind.PLAYLIST) {
            completion.finished(false,"На Watch7 підтримано пошук пісні або плейлиста.");
            return;
        }
        if(title==null || title.trim().isEmpty() || title.length()>75) {
            completion.finished(false,"Назва пісні або плейлиста не розпізнана.");
            return;
        }
        if(!JarvisWatchScreenService.available(context)) {
            completion.finished(false,"Увімкни на годиннику службу "+
                "«Дімон — екран годинника» у спеціальних можливостях.");
            return;
        }
        Intent intent=context.getPackageManager().getLaunchIntentForPackage(PACKAGE);
        if(intent==null) {
            completion.finished(false,"Установи Spotify на Galaxy Watch7 та увійди в акаунт.");
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            context.startActivity(intent);
        } catch(RuntimeException failure) {
            completion.finished(false,"Wear OS не вдалося відкрити Spotify.");
            return;
        }
        Session session=new Session(context.getApplicationContext(),kind,title,completion);
        session.status("Відкриваю Spotify на годиннику…");
        MAIN.postDelayed(()->session.run(JarvisWatchScreenService.SpotifyStep.OPEN_SEARCH),
            3000L);
    }

    private static final class Session {
        final Context context;
        final SpotifyMusicCommands.Kind kind;
        final String title;
        final JarvisWatchLocalController.Callback callback;
        final long deadline=SystemClock.elapsedRealtime()+TOTAL_TIMEOUT_MS;
        long searchStart=SystemClock.elapsedRealtime();
        long stageStart=SystemClock.elapsedRealtime();
        boolean revealed;
        boolean finished;

        Session(Context context,SpotifyMusicCommands.Kind kind,String title,
                JarvisWatchLocalController.Callback callback) {
            this.context=context;this.kind=kind;this.title=title;
            this.callback=callback;
        }

        void status(String detail) {
            if(finished)return;
            Toast.makeText(context,detail,Toast.LENGTH_SHORT).show();
        }

        void done(boolean ok,String detail) {
            if(finished)return;
            finished=true;
            Toast.makeText(context,detail,Toast.LENGTH_SHORT).show();
            callback.finished(ok,detail);
        }

        void run(JarvisWatchScreenService.SpotifyStep step) {
            if(finished)return;
            long now=SystemClock.elapsedRealtime();
            if(now>deadline) {
                done(false,"Spotify не завершив пошук на годиннику. "+
                    "Перевір екран, інтернет і авторизацію.");
                return;
            }
            // If Spotify opens on the player, reveal the new Wear OS menu once,
            // but never swipe blindly before the app's own window is visible.
            if(step==JarvisWatchScreenService.SpotifyStep.OPEN_SEARCH &&
               !revealed && now-searchStart>=7000L &&
               JarvisWatchScreenService.foreground(PACKAGE)) {
                revealed=true;
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
                    return JarvisWatchScreenService.SpotifyStep.SUBMIT_QUERY;
                case SUBMIT_QUERY:
                    return JarvisWatchScreenService.SpotifyStep.SELECT_RESULT;
                case SELECT_RESULT:
                    return kind==SpotifyMusicCommands.Kind.PLAYLIST
                        ? JarvisWatchScreenService.SpotifyStep.PLAY_PLAYLIST:null;
                case PLAY_PLAYLIST:
                    return JarvisWatchScreenService.SpotifyStep.VERIFY_PLAYLIST;
                default:return null;
            }
        }

        private void perform(JarvisWatchScreenService.SpotifyStep step,
                             JarvisWatchScreenService.SpotifyStep next) {
            JarvisWatchScreenService.Result result;
            try {
                result=JarvisWatchScreenService.spotifyStep(step,title,
                    kind==SpotifyMusicCommands.Kind.PLAYLIST,(ok,detail)->{
                    if(finished)return;
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
                    advance(step,next);
                    break;
                case RUNNING:
                    // Gesture callback advances exactly once on completion.
                    break;
                case WAIT:
                    if(step==JarvisWatchScreenService.SpotifyStep.PLAY_PLAYLIST &&
                       SystemClock.elapsedRealtime()-stageStart>12000L) {
                        done(false,"Плейлист відкрито, але кнопку відтворення "+
                            "на годиннику не знайдено.");
                    } else if(step==JarvisWatchScreenService.SpotifyStep.VERIFY_PLAYLIST &&
                              SystemClock.elapsedRealtime()-stageStart>9000L) {
                        done(false,"Натиснув відтворення плейлиста, але Spotify "+
                            "не підтвердив запуск музики.");
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
            if(finished)return;
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
                1600L:step==JarvisWatchScreenService.SpotifyStep.SELECT_RESULT?
                1200L:650L;
            MAIN.postDelayed(()->run(next),delay);
        }
    }
}
