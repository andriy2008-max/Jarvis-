package com.andriy.jarvis;

public final class JarvisSequenceEngineTest {
    private static int checks;
    private static void check(boolean ok, String reason) {
        if (!ok) throw new AssertionError(reason);
        checks++;
    }

    private static final class Fake implements JarvisSequenceEngine.Environment {
        String visible;
        boolean buttonReady;
        boolean inputReady;
        boolean permission = true;
        boolean blocked;
        int opens, clicks, types, volumes, attempts;
        String written;
        String target;
        int percent;

        @Override public String launchApp(String app) {
            opens++;
            target = "com.google.android.youtube";
            return target;
        }
        @Override public String visiblePackage() { return visible; }
        @Override public JarvisSequenceEngine.Attempt click(String label) {
            attempts++;
            if (blocked) return JarvisSequenceEngine.Attempt.DENY;
            if (!buttonReady) return JarvisSequenceEngine.Attempt.WAIT;
            clicks++;
            return JarvisSequenceEngine.Attempt.OK;
        }
        @Override public boolean focusedInputReady(String pkg) {
            return inputReady && pkg != null && pkg.equals(visible);
        }
        @Override public JarvisSequenceEngine.Attempt type(String text) {
            types++;
            written = text;
            return JarvisSequenceEngine.Attempt.OK;
        }
        @Override public boolean setMediaVolume(int value) {
            volumes++;
            percent = value;
            return true;
        }
        @Override public boolean hasScreenControlPermission() { return permission; }
        @Override public String failureReason() { return "Виконання відхилено."; }
    }

    private static JarvisSequenceEngine newChain(Fake env) {
        JarvisCommandSequence.Plan plan = JarvisCommandSequence.parse(
            "Відкрий Ютуб натисни пошук напиши тварини і постав гучність на 70");
        check(plan != null && plan.valid(), "Parser must produce a valid chain");
        return new JarvisSequenceEngine(plan, env);
    }

    public static void main(String[] args) {
        Fake fake = new Fake();
        JarvisSequenceEngine engine = newChain(fake);
        check(engine.tick(0) == JarvisSequenceEngine.Progress.WAITING, "launch pending");
        check(fake.opens == 1 && fake.clicks == 0 && fake.types == 0 && fake.volumes == 0,
            "No action before YouTube is open");
        // App is loading, previous app still visible.
        fake.visible = "com.android.launcher3";
        engine.tick(400);
        engine.tick(800);
        check(fake.opens == 1 && fake.clicks == 0, "Must not click on launcher");
        fake.visible = "com.google.android.youtube";
        engine.tick(1200); // only NOW the open step completes
        check(engine.completedSteps() == 1 && fake.clicks == 0, "Opened and step advanced");
        engine.tick(1600); // search not visible yet
        check(fake.clicks == 0 && fake.types == 0, "No click until search appears");
        fake.buttonReady = true;
        engine.tick(2000);
        check(fake.attempts == 1 && fake.clicks == 0,"Missing button waits 4 seconds before next check");
        engine.tick(5600);
        check(fake.clicks == 1 && engine.completedSteps() == 2, "Click exactly once");
        engine.tick(6000); // focused input not ready
        check(fake.types == 0 && fake.volumes == 0, "No text or volume before search field");
        fake.inputReady = true;
        engine.tick(10000);
        check(fake.types == 1 && "тварини".equals(fake.written),
            "Insert exactly requested phrase only after focus");
        check(fake.volumes == 0, "Volume cannot overtake typing");
        check(engine.tick(10400) == JarvisSequenceEngine.Progress.COMPLETED,
            "All steps complete");
        check(fake.volumes == 1 && fake.percent == 70, "Media volume is last");
        engine.tick(10800);
        check(fake.clicks == 1 && fake.types == 1 && fake.volumes == 1,
            "No duplicated actions after completion");

        Fake stalledApp = new Fake();
        JarvisSequenceEngine appEngine = newChain(stalledApp);
        appEngine.tick(0);
        check(appEngine.tick(20000) == JarvisSequenceEngine.Progress.FAILED,
            "App wait must time out");
        check(stalledApp.clicks == 0 && stalledApp.types == 0 && stalledApp.volumes == 0,
            "App timeout must abort everything else");

        Fake noField = new Fake();
        JarvisSequenceEngine fieldEngine = newChain(noField);
        fieldEngine.tick(0);
        noField.visible = "com.google.android.youtube";
        fieldEngine.tick(400);
        noField.buttonReady = true;
        fieldEngine.tick(800);
        fieldEngine.tick(1000);
        check(fieldEngine.tick(15000) == JarvisSequenceEngine.Progress.WAITING,
            "Missing edit field keeps waiting without typing");
        check(noField.clicks == 1 && noField.types == 0 && noField.volumes == 0,
            "Do not type or change volume while waiting for input");

        noField.inputReady = true;
        fieldEngine.tick(16000);
        check(noField.types==0,"Subsequent wait is 2 seconds");
        fieldEngine.tick(17000);
        check(noField.types==1,"Late field appears and is typed exactly once");
        fieldEngine.cancel();
        fieldEngine.tick(20000);
        check(noField.volumes==0,"Cancel prevents remaining steps after long wait");

        Fake delayed = new Fake();
        JarvisSequenceEngine delayedEngine = newChain(delayed);
        delayedEngine.tick(0);delayed.visible="com.google.android.youtube";delayedEngine.tick(1);
        delayedEngine.tick(2);delayedEngine.tick(4001);
        check(delayed.attempts==1,"No premature button retry");
        delayedEngine.tick(4002);delayedEngine.tick(6001);
        check(delayed.attempts==2,"4 second retry then 2 second interval");
        delayedEngine.tick(6002);check(delayed.attempts==3,"Repeat missing button every 2 seconds");
        delayed.buttonReady=true;delayedEngine.tick(8002);delayedEngine.tick(9000);
        check(delayed.clicks==1,"Late button clicked once then waits for field");

        Fake lostPermission = new Fake();
        JarvisSequenceEngine stopEngine = newChain(lostPermission);
        stopEngine.tick(0);
        lostPermission.permission = false;
        check(stopEngine.tick(200) == JarvisSequenceEngine.Progress.FAILED,
            "Accessibility permission revoked: abort");
        check(lostPermission.clicks == 0 && lostPermission.types == 0, "Never continue revoked");

        Fake canceled = new Fake();
        JarvisSequenceEngine cancelEngine = newChain(canceled);
        cancelEngine.tick(0);
        cancelEngine.cancel();
        check(cancelEngine.tick(100) == JarvisSequenceEngine.Progress.FAILED,
            "Cancellation sticks");
        check(canceled.clicks == 0 && canceled.types == 0 && canceled.volumes == 0,
            "Cancelled engine may not execute subsequent steps");

        Fake denied = new Fake();
        JarvisSequenceEngine deniedEngine = newChain(denied);
        deniedEngine.tick(0);
        denied.visible = "com.google.android.youtube";
        deniedEngine.tick(500);
        denied.blocked = true;
        check(deniedEngine.tick(1000) == JarvisSequenceEngine.Progress.FAILED,
            "Denied action must not be retried");
        check(denied.types == 0 && denied.volumes == 0, "No later action after denial");

        System.out.println("PASS " + checks + " ordered command execution and gating checks");
    }
}
