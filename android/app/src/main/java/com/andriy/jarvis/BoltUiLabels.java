package com.andriy.jarvis;

import java.util.Locale;

/** Allowlist for the passenger rides/destination controls, never checkout. */
public final class BoltUiLabels {
    private BoltUiLabels() {}
    /** Fixed launch-to-tap delay agreed with the user, independent of tile text. */
    public static final long RIDES_TAP_DELAY_MS=4000L;
    public static boolean ridesTapDue(long elapsedMs) {
        return elapsedMs>=RIDES_TAP_DELAY_MS;
    }

    private static String normal(String s) {
        return s==null?"":s.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();
    }
    public static boolean rides(String s){
        String v=normal(s);
        // Bolt's home tile often exposes a combined accessibility label:
        // «Поїздки · Рушаймо!», not just the short text «Поїздки».
        return v.equals("поїздки")||v.equals("поїздки рушаймо")||
            v.equals("поездки")||v.equals("поездки поехали")||
            v.equals("rides")||v.equals("rides let s go")||
            v.equals("ride")||v.equals("таксі")||v.equals("taxi");
    }
    /** Visible on the passenger home screen above the four service tiles. */
    public static boolean ridesHomeHeading(String s) {
        String v=normal(s);
        return v.equals("тебе підвезти") || v.equals("need a ride") ||
            v.equals("раді тебе бачити") || v.equals("рады тебя видеть") ||
            v.equals("good to see you");
    }

    /** Bolt may render service tiles without accessible text. Only allow a
     * screenshot-calibrated fallback when multiple independent HOME cues agree. */
    public static boolean ridesHomeFallback(boolean heading,boolean plan,
                                             boolean food,boolean send,boolean search) {
        int cues=(plan?1:0)+(food?1:0)+(send?1:0)+(search?1:0);
        return cues>=3 || (heading && cues>=2);
    }

    /** Center of the top-left «Поїздки» tile in the 684x1536
     * screenshot supplied by the user on November 14, 2026.
     * New Bolt home UI: x=27..335, y=386..589. The previous y=321
     * targets the HOME HEADING, not «Поїздки», on this layout.
     * Require verified Bolt home markers and portrait geometry first. */
    public static int[] screenshotRidesPoint(int width,int height) {
        if(width<360 || height<width*2.05f || height>width*2.50f)return null;
        return new int[]{Math.round(width*(182f/684f)),
                         Math.round(height*(489f/1536f))};
    }

    /** Stops a four-second screenshot tap on route/checkout/payment screens.
     * Never identifies a home tile by itself: these are blocking cues only. */
    public static boolean unsafeForTimedRidesTap(String label) {
        String v=normal(label);
        if(v.isEmpty())return false;
        if(routeTitle(label))return true;
        return v.contains("підтвердити") || v.contains("подтвердить") ||
            v.contains("confirm ride") || v.contains("confirm trip") ||
            v.contains("замовити поїздку") || v.contains("заказать поездку") ||
            v.contains("замовити автомобіль") || v.contains("book ride") ||
            v.contains("оплатити") || v.contains("оплата") ||
            v.contains("оплатить") || v.contains("платеж") ||
            v.contains("payment") || v.contains("checkout") ||
            v.contains("спосіб оплати") || v.contains("способ оплаты") ||
            v.contains("trip details") || v.contains("деталі поїздки");
    }

    public static boolean destination(String s){
        String v=normal(s);
        return v.contains("куди")||v.contains("куда")||v.contains("where to")||
            v.contains("destination")||v.contains("пункт призначення")||
            v.contains("місце прибуття")||v.contains("адреса прибуття")||
            v.contains("dropoff")||v.contains("drop off")||
            v.contains("місце висадки")||v.contains("место высадки");
    }
    /** Confirm the Bolt trip-planning page, not a home tile or checkout. */
    public static boolean routeTitle(String s){
        String v=normal(s);
        return v.equals("маршрут")||v.equals("route")||
            v.equals("маршрут поїздки")||v.equals("маршрут поездки");
    }

    /** Physical screen coordinates calibrated from the user's 684x1536
     * Bolt route screenshot; only usable after verifying route and pickup. */
    public static int[] screenshotDropoffPoint(int width,int height) {
        if(width<360 || height<width*2.05f || height>width*2.50f) return null;
        int x=Math.round(width*(250f/684f));
        int y=Math.round(height*(322f/1536f));
        return dropoffRegion(x,y,width,height) ? new int[]{x,y} : null;
    }

    public static boolean dropoffRegion(int x,int y,int width,int height) {
        return width>0 && height>0 &&
            x>width*.14f && x<width*.80f &&
            y>height*.18f && y<height*.34f;
    }

    /** Only submit a focused Bolt editor when it displays the exact address
     * that Jarvis just inserted. Never submit empty or unrelated text. */
    public static boolean typedDestinationMatches(String expected, CharSequence actual) {
        return expected != null && !expected.trim().isEmpty() &&
            expected.length() <= 250 && actual != null && expected.contentEquals(actual);
    }

    public static boolean pickup(String s){
        String v=normal(s);
        return v.contains("звідки")||v.contains("откуда")||
            v.contains("pickup")||v.contains("pick up")||
            v.contains("місце посадки")||v.contains("адреса подачі")||
            v.contains("пункт відправлення")||v.contains("your location");
    }
}
