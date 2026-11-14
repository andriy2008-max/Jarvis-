package com.andriy.jarvis;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.MessageClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.Wearable;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Foreground watch-side connection to companion Android phone.
 * A successful send is NOT proof of execution. Only a matching phone ACK
 * activates the 8-bit phone state on the watch.
 */
public final class JarvisWearPhoneLink {
    public interface Callback { void finished(boolean ok, String message); }
    public interface DocumentCallback { void finished(boolean ok, Document page); }

    /** Paginated answer; the document ID remains on watch only while readable. */
    public static final class Document {
        public final String text,documentId;
        public final int pageIndex,pageCount;
        Document(String text,String id,int index,int count) {
            this.text=text;documentId=id;pageIndex=index;pageCount=count;
        }
        public boolean hasMore(){return !documentId.isEmpty() && pageIndex+1<pageCount;}
    }

    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MessageClient messageClient;
    // Wear message callbacks can arrive on binder threads. Serialize them
    // with sends, timeouts and node discovery on the Android main looper.
    private final MessageClient.OnMessageReceivedListener replies =
        event -> main.post(() -> onReply(event));
    private Task<Void> listenerRegistration;
    private boolean listenerReady;
    private boolean requesting;
    private String requestingType;
    // Async node lookups may finish after a higher-priority voice command.
    private long requestEpoch;
    private boolean closed;
    private String activeSession = "";
    private String pendingId;
    private String pendingNode;
    private String pendingType;
    private Callback pendingCallback;
    private Document pendingDocument;
    private Runnable timeout;
    private long feedback;
    private final Runnable sync = new Runnable() {
        @Override public void run() {
            if (closed) return;
            if (listenerReady && !busy()) send(JarvisWearProtocol.SYNC, "", (ok, detail) -> {});
            main.postDelayed(this, 10000L);
        }
    };

    public JarvisWearPhoneLink(Context context) {
        this.context = context.getApplicationContext();
        this.messageClient = Wearable.getMessageClient(this.context);
        listenerRegistration = messageClient.addListener(replies,
            Uri.parse("wear://*" + JarvisWearProtocol.REPLY), MessageClient.FILTER_LITERAL);
        listenerRegistration.addOnSuccessListener(v -> {
            listenerReady = true;
            if (!closed) main.post(sync);
        });
    }

    public boolean busy() { return requesting || pendingId != null; }
    public boolean active() { return !activeSession.isEmpty() && JarvisPhoneMode.isActive(); }

    public boolean spotifyRunning() {
        return JarvisWearProtocol.SPOTIFY.equals(pendingType);
    }

    public boolean taxiRunning() {
        return JarvisWearProtocol.TAXI.equals(pendingType);
    }

    /** Local taxi command: phone controls Bolt without invoking conversational AI. */
    public void taxi(String voice, Callback callback) {
        if (!JarvisWearProtocol.validCommand(voice) ||
            JarvisTaxiCommands.destination(voice) == null) {
            callback.finished(false, "Невідома команда таксі.");
            return;
        }
        send(JarvisWearProtocol.TAXI, voice, callback);
    }

    /** Voice and external integrations are handled on the phone. */
    public void assistant(String voice, Callback callback) {
        if (!JarvisWearProtocol.validCommand(voice)) {
            callback.finished(false,"Команда занадто довга або порожня.");
            return;
        }
        String normalized = CommandPatterns.normalizeControlVerb(voice);
        if (JarvisWearProtocol.ASSISTANT.equals(pendingType) &&
            (normalized.equals("скасуй макрос") || normalized.equals("зупини макрос") ||
             normalized.equals("стоп макрос"))) {
            Callback previous = pendingCallback;
            clearPending();
            if (previous != null) previous.finished(false,"Очікую зупинку макросу на телефоні.");
        }
        send(JarvisWearProtocol.ASSISTANT,voice,callback);
    }

