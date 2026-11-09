package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure-Java voice conversation state machine. The recognizer owns audio; this class only
 * interprets already-transcribed text. Neither wake-only nor finish events go to AI.
 */
public final class WakeConversation {
    public enum Kind { IGNORE, ARMED, COMMAND, FINISHED }

    public static final class Decision {
        public final Kind kind;
        public final String command;
        private Decision(Kind kind, String command) {
            this.kind = kind;
            this.command = command;
        }
    }

    public static final long WAKE_ONLY_WINDOW_MS = 12_000L;
    public static final long CONVERSATION_WINDOW_MS = 35_000L;

    private static final Pattern NAME = Pattern.compile(
        "^(?:(?:гей|ей)\\s+)?(?:джарвіс|джарвис|jarvis)(?:[\\s,.:;!?—-]+|$)(.*)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Decision IGNORE = new Decision(Kind.IGNORE, "");
    private static final Decision ARMED = new Decision(Kind.ARMED, "");
    private static final Decision FINISHED = new Decision(Kind.FINISHED, "");

    private long untilMs;
    private boolean active;

    /** Spoken transcript; manual capture bypasses the wake requirement. */
    public synchronized Decision accept(String utterance, long nowMs, boolean manualCapture) {
        if (utterance == null) return IGNORE;
        String normalized = utterance.trim().toLowerCase(Locale.forLanguageTag("uk-UA"))
            .replaceAll("\\s+", " ").trim();
        if (normalized.isEmpty()) return IGNORE;
        if (active && nowMs >= untilMs) end();

        Matcher match = NAME.matcher(normalized);
        boolean calledByName = match.matches();
        if (!calledByName && !active && !manualCapture) return IGNORE;
        String command = calledByName ? match.group(1).trim() : normalized;
        command = command.replaceAll("^[,.:;!?—\\s-]+|[,.:;!?—\\s-]+$", "").trim();

        if (isFinish(command)) {
            end();
            return FINISHED;
        }
        if (calledByName && command.isEmpty()) {
            active = true;
            untilMs = nowMs + WAKE_ONLY_WINDOW_MS;
            return ARMED;
        }
        if (command.isEmpty()) return IGNORE;
        active = true;
        untilMs = nowMs + CONVERSATION_WINDOW_MS;
        return new Decision(Kind.COMMAND, command);
    }

    private static boolean isFinish(String command) {
        return command.equals("досить") || command.equals("закінчи розмову") ||
            command.equals("заверши розмову") || command.equals("кінець розмови") ||
            command.equals("бувай джарвіс");
    }

    /** Extend follow-up window after a spoken response completes, not while it is playing. */
    public synchronized void assistantFinished(long nowMs) {
        if (active) untilMs = nowMs + CONVERSATION_WINDOW_MS;
    }

    public synchronized boolean isActive(long nowMs) {
        if (active && nowMs >= untilMs) end();
        return active;
    }

    public synchronized void end() {
        active = false;
        untilMs = 0L;
    }
}
