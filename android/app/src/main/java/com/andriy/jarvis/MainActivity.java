package com.andriy.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
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

public final class MainActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_MICROPHONE = 101;
    private static final int BG = Color.rgb(8, 14, 29);
    private static final int PANEL = Color.rgb(17, 27, 48);
    private static final int PANEL_DARK = Color.rgb(12, 21, 40);
    private static final int BORDER = Color.rgb(38, 57, 87);
    private static final int CYAN = Color.rgb(80, 223, 251);
    private static final int CYAN_DARK = Color.rgb(49, 199, 237);
    private static final int GREEN = Color.rgb(107, 232, 191);
    private static final int WHITE = Color.rgb(237, 246, 255);
    private static final int MUTED = Color.rgb(161, 179, 208);
    private static final int RED = Color.rgb(255, 124, 145);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<String> notes = new ArrayList<>();

    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private TextView status;
    private TextView dialogue;
    private EditText input;
    private Button microphone;
    private PixelFace face;
    private boolean listening = false;
    private boolean processing = false;
    private boolean speaking = false;
    private boolean isCompact = false;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        isCompact = getResources().getConfiguration().smallestScreenWidthDp < 360;
        loadNotes();
        buildInterface();

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int supported = tts.setLanguage(Locale.forLanguageTag("uk-UA"));
                if (supported == TextToSpeech.LANG_MISSING_DATA || supported == TextToSpeech.LANG_NOT_SUPPORTED) {
                    appendDialogue("Джарвіс", "Для озвучення встанови український голос TTS у налаштуваннях Android.");
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        runOnUiThread(() -> {
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

    private int getStatusBarInset() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : dp(24);
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
        scroll.setBackgroundColor(BG);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(16), getStatusBarInset() + dp(12), dp(16), dp(28));
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

        TextView version = makeText("● V 0.1.2", isCompact ? 11 : 12, GREEN, Gravity.CENTER, false);
        version.setBackground(roundedFill(Color.TRANSPARENT, Color.rgb(43, 131, 108), 99));
        version.setPadding(dp(10), dp(6), dp(10), dp(6));
        topbar.addView(version);
        content.addView(topbar);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        heroParams.topMargin = dp(18);
        content.addView(hero, heroParams);

        face = new PixelFace();
        int faceSize = dp(isCompact ? 192 : 210);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(faceSize, faceSize);
        hero.addView(face, faceParams);

        TextView hello = makeText("Вітаю, я Джарвіс", isCompact ? 28 : 32, WHITE, Gravity.CENTER, true);
        LinearLayout.LayoutParams helloParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        helloParams.topMargin = dp(10);
        hero.addView(hello, helloParams);

        TextView subtitle = makeText("Голосовий помічник українською. Натисни мікрофон або введи команду нижче.", isCompact ? 13 : 14, MUTED, Gravity.CENTER, false);
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

        TextView hint = makeText("Кнопка на екрані або бокова кнопка STEM_1/2 на сумісному годиннику: натисни для запису, ще раз — для обробки.", isCompact ? 11 : 12, MUTED, Gravity.CENTER, false);
        hint.setLineSpacing(0f, 1.22f);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = dp(10);
        hero.addView(hint, hintParams);

        LinearLayout chatPanel = panel();
        LinearLayout.LayoutParams chatPanelParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        chatPanelParams.topMargin = dp(18);
        content.addView(chatPanel, chatPanelParams);

        TextView panelTitle = makeText("Команди", isCompact ? 13 : 14, Color.rgb(185, 214, 240), Gravity.START, true);
        panelTitle.setLetterSpacing(0.10f);
        chatPanel.addView(panelTitle);

        input = new EditText(this);
        input.setHint("Напиши команду...");
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(TypedValue.COMPLEX_UNIT_SP, isCompact ? 14 : 15);
        input.setBackground(roundedFill(Color.rgb(9, 21, 40), Color.rgb(52, 73, 108), 12));
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(14);
        chatPanel.addView(input, inputParams);

        LinearLayout sendRow = new LinearLayout(this);
        sendRow.setOrientation(LinearLayout.HORIZONTAL);
        sendRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sendRowParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sendRowParams.topMargin = dp(12);
        chatPanel.addView(sendRow, sendRowParams);

        Button send = primaryButton("Надіслати", () -> {
            String command = input.getText().toString();
            input.setText("");
            processCommand(command);
        });
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        sendRow.addView(send, sendParams);

        Button showNotes = secondaryButton("Мої нотатки", () -> processCommand("Покажи нотатки"));
        LinearLayout.LayoutParams notesParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
        notesParams.topMargin = dp(12);
        chatPanel.addView(showNotes, notesParams);

        dialogue = makeText("", isCompact ? 13 : 14, WHITE, Gravity.START, false);
        dialogue.setLineSpacing(0f, 1.28f);
        dialogue.setBackground(roundedFill(PANEL_DARK, BORDER, 12));
        dialogue.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams dialogueParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dialogueParams.topMargin = dp(14);
        chatPanel.addView(dialogue, dialogueParams);

        TextView footer = makeText("JARVIS 0.1.2 • Тестовий Android APK. Голосове розпізнавання залежить від сервісів Android.", isCompact ? 11 : 12, Color.rgb(127, 154, 184), Gravity.CENTER, false);
        footer.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = dp(18);
        content.addView(footer, footerParams);

        setContentView(scroll);
        appendDialogue("Джарвіс", "Готовий! Скажи «Котра година?» або «Запиши купити хліб».\n\nТакож можеш попросити: «Що ти вмієш?» або «Покажи нотатки». ");
    }

    private void setStatus(String message) {
        if (status != null) status.setText(message);
    }

    private void appendDialogue(String who, String message) {
        if (dialogue == null) return;
        String previous = dialogue.getText().toString().trim();
        String block = who + ": " + message.trim();
        String next = previous.isEmpty() ? block : previous + "\n\n" + block;
        dialogue.setText(next.length() > 4000 ? next.substring(next.length() - 4000) : next);
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
        }
    }

    @Override public void onReadyForSpeech(Bundle params) { setStatus("Слухаю — говори..."); }
    @Override public void onBeginningOfSpeech() { setStatus("Розпізнаю голос..."); }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }

    @Override
    public void onEndOfSpeech() {
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

    private void processCommand(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        appendDialogue("Ти", raw.trim());

        String command = raw.trim().toLowerCase(new Locale("uk", "UA"))
            .replaceFirst("^(джарвіс|джарвис|jarvis)[,\\s:—-]*", "")
            .trim();

        String reply;
        if (command.contains("котра година") || command.contains("яка година") || command.contains("скільки часу")) {
            reply = "Зараз " + DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("uk", "UA")).format(new Date()) + ".";
        } else if (command.contains("яка сьогодні дата") || command.contains("який сьогодні день") || command.contains("сьогоднішня дата")) {
            reply = "Сьогодні " + DateFormat.getDateInstance(DateFormat.FULL, new Locale("uk", "UA")).format(new Date()) + ".";
        } else if (command.startsWith("запиши ") || command.startsWith("занотуй ") || command.startsWith("нотатка ")) {
            String item = raw.trim().replaceFirst("(?iu)^(джарвіс|джарвис|jarvis)[,\\s:—-]*", "")
                .replaceFirst("(?iu)^(запиши|занотуй|нотатка)\\s+", "")
                .trim();
            if (item.isEmpty()) reply = "Скажи, що записати.";
            else {
                notes.add(item);
                saveNotes();
                reply = "Записав: " + item;
            }
        } else if (command.contains("покажи нотатки") || command.contains("мої нотатки") || command.contains("прочитай нотатки")) {
            reply = notes.isEmpty() ? "У тебе ще немає нотаток." : "Нотатки: " + String.join("; ", notes);
        } else if (command.startsWith("відкрий ")) {
            String url = null;
            if (command.contains("youtube") || command.contains("ютуб")) url = "https://www.youtube.com/";
            else if (command.contains("chatgpt") || command.contains("чат гпт") || command.contains("чатгпт")) url = "https://chatgpt.com/";
            else if (command.contains("google") || command.contains("гугл")) url = "https://www.google.com/";
            else if (command.contains("телеграм") || command.contains("telegram")) url = "https://web.telegram.org/";
            if (url == null) {
                reply = "Поки відкриваю YouTube, ChatGPT, Google та Telegram.";
            } else {
                reply = "Відкриваю сайт.";
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
                } catch (Exception e) {
                    reply = "На цьому пристрої немає браузера для відкриття сайту.";
                }
            }
        } else if (command.startsWith("привіт")) {
            reply = "Привіт! Я Джарвіс. Готовий допомагати.";
        } else if (command.contains("що ти вмієш") || command.equals("допомога")) {
            reply = "Я можу назвати час і дату, зберегти і прочитати нотатки, відкрити сайти та відповідати голосом.";
        } else if (command.contains("дякую")) {
            reply = "Завжди радий допомогти.";
        } else {
            reply = "Не знаю такої команди. Скажи: що ти вмієш?";
        }

        appendDialogue("Джарвіс", reply);
        setStatus("Відповідь готова");
        if (tts != null) {
            Bundle speechParams = new Bundle();
            tts.speak(reply, TextToSpeech.QUEUE_FLUSH, speechParams, "jarvis-" + SystemClock.uptimeMillis());
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
            paint.setColor(Color.argb(210, 114, 235, 255));
            canvas.drawCircle(cx, cy, orb, paint);

            paint.setStyle(Paint.Style.FILL);
            float pixel = orb * 0.13f;
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
