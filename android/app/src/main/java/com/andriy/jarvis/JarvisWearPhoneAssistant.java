package com.andriy.jarvis;

import android.content.Context;
import android.os.SystemClock;

/**
 * Companion-phone dispatcher for Wear OS voice commands.
 *
 * The watch sends ONLY the recognized utterance over Google's authenticated
 * Wear Data Layer. All API credentials (AI proxy access, Telegram TDLib
 * authorization, Spotify settings) stay on the phone or on the user's server.
 * Conversational AI can answer questions but never authorizes device actions.
 */
public final class JarvisWearPhoneAssistant {
    public interface Completion { void finished(boolean ok, String message); }

    private static AiChatClient ai;
    private static boolean aiBusy;
    private static JarvisSequenceExecutor sequences;
    private static DeviceActions deviceActions;

    private JarvisWearPhoneAssistant() {}

    /** Called on the phone main thread by JarvisWearPhoneSession. */
    public static void execute(Context context, String watchNode, String raw,
                               Completion completion) {
        if (completion == null) return;
        if (context == null || !JarvisWearProtocol.validCommand(raw)) {
            completion.finished(false,"Порожня або надто довга команда з годинника.");
            return;
        }
        if (context.getPackageManager().hasSystemFeature(
                android.content.pm.PackageManager.FEATURE_WATCH)) {
            completion.finished(false,"Команду має обробити підключений телефон.");
            return;
        }
        Context phone = context.getApplicationContext();
        final String normalized = CommandPatterns.normalizeControlVerb(raw);
        if (normalized.isEmpty()) {
            completion.finished(false,"Не розпізнав голосову команду.");
            return;
        }

        // Telegram: explicit user-authored messages only. Do not give an AI
        // model implicit power to choose contacts, write drafts or send.
        TelegramReplyCommands.Request telegram = TelegramReplyCommands.parse(raw);
        if (telegram != null) {
            TelegramReplyController.perform(phone,telegram,completion::finished);
            return;
        }
        if (TelegramReplyCommands.dialogAction(raw) !=
                TelegramReplyCommands.DialogAction.NONE ||
            TelegramReplyController.needsFollowupDictation(phone)) {
            if (TelegramReplyController.handleFollowup(phone,raw,
                    completion::finished)) return;
        }

        // Money Manager is an Accessibility-driven app, NOT an external API.
        // Never re-submit a financial record without explicit user speech.
        if (FinanceManagerController.isRunning()) {
            if (FinanceVoiceCommands.cancel(raw)) {
                FinanceManagerController.cancel();
                completion.finished(true,"Скасував фінансову операцію на телефоні.");
            } else {
                completion.finished(false,
                    "Фінансова операція ще виконується. Повтори після завершення.");
            }
            return;
        }
        if (FinanceVoiceCommands.parse(raw) != null) {
            FinanceManagerController.start(phone,raw,new FinanceManagerController.Listener() {
                @Override public void onProgress(String message) { }
                @Override public void onFinished(boolean ok,String message) {
                    completion.finished(ok,message);
                }
            });
            return;
        }
        if (looksLikeFinanceCommand(normalized)) {
            completion.finished(false,
                "Не розпізнав суму або категорію. Скажи: «Запиши витрату 350 гривень на харчування».");
            return;
        }

        if (SongRecognitionCommands.matches(raw)) {
            String started = SongRecognitionLauncher.launch(phone,completion::finished);
            if (!started.startsWith("Відкриваю Shazam")) {
                completion.finished(false,started);
            }
            return;
        }

        // Individual UI steps and sequences use the phone's previously
        // user-authorized Accessibility service; no remote AI screen access.
        JarvisCommandSequence.Plan plan = JarvisCommandSequence.parse(raw);
        if (plan != null && (!plan.valid() || plan.multiple())) {
            if (!plan.valid()) {
                completion.finished(false,plan.error);
                return;
            }
            if (sequences == null) sequences = new JarvisSequenceExecutor(phone);
            if (sequences.isRunning()) {
                completion.finished(false,"Послідовність уже виконується на телефоні.");
                return;
            }
            sequences.start(plan,new JarvisSequenceExecutor.Listener() {
                @Override public void onProgress(int step,int total,String detail) { }
                @Override public void onFinished(boolean ok,String description) {
                    completion.finished(ok,description);
                }
            });
            return;
        }
        ScreenControlCommands.Action screen = ScreenControlCommands.parse(raw);
        if (screen != null) {
            switch(screen.kind) {
                case STATUS:
                    completion.finished(true,
                        ScreenControlAccessibilityService.isConnected()
                        ? "Керування екраном телефона дозволено."
                        : "Керування екраном телефона вимкнено в Спеціальних можливостях.");
                    return;
                case SETTINGS:
                    completion.finished(false,
                        "Дозвіл керування екраном відкрий вручну на телефоні в Спеціальних можливостях.");
                    return;
                case ENABLE:
                    String activated = JarvisPhoneModeManager.enter(phone,false);
                    completion.finished(ScreenControlAccessibilityService.isSessionActive(),activated);
                    return;
                case DISABLE:
                    completion.finished(true,JarvisPhoneModeManager.exit());
                    return;
                default:
                    JarvisPhoneModeManager.perform(phone,screen,completion::finished);
                    return;
            }
        }

        DeviceCommands.Action device = DeviceCommands.parse(normalized);
        if (device != null) {
            try {
                if (deviceActions == null) deviceActions = new DeviceActions(phone);
                String answer = deviceActions.perform(device);
                completion.finished(JarvisControlFeedback.successfulReply(answer),answer);
            } catch (RuntimeException error) {
                completion.finished(false,
                    "Android не дозволив системну дію. Перевір дозволи Джарвіса на телефоні.");
            }
            return;
        }

        Integer timer = CommandPatterns.timerSeconds(normalized);
        if (timer != null) {
            boolean ok = timer > 0 && TimerScheduler.start(phone,timer);
            completion.finished(ok,ok
                ? "Таймер телефона запущено на " + timer + " секунд."
                : "Не вдалося запустити таймер. Перевір дозвіл точних будильників.");
            return;
        }
        if (normalized.equals("скасуй таймер") ||
            normalized.equals("зупини таймер") ||
            normalized.equals("вимкни таймер")) {
            boolean ok = TimerScheduler.cancel(phone);
            completion.finished(ok,ok
                ? "Таймер телефона скасовано."
                : "Активного таймера на телефоні немає.");
            return;
        }

        String app = AssistantIntents.requestedApp(normalized);
        if (app != null) {
            String opened = JarvisWearPhoneSession.launchApp(phone,app);
            completion.finished(opened.startsWith("Відкриваю"),opened);
            return;
        }

        // Only free-form, non-device-control speech reaches the phone's AI.
        // No API key, personal token or other credential is sent to the watch.
        if (aiBusy) {
            completion.finished(false,"ШІ на телефоні ще відповідає на попереднє запитання.");
            return;
        }
        if (ai == null) ai = new AiChatClient(phone);
        if (!ai.isConfigured()) {
            completion.finished(false,
                "Налаштуй ШІ один раз на телефоні: Джарвіс → «ШІ — підключення». " +
                "На годиннику ключі вводити не потрібно.");
            return;
        }
        String webQuery = WorldNewsCommands.requestsWorldNews(normalized)
            ? normalized : JarvisSearchRouting.query(raw);
        final String mode = webQuery == null ? "chat" : "web";
        aiBusy = true;
        try {
            ai.sendDetailed(raw,"web".equals(mode) ? "web" : "chat",null,(answer,error) -> {
                aiBusy = false;
                String reply = error == null && answer != null ? answer.reply : error;
                if (reply == null || reply.trim().isEmpty())
                    reply = "ШІ не повернув відповіді.";
                completion.finished(error == null && answer != null,reply);
            });
        } catch (RuntimeException error) {
            aiBusy = false;
            completion.finished(false,"Не вдалося передати запит телефонному ШІ.");
        }
    }

    private static boolean looksLikeFinanceCommand(String normalized) {
        return normalized.matches(
            "^(?:запиши|додай|занеси|зафіксуй)\\s+" +
            "(?:витрату|витрати|дохід|доход|надходження)(?:\\s+.*)?$");
    }
}
