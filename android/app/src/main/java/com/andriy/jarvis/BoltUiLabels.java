package com.andriy.jarvis;

import java.util.Locale;

/** Allowlist for the passenger rides/destination controls, never checkout. */
public final class BoltUiLabels {
    private BoltUiLabels() {}
    private static String normal(String s) {
        return s==null?"":s.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+"," ").replaceAll("\\s+"," ").trim();
    }
    public static boolean rides(String s){
        String v=normal(s);
        return v.equals("поїздки")||v.equals("поездки")||
            v.equals("rides")||v.equals("ride")||
            v.equals("таксі")||v.equals("taxi");
    }
    public static boolean destination(String s){
        String v=normal(s);
        return v.contains("куди")||v.contains("куда")||v.contains("where to")||
            v.contains("destination")||v.contains("пункт призначення")||
            v.contains("місце прибуття")||v.contains("адреса прибуття")||
            v.contains("dropoff")||v.contains("drop off");
    }
    public static boolean pickup(String s){
        String v=normal(s);
        return v.contains("звідки")||v.contains("откуда")||
            v.contains("pickup")||v.contains("pick up")||
            v.contains("місце посадки")||v.contains("адреса подачі")||
            v.contains("пункт відправлення")||v.contains("your location");
    }
}
