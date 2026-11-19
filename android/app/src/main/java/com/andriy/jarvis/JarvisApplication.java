package com.andriy.jarvis;

import android.app.*;
import android.os.Bundle;

public final class JarvisApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        JarvisDiagnostics.init(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private void event(Activity a,JarvisDiagnostics.Event e) {
                if(a instanceof MainActivity) JarvisDiagnostics.event(e);
            }
            public void onActivityCreated(Activity a,Bundle state) { event(a,JarvisDiagnostics.Event.CREATE); }
            public void onActivityStarted(Activity a) { event(a,JarvisDiagnostics.Event.START); }
            public void onActivityResumed(Activity a) { event(a,JarvisDiagnostics.Event.RESUME); }
            public void onActivityPaused(Activity a) { event(a,JarvisDiagnostics.Event.PAUSE); }
            public void onActivityStopped(Activity a) { event(a,JarvisDiagnostics.Event.STOP); }
            public void onActivitySaveInstanceState(Activity a,Bundle b) {}
            public void onActivityDestroyed(Activity a) {
                event(a,a.isChangingConfigurations()?JarvisDiagnostics.Event.CONFIG_CHANGE:
                    a.isFinishing()?JarvisDiagnostics.Event.USER_FINISH:JarvisDiagnostics.Event.DESTROY);
            }
        });
    }
    @Override public void onTrimMemory(int level) {
        JarvisDiagnostics.event(JarvisDiagnostics.Event.TRIM_MEMORY,level); super.onTrimMemory(level);
    }
    @Override public void onLowMemory() {
        JarvisDiagnostics.event(JarvisDiagnostics.Event.LOW_MEMORY); super.onLowMemory();
    }
}