    /** Same phone assistant request, with all pages available in a watch reader. */
    public void assistantDocument(String voice,DocumentCallback cb) {
        if(cb==null)return;
        pendingDocument=null;
        assistant(voice,(ok,detail)->{
            Document page=pendingDocument;
            cb.finished(ok,page!=null ? page : new Document(detail,"",0,1));
        });
    }

    /** Read one cached result page; no network AI request or screen capture. */
    public void readPage(String documentId,int index,DocumentCallback cb) {
        if(cb==null)return;
        if(!JarvisWearProtocol.validId(documentId) || index<0 || index>200) {
            cb.finished(false,new Document("Невірна сторінка.","",0,1));
            return;
        }
        pendingDocument=null;
        send(JarvisWearProtocol.READ_PAGE,documentId+":"+index,(ok,detail)->{
            Document page=pendingDocument;
            cb.finished(ok,page!=null?page:new Document(detail,"",0,1));
        });
    }

    /** PIN entered for this single command; never persisted or sent to AI. */
    public void unlockPin(String challenge,String pin,Callback callback) {
        if (challenge == null || !challenge.matches("[0-9a-f]{32}") ||
            pin == null || !pin.matches("[0-9]{4,12}")) {
            callback.finished(false,"Введи числовий PIN Pixel (4–12 цифр).");
            return;
        }
        android.app.KeyguardManager watchLock =
            context.getSystemService(android.app.KeyguardManager.class);
        if (watchLock == null || !watchLock.isDeviceSecure() || watchLock.isDeviceLocked()) {
            callback.finished(false,"Спочатку ввімкни захист PIN на годиннику й розблокуй Watch7.");
            return;
        }
        send(JarvisWearProtocol.UNLOCK,challenge + ":" + pin,callback);
    }

    public void spotify(String voice, Callback callback) {
        if (!JarvisWearProtocol.validCommand(voice) ||
            SpotifyMusicCommands.parse(voice) == null) {
            callback.finished(false,"Невідома команда Spotify.");
            return;
        }
        send(JarvisWearProtocol.SPOTIFY,voice,callback);
    }

    public void cancelSpotify(Callback callback) {
        if (!spotifyRunning() || pendingNode == null) {
            callback.finished(false,"Немає активного запиту Spotify.");
            return;
        }
        // Retire the old request; cancellation has its own id and phone ACK.
        Callback previous = pendingCallback;
        clearPending();
        if (previous != null) previous.finished(false,"Очікую скасування Spotify на телефоні.");
        send(JarvisWearProtocol.CANCEL_SPOTIFY,"",callback);
    }

    public void enter(Callback cb) { send(JarvisWearProtocol.ENTER, "", cb); }
    public void command(String voice, Callback cb) {
        if (!active()) {
            cb.finished(false, "Режим телефона не підключено. Скажи «Телефон».");
            return;
        }
        if (!JarvisWearProtocol.validCommand(voice)) {
            cb.finished(false, "Команда занадто довга або порожня.");
            return;
        }
        send(JarvisWearProtocol.COMMAND, voice, cb);
    }

    public void exit(Callback cb) {
        // The watch icon must immediately return to ordinary Jarvis even if
        // connection drops; the phone expires its own idle control session.
        if (!active()) {
            JarvisPhoneMode.setActive(false);
            cb.finished(true, "Звичайний Джарвіс.");
            return;
        }
        send(JarvisWearProtocol.EXIT, "", (ok, detail) -> {
            activeSession = "";
            JarvisPhoneMode.setActive(false);
            cb.finished(ok, ok ? "Звичайний Джарвіс." :
                "Годинник вийшов із режиму «Телефон». Телефон недоступний; " +
                "керування на ньому автоматично завершиться після паузи.");
        });
    }

