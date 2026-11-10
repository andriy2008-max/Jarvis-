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
                reply.put("detail",description == null ? "" :
                    description.length() > 600 ? description.substring(0,600) : description);
                reply.put("session",token == null ? "" : token);
                reply.put("session_ended",ended);
                Wearable.getMessageClient(getApplicationContext()).sendMessage(
                    node,JarvisWearProtocol.REPLY,
                    reply.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) { }
        });
    }
}
