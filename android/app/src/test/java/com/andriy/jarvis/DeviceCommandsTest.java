package com.andriy.jarvis;

public final class DeviceCommandsTest {
    private static int checks = 0;

    private static void kind(String speech, DeviceCommands.Kind expected) {
        DeviceCommands.Action result = DeviceCommands.parse(speech);
        if (result == null || result.kind != expected) {
            throw new AssertionError("Failed action for «" + speech + "»: " + (result == null ? "null" : result.kind));
        }
        checks++;
    }

    private static void volume(String speech, int expected) {
        DeviceCommands.Action result = DeviceCommands.parse(speech);
        if (result == null || result.kind != DeviceCommands.Kind.VOLUME_PERCENT || result.percent != expected) {
            throw new AssertionError("Wrong volume: " + speech);
        }
        checks++;
    }

    private static void unrelated(String speech) {
        if (DeviceCommands.parse(speech) != null) throw new AssertionError("Should not match: " + speech);
        checks++;
    }

    public static void main(String[] args) {
        kind("увімкни ліхтарик", DeviceCommands.Kind.TORCH_ON);
        kind("ввімкни ліхтарик", DeviceCommands.Kind.TORCH_ON);
        kind("включи фонарик", DeviceCommands.Kind.TORCH_ON);
        kind("засвіти ліхтарик", DeviceCommands.Kind.TORCH_ON);
        kind("вимкни ліхтарик", DeviceCommands.Kind.TORCH_OFF);
        kind("виключи фонарик", DeviceCommands.Kind.TORCH_OFF);
        kind("погаси ліхтарик", DeviceCommands.Kind.TORCH_OFF);
        kind("постав музику на паузу", DeviceCommands.Kind.MUSIC_PAUSE);
        kind("зупини музику", DeviceCommands.Kind.MUSIC_PAUSE);
        kind("пауза пісні", DeviceCommands.Kind.MUSIC_PAUSE);
        kind("продовж музику", DeviceCommands.Kind.MUSIC_PLAY);
        kind("віднови відтворення", DeviceCommands.Kind.MUSIC_PLAY);
        kind("увімкни музику", DeviceCommands.Kind.MUSIC_PLAY);
        kind("наступна пісня", DeviceCommands.Kind.MUSIC_NEXT);
        kind("наступний трек", DeviceCommands.Kind.MUSIC_NEXT);
        kind("перемкни на наступну пісню", DeviceCommands.Kind.MUSIC_NEXT);
        kind("попередня пісня", DeviceCommands.Kind.MUSIC_PREVIOUS);
        kind("попередній трек", DeviceCommands.Kind.MUSIC_PREVIOUS);
        kind("вимкни звук", DeviceCommands.Kind.VOLUME_MUTE);
        kind("без звуку", DeviceCommands.Kind.VOLUME_MUTE);
        kind("віднови звук", DeviceCommands.Kind.VOLUME_UNMUTE);
        kind("увімкни звук", DeviceCommands.Kind.VOLUME_UNMUTE);
        kind("відкрий налаштування екрана", DeviceCommands.Kind.DISPLAY_SETTINGS);
        kind("включи вай фай", DeviceCommands.Kind.WIFI_ON);
        kind("виключи wi-fi", DeviceCommands.Kind.WIFI_OFF);
        kind("увімкни wifi", DeviceCommands.Kind.WIFI_ON);
        kind("вимкни вайфай", DeviceCommands.Kind.WIFI_OFF);
        kind("включи блютуз", DeviceCommands.Kind.BLUETOOTH_ON);
        kind("виключи bluetooth", DeviceCommands.Kind.BLUETOOTH_OFF);
        kind("увімкни режим польоту", DeviceCommands.Kind.AIRPLANE_ON);
        kind("виключи авіарежим", DeviceCommands.Kind.AIRPLANE_OFF);
        kind("включи мобільні дані", DeviceCommands.Kind.MOBILE_DATA_ON);
        kind("вимкни мобільний інтернет", DeviceCommands.Kind.MOBILE_DATA_OFF);
        kind("включи точку доступу", DeviceCommands.Kind.HOTSPOT_ON);
        kind("виключи роздачу інтернету", DeviceCommands.Kind.HOTSPOT_OFF);
        kind("увімкни геодані", DeviceCommands.Kind.LOCATION_ON);
        kind("виключи геолокацію", DeviceCommands.Kind.LOCATION_OFF);
        kind("включи автоповорот", DeviceCommands.Kind.AUTO_ROTATE_ON);
        kind("виключи обертання екрана", DeviceCommands.Kind.AUTO_ROTATE_OFF);
        kind("включи режим не турбувати", DeviceCommands.Kind.DO_NOT_DISTURB_ON);
        kind("виключи не турбувати", DeviceCommands.Kind.DO_NOT_DISTURB_OFF);
        kind("включи режим звуку", DeviceCommands.Kind.RINGER_NORMAL);
        kind("виключи режим звуку", DeviceCommands.Kind.RINGER_SILENT);
        kind("включи вібрацію", DeviceCommands.Kind.RINGER_VIBRATE);
        kind("виключи вібрацію", DeviceCommands.Kind.VIBRATE_OFF);
        kind("включи нфс", DeviceCommands.Kind.NFC_ON);
        kind("виключи NFC", DeviceCommands.Kind.NFC_OFF);
        kind("включи енергозбереження", DeviceCommands.Kind.BATTERY_SAVER_ON);
        kind("виключи енергозбереження", DeviceCommands.Kind.BATTERY_SAVER_OFF);
        kind("увімкни темний режим", DeviceCommands.Kind.DARK_MODE_ON);
        kind("виключи темну тему", DeviceCommands.Kind.DARK_MODE_OFF);
        kind("включи автоматичну яскравість", DeviceCommands.Kind.AUTO_BRIGHTNESS_ON);
        kind("виключи автояскравість", DeviceCommands.Kind.AUTO_BRIGHTNESS_OFF);
        kind("включи vpn", DeviceCommands.Kind.VPN_ON);
        kind("виключи впн", DeviceCommands.Kind.VPN_OFF);
        kind("включи звук", DeviceCommands.Kind.VOLUME_UNMUTE);
        kind("виключи звук", DeviceCommands.Kind.VOLUME_MUTE);
        kind("включи ліхтарик", DeviceCommands.Kind.TORCH_ON);
        kind("виключи ліхтарик", DeviceCommands.Kind.TORCH_OFF);
        kind("вимкни музику", DeviceCommands.Kind.MUSIC_PAUSE);
        kind("виключи музику", DeviceCommands.Kind.MUSIC_PAUSE);
        kind("вимкни spotify", DeviceCommands.Kind.MUSIC_PAUSE);
        kind("виключи spotify", DeviceCommands.Kind.MUSIC_PAUSE);
        kind("виключи звук", DeviceCommands.Kind.VOLUME_MUTE);
        kind("включи звук", DeviceCommands.Kind.VOLUME_UNMUTE);
        kind("включи геодані", DeviceCommands.Kind.LOCATION_ON);
        kind("виключи геодані", DeviceCommands.Kind.LOCATION_OFF);
        kind("налаштування екрана", DeviceCommands.Kind.DISPLAY_SETTINGS);
        volume("постав гучність на 50%", 50);
        volume("гучність 0", 0);
        volume("зроби звук на 100 відсотків", 100);
        volume("зміни медіа гучність на 33 відсотки", 33);
        volume("гучність 999", 999); // rejected by executor, not misinterpreted as app launch
        kind("   Увімкни Ліхтарик! ", DeviceCommands.Kind.TORCH_ON);
        unrelated(null);
        unrelated("");
        unrelated("вимкни голос");
        unrelated("вимкни таймер");
        unrelated("відкрий telegram");
        unrelated("відкрий камеру");
        unrelated("постав будильник на 07:30");
        unrelated("гучніше");
        unrelated("тихіше");
        unrelated("стоп");
        unrelated("увімкни світло в кімнаті");
        unrelated("увімкни голос");
        unrelated("включи телеграм");
        unrelated("включи режим модема у ноутбуці");
        unrelated("включи світло в кімнаті");
        unrelated("відкрий налаштування");
        System.out.println("PASS " + checks + " device command checks");
    }
}