    private void send(String type, String text, Callback cb) {
        if (closed) { cb.finished(false, "Зв'язок із телефоном вимкнено."); return; }
        // User voice takes priority over the 10-second background heartbeat.
        // It can preempt both an in-flight node lookup and an ACK wait.
        if (!JarvisWearProtocol.SYNC.equals(type)) {
            if (JarvisWearProtocol.SYNC.equals(requestingType)) {
                requestEpoch++;
                requesting = false;
                requestingType = null;
            }
            if (JarvisWearProtocol.SYNC.equals(pendingType)) clearPending();
        }
        if (busy()) { cb.finished(false, "Чекаю відповіді телефона на попередню команду."); return; }
        if (!JarvisWearProtocol.validAction(type)) {
            cb.finished(false, "Непідтримувана команда телефона."); return;
        }
        if (!listenerReady) {
            listenerRegistration.addOnSuccessListener(v -> {
                listenerReady = true;
                if (!closed) send(type,text,cb);
            });
            listenerRegistration.addOnFailureListener(err ->
                cb.finished(false, "Немає Wear OS Data Layer. Перевір сервіси Google."));
            return;
        }

        requesting = true;
        requestingType = type;
        final long epoch = ++requestEpoch;
        Wearable.getNodeClient(context).getConnectedNodes()
            .addOnSuccessListener(nodes -> {
                // A stale SYNC lookup must not overwrite a pending real command.
                if (closed || epoch != requestEpoch) return;
                Node target = selectPhone(nodes);
                if (target == null) {
                    requesting = false;
                    requestingType = null;
                    cb.finished(false, "Не знайшов підключений Android-телефон. " +
                        "На годиннику й телефоні має бути Джарвіс з однаковим підписом.");
                    return;
                }
                try {
                    JSONObject request = new JSONObject();
                    pendingId = UUID.randomUUID().toString();
                    final String requestId = pendingId;
                    requesting = false;
                    requestingType = null;
                    request.put("id",requestId);
                    request.put("action",type);
                    request.put("text",text);
                    request.put("session",activeSession);
                    byte[] data = request.toString().getBytes(StandardCharsets.UTF_8);
                    pendingNode = target.getId();
                    pendingType = type;
                    pendingCallback = cb;
                    if (JarvisWearProtocol.COMMAND.equals(type) ||
                        JarvisWearProtocol.SPOTIFY.equals(type) ||
                        JarvisWearProtocol.TAXI.equals(type) ||
                        JarvisWearProtocol.ASSISTANT.equals(type))
                        feedback = JarvisControlFeedback.begin();
                    if (data.length > JarvisWearProtocol.MAX_BYTES)
                        throw new IllegalArgumentException("Payload too large");
                    timeout = () -> failPending("Телефон не підтвердив виконання команди.");
                    main.postDelayed(timeout,JarvisWearProtocol.timeoutMs(type));
                    if (JarvisWearProtocol.SPOTIFY.equals(type) ||
                        JarvisWearProtocol.TAXI.equals(type))
                        JarvisPhoneMode.setActive(true);
                    messageClient.sendMessage(target.getId(),JarvisWearProtocol.REQUEST,data)
                        .addOnFailureListener(err -> {
                            // The old SYNC send may fail *after* replacement;
                            // never fail the newer user's request by mistake.
                            if (requestId.equals(pendingId))
                                failPending("Не вдалося передати команду на телефон. " +
                                    "Перевір Bluetooth/Wi-Fi.");
                        });
                } catch (Exception error) {
                    requesting = false;
                    requestingType = null;
                    if (pendingId != null)
                        failPending("Не вдалося підготувати команду для телефона.");
                    else cb.finished(false,"Не вдалося підготувати команду для телефона.");
                }
            })
            .addOnFailureListener(err -> {
                if (closed || epoch != requestEpoch) return;
                requesting = false;
                requestingType = null;
                cb.finished(false,"Немає зв'язку з телефоном через Wear OS.");
            });
    }

    private static Node selectPhone(List<Node> nodes) {
        if (nodes == null || nodes.isEmpty()) return null;
        if (nodes.size() == 1) return nodes.get(0);
        Node onlyNearby = null;
        for (Node node : nodes) {
            if (node.isNearby()) {
                if (onlyNearby != null) return null; // never pick a random phone
                onlyNearby = node;
            }
        }
        return onlyNearby;
    }

