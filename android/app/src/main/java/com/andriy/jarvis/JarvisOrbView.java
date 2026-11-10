package com.andriy.jarvis;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/** Tiny standalone drawing of the same 8-bit Jarvis face as the main screen. */
public final class JarvisOrbView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String expression = "idle";
    private final int cyan = Color.rgb(114, 235, 255);
    private final int green = Color.rgb(107, 232, 191);

    public JarvisOrbView(Context context) {
        super(context);
        setContentDescription("Плаваюче 8-бітне обличчя Джарвіса. Натисни, щоб говорити.");
        setClickable(true);
        setFocusable(true);
    }

    public void setState(String state) {
        expression = state == null ? "idle" : state;
        invalidate();
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
        paint.setColor("listening".equals(expression) ? green : cyan);
        canvas.drawCircle(cx, cy, orb, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(217, 254, 255));
        float pixel = orb * .076f;
        float ox = cx - 8 * pixel, oy = cy - 8 * pixel;
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
                drawPixel(canvas, ox, oy, pixel, 6, 10, 4,
                    (SystemClock.uptimeMillis() / 270) % 2 == 0 ? 3 : 1);
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
        postInvalidateDelayed(180);
    }

    private void drawPixel(Canvas c, float x, float y, float size,
                           int dx, int dy, int w, int h) {
        c.drawRect(x + dx * size, y + dy * size,
            x + (dx + w) * size, y + (dy + h) * size, paint);
    }
}
