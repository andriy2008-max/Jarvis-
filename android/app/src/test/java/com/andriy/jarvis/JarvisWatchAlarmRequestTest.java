package com.andriy.jarvis;

/** Watch7 -> Pixel set-alarm transport; only HH:mm is a valid payload. */
public final class JarvisWatchAlarmRequestTest {
    private static int checks;
    private static void check(boolean ok,String label) {
        if(!ok)throw new AssertionError(label);
        checks++;
    }
    public static void main(String[] args) {
        check("07:30".equals(JarvisWatchAlarmRequest.encode(
            new AssistantIntents.ClockTime(7,30))),"serialize 07:30");
        check("00:00".equals(JarvisWatchAlarmRequest.encode(
            new AssistantIntents.ClockTime(0,0))),"midnight");
        check("23:59".equals(JarvisWatchAlarmRequest.encode(
            new AssistantIntents.ClockTime(23,59))),"end of day");
        check(JarvisWatchAlarmRequest.encode(
            new AssistantIntents.ClockTime(25,0))==null,"invalid input");
        check(JarvisWatchAlarmRequest.encode(
            new AssistantIntents.ClockTime(23,-2))==null,"invalid minutes");
        AssistantIntents.ClockTime t=JarvisWatchAlarmRequest.decode("07:30");
        check(t!=null && t.hour==7 && t.minute==30,"roundtrip");
        t=JarvisWatchAlarmRequest.decode("23:59");
        check(t!=null && t.hour==23 && t.minute==59,"late alarm");
        check(JarvisWatchAlarmRequest.decode("7:30")==null,"strict two-digit hours");
        check(JarvisWatchAlarmRequest.decode("24:00")==null,"reject 24h");
        check(JarvisWatchAlarmRequest.decode("06:60")==null,"reject 60min");
        check(JarvisWatchAlarmRequest.decode("07:30;delete")==null,"reject extra input");
        check(JarvisWatchAlarmRequest.decode("07:30\n")==null,"reject multiline");
        check(JarvisWatchAlarmRequest.decode(null)==null,"reject null");
        check(JarvisWearProtocol.validAction(JarvisWearProtocol.SET_ALARM),
            "dedicated alarm action available");
        check(JarvisWearProtocol.timeoutMs(JarvisWearProtocol.SET_ALARM)>=10000,
            "allow phone exact alarm scheduling");
        System.out.println("PASS "+checks+" Watch alarm transport checks");
    }
}