    private void onReply(MessageEvent event) {
        if (!JarvisWearProtocol.REPLY.equals(event.getPath()) ||
            pendingId == null || pendingNode == null ||
            !pendingNode.equals(event.getSourceNodeId())) return;
        if (event.getData() == null || event.getData().length > JarvisWearProtocol.MAX_BYTES) return;
        try {
            JSONObject data = new JSONObject(new String(event.getData(),StandardCharsets.UTF_8));
            if (!pendingId.equals(data.optString("id"))) return;
            final String type = pendingType;
            final Callback cb = pendingCallback;
            final boolean ok = data.optBoolean("ok",false);
            final String detail = data.optString("detail","Телефон не повернув результат.");
            final String documentId=data.optString("document_id","");
            if(JarvisWearProtocol.validId(documentId)) {
                pendingDocument=new Document(detail,documentId,
                    data.optInt("page_index",0),data.optInt("page_count",1));
            } else pendingDocument=null;
            if (JarvisWearProtocol.ENTER.equals(type) && ok) {
                String token = data.optString("session","");
                if (!JarvisWearProtocol.validId(token)) {
                    failPending("Телефон повернув некоректне підтвердження.");
                    return;
                }
                activeSession = token;
                JarvisPhoneMode.setActive(true);
            } else if (JarvisWearProtocol.SYNC.equals(type) && ok) {
                String token = data.optString("session", "");
                activeSession = JarvisWearProtocol.validId(token) ? token : "";
                JarvisPhoneMode.setActive(!activeSession.isEmpty());
            } else if (JarvisWearProtocol.COMMAND.equals(type) &&
                       data.optBoolean("session_ended",false)) {
                activeSession = "";
                JarvisPhoneMode.setActive(false);
            } else if (JarvisWearProtocol.SPOTIFY.equals(type) ||
                       JarvisWearProtocol.CANCEL_SPOTIFY.equals(type) ||
                       JarvisWearProtocol.TAXI.equals(type)) {
                // Temporary task complete: restore the usual 8-bit face.
                if (activeSession.isEmpty()) JarvisPhoneMode.setActive(false);
            }
            JarvisControlFeedback.finish(feedback,ok,SystemClock.uptimeMillis());
            feedback = 0L;
            clearPending();
            if (cb != null) cb.finished(ok,detail);
        } catch (Exception ignored) { }
    }

    private void failPending(String reason) {
        Callback cb = pendingCallback;
        boolean exiting = JarvisWearProtocol.EXIT.equals(pendingType);
        boolean spotify = JarvisWearProtocol.SPOTIFY.equals(pendingType) ||
            JarvisWearProtocol.CANCEL_SPOTIFY.equals(pendingType);
        boolean taxi = JarvisWearProtocol.TAXI.equals(pendingType);
        boolean syncing = JarvisWearProtocol.SYNC.equals(pendingType);
        clearPending();
        if (syncing) { activeSession = ""; JarvisPhoneMode.setActive(false); }
        if ((spotify || taxi) && activeSession.isEmpty()) JarvisPhoneMode.setActive(false);
        if (exiting) {
            activeSession = "";
            JarvisPhoneMode.setActive(false);
        }
        if (cb != null) cb.finished(false,reason);
    }

    private void clearPending() {
        JarvisControlFeedback.finish(feedback,false,SystemClock.uptimeMillis());
        feedback = 0L;
        if (timeout != null) main.removeCallbacks(timeout);
        timeout = null;
        pendingId = null;
        pendingNode = null;
        pendingType = null;
        pendingCallback = null;
        // The current document is consumed by the completion callback before
        // the following request begins; callers keep their own copy.
    }

    public void close() {
        closed = true;
        requestEpoch++; // Invalidate in-flight node lookups.
        requesting = false;
        requestingType = null;
        clearPending();
        main.removeCallbacks(sync);
        messageClient.removeListener(replies);
        activeSession = "";
        JarvisPhoneMode.setActive(false);
    }
}
