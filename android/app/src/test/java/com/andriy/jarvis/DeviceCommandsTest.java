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
        unrelated("відкрий налаштування");
        System.out.println("PASS " + checks + " device command checks");
    }
}
