package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Toast;

/**
 * Google Maps actions are performed on the Wear OS device that heard the command.
 * Prefer an inspectable Maps search and the first query-matching suggestion.
 * If the Wear OS Maps UI does not expose its controls, delegate walking
 * navigation to the official Google Maps navigation intent (mode=w).
 * No phone coordinates, screen-coordinate guessing, or automatic route claims.
 */
public final class JarvisWatchMapsController {
    public static final String MAPS_PACKAGE="com.google.android.apps.maps";
    private static final long MAX_MS=60000L;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());

    private JarvisWatchMapsController() {}

    public static void execute(Context context,JarvisWatchMapsCommands.Request request,
                               JarvisWatchLocalController.Callback completion) {
        if(context==null || request==null || completion==null)return;
        if(!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            completion.finished(false,"Google Карти потрібно відкрити на годиннику.");
            return;
        }
        if(context.getPackageManager().getLaunchIntentForPackage(MAPS_PACKAGE)==null) {
            completion.finished(false,"Установи Google Карти на Galaxy Watch7.");
            return;
        }
        Context app=context.getApplicationContext();
        Session session=new Session(app,request,completion);
        session.begin(context);
    }

    private static final class Session {
        final Context context;
        final JarvisWatchMapsCommands.Request request;
        final JarvisWatchLocalController.Callback completion;
        final long deadline=SystemClock.elapsedRealtime()+MAX_MS;
        long stageSince=SystemClock.elapsedRealtime();
        boolean finished;
        boolean directionsOpened;
        boolean shortSwipeCompleted;
        final JarvisCallbackGate steps=new JarvisCallbackGate();

        Session(Context app,JarvisWatchMapsCommands.Request req,
                JarvisWatchLocalController.Callback callback) {
            context=app;request=req;completion=callback;
            MAIN.postDelayed(()->done(false,"Час очікування Google Карт минув."),MAX_MS);
        }

        void progress(String message) {
            if(!finished)Toast.makeText(context,message,Toast.LENGTH_SHORT).show();
        }
        void done(boolean ok,String message) {
            if(finished || !completion.active())return;
            finished=true;
            steps.cancel();
            progressFinal(message);
            completion.finished(ok,message);
        }
        void progressFinal(String message) {
            Toast.makeText(context,message,Toast.LENGTH_SHORT).show();
        }

        void begin(Context launcher) {
            Intent launch=context.getPackageManager()
                .getLaunchIntentForPackage(MAPS_PACKAGE);
            if(launch==null) {
                done(false,"Google Карти не встановлені на годиннику.");return;
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                            Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try { JarvisDiagnostics.event(JarvisDiagnostics.Event.EXTERNAL_LAUNCH,2); launcher.startActivity(launch); }
            catch(RuntimeException ex) {
                done(false,"Wear OS не відкрила Google Карти.");return;
            }
            progress(request.whereAmI?
                "Відкриваю своє місце в Google Картах…":
                "Відкриваю Google Карти, шукаю «"+request.destination+"»…");
            if(request.whereAmI) {
                // Wear Maps often launches to its four-tile home, not to
                // the map canvas. The upper-left map tile must be opened
                // before a "my location" control can become available.
                MAIN.postDelayed(()->openMapHome(SystemClock.elapsedRealtime()+14500L),
                                 1600L);
            } else if(JarvisWatchScreenService.available(context)) {
                MAIN.postDelayed(()->step(JarvisWatchScreenService.MapsStep.OPEN_SEARCH),
                                 2400L);
            } else {
                // No Accessibility: still provide the same function using the
                // Android native walking navigation intent, with no blind taps.
                MAIN.postDelayed(this::openWalkingIntent,1200L);
            }
        }

        void openMapHome(long expires) {
            if(finished || !completion.active())return;
            if(!JarvisWatchScreenService.available(context)) {
                done(false,"Для натискання кнопки карти увімкни службу "+
                    "«Дімон — екран годинника» у спеціальних можливостях.");
                return;
            }
            long now=SystemClock.elapsedRealtime();
            if(now>=expires) {
                done(false,"Дімон не знайшов кнопку карти на головному екрані Google Карт.");
                return;
            }
            if(!JarvisWatchScreenService.foreground(MAPS_PACKAGE)) {
                MAIN.postDelayed(()->openMapHome(expires),2000L);
                return;
            }
            JarvisWatchScreenService.Result result=JarvisWatchScreenService.mapsStep(
                JarvisWatchScreenService.MapsStep.OPEN_MAP,null,(ok,detail)->{
                    if(finished || !completion.active())return;
                    if(!ok) {
                        done(false,"Wear OS скасувала натискання кнопки карти.");
                        return;
                    }
                    MAIN.postDelayed(()->centerLocation(expires),1000L);
                });
            switch(result) {
                case DONE:
                    MAIN.postDelayed(()->centerLocation(expires),1000L);
                    return;
                case RUNNING:return; // Wait for the gesture callback.
                case WAIT:
                    MAIN.postDelayed(()->openMapHome(expires),2000L);
                    return;
                case BLOCKED:
                default:
                    done(false,"Дімон не зміг натиснути кнопку карти на Watch7.");
            }
        }

        void centerLocation(long deadlineHere) {
            if(finished || !completion.active())return;
            if(JarvisWatchScreenService.mapsHomeVisible()) {
                if(SystemClock.elapsedRealtime()>=deadlineHere)
                    done(false,"Google Карти залишилися на початковому екрані. "+
                        "Не вдалося натиснути кнопку карти.");
                else MAIN.postDelayed(()->openMapHome(deadlineHere),1700L);
                return;
            }
            if(!JarvisWatchScreenService.available(context)) {
                done(false,"Служба керування екраном Watch7 стала недоступною.");
                return;
            }
            if(JarvisWatchScreenService.foreground(MAPS_PACKAGE)) {
                JarvisWatchScreenService.Result r=JarvisWatchScreenService
                    .mapsStep(JarvisWatchScreenService.MapsStep.CENTER_LOCATION,
                        null,(ok,details)->done(ok,
                            ok?"Центрую карту на поточному місці Watch7.":
                               "Карти відкриті, але центрування не виконано."));
                if(r==JarvisWatchScreenService.Result.DONE) {
                    done(true,"Показую поточне місце на Google Картах Watch7.");return;
                }
                if(r==JarvisWatchScreenService.Result.RUNNING)return;
            }
            if(SystemClock.elapsedRealtime()>=deadlineHere) {
                done(true,"Google Карти відкриті на Watch7. Якщо потрібно, натисни кнопку «Моє місце».");
                return;
            }
            MAIN.postDelayed(()->centerLocation(deadlineHere),1600L);
        }

        void step(JarvisWatchScreenService.MapsStep current) {
            if(finished || !completion.active())return;
            long now=SystemClock.elapsedRealtime();
            // On the Watch7 screenshot the pedestrian row is BETWEEN
            // public transport and the bicycle row. A former 84% bottom
            // click would tap bicycle. Reveal walking with one gentle UP.
            if(current==JarvisWatchScreenService.MapsStep.DIRECTIONS &&
               JarvisWatchScreenService.mapsRouteChoicesVisible()) {
                directionsOpened=true;
                stageSince=now;
                progress("Маршрути знайдено. Легенько свайпаю вгору…");
                MAIN.postDelayed(()->step(
                    JarvisWatchScreenService.MapsStep.SHORT_SWIPE_UP),600L);
                return;
            }
            if(current==JarvisWatchScreenService.MapsStep.SHORT_SWIPE_UP &&
               (!directionsOpened ||
                !JarvisWatchScreenService.mapsRouteChoicesVisible())) {
                if(now>=deadline || now-stageSince>=12500L) {
                    openWalkingIntent();return;
                }
                MAIN.postDelayed(()->step(current),750L);
                return;
            }
            if(current==JarvisWatchScreenService.MapsStep.TAP_MODE_BOTTOM &&
               !JarvisWatchUiRules.allowWalkingCardTap(
                   directionsOpened,shortSwipeCompleted,
                   JarvisWatchScreenService.foreground(MAPS_PACKAGE),
                   JarvisWatchScreenService.mapsHomeVisible(),
                   JarvisWatchScreenService.mapsInputOrKeyboardOpen(),
                   JarvisWatchScreenService.mapsRouteChoicesVisible())) {
                if(now>=deadline || now-stageSince>=12500L){
                    openWalkingIntent();return;
                }
                MAIN.postDelayed(()->step(current),700L);
                return;
            }
            if(now>=deadline || now-stageSince>=12500L) {
                if(current==JarvisWatchScreenService.MapsStep.TAP_MODE_BOTTOM ||
                   current==JarvisWatchScreenService.MapsStep.SHORT_SWIPE_UP)
                    openWalkingIntent();
                else openWalkingIntent();
                return;
            }
            final long token=steps.begin();
            JarvisWatchScreenService.MapsStep next=next(current);
            JarvisWatchScreenService.Result r=JarvisWatchScreenService.mapsStep(
                current,request.destination,(ok,detail)->{
                    if(finished || !completion.active() || !steps.finish(token))return;
                    if(!ok) {
                        if(current==JarvisWatchScreenService.MapsStep.TAP_MODE_BOTTOM)
                            done(false,"Wear OS скасувала натискання вибору маршруту.");
                        else openWalkingIntent();
                        return;
                    }
                    advance(current,next);
                });
            switch(r) {
                case DONE:if(steps.finish(token))advance(current,next);return;
                case RUNNING:return;
                case WAIT:
                    steps.finish(token);
                    MAIN.postDelayed(()->step(current),2000L);
                    return;
                case BLOCKED:
                default:
                    if(current==JarvisWatchScreenService.MapsStep.TAP_MODE_BOTTOM)
                        done(false,"Google Карти не дозволили натиснути вибір маршруту.");
                    else openWalkingIntent();
            }
        }

        JarvisWatchScreenService.MapsStep next(JarvisWatchScreenService.MapsStep s) {
            switch(s) {
                case OPEN_SEARCH:return JarvisWatchScreenService.MapsStep.SET_DESTINATION;
                case SET_DESTINATION:return JarvisWatchScreenService.MapsStep.SUBMIT_SEARCH;
                case SUBMIT_SEARCH:return JarvisWatchScreenService.MapsStep.FIRST_RESULT;
                case FIRST_RESULT:return JarvisWatchScreenService.MapsStep.DIRECTIONS;
                case DIRECTIONS:return JarvisWatchScreenService.MapsStep.SHORT_SWIPE_UP;
                case SHORT_SWIPE_UP:return JarvisWatchScreenService.MapsStep.TAP_MODE_BOTTOM;
                default:return null;
            }
        }

        void advance(JarvisWatchScreenService.MapsStep from,
                     JarvisWatchScreenService.MapsStep next) {
            if(finished || !completion.active())return;
            if(from==JarvisWatchScreenService.MapsStep.FIRST_RESULT)
                progress("Перший відповідний результат обрано. Відкриваю маршрут…");
            if(from==JarvisWatchScreenService.MapsStep.DIRECTIONS)
                directionsOpened=true;
            if(from==JarvisWatchScreenService.MapsStep.SHORT_SWIPE_UP) {
                shortSwipeCompleted=true;
                progress("Підтягнув маршрути вгору. Натискаю картку з пішоходом…");
            }
            if(from==JarvisWatchScreenService.MapsStep.SET_DESTINATION)
                progress("Адресу введено. Перевіряю варіанти…");
            if(from==JarvisWatchScreenService.MapsStep.WALKING) {
                done(true,"Натиснув «Пішки» у Google Картах Watch7. Перевір маршрут.");
                return;
            }
            if(from==JarvisWatchScreenService.MapsStep.TAP_MODE_BOTTOM) {
                done(true,"Після короткого свайпу вгору натиснув картку "+
                    "з пішоходом у Google Картах Watch7. Перевір, чи маршрут відкрився.");
                return;
            }
            if(next==null) {openWalkingIntent();return;}
            stageSince=SystemClock.elapsedRealtime();
            MAIN.postDelayed(()->step(next),
                from==JarvisWatchScreenService.MapsStep.FIRST_RESULT?1900L:900L);
        }

        /** Documented Android Google Maps intent, not a phone-side launch. */
        void openWalkingIntent() {
            if(finished || !completion.active())return;
            Uri uri=Uri.parse("google.navigation:q="+
                Uri.encode(request.destination)+"&mode=w");
            Intent intent=new Intent(Intent.ACTION_VIEW,uri);
            intent.setPackage(MAPS_PACKAGE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                context.startActivity(intent);
                done(true,"Передав Google Картам на Watch7 адресу «"+
                    request.destination+"» і піший режим. Перевір маршрут на екрані.");
            } catch(RuntimeException error) {
                done(false,"Google Карти на Watch7 не підтримали пішу навігацію. "+
                    "Відкрий пошук у Картах вручну.");
            }
        }
    }
}
