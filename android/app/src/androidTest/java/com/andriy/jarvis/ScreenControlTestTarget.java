package com.andriy.jarvis;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
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

        // Real editable view in a separate test APK, with existing text.
        // Logs only a Boolean pass marker, never the content of other app fields.
        EditText editor = new EditText(this);
        editor.setSingleLine(true);
        editor.setHint("Тестове поле повідомлення");
        editor.setText("Початок ");
        editor.setSelection(editor.length());
        editor.setShowSoftInputOnFocus(false);
        editor.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start,
                                                      int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start,
                                                 int before, int count) {
                if ("Початок Привіт, Тест! 42".contentEquals(s))
                    Log.i("JARVIS_INPUT_QA", "qa_exact_text=true");
            }
            @Override public void afterTextChanged(Editable e) {}
        });
        content.addView(editor);

        // Icon-only control; no visible "Пошук" label.
        // Android's accessibility description is in English as in many apps.
        Button searchIcon = new Button(this);
        searchIcon.setText("🔎");
        searchIcon.setContentDescription("Search");
        searchIcon.setOnClickListener(v -> {
            searchIcon.setText("Знайдено");
            searchIcon.setContentDescription("Відкрито");
            // A real user can tap the input; the fixture simulates that after
            // opening Search, keeping the focus on an actual editable field.
            editor.requestFocus();
            editor.setSelection(editor.length());
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
