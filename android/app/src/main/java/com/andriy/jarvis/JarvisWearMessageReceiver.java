package com.andriy.jarvis;

import android.content.pm.PackageManager;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Phone companion: receives only this application's authenticated Data Layer messages. */
public final class JarvisWearMessageReceiver extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        if (!JarvisWearProtocol.REQUEST.equals(event.getPath()) ||
            getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) return;
        byte[] data = event.getData();
        if (data == null || data.length == 0 || data.length > JarvisWearProtocol.MAX_BYTES) return;
        JSONObject request;
        try {
            request = new JSONObject(new String(data,StandardCharsets.UTF_8));
        } catch (Exception invalid) { return; }
        String id = request.optString("id","");
        if (!JarvisWearProtocol.validId(id)) return;
        String node = event.getSourceNodeId();
        JarvisWearPhoneSession.receive(this,node,request,(ok,description,token,ended) -> {
            try {
                JSONObject reply = new JSONObject();
                reply.put("id",id);
                reply.put("ok",ok);
                String answer = description == null ? "" : description;
                final boolean cut = answer.length() > 1500;
                if (cut) answer = answer.substring(0,1500) + "… (скорочено для годинника)";
                reply.put("detail",answer);
                reply.put("session",token == null ? "" : token);
                reply.put("session_ended",ended);
                byte[] encoded = reply.toString().getBytes(StandardCharsets.UTF_8);
                // UTF-8 Ukrainian text and JSON escaping may exceed the Wear
                // Data Layer budget. Shorten the REPLY, never the auth token.
                while (encoded.length > JarvisWearProtocol.MAX_BYTES && answer.length() > 80) {
                    answer = answer.substring(0,Math.max(0,answer.length()-120));
                    reply.put("detail",answer + "…");
                    encoded = reply.toString().getBytes(StandardCharsets.UTF_8);
                }
                if (encoded.length > JarvisWearProtocol.MAX_BYTES) return;
                Wearable.getMessageClient(getApplicationContext()).sendMessage(
                    node,JarvisWearProtocol.REPLY,encoded);
            } catch (Exception ignored) { }
        });
    }
}
