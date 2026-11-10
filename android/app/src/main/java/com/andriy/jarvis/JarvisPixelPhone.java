package com.andriy.jarvis;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** 16x16 pixel art cellphone, shares the existing cyan Jarvis orb palette. */
public final class JarvisPixelPhone {
    private JarvisPixelPhone() {}

    private static void rect(Canvas canvas, Paint paint, float ox, float oy,
                             float unit, int x, int y, int width, int height) {
        canvas.drawRect(ox + x * unit, oy + y * unit,
            ox + (x + width) * unit, oy + (y + height) * unit, paint);
    }

    public static void draw(Canvas canvas, Paint paint, float cx, float cy,
                            float unit) {
        float ox = cx - 8f * unit;
        float oy = cy - 8f * unit;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(127, 231, 255));
        // Two-tone phone frame with squared, blocky 8-bit corners.
        rect(canvas,paint,ox,oy,unit,5,1,6,1);
        rect(canvas,paint,ox,oy,unit,4,2,8,12);
        rect(canvas,paint,ox,oy,unit,5,14,6,1);
        paint.setColor(Color.rgb(10, 35, 69));
        rect(canvas,paint,ox,oy,unit,5,3,6,10);
        paint.setColor(Color.rgb(20, 111, 157));
        rect(canvas,paint,ox,oy,unit,6,4,4,7);
        paint.setColor(Color.rgb(190, 247, 255));
        rect(canvas,paint,ox,oy,unit,7,2,2,1);  // earpiece
        rect(canvas,paint,ox,oy,unit,7,12,2,1); // home control
        paint.setColor(Color.rgb(100, 222, 231));
        rect(canvas,paint,ox,oy,unit,7,6,2,2); // centered command symbol
    }
}
