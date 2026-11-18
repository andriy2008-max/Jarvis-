package com.andriy.jarvis;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.ref.WeakReference;
import java.util.ArrayDeque;

/**
 * Optional, local-only Wear OS screen control. Activated by the watch owner
 * through Settings > Accessibility > Installed apps. No root/screenshot/AI
 * accessibility data transmission. It cannot operate on a phone.
 */
public final class JarvisWatchScreenService extends AccessibilityService {
    public interface Completion { void finished(boolean ok,String detail); }
    public enum Result { WAIT, RUNNING, DONE, BLOCKED }
    private static WeakReference<JarvisWatchScreenService> connected=
        new WeakReference<>(null);
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final int MAX_NODES=450;

    public static boolean available(Context context) {
        return context!=null && context.getPackageManager().hasSystemFeature(
            PackageManager.FEATURE_WATCH) && connected.get()!=null;
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if(!getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            disableSelf(); return;
        }
        connected=new WeakReference<>(this);
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        // User-command-driven only; no passive event-based screen harvesting.
    }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() {
        if(connected.get()==this)connected=new WeakReference<>(null);
        super.onDestroy();
    }

    private AccessibilityNodeInfo appRoot(String expectedPackage) {
        if(expectedPackage==null || expectedPackage.isEmpty())return null;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root==null || !root.isVisibleToUser() || root.getPackageName()==null ||
           !expectedPackage.contentEquals(root.getPackageName()))return null;
        return root;
    }
    public static boolean foreground(String expectedPackage) {
        JarvisWatchScreenService s=connected.get();
        return s!=null && s.appRoot(expectedPackage)!=null;
    }

    /** All actions check the current app package again, before interacting. */
    public static Result perform(String expectedPackage,
                                  ScreenControlCommands.Action action,
                                  Completion cb) {
        JarvisWatchScreenService s=connected.get();
        if(s==null || action==null || cb==null)return Result.BLOCKED;
        AccessibilityNodeInfo root=s.appRoot(expectedPackage);
        if(root==null)return Result.WAIT;
        switch(action.kind) {
            case BACK:
                return s.performGlobalAction(GLOBAL_ACTION_BACK)
                    ? Result.DONE : Result.BLOCKED;
            case CLICK: case LONG_PRESS:
                return s.tapLabel(root,expectedPackage,action,cb);
            case TYPE_TEXT:
                return s.typeText(root,action.label);
            case SCROLL_UP:case SWIPE_UP:
                return s.swipe(expectedPackage,true,true,cb);
            case SCROLL_DOWN:case SWIPE_DOWN:
                return s.swipe(expectedPackage,true,false,cb);
            case SWIPE_LEFT:
                return s.swipe(expectedPackage,false,true,cb);
            case SWIPE_RIGHT:
                return s.swipe(expectedPackage,false,false,cb);
            default:return Result.BLOCKED;
        }
    }

    private Result typeText(AccessibilityNodeInfo root,String text) {
        if(text==null || text.trim().isEmpty() || text.length()>200)return Result.BLOCKED;
        AccessibilityNodeInfo input=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if(input==null || !input.isEditable() || !input.isFocused() ||
           !input.isEnabled() || !input.isVisibleToUser() || input.isPassword())
            return Result.WAIT;
        int type=input.getInputType();
        int variation=type&InputType.TYPE_MASK_VARIATION;
        if(variation==InputType.TYPE_TEXT_VARIATION_PASSWORD ||
           variation==InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
           variation==InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
           variation==InputType.TYPE_NUMBER_VARIATION_PASSWORD)
            return Result.BLOCKED;
        Bundle args=new Bundle();
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text);
        return input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)
            ? Result.DONE : Result.WAIT;
    }

    private Result tapLabel(AccessibilityNodeInfo root,String pkg,
                            ScreenControlCommands.Action action,Completion cb) {
        String wanted=AppNameMatcher.normalize(action.label);
        if(wanted.isEmpty() || ScreenControlCommands.isSensitiveLabel(action.label))
            return Result.BLOCKED;
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        AccessibilityNodeInfo found=null;
        Rect chosen=null;
        int visited=0;
        while(!queue.isEmpty() && ++visited<=MAX_NODES) {
            AccessibilityNodeInfo n=queue.removeFirst();
            if(!n.isVisibleToUser() || !n.isEnabled() || n.isPassword())continue;
            String label=AppNameMatcher.normalize(
                n.getText()==null?"":n.getText().toString());
            String desc=AppNameMatcher.normalize(n.getContentDescription()==null?
                "":n.getContentDescription().toString());
            if(!n.isEditable() && (wanted.equals(label)||wanted.equals(desc))) {
                Rect b=new Rect();
                n.getBoundsInScreen(b);
                if(b.width()>5 && b.height()>5) {
                    if(found!=null && chosen!=null && !chosen.contains(b) &&
                       !b.contains(chosen) && !chosen.equals(b))
                        return Result.WAIT; // Ambiguous, never guess a button.
                    if(found==null || b.width()*b.height()<chosen.width()*chosen.height()) {
                        found=n;chosen=b;
                    }
                }
            }
            for(int i=0;i<n.getChildCount();i++){
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)queue.addLast(child);
            }
        }
        if(visited>MAX_NODES || found==null || chosen==null)return Result.WAIT;

        if(action.kind==ScreenControlCommands.Kind.CLICK) {
            AccessibilityNodeInfo click=found;
            for(int i=0;i<4 && click!=null;i++,click=click.getParent()) {
                if(click.isEnabled() && click.isClickable() &&
                   click.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    return Result.DONE;
            }
        }
        // Some Compose controls don't expose ACTION_CLICK. Gesture fallback
        // uses the verified label rectangle, never a hard-coded screen point.
        Path point=new Path();
        point.moveTo(chosen.exactCenterX(),chosen.exactCenterY());
        long duration=action.kind==ScreenControlCommands.Kind.LONG_PRESS?650L:90L;
        return gesture(pkg,point,duration,cb);
    }

    private Result swipe(String pkg,boolean vertical,boolean reverse,Completion cb) {
        android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
        float w=metrics.widthPixels,h=metrics.heightPixels;
        if(w<160||h<160)return Result.BLOCKED;
        Path p=new Path();
        if(vertical) {
            p.moveTo(w*.50f,h*(reverse?.74f:.29f));
            p.lineTo(w*.50f,h*(reverse?.29f:.74f));
        }else{
            p.moveTo(w*(reverse?.76f:.24f),h*.52f);
            p.lineTo(w*(reverse?.24f:.76f),h*.52f);
        }
        return gesture(pkg,p,340L,cb);
    }

    private Result gesture(String pkg,Path p,long ms,Completion cb) {
        if(appRoot(pkg)==null)return Result.WAIT;
        GestureDescription description=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p,0,ms)).build();
        final boolean accepted;
        try{
            accepted=dispatchGesture(description,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription gesture) {
                    cb.finished(true,"Жест виконано на годиннику.");
                }
                @Override public void onCancelled(GestureDescription gesture) {
                    cb.finished(false,"Wear OS скасувала жест.");
                }
            },main);
        }catch(RuntimeException error){
            return Result.BLOCKED;
        }
        return accepted?Result.RUNNING:Result.BLOCKED;
    }
}
