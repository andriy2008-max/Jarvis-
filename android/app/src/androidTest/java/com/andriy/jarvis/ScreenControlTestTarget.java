package com.andriy.jarvis;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Standalone test APK activity: a different package from the Jarvis service. */
public final class ScreenControlTestTarget extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        Button namedButton = new Button(this);
        namedButton.setText("Тестова кнопка");
        namedButton.setOnClickListener(v -> namedButton.setText("Знайдено"));
        content.addView(namedButton);

        // Icon-only control; no visible "Пошук" label.
        // Android's accessibility description is in English as in many apps.
        Button searchIcon = new Button(this);
        searchIcon.setText("🔎");
        searchIcon.setContentDescription("Search");
        searchIcon.setOnClickListener(v -> {
            searchIcon.setText("Знайдено");
            searchIcon.setContentDescription("Відкрито");
        });
        content.addView(searchIcon);

        for (int i = 0; i < 32; i++) {
            TextView row = new TextView(this);
            row.setText("Тестовий рядок " + i);
            row.setTextSize(22f);
            row.setPadding(12, 18, 12, 18);
            content.addView(row);
        }
        setContentView(scroll);
    }
}
