package com.andriy.jarvis;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Explicit, user-driven, screenshot-free point picker. In navigation mode,
 * the translucent surface is untouchable: the underlying app remains usable.
 * On "Pick", one touch stores a normalized point but never performs a tap.
 * It is not a background touch recorder. */
public final class JarvisMacroPointPicker {
    public interface Callback { void onPick(String percentPoint); }
    private final AccessibilityService context;
    private final Callback callback;
    private final String expectedPackage;
    private final WindowManager windows;
    private WindowManager.LayoutParams surfaceParams;
    private Surface surface;
    private LinearLayout controls;
    private TextView status;
    private boolean choosing;
    private boolean closed;
    private double x=50, y=50;
    private boolean hasPoint;

    JarvisMacroPointPicker(AccessibilityService context,String expectedPackage,Callback callback) {
        this.context=context;
        this.expectedPackage=expectedPackage;
        this.callback=callback;
        windows=(WindowManager)context.getSystemService(android.content.Context.WINDOW_SERVICE);
    }

    private Button button(String title,Runnable action) {
        Button view=new Button(context);
        view.setText(title);
        view.setTextSize(12);
        view.setAllCaps(false);
        view.setOnClickListener(v->action.run());
        return view;
    }

    void show() {
        if(windows==null)throw new IllegalStateException("No window manager");
        int type=WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        int base=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        surface=new Surface();
        surfaceParams=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,type,base|
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,PixelFormat.TRANSLUCENT);
        surfaceParams.gravity=Gravity.TOP|Gravity.START;
        windows.addView(surface,surfaceParams);
        controls=new LinearLayout(context);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(8,8,8,8);
        controls.setBackgroundColor(Color.argb(237,11,26,48));
        status=new TextView(context);
        status.setTextColor(Color.WHITE);
        status.setTextSize(13);
        status.setText("🎯 Перейди на потрібний екран і натисни «Вибрати».");
        controls.addView(status);
        controls.addView(button("📍 Вибрати точку",()->setChoosing(true)));
        controls.addView(button("👆 Навігація по програмі",()->setChoosing(false)));
        controls.addView(button("✅ Зберегти точку",()->{
            if(!hasPoint){status.setText("Спочатку вибери точку на екрані.");return;}
            String point=JarvisMacroScreenRules.formatPoint(x,y);
            finish(point);
        }));
        controls.addView(button("✕ Скасувати",()->finish(null)));
        WindowManager.LayoutParams options=new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,type,base,
            PixelFormat.TRANSLUCENT);
        options.gravity=Gravity.TOP|Gravity.CENTER_HORIZONTAL;
        options.y=44;
        try { windows.addView(controls,options); }
        catch(Exception problem){close();throw problem;}
    }

    private void setChoosing(boolean selected) {
        if(closed || surface==null)return;
        choosing=selected;
        if(selected) {
            surfaceParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            status.setText("Торкнись місця потрібної кнопки. Натискання НЕ виконається.");
        } else {
            surfaceParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            status.setText(hasPoint?"Точка "+JarvisMacroScreenRules.formatPoint(x,y)+
                ". Можеш перейти на інший екран.":"Відкрий потрібний екран у програмі.");
        }
        try{windows.updateViewLayout(surface,surfaceParams);}
        catch(Exception ignored){finish(null);}
        surface.invalidate();
    }
    private void finish(String point) {
        if(closed)return;
        close();
        if(callback!=null) callback.onPick(point);
    }
    public void close() {
        if(closed)return;
        closed=true;
        if(windows!=null) {
            if(controls!=null)try{windows.removeView(controls);}catch(Exception ignored){}
            if(surface!=null)try{windows.removeView(surface);}catch(Exception ignored){}
        }
        controls=null;surface=null;
    }
    private final class Surface extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Surface(){super(context);setBackgroundColor(Color.TRANSPARENT);}
        @Override public boolean onTouchEvent(MotionEvent event) {
            if(!choosing)return false;
            if(event.getActionMasked()==MotionEvent.ACTION_UP) {
                AccessibilityNodeInfo target=context.getRootInActiveWindow();
                Rect bounds=new Rect();
                if(target==null || target.getPackageName()==null ||
                   !expectedPackage.equals(target.getPackageName().toString())) {
                    status.setText("Зараз відкрита інша програма. Повернись до цільової.");
                    return true;
                }
                target.getBoundsInScreen(bounds);
                if(bounds.width()<120 || bounds.height()<160 ||
                   !bounds.contains((int)event.getRawX(),(int)event.getRawY())) {
                    status.setText("Обери точку в активній області програми.");
                    return true;
                }
                double px=100.0*(event.getRawX()-bounds.left)/bounds.width();
                double py=100.0*(event.getRawY()-bounds.top)/bounds.height();
                if(px>=3 && px<=97 && py>=3 && py<=97) {
                    x=px; y=py; hasPoint=true;
                    status.setText("Збережеться точка "+
                        JarvisMacroScreenRules.formatPoint(x,y)+
                        ". Натисни «Зберегти точку».");
                    invalidate();
                } else status.setText("Обери точку всередині екрана (3–97%).");
            }
            return true;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if(!choosing)return;
            paint.setColor(Color.argb(44,0,180,255));
            canvas.drawColor(Color.argb(30,0,150,200));
            if(!hasPoint)return;
            AccessibilityNodeInfo target=context.getRootInActiveWindow();
            if(target==null || target.getPackageName()==null ||
               !expectedPackage.equals(target.getPackageName().toString()))return;
            Rect bounds=new Rect();
            target.getBoundsInScreen(bounds);
            int[] offset=new int[2];
            getLocationOnScreen(offset);
            float sx=(float)(bounds.left+bounds.width()*x/100.0-offset[0]);
            float sy=(float)(bounds.top+bounds.height()*y/100.0-offset[1]);
            paint.setColor(Color.YELLOW);
            paint.setStrokeWidth(3f);
            canvas.drawCircle(sx,sy,20,paint);
            canvas.drawLine(sx-30,sy,sx+30,sy,paint);
            canvas.drawLine(sx,sy-30,sx,sy+30,paint);
        }
    }
}
