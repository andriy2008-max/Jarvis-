package com.andriy.jarvis;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Dependency-free parsing for delegated Android clock actions and app launching. */
public final class AssistantIntents {
    private static final int MAX_REMINDER_SECONDS = 24 * 60 * 60;
    private static final Pattern ALARM = Pattern.compile(
        "^(?:(?:постав|встанови|заведи)\\s+)?будильник\\s+(?:на|о)\\s+(\\d{1,2})[:.](\\d{2})$|^розбуди\\s+мене\\s+о\\s+(\\d{1,2})[:.](\\d{2})$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    // Speech recognition commonly returns "на 7" or "на 7 30", not "07:30".
    // The strict numeric parser above remains for backward compatibility.
    private static final Pattern ALARM_SPOKEN = Pattern.compile(
        "^(?:(?:постав|поставити|встанови|установи|заведи|увімкни|включи)\\s+)?будильник\\s+(?:на|о)\\s+(.+)$" +
        "|^розбуди\\s+мене\\s+о\\s+(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ALARM_HOUR_MINUTE = Pattern.compile(
        "^(\\d{1,2})(?:\\s+годин(?:а|у|и)?)?(?:[:.]|\\s+)(\\d{1,2})" +
        "(?:\\s+хвилин(?:а|у|и)?)?(?:\\s+(ранку|зранку|вранці|вечора|ввечері|увечері|дня|вдень|ночі))?$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ALARM_HOUR_ONLY = Pattern.compile(
        "^(\\d{1,2})(?:\\s+годин(?:а|у|и)?)?" +
        "(?:\\s+(ранку|зранку|вранці|вечора|ввечері|увечері|дня|вдень|ночі))?$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ALARM_NO_TIME = Pattern.compile(
        "^(?:(?:постав|поставити|встанови|установи|заведи|увімкни|включи)\\s+)?будильник(?:\\s+(?:на|о))?$" +
        "|^розбуди\\s+мене(?:\\s+о)?$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private static final String REMIND_PREFIX = "^(?:нагадай|нагадати)(?:\\s+мені)?\\s+";
    private static final Pattern REMINDER_AFTER = Pattern.compile(
        REMIND_PREFIX + "через\\s+(\\d{1,4})\\s*(секунд(?:у|и|ів)?|сек|с|хвилин(?:у|и)?|хв|годин(?:у|и)?|год)\\s+(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REMINDER_AT = Pattern.compile(
        REMIND_PREFIX + "(?:о|на)\\s+(\\d{1,2})[:.](\\d{2})\\s+(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern REMINDER_AT_END = Pattern.compile(
        REMIND_PREFIX + "(.+?)\\s+о\\s+(\\d{1,2})[:.](\\d{2})$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern OPEN_APP = Pattern.compile(
        "^(?:запусти|відкрий)(?:\\s+(?:додаток|застосунок|програму))?\\s+(.+)$",
        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private AssistantIntents() {}

    public static final class ClockTime {
        public final int hour;
        public final int minute;
        public ClockTime(int hour, int minute) { this.hour = hour; this.minute = minute; }
    }

    public static final class Reminder {
        public final int seconds;  // >0: relative reminder via system timer, otherwise clock time
        public final ClockTime time;
        public final String message;
        private Reminder(int seconds, ClockTime time, String message) {
            this.seconds = seconds;
            this.time = time;
            this.message = message;
        }
    }

    private static ClockTime clock(String hh, String mm) {
        int h = Integer.parseInt(hh);
        int m = Integer.parseInt(mm);
        return h >= 0 && h <= 23 && m >= 0 && m <= 59 ? new ClockTime(h, m) : null;
    }

    public static ClockTime alarmTime(String normalized) {
        if (normalized == null) return null;
        String spoken=normalized.trim();
        Matcher m = ALARM.matcher(spoken);
        if (m.matches())
            return m.group(1) != null ? clock(m.group(1), m.group(2))
                : clock(m.group(3), m.group(4));
        Matcher natural=ALARM_SPOKEN.matcher(spoken);
        if(!natural.matches())return null;
        String value=natural.group(1)!=null?natural.group(1):natural.group(2);
        Matcher minutes=ALARM_HOUR_MINUTE.matcher(value);
        if(minutes.matches())
            return spokenClock(minutes.group(1),minutes.group(2),minutes.group(3));
        Matcher hours=ALARM_HOUR_ONLY.matcher(value);
        return hours.matches()?spokenClock(hours.group(1),"0",hours.group(2)):null;
    }

    private static ClockTime spokenClock(String hour,String minute,String period) {
        ClockTime t=clock(hour,minute);
        if(t==null || period==null)return t;
        String p=period.toLowerCase(new Locale("uk","UA"));
        if(p.equals("вечора") || p.equals("ввечері") || p.equals("увечері") ||
           p.equals("дня") || p.equals("вдень")) {
            if(t.hour>=1 && t.hour<=11)return new ClockTime(t.hour+12,t.minute);
        } else if(p.equals("ранку") || p.equals("зранку") || p.equals("вранці")) {
            if(t.hour==12)return new ClockTime(0,t.minute);
        } else if(p.equals("ночі") && t.hour==12) {
            return new ClockTime(0,t.minute);
        }
        return t;
    }

    /** Bare watch command should open a clock picker, not fail or call AI. */
    public static boolean alarmNeedsTime(String normalized) {
        return normalized!=null && ALARM_NO_TIME.matcher(normalized.trim()).matches();
    }

    public static Reminder reminder(String normalized) {
        if (normalized == null) return null;
        Matcher after = REMINDER_AFTER.matcher(normalized);
        if (after.matches()) {
            long amount = Long.parseLong(after.group(1));
            String unit = after.group(2);
            long factor = unit.startsWith("год") ? 3600L : unit.startsWith("хв") ? 60L : 1L;
            long seconds = amount * factor;
            String message = cleanMessage(after.group(3));
            if (seconds < 1 || seconds > MAX_REMINDER_SECONDS || message == null) return null;
            return new Reminder((int) seconds, null, message);
        }
        Matcher at = REMINDER_AT.matcher(normalized);
        if (at.matches()) {
            ClockTime time = clock(at.group(1), at.group(2));
            String message = cleanMessage(at.group(3));
            return time == null || message == null ? null : new Reminder(0, time, message);
        }
        Matcher atEnd = REMINDER_AT_END.matcher(normalized);
        if (atEnd.matches()) {
            ClockTime time = clock(atEnd.group(2), atEnd.group(3));
            String message = cleanMessage(atEnd.group(1));
            return time == null || message == null ? null : new Reminder(0, time, message);
        }
        return null;
    }

    private static String cleanMessage(String text) {
        String message = text.trim().replaceFirst("^(?:про|щоб|те,?\\s+що)\\s+", "").trim();
        if (message.isEmpty()) return null;
        return message.length() > 160 ? message.substring(0, 160) : message;
    }

    public static boolean isReminderCommand(String normalized) {
        return normalized != null && (normalized.startsWith("нагадай") || normalized.startsWith("нагадати"));
    }

    public static boolean isAlarmCommand(String normalized) {
        return normalized != null && (normalized.contains("будильник") || normalized.startsWith("розбуди "));
    }

    /** Returns user-supplied app label, or null when this is not an app launch request. */
    public static String requestedApp(String normalized) {
        if (normalized == null) return null;
        Matcher m = OPEN_APP.matcher(normalized);
        if (!m.matches()) return null;
        String candidate = m.group(1).trim();
        return candidate.length() > 80 ? null : candidate;
    }

    /** Exact label aliases; no fuzzy substring matching on untrusted app/package names. */
    public static String appLabel(String spoken) {
        if (spoken == null) return "";
        String s = spoken.trim().toLowerCase(new Locale("uk", "UA"))
            .replaceAll("[.!?,:;]+$", "").trim();
        switch (s) {
            case "ютуб": case "youtube": return "youtube";
            case "телеграм": case "telegram": return "telegram";
            case "вайбер": case "viber": return "viber";
            case "вотсап": case "ватсап": case "whatsapp": return "whatsapp";
            case "інстаграм": case "instagram": return "instagram";
            case "тік ток": case "тікток": case "tiktok": return "tiktok";
            case "спотіфай": case "spotify": return "spotify";
            case "хром": case "chrome": return "chrome";
            case "чатгпт": case "чат гпт": case "chatgpt": return "chatgpt";
            case "гмейл": case "gmail": return "gmail";
            case "камеру": case "камера": return "camera";
            case "календар": case "календарь": return "calendar";
            case "годинник": return "clock";
            case "карти": case "карта": case "мапи": return "google maps";
            case "калькулятор": return "calculator";
            default: return s;
        }
    }
}
