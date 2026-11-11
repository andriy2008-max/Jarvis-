package com.andriy.jarvis;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Local parser; null=not taxi, empty=open Bolt only, text=destination. */
public final class JarvisTaxiCommands {
    private static final Pattern COMMAND=Pattern.compile(
        "(?iu)^(?:(?:джарвіс|джарвис|jarvis)[\\s,:—-]*)?(?:таксі|такси|taxi|виклич\\s+таксі|замов\\s+таксі)(?:[\\s,:—-]+(.*))?$");
    private static final Pattern PREPOSITION=Pattern.compile(
        "(?iu)^(?:до|на|в|у|за\\s+адресою|адреса|адресу)\\s+");
    private JarvisTaxiCommands() {}
    public static String destination(String raw) {
        if(raw==null)return null;
        Matcher match=COMMAND.matcher(raw.trim().replaceAll("[.!?]+$",""));
        if(!match.matches())return null;
        String place=match.group(1);
        return place==null?"":PREPOSITION.matcher(place.trim()).replaceFirst("").trim();
    }
}
