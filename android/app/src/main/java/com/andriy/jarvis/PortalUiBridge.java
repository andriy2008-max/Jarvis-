package com.andriy.jarvis;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An optional adapter to Mobilerun Portal's documented LOCAL HTTP API.
 * Does not embed or modify AGPL Portal source; never starts its cloud agent.
 * No arbitrary click endpoint: app/package and semantic target are allowlisted.
 * A read-only diagnostic is available after the user explicitly enables Portal.
 */
final class PortalUiBridge {
    enum Target { BOLT_RIDES, BOLT_DROPOFF, SPOTIFY_SEARCH, SPOTIFY_LIBRARY }
    enum State { CLICKED, WAIT, UNAVAILABLE, UNSURE }
    interface Callback { void completed(State state,String note); }
    interface Inspection { void done(boolean ok,String note); }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{
        Thread t=new Thread(r,"jarvis-portal-local");
        t.setDaemon(true);
        return t;
    });
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final int MAX_BYTES=950_000;
    private PortalUiBridge(){}

    static boolean available(Context c) {
        if(c==null || c.getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_WATCH))return false;
        return PortalUiSettings.enabled(c);
    }

    static void inspect(Context context,Inspection cb) {
        if(cb==null)return;
        if(!available(context)) {
            cb.done(false,"Mobilerun Portal вимкнений або не має локального токена.");
            return;
        }
        Context c=context.getApplicationContext();
        IO.execute(()->{
            try {
                JSONObject state=state(c);
                String current=state.optJSONObject("phone_state")==null?"невідомий":
                    state.getJSONObject("phone_state").optString("packageName","невідомий");
                JSONArray tree=state.optJSONArray("a11y_tree");
                int count=tree==null?0:tree.length();
                MAIN.post(()->cb.done(true,"Mobilerun Portal доступний. "+
                    "Поточний пакет: "+current+". Елементів дерева: "+count+"."));
            } catch(Exception e) {
                MAIN.post(()->cb.done(false,
                    "Не отримав дерево Portal. Перевір запущений локальний HTTP "+
                    "сервер 8080, токен та Accessibility Mobilerun Portal."));
            }
        });
    }

    static void tap(Context context,Target target,Callback cb) {
        if(cb==null)return;
        if(!available(context)) {
            cb.completed(State.UNAVAILABLE,"Portal не налаштовано");
            return;
        }
        Context c=context.getApplicationContext();
        IO.execute(()->{
            State result;
            String note;
            boolean gestureRequested=false;
            try {
                JSONObject snapshot=state(c);
                JSONObject phone=snapshot.optJSONObject("phone_state");
                String expected=packageFor(target);
                if(phone==null || !expected.equals(phone.optString("packageName",""))) {
                    result=State.WAIT;
                    note="Потрібний застосунок не на передньому плані";
                } else {
                    JSONArray tree=snapshot.optJSONArray("a11y_tree");
                    ScreenPoint picked=choose(tree,target,c);
                    if(picked==null) {
                        result=State.WAIT;
                        note="Елемент ще не з'явився в дереві Portal";
                    } else if(picked.x<0 || picked.y<0) {
                        result=State.UNSURE;
                        note="Знайдено декілька неоднозначних кнопок";
                    } else {
                        // Read immediately before action; only the exact UI
                        // element in a known foreground package is actionable.
                        gestureRequested=true;
                        result=sendTap(c,picked.x,picked.y) ?
                            State.CLICKED:State.UNSURE;
                        note=result==State.CLICKED
                            ? "Portal прийняв натискання. Перевіряю наступний екран."
                            : "Portal не підтвердив натискання. Не повторював.";
                    }
                }
            } catch(java.io.IOException notConnected) {
                // A lost reply AFTER sending a tap is not proof that it
                // failed. Never retry the gesture or tap fixed coordinates.
                result=gestureRequested?State.UNSURE:State.UNAVAILABLE;
                note=gestureRequested
                    ?"Portal не підтвердив натискання — повтор небезпечний"
                    :"Локальний сервер Portal недоступний";
            } catch(Exception malformed) {
                result=State.UNSURE;
                note="Відповідь Portal неоднозначна; дія не повторюється";
            }
            final State finalState=result;
            final String finalNote=note;
            MAIN.post(()->cb.completed(finalState,finalNote));
        });
    }

    private static String packageFor(Target target) {
        return target==Target.BOLT_RIDES || target==Target.BOLT_DROPOFF ?
            "ee.mtakso.client":"com.spotify.music";
    }

    private static JSONObject state(Context c) throws Exception {
        JSONObject envelope=request(c,"GET","/state",null);
        Object content=envelope.opt("result");
        JSONObject parsed=content instanceof JSONObject?(JSONObject)content:
            content instanceof String?new JSONObject((String)content):null;
        if(parsed==null || parsed.optJSONObject("phone_state")==null)
            throw new IllegalStateException("Portal did not provide phone_state");
        return parsed;
    }

    private static boolean sendTap(Context c,int x,int y) throws Exception {
        String body="x="+x+"&y="+y;
        JSONObject out=request(c,"POST","/tap",body);
        return "success".equals(out.optString("status"));
    }

    private static JSONObject request(Context c,String verb,String endpoint,String body)
        throws Exception {
        String token=PortalUiSettings.token(c);
        if(!PortalUiSettings.valid(token))throw new IllegalStateException("No token");
        HttpURLConnection con=(HttpURLConnection)new URL(
            PortalUiSettings.HOST+endpoint).openConnection();
        con.setConnectTimeout(1800);
        con.setReadTimeout(2000);
        con.setRequestMethod(verb);
        con.setRequestProperty("Authorization","Bearer "+token);
        con.setUseCaches(false);
        if(body!=null) {
            con.setDoOutput(true);
            con.setRequestProperty("Content-Type",
                "application/x-www-form-urlencoded; charset=UTF-8");
            byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
            con.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream out=con.getOutputStream()){out.write(bytes);}
        }
        try {
            if(con.getResponseCode()!=200)throw new IllegalStateException(
                "Portal HTTP response not successful");
            ByteArrayOutputStream buffer=new ByteArrayOutputStream();
            try(InputStream input=con.getInputStream()) {
                byte[] chunk=new byte[8192];
                int n;
                while((n=input.read(chunk))!=-1) {
                    if(buffer.size()+n>MAX_BYTES)
                        throw new IllegalStateException("Portal state too large");
                    buffer.write(chunk,0,n);
                }
            }
            JSONObject json=new JSONObject(new String(buffer.toByteArray(),
                StandardCharsets.UTF_8));
            if(!"success".equals(json.optString("status")))
                throw new IllegalStateException("Portal operation returned error");
            return json;
        } finally {con.disconnect();}
    }

    private static final class ScreenPoint {
        final int x,y;
        ScreenPoint(int x,int y){this.x=x;this.y=y;}
    }

    private static ScreenPoint choose(JSONArray tree,Target target,Context c) {
        if(tree==null || tree.length()==0)return null;
        DisplayMetrics screen=c.getResources().getDisplayMetrics();
        final int width=screen.widthPixels;
        final int height=screen.heightPixels;
        if(width<360 || height<640 || height<width*1.25f)return null;
        ArrayDeque<JSONObject> nodes=new ArrayDeque<>();
        for(int i=0;i<tree.length();i++){
            JSONObject node=tree.optJSONObject(i);
            if(node!=null)nodes.add(node);
        }
        List<ScreenPoint> choices=new ArrayList<>();
        int scanned=0;
        while(!nodes.isEmpty() && ++scanned<=900){
            JSONObject n=nodes.removeFirst();
            String value=n.optString("text","");
            if(matches(target,value)) {
                ScreenPoint point=parseBounds(n.optString("bounds",""),width,height);
                if(point!=null && safeRegion(target,point,width,height)) {
                    boolean same=false;
                    for(ScreenPoint p:choices)
                        if(Math.abs(p.x-point.x)<34 && Math.abs(p.y-point.y)<34){
                            same=true;break;
                        }
                    if(!same)choices.add(point);
                }
            }
            JSONArray children=n.optJSONArray("children");
            if(children!=null)for(int i=0;i<children.length();i++){
                JSONObject child=children.optJSONObject(i);
                if(child!=null)nodes.add(child);
            }
        }
        if(scanned>900 || choices.isEmpty())return null;
        return choices.size()==1 ? choices.get(0):new ScreenPoint(-1,-1);
    }

    private static boolean matches(Target target,String value) {
        String s=value==null?"":value.toLowerCase(
            Locale.forLanguageTag("uk-UA")).trim().replaceAll("\\s+"," ");
        if(s.length()<3 || s.length()>100)return false;
        switch(target) {
            case BOLT_RIDES:
                return BoltUiLabels.letsGo(s) &&
                    !s.contains("оплат") && !s.contains("замовити");
            case BOLT_DROPOFF:
                return BoltUiLabels.destination(s) && !BoltUiLabels.pickup(s);
            case SPOTIFY_SEARCH:
                return s.equals("пошук") || s.equals("search");
            case SPOTIFY_LIBRARY:
                return s.equals("моя бібліотека") || s.equals("your library");
            default:return false;
        }
    }

    private static boolean safeRegion(Target target,ScreenPoint p,int w,int h) {
        if(target==Target.BOLT_RIDES)
            return BoltUiLabels.letsGoRegion(p.x,p.y,0,0,w,h);
        if(target==Target.BOLT_DROPOFF)
            return BoltUiLabels.dropoffRegion(p.x,p.y,w,h);
        return p.x>=0 && p.x<w && p.y>h*.68 && p.y<h;
    }

    private static ScreenPoint parseBounds(String raw,int width,int height) {
        String[] b=raw.split(",");
        if(b.length!=4)return null;
        try {
            int l=Integer.parseInt(b[0].trim()),t=Integer.parseInt(b[1].trim());
            int r=Integer.parseInt(b[2].trim()),bottom=Integer.parseInt(b[3].trim());
            if(l<0 || t<0 || r>width || bottom>height ||
               r-l<20 || bottom-t<18)return null;
            return new ScreenPoint((l+r)/2,(t+bottom)/2);
        }catch(NumberFormatException bad){return null;}
    }
}
