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
        DISPLAY_SETTINGS,
        DO_NOT_DISTURB_ON, DO_NOT_DISTURB_OFF,
        WIFI_ON, WIFI_OFF,
        BLUETOOTH_ON, BLUETOOTH_OFF,
        AIRPLANE_ON, AIRPLANE_OFF,
        MOBILE_DATA_ON, MOBILE_DATA_OFF,
        HOTSPOT_ON, HOTSPOT_OFF,
        LOCATION_ON, LOCATION_OFF,
        AUTO_ROTATE_ON, AUTO_ROTATE_OFF,
        RINGER_NORMAL, RINGER_SILENT, RINGER_VIBRATE, VIBRATE_OFF,
        NFC_ON, NFC_OFF,
        BATTERY_SAVER_ON, BATTERY_SAVER_OFF,
        DARK_MODE_ON, DARK_MODE_OFF,
        AUTO_BRIGHTNESS_ON, AUTO_BRIGHTNESS_OFF,
        VPN_ON, VPN_OFF
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
                return parseSwitch(s);
        }
    }

    /** Exact target matching only, so unrelated app and voice commands pass through. */
    private static Action parseSwitch(String speech) {
        Matcher verb = Pattern.compile(
            "^(увімкни|ввімкни|включи|включить|включити|активуй|підключи|вимкни|виключи|виключить|виключити|відключи|деактивуй)\\s+(.+)$",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(speech);
        if (!verb.matches()) return null;
        String word = verb.group(1);
        boolean enabled = word.startsWith("у") || word.startsWith("вв")
            || word.startsWith("вкл") || word.startsWith("актив") || word.startsWith("підкл");
        String item = verb.group(2).trim();
        switch (item) {
            case "ліхтарик": case "фонарик": case "спалах": case "спалах камери":
                return action(enabled ? Kind.TORCH_ON : Kind.TORCH_OFF);
            case "звук": case "медіазвук": case "медіа звук": case "звук медіа":
            case "гучність":
                return action(enabled ? Kind.VOLUME_UNMUTE : Kind.VOLUME_MUTE);
            case "не турбувати": case "режим не турбувати": case "режим «не турбувати»":
            case "do not disturb": case "dnd":
                return action(enabled ? Kind.DO_NOT_DISTURB_ON : Kind.DO_NOT_DISTURB_OFF);
            case "вай фай": case "вай-фай": case "вайфай": case "вайфаю":
            case "wi fi": case "wi-fi": case "wifi": case "бездротову мережу":
                return action(enabled ? Kind.WIFI_ON : Kind.WIFI_OFF);
            case "блютуз": case "блютус": case "bluetooth":
                return action(enabled ? Kind.BLUETOOTH_ON : Kind.BLUETOOTH_OFF);
            case "режим польоту": case "режим літака": case "авіарежим":
            case "авіа режим": case "airplane mode":
                return action(enabled ? Kind.AIRPLANE_ON : Kind.AIRPLANE_OFF);
            case "мобільні дані": case "мобільний інтернет": case "передачу даних":
            case "мобільну передачу даних": case "мобільну мережу":
                return action(enabled ? Kind.MOBILE_DATA_ON : Kind.MOBILE_DATA_OFF);
            case "точку доступу": case "точка доступу": case "мобільну точку доступу":
            case "роздачу інтернету": case "роздача інтернету": case "хотспот":
            case "хот спот": case "hotspot": case "режим модема":
                return action(enabled ? Kind.HOTSPOT_ON : Kind.HOTSPOT_OFF);
            case "геодані": case "геолокацію": case "геолокація":
            case "геопозицію": case "локацію": case "місцезнаходження":
            case "gps": case "джіпіес":
                return action(enabled ? Kind.LOCATION_ON : Kind.LOCATION_OFF);
            case "обертання екрана": case "поворот екрана":
            case "автообертання екрана": case "автоповорот екрана":
            case "автоповорот": case "автообертання":
            case "автоматичне обертання екрана": case "автоматичний поворот екрана":
            case "обертання крупна":
                return action(enabled ? Kind.AUTO_ROTATE_ON : Kind.AUTO_ROTATE_OFF);
            case "режим звуку": case "звуковий режим": case "режим дзвінка":
            case "звук дзвінка": case "дзвінок":
                return action(enabled ? Kind.RINGER_NORMAL : Kind.RINGER_SILENT);
            case "вібрацію": case "вібрація": case "режим вібрації":
            case "віброрежим":
                return action(enabled ? Kind.RINGER_VIBRATE : Kind.VIBRATE_OFF);
            case "nfc": case "нфс": case "нфц": case "безконтактну оплату":
                return action(enabled ? Kind.NFC_ON : Kind.NFC_OFF);
            case "енергозбереження": case "режим енергозбереження":
            case "економію батареї": case "економію енергії":
                return action(enabled ? Kind.BATTERY_SAVER_ON : Kind.BATTERY_SAVER_OFF);
            case "темну тему": case "темний режим": case "нічну тему":
            case "нічний режим":
                return action(enabled ? Kind.DARK_MODE_ON : Kind.DARK_MODE_OFF);
            case "адаптивну яскравість": case "автоматичну яскравість":
            case "автояскравість": case "авто яскравість":
                return action(enabled ? Kind.AUTO_BRIGHTNESS_ON : Kind.AUTO_BRIGHTNESS_OFF);
            case "vpn": case "впн":
                return action(enabled ? Kind.VPN_ON : Kind.VPN_OFF);
            default:
                return null;
        }
    }
}
