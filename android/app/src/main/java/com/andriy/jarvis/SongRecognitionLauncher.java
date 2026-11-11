package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;

/** User-initiated handoff, NOT a Now Playing API or a recognition-result callback. */
public final class SongRecognitionLauncher {
    private static final String GOOGLE = "com.google.android.googlequicksearchbox";
    private SongRecognitionLauncher() {}

    public static String launch(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return "Розпізнавання поки запускається лише на телефоні. Скажи цю команду Джарвісу на Pixel.";
        // Optional Google app action. It is not a public Android contract:
        // handle its absence/denial and never report that a song was identified.
        Intent search = new Intent(GOOGLE + ".MUSIC_SEARCH").setPackage(GOOGLE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(search);
            return "Відкриваю розпізнавання Google. Результат з’явиться у Google. Щоб знову говорити з Джарвісом, натисни його іконку.";
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Intent app = context.getPackageManager().getLaunchIntentForPackage(GOOGLE);
            if (app != null) {
                try {
                    context.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return "Прямий запуск розпізнавання недоступний. У Google натисни мікрофон → «Знайти пісню».";
                } catch (ActivityNotFoundException | SecurityException ignored) { }
            }
            return "Не вдалося відкрити розпізнавання. Перевір, чи встановлений і ввімкнений застосунок Google.";
        }
    }
}
