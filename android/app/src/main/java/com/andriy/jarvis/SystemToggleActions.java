package com.andriy.jarvis;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

/**
 * Explicit phone feature controls. When Android restricts a switch, open its
 * standard settings screen and never claim the switch changed automatically.
 * No root, hidden system APIs or Accessibility privilege escalation.
 */
public final class SystemToggleActions {
    private SystemToggleActions() {}

    /** Returns null only for existing music/volume/torch commands. */
    public static String perform(Context context, DeviceCommands.Action command) {
        if (context == null || command == null) return null;
        switch (command.kind) {
            case DO_NOT_DISTURB_ON:
            case DO_NOT_DISTURB_OFF:
                return setDoNotDisturb(context,
                    command.kind == DeviceCommands.Kind.DO_NOT_DISTURB_ON);
            case RINGER_NORMAL:
            case RINGER_SILENT:
            case RINGER_VIBRATE:
            case VIBRATE_OFF:
                return setRinger(context, command.kind);
            case AUTO_ROTATE_ON:
            case AUTO_ROTATE_OFF:
                return setSystemValue(context, Settings.System.ACCELEROMETER_ROTATION,
                    command.kind == DeviceCommands.Kind.AUTO_ROTATE_ON ? 1 : 0,
                    "Автоповорот екрана");
            case AUTO_BRIGHTNESS_ON:
            case AUTO_BRIGHTNESS_OFF:
                return setSystemValue(context, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    command.kind == DeviceCommands.Kind.AUTO_BRIGHTNESS_ON
                        ? Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                        : Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
                    "Автоматична яскравість");
            case WIFI_ON:
            case WIFI_OFF:
                return openSettings(context, "Wi-Fi",
                    Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS);
            case BLUETOOTH_ON:
            case BLUETOOTH_OFF:
                return openSettings(context, "Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS);
            case AIRPLANE_ON:
            case AIRPLANE_OFF:
                return openSettings(context, "режиму польоту", Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            case MOBILE_DATA_ON:
            case MOBILE_DATA_OFF:
                return openSettings(context, "мобільних даних",
                    Settings.ACTION_DATA_USAGE_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS);
            case HOTSPOT_ON:
            case HOTSPOT_OFF:
                // Vendor-specific tether settings with a public wireless-settings fallback.
                return openSettings(context, "точки доступу",
                    "android.settings.TETHER_SETTINGS", Settings.ACTION_WIRELESS_SETTINGS);
            case LOCATION_ON:
            case LOCATION_OFF:
                return openSettings(context, "геолокації", Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            case NFC_ON:
            case NFC_OFF:
                return openSettings(context, "NFC",
                    Settings.ACTION_NFC_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS);
            case BATTERY_SAVER_ON:
            case BATTERY_SAVER_OFF:
                return openSettings(context, "енергозбереження",
                    Settings.ACTION_BATTERY_SAVER_SETTINGS, "android.intent.action.POWER_USAGE_SUMMARY");
            case DARK_MODE_ON:
            case DARK_MODE_OFF:
                return openSettings(context, "темної теми", Settings.ACTION_DISPLAY_SETTINGS);
            case VPN_ON:
            case VPN_OFF:
                return openSettings(context, "VPN",
                    Settings.ACTION_VPN_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS);
            default:
                return null;
        }
    }

    private static String setDoNotDisturb(Context context, boolean enabled) {
        NotificationManager manager =
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return "Режим «Не турбувати» недоступний.";
        if (!manager.isNotificationPolicyAccessGranted()) {
            return openPermission(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                "Для керування режимом «Не турбувати» надай Джарвісу доступ до налаштувань сповіщень. Після цього повтори команду.");
        }
        try {
            int desired = enabled ? NotificationManager.INTERRUPTION_FILTER_NONE :
                NotificationManager.INTERRUPTION_FILTER_ALL;
            manager.setInterruptionFilter(desired);
            // Android 15+ may change only this application's automatic Zen rule.
            if (manager.getCurrentInterruptionFilter() != desired)
                return "Запит передано Android, але загальний стан «Не турбувати» не підтверджено.";
            return enabled ? "Режим «Не турбувати» активовано." :
                "Режим «Не турбувати» вимкнено.";
        } catch (RuntimeException denied) {
            return "Android не дозволив змінити «Не турбувати». Перевір доступ до сповіщень.";
        }
    }

    private static String setRinger(Context context, DeviceCommands.Kind kind) {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (audio == null || audio.isVolumeFixed()) return "Режим дзвінка недоступний.";
        int target;
        switch (kind) {
            case RINGER_NORMAL: case VIBRATE_OFF:
                target = AudioManager.RINGER_MODE_NORMAL;
                break;
            case RINGER_VIBRATE:
                target = AudioManager.RINGER_MODE_VIBRATE;
                break;
            default:
                target = AudioManager.RINGER_MODE_SILENT;
        }
        try {
            audio.setRingerMode(target);
            if (audio.getRingerMode() != target)
                return "Android не підтвердив зміну режиму дзвінка.";
            switch (target) {
                case AudioManager.RINGER_MODE_NORMAL: return "Звук дзвінка увімкнено.";
                case AudioManager.RINGER_MODE_VIBRATE: return "Вібрацію дзвінка увімкнено.";
                default: return "Звук дзвінка вимкнено.";
            }
        } catch (RuntimeException denied) {
            return openPermission(context, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS,
                "Android заблокував зміну звукового режиму. Перевір доступ «Не турбувати» й повтори команду.");
        }
    }

    private static String setSystemValue(Context context, String key, int value, String title) {
        if (!Settings.System.canWrite(context)) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + context.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return "Для функції «" + title +
                    "» дозволь Джарвісу змінювати системні налаштування й повтори команду.";
            } catch (RuntimeException unavailable) {
                return "Для функції «" + title + "» потрібен дозвіл Android на зміну системних налаштувань.";
            }
        }
        try {
            if (!Settings.System.putInt(context.getContentResolver(), key, value) ||
                Settings.System.getInt(context.getContentResolver(), key, -100) != value)
                return "Android не підтвердив зміну: " + title + ".";
            boolean on = Settings.System.SCREEN_BRIGHTNESS_MODE.equals(key)
                ? value == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC : value == 1;
            return title + (on ? " увімкнено." : " вимкнено.");
        } catch (RuntimeException error) {
            return "Android не дозволив змінити: " + title + ".";
        }
    }

    private static String openPermission(Context context, String action, String explanation) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return explanation;
        } catch (RuntimeException unavailable) {
            return explanation + " Не вдалося відкрити сторінку дозволу.";
        }
    }

    private static String openSettings(Context context, String name, String... actions) {
        for (String action : actions) {
            try {
                // ACTION_WIFI panel exists from API 29; older Android uses settings.
                if (Settings.Panel.ACTION_WIFI.equals(action) && Build.VERSION.SDK_INT < 29) continue;
                Intent intent = new Intent(action);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                return "Відкриваю налаштування " + name +
                    ". Перемкни функцію вручну: Android обмежує пряме керування.";
            } catch (RuntimeException unavailable) {
                // Each manufacturer may provide a different settings component.
            }
        }
        return openPermission(context, Settings.ACTION_SETTINGS,
            "Не знайшов окремого перемикача " + name +
            ". Відкриваю загальні налаштування для ручного перемикання.");
    }
}
