package com.andriy.jarvis;

public final class JarvisControlFeedbackTest {
    private static void eq(Object expected,Object actual) {
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) {
        JarvisControlFeedback.clear();
        eq(JarvisControlFeedback.Phase.IDLE,JarvisControlFeedback.phase(0));
        long command=JarvisControlFeedback.begin();
        long gesture=JarvisControlFeedback.begin();
        JarvisControlFeedback.finish(command,true,100);
        eq(JarvisControlFeedback.Phase.RUNNING,JarvisControlFeedback.phase(500));
        JarvisControlFeedback.finish(gesture,true,600);
        eq(JarvisControlFeedback.Phase.SUCCESS,JarvisControlFeedback.phase(600));
        eq(JarvisControlFeedback.Phase.SUCCESS,JarvisControlFeedback.phase(2099));
        eq(JarvisControlFeedback.Phase.IDLE,JarvisControlFeedback.phase(2100));
        command=JarvisControlFeedback.begin();
        eq(JarvisControlFeedback.Phase.RUNNING,JarvisControlFeedback.phase(2200));
        JarvisControlFeedback.finish(gesture,false,2300); // late callback from previous command
        eq(JarvisControlFeedback.Phase.RUNNING,JarvisControlFeedback.phase(2300));
        gesture=JarvisControlFeedback.begin();
        JarvisControlFeedback.finish(gesture,false,2400);
        JarvisControlFeedback.finish(command,true,2500);
        eq(JarvisControlFeedback.Phase.FAILURE,JarvisControlFeedback.phase(2500));
        command=JarvisControlFeedback.begin();
        JarvisControlFeedback.clear();
        JarvisControlFeedback.finish(command,true,3000);
        eq(JarvisControlFeedback.Phase.IDLE,JarvisControlFeedback.phase(3000));
        for (String error : new String[] {"Не знайшов кнопку", "Android відхилив жест.",
            "Знайшов кілька можливих кнопок", "Спочатку увімкни керування екраном.",
            "Запит передано Android, але загальний стан не підтверджено.", "", "Немає таймера."})
            eq(false,JarvisControlFeedback.successfulReply(error));
        eq(false,JarvisControlFeedback.successfulReply(null));
        eq(true,JarvisControlFeedback.successfulReply("Знайшов потрібну кнопку й натиснув."));
        eq(true,JarvisControlFeedback.successfulReply("Текст введено в активне поле. Повідомлення не надсилав."));
        eq(true,JarvisControlFeedback.successfulReply("Медіагучність: 70 відсотків."));
        System.out.println("PASS task feedback: callback ordering, failure, expiry, stale replies");
    }
}
