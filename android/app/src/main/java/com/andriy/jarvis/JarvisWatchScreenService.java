package com.andriy.jarvis;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityWindowInfo;
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
        try { return appRootUnsafe(expectedPackage); }
        catch(RuntimeException failure) {
            JarvisDiagnostics.error(JarvisDiagnostics.Event.ACCESSIBILITY_ERROR,failure);
            return null;
        }
    }
    private AccessibilityNodeInfo appRootUnsafe(String expectedPackage) {
        if(expectedPackage==null || expectedPackage.isEmpty())return null;
        AccessibilityNodeInfo root=getRootInActiveWindow();
        if(root!=null && root.isVisibleToUser() && root.getPackageName()!=null &&
           expectedPackage.contentEquals(root.getPackageName()))return root;
        // The Wear OS keyboard sometimes becomes the active accessibility
        // window. Read the still-visible Spotify window beneath the IME, but
        // never operate behind permission dialogs or other applications.
        boolean imeVisible=false;
        for(AccessibilityWindowInfo win:getWindows()) {
            if(win.getType()==AccessibilityWindowInfo.TYPE_INPUT_METHOD &&
               win.isActive())imeVisible=true;
        }
        if(!imeVisible)return null;
        for(AccessibilityWindowInfo win:getWindows()) {
            if(win.getType()!=AccessibilityWindowInfo.TYPE_APPLICATION)continue;
            AccessibilityNodeInfo candidate=win.getRoot();
            if(candidate!=null && candidate.isVisibleToUser() &&
               candidate.getPackageName()!=null &&
               expectedPackage.contentEquals(candidate.getPackageName()))
                return candidate;
        }
        return null;
    }
    public static boolean foreground(String expectedPackage) {
        JarvisWatchScreenService s=connected.get();
        return s!=null && s.appRoot(expectedPackage)!=null;
    }

    /** All actions check the current app package again, before interacting. */
    public static Result perform(String expectedPackage,
                                  ScreenControlCommands.Action action,
                                  Completion cb) {
        try {
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

        } catch(RuntimeException failure) {
            JarvisDiagnostics.error(JarvisDiagnostics.Event.ACCESSIBILITY_ERROR,failure);
            return Result.BLOCKED;
        }
    }


    // Google Maps UI automation on this watch, never on the phone.
    // The target application package is checked at every step and before
    // gesture injection. No fixed screen positions are used.
    public enum MapsStep {
        CENTER_LOCATION,OPEN_SEARCH,SET_DESTINATION,SUBMIT_SEARCH,
        FIRST_RESULT,DIRECTIONS,WALKING
    }
    private static final String MAPS_PKG="com.google.android.apps.maps";

    public static Result mapsStep(MapsStep step,String destination,
                                   Completion completion) {
        try {
        JarvisWatchScreenService service=connected.get();
        if(service==null || step==null || completion==null)return Result.BLOCKED;
        AccessibilityNodeInfo root=service.appRoot(MAPS_PKG);
        if(root==null)return Result.WAIT;
        switch(step) {
            case CENTER_LOCATION:
                return service.mapsTapExact(root,completion,
                    "my location","your location","current location",
                    "show your location","center on my location",
                    "моє місцезнаходження","моє розташування",
                    "поточне місцезнаходження","моє місце",
                    "моя геопозиція","моє положення");
            case OPEN_SEARCH:
                if(service.spotifyHasEditable(root))return Result.DONE;
                return service.mapsTapExact(root,completion,
                    "search","пошук","поиск","search here",
                    "search maps","знайти місце","шукати");
            case SET_DESTINATION:
                return service.mapsSetDestination(root,destination);
            case SUBMIT_SEARCH:
                return service.mapsSubmitSearch(root);
            case FIRST_RESULT:
                return service.mapsPickFirstResult(root,destination,completion);
            case DIRECTIONS:
                return service.mapsTapExact(root,completion,
                    "directions","get directions","маршрут",
                    "маршрути","прокласти маршрут","напрямки","route",
                    "маршрут до");
            case WALKING:
                return service.mapsTapExact(root,completion,
                    "walking","walk","walking directions",
                    "пішки","пішохідний","пешком","пеший");
            default:return Result.BLOCKED;
        }

        } catch(RuntimeException failure) {
            JarvisDiagnostics.error(JarvisDiagnostics.Event.ACCESSIBILITY_ERROR,failure);
            return Result.BLOCKED;
        }
    }

    private Result mapsTapExact(AccessibilityNodeInfo root,Completion cb,
                                String... labels) {
        AccessibilityNodeInfo best=null;
        Rect bounds=null;
        for(AccessibilityNodeInfo node:spotifyNodes(root)) {
            if(node.isEditable() || !spotifyNodeMatches(node,labels))continue;
            Rect b=new Rect();node.getBoundsInScreen(b);
            if(b.width()<6 || b.height()<6)continue;
            if(best==null || b.top<bounds.top ||
               (b.top==bounds.top && b.width()*b.height()<
                                       bounds.width()*bounds.height())) {
                best=node;bounds=b;
            }
        }
        return best==null?Result.WAIT:mapsTapNode(best,cb);
    }

    private Result mapsTapNode(AccessibilityNodeInfo node,Completion cb) {
        if(appRoot(MAPS_PKG)==null)return Result.WAIT;
        AccessibilityNodeInfo parent=node;
        for(int i=0;i<4 && parent!=null;i++,parent=parent.getParent()) {
            if(parent.isEnabled() && parent.isClickable() &&
               parent.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                return Result.DONE;
        }
        Rect box=new Rect();node.getBoundsInScreen(box);
        if(box.width()<6 || box.height()<6)return Result.WAIT;
        Path point=new Path();
        point.moveTo(box.exactCenterX(),box.exactCenterY());
        return gesture(MAPS_PKG,point,90L,cb);
    }

    private Result mapsSetDestination(AccessibilityNodeInfo root,String destination) {
        if(destination==null || destination.trim().isEmpty() ||
           destination.length()>180)return Result.BLOCKED;
        for(AccessibilityNodeInfo node:spotifyNodes(root)) {
            if(!node.isEditable() || node.isPassword())continue;
            // Touch/focus the search input to open the Wear OS keyboard, then
            // enter dictated text using the accessibility text action.
            if(!node.isFocused()) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                node.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            }
            Bundle args=new Bundle();
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                destination);
            return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)
                ?Result.DONE:Result.WAIT;
        }
        return Result.WAIT;
    }

    private Result mapsSubmitSearch(AccessibilityNodeInfo root) {
        for(AccessibilityNodeInfo node:spotifyNodes(root)) {
            if(!node.isEditable() || node.isPassword())continue;
            if(Build.VERSION.SDK_INT>=30) {
                int enter=AccessibilityNodeInfo.AccessibilityAction
                    .ACTION_IME_ENTER.getId();
                for(AccessibilityNodeInfo.AccessibilityAction action:
                    node.getActionList()) {
                    if(action.getId()==enter)
                        return node.performAction(enter)
                            ?Result.DONE:Result.WAIT;
                }
            }
            // Autocomplete may already display suggestions as text is set.
            return Result.DONE;
        }
        // IME dismissed after a successful submit; proceed to find a result.
        return Result.DONE;
    }

    private Result mapsPickFirstResult(AccessibilityNodeInfo root,String query,
                                       Completion cb) {
        if(query==null || query.trim().isEmpty())return Result.BLOCKED;
        String normalized=AppNameMatcher.normalize(query);
        if(normalized.isEmpty())return Result.BLOCKED;
        String[] tokens=normalized.split(" ");
        int below=0;
        int screenHeight=getResources().getDisplayMetrics().heightPixels;
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            if(n.isEditable()) {
                Rect b=new Rect();n.getBoundsInScreen(b);
                below=Math.max(below,b.bottom);
            }
        }
        if(below==0)below=(int)(screenHeight*.18f);
        AccessibilityNodeInfo selected=null;
        Rect best=null;
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            if(n.isEditable())continue;
            String label=spotifyLabel(n);
            if(label.isEmpty() || label.equals(normalized)) {
                // Exact text still qualifies if it is below the input. Do
                // not reject it; the coordinate check protects the input.
                if(label.isEmpty())continue;
            }
            // Only a result relevant to the dictated place can be selected.
            // Excludes toolbar buttons, map controls, unrelated locations.
            boolean related=label.equals(normalized) ||
                label.startsWith(normalized+" ") ||
                normalized.startsWith(label+" ");
            if(!related) {
                for(String token:tokens) {
                    if(token.length()>=4 &&
                       (label.equals(token) || label.contains(token))) {
                        related=true;break;
                    }
                }
            }
            if(!related || n.isPassword())continue;
            Rect b=new Rect();n.getBoundsInScreen(b);
            if(b.width()<6 || b.height()<6 || b.top<below+4)continue;
            if(best==null || b.top<best.top ||
               (b.top==best.top && b.left<best.left)) {
                selected=n;best=b;
            }
        }
        return selected==null?Result.WAIT:mapsTapNode(selected,cb);
    }

    // Spotify on Wear OS has its own navigation and search. Each operation is
    // restricted to Spotify's visible accessibility tree on THIS watch.
    // Screen locations are never copied from the paired phone.
    public enum SpotifyStep {
        OPEN_SEARCH, REVEAL_NAV, FOCUS_SEARCH, SET_QUERY, SUBMIT_QUERY,
        SELECT_RESULT, PLAY_PLAYLIST, VERIFY_PLAYLIST
    }
    private static final String SPOTIFY_PKG="com.spotify.music";

    public static Result spotifyStep(SpotifyStep step,String title,
                                     boolean playlist,Completion completion) {
        try {
        return spotifyStep(step,title,playlist,false,completion);

        } catch(RuntimeException failure) {
            JarvisDiagnostics.error(JarvisDiagnostics.Event.ACCESSIBILITY_ERROR,failure);
            return Result.BLOCKED;
        }
    }

    /** Genre searches allow a prefix match in results only. Named requests
     * still require exact song/playlist names. */
    public static Result spotifyStep(SpotifyStep step,String title,boolean playlist,
                                     boolean genreDiscovery,Completion completion) {
        // Original 4-argument implementation follows this wrapper.
        return spotifyStepInternal(step,title,playlist,genreDiscovery,completion);
    }

    private static Result spotifyStepInternal(SpotifyStep step,String title,
                                     boolean playlist,boolean genreDiscovery,Completion completion) {
        JarvisWatchScreenService service=connected.get();
        if(service==null || step==null || completion==null)return Result.BLOCKED;
        AccessibilityNodeInfo root=service.appRoot(SPOTIFY_PKG);
        if(root==null)return Result.WAIT;
        switch(step) {
            case OPEN_SEARCH:
                if(service.spotifyHasEditable(root))return Result.DONE;
                return service.spotifyTapExact(root,completion,
                    "search","пошук","поиск");
            case REVEAL_NAV:
                return service.swipe(SPOTIFY_PKG,true,true,completion);
            case FOCUS_SEARCH:
                return service.spotifyFocusSearch(root,completion);
            case SET_QUERY:
                return service.spotifySetQuery(root,title);
            case SUBMIT_QUERY:
                return service.spotifySubmitQuery(root);
            case SELECT_RESULT:
                return service.spotifySelectResult(root,title,playlist,genreDiscovery,completion);
            case PLAY_PLAYLIST:
                // The mini-player can say Pause for an unrelated song.
                // Prefer the playlist's actual Play action if it is visible.
                Result play=service.spotifyTapPlay(root,completion);
                if(play!=Result.WAIT)return play;
                return service.spotifyHasTitle(root,title) &&
                       service.spotifyHasLabel(root,"pause","призупинити","пауза")
                    ?Result.DONE:Result.WAIT;
            case VERIFY_PLAYLIST:
                // A play button click is not proof of playback. Only report
                // verified when the target is present with an active Pause UI.
                return service.spotifyHasTitle(root,title) &&
                       service.spotifyHasLabel(root,"pause","призупинити","пауза")
                        ?Result.DONE:Result.WAIT;
            default:return Result.BLOCKED;
        }
    }

    private static String spotifyLabel(AccessibilityNodeInfo n) {
        CharSequence text=n.getText();
        if(text!=null && text.length()>0)
            return AppNameMatcher.normalize(text.toString());
        CharSequence content=n.getContentDescription();
        return AppNameMatcher.normalize(content==null?"":content.toString());
    }

    private static String spotifyDescription(AccessibilityNodeInfo n) {
        CharSequence content=n.getContentDescription();
        return AppNameMatcher.normalize(content==null?"":content.toString());
    }

    private static boolean spotifyNodeMatches(AccessibilityNodeInfo n,String... names) {
        return spotifyMatches(spotifyLabel(n),names) ||
               spotifyMatches(spotifyDescription(n),names);
    }

    private static boolean spotifyMatches(String label,String... names) {
        for(String name:names) {
            String wanted=AppNameMatcher.normalize(name);
            if(label.equals(wanted) || label.startsWith(wanted+" "))return true;
        }
        return false;
    }

    private ArrayDeque<AccessibilityNodeInfo> spotifyNodes(AccessibilityNodeInfo root) {
        ArrayDeque<AccessibilityNodeInfo> result=new ArrayDeque<>();
        ArrayDeque<AccessibilityNodeInfo> queue=new ArrayDeque<>();
        queue.add(root);
        int seen=0;
        while(!queue.isEmpty() && ++seen<=MAX_NODES) {
            AccessibilityNodeInfo n=queue.removeFirst();
            if(n==null || !n.isVisibleToUser() || !n.isEnabled() || n.isPassword())
                continue;
            result.addLast(n);
            for(int i=0;i<n.getChildCount();i++) {
                AccessibilityNodeInfo child=n.getChild(i);
                if(child!=null)queue.addLast(child);
            }
        }
        if(seen>MAX_NODES)result.clear();
        return result;
    }

    private boolean spotifyHasEditable(AccessibilityNodeInfo root) {
        for(AccessibilityNodeInfo n:spotifyNodes(root))
            if(n.isEditable() && !n.isPassword())return true;
        return false;
    }

    private boolean spotifyHasLabel(AccessibilityNodeInfo root,String... labels) {
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            if(!n.isEditable() && spotifyNodeMatches(n,labels))
                return true;
        }
        return false;
    }

    private boolean spotifyHasTitle(AccessibilityNodeInfo root,String title) {
        String wanted=AppNameMatcher.normalize(title);
        if(wanted.isEmpty())return false;
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            String label=spotifyLabel(n);
            String description=spotifyDescription(n);
            if(!n.isEditable() && (spotifyTitleMatches(label,wanted) ||
                                  spotifyTitleMatches(description,wanted)))
                return true;
        }
        return false;
    }

    private Result spotifyTapExact(AccessibilityNodeInfo root,Completion cb,String... names) {
        AccessibilityNodeInfo selected=null;
        Rect bestBounds=null;
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            if(n.isEditable() || !spotifyNodeMatches(n,names))continue;
            Rect b=new Rect();n.getBoundsInScreen(b);
            if(b.width()<6 || b.height()<6)continue;
            if(bestBounds==null || b.top<bestBounds.top) {
                selected=n;bestBounds=b;
            }
        }
        return selected==null?Result.WAIT:spotifyTapNode(selected,cb);
    }

    private Result spotifyTapNode(AccessibilityNodeInfo found,Completion cb) {
        if(appRoot(SPOTIFY_PKG)==null)return Result.WAIT;
        AccessibilityNodeInfo parent=found;
        for(int i=0;i<4 && parent!=null;i++,parent=parent.getParent()) {
            if(parent.isEnabled() && parent.isClickable() &&
               parent.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                return Result.DONE;
        }
        Rect rect=new Rect();found.getBoundsInScreen(rect);
        if(rect.width()<6 || rect.height()<6)return Result.WAIT;
        Path point=new Path();point.moveTo(rect.exactCenterX(),rect.exactCenterY());
        return gesture(SPOTIFY_PKG,point,90L,cb);
    }

    private Result spotifyFocusSearch(AccessibilityNodeInfo root,Completion cb) {
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            if(n.isEditable() && !n.isPassword()) {
                if(n.isFocused())return Result.DONE;
                return spotifyTapNode(n,cb);
            }
        }
        return spotifyTapExact(root,cb,
            "search songs","search spotify","пошук пісень",
            "що хочете послухати","що ви хочете послухати","пошук");
    }

    private Result spotifySetQuery(AccessibilityNodeInfo root,String title) {
        if(title==null || title.isEmpty() || title.length()>75)return Result.BLOCKED;
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            if(!n.isEditable() || n.isPassword())continue;
            Bundle args=new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,title);
            return n.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,args)
                ?Result.DONE:Result.WAIT;
        }
        return Result.WAIT;
    }

    private Result spotifySubmitQuery(AccessibilityNodeInfo root) {
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            if(!n.isEditable() || n.isPassword())continue;
            if(Build.VERSION.SDK_INT>=30) {
                int enter=AccessibilityNodeInfo.AccessibilityAction
                    .ACTION_IME_ENTER.getId();
                for(AccessibilityNodeInfo.AccessibilityAction action:n.getActionList()) {
                    if(action.getId()==enter)
                        return n.performAction(enter)?Result.DONE:Result.WAIT;
                }
            }
            // Some Spotify versions update results on each keystroke and
            // expose no IME Enter. Let the result search decide readiness.
            return Result.DONE;
        }
        return Result.WAIT;
    }

    private static boolean spotifyTitleMatches(String label,String wanted) {
        return label.equals(wanted) ||
               label.startsWith(wanted+" song") ||
               label.startsWith(wanted+" пісня") ||
               label.startsWith(wanted+" playlist") ||
               label.startsWith(wanted+" плейлист") ||
               label.startsWith(wanted+" плейліст");
    }

    private Result spotifySelectResult(AccessibilityNodeInfo root,String title,
                                        boolean playlist,boolean genreDiscovery,Completion cb) {
        if(title==null || title.trim().isEmpty())return Result.BLOCKED;
        String wanted=AppNameMatcher.normalize(title);
        AccessibilityNodeInfo selected=null;
        Rect bestBounds=null;
        int bestRank=Integer.MAX_VALUE;
        for(AccessibilityNodeInfo n:spotifyNodes(root)) {
            if(n.isEditable())continue; // Never tap the search input as a result.
            boolean exact=spotifyTitleMatches(spotifyLabel(n),wanted) ||
                spotifyTitleMatches(spotifyDescription(n),wanted);
            boolean genre=genreDiscovery &&
                (spotifyLabel(n).startsWith(wanted+" ") ||
                 spotifyDescription(n).startsWith(wanted+" "));
            if(!exact && !genre)continue;
            Rect b=new Rect();n.getBoundsInScreen(b);
            if(b.width()<6 || b.height()<6)continue;
            // Spotify sometimes returns both songs and similarly named
            // playlists. Prefer the requested result type if metadata is
            // accessible; otherwise use the first visible exact title.
            String metadata=spotifyLabel(n)+" "+spotifyDescription(n);
            AccessibilityNodeInfo parent=n.getParent();
            if(parent!=null)metadata+=" "+spotifyDescription(parent);
            boolean playlistType=metadata.contains("playlist") ||
                metadata.contains("плейлист") || metadata.contains("плейліст");
            boolean songType=metadata.contains("song") ||
                metadata.contains("пісня") || metadata.contains("песня") ||
                metadata.contains("track") || metadata.contains("трек");
            int rank=(playlist?playlistType:songType)?0:
                (playlist?songType:playlistType)?2:1;
            if(bestBounds==null || rank<bestRank ||
               (rank==bestRank && b.top<bestBounds.top)) {
                selected=n;bestBounds=b;bestRank=rank;
            }
        }
        // A clearly wrong category should not be selected just because
        // its title is identical. Wait for a matching category to load.
        return selected==null || bestRank==2?
            Result.WAIT:spotifyTapNode(selected,cb);
    }

    private Result spotifyTapPlay(AccessibilityNodeInfo root,Completion cb) {
        return spotifyTapExact(root,cb,"play","play playlist",
            "відтворити","відтворення","слухати","воспроизвести");
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

    /** Gesture completion is delivered asynchronously on the UI looper.
     * A client callback exception must not crash the accessibility service
     * and take the whole watch process back to the watch face. */
    static void safeGestureCompletion(Completion cb,boolean ok,String detail) {
        if(cb==null)return;
        try {
            cb.finished(ok,detail);
        } catch(RuntimeException error) {
            JarvisDiagnostics.error(JarvisDiagnostics.Event.ACCESSIBILITY_ERROR,error);
            android.util.Log.e("DIMON_WATCH_CONTROL",
                "Wear gesture completion callback failed",error);
        }
    }

    private Result gesture(String pkg,Path p,long ms,Completion cb) {
        if(appRoot(pkg)==null)return Result.WAIT;
        GestureDescription description=new GestureDescription.Builder()
            .addStroke(new GestureDescription.StrokeDescription(p,0,ms)).build();
        final boolean accepted;
        try{
            accepted=dispatchGesture(description,new GestureResultCallback(){
                @Override public void onCompleted(GestureDescription gesture) {
                    safeGestureCompletion(cb,true,"Жест виконано на годиннику.");
                }
                @Override public void onCancelled(GestureDescription gesture) {
                    safeGestureCompletion(cb,false,"Wear OS скасувала жест.");
                }
            },main);
        }catch(RuntimeException error){
            return Result.BLOCKED;
        }
        return accepted?Result.RUNNING:Result.BLOCKED;
    }
}
