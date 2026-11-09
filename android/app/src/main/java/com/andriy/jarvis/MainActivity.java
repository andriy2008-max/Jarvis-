package com.andriy.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
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
    private static final int CYAN = Color.rgb(99, 228, 251);
    private static final int WHITE = Color.rgb(235, 248, 255);
    private static final int MUTED = Color.rgb(167, 191, 217);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private TextView status;
    private TextView dialogue;
    private EditText input;
    private Button microphone;
    private PixelFace face;
    private final ArrayList<String> notes = new ArrayList<>();
    private boolean listening = false;
    private boolean processing = false;
    private boolean speaking = false;
    private boolean isCompact = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(5, 11, 24));
        getWindow().setNavigationBarColor(Color.rgb(5, 11, 24));
        isCompact = getResources().getConfiguration().smallestScreenWidthDp < 320;
        loadNotes();
        buildInterface();
        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int supported = tts.setLanguage(Locale.forLanguageTag("uk-UA"));
                if (supported == TextToSpeech.LANG_MISSING_DATA || supported == TextToSpeech.LANG_NOT_SUPPORTED) {
                    appendDialogue("Джарвіс: Для озвучення встанови український голос TTS у налаштуваннях Android.");
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { runOnUiThread(() -> { speaking = true; face.setState("speaking"); }); }
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

    private int dp(float value) { return Math.round(getResources().getDisplayMetrics().density * value); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setGravity(Gravity.CENTER);
        return view;
    }
    private Button button(String title, Runnable callback) {
        Button btn = new Button(this);
        btn.setText(title);
        btn.setTextColor(Color.rgb(2, 23, 36));
        btn.setAllCaps(false);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(CYAN));
        btn.setOnClickListener(v -> callback.run());
        return btn;
    }
    private void buildInterface() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(6, 14, 30));
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(isCompact ? 12 : 22), dp(16), dp(isCompact ? 12 : 22), dp(22));
        scroll.addView(content);
        TextView title = text("J.A.R.V.I.S. 0.1.2", isCompact ? 14 : 20, CYAN);
        title.setLetterSpacing(0.14f);
        content.addView(title);
        face = new PixelFace();
        int faceSize = dp(isCompact ? 145 : 205);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(faceSize, faceSize);
        faceParams.topMargin = dp(10);
        faceParams.bottomMargin = dp(8);
        content.addView(face, faceParams);
        status = text("Система готова до команд", isCompact ? 12 : 15, MUTED);
        content.addView(status);
        microphone = button("🎙 Почати запис", this::toggleListening);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, dp(isCompact ? 50 : 58));
        mp.topMargin = dp(12);
        content.addView(microphone, mp);
        TextView hint = text("Кнопка на екрані або бокова кнопка STEM_1/2 на сумісному годиннику: натисни для запису, ще раз — для обробки.", isCompact ? 10 : 12, MUTED);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(8);
        content.addView(hint, hp);
        input = new EditText(this);
        input.setHint("Напиши команду...");
        input.setSingleLine(true);
        input.setTextColor(WHITE);
        input.setHintTextColor(MUTED);
        input.setTextSize(isCompact ? 12 : 15);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, dp(isCompact ? 48 : 54));
        ip.topMargin = dp(10);
        content.addView(input, ip);
        Button send = button("Надіслати", () -> {
            String command = input.getText().toString();
            input.setText("");
            processCommand(command);
        });
        content.addView(send, new LinearLayout.LayoutParams(-1, dp(isCompact ? 45 : 54)));
        Button showNotes = button("Мої нотатки", () -> processCommand("Покажи нотатки"));
        content.addView(showNotes, new LinearLayout.LayoutParams(-1, dp(isCompact ? 43 : 49)));
        dialogue = text("Джарвіс: Готовий! Скажи «Котра година?» або «Запиши купити хліб».", isCompact ? 12 : 14, WHITE);
        dialogue.setGravity(Gravity.START);
        dialogue.setPadding(dp(6), dp(12), dp(6), dp(12));
        content.addView(dialogue);
        setContentView(scroll);
    }
    private void setStatus(String message) { if (status != null) status.setText(message); }
    private void appendDialogue(String message) {
        if (dialogue != null) {
            String previous = dialogue.getText().toString();
            String next = previous + "\n\n" + message;
            dialogue.setText(next.length() > 3500 ? next.substring(next.length() - 3500) : next);
        }
    }
    private void toggleListening() {
        if (processing) { setStatus("Обробляю команду..."); return; }
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
        microphone.setText("■ Зупинити й надіслати");
        face.setState("listening");
        setStatus("Слухаю...");
        try { recognizer.startListening(intent); }
        catch (Exception e) { resetListening("Не вдалося запустити запис. Спробуй ще раз."); }
    }
    private void resetListening(String message) {
        listening = false;
        processing = false;
        microphone.setText("🎙 Почати запис");
        face.setState(speaking ? "speaking" : "idle");
        setStatus(message);
    }
    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
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
    @Override public void onEndOfSpeech() {
        if (listening) {
            listening = false;
            processing = true;
            microphone.setText("Обробляю...");
            face.setState("thinking");
            setStatus("Обробляю команду...");
        }
    }
    @Override public void onError(int error) {
        String message = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
            ? "Не вдалося розпізнати. Спробуй ще раз." : "Помилка голосу (" + error + "). Перевір розпізнавання в Android.";
        resetListening(message);
    }
    @Override public void onResults(Bundle results) {
        ArrayList<String> matches = results == null ? null : results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        resetListening("Команду розпізнано");
        if (matches != null && !matches.isEmpty()) processCommand(matches.get(0));
        else setStatus("Порожня команда. Спробуй ще раз.");
    }
    @Override public void onPartialResults(Bundle results) { }
    @Override public void onEvent(int eventType, Bundle params) { }
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event != null && event.getRepeatCount() == 0 &&
            (keyCode == KeyEvent.KEYCODE_STEM_1 || keyCode == KeyEvent.KEYCODE_STEM_2 || keyCode == KeyEvent.KEYCODE_STEM_3)) {
            toggleListening();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
    private void processCommand(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        appendDialogue("Ти: " + raw.trim());
        String command = raw.trim().toLowerCase(new Locale("uk", "UA"))
            .replaceFirst("^(джарвіс|джарвис|jarvis)[,\\s:—-]*", "").trim();
        String reply;
        if (command.contains("котра година") || command.contains("яка година") || command.contains("скільки часу")) {
            reply = "Зараз " + DateFormat.getTimeInstance(DateFormat.SHORT, new Locale("uk", "UA")).format(new Date()) + ".";
        } else if (command.contains("яка сьогодні дата") || command.contains("який сьогодні день") || command.contains("сьогоднішня дата")) {
            reply = "Сьогодні " + DateFormat.getDateInstance(DateFormat.FULL, new Locale("uk", "UA")).format(new Date()) + ".";
        } else if (command.startsWith("запиши ") || command.startsWith("занотуй ") || command.startsWith("нотатка ")) {
            String item = raw.trim().replaceFirst("(?iu)^(джарвіс|джарвис|jarvis)[,\\s:—-]*", "")
                .replaceFirst("(?iu)^(запиши|занотуй|нотатка)\\s+", "").trim();
            if (item.isEmpty()) reply = "Скажи, що записати.";
            else { notes.add(item); saveNotes(); reply = "Записав: " + item; }
        } else if (command.contains("покажи нотатки") || command.contains("мої нотатки") || command.contains("прочитай нотатки")) {
            reply = notes.isEmpty() ? "У тебе ще немає нотаток." : "Нотатки: " + String.join("; ", notes);
        } else if (command.startsWith("відкрий ")) {
            String url = null;
            if (command.contains("youtube") || command.contains("ютуб")) url = "https://www.youtube.com/";
            else if (command.contains("chatgpt") || command.contains("чат гпт") || command.contains("чатгпт")) url = "https://chatgpt.com/";
            else if (command.contains("google") || command.contains("гугл")) url = "https://www.google.com/";
            else if (command.contains("телеграм") || command.contains("telegram")) url = "https://web.telegram.org/";
            if (url == null) reply = "Поки відкриваю YouTube, ChatGPT, Google та Telegram.";
            else {
                reply = "Відкриваю сайт.";
                try { startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))); }
                catch (Exception e) { reply = "На цьому пристрої немає браузера для відкриття сайту."; }
            }
        } else if (command.startsWith("привіт")) reply = "Привіт! Я Джарвіс. Готовий допомагати.";
        else if (command.contains("що ти вмієш") || command.equals("допомога"))
            reply = "Я можу назвати час і дату, зберегти і прочитати нотатки, відкрити сайти та відповідати голосом.";
        else if (command.contains("дякую")) reply = "Завжди радий допомогти.";
        else reply = "Не знаю такої команди. Скажи: що ти вмієш?";
        appendDialogue("Джарвіс: " + reply);
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
        } catch (JSONException ignored) { notes.clear(); }
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
    @Override protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }

    private final class PixelFace extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String expression = "idle";
        PixelFace() { super(MainActivity.this); setContentDescription("8-бітне обличчя Джарвіса"); }
        void setState(String state) { expression = state; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float cx = w / 2, cy = h / 2, r = Math.min(w, h) * 0.40f;
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(10, 51, 89));
            canvas.drawCircle(cx, cy, r, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(CYAN);
            canvas.drawCircle(cx, cy, r, paint);
            paint.setColor(Color.rgb(31, 109, 148));
            paint.setStrokeWidth(dp(1));
            canvas.drawCircle(cx, cy, r * 1.16f, paint);
            paint.setStyle(Paint.Style.FILL);
            float pixel = r * .11f;
            float ox = cx - 8 * pixel, oy = cy - 8 * pixel;
            paint.setColor(WHITE);
            boolean blink = (SystemClock.uptimeMillis() % 5000) > 4570;
            if (blink) {
                drawPixel(canvas, ox, oy, pixel, 3, 6, 3, 1);
                drawPixel(canvas, ox, oy, pixel, 10, 6, 3, 1);
            } else {
                drawPixel(canvas, ox, oy, pixel, 3, 5, 3, 3);
                drawPixel(canvas, ox, oy, pixel, 10, 5, 3, 3);
            }
            switch (expression) {
                case "listening": drawPixel(canvas, ox, oy, pixel, 5, 11, 6, 1); break;
                case "thinking":
                    drawPixel(canvas, ox, oy, pixel, 5, 11, 1, 1);
                    drawPixel(canvas, ox, oy, pixel, 7, 11, 1, 1);
                    drawPixel(canvas, ox, oy, pixel, 9, 11, 1, 1); break;
                case "speaking": drawPixel(canvas, ox, oy, pixel, 6, 10, 4, (SystemClock.uptimeMillis() / 270) % 2 == 0 ? 3 : 1); break;
                default:
                    drawPixel(canvas, ox, oy, pixel, 4, 11, 2, 1);
                    drawPixel(canvas, ox, oy, pixel, 6, 12, 4, 1);
                    drawPixel(canvas, ox, oy, pixel, 10, 11, 2, 1);
            }
            postInvalidateDelayed(180);
        }
        private void drawPixel(Canvas canvas, float x, float y, float unit, int px, int py, int width, int height) {
            canvas.drawRect(x + px * unit, y + py * unit, x + (px + width) * unit,
                y + (py + height) * unit, paint);
        }
    }
}
