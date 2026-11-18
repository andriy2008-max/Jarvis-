package com.andriy.jarvis;
/** Coarse, fixed telemetry categories. Never returns any part of the input. */
final class JarvisCommandMetadata {
    enum Kind { OTHER, SPOTIFY, MAPS, PHONE_MODE, SPEAK, TIMER, ALARM, NOTES, TAXI,
        TELEGRAM, FINANCE, SCREEN, SYSTEM, DIAGNOSTICS }
    static Kind kind(String raw) {
        String s=CommandPatterns.normalize(raw);
        if(s.contains("діагностика") || s.contains("статус spotify"))return Kind.DIAGNOSTICS;
        if(JarvisWatchLocalCommands.spotifyRelatedOnWatch(raw) ||
            JarvisWatchLocalCommands.spotifyOnWatch(raw)!=null)return Kind.SPOTIFY;
        if(JarvisWatchLocalCommands.mapsOnWatch(raw)!=null)return Kind.MAPS;
        if(JarvisPhoneMode.transition(raw)!=JarvisPhoneMode.Transition.NONE)return Kind.PHONE_MODE;
        if(CommandPatterns.spokenText(raw)!=null)return Kind.SPEAK;
        if(s.contains("таймер"))return Kind.TIMER;
        if(s.contains("будильник"))return Kind.ALARM;
        if(s.contains("нотат"))return Kind.NOTES;
        if(s.contains("таксі"))return Kind.TAXI;
        if(s.contains("telegram") || s.contains("телеграм"))return Kind.TELEGRAM;
        if(s.contains("витратив") || s.contains("заробив") || s.contains("баланс") || s.contains("бюджет"))return Kind.FINANCE;
        if(s.contains("натисни") || s.contains("напиши") || s.contains("свайп"))return Kind.SCREEN;
        if(s.contains("гучність") || s.contains("ліхтарик"))return Kind.SYSTEM;
        return Kind.OTHER;
    }
}
