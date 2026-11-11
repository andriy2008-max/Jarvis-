package com.andriy.jarvis;

import android.content.Context;
import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;

/** Opens the installed Shazam app; does not claim recognition or retrieve its result. */
public final class SongRecognitionLauncher {
    private static final String SHAZAM = "com.shazam.android";
    private SongRecognitionLauncher() {}

    public static String launch(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH))
            return "Запуск Shazam поки доступний на телефоні. Скажи «Шазам» Джарвісу на телефоні.";
        Intent app = context.getPackageManager().getLaunchIntentForPackage(SHAZAM);
        if (app == null)
            return "Shazam не встановлений або вимкнений. Установи чи ввімкни Shazam і повтори команду «Шазам».";
        try {
            context.startActivity(app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return "Відкриваю Shazam. Натисни його кнопку розпізнавання; результат з’явиться у Shazam.";
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            return "Android не дозволив відкрити Shazam. Спробуй відкрити його вручну.";
        }
    }
}
