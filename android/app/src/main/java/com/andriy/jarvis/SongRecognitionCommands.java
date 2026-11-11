package com.andriy.jarvis;

/** Exact local requests; song-title searches and playback must not start the microphone. */
public final class SongRecognitionCommands {
    private SongRecognitionCommands() {}
    public static boolean matches(String raw) {
        if (raw == null) return false;
        String s = CommandPatterns.normalize(raw).replaceAll("[.,!?]+$", "").trim();
        switch (s) {
            case "що це за пісня": case "що за пісня":
            case "яка це пісня": case "яка пісня грає":
            case "що зараз грає": case "розпізнай пісню":
            case "розпізнай музику": case "визнач пісню":
            case "шазам": case "shazam": case "запусти шазам": case "відкрий шазам":
            case "зашазам": case "зашазам пісню": return true;
            default: return false;
        }
    }
}
