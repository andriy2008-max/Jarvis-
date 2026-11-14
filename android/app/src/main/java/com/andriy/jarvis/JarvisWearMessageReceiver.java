package com.andriy.jarvis;

import android.content.pm.PackageManager;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Phone companion: receives authenticated Data Layer messages from the watch. */
public final class JarvisWearMessageReceiver extends WearableListenerService {
    @Override public void onMessageReceived(MessageEvent event) {
        if(!JarvisWearProtocol.REQUEST.equals(event.getPath()) ||
           getPackageManager().hasSystemFeature(PackageManager.FEATURE_WATCH)) return;
        byte[] data=event.getData();
        if(data==null || data.length==0 || data.length>JarvisWearProtocol.MAX_BYTES)return;
        JSONObject request;
        try { request=new JSONObject(new String(data,StandardCharsets.UTF_8)); }
        catch(Exception invalid) { return; }
        String id=request.optString("id","");
        if(!JarvisWearProtocol.validId(id))return;
        String node=event.getSourceNodeId();
        if(node==null || node.isEmpty())return;
        String action=request.optString("action","");

        if(JarvisWearProtocol.GENERATE_IMAGE.equals(action)) {
            String command=request.optString("text","");
            if(!JarvisWearProtocol.validCommand(command) ||
               JarvisWearImageCommands.prompt(command)==null) {
                sendReply(node,id,false,"Не розпізнав команду створення зображення.",
                    "",false,null);
                return;
            }
            JarvisWearImageBridge.generate(getApplicationContext(),node,command,
                (ok,detail,imageId)->sendImageReply(node,id,ok,detail,imageId));
            return;
        }
        if(JarvisWearProtocol.SAVE_IMAGE.equals(action)) {
            String imageId=request.optString("text","");
            if(!JarvisWearProtocol.validId(imageId)) {
                sendReply(node,id,false,"Неправильний ідентифікатор зображення.",
                    "",false,null);
                return;
            }
            JarvisWearImageBridge.save(getApplicationContext(),node,imageId,
                (ok,detail,storedId)->sendReply(node,id,ok,detail,"",false,null));
            return;
        }
        if(JarvisWearProtocol.READ_PAGE.equals(action)) {
            String locator=request.optString("text","");
            int colon=locator.lastIndexOf(':');
            if(colon<=0 || colon>=locator.length()-1) {
                sendReply(node,id,false,"Некоректна сторінка.","",false,null);
                return;
            }
            String documentId=locator.substring(0,colon);
            int index;
            try {index=Integer.parseInt(locator.substring(colon+1));}
            catch(NumberFormatException invalid) {
                sendReply(node,id,false,"Некоректний номер сторінки.","",false,null);
                return;
            }
            // Pages are scoped to the same paired-watch node that received
            // the first reply. Re-reading does not reissue the AI API request.
            JarvisWearReplyPages.Page page=JarvisWearReplyPages.read(node,documentId,index);
            sendReply(node,id,page!=null,page==null
                ? "Відповідь більше недоступна. Повтори голосовий запит."
                : page.text,"",false,page);
            return;
        }
        JarvisWearPhoneSession.receive(this,node,request,(ok,description,token,ended)->{
            JarvisWearReplyPages.Page page =
                ok && JarvisWearProtocol.ASSISTANT.equals(action)
                    ? JarvisWearReplyPages.save(node,id,description) : null;
            sendReply(node,id,ok,description,token,ended,page);
        });
    }

    private void sendImageReply(String node,String id,boolean ok,String detail,
                                String imageId) {
        try {
            JSONObject response=new JSONObject();
            response.put("id",id);
            response.put("ok",ok);
            response.put("detail",detail==null?"":detail);
            response.put("session","");
            response.put("session_ended",false);
            if(ok && JarvisWearProtocol.validId(imageId))
                response.put("image_id",imageId);
            byte[] bytes=response.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>JarvisWearProtocol.MAX_BYTES)return;
            Wearable.getMessageClient(getApplicationContext()).sendMessage(
                node,JarvisWearProtocol.REPLY,bytes);
        }catch(Exception ignored){}
    }

    private void sendReply(String node,String id,boolean ok,String detail,
                           String token,boolean ended,JarvisWearReplyPages.Page page) {
        try {
            JSONObject reply=new JSONObject();
            reply.put("id",id);
            reply.put("ok",ok);
            reply.put("session",token==null?"":token);
            reply.put("session_ended",ended);
            String answer=page==null ? (detail==null?"":detail) : page.text;
            if(page!=null) {
                reply.put("document_id",page.documentId);
                reply.put("page_index",page.index);
                reply.put("page_count",page.count);
            } else if(answer.length()>1500) answer=answer.substring(0,1500)+"…";
            reply.put("detail",answer);
            byte[] encoded=reply.toString().getBytes(StandardCharsets.UTF_8);
            if(encoded.length>JarvisWearProtocol.MAX_BYTES) {
                // Never silently drop a page due to an oversized payload.
                // The page may be requested again after a connection error.
                if(page!=null) {
                    reply.remove("document_id");
                    reply.remove("page_index");
                    reply.remove("page_count");
                    reply.put("ok",false);
                    answer="Сторінка завелика для передачі. Повтори запит.";
                    reply.put("detail",answer);
                } else {
                    while(encoded.length>JarvisWearProtocol.MAX_BYTES && answer.length()>80) {
                        answer=answer.substring(0,answer.length()-120);
                        reply.put("detail",answer+"…");
                        encoded=reply.toString().getBytes(StandardCharsets.UTF_8);
                    }
                }
                encoded=reply.toString().getBytes(StandardCharsets.UTF_8);
            }
            if(encoded.length>JarvisWearProtocol.MAX_BYTES)return;
            Wearable.getMessageClient(getApplicationContext())
                .sendMessage(node,JarvisWearProtocol.REPLY,encoded);
        }catch(Exception ignored) { }
    }
}
