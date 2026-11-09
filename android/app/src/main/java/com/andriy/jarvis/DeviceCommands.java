package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pure-Java Ukrainian parser for explicit device controls; does not guess user intent. */
public final class DeviceCommands {
    public enum Kind {
        TORCH_ON, TORCH_OFF,
        MUSIC_PLAY, MUSIC_PAUSE, MUSIC_NEXT, MUSIC_PREVIOUS,
        VOLUME_PERCENT, VOLUME_MUTE, VOLUME_UNMUTE,
        DISPLAY_SETTINGS
    }

    public static final class Action {
        public final Kind kind;
        public final int percent;
        private Action(Kind kind, int percent) {
            this.kind = kind;
            this.percent = percent;
        }
    }

    private static final Pattern VOLUME = Pattern.compile(
        "^(?:(?:постав|встанови|зроби|зміни|задай)\\s+)?(?:медіа\\s*)?(?:гучність|звук)\\s*(?:на\\s*)?(\\d{1,3})\\s*(?:%|відсотків|відсотки|відсотка)?$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private DeviceCommands() { }

    private static Action action(Kind kind) { return new Action(kind, -1); }

    /** Input is normalized speech/text; null means unrelated command. */
    public static Action parse(String utterance) {
        if (utterance == null) return null;
        String s = utterance.toLowerCase(Locale.forLanguageTag("uk-UA"))
            .trim().replaceAll("[.!?]+$", "").trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return null;
        switch (s) {
            case "увімкни ліхтарик": case "ввімкни ліхтарик":
            case "включи ліхтарик": case "увімкни фонарик":
            case "ввімкни фонарик": case "включи фонарик":
            case "засвіти ліхтарик":
                return action(Kind.TORCH_ON);
            case "вимкни ліхтарик": case "виключи ліхтарик":
            case "вимкни фонарик": case "виключи фонарик":
            case "погаси ліхтарик":
                return action(Kind.TORCH_OFF);
            case "постав музику на паузу": case "пауза музики":
            case "призупини музику": case "зупини музику":
            case "пауза пісні":
                return action(Kind.MUSIC_PAUSE);
            case "продовж музику": case "віднови музику":
            case "продовж відтворення": case "віднови відтворення":
            case "грай музику": case "увімкни музику":
                return action(Kind.MUSIC_PLAY);
            case "наступна пісня": case "наступний трек":
            case "перемкни на наступну пісню": case "наступну пісню":
                return action(Kind.MUSIC_NEXT);
            case "попередня пісня": case "попередній трек":
            case "увімкни попередню пісню":
                return action(Kind.MUSIC_PREVIOUS);
            case "вимкни звук": case "вимкни медіазвук":
            case "прибери звук": case "без звуку":
                return action(Kind.VOLUME_MUTE);
            case "увімкни звук": case "віднови звук":
            case "поверни звук":
                return action(Kind.VOLUME_UNMUTE);
            case "відкрий налаштування екрана":
            case "налаштування екрана":
            case "відкрий параметри екрана":
                return action(Kind.DISPLAY_SETTINGS);
            default:
                Matcher match = VOLUME.matcher(s);
                if (match.matches()) {
                    return new Action(Kind.VOLUME_PERCENT, Integer.parseInt(match.group(1)));
                }
                return null;
        }
    }
}
