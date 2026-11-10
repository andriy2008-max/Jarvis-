package com.andriy.jarvis;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.content.ComponentName;
import android.graphics.drawable.Icon;
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
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.TypedValue;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
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
import java.util.Set;
import java.util.Date;
import java.util.Locale;
import java.util.List;

public final class MainActivity extends Activity implements RecognitionListener {
    private static final int REQUEST_MICROPHONE = 101;
    private static final int REQUEST_TIMER_NOTIFICATIONS = 103;
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
    // The timer is scheduled with AlarmManager and survives closing this activity.
    private int pendingTimerSeconds;

    private SpeechRecognizer recognizer;
    private boolean recognizerUsesOnDevice;
    private TextToSpeech tts;
    private NeuralSpeechClient neuralSpeech;
    private OfflineVoicePackManager offlinePacks;
    private OfflineSpeechClient offlineSpeech;
    private String activeTtsId;
    private DeviceActions deviceActions;
    private final WakeConversation wakeConversation = new WakeConversation();
    private final Handler wakeHandler = new Handler(Looper.getMainLooper());
    private Runnable wakeRestart;
    private CheckBox wakeToggle;
    private boolean autoWakeEnabled;
    private boolean supportsOnDeviceRecognizer;
    private boolean foreground;
    private int visibleDialogs;
    private boolean manualCapture;
    private boolean manualStopRequested;
    private boolean recognizerSessionActive;
    private int manualCycleFailures;
    private final StringBuilder manualTranscript = new StringBuilder();
    private String manualHypothesis = "";
    private String wakeHypothesis = "";
    private String pendingWakeCommand;
    private Runnable pendingWakeDispatch;
    private Runnable manualRestart;
    private boolean autoWakeListening;
    private boolean autoWakePausedForErrors;
    private int autoWakeErrors;
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
    private static final String TRANSCRIPT_OWNER = "main";
    private final JarvisLiveTranscript.Listener transcriptListener =
        (words, active, untilMs) -> {
            if (face != null) face.showTranscript(words, active, untilMs);
        };
    private boolean listening = false;
    private boolean processing = false;
    private boolean speaking = false;
    private boolean isCompact = false;
    private boolean isWatchDevice = false;
    private boolean voiceEnabled = true;
    private boolean ttsReady;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (state != null) pendingTimerSeconds = state.getInt("pending_timer_seconds", 0);
        isWatchDevice = getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH);
        isCompact = getResources().getConfiguration().smallestScreenWidthDp < 360;
        voiceEnabled = getPreferences(MODE_PRIVATE).getBoolean("voiceEnabled", true);
        autoWakeEnabled = getPreferences(MODE_PRIVATE).getBoolean("autoWakeEnabled", true);
        supportsOnDeviceRecognizer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this);
        loadNotes();
        aiChatClient = new AiChatClient(this);
        neuralSpeech = new NeuralSpeechClient(this);
        offlinePacks = new OfflineVoicePackManager(this);
        offlineSpeech = new OfflineSpeechClient(this, offlinePacks);
        buildInterface();
        deviceActions = new DeviceActions(this);

        tts = new TextToSpeech(this, result -> {
            if (result == TextToSpeech.SUCCESS && tts != null) {
                int supported = tts.setLanguage(Locale.forLanguageTag("uk-UA"));
                if (supported == TextToSpeech.LANG_MISSING_DATA || supported == TextToSpeech.LANG_NOT_SUPPORTED) {
                    appendDialogue("Джарвіс", "Для озвучення встанови український голос TTS у налаштуваннях Android.");
                }
                ttsReady = true;
                applyPreferredVoice();
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) {
                        runOnUiThread(() -> {
                            if (id != null && id.equals(activeTtsId) && voiceEnabled) markSpeaking();
                        });
                    }
                    @Override public void onDone(String id) {
                        if (id != null && id.equals(activeTtsId)) endSpeaking();
                    }
                    @Override public void onError(String id) {
                        if (id != null && id.equals(activeTtsId)) endSpeaking();
                    }
                });
            }
        });

        try {
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                // For user-initiated conversation prefer the normal recognizer:
                // Ukrainian offline packs may be missing even when Android reports
                // that some on-device recognition service is available.
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizerUsesOnDevice = false;
            } else if (supportsOnDeviceRecognizer) {
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                recognizerUsesOnDevice = true;
            }
            if (recognizer != null) recognizer.setRecognitionListener(this);
            else setStatus("Розпізнавання мовлення не встановлено. Текстові команди працюють.");
        } catch (Exception error) {
            supportsOnDeviceRecognizer = false;
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizerUsesOnDevice = false;
                recognizer.setRecognitionListener(this);
            } else setStatus("Розпізнавання мовлення недоступне.");
        }
        if (wakeToggle != null) {
            wakeToggle.setEnabled(supportsOnDeviceRecognizer);
            wakeToggle.setChecked(autoWakeEnabled && supportsOnDeviceRecognizer);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        foreground = true;
        JarvisLiveTranscript.subscribe(transcriptListener);
        JarvisLiveTranscript.setCorrectionHandler(TRANSCRIPT_OWNER, this::undoHeardWord);
        autoWakePausedForErrors = false;
        autoWakeErrors = 0;
        scheduleAutoWake(650L);
        if (TimerScheduler.consumeCompletion(this)) {
            appendDialogue("Джарвіс", "Час вийшов! Фоновий таймер завершився.");
            setStatus("Час вийшов! Таймер завершено.");
        }
        resumePendingTimerIfAllowed();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        out.putInt("pending_timer_seconds", pendingTimerSeconds);
        super.onSaveInstanceState(out);
    }

    @Override
    protected void onPause() {
        cancelPendingWakeCommand();
        if (manualCapture || autoWakeListening) JarvisLiveTranscript.clear(TRANSCRIPT_OWNER);
        JarvisLiveTranscript.setCorrectionHandler(TRANSCRIPT_OWNER, null);
        JarvisLiveTranscript.unsubscribe(transcriptListener);
        foreground = false;
        cancelAutoWakeSchedule();
        cancelManualRestart();
        if (recognizer != null && recognizerSessionActive) recognizer.cancel();
        recognizerSessionActive = false;
        listening = false;
        processing = false;
        autoWakeListening = false;
        manualCapture = false;
        manualStopRequested = false;
        manualTranscript.setLength(0);
        manualHypothesis = "";
        wakeHypothesis = "";
        if (microphone != null)
            microphone.setText(isWatchDevice ? "🎙 Говорити" : "🎙️ Говорити з Джарвісом");
        super.onPause();
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
        if (isWatchDevice) {
            buildWatchInterface();
            return;
        }
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

        String versionName = "—";
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {}
        TextView version = makeText("● V " + versionName, isCompact ? 11 : 12, GREEN, Gravity.CENTER, false);
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
        attachFaceSwipe();
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

        wakeToggle = makeWakeToggle();
        LinearLayout.LayoutParams wakeParams = new LinearLayout.LayoutParams(-1, -2);
        wakeParams.topMargin = dp(8);
        hero.addView(wakeToggle, wakeParams);

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
                stopSpeaking();
                if (face != null) face.setState(listening ? "listening" : processing ? "thinking" : "idle");
            }
        });
        chatHeader.addView(voiceToggle);
        Button voiceSettings = secondaryButton("🔊 Вибрати голос Джарвіса", this::showVoiceSettings);
        LinearLayout.LayoutParams voiceSettingsParams = new LinearLayout.LayoutParams(-1, dp(43));
        voiceSettingsParams.topMargin = dp(7);
        chatPanel.addView(voiceSettings, voiceSettingsParams);

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

        // Lite APK deliberately has NO accessibility service: do not misrepresent it.
        Button screenControlButton = secondaryButton(
            getResources().getBoolean(R.bool.screen_control_available)
                ? "🖐 Керування екраном — дозвіл і запуск"
                : "🛡 Lite: керування екраном недоступне",
            this::showScreenControlConsent);
        LinearLayout.LayoutParams screenControlParams = new LinearLayout.LayoutParams(-1, -2);
        screenControlParams.topMargin = dp(8);
        chatPanel.addView(screenControlButton, screenControlParams);

        // Android Quick Settings shortcut: only the tiny floating face is shown,
        // and the microphone starts only after tapping it.
        if (!isWatchDevice) {
            Button quickTileButton = secondaryButton(
                "◉ Додати Джарвіса в швидкі налаштування",
                this::requestJarvisQuickTile);
            LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(-1, -2);
            tileParams.topMargin = dp(8);
            chatPanel.addView(quickTileButton, tileParams);

            Button floatingButton = secondaryButton(
                "🫧 Показати плаваюче обличчя",
                () -> startActivity(new Intent(this, JarvisBubbleLauncherActivity.class)));
            LinearLayout.LayoutParams floatParams = new LinearLayout.LayoutParams(-1, -2);
            floatParams.topMargin = dp(6);
            chatPanel.addView(floatingButton, floatParams);

            Button customizeFaceButton = secondaryButton(
                "🎨 Змінити розмір і колір плаваючого обличчя",
                this::showFloatingFaceOptions);
            LinearLayout.LayoutParams customizeParams =
                new LinearLayout.LayoutParams(-1, -2);
            customizeParams.topMargin = dp(6);
            chatPanel.addView(customizeFaceButton, customizeParams);
        }

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
            "💬 Поговорімо", "🤔 Поясни мені",
            "🗣 Скажи фразу", "💬 Скажи слово",
            "🖐 Керування екраном", "⏹ Зупини керування",
            "🔎 Відкрий пошук", "💬 Знайди чати", "✍ Напиши текст"
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
            "Привіт, Джарвіс", "Поясни простими словами, як працює штучний інтелект",
            "Скажи Привіт, як справи?", "Джарвіс, скажи Дякую",
            "Увімкни керування екраном", "Зупини керування екраном",
            "Відкрий пошук", "Знайди чати", "Напиши Привіт, як справи?"
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

        TextView footer = makeText("JARVIS 0.1.11 • Вільна розмова із ШІ після підключення сервера. Активація «Джарвіс» доступна лише у відкритій програмі за підтримки локального розпізнавання Android.",
            isCompact ? 11 : 12, Color.rgb(127, 154, 184), Gravity.CENTER, false);
        footer.setLineSpacing(0f, 1.25f);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, -2);
        footerParams.topMargin = dp(18);
        content.addView(footer, footerParams);

        setContentView(scroll);
        renderNotes();
        appendDialogue("Джарвіс", "Система запущена. Я готовий до перших команд. Спробуй запитати, що я вмію.");
    }

    /** A compact Wear OS view. All commands and AI processing are shared with the phone. */
    private void buildWatchInterface() {
        ScrollView root = new ScrollView(this);
        root.setFillViewport(true);
        root.setBackgroundColor(BG);
        LinearLayout body = new LinearLayout(this);
        body.setPadding(dp(14), dp(10), dp(14), dp(18));
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(body, new ScrollView.LayoutParams(-1, -2));

        TextView heading = makeText("J.A.R.V.I.S.", 13, CYAN, Gravity.CENTER, true);
        heading.setLetterSpacing(0.14f);
        body.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        face = new PixelFace();
        attachFaceSwipe();
        int diameter = Math.min(dp(132), Math.max(dp(104),
            getResources().getDisplayMetrics().widthPixels - dp(38)));
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(diameter, diameter);
        faceParams.topMargin = dp(3);
        body.addView(face, faceParams);

        status = makeText("Скажи «Джарвіс»", 12, WHITE, Gravity.CENTER, false);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = dp(3);
        body.addView(status, statusParams);

        microphone = primaryButton("🎙 Говорити", this::toggleListening);
        microphone.setMinHeight(dp(45));
        LinearLayout.LayoutParams micParams = new LinearLayout.LayoutParams(-1, dp(48));
        micParams.topMargin = dp(7);
        body.addView(microphone, micParams);

        wakeToggle = makeWakeToggle();
        wakeToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        body.addView(wakeToggle, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout controls = new LinearLayout(this);
        controls.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(-1, -2);
        controlsParams.topMargin = dp(5);
        body.addView(controls, controlsParams);
        Button textButton = secondaryButton("⌨️", this::showWatchTextInput);
        textButton.setContentDescription("Ввести текстову команду");
        controls.addView(textButton, new LinearLayout.LayoutParams(0, dp(42), 1f));
        Button menuButton = secondaryButton("⚙️", this::showWatchActions);
        menuButton.setContentDescription("Налаштування і команди");
        LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        menuParams.leftMargin = dp(6);
        controls.addView(menuButton, menuParams);

        voiceToggle = new CheckBox(this);
        voiceToggle.setText("Голосові відповіді");
        voiceToggle.setTextColor(MUTED);
        voiceToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        voiceToggle.setChecked(voiceEnabled);
        voiceToggle.setOnCheckedChangeListener((button, selected) -> {
            voiceEnabled = selected;
            getPreferences(MODE_PRIVATE).edit().putBoolean("voiceEnabled", selected).apply();
            if (!selected) stopSpeaking();
        });
        body.addView(voiceToggle, new LinearLayout.LayoutParams(-1, -2));

        feedScroll = new ScrollView(this);
        feedScroll.setFillViewport(true);
        LinearLayout.LayoutParams feedParams = new LinearLayout.LayoutParams(-1, dp(124));
        feedParams.topMargin = dp(5);
        body.addView(feedScroll, feedParams);
        feedMessages = new LinearLayout(this);
        feedMessages.setOrientation(LinearLayout.VERTICAL);
        feedScroll.addView(feedMessages, new ScrollView.LayoutParams(-1, -2));

        setContentView(root);
        appendDialogue("Джарвіс", "Готовий. Натисни кнопку або скажи моє ім'я.");
    }


    /** Android does not expose gender for its Voice objects. Select a Ukrainian
     * voice with an explicit male label when available; otherwise offer a
     * comfortable lower-pitched local fallback. No additional OpenAI billing. */
    private ArrayList<Voice> ukrainianVoices() {
        ArrayList<Voice> voices = new ArrayList<>();
        if (tts == null || !ttsReady) return voices;
        try {
            Set<Voice> installed = tts.getVoices();
            if (installed != null) {
                for (Voice v : installed) {
                    if (v != null && v.getName() != null && v.getLocale() != null &&
                        VoiceRanking.ukrainian(v.getLocale().toLanguageTag())) voices.add(v);
                }
            }
        } catch (RuntimeException ignored) { }
        voices.sort((a, b) -> Integer.compare(
            VoiceRanking.score(b.getLocale().toLanguageTag(), b.getName(),
                b.isNetworkConnectionRequired(), b.getQuality()),
            VoiceRanking.score(a.getLocale().toLanguageTag(), a.getName(),
                a.isNetworkConnectionRequired(), a.getQuality())));
        return voices;
    }

    private Voice voiceForProfile(ArrayList<Voice> available, String profile) {
        for (Voice voice : available) {
            if (VoiceRanking.matchesProfile(voice.getName(), profile)) return voice;
        }
        return null;
    }

    private String selectedVoiceProfile() {
        return VoiceRanking.profile(
            getPreferences(MODE_PRIVATE).getString("jarvisVoiceProfile", VoiceRanking.DMYTRO));
    }

    private String profileLabel(String profile) {
        return VoiceRanking.LADA.equals(profile) ? "Лада" : "Дмитро";
    }

    private void applyPreferredVoice() {
        if (tts == null || !ttsReady) return;
        ArrayList<Voice> available = ukrainianVoices();
        String profile = selectedVoiceProfile();
        Voice namedVoice = voiceForProfile(available, profile);
        // Android TTS is used only as fallback; named neural models run on the configured server.
        Voice chosen = namedVoice != null ? namedVoice :
            (available.isEmpty() ? null : available.get(0));
        if (chosen != null) {
            try { tts.setVoice(chosen); } catch (RuntimeException ignored) { }
        } else {
            tts.setLanguage(Locale.forLanguageTag("uk-UA"));
        }
        boolean knownProfile = chosen != null &&
            (VoiceRanking.matchesProfile(chosen.getName(), VoiceRanking.DMYTRO) ||
             VoiceRanking.matchesProfile(chosen.getName(), VoiceRanking.LADA));
        boolean clearlyMale = chosen != null && VoiceRanking.labeledMale(chosen.getName());
        tts.setPitch((knownProfile || clearlyMale) ? 1.0f : 0.86f);
        tts.setSpeechRate(0.94f);
        if (namedVoice == null && (neuralSpeech == null || !neuralSpeech.isConfigured())) {
            String why = offlinePacks != null && offlinePacks.hasInstalledFiles()
                ? "Офлайн-файли збережені, але синтез ще тестується."
                : "Офлайн-пакет ще не підключений.";
            setStatus(why + " Зараз озвучує Android.");
        }
    }

    /** Pause wake recognition while either voice engine is producing audio. */
    private void markSpeaking() {
        speaking = true;
        cancelAutoWakeSchedule();
        if (autoWakeListening && recognizer != null) {
            recognizer.cancel();
            listening = false;
            recognizerSessionActive = false;
            autoWakeListening = false;
        }
        if (face != null) face.setState("speaking");
    }

    private void speakSystemVoice(String text) {
        if (!voiceEnabled) { endSpeaking(); return; }
        if (!ttsReady || tts == null) {
            setStatus("Немає доступного синтезатора мовлення.");
            endSpeaking();
            return;
        }
        markSpeaking();
        activeTtsId = "jarvis-system-" + SystemClock.uptimeMillis();
        int statusCode = tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), activeTtsId);
        if (statusCode != TextToSpeech.SUCCESS) endSpeaking();
    }

    /** Prefer fully-local two-speaker ONNX, then private TTS server, then Android TTS. */
    private void speakReply(String text) {
        if (!voiceEnabled || text == null || text.trim().isEmpty()) {
            endSpeaking();
            return;
        }
        stopSpeaking();
        if (offlineSpeech != null && offlineSpeech.isInstalled()) {
            markSpeaking();
            if (offlineSpeech.speak(text, selectedVoiceProfile(), new NeuralSpeechClient.Listener() {
                @Override public void onStart() { markSpeaking(); }
                @Override public void onDone() { endSpeaking(); }
                @Override public void onFailure(String error) {
                    if (!voiceEnabled || isFinishing() || isDestroyed()) return;
                    setStatus("Офлайн-модель не запустилася (" + error + "). Спробую резервне озвучення.");
                    speakRemoteOrSystem(text);
                }
            })) return;
        }
        speakRemoteOrSystem(text);
    }

    private void speakRemoteOrSystem(String text) {
        if (neuralSpeech != null && neuralSpeech.isConfigured()) {
            markSpeaking();
            if (neuralSpeech.speak(text, selectedVoiceProfile(), new NeuralSpeechClient.Listener() {
                @Override public void onStart() { markSpeaking(); }
                @Override public void onDone() { endSpeaking(); }
                @Override public void onFailure(String error) {
                    if (!voiceEnabled || isFinishing() || isDestroyed()) return;
                    setStatus("TTS-сервер недоступний (" + error + "). Озвучує Android.");
                    speakSystemVoice(text);
                }
            })) return;
        }
        speakSystemVoice(text);
    }

    private void previewCurrentVoice() {
        if (!voiceEnabled) {
            setStatus("Спочатку ввімкни голосові відповіді.");
            return;
        }
        speakReply("Привіт! Я Джарвіс. Готовий допомогти тобі.");
    }

    private void openSystemVoiceSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS"));
        } catch (Exception missing) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) { setStatus("Налаштування голосу недоступні."); }
        }
    }

    private void openOriginalVoicePreview(String profile) {
        String url = VoiceRanking.LADA.equals(profile)
            ? "https://github.com/robinhad/ukrainian-tts/assets/5759207/fcf558b2-3ff9-4539-ad9e-8455b52223a4"
            : "https://github.com/robinhad/ukrainian-tts/assets/5759207/67d3dac9-6626-40ef-98e5-ec194096bbe0";
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Exception ignored) {
            setStatus("Не вдалося відкрити приклад голосу " + profileLabel(profile) + ".");
        }
    }

    private void showVoiceUnavailable(String profile) {
        String label = profileLabel(profile);
        AlertDialog alert = new AlertDialog.Builder(this)
            .setTitle(label + " — поки недоступний")
            .setMessage("Профіль збережено. Для справжнього голосу " + label +
                " потрібно підключити особистий HTTPS TTS-сервер. Без нього працює системний голос Android.")
            .setNegativeButton("Закрити", null)
            .setNeutralButton("Офлайн-пакет", (d, w) -> showOfflineVoiceSettings())
            .setPositiveButton("Зразок " + label, (d, w) -> openOriginalVoicePreview(profile))
            .create();
        showDialogWithoutWake(alert);
    }

    private void selectVoiceProfile(String requested) {
        String profile = VoiceRanking.profile(requested);
        getPreferences(MODE_PRIVATE).edit().putString("jarvisVoiceProfile", profile).apply();
        applyPreferredVoice();
        if ((offlineSpeech != null && offlineSpeech.isInstalled()) ||
            (neuralSpeech != null && neuralSpeech.isConfigured()) ||
            (ttsReady && voiceForProfile(ukrainianVoices(), profile) != null)) {
            setStatus("Обрано голос " + profileLabel(profile) + ". Перевіряю звучання.");
            previewCurrentVoice();
        } else showVoiceUnavailable(profile);
    }

    private void showVoiceSettings() {
        ArrayList<Voice> available = ukrainianVoices();
        boolean hasDmytro = voiceForProfile(available, VoiceRanking.DMYTRO) != null;
        boolean hasLada = voiceForProfile(available, VoiceRanking.LADA) != null;
        boolean modelConfigured = neuralSpeech != null && neuralSpeech.isConfigured();
        boolean offlineSaved = offlinePacks != null && offlinePacks.hasInstalledFiles();
        String[] labels = {
            "Дмитро — чоловічий · " + (offlineSaved ? "локальна ONNX-модель (експериментально)" : modelConfigured ? "сервер налаштовано" : hasDmytro ? "системний голос" : "ще не підключений"),
            "Лада — жіночий · " + (offlineSaved ? "локальна ONNX-модель (експериментально)" : modelConfigured ? "сервер налаштовано" : hasLada ? "системний голос" : "ще не підключений")
        };
        int checked = VoiceRanking.LADA.equals(selectedVoiceProfile()) ? 1 : 0;
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Українські голоси Джарвіса")
            .setSingleChoiceItems(labels, checked, (d, which) -> {
                selectVoiceProfile(which == 1 ? VoiceRanking.LADA : VoiceRanking.DMYTRO);
            })
            .setNegativeButton("Закрити", null)
            .setNeutralButton("Офлайн-пакет", (d, w) -> showOfflineVoiceSettings())
            .create();
        showDialogWithoutWake(dialog);
    }

    /** Verified ONNX pack installer; inference is experimental until tested on device. */
    private void showOfflineVoiceSettings() {
        if (offlinePacks == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));

        String state = offlinePacks.hasInstalledFiles()
            ? "ONNX-пакет збережено. Офлайн-синтез увімкнеться під час наступної відповіді; потрібна перевірка реального звучання на пристрої."
            : "Пакет ще не встановлено. Потрібен конвертований ONNX ZIP, не оригінальний model.pth.";
        TextView description = makeText(
            state + "\nНатисни «Завантажити». Джарвіс сам перевірить офіційний пакет та SHA-256. " +
            "Поля нижче потрібні лише для ручного встановлення експериментального пакета.",
            13, MUTED, Gravity.START, false);
        form.addView(description, new LinearLayout.LayoutParams(-1, -2));

        EditText link = new EditText(this);
        link.setSingleLine(true);
        link.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        link.setHint("https://.../jarvis-uktts-v6-onnx.zip");
        form.addView(link, new LinearLayout.LayoutParams(-1, -2));

        EditText sha = new EditText(this);
        sha.setSingleLine(true);
        sha.setInputType(InputType.TYPE_CLASS_TEXT);
        sha.setHint("SHA-256 з перевіреного релізу (64 символи)");
        form.addView(sha, new LinearLayout.LayoutParams(-1, -2));

        TextView progress = makeText(
            "Офлайн-рушій додано, але перевіреного пакета моделі та тесту на телефоні поки немає.",
            12, MUTED, Gravity.START, false);
        form.addView(progress, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Дмитро і Лада · офлайн")
            .setView(form)
            .setNegativeButton("Закрити", null)
            .setNeutralButton("TTS-сервер", (d, which) -> showNeuralTtsSettings())
            .setPositiveButton("Завантажити пакет", null)
            .create();
        dialog.setOnShowListener(d -> {
            Button install = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            install.setOnClickListener(v -> {
                String url = link.getText().toString().trim();
                String hash = sha.getText().toString().trim();
                boolean automatic = url.isEmpty() && hash.isEmpty();
                if (!automatic &&
                    (!OfflineVoicePackRules.validUrl(url) || !OfflineVoicePackRules.validSha256(hash))) {
                    progress.setText("Для ручного завантаження потрібні HTTPS ZIP та правильний SHA-256. Або залиш поля порожніми.");
                    return;
                }
                install.setEnabled(false);
                progress.setText("Перевіряю офіційний випуск. Не закривай Джарвіса до завершення.");
                OfflineVoicePackManager.Callback callback = new OfflineVoicePackManager.Callback() {
                    @Override public void onProgress(int percentage) {
                        if (!isFinishing() && !isDestroyed())
                            progress.setText("Завантаження: " + percentage + "%. Після нього буде перевірка файлів.");
                    }
                    @Override public void onComplete(boolean installed, String message) {
                        if (isFinishing() || isDestroyed()) return;
                        progress.setText(message);
                        setStatus(message);
                        install.setEnabled(true);
                    }
                };
                if (automatic) offlinePacks.installOfficial(callback);
                else offlinePacks.install(url, hash, callback);
            });
        });
        showDialogWithoutWake(dialog);
    }

    private void showNeuralTtsSettings() {
        if (neuralSpeech == null) return;
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(8), dp(18), dp(8));

        form.addView(makeText(
            "Дмитро і Лада працюють через власний HTTPS TTS-сервер. " +
            "Сервер отримуватиме текст відповіді; без нього використовується озвучення Android.",
            13, MUTED, Gravity.START, false), new LinearLayout.LayoutParams(-1, -2));
        EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://your-server.example/tts");
        url.setText(neuralSpeech.endpoint());
        form.addView(url, new LinearLayout.LayoutParams(-1, -2));

        EditText token = new EditText(this);
        token.setSingleLine(true);
        token.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        token.setHint(neuralSpeech.isConfigured() ? "Код (порожньо = без змін)" : "Код доступу, від 20 символів");
        form.addView(token, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Нейро-TTS · Дмитро / Лада")
            .setView(form)
            .setNegativeButton("Закрити", null)
            .setNeutralButton("Від'єднати", (d, w) -> {
                neuralSpeech.disconnect();
                stopSpeaking();
                applyPreferredVoice();
                setStatus("Нейро-TTS від'єднано. Озвучення Android активне.");
            })
            .setPositiveButton("Зберегти", null)
            .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                neuralSpeech.configure(url.getText().toString(), token.getText().toString());
                setStatus("TTS-сервер налаштовано. Обери Дмитра або Ладу для прослуховування.");
                dialog.dismiss();
            } catch (Exception error) {
                url.setError(error.getMessage());
                setStatus(error.getMessage());
            }
        }));
        showDialogWithoutWake(dialog);
    }

    /**
     * Prominent disclosure before the user opens Android Accessibility settings
     * or activates a session. The service does nothing until the user enables
     * it in the OS and explicitly turns on screen control here.
     */
    private void showScreenControlConsent() {
        if (!getResources().getBoolean(R.bool.screen_control_available)) {
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Джарвіс Lite — без доступу до екрана")
                .setMessage("Цей тестовий APK не містить служби спеціальних можливостей. " +
                    "Голосові команди, таймер та офлайн-голоси залишаються доступними. " +
                    "Натискання кнопок в інших застосунках не підтримується. " +
                    "Для цієї функції потрібна окрема повна версія з перевіреним способом встановлення.")
                .setPositiveButton("Зрозуміло", null).create();
            showDialogWithoutWake(dialog);
            return;
        }
        boolean connected = ScreenControlAccessibilityService.isConnected();
        String disclosure =
            "Джарвіс зможе читати назви видимих кнопок і виконувати окремі " +
            "команди натискання, прокручування та повернення назад.\n\n" +
            "Служба спеціальних можливостей має широкий доступ до вмісту " +
            "екрана, тому вмикай її лише якщо довіряєш Джарвісу. " +
            "Ми не надсилаємо вміст екрана на сервер ШІ та не зберігаємо його.\n\n" +
            "Дії виконуються лише після твоєї команди. Платежі, видалення, " +
            "надсилання та підтвердження заблоковані в цій версії. " +
            "Для зупинки скажи «Зупини керування екраном» або " +
            "утримуй плаваючу кнопку Джарвіса.\n\n" +
            (connected ? "Дозвіл Android уже ввімкнений. Запустити режим?"
                       : "Наступний крок: вручну ввімкнути службу Джарвіса " +
                         "в розділі «Спеціальні можливості» Android.");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Керування екраном — доступ і приватність")
            .setMessage(disclosure)
            .setNegativeButton("Скасувати", null)
            .setPositiveButton(connected ? "Увімкнути режим" : "Відкрити налаштування",
                (d, which) -> {
                    if (connected) {
                        String result = ScreenControlAccessibilityService.start();
                        appendDialogue("Джарвіс", result);
                        setStatus(result);
                    } else {
                        try {
                            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                        } catch (Exception error) {
                            setStatus("Не вдалося відкрити спеціальні можливості Android.");
                        }
                    }
                }).create();
        showDialogWithoutWake(dialog);
    }

    private void showFloatingFaceOptions() {
        String[] choices = {
            "Мале обличчя — 96 dp",
            "Звичайне обличчя — 112 dp",
            "Велике обличчя — 144 dp (краще видно слова)",
            "Колір — класичний блакитний",
            "Колір — бірюзовий",
            "Колір — фіолетовий"
        };
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Налаштування плаваючого Джарвіса")
            .setMessage("Перетягуй обличчя пальцем на будь-яке місце екрана. " +
                "Коротке натискання — слухати, довге — закрити. " +
                "Під час розпізнавання на обличчі показуватимуться почуті слова.")
            .setItems(choices, (d, index) -> {
                android.content.SharedPreferences.Editor editor =
                    getSharedPreferences("jarvis_orb", MODE_PRIVATE).edit();
                if (index <= 2) {
                    int[] diameters = {96, 112, 144};
                    editor.putInt("diameter_dp", diameters[index]);
                } else {
                    editor.putInt("accent_style", index - 3);
                }
                editor.apply();
                if (FloatingJarvisService.isShowing()) {
                    Intent resize = new Intent(this, FloatingJarvisService.class);
                    resize.setAction(FloatingJarvisService.ACTION_RESIZE);
                    try { startService(resize); }
                    catch (Exception ignored) {
                        // Preference is saved and used the next time the orb opens.
                    }
                }
                setStatus("Зовнішній вигляд плаваючого Джарвіса оновлено.");
            })
            .setNegativeButton("Скасувати", null)
            .create();
        showDialogWithoutWake(dialog);
    }

    private void requestJarvisQuickTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            StatusBarManager statusBar = getSystemService(StatusBarManager.class);
            if (statusBar != null) {
                try {
                    statusBar.requestAddTileService(
                        new ComponentName(this, JarvisQuickTileService.class),
                        "Джарвіс",
                        Icon.createWithResource(this, R.drawable.ic_launcher),
                        getMainExecutor(),
                        result -> {
                            if (result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED)
                                setStatus("Кнопку Джарвіса додано до швидких налаштувань.");
                            else
                                setStatus("Якщо кнопки немає, відкрий шторку → Редагувати → додай «Джарвіс».");
                        });
                    return;
                } catch (Exception ignored) {
                    // OEM may not offer requestAddTileService. Use manual editor.
                }
            }
        }
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Додати Джарвіса у швидкі налаштування")
            .setMessage("Потягни шторку зверху двічі, натисни «Редагувати» (олівець) " +
                "і перетягни плитку «Джарвіс» у швидкі кнопки. " +
                "Після натискання відкриється лише маленьке плаваюче обличчя.")
            .setPositiveButton("Зрозуміло", null).create();
        showDialogWithoutWake(dialog);
    }

    private void showWatchTextInput() {
        EditText editor = new EditText(this);
        editor.setSingleLine(false);
        editor.setMinLines(2);
        editor.setHint("Команда або запитання");
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Написати Джарвісу")
            .setView(editor)
            .setNegativeButton("Скасувати", null)
            .setPositiveButton("Надіслати", (d, which) -> processCommand(editor.getText().toString()))
            .create();
        showDialogWithoutWake(dialog);
    }

    private void showWatchActions() {
        final String[] actions = {
            "Підключення до ШІ", "Новий діалог", "Показати нотатки",
            "Що ти вмієш?", "Введення текстом", "Голосова активація", "Дмитро / Лада",
            "Керування екраном"
        };
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Джарвіс — дії")
            .setItems(actions, (d, which) -> {
                switch (which) {
                    case 0: showAiSettings(); break;
                    case 1: clearAiConversation(); break;
                    case 2: processCommand("покажи нотатки"); break;
                    case 3: processCommand("що ти вмієш"); break;
                    case 4: showWatchTextInput(); break;
                    case 5:
                        if (supportsOnDeviceRecognizer && wakeToggle != null) {
                            wakeToggle.setChecked(!wakeToggle.isChecked());
                        } else {
                            setStatus("Для активації голосом потрібне локальне розпізнавання Android.");
                        }
                        break;
                    case 6: showVoiceSettings(); break;
                    case 7: showScreenControlConsent(); break;
                    default: break;
                }
            }).create();
        showDialogWithoutWake(dialog);
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


    private void attachFaceSwipe() {
        if (face == null) return;
        face.setClickable(true);
        face.setContentDescription(
            "Обличчя Джарвіса. Свайп вліво по словах — прибрати останнє й вимовити заново.");
        face.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (JarvisTranscriptCorrection.isLeftSwipe(
                            event.getX() - startX, event.getY() - startY, dp(38))) {
                            JarvisLiveTranscript.requestCorrection();
                        } else view.performClick();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        return true;
                    default:
                        return true;
                }
            }
        });
    }

    private void cancelPendingWakeCommand() {
        if (pendingWakeDispatch != null) {
            wakeHandler.removeCallbacks(pendingWakeDispatch);
            pendingWakeDispatch = null;
        }
        pendingWakeCommand = null;
    }

    private void scheduleWakeCommand(String command) {
        cancelPendingWakeCommand();
        pendingWakeCommand = command;
        JarvisLiveTranscript.update(TRANSCRIPT_OWNER, command, false,
            SystemClock.uptimeMillis());
        microphone.setText("✓ Надіслати команду");
        face.setState("thinking");
        setStatus("Свайп вліво на обличчі — виправити слово. Надсилання через 5 с.");
        pendingWakeDispatch = () -> {
            if (!foreground || pendingWakeCommand == null) return;
            String ready = pendingWakeCommand;
            cancelPendingWakeCommand();
            JarvisLiveTranscript.update(TRANSCRIPT_OWNER, ready, true,
                SystemClock.uptimeMillis());
            microphone.setText(isWatchDevice ? "🎙 Говорити" : "🎙️ Говорити з Джарвісом");
            processCommand(ready);
            scheduleAutoWake(550L);
        };
        wakeHandler.postDelayed(pendingWakeDispatch, 5000L);
    }

    private void undoHeardWord() {
        if (!foreground) return;
        String heard;
        if (pendingWakeCommand != null) heard = pendingWakeCommand;
        else if (manualCapture && !manualStopRequested)
            heard = JarvisTranscriptCorrection.join(
                manualTranscript.toString(), manualHypothesis);
        else if (autoWakeListening)
            heard = wakeHypothesis;
        else return;
        if (heard.trim().isEmpty()) return;
        String undone = JarvisTranscriptCorrection.removeLastWord(heard);
        cancelPendingWakeCommand();
        cancelAutoWakeSchedule();
        cancelManualRestart();
        manualCapture = true;
        manualStopRequested = false;
        manualCycleFailures = 0;
        manualTranscript.setLength(0);
        manualTranscript.append(undone);
        manualHypothesis = "";
        wakeHypothesis = "";
        // A canceled recognizer must not report its old hypothesis and
        // restore the word the user just removed.
        SpeechRecognizer previous = recognizer;
        recognizer = null;
        if (previous != null) {
            try { previous.cancel(); previous.destroy(); }
            catch (Exception ignored) { }
        }
        recognizerSessionActive = false;
        autoWakeListening = false;
        listening = false;
        processing = false;
        JarvisLiveTranscript.update(TRANSCRIPT_OWNER, undone, false,
            SystemClock.uptimeMillis());
        face.setState("listening");
        microphone.setText("■ Завершити та надіслати");
        setStatus("Слово видалено. Повтори його та заверши запис кнопкою.");
        scheduleManualRestart(550L);
    }

    private void cancelAutoWakeSchedule() {
        if (wakeRestart != null) {
            wakeHandler.removeCallbacks(wakeRestart);
            wakeRestart = null;
        }
    }

    private void scheduleAutoWake(long delayMs) {
        cancelAutoWakeSchedule();
        wakeRestart = () -> {
            wakeRestart = null;
            if (canAutoWake()) beginListening();
        };
        wakeHandler.postDelayed(wakeRestart, delayMs);
    }

    private boolean canAutoWake() {
        return foreground && visibleDialogs == 0 &&
            autoWakeEnabled && supportsOnDeviceRecognizer &&
            !autoWakePausedForErrors && pendingWakeCommand == null &&
            !FloatingJarvisService.isShowing() && recognizer != null &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            !manualCapture && !listening && !processing && !aiBusy && !speaking &&
            (tts == null || !tts.isSpeaking());
    }

    private void resumeAfterReply() {
        wakeConversation.assistantFinished(SystemClock.elapsedRealtime());
        scheduleAutoWake(450L);
    }

    private void showDialogWithoutWake(AlertDialog dialog) {
        visibleDialogs++;
        cancelAutoWakeSchedule();
        if (autoWakeListening && recognizer != null) {
            recognizer.cancel();
            autoWakeListening = false;
            recognizerSessionActive = false;
            listening = false;
        }
        dialog.setOnDismissListener(d -> {
            visibleDialogs = Math.max(0, visibleDialogs - 1);
            scheduleAutoWake(550L);
        });
        dialog.show();
    }

    private CheckBox makeWakeToggle() {
        CheckBox toggle = new CheckBox(this);
        toggle.setText("Активація «Джарвіс»");
        toggle.setTextColor(MUTED);
        toggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        toggle.setButtonTintList(ColorStateList.valueOf(CYAN));
        toggle.setChecked(autoWakeEnabled && supportsOnDeviceRecognizer);
        toggle.setEnabled(supportsOnDeviceRecognizer);
        toggle.setOnCheckedChangeListener((button, enabled) -> {
            autoWakeEnabled = enabled;
            getPreferences(MODE_PRIVATE).edit().putBoolean("autoWakeEnabled", enabled).apply();
            if (!enabled) {
                cancelAutoWakeSchedule();
                wakeConversation.end();
                if (autoWakeListening && recognizer != null) {
                    recognizer.cancel();
                    autoWakeListening = false;
                    recognizerSessionActive = false;
                    listening = false;
                    processing = false;
                }
                setStatus("Режим активації голосом вимкнено");
            } else {
                autoWakePausedForErrors = false;
                autoWakeErrors = 0;
                setStatus("Очікую «Джарвіс» у відкритому застосунку");
                scheduleAutoWake(350L);
            }
        });
        return toggle;
    }



    /** Switch between the general user-initiated recognizer (may use network)
     * and the locally running wake listener. On-device availability does not
     * guarantee that an offline Ukrainian language pack has been installed. */
    private void selectRecognizerForMode(boolean forManual) {
        if (recognizerSessionActive) return;
        boolean wantOnDevice = !forManual && supportsOnDeviceRecognizer;
        if (recognizer != null && recognizerUsesOnDevice == wantOnDevice) return;
        SpeechRecognizer next = null;
        boolean nextIsOnDevice = false;
        try {
            if (wantOnDevice) {
                next = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                nextIsOnDevice = true;
            } else if (SpeechRecognizer.isRecognitionAvailable(this)) {
                next = SpeechRecognizer.createSpeechRecognizer(this);
            } else if (supportsOnDeviceRecognizer) {
                // Fallback when an Android device has only an offline provider.
                next = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
                nextIsOnDevice = true;
            }
        } catch (Exception error) {
            if (wantOnDevice) {
                supportsOnDeviceRecognizer = false;
                if (wakeToggle != null) {
                    wakeToggle.setChecked(false);
                    wakeToggle.setEnabled(false);
                }
            }
            return;
        }
        if (next != null) {
            if (recognizer != null) recognizer.destroy();
            recognizer = next;
            recognizerUsesOnDevice = nextIsOnDevice;
            recognizer.setRecognitionListener(this);
        }
    }

    /** Manual press-to-talk stays armed until the SECOND press. Android recognition
     * may end each low-level segment on silence: we restart those segments instead of
     * treating the service callback as the end of the user's requested recording. */
    private void toggleListening() {
        if (pendingWakeCommand != null) {
            String ready = pendingWakeCommand;
            cancelPendingWakeCommand();
            JarvisLiveTranscript.update(TRANSCRIPT_OWNER, ready, true,
                SystemClock.uptimeMillis());
            microphone.setText(isWatchDevice ? "🎙 Говорити" : "🎙️ Говорити з Джарвісом");
            processCommand(ready);
            return;
        }
        if (manualCapture) {
            manualStopRequested = true;
            cancelManualRestart();
            microphone.setText("Обробляю...");
            setStatus("Завершую запис, розпізнаю останні слова...");
            if (recognizerSessionActive) {
                if (listening && recognizer != null) {
                    listening = false;
                    recognizer.stopListening();
                }
                // If Android already ended speech, wait for its onResults/onError.
            } else {
                finishManualCapture();
            }
            return;
        }
        if (recognizer == null) {
            setStatus("Сервіс розпізнавання відсутній. Спробуй текстову команду.");
            return;
        }
        if (processing || aiBusy) {
            // A foreground wake-word segment may be waiting for its results.
            if (!(autoWakeListening && recognizerSessionActive)) {
                setStatus("Обробляю попередню команду...");
                return;
            }
        }
        manualCapture = true;
        manualStopRequested = false;
        manualCycleFailures = 0;
        manualTranscript.setLength(0);
        manualHypothesis = "";
        wakeHypothesis = "";
        JarvisLiveTranscript.begin(TRANSCRIPT_OWNER);
        cancelManualRestart();
        cancelAutoWakeSchedule();
        // Reuse an already-started wake session, then restart in manual mode.
        if (autoWakeListening && recognizerSessionActive) {
            autoWakeListening = false;
            processing = false;
            microphone.setText("■ Завершити та надіслати");
            face.setState("listening");
            setStatus("Запис триває. Натисни ще раз, щоб надіслати.");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_MICROPHONE);
            return;
        }
        beginListening();
    }

    private void cancelManualRestart() {
        if (manualRestart != null) {
            wakeHandler.removeCallbacks(manualRestart);
            manualRestart = null;
        }
    }

    private void scheduleManualRestart(long delayMs) {
        cancelManualRestart();
        manualRestart = () -> {
            manualRestart = null;
            if (foreground && manualCapture && !manualStopRequested &&
                !recognizerSessionActive && !aiBusy && visibleDialogs == 0) {
                beginListening();
            }
        };
        wakeHandler.postDelayed(manualRestart, delayMs);
    }

    private void finishManualCapture() {
        cancelManualRestart();
        final String transcript = manualTranscript.toString().trim();
        manualHypothesis = "";
        if (transcript.isEmpty()) JarvisLiveTranscript.clear(TRANSCRIPT_OWNER);
        else JarvisLiveTranscript.update(TRANSCRIPT_OWNER, transcript, true,
            SystemClock.uptimeMillis());
        manualTranscript.setLength(0);
        manualCapture = false;
        manualStopRequested = false;
        manualCycleFailures = 0;
        listening = false;
        recognizerSessionActive = false;
        processing = false;
        microphone.setText(isWatchDevice ? "🎙 Говорити" : "🎙️ Говорити з Джарвісом");
        if (transcript.isEmpty()) {
            face.setState("idle");
            setStatus("Запис порожній. Спробуй говорити після натискання.");
            scheduleAutoWake(550L);
            return;
        }
        WakeConversation.Decision decision = wakeConversation.accept(
            transcript, SystemClock.elapsedRealtime(), true);
        if (decision.kind == WakeConversation.Kind.COMMAND) {
            face.setState("thinking");
            processCommand(decision.command);
        } else if (decision.kind == WakeConversation.Kind.ARMED) {
            face.setState("listening");
            setStatus("Слухаю. Можеш продовжувати розмову.");
        } else if (decision.kind == WakeConversation.Kind.FINISHED) {
            face.setState("idle");
            setStatus("Розмову завершено.");
        }
        scheduleAutoWake(550L);
    }

    private void beginListening() {
        if (processing || recognizerSessionActive) return;
        selectRecognizerForMode(manualCapture);
        if (recognizer == null) {
            setStatus("На пристрої немає сервісу розпізнавання мовлення.");
            manualCapture = false;
            return;
        }
        if (!manualCapture && !recognizerUsesOnDevice) {
            autoWakePausedForErrors = true;
            setStatus("Локальне розпізнавання недоступне. Користуйся кнопкою мікрофона.");
            return;
        }
        if (tts != null && tts.isSpeaking()) tts.stop();
        speaking = false;
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "uk-UA");
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "uk-UA");
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, recognizerUsesOnDevice);
        if (manualCapture) {
            // Hints only; engines may still end a segment early. Cycling handles that.
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 5000L);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 5000L);
        }
        autoWakeListening = !manualCapture;
        recognizerSessionActive = true;
        listening = true;
        microphone.setText(manualCapture ? "■ Завершити та надіслати" :
            isWatchDevice ? "🎙 Говорити" : "🎙️ Говорити з Джарвісом");
        face.setState(manualCapture || wakeConversation.isActive(SystemClock.elapsedRealtime())
            ? "listening" : "idle");
        setStatus(manualCapture ? "Запис триває. Натисни ще раз, щоб надіслати." :
            wakeConversation.isActive(SystemClock.elapsedRealtime()) ? "Продовжуй розмову..." :
                "Очікую «Джарвіс»");
        try {
            recognizer.startListening(intent);
        } catch (Exception error) {
            recognizerSessionActive = false;
            listening = false;
            autoWakeListening = false;
            if (manualCapture && manualCycleFailures++ < 3) {
                setStatus("Перезапускаю мікрофон...");
                scheduleManualRestart(900L);
            } else {
                manualCapture = false;
                resetListening("Не вдалося запустити розпізнавання. Перевір мікрофон Android.");
                autoWakePausedForErrors = true;
            }
        }
    }

    private void resetListening(String message) {
        listening = false;
        processing = false;
        microphone.setText(isWatchDevice ? "🎙 Говорити" : "🎙️ Говорити з Джарвісом");
        face.setState(speaking ? "speaking" : "idle");
        setStatus(message);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == REQUEST_TIMER_NOTIFICATIONS) {
            if (results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED) {
                setStatus("Сповіщення таймера заборонені. Вібрація та сигнал можуть працювати, але повідомлення не з'явиться.");
            }
        } else if (code == REQUEST_MICROPHONE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                beginListening();
            } else {
                manualCapture = false;
                JarvisLiveTranscript.clear(TRANSCRIPT_OWNER);
                setStatus("Дозволь доступ до мікрофона в налаштуваннях застосунку.");
            }
        } else if (code == DeviceActions.REQUEST_CAMERA_FOR_TORCH && deviceActions != null) {
            String feedback = deviceActions.onCameraPermissionResult(
                results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED);
            if (feedback != null) {
                appendDialogue("Джарвіс", feedback);
                setStatus(feedback);
                if (voiceEnabled) speakReply(feedback);
            }
        }
    }

    @Override public void onReadyForSpeech(Bundle params) {
        if (manualCapture) setStatus("Слухаю — говори...");
        else setStatus(wakeConversation.isActive(SystemClock.elapsedRealtime())
            ? "Продовжуй розмову..." : "Очікую «Джарвіс»");
    }
    @Override public void onBeginningOfSpeech() {
        if (!manualCapture) JarvisLiveTranscript.begin(TRANSCRIPT_OWNER);
        setStatus(manualCapture ? "Розпізнаю голос..." : "Слухаю фразу...");
    }
    @Override public void onRmsChanged(float rmsdB) { }
    @Override public void onBufferReceived(byte[] buffer) { }


    @Override
    public void onEndOfSpeech() {
        listening = false;
        if (manualCapture) {
            // SpeechRecognizer stops automatically on pauses. Keep the *user's*
            // manual recording active and restart once results arrive.
            setStatus(manualStopRequested ? "Завершую запис..." :
                "Запис активний: продовжуй говорити або натисни для надсилання.");
            return;
        }
        processing = true;
        microphone.setText("Обробляю...");
        face.setState("thinking");
        setStatus("Обробляю команду...");
    }

    @Override
    public void onError(int error) {
        recognizerSessionActive = false;
        listening = false;
        boolean wasManual = manualCapture;
        autoWakeListening = false;
        if (!foreground) {
            manualCapture = false;
            manualStopRequested = false;
            resetListening("Режим очікування");
            return;
        }
        if (wasManual) {
            manualHypothesis = "";
            processing = false;
            if (manualStopRequested) {
                finishManualCapture();
                return;
            }
            if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                manualCapture = false;
                resetListening("Надай дозвіл на мікрофон у налаштуваннях Android.");
                return;
            }
            boolean noSpeech = error == SpeechRecognizer.ERROR_NO_MATCH ||
                error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
            if (noSpeech) manualCycleFailures = 0;
            else manualCycleFailures++;
            if (manualCycleFailures > 5) {
                if (manualTranscript.length() == 0)
                    JarvisLiveTranscript.clear(TRANSCRIPT_OWNER);
                else JarvisLiveTranscript.update(TRANSCRIPT_OWNER,
                    manualTranscript.toString(), true, SystemClock.uptimeMillis());
                manualCapture = false;
                resetListening("Розпізнавання зупинилося через повторні помилки (" + error + ").");
                if (manualTranscript.length() > 0) {
                    // Never silently lose a successfully transcribed earlier segment.
                    final String partial = manualTranscript.toString();
                    manualTranscript.setLength(0);
                    processCommand(partial);
                }
                return;
            }
            if (manualTranscript.length() > 0)
                JarvisLiveTranscript.update(TRANSCRIPT_OWNER,
                    manualTranscript.toString(), false, SystemClock.uptimeMillis());
            microphone.setText("■ Завершити та надіслати");
            face.setState("listening");
            setStatus("Запис триває. Натисни ще раз, щоб надіслати.");
            scheduleManualRestart(noSpeech ? 450L : 1100L);
            return;
        }
        wakeHypothesis = "";
        JarvisLiveTranscript.clear(TRANSCRIPT_OWNER);
        resetListening("Очікую «Джарвіс»");
        if (error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
            autoWakeErrors = 0;
        } else {
            autoWakeErrors++;
            if (autoWakeErrors >= 4) {
                autoWakePausedForErrors = true;
                setStatus("Голосову активацію призупинено. Використай кнопку мікрофона.");
            }
        }
        if (!autoWakePausedForErrors) scheduleAutoWake(
            error == SpeechRecognizer.ERROR_NO_MATCH ||
            error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT ? 650L : 1800L);
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results == null ? null :
            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        recognizerSessionActive = false;
        listening = false;
        boolean wasManual = manualCapture;
        autoWakeListening = false;
        if (!foreground) {
            manualCapture = false;
            manualStopRequested = false;
            resetListening("Режим очікування");
            return;
        }
        if (wasManual) {
            manualHypothesis = "";
            processing = false;
            manualCycleFailures = 0;
            if (matches != null && !matches.isEmpty() && matches.get(0) != null) {
                String segment = matches.get(0).trim();
                if (!segment.isEmpty()) {
                    if (manualTranscript.length() > 0) manualTranscript.append(' ');
                    manualTranscript.append(segment);
                }
            }
            if (manualStopRequested) {
                finishManualCapture();
            } else {
                JarvisLiveTranscript.update(TRANSCRIPT_OWNER,
                    manualTranscript.toString(), false, SystemClock.uptimeMillis());
                microphone.setText("■ Завершити та надіслати");
                face.setState("listening");
                setStatus("Запис триває. Натисни ще раз, щоб надіслати.");
                scheduleManualRestart(350L);
            }
            return;
        }
        wakeHypothesis = "";
        resetListening("Команду розпізнано");
        autoWakeErrors = 0;
        if (matches == null || matches.isEmpty() || matches.get(0) == null ||
            matches.get(0).trim().isEmpty()) {
            JarvisLiveTranscript.clear(TRANSCRIPT_OWNER);
            setStatus("Не вдалося розпізнати фразу.");
            scheduleAutoWake(450L);
            return;
        }
        JarvisLiveTranscript.update(TRANSCRIPT_OWNER, matches.get(0), true,
            SystemClock.uptimeMillis());
        WakeConversation.Decision decision = wakeConversation.accept(
            matches.get(0), SystemClock.elapsedRealtime(), false);
        if (decision.kind == WakeConversation.Kind.ARMED) {
            setStatus("Активовано — говори, Джарвіс слухає мовчки.");
            face.setState("listening");
        } else if (decision.kind == WakeConversation.Kind.FINISHED) {
            setStatus("Розмову завершено. Очікую «Джарвіс».");
            face.setState("idle");
        } else if (decision.kind == WakeConversation.Kind.COMMAND) {
            scheduleWakeCommand(decision.command);
            return;
        } else {
            setStatus("Очікую «Джарвіс».");
        }
        scheduleAutoWake(450L);
    }

    @Override public void onPartialResults(Bundle results) {
        if (!foreground || results == null) return;
        ArrayList<String> hypotheses =
            results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (hypotheses == null || hypotheses.isEmpty() || hypotheses.get(0) == null) return;
        String partial = hypotheses.get(0).trim();
        if (partial.isEmpty()) return;
        if (manualCapture) {
            manualHypothesis = partial;
            partial = JarvisTranscriptCorrection.join(
                manualTranscript.toString(), manualHypothesis);
        } else {
            wakeHypothesis = partial;
        }
        JarvisLiveTranscript.update(TRANSCRIPT_OWNER, partial, false,
            SystemClock.uptimeMillis());
    }
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


    private void maybeRequestTimerNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) return;
        if (!getPreferences(MODE_PRIVATE).getBoolean("askedTimerNotifications", false)) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("askedTimerNotifications", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_TIMER_NOTIFICATIONS);
        }
    }

    private void openExactAlarmSettings() {
        try {
            Intent settings = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:" + getPackageName()));
            startActivity(settings);
        } catch (Exception error) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
            } catch (Exception ignored) {
                setStatus("Не вдалося відкрити налаштування. Увімкни «Будильники й нагадування» вручну.");
            }
        }
    }

    /** Called after the user returns from the exact alarm permission screen. */
    private void resumePendingTimerIfAllowed() {
        if (pendingTimerSeconds <= 0 || !TimerScheduler.canScheduleExact(this)) return;
        int seconds = pendingTimerSeconds;
        pendingTimerSeconds = 0;
        String message = startTimer(seconds);
        appendDialogue("Джарвіс", message);
        setStatus(message);
    }

    private String startTimer(int seconds) {
        if (!TimerRules.valid(seconds))
            return "Можна встановити таймер від 1 секунди до 3 годин.";
        if (!TimerScheduler.canScheduleExact(this)) {
            pendingTimerSeconds = seconds;
            openExactAlarmSettings();
            return "Для точного таймера у фоні дозволь «Будильники й нагадування». Після дозволу таймер запуститься автоматично.";
        }
        boolean started = TimerScheduler.start(this, seconds);
        if (!started) return "Не вдалося запустити системний таймер. Перевір дозвіл на будильники.";
        maybeRequestTimerNotificationPermission();
        return "Таймер на " + timerLabel(seconds) +
            " запущено. Він працює у фоні; після завершення — сильна вібрація і сигнал гучності дзвінка.";
    }

    private String cancelTimer() {
        boolean cancelled = TimerScheduler.cancel(this);
        pendingTimerSeconds = 0;
        return cancelled ? "Таймер скасовано." : "Активного таймера немає.";
    }

    private String timerRemaining() {
        long remaining = TimerScheduler.remainingSeconds(this);
        if (remaining < 0) return "Активного таймера немає.";
        if (remaining == 0) return "Час таймера вийшов; очікую системне сповіщення.";
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
        activeTtsId = null;
        if (offlineSpeech != null) offlineSpeech.stop();
        if (neuralSpeech != null) neuralSpeech.stop();
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
            if (error == null && voiceEnabled) {
                speakReply(response);
            } else {
                face.setState("idle");
                resumeAfterReply();
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
        showDialogWithoutWake(dialog);
    }

    private JarvisSequenceExecutor commandSequenceExecutor;

    private void processCommand(String raw) {
        if (raw == null || raw.trim().isEmpty()) return;
        if (commandSequenceExecutor != null && commandSequenceExecutor.isRunning()) {
            String request = CommandPatterns.normalize(raw);
            if (request.equals("скасуй послідовність") ||
                request.equals("зупини послідовність") ||
                request.equals("скасуй виконання") ||
                request.equals("стоп")) {
                commandSequenceExecutor.cancel();
            } else {
                setStatus("Спочатку заверши поточну послідовність або скажи «Скасуй послідовність».");
            }
            return;
        }
        appendDialogue("Ти", raw.trim());
        String command = CommandPatterns.normalize(raw);
        if (command.isEmpty()) return;

        // Complex commands are parsed and validated as a complete plan BEFORE
        // opening any app. This prevents partial execution on malformed speech.
        JarvisCommandSequence.Plan plan = JarvisCommandSequence.parse(raw);
        if (plan != null && (!plan.valid() || plan.multiple())) {
            if (!plan.valid()) {
                appendDialogue("Джарвіс", plan.error);
                setStatus(plan.error);
                speakReply(plan.error);
                return;
            }
            if (commandSequenceExecutor == null)
                commandSequenceExecutor = new JarvisSequenceExecutor(this);
            commandSequenceExecutor.start(plan, new JarvisSequenceExecutor.Listener() {
                @Override public void onProgress(int step, int total, String description) {
                    setStatus("Крок " + step + "/" + total + ": " + description);
                }
                @Override public void onFinished(boolean success, String summary) {
                    appendDialogue("Джарвіс", summary);
                    setStatus(summary);
                    speakReply(summary);
                }
            });
            return;
        }
        String directSpeech = CommandPatterns.spokenText(raw);

        Integer requestedTimer = CommandPatterns.timerSeconds(command);
        Integer noteIndex = CommandPatterns.noteIndex(command);
        String dialNumber = CommandPatterns.dialNumber(command);
        String searchTerms = CommandPatterns.searchTerms(command);
        AssistantIntents.ClockTime alarmTime = AssistantIntents.alarmTime(command);
        AssistantIntents.Reminder reminder = AssistantIntents.reminder(command);
        String requestedApp = AssistantIntents.requestedApp(command);
        DeviceCommands.Action deviceCommand = DeviceCommands.parse(command);
        ScreenControlCommands.Action screenCommand = ScreenControlCommands.parse(raw);
        boolean silentReply = false;
        String reply;

        if (directSpeech != null) {
            if (directSpeech.isEmpty()) {
                reply = "Назви слово або речення після команди «Скажи».";
            } else if (directSpeech.length() > 1200) {
                reply = "Фраза занадто довга. Назви текст до 1200 символів.";
            } else if (!voiceEnabled) {
                reply = "Щоб я повторив фразу, увімкни голосові відповіді.";
                silentReply = true;
            } else {
                // Speak exactly the requested phrase, without asking conversational AI
                // and without reading back a separate acknowledgment first.
                appendDialogue("Джарвіс", directSpeech);
                setStatus("Озвучую задану фразу");
                speakReply(directSpeech);
                return;
            }
        } else if (screenCommand != null) {
            if (!getResources().getBoolean(R.bool.screen_control_available)) {
                reply = "У Джарвіс Lite керування іншими програмами вимкнено. Голосові команди, таймер і озвучення доступні.";
            } else {
            switch (screenCommand.kind) {
                case ENABLE:
                case SETTINGS:
                    showScreenControlConsent();
                    return;
                case DISABLE:
                    reply = ScreenControlAccessibilityService.stop();
                    break;
                case STATUS:
                    reply = ScreenControlAccessibilityService.isSessionActive()
                        ? "Режим керування екраном увімкнено."
                        : "Керування екраном вимкнено. Скажи: увімкни керування екраном.";
                    break;
                default:
                    reply = ScreenControlAccessibilityService.perform(screenCommand);
                    break;
            }
            }
        } else if (deviceCommand != null) {
            reply = deviceActions == null ? "Керування пристроєм ще не готове." : deviceActions.perform(deviceCommand);
        } else if (command.equals("досить") || command.equals("закінчи розмову") ||
            command.equals("заверши розмову") || command.equals("кінець розмови")) {
            wakeConversation.end();
            setStatus("Розмову завершено. Очікую «Джарвіс».");
            face.setState("idle");
            scheduleAutoWake(400L);
            return;
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
        } else if (command.equals("голос дмитро") || command.equals("голос дмитра") ||
            command.equals("вибери дмитра") || command.equals("увімкни дмитра")) {
            selectVoiceProfile(VoiceRanking.DMYTRO);
            return;
        } else if (command.equals("голос лада") || command.equals("голос лади") ||
            command.equals("вибери ладу") || command.equals("увімкни ладу")) {
            selectVoiceProfile(VoiceRanking.LADA);
            return;
        } else if (command.equals("зміни голос") || command.equals("чоловічий голос") ||
            command.equals("налаштуй голос") || command.equals("вибери голос")) {
            showVoiceSettings();
            return;
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
            reply = "Я вмію: команди телефону, нотатки, таймери, будильники, запуск програм, музику, гучність, ліхтарик, керування екраном після ручного дозволу, озвучення будь-якої фрази командою «Скажи...» та розмову з ШІ (після підключення сервера). Спробуй: скажи Привіт, як справи?";
        } else if (command.contains("дякую")) {
            reply = "Завжди радий допомогти.";
        } else {
            askAi(raw);
            return;
        }

        appendDialogue("Джарвіс", reply);
        setStatus("Відповідь готова");
        if (!silentReply && voiceEnabled) {
            speakReply(reply);
        } else if (!listening && !processing) {
            face.setState("idle");
            resumeAfterReply();
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
            resumeAfterReply();
        });
    }

    @Override
    protected void onDestroy() {
        JarvisLiveTranscript.setCorrectionHandler(TRANSCRIPT_OWNER, null);
        JarvisLiveTranscript.unsubscribe(transcriptListener);
        cancelPendingWakeCommand();
        if (commandSequenceExecutor != null) commandSequenceExecutor.cancel();
        cancelAutoWakeSchedule();
        wakeConversation.end();
        // Never cancel AlarmManager timers when Android destroys the UI.
        if (deviceActions != null) deviceActions.close();
        if (aiChatClient != null) aiChatClient.close();
        if (recognizer != null) recognizer.destroy();
        if (offlineSpeech != null) offlineSpeech.close();
        if (offlinePacks != null) offlinePacks.close();
        if (neuralSpeech != null) neuralSpeech.close();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }

    private final class PixelFace extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private String expression = "idle";
        private final TextPaint transcriptPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private String recognizedText = "";
        private boolean transcribing;
        private long showRecognizedUntilMs;

        PixelFace() {
            super(MainActivity.this);
            setContentDescription("8-бітне обличчя Джарвіса");
        }

        void setState(String state) {
            expression = state;
            invalidate();
        }

        void showTranscript(String words, boolean active, long untilMs) {
            recognizedText = JarvisOrbTranscript.visibleTail(words);
            transcribing = active;
            showRecognizedUntilMs = untilMs;
            invalidate();
        }

        private boolean transcriptVisible() {
            return transcribing || (!recognizedText.isEmpty() &&
                SystemClock.uptimeMillis() < showRecognizedUntilMs);
        }

        private void drawTranscript(Canvas canvas, float cx, float cy, float radius) {
            String visible = recognizedText.isEmpty() ? "Слухаю…" : recognizedText;
            transcriptPaint.setColor(Color.rgb(224, 253, 255));
            transcriptPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            float sp = getResources().getDisplayMetrics().scaledDensity;
            transcriptPaint.setTextSize(Math.min(17f * sp,
                Math.max(11f * sp, radius * .27f)));
            int width = Math.max(1, Math.round(radius * 1.50f));
            StaticLayout layout;
            while (true) {
                layout = StaticLayout.Builder.obtain(visible, 0, visible.length(),
                        transcriptPaint, width)
                    .setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false).build();
                if (layout.getLineCount() <= 4 || visible.length() <= 12) break;
                int nextSpace = visible.indexOf(' ');
                if (nextSpace > 0 && nextSpace < visible.length() - 1)
                    visible = "…" + visible.substring(nextSpace + 1);
                else visible = "…" + visible.substring(1);
            }
            Path circle = new Path();
            circle.addCircle(cx, cy, radius * .93f, Path.Direction.CW);
            canvas.save();
            canvas.clipPath(circle);
            canvas.translate(cx - width / 2f, cy - layout.getHeight() / 2f);
            layout.draw(canvas);
            canvas.restore();
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
            if (transcriptVisible()) {
                drawTranscript(canvas, cx, cy, orb);
                postInvalidateDelayed(180);
                return;
            }
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
