package com.andriy.jarvis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.View;

/** Tiny standalone drawing of the same 8-bit Jarvis face as the main screen. */
public final class JarvisOrbView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String expression = "idle";
    private String recognizedText = "";
    private boolean transcribing;
    private long showRecognizedUntilMs;
    private final TextPaint transcriptPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private int accent = Color.rgb(114, 235, 255);
    private final int green = Color.rgb(107, 232, 191);

    public JarvisOrbView(Context context) {
        super(context);
        setContentDescription("Плаваюче 8-бітне обличчя Джарвіса. Натисни, щоб говорити.");
        setClickable(true);
        setFocusable(true);
    }

    /** User-selected color, applied immediately without rebuilding the overlay. */
    public void setAccentStyle(int style) {
        switch (style) {
            case 1: accent = Color.rgb(99, 248, 197); break; // turquoise
            case 2: accent = Color.rgb(197, 152, 255); break; // violet
            default: accent = Color.rgb(114, 235, 255); break; // classic blue
        }
        invalidate();
    }

    public void setState(String state) {
        expression = state == null ? "idle" : state;
        invalidate();
    }

    /** Both the main app and floating face render this same in-memory transcript. */
    public void showTranscript(String words, boolean active, long untilMs) {
        recognizedText = JarvisOrbTranscript.visibleTail(words);
        transcribing = active;
        showRecognizedUntilMs = untilMs;
        invalidate();
    }

    private boolean transcriptVisible() {
        return transcribing ||
            (!recognizedText.isEmpty() &&
                SystemClock.uptimeMillis() < showRecognizedUntilMs);
    }

    private void drawRecognizedText(Canvas canvas, float cx, float cy, float radius) {
        String text = recognizedText.isEmpty() ? "Слухаю…" : recognizedText;
        float sp = getResources().getDisplayMetrics().scaledDensity;
        transcriptPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        transcriptPaint.setTextSize(Math.min(14f * sp,
            Math.max(10f * sp, radius * .28f)));
        transcriptPaint.setColor(Color.rgb(224, 253, 255));
        transcriptPaint.setStyle(Paint.Style.FILL);

        int lineWidth = Math.max(1, Math.round(radius * 1.75f));
        // Fit ordinary words before wrapping; narrow phone layouts must not
        // split a short Ukrainian word into two rows.
        float widest=0f;
        for (String word:text.split(" ")) widest=Math.max(widest,transcriptPaint.measureText(word));
        if (widest>lineWidth) transcriptPaint.setTextSize(Math.max(7f*sp,
            transcriptPaint.getTextSize()*lineWidth*.90f/widest));
        String visible = text;
        StaticLayout layout;
        // Keep the latest words visible even when the recognition result is
        // too long for the small circle. Do not send speech data to any server.
        while (true) {
            layout = StaticLayout.Builder.obtain(visible, 0, visible.length(),
                    transcriptPaint, lineWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setIncludePad(false)
                .build();
            if (layout.getLineCount() <= 4 || visible.length() <= 12) break;
            int nextWord = visible.indexOf(' ');
            if (nextWord > 0 && nextWord < visible.length() - 1)
                visible = "…" + visible.substring(nextWord + 1);
            else {
                int start = visible.offsetByCodePoints(0,
                    Math.min(3, visible.codePointCount(0, visible.length()) - 1));
                visible = "…" + visible.substring(start);
            }
        }
        Path clip = new Path();
        clip.addCircle(cx, cy, radius * .93f, Path.Direction.CW);
        canvas.save();
        canvas.clipPath(clip);
        canvas.translate(cx - lineWidth / 2f, cy - layout.getHeight() / 2f);
        layout.draw(canvas);
        canvas.restore();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float dp = getResources().getDisplayMetrics().density;
        float ring = Math.min(getWidth(), getHeight()) * .47f;
        float orb = ring * .79f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(220, 8, 14, 29));
        canvas.drawCircle(cx, cy, ring, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, dp));
        paint.setColor(Color.argb(170, 76, 197, 236));
        canvas.drawCircle(cx, cy, ring, paint);
        paint.setColor(Color.argb(150, 36, 120, 167));
        canvas.drawCircle(cx, cy, ring - 9 * dp, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(13, 46, 84));
        canvas.drawCircle(cx, cy, orb, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.5f * dp);
        paint.setColor("listening".equals(expression) ? green : accent);
        canvas.drawCircle(cx, cy, orb, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(217, 254, 255));
        float pixel = orb * .076f;
        long now = SystemClock.uptimeMillis();
        JarvisControlFeedback.Phase feedback = JarvisControlFeedback.phase(now);
        boolean hearing = !"speaking".equals(expression) &&
            (transcribing || "listening".equals(expression));
        if (JarvisPhoneMode.isActive() || feedback != JarvisControlFeedback.Phase.IDLE) {
            boolean hasWords = transcriptVisible() && !recognizedText.isEmpty() &&
                    !"speaking".equals(expression);
                JarvisPixelPhone.draw(canvas, paint, cx,
                    hasWords ? cy-orb*.40f : cy, orb*(hasWords ? .53f : 1f),
                    hearing, feedback, now);
                if (hasWords) drawRecognizedText(canvas,cx,cy+orb*.40f,orb*.60f);
            postInvalidateDelayed(80L);
            return;
        }
        if (hearing) {
            boolean hasWords = !recognizedText.isEmpty();
            JarvisPixelPhone.drawListening(canvas, paint, cx,
                hasWords ? cy-orb*.35f : cy, orb*(hasWords ? .43f : .74f));
            if (hasWords) drawRecognizedText(canvas,cx,cy+orb*.40f,orb*.58f);
            postInvalidateDelayed(100L);
            return;
        }
        float ox = cx - 8 * pixel, oy = cy - 8 * pixel;
        if (transcriptVisible() && !"speaking".equals(expression)) {
            drawRecognizedText(canvas, cx, cy, orb);
            postInvalidateDelayed(180);
            return;
        }
        boolean blink = SystemClock.uptimeMillis() % 5200 > 4880;
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
                int height = JarvisSpeechMouth.height();
                if (height > 0)
                    drawPixel(canvas, ox, oy, pixel, 6, 10, 4, height);
                else
                    drawPixel(canvas, ox, oy, pixel, 6, 11, 4, 1);
                break;
            default:
                drawPixel(canvas, ox, oy, pixel, 4, 11, 2, 1);
                drawPixel(canvas, ox, oy, pixel, 6, 12, 4, 1);
                drawPixel(canvas, ox, oy, pixel, 10, 11, 2, 1);
        }
        if ("listening".equals(expression)) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setColor(green);
            paint.setStrokeWidth(2 * dp);
            canvas.drawCircle(cx, cy, ring - 3 * dp, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        postInvalidateDelayed("speaking".equals(expression) ? 42L : 180L);
    }

    private void drawPixel(Canvas c, float x, float y, float size,
                           int dx, int dy, int w, int h) {
        c.drawRect(x + dx * size, y + dy * size,
            x + (dx + w) * size, y + (dy + h) * size, paint);
    }
}
