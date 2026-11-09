package com.andriy.jarvis;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.AlarmClock;
import java.net.URLEncoder;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.TypedValue;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.List;

public final class MainActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_MICROPHONE = 101;
    private static final int BG = Color.rgb(8, 14, 29);
    private static final int PANEL = Color.rgb(17, 27, 48);
    private static final int BORDER = Color.rgb(38, 57, 87);
    private static final int CYAN = Color.rgb(80, 223, 251);
    private static final int CYAN_DARK = Color.rgb(49, 199, 237);
    private static final int GREEN = Color.rgb(107, 232, 191);
    private static final int WHITE = Color.rgb(237, 246, 255);
    private static final int MUTED = Color.rgb(161, 179, 208);
    private static final int RED = Color.rgb(255, 124, 145);

    private final ArrayList<String> notes = new ArrayList<>();
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerCallback;
    private long timerDeadlineMs = 0L;

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private DeviceActions deviceActions;
    private AiChatClient aiChatClient;
    private boolean aiBusy = false;
    private TextView status;
    private ScrollView feedScroll;
    private LinearLayout feedMessages;
    private TextView notesTitle;
    private LinearLayout notesList;
    private CheckBox voiceToggle;
    private EditText input;
    private Button microphone;
    private PixelFace face;
    private boolean listening = false;
    private boolean processing = false;
    private boolean speaking = false;
    private boolean isCompact = false;
    private boolean isWatchDevice = false;
    private boolean voiceEnabled = true;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        isWatchDevice = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        isCompact = getResources().getConfiguration().smallestScreenWidthDp < 360;
        voiceEnabled = getPreferences(MODE_PRIVATE).getBoolean("voiceEnabled", true);
        loadNotes();
        aiChatClient = new AiChatClient(this);
        buildInterface();
        deviceActions = new DeviceActions(this);

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int supported = tts.setLanguage(Locale.forLanguageTag("uk-UA"));
                if (supported == TextToSpeech.LANG_MISSING_DATA || supported == TextToSpeech.LANG_NOT_SUPPORTED) {
                    appendDialogue("Джарвіс", "Для озвучення встанови український голос TTS у налаштуваннях Android.");
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        runOnUiThread(() -> {
                            if (!voiceEnabled) return;
                            speaking = true;
                            face.setState("speaking");
                        });
                    }
                    @Override public void onDone(String id) { endSpeaking(); }
                    @Override public void onError(String id) { endSpeaking(); }
                });
            }
        });

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(this);
        } else {
            setStatus("Розпізнавання мовлення не встановлено. Текстові команди працюють.");
        }
    }

    private int dp(float value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void styleText(TextView view, String value, float sizeSp, int color, int gravity, boolean bold) {
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setGravity(gravity);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private TextView makeText(String value, float sizeSp, int color, int gravity, boolean bold) {
        TextView view = new TextView(this);
        styleText(view, value, sizeSp, color, gravity, bold);
        return view;
    }

    private GradientDrawable roundedFill(int fill, int stroke, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setCornerRadius(dp(radiusDp));
        d.setColor(fill);
        if (stroke != 0) d.setStroke(dp(1), stroke);
        return d;
    }

    private Button primaryButton(String title, Runnable callback) {
        Button btn = new Button(this);
        btn.setText(title);
        btn.setAllCaps(false);
        btn.setTextColor(Color.rgb(3, 23, 37));
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 16 : 17);
        GradientDrawable bg = new GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            new int[]{Color.rgb(145, 246, 255), CYAN_DARK}
        );
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.rgb(124, 232, 255));
        btn.setBackground(bg);
        btn.setBackgroundTintList(null);
        btn.setPadding(dp(20), dp(14), dp(20), dp(14));
        btn.setOnClickListener(v -> callback.run());
        return btn;
    }

    private Button secondaryButton(String title, Runnable callback) {
        Button btn = new Button(this);
        btn.setText(title);
        btn.setAllCaps(false);
        btn.setTextColor(WHITE);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 14 : 15);
        btn.setBackground(roundedFill(Color.rgb(19, 46, 73), Color.rgb(49, 76, 111), 12));
        btn.setBackgroundTintList(null);
        btn.setPadding(dp(14), dp(12), dp(14), dp(12));
        btn.setOnClickListener(v -> callback.run());
        return btn;
    }

    private LinearLayout panel() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackground(roundedFill(PANEL, BORDER, 18));
        layout.setPadding(dp(isCompact ? 14 : 18), dp(isCompact ? 14 : 18), dp(isCompact ? 14 : 18), dp(isCompact ? 14 : 18));
        return layout;
    }

    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        GradientDrawable nightSky = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            new int[]{Color.rgb(18, 49, 92), BG, Color.rgb(6, 10, 19)});
        scroll.setBackground(nightSky);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        // Explicit edge-to-edge makes the inset calculation consistent on Android 15+.
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));
        scroll.setOnApplyWindowInsetsListener((v, insets) -> {
            content.setPadding(dp(16), insets.getSystemWindowInsetTop() + dp(16),
                dp(16), insets.getSystemWindowInsetBottom() + dp(28));
            return insets;
        });
        scroll.addView(content, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        LinearLayout topbar = new LinearLayout(this);
        topbar.setOrientation(LinearLayout.HORIZONTAL);
        topbar.setGravity(Gravity.CENTER_VERTICAL);
        topbar.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        brand.setLayoutParams(brandParams);

        TextView brandMark = makeText("J", 20, CYAN, Gravity.CENTER, true);
        brandMark.setBackground(roundedFill(Color.TRANSPARENT, Color.rgb(76, 219, 255), 11));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        brand.addView(brandMark, markParams);

        LinearLayout brandText = new LinearLayout(this);
        brandText.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams btParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        btParams.leftMargin = dp(12);
        brand.addView(brandText, btParams);

        TextView brandTitle = makeText("J.A.R.V.I.S.", isCompact ? 13 : 15, WHITE, Gravity.START, true);
        brandTitle.setLetterSpacing(0.18f);
        brandText.addView(brandTitle);
        TextView brandSub = makeText("PERSONAL VOICE ASSISTANT", isCompact ? 9 : 10, MUTED, Gravity.START, false);
        brandSub.setLetterSpacing(0.12f);
        brandText.addView(brandSub);

        topbar.addView(brand);

        TextView version = makeText("● V 0.1.6", isCompact ? 11 : 12, GREEN, Gravity.CENTER, false);
        version.setBackground(roundedFill(Color.TRANSPARENT, Color.rgb(43, 131, 108), 99));
        version.setPadding(dp(10), dp(6), dp(10), dp(6));
        if (!isWatchDevice) topbar.addView(version);
        content.addView(topbar);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        heroParams.topMargin = dp(18);
        content.addView(hero, heroParams);

        face = new PixelFace();
        int faceSize = Math.min(dp(isWatchDevice ? 164 : isCompact ? 192 : 210),
            getResources().getDisplayMetrics().widthPixels - dp(32));
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(faceSize, faceSize);
        hero.addView(face, faceParams);

        TextView hello = makeText("Вітаю, я Джарвіс", isWatchDevice ? 19 : isCompact ? 28 : 32, WHITE, Gravity.CENTER, true);
        SpannableString greeting = new SpannableString("Вітаю, я Джарвіс");
        greeting.setSpan(new ForegroundColorSpan(CYAN), 7, greeting.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        hello.setText(greeting);
        LinearLayout.LayoutParams helloParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        helloParams.topMargin = dp(10);
        hero.addView(hello, helloParams);

        TextView subtitle = makeText("Голосовий помічник українською. Натисни мікрофон або введи команду нижче.", isWatchDevice ? 11 : isCompact ? 13 : 14, MUTED, Gravity.CENTER, false);
        subtitle.setLineSpacing(0f, 1.2f);
        subtitle.setPadding(dp(10), dp(8), dp(10), 0);
        hero.addView(subtitle);

        LinearLayout statusWrap = new LinearLayout(this);
        statusWrap.setOrientation(LinearLayout.HORIZONTAL);
        statusWrap.setGravity(Gravity.CENTER_VERTICAL);
        statusWrap.setBackground(roundedFill(Color.argb(184, 16, 29, 51), BORDER, 99));
        statusWrap.setPadding(dp(12), dp(9), dp(12), dp(9));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = dp(16);
        hero.addView(statusWrap, statusParams);

        View dot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(GREEN);
        dot.setBackground(dotBg);
        statusWrap.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));

        status = makeText("Система готова до команд", isCompact ? 12 : 13, Color.rgb(181, 221, 248), Gravity.START, false);
        LinearLayout.LayoutParams st = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        st.leftMargin = dp(8);
        statusWrap.addView(status, st);

        microphone = primaryButton("🎙️ Говорити з Джарвісом", this::toggleListening);
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(Math.min(dp(260), LinearLayout.LayoutParams.MATCH_PARENT), dp(54));
        micParams.topMargin = dp(18);
        micParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        hero.addView(microphone, micParams);

        TextView hint = makeText(isWatchDevice
            ? "Бокова кнопка: натисни, щоб почати запис, ще раз — щоб надіслати."
            : "Скажи: «Джарвіс, котра година?» або «Джарвіс, запиши купити хліб».",
            isCompact ? 11 : 12, MUTED, Gravity.CENTER, false);
        hint.setLineSpacing(0f, 1.22f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(10);
        hero.addView(hint, hintParams);

        // Conversation card: the same hierarchy as web (title, voice toggle, bubbles, command field).
        LinearLayout chatPanel = panel();
        LinearLayout.LayoutParams chatParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chatParams.topMargin = dp(20);
        content.addView(chatPanel, chatParams);

        LinearLayout chatHeader = new LinearLayout(this);
        chatHeader.setOrientation(LinearLayout.HORIZONTAL);
        chatHeader.setGravity(Gravity.CENTER_VERTICAL);
        chatPanel.addView(chatHeader, new LinearLayout.LayoutParams(-1, -2));
        TextView dialogHeading = panelHeading("ДІАЛОГ");
        chatHeader.addView(dialogHeading, new LinearLayout.LayoutParams(0, -2, 1f));
        voiceToggle = new CheckBox(this);
        voiceToggle.setText("Голосові відповіді");
        voiceToggle.setTextColor(MUTED);
        voiceToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 10 : 12);
        voiceToggle.setButtonTintList(ColorStateList.valueOf(CYAN));
        voiceToggle.setChecked(voiceEnabled);
        voiceToggle.setOnCheckedChangeListener((btn, checked) -> {
            voiceEnabled = checked;
            getPreferences(MODE_PRIVATE).edit().putBoolean("voiceEnabled", checked).apply();
            if (!checked) {
                speaking = false;
                if (tts != null) tts.stop();
                if (face != null) face.setState(listening ? "listening" : processing ? "thinking" : "idle");
            }
        });
        chatHeader.addView(voiceToggle);

        feedScroll = new ScrollView(this);
        feedScroll.setFillViewport(true);
        feedScroll.setVerticalScrollBarEnabled(false);
        LinearLayout.LayoutParams feedParams = new LinearLayout.LayoutParams(-1, dp(isWatchDevice ? 154 : 220));
        feedParams.topMargin = dp(13);
        chatPanel.addView(feedScroll, feedParams);
        feedMessages = new LinearLayout(this);
        feedMessages.setOrientation(LinearLayout.VERTICAL);
        feedMessages.setGravity(Gravity.TOP);
        feedScroll.addView(feedMessages, new ScrollView.LayoutParams(-1, -2));

        LinearLayout commandRow = new LinearLayout(this);
        commandRow.setOrientation(LinearLayout.HORIZONTAL);
        commandRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams commandParams = new LinearLayout.LayoutParams(-1, -2);
        commandParams.topMargin = dp(14);
        chatPanel.addView(commandRow, commandParams);
        input = new EditText(this);
        input.setHint("Напиши команду Джарвісу...");
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 13 : 14);
        input.setBackground(roundedFill(Color.rgb(9, 21, 40), Color.rgb(52, 73, 108), 12));
        input.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        commandRow.addView(input, inputParams);
        Button send = secondaryButton("Надіслати", () -> {
            String command = input.getText().toString();
            input.setText("");
            processCommand(command);
        });
        send.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 12 : 13);
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(isWatchDevice ? 76 : 94), dp(50));
        sendParams.leftMargin = dp(8);
        commandRow.addView(send, sendParams);

        // Voice/text chat and local device commands share the same conversation feed.
        LinearLayout aiControls = new LinearLayout(this);
        aiControls.setOrientation(isWatchDevice ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams aiControlsParams = new LinearLayout.LayoutParams(-1, -2);
        aiControlsParams.topMargin = dp(10);
        chatPanel.addView(aiControls, aiControlsParams);
        Button aiSettingsButton = secondaryButton("🤖 ШІ — підключення", this::showAiSettings);
        aiSettingsButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 10 : 12);
        LinearLayout.LayoutParams aiConnectParams = isWatchDevice
            ? new LinearLayout.LayoutParams(-1, -2)
            : new LinearLayout.LayoutParams(0, -2, 1f);
        aiControls.addView(aiSettingsButton, aiConnectParams);
        Button aiResetButton = secondaryButton("🧹 Новий діалог", this::clearAiConversation);
        aiResetButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 10 : 12);
        LinearLayout.LayoutParams aiResetParams = isWatchDevice
            ? new LinearLayout.LayoutParams(-1, -2)
            : new LinearLayout.LayoutParams(0, -2, 1f);
        if (isWatchDevice) aiResetParams.topMargin = dp(6);
        else aiResetParams.leftMargin = dp(8);
        aiControls.addView(aiResetButton, aiResetParams);

        // Web quick-command chips, in responsive two-column rows.
        LinearLayout examplesPanel = panel();
        LinearLayout.LayoutParams exParams = new LinearLayout.LayoutParams(-1, -2);
        exParams.topMargin = dp(18);
        content.addView(examplesPanel, exParams);
        examplesPanel.addView(panelHeading("СПРОБУЙ КОМАНДИ"));
        String[] examples = {
            "🕒 Котра година?", "▶ Відкрий YouTube",
            "✦ Відкрий ChatGPT", "✎ Запиши купити молоко",
            "☷ Покажи нотатки", "? Що ти вмієш?",
            "🔋 Заряд батареї", "⏱ Таймер на 1 хвилину",
            "⚙ Налаштування", "🗺 Відкрий карти",
            "⏰ Будильник 07:30", "🔔 Нагадай через 5 хв",
            "📱 Відкрий Telegram", "⏰ Покажи будильники",
            "🔦 Увімкни ліхтарик", "⏸ Пауза музики",
            "🔉 Гучність 50%", "☀ Налаштування екрана",
            "💬 Поговорімо", "🤔 Поясни мені"
        };
        String[] commands = {
            "Котра година", "Відкрий YouTube",
            "Відкрий ChatGPT", "Запиши купити молоко",
            "Покажи нотатки", "Що ти вмієш",
            "Заряд батареї", "Таймер на 1 хвилину",
            "Відкрий налаштування", "Відкрий карти",
            "Постав будильник на 07:30", "Нагадай через 5 хвилин випити води",
            "Відкрий Телеграм", "Покажи будильники",
            "Увімкни ліхтарик", "Постав музику на паузу",
            "Постав гучність на 50%", "Відкрий налаштування екрана",
            "Привіт, Джарвіс", "Поясни простими словами, як працює штучний інтелект"
        };
        for (int i = 0; i < examples.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = dp(8);
            examplesPanel.addView(row, rowParams);
            for (int j = i; j < Math.min(i + 2, examples.length); j++) {
                final String command = commands[j];
                Button quick = secondaryButton(examples[j], () -> processCommand(command));
                quick.setTypeface(Typeface.DEFAULT);
                quick.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 10 : 12);
                quick.setMinHeight(dp(48));
                quick.setPadding(dp(7), dp(7), dp(7), dp(7));
                LinearLayout.LayoutParams quickParams = new LinearLayout.LayoutParams(0, -2, 1f);
                if (j % 2 == 1) quickParams.leftMargin = dp(8);
                row.addView(quick, quickParams);
            }
        }

        // Live notes list: web-style note cards, count, deletion and explanatory text.
        LinearLayout notesPanel = panel();
        LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(-1, -2);
        notesParams.topMargin = dp(18);
        content.addView(notesPanel, notesParams);
        notesTitle = panelHeading("МОЇ НОТАТКИ (0)");
        notesPanel.addView(notesTitle);
        notesList = new LinearLayout(this);
        notesList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(12);
        notesPanel.addView(notesList, listParams);
        TextView noteHint = makeText("Зберігаються тільки на цьому пристрої, без синхронізації.",
            isCompact ? 11 : 12, MUTED, Gravity.START, false);
        LinearLayout.LayoutParams noteHintParams = new LinearLayout.LayoutParams(-1, -2);
        noteHintParams.topMargin = dp(12);
        notesPanel.addView(noteHint, noteHintParams);

        TextView footer = makeText("JARVIS 0.1.6 • Тестовий Android-застосунок. Без GPT і акаунтів; для голосу потрібен сервіс розпізнавання Android.",
            isCompact ? 11 : 12, Color.rgb(127, 154, 184), Gravity.CENTER, false);
        footer.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(18);
        content.addView(footer, footerParams);

        setContentView(scroll);
        renderNotes();
        appendDialogue("Джарвіс", "Система запущена. Я готовий до перших команд. Спробуй запитати, що я вмію.");
    }

    private TextView panelHeading(String title) {
        TextView view = makeText(title, isCompact ? 13 : 14,
            Color.rgb(185, 214, 240), Gravity.START, true);
        view.setLetterSpacing(0.10f);
        return view;
    }

    private void setStatus(String message) {
        if (status != null) status.setText(message);
    }

    private void appendDialogue(String who, String message) {
        if (feedMessages == null) return;
        boolean user = "Ти".equals(who);
        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(12), dp(10), dp(12), dp(10));
        bubble.setBackground(roundedFill(
            user ? Color.rgb(17, 53, 74) : Color.rgb(23, 33, 57),
            user ? Color.rgb(36, 97, 122) : BORDER, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        if (user) params.leftMargin = dp(28);
        else params.rightMargin = dp(20);
        feedMessages.addView(bubble, params);
        TextView name = makeText(user ? "ТИ" : "ДЖАРВІС", 11,
            user ? Color.rgb(164, 232, 210) : CYAN, Gravity.START, true);
        name.setLetterSpacing(0.09f);
        bubble.addView(name);
        TextView body = makeText(message.trim(), isCompact ? 13 : 14, WHITE, Gravity.START, false);
        body.setLineSpacing(0f, 1.18f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = dp(6);
        bubble.addView(body, bodyParams);
        while (feedMessages.getChildCount() > 40) feedMessages.removeViewAt(0);
        feedScroll.post(() -> feedScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void renderNotes() {
        if (notesList == null || notesTitle == null) return;
        notesTitle.setText("МОЇ НОТАТКИ (" + notes.size() + ")");
        notesList.removeAllViews();
        if (notes.isEmpty()) {
            TextView empty = makeText("Тут з'являться твої нотатки.", 13, MUTED, Gravity.START, false);
            notesList.addView(empty);
            return;
        }
        for (int i = 0; i < notes.size(); i++) {
            final int idx = i;
            LinearLayout noteRow = new LinearLayout(this);
            noteRow.setOrientation(LinearLayout.HORIZONTAL);
            noteRow.setGravity(Gravity.CENTER_VERTICAL);
            noteRow.setPadding(dp(10), dp(7), dp(7), dp(7));
            noteRow.setBackground(roundedFill(Color.rgb(13, 27, 48), BORDER, 10));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) rowParams.topMargin = dp(8);
            notesList.addView(noteRow, rowParams);
            TextView noteText = makeText(notes.get(i), isCompact ? 12 : 13, WHITE, Gravity.START, false);
            noteRow.addView(noteText, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView delete = makeText("×", 24, RED, Gravity.CENTER, false);
            delete.setContentDescription("Видалити нотатку: " + notes.get(i));
            delete.setClickable(true);
            delete.setFocusable(true);
            delete.setOnClickListener(v -> {
                if (idx < notes.size()) {
                    notes.remove(idx);
                    saveNotes();
                    renderNotes();
                }
            });
            noteRow.addView(delete, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
    }

    private void toggleListening() {
        if (processing) {
            setStatus("Обробляю команду...");
            return;
        }
        if (listening) {
            listening = false;
            processing = true;
            microphone.setText("Обробляю...");
            face.setState("thinking");
            setStatus("Розпізнаю запис...");
            if (recognizer != null) recognizer.stopListening();
            return;
        }
        if (recognizer == null) {
            setStatus("Сервіс розпізнавання відсутній. Спробуй текстову команду.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        beginListening();
    }

    private void beginListening() {
        if (recognizer == null || processing || listening) return;
        if (tts != null) tts.stop();
        speaking = false;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uk-UA");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        listening = true;
        microphone.setText("■ Зупинити прослуховування");
        face.setState("listening");
        setStatus("Слухаю тебе...");
        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            resetListening("Не вдалося запустити запис. Спробуй ще раз.");
        }
    }

    private void resetListening(String message) {
        listening = false;
        processing = false;
        microphone.setText("🎙️ Говорити з Джарвісом");
        face.setState(speaking ? "speaking" : "idle");
        setStatus(message);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_MICROPHONE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) beginListening();
            else setStatus("Дозволь доступ до мікрофона в налаштуваннях застосунку.");
        } else if (code == DeviceActions.REQUEST_CAMERA_FOR_TORCH && deviceActions != null) {
            String feedback = deviceActions.onCameraPermissionResult(
                results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
            if (feedback != null) {
                appendDialogue("Джарвіс", feedback);
                setStatus(feedback);
                if (tts != null && voiceEnabled) {
                    tts.speak(feedback, TextToSpeech.QUEUE_FLUSH, new Bundle(),
                        "jarvis-torch-" + SystemClock.uptimeMillis());
                }
            }
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { setStatus("Слухаю — говори..."); }
    @Override public void onBeginningOfSpeech() { setStatus("Розпізнаю голос..."); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }

    @Override    public void onEndOfSpeech() {
        if (listening) {
            listening = false;
            processing = true;
            microphone.setText("Обробляю...");
            face.setState("thinking");
            setStatus("Обробляю команду...");
        }
    }

    @Override
    public void onError(int error) {
        String message = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ? "Не вдалося розпізнати. Спробуй ще раз."
            : "Помилка голосу (" + error + "). Перевір розпізнавання в Android.";
        resetListening(message);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        resetListening("Команду розпізнано");
        if (matches != null && !matches.isEmpty()) processCommand(matches.get(0));
        else setStatus("Порожня команда. Спробуй ще раз.");
    }

    @Override public void onPartialResults(Bundle results) { }
    @Override public void onEvent(int eventType, Bundle params) { }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event != null && event.getRepeatCount() == 0 &&
            (keyCode == KeyEvent.KEYCODE_STEM_1 || keyCode == KeyEvent.KEYCODE_STEM_2 || keyCode == KeyEvent.KEYCODE_STEM_3)) {
            toggleListening();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private String launchIntent(Intent intent, String label) {
        try {
            startActivity(intent);
            return "Відкриваю " + label + ".";
        } catch (Exception exception) {
            return "Не вдалося відкрити " + label + " на цьому пристрої.";
        }
    }

    private String openUrl(String url, String label) {
        return launchIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(url)), label);
    }

    /** System Clock owns the alarm; the user sees and confirms the request there. */
    private String openAlarmClock(AssistantIntents.ClockTime time, String message) {
        Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(AlarmClock.EXTRA_HOUR, time.hour);
        intent.putExtra(AlarmClock.EXTRA_MINUTES, time.minute);
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, message);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        try {
            startActivity(intent);
            return "Відкрив Годинник: " + String.format(Locale.forLanguageTag("uk-UA"), "%02d:%02d", time.hour, time.minute)
                + ". Перевір і підтвердь будильник у системному застосунку.";
        } catch (Exception exception) {
            return "На цьому пристрої не вдалося відкрити системний Годинник для будильника.";
        }
    }

    /** Reminders use the Clock app's persistent timer/alarm, not Jarvis's in-process Handler. */
    private String openReminder(AssistantIntents.Reminder reminder) {
        if (reminder.time != null) {
            return openAlarmClock(reminder.time, "Нагадування: " + reminder.message);
        }
        Intent intent = new Intent(AlarmClock.ACTION_SET_TIMER);
        intent.putExtra(AlarmClock.EXTRA_LENGTH, reminder.seconds);
        intent.putExtra(AlarmClock.EXTRA_MESSAGE, "Нагадування: " + reminder.message);
        intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
        try {
            startActivity(intent);
            return "Відкрив системний таймер для нагадування «" + reminder.message
                + "». Перевір його запуск у Годиннику.";
        } catch (Exception exception) {
            return "Не вдалося відкрити системний таймер. Перевір наявність застосунку Годинник.";
        }
    }

    /** Discover all launchable apps visible to this Android profile, not a fixed app allowlist. */
    private String launchInstalledApp(String spoken) {
        Intent query = new Intent(Intent.ACTION_MAIN);
        query.addCategory(Intent.CATEGORY_LAUNCHER);
        try {
            PackageManager manager = getPackageManager();
            List<ResolveInfo> candidates = manager.queryIntentActivities(query, 0);
            List<AppNameMatcher.App> installed = new ArrayList<>();
            for (ResolveInfo candidate : candidates) {
                if (candidate.activityInfo == null || candidate.activityInfo.packageName == null) continue;
                String pkg = candidate.activityInfo.packageName;
                String activity = candidate.activityInfo.name;
                if (activity == null) continue;
                CharSequence visible = candidate.loadLabel(manager);
                String label = visible == null ? "" : visible.toString().trim();
                if (!label.isEmpty()) installed.add(new AppNameMatcher.App(pkg, activity, label));
                if (candidate.activityInfo.applicationInfo != null) {
                    CharSequence application = manager.getApplicationLabel(candidate.activityInfo.applicationInfo);
                    String appLabel = application == null ? "" : application.toString().trim();
                    if (!appLabel.isEmpty() && !appLabel.equalsIgnoreCase(label)) {
                        installed.add(new AppNameMatcher.App(pkg, activity, appLabel));
                    }
                }
            }
            AppNameMatcher.Result result = AppNameMatcher.match(spoken, installed);
            if (result.ambiguous()) {
                String[] labels = new String[result.alternatives.size()];
                for (int i = 0; i < labels.length; i++) {
                    AppNameMatcher.App app = result.alternatives.get(i);
                    boolean repeated = false;
                    for (int k = 0; k < labels.length; k++) {
                        if (i != k && app.label.equalsIgnoreCase(result.alternatives.get(k).label)) {
                            repeated = true;
                            break;
                        }
                    }
                    labels[i] = repeated ? app.label + " (" + app.packageName + ")" : app.label;
                }
                new AlertDialog.Builder(this)
                    .setTitle("Яку програму відкрити?")
                    .setItems(labels, (dialog, which) -> {
                        String launched = startMatchedApp(result.alternatives.get(which));
                        appendDialogue("Джарвіс", launched);
                        setStatus(launched);
                    })
                    .setNegativeButton("Скасувати", null)
                    .show();
                return "Знайшов кілька відповідних програм. Обери потрібну на екрані.";
            }
            if (!result.found()) return null;
            return startMatchedApp(result.app);
        } catch (Exception exception) {
            return "Не вдалося отримати список програм. Перевір доступність меню застосунків Android.";
        }
    }

    /** Start the exact launcher activity; never substitute a website for an installed app. */
    private String startMatchedApp(AppNameMatcher.App app) {
        try {
            Intent launch = new Intent(Intent.ACTION_MAIN);
            launch.addCategory(Intent.CATEGORY_LAUNCHER);
            launch.setClassName(app.packageName, app.activityName);
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            startActivity(launch);
            return "Відкриваю «" + app.label + "».";
        } catch (Exception ignored) {
            try {
                Intent fallback = getPackageManager().getLaunchIntentForPackage(app.packageName);
                if (fallback != null) {
                    startActivity(fallback);
                    return "Відкриваю «" + app.label + "».";
                }
            } catch (Exception second) {
                // No exported launch activity was available for this installed package.
            }
            return "Не вдалося відкрити «" + app.label + "». Перевір, чи програма доступна для запуску.";
        }
    }
    private String batteryReport() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return "Не вдалося дізнатися рівень заряду.";
        int level = battery.getIntExtra("level", -1);
        int scale = battery.getIntExtra("scale", -1);
        if (level < 0 || scale <= 0) return "Не вдалося дізнатися рівень заряду.";
        int percent = Math.round(level * 100f / scale);
        int plugged = battery.getIntExtra("plugged", 0);
        return "Заряд батареї: " + percent + " відсотків." +
            (plugged != 0 ? " Пристрій заряджається." : "");
    }

    private String setMusicVolume(int direction) {
        try {
            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audio == null || audio.isVolumeFixed()) return "Гучність на цьому пристрої не можна змінити.";
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI);
            int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            return "Медіагучність: " + (max > 0 ? Math.round(current * 100f / max) : 0) + " відсотків.";
        } catch (SecurityException exception) {
            return "Android не дозволив змінити гучність.";
        }
    }

    private String timerLabel(long seconds) {
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainder = seconds % 60L;
        if (hours > 0) return hours + " год " + minutes + " хв";
        if (minutes > 0) return minutes + " хв " + remainder + " с";
        return remainder + " с";
    }

    private void cancelTimerInternal() {
        if (timerCallback != null) timerHandler.removeCallbacks(timerCallback);
        timerCallback = null;
        timerDeadlineMs = 0L;
    }

    private String startTimer(int seconds) {
        if (seconds <= 0) return "Можна встановити таймер від 1 секунди до 3 годин.";
        cancelTimerInternal();
        timerDeadlineMs = SystemClock.elapsedRealtime() + seconds * 1000L;
        timerCallback = () -> {
            timerCallback = null;
            timerDeadlineMs = 0L;
            String finished = "Час вийшов! Таймер завершено.";
            appendDialogue("Джарвіс", finished);
            setStatus(finished);
            if (voiceEnabled && tts != null) {
                tts.speak(finished, TextToSpeech.QUEUE_FLUSH, null,
                    "jarvis-timer-" + SystemClock.uptimeMillis());
            }
        };
        timerHandler.postDelayed(timerCallback, seconds * 1000L);
        return "Таймер на " + timerLabel(seconds) + " запущено. Він працює, поки застосунок активний.";
    }

    private String cancelTimer() {
        if (timerCallback == null) return "Активного таймера немає.";
        cancelTimerInternal();
        return "Таймер скасовано.";
    }

    private String timerRemaining() {
        if (timerCallback == null) return "Активного таймера немає.";
        long remaining = Math.max(0L,
            (timerDeadlineMs - SystemClock.elapsedRealtime() + 999L) / 1000L);
        return "До завершення таймера: " + timerLabel(remaining) + ".";
    }

    private void confirmDeleteAllNotes() {
        new AlertDialog.Builder(this)
            .setTitle("Очистити всі нотатки?")
            .setMessage("Цю дію не можна скасувати.")
            .setNegativeButton("Ні", (dialog, which) -> {})
            .setPositiveButton("Видалити", (dialog, which) -> {
                notes.clear();
                saveNotes();
                renderNotes();
                appendDialogue("Джарвіс", "Усі нотатки видалено.");
            })
            .show();
    }

    private void stopSpeaking() {
        if (tts != null) tts.stop();
        speaking = false;
        if (face != null) face.setState(listening ? "listening" : "idle");
    }


    /** Unrecognized conversation goes to AI; local phone commands never give AI device control. */
    private void askAi(String question) {
        if (aiChatClient == null || !aiChatClient.isConfigured()) {
            String info = "Для вільної розмови підключи особистий ШІ-сервер. Натисни «ШІ — підключення». Ключ OpenAI у телефон вводити не потрібно.";
            appendDialogue("Джарвіс", info);
            setStatus("ШІ не підключений");
            showAiSettings();
            return;
        }
        if (aiBusy) {
            appendDialogue("Джарвіс", "Я ще відповідаю на попереднє запитання.");
            return;
        }
        aiBusy = true;
        processing = true;
        face.setState("thinking");
        setStatus("Джарвіс думає...");
        aiChatClient.send(question, (answer, error) -> {
            aiBusy = false;
            processing = false;
            if (isFinishing() || isDestroyed()) return;
            String response = error == null ? answer : error;
            if (response == null || response.isEmpty()) response = "Не вдалося отримати відповідь від ШІ.";
            appendDialogue("Джарвіс", response);
            setStatus(error == null ? "Відповідь ШІ готова" : "Не вдалося звернутися до ШІ");
            if (error == null && voiceEnabled && tts != null) {
                tts.speak(response, TextToSpeech.QUEUE_FLUSH, new Bundle(),
                    "jarvis-ai-" + SystemClock.uptimeMillis());
            } else {
                face.setState("idle");
            }
        });
    }

    private void clearAiConversation() {
        if (aiChatClient != null) aiChatClient.clearHistory();
        aiBusy = false;
        processing = false;
        if (face != null) face.setState("idle");
        setStatus("Новий діалог ШІ");
        appendDialogue("Джарвіс", "Контекст ШІ очищено. Можемо почати нову розмову.");
    }

    private void showAiSettings() {
        if (aiChatClient == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(10), dp(20), dp(6));

        TextView description = makeText(
            "Введи адресу особистого HTTPS-сервера /chat та код доступу. Це НЕ ключ OpenAI — він має залишатися тільки на сервері.",
            13, MUTED, Gravity.START, false);
        form.addView(description, new LinearLayout.LayoutParams(-1, -2));

        EditText urlInput = new EditText(this);
        urlInput.setSingleLine(true);
        urlInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setHint("https://...workers.dev/chat");
        urlInput.setText(aiChatClient.endpoint());
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, -2);
        urlParams.topMargin = dp(10);
        form.addView(urlInput, urlParams);

        EditText codeInput = new EditText(this);
        codeInput.setSingleLine(true);
        codeInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        codeInput.setHint("Код доступу (не OpenAI API key)");
        LinearLayout.LayoutParams codeParams = new LinearLayout.LayoutParams(-1, -2);
        codeParams.topMargin = dp(8);
        form.addView(codeInput, codeParams);
        TextView note = makeText(
            "Якщо код уже збережено, залиш поле порожнім. «Від'єднати» очищує адресу, код і контекст ШІ.",
            11, MUTED, Gravity.START, false);
        form.addView(note, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Підключення Джарвіса до ШІ")
            .setView(form)
            .setNegativeButton("Скасувати", null)
            .setNeutralButton("Від'єднати", (d, which) -> {
                aiChatClient.disconnect();
                aiBusy = false;
                processing = false;
                appendDialogue("Джарвіс", "Підключення ШІ видалено з телефону.");
                setStatus("ШІ від'єднаний");
            })
            .setPositiveButton("Зберегти", null)
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                aiChatClient.configure(urlInput.getText().toString(), codeInput.getText().toString());
                aiBusy = false;
                processing = false;
                setStatus("Налаштування ШІ збережено");
                appendDialogue("Джарвіс", "Адресу та код доступу збережено. Тепер можемо спілкуватися!");
                dialog.dismiss();
            } catch (Exception error) {
                urlInput.setError(error.getMessage() == null ? "Перевір адресу й код." : error.getMessage());
            }
        }));
        dialog.show();
    }

    private void processCommand(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        appendDialogue("Ти", raw.trim());
        String command = CommandPatterns.normalize(raw);
        if (command.isEmpty()) return;

        Integer requestedTimer = CommandPatterns.timerSeconds(command);
        Integer noteIndex = CommandPatterns.noteIndex(command);
        String dialNumber = CommandPatterns.dialNumber(command);
        String searchTerms = CommandPatterns.searchTerms(command);
        AssistantIntents.ClockTime alarmTime = AssistantIntents.alarmTime(command);
        AssistantIntents.Reminder reminder = AssistantIntents.reminder(command);
        String requestedApp = AssistantIntents.requestedApp(command);
        DeviceCommands.Action deviceCommand = DeviceCommands.parse(command);
        boolean silentReply = false;
        String reply;

        if (deviceCommand != null) {
            reply = deviceActions == null ? "Керування пристроєм ще не готове." : deviceActions.perform(deviceCommand);
        } else if (command.equals("новий діалог") || command.equals("очисти діалог") ||
            command.equals("забудь розмову") || command.equals("почни новий діалог")) {
            clearAiConversation();
            return;
        } else if (command.equals("стоп") || command.equals("замовкни") ||
            command.equals("зупини відповідь") || command.equals("припини говорити")) {
            stopSpeaking();
            reply = "Озвучення зупинено.";
            silentReply = true;
        } else if (command.equals("покажи будильники") || command.equals("відкрий будильники")) {
            reply = launchIntent(new Intent(AlarmClock.ACTION_SHOW_ALARMS), "список будильників");
        } else if (alarmTime != null) {
            reply = openAlarmClock(alarmTime, "Будильник Джарвіса");
        } else if (AssistantIntents.isAlarmCommand(command)) {
            reply = "Вкажи час у 24-годинному форматі, наприклад: постав будильник на 07:30.";
        } else if (reminder != null) {
            reply = openReminder(reminder);
        } else if (AssistantIntents.isReminderCommand(command)) {
            reply = "Скажи: нагадай через 10 хвилин випити води, або нагадай о 18:30 тренування. Дату «завтра» ця версія ще не підтримує.";
        } else if (command.contains("котра година") || command.contains("яка година") || command.contains("скільки часу")) {
            reply = "Зараз " + DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("uk", "UA")).format(new Date()) + ".";
        } else if (command.contains("яка сьогодні дата") || command.contains("який сьогодні день") || command.contains("сьогоднішня дата")) {
            reply = "Сьогодні " + DateFormat.getDateInstance(DateFormat.FULL, new Locale("uk", "UA")).format(new Date()) + ".";
        } else if (command.contains("заряд батареї") || command.contains("скільки заряду") ||
            command.contains("заряд телефону") || command.contains("заряд годинника")) {
            reply = batteryReport();
        } else if (command.equals("гучніше") || command.equals("зроби гучніше") || command.equals("додай гучність")) {
            reply = setMusicVolume(AudioManager.ADJUST_RAISE);
        } else if (command.equals("тихіше") || command.equals("зроби тихіше") || command.equals("зменш гучність")) {
            reply = setMusicVolume(AudioManager.ADJUST_LOWER);
        } else if (command.equals("вимкни голос") || command.equals("вимкни озвучення")) {
            voiceToggle.setChecked(false);
            reply = "Голосові відповіді вимкнено.";
            silentReply = true;
        } else if (command.equals("увімкни голос") || command.equals("увімкни озвучення")) {
            voiceToggle.setChecked(true);
            reply = "Голосові відповіді увімкнено.";
        } else if (command.equals("скасуй таймер") || command.equals("зупини таймер") || command.equals("вимкни таймер")) {
            reply = cancelTimer();
        } else if (command.equals("скільки залишилося таймеру") || command.equals("скільки залишилось таймеру") ||
            command.equals("скільки до кінця таймера") || command.equals("скільки залишилось на таймері")) {
            reply = timerRemaining();
        } else if (requestedTimer != null) {
            reply = startTimer(requestedTimer);
        } else if (command.contains("таймер") && (command.startsWith("постав") || command.startsWith("встанови") || command.startsWith("таймер"))) {
            reply = "Скажи, наприклад: таймер на 5 хвилин або таймер на 30 секунд.";
        } else if (command.startsWith("запиши ") || command.startsWith("занотуй ") || command.startsWith("нотатка ")) {
            String item = raw.trim().replaceFirst("(?iu)^(джарвіс|джарвис|jarvis)[,\\s:—-]*", "")
                .replaceFirst("(?iu)^(запиши|занотуй|нотатка)\\s+", "").trim();
            if (item.isEmpty()) reply = "Скажи, що записати.";
            else {
                notes.add(item);
                saveNotes();
                renderNotes();
                reply = "Записав: " + item;
            }
        } else if (noteIndex != null) {
            if (noteIndex < 1 || noteIndex > notes.size()) reply = "Нотатки з таким номером немає.";
            else {
                String removed = notes.remove(noteIndex - 1);
                saveNotes();
                renderNotes();
                reply = "Видалив нотатку: " + removed;
            }
        } else if (command.equals("видали останню нотатку")) {
            if (notes.isEmpty()) reply = "Нотаток немає.";
            else {
                String removed = notes.remove(notes.size() - 1);
                saveNotes();
                renderNotes();
                reply = "Видалив останню нотатку: " + removed;
            }
        } else if (command.equals("очисти нотатки") || command.equals("видали всі нотатки")) {
            if (notes.isEmpty()) reply = "Нотаток немає.";
            else {
                confirmDeleteAllNotes();
                reply = "Підтверди видалення нотаток у вікні на екрані.";
            }
        } else if (command.contains("покажи нотатки") || command.contains("мої нотатки") || command.contains("прочитай нотатки")) {
            reply = notes.isEmpty() ? "У тебе ще немає нотаток." : "Нотатки: " + String.join("; ", notes);
        } else if (dialNumber != null) {
            reply = launchIntent(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dialNumber)), "набір номера");
        } else if (command.startsWith("подзвони") || command.startsWith("зателефонуй") || command.startsWith("набери ")) {
            reply = "Назви номер телефону, наприклад: набери 0501234567. Дзвінок потрібно підтвердити самостійно.";
        } else if (searchTerms != null) {
            try {
                String encoded = URLEncoder.encode(searchTerms, "UTF-8");
                reply = openUrl("https://www.google.com/search?q=" + encoded, "результати пошуку");
            } catch (Exception ex) {
                reply = "Не вдалося сформувати пошуковий запит.";
            }
        } else if (command.equals("відкрий налаштування") || command.equals("налаштування телефону")) {
            reply = launchIntent(new Intent(Settings.ACTION_SETTINGS), "налаштування");
        } else if (command.equals("відкрий вайфай") || command.equals("відкрий wi-fi") || command.equals("налаштування вайфаю")) {
            reply = launchIntent(new Intent(Settings.ACTION_WIFI_SETTINGS), "налаштування Wi-Fi");
        } else if (command.equals("відкрий блютуз") || command.equals("відкрий bluetooth")) {
            reply = launchIntent(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "налаштування Bluetooth");
        } else if (command.equals("відкрий карти") || command.equals("відкрий карту") || command.equals("покажи карту")) {
            // Prefer the installed Maps app; use a browser only for an explicit map request.
            String installed = launchInstalledApp("карти");
            reply = installed != null ? installed : openUrl("https://www.google.com/maps", "карти");
        } else if (requestedApp != null) {
            // Launcher names are discovered dynamically; no hardcoded list of apps.
            reply = launchInstalledApp(requestedApp);
            if (reply == null) {
                reply = "Не знайшов програму «" + requestedApp
                    + "» у меню встановлених застосунків. Назви її так, як під іконкою на телефоні.";
            }
        } else if (command.startsWith("привіт")) {
            askAi(raw);
            return;
        } else if (command.contains("що ти вмієш") || command.equals("допомога") || command.equals("команди")) {
            reply = "Я вмію: команди телефону, нотатки, таймери, будильники, запуск програм, музику, гучність, ліхтарик та вільну розмову з ШІ (після підключення сервера). Спробуй: розкажи цікаву історію.";
        } else if (command.contains("дякую")) {
            reply = "Завжди радий допомогти.";
        } else {
            askAi(raw);
            return;
        }

        appendDialogue("Джарвіс", reply);
        setStatus("Відповідь готова");
        if (!silentReply && tts != null && voiceEnabled) {
            tts.speak(reply, TextToSpeech.QUEUE_FLUSH, new Bundle(),
                "jarvis-" + SystemClock.uptimeMillis());
        } else if (!listening && !processing) {
            face.setState("idle");
        }
    }

    private void loadNotes() {
        String data = getPreferences(MODE_PRIVATE).getString("notes", "[]");
        try {
            JSONArray array = new JSONArray(data);
            for (int i = 0; i < array.length(); i++) notes.add(array.getString(i));
        } catch (JSONException ignored) {
            notes.clear();
        }
    }

    private void saveNotes() {
        JSONArray array = new JSONArray();
        for (String note : notes) array.put(note);
        getPreferences(MODE_PRIVATE).edit().putString("notes", array.toString()).apply();
    }

    private void endSpeaking() {
        runOnUiThread(() -> {
            speaking = false;
            if (!listening && !processing) face.setState("idle");
        });
    }

    @Override
    protected void onDestroy() {
        cancelTimerInternal();
        if (deviceActions != null) deviceActions.close();
        if (aiChatClient != null) aiChatClient.close();
        if (recognizer != null) recognizer.destroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private final class PixelFace extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String expression = "idle";

        PixelFace() {
            super(MainActivity.this);
            setContentDescription("8-бітне обличчя Джарвіса");
        }

        void setState(String state) {
            expression = state;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f;
            float ring = Math.min(w, h) * 0.45f;
            float orb = ring * 0.78f;

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1));
            paint.setColor(Color.argb(150, 76, 197, 236));
            canvas.drawCircle(cx, cy, ring, paint);

            paint.setColor(Color.argb(145, 36, 120, 167));
            canvas.drawCircle(cx, cy, ring - dp(14), paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(13, 46, 84));
            canvas.drawCircle(cx, cy, orb, paint);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(3));
            paint.setColor("listening".equals(expression) ? GREEN : Color.argb(210, 114, 235, 255));
            canvas.drawCircle(cx, cy, orb, paint);

            paint.setStyle(Paint.Style.FILL);
            // Match the web SVG sprite: 16x16 grid with a roughly 90/148 face/orb ratio.
            float pixel = orb * 0.076f;
            float ox = cx - 8 * pixel;
            float oy = cy - 8 * pixel;
            paint.setColor(Color.rgb(217, 254, 255));
            boolean blink = (SystemClock.uptimeMillis() % 5000) > 4570;
            if (blink) {
                drawPixel(canvas, ox, oy, pixel, 3, 6, 3, 1);
                drawPixel(canvas, ox, oy, pixel, 10, 6, 3, 1);
            } else {
                drawPixel(canvas, ox, oy, pixel, 3, 5, 3, 3);
                drawPixel(canvas, ox, oy, pixel, 10, 5, 3, 3);
            }

            switch (expression) {
                case "listening":
                    drawPixel(canvas, ox, oy, pixel, 5, 11, 6, 1);
                    break;
                case "thinking":
                    drawPixel(canvas, ox, oy, pixel, 5, 11, 1, 1);
                    drawPixel(canvas, ox, oy, pixel, 7, 11, 1, 1);
                    drawPixel(canvas, ox, oy, pixel, 9, 11, 1, 1);
                    break;
                case "speaking":
                    drawPixel(canvas, ox, oy, pixel, 6, 10, 4, (SystemClock.uptimeMillis() / 270) % 2 == 0 ? 3 : 1);
                    break;
                default:
                    drawPixel(canvas, ox, oy, pixel, 4, 11, 2, 1);
                    drawPixel(canvas, ox, oy, pixel, 6, 12, 4, 1);
                    drawPixel(canvas, ox, oy, pixel, 10, 11, 2, 1);
                    break;
            }
            postInvalidateDelayed(180);
        }

        private void drawPixel(Canvas canvas, float x, float y, float unit, int px, int py, int width, int height) {
            canvas.drawRect(x + px * unit, y + py * unit, x + (px + width) * unit, y + (py + height) * unit, paint);
        }
    }
}
