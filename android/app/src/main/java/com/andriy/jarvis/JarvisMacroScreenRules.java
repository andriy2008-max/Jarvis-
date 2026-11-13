package com.andriy.jarvis;

import java.util.Locale;

/** Strict, portable macro screen coordinates. Pixel positions are intentionally
 * not stored: percentages stay stable across similar screen resolutions. */
public final class JarvisMacroScreenRules {
    public static final class Point {
        public final double x, y;
        Point(double x, double y) { this.x=x; this.y=y; }
    }
    private JarvisMacroScreenRules() {}

    /** Input: "45,62" or "45.5,62.25" (X%,Y%). No special or shell tokens. */
    public static Point parsePoint(String raw) {
        if (raw == null || !raw.matches(
                "\\s*\\d{1,2}(?:\\.\\d{1,2})?\\s*,\\s*\\d{1,2}(?:\\.\\d{1,2})?\\s*"))
            throw new IllegalArgumentException("Координати мають формат X,Y у відсотках, наприклад 45,62.");
        String[] parts=raw.split(",");
        double x=Double.parseDouble(parts[0].trim());
        double y=Double.parseDouble(parts[1].trim());
        if (x<3 || x>97 || y<3 || y>97)
            throw new IllegalArgumentException("Точка має бути всередині екрана (3–97%).");
        return new Point(x,y);
    }

    public static String formatPoint(double x,double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y) ||
            x<3 || x>97 || y<3 || y>97)
            throw new IllegalArgumentException("Координати поза робочою областю.");
        return String.format(Locale.ROOT,"%.1f,%.1f",x,y);
    }

    /** Human-readable pause: 0.1–60 seconds; supports Ukrainian comma notation. */
    public static int secondsToMillis(String raw) {
        if(raw==null || !raw.trim().matches("\\d{1,2}(?:[.,]\\d{1,3})?"))
            throw new IllegalArgumentException("Пауза в секундах: 0,1–60, наприклад 1,5.");
        double seconds=Double.parseDouble(raw.trim().replace(',','.'));
        if(seconds<0.1 || seconds>60)
            throw new IllegalArgumentException("Пауза має бути від 0,1 до 60 секунд.");
        return (int)Math.round(seconds*1000);
    }

    /** Maximum wait for an app, button or editable field to appear: 1–60 sec.
     * Old saved macros without a value use 20 seconds. */
    public static int waitTimeoutMillis(String raw) {
        if(raw==null || !raw.matches("\\d{1,2}"))
            throw new IllegalArgumentException("Очікування: ціле число секунд від 1 до 60.");
        int seconds=Integer.parseInt(raw);
        if(seconds<1 || seconds>60)
            throw new IllegalArgumentException("Очікування: від 1 до 60 секунд.");
        return seconds*1000;
    }

    public static boolean validText(String text) {
        return text != null && !text.isEmpty() && text.length() <= 1000 &&
            text.indexOf('\u0000') < 0;
    }
    public static boolean matchesLabel(String requested,String visible) {
        if(requested==null || visible==null)return false;
        String a=requested.replaceAll("\\s+"," ").trim();
        String b=visible.replaceAll("\\s+"," ").trim();
        return !a.isEmpty() && a.length()<=100 &&
            a.equalsIgnoreCase(b);
    }

    /** Never let a replay script blindly tap submit/pay/delete actions. */
    public static boolean riskyLabel(String text) {
        if (text == null) return false;
        String s=text.toLowerCase(Locale.ROOT);
        for (String key : new String[]{"оплат", "сплат", "переказ", "покуп",
                "купити", "замовити", "підтвердити", "видалити", "надіслати",
                "відправити", "pay now", "purchase", "checkout",
                "transfer", "confirm purchase", "delete", "send money"})
            if (s.contains(key)) return true;
        return false;
    }
}
