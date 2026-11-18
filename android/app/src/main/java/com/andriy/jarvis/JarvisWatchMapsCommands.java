package com.andriy.jarvis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Voice grammar for Google Maps commands spoken on the Wear OS watch only. */
public final class JarvisWatchMapsCommands {
    public static final class Request {
        public final boolean whereAmI;
        public final String destination;
        private Request(boolean location, String place) {
            whereAmI=location;
            destination=place;
        }
    }

    private static final Pattern WAKE=Pattern.compile(
        "(?iu)^(?:джарвіс|джарвис|jarvis)[\\s,:—-]+");
    private static final Pattern HERE=Pattern.compile(
        "(?iu)^(?:де\\s+я(?:\\s+зараз)?|покажи\\s+де\\s+я(?:\\s+зараз)?|"+
        "покажи\\s+моє\\s+(?:місцезнаходження|розташування)|"+
        "де\\s+моє\\s+місцезнаходження)\\s*[.!?]*$");
    private static final Pattern DIRECTIONS=Pattern.compile(
        "(?iu)^(?:знайди\\s+(?:дорогу|маршрут)\\s+до|"+
        "проклади\\s+маршрут\\s+до|побудуй\\s+маршрут\\s+до)\\s+(.+)$");
    private static final Pattern WALK=Pattern.compile(
        "(?iu)\\s+(?:пішки|пішим\\s+маршрутом|пішохідним\\s+маршрутом)\\s*$");

    private JarvisWatchMapsCommands() {}

    public static Request parse(String raw) {
        if(raw==null || raw.length()>450)return null;
        String words=WAKE.matcher(raw.trim()).replaceFirst("").trim();
        if(HERE.matcher(words).matches())return new Request(true,null);
        Matcher route=DIRECTIONS.matcher(words);
        if(!route.matches())return null;
        String place=route.group(1).trim().replaceAll("[.!?]+$","").trim();
        place=WALK.matcher(place).replaceFirst("").trim();
        if(place.isEmpty() || place.length()>180 ||
           place.contains("://") || place.indexOf('\n')>=0 ||
           place.indexOf('\r')>=0)return null;
        return new Request(false,place);
    }
}
