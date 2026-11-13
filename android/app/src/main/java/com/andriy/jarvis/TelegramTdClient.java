package com.andriy.jarvis;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import org.drinkless.tdlib.Client;
import org.drinkless.tdlib.TdApi;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Personal Telegram client via TDLib (MTProto), NOT Bot API and NOT
 * Android notification actions.
 *
 * Private on-device TDLib session, phone/code/2FA login, explicit voice
 * draft + independent send confirmation. Never auto-reply to messages.
 * This session starts only when the user opens setup or requests Telegram.
 */
public final class TelegramTdClient {
    public interface Callback { void done(boolean ok,String message); }
    public interface StatusListener { void stateChanged(String detail); }

    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final long DRAFT_MAX_AGE_MS=90000L;
    private static TelegramTdClient instance;

    private final Context context;
    private Client client;
    private TdApi.AuthorizationState authorizationState;
    private StatusListener statusListener;
    private boolean loading;
    private boolean busy;
    private boolean sendDispatched;
    private int revision;
    private Draft draft;
    /** Only CONFIRM is authorized to send. Correcting text/contact revokes it. */
    private enum Dialogue { NONE, CONFIRM, WAIT_MESSAGE, WAIT_CONTACT }
    private Dialogue dialogue=Dialogue.NONE;
    private long pendingSince;
    private long correctedChatId;
    private String correctedRecipient;
    private String correctedText;
    private String startupError="";

    private static final class Draft {
        final long chatId;
        final String recipient;
        final String text;
        final long createdAt=SystemClock.elapsedRealtime();
        Draft(TdApi.Chat c,String text){
            this(c.id,c.title,text);
        }
        Draft(long chatId,String recipient,String text) {
            this.chatId=chatId;this.recipient=recipient;this.text=text;
        }
    }
    private void clearCorrection() {
        correctedChatId=0;
        correctedRecipient=null;
        correctedText=null;
        pendingSince=0;
    }
    private void resetDialogue() {
        dialogue=Dialogue.NONE;
        draft=null;
        clearCorrection();
    }

    /** Intercept short confirmations ONLY for an active user-reviewed draft.
     * Returns false for unrelated speech when only confirmation is pending.
     * In explicit correction modes, the following utterance is the replacement
     * text or recipient, not a command to some other app. */
    public boolean processFollowup(String raw,Callback cb) {
        if(dialogue==Dialogue.NONE) return false;
        TelegramReplyCommands.DialogAction action=TelegramReplyCommands.dialogAction(raw);
        TelegramReplyCommands.Request explicit=TelegramReplyCommands.parse(raw);
        if(explicit!=null && explicit.kind==TelegramReplyCommands.Kind.CANCEL) {
            process(explicit,cb); return true;
        }
        if(action==TelegramReplyCommands.DialogAction.CANCEL) {
            cancelDraft(cb); return true;
        }
        // Full Telegram commands remain usable to start an entirely new draft.
        if(explicit!=null && (explicit.kind==TelegramReplyCommands.Kind.DRAFT ||
            explicit.kind==TelegramReplyCommands.Kind.DRAFT_AUTO)) {
            resetDialogue();
            process(explicit,cb);
            return true;
        }
        long now=SystemClock.elapsedRealtime();
        long created=dialogue==Dialogue.CONFIRM && draft!=null
            ? draft.createdAt : pendingSince;
        if(created==0 || now-created>DRAFT_MAX_AGE_MS) {
            resetDialogue();
            if(action!=TelegramReplyCommands.DialogAction.NONE ||
               explicit!=null || dialogue!=Dialogue.CONFIRM) {
                cb.done(false,"Час підтвердження Telegram минув. Продиктуй нове повідомлення.");
                return true;
            }
            return false;
        }
        if(busy) {
            cb.done(false,"Telegram ще обробляє попередній вибір контакту.");return true;
        }
        switch(dialogue) {
            case CONFIRM:
                if(action==TelegramReplyCommands.DialogAction.CONFIRM) {
                    sendDraft(cb);return true;
                }
                if(action==TelegramReplyCommands.DialogAction.REWRITE_MESSAGE) {
                    if(draft==null) { resetDialogue();return false; }
                    correctedChatId=draft.chatId;
                    correctedRecipient=draft.recipient;
                    correctedText=null; // old text is discarded
                    draft=null;
                    dialogue=Dialogue.WAIT_MESSAGE;
                    pendingSince=now;
                    cb.done(true,"Попередній текст прибрано. Назви нове повідомлення для «"+
                        correctedRecipient+"». Слухаю.");
                    return true;
                }
                if(action==TelegramReplyCommands.DialogAction.CHANGE_CONTACT) {
                    if(draft==null) {resetDialogue();return false;}
                    correctedText=draft.text;
                    correctedRecipient=null;
                    correctedChatId=0;
                    draft=null;
                    dialogue=Dialogue.WAIT_CONTACT;
                    pendingSince=now;
                    cb.done(true,"Попередній контакт прибрано. Назви правильне ім'я контакту "+
                        "як у Telegram. Текст повідомлення збережено. Слухаю.");
                    return true;
                }
                return false;
            case WAIT_MESSAGE:
                if(action==TelegramReplyCommands.DialogAction.CHANGE_CONTACT) {
                    cb.done(false,"Спочатку продиктуй новий текст для «"+
                        correctedRecipient+"»."); return true;
                }
                if(action==TelegramReplyCommands.DialogAction.CONFIRM ||
                   action==TelegramReplyCommands.DialogAction.REWRITE_MESSAGE) {
                    cb.done(false,"Надсилання ще не підтверджено. Назви новий текст повідомлення.");
                    return true;
                }
                String message=TelegramReplyCommands.dictatedCorrection(raw);
                if(message==null) {
                    cb.done(false,"Не почув текст. Продиктуй повідомлення ще раз.");return true;
                }
                if(correctedChatId<=0 || correctedRecipient==null) {
                    resetDialogue();
                    cb.done(false,"Контакт для виправлення втрачено. Почни повідомлення заново.");
                    return true;
                }
                draft=new Draft(correctedChatId,correctedRecipient,message);
                dialogue=Dialogue.CONFIRM;
                clearCorrection();
                cb.done(true,confirmationPrompt());
                return true;
            case WAIT_CONTACT:
                if(action==TelegramReplyCommands.DialogAction.CONFIRM ||
                   action==TelegramReplyCommands.DialogAction.CHANGE_CONTACT ||
                   action==TelegramReplyCommands.DialogAction.REWRITE_MESSAGE) {
                    cb.done(false,"Назви правильний контакт Telegram. Повідомлення ще не надсилалося.");
                    return true;
                }
                String recipient=TelegramReplyCommands.dictatedCorrection(raw);
                TelegramReplyCommands.Request replacement=
                    TelegramReplyCommands.correctedContact(recipient,correctedText);
                if(replacement==null) {
                    cb.done(false,"Назви ім'я контакту, як воно записане в Telegram.");return true;
                }
                createDraft(replacement,cb);
                return true;
            default: return false;
        }
    }

    /** True only while a correction requires an additional dictated turn. */
    public boolean needsFollowupDictation() {
        return dialogue==Dialogue.WAIT_MESSAGE || dialogue==Dialogue.WAIT_CONTACT;
    }

    private String confirmationPrompt() {
        if(draft==null)return "Немає повідомлення для підтвердження.";
        return "Чернетка для «"+draft.recipient+"»: «"+draft.text+"». "+
            "Скажи «Ок» або «Окей», щоб надіслати. "+
            "«Не так» чи «Заново» — змінити текст; "+
            "«Не той контакт» — змінити адресата; «Не надсилай» — скасувати.";
    }

    private void cancelDraft(Callback cb) {
        if(sendDispatched) {
            cb.done(false,"Повідомлення вже передано Telegram. "+
                "Не можу скасувати його надсилання. Перевір чат.");
            return;
        }
        revision++;
        resetDialogue();
        busy=false;
        cb.done(true,"Скасував чернетку Telegram. Нічого не надсилав.");
    }

    private static final class ResolvedChat {
        final TdApi.Chat chat;
        final String text;
        ResolvedChat(TdApi.Chat chat,String text){this.chat=chat;this.text=text;}
    }
    private TelegramTdClient(Context c){context=c.getApplicationContext();}
    public static synchronized TelegramTdClient get(Context c){
        if(instance==null)instance=new TelegramTdClient(c);
        return instance;
    }
    public void setStatusListener(StatusListener listener){
        statusListener=listener;
        if(listener!=null)listener.stateChanged(status());
    }
    private void changed(){
        if(statusListener!=null)statusListener.stateChanged(status());
    }
    public boolean isAuthorized(){
        return authorizationState instanceof TdApi.AuthorizationStateReady;
    }
    public String status(){
        if(!startupError.isEmpty())return startupError;
        if(TelegramApiSecrets.load(context)==null)
            return "Не налаштовано Telegram API. Введи api_id і api_hash з my.telegram.org.";
        if(loading || authorizationState==null)
            return "Telegram TDLib: підключення до API...";
        if(authorizationState instanceof TdApi.AuthorizationStateReady)
            return "Telegram API підключено до твого особистого акаунта ✅";
        if(authorizationState instanceof TdApi.AuthorizationStateWaitPhoneNumber)
            return "Введи свій номер Telegram у міжнародному форматі (+380...).";
        if(authorizationState instanceof TdApi.AuthorizationStateWaitCode)
            return "Введи код входу з Telegram (іноді SMS).";
        if(authorizationState instanceof TdApi.AuthorizationStateWaitPassword)
            return "Введи пароль двоетапної перевірки Telegram.";
        if(authorizationState instanceof TdApi.AuthorizationStateWaitEmailAddress ||
            authorizationState instanceof TdApi.AuthorizationStateWaitEmailCode)
            return "Telegram потребує підтвердження e-mail — потрібен додатковий етап авторизації.";
        if(authorizationState instanceof TdApi.AuthorizationStateWaitRegistration)
            return "Цей номер потребує реєстрації Telegram. Спершу зареєструйся в офіційній програмі.";
        return "TDLib очікує завершення авторизації Telegram.";
    }
    public void start(Callback callback){
        if(client!=null){
            if(callback!=null)callback.done(true,status());
            return;
        }
        TelegramApiSecrets.Credentials creds=TelegramApiSecrets.load(context);
        if(creds==null){
            if(callback!=null)callback.done(false,status());
            changed();
            return;
        }
        try{
            loading=true;
            startupError="";
            // The prebuilt TDLib AAR contains matching Java API + native JNI.
            client=Client.create(object -> MAIN.post(() -> onUpdate(object)),
                error -> MAIN.post(() -> {
                    startupError="Помилка обробки Telegram TDLib.";
                    changed();
                }),
                error -> MAIN.post(() -> {
                    startupError="Помилка виконання Telegram API.";
                    changed();
                }));
            if(callback!=null)callback.done(true,"Запускаю Telegram API...");
        } catch(Throwable e){
            client=null;
            loading=false;
            startupError="Не завантажено TDLib на цьому пристрої. Перевір сумісність ABI.";
            if(callback!=null)callback.done(false,startupError);
        }
        changed();
    }
    private void onUpdate(TdApi.Object object){
        if(object instanceof TdApi.UpdateAuthorizationState){
            TdApi.AuthorizationState state=
                ((TdApi.UpdateAuthorizationState)object).authorizationState;
            authorizationState=state;
            loading=false;
            startupError="";
            changed();
            if(state instanceof TdApi.AuthorizationStateWaitTdlibParameters){
                setupParameters();
            }else if(state instanceof TdApi.AuthorizationStateReady){
                // Populate TDLib's local chat knowledge for exact title lookup.
                client.send(new TdApi.LoadChats(null,100),ignored->{});
            }else if(state instanceof TdApi.AuthorizationStateClosed){
                client=null;
                resetDialogue();
                busy=false;
            }
        }
    }
    private void setupParameters(){
        TelegramApiSecrets.Credentials creds=TelegramApiSecrets.load(context);
        if(creds==null||client==null){
            startupError="Немає локальних реквізитів Telegram API.";
            changed();
            return;
        }
        File root=new File(context.getNoBackupFilesDir(),"tdlib_personal");
        File media=new File(root,"media");
        if(!root.exists()&&!root.mkdirs()||!media.exists()&&!media.mkdirs()){
            startupError="Не вдалося створити захищену базу TDLib.";
            changed();return;
        }
        // TDLib database uses an independent 256-bit key stored encrypted
        // in the Android Keystore-backed no-backup settings.
        TdApi.SetTdlibParameters parameters=new TdApi.SetTdlibParameters(
            false,root.getAbsolutePath(),media.getAbsolutePath(),creds.dbKey,
            false,true,true,false,
            creds.apiId,creds.apiHash,"uk",Build.MANUFACTURER+" "+Build.MODEL,
            Build.VERSION.RELEASE,"JARVIS Android");
        client.send(parameters,object -> MAIN.post(() -> {
            if(object instanceof TdApi.Error){
                startupError="Помилка налаштування TDLib: "+
                    ((TdApi.Error)object).message;
                changed();
            }
        }));
    }
    private static String resultMessage(TdApi.Object object,String success){
        if(object instanceof TdApi.Error)
            return "Telegram відхилив запит: "+((TdApi.Error)object).message;
        return success;
    }
    private void auth(TdApi.Function<?> request,Callback cb,String success){
        if(client==null){cb.done(false,"Спочатку підключи Telegram API.");return;}
        client.send(request,response->MAIN.post(() ->{
            boolean ok=!(response instanceof TdApi.Error);
            cb.done(ok,resultMessage(response,success));
            changed();
        }));
    }
    public void phone(String raw,Callback cb){
        if(!(authorizationState instanceof TdApi.AuthorizationStateWaitPhoneNumber)){
            cb.done(false,status());return;
        }
        String number=raw==null?"":raw.trim().replaceAll("[ ()-]","");
        if(!number.matches("\\+[1-9][0-9]{7,14}")){
            cb.done(false,"Введи номер у форматі +380XXXXXXXXX.");return;
        }
        auth(new TdApi.SetAuthenticationPhoneNumber(number,null),cb,
            "Запит на код входу надіслано в Telegram.");
    }
    public void code(String raw,Callback cb){
        if(!(authorizationState instanceof TdApi.AuthorizationStateWaitCode)){
            cb.done(false,status());return;
        }
        String value=raw==null?"":raw.trim();
        if(!value.matches("[A-Za-z0-9-]{3,16}")){
            cb.done(false,"Перевір код входу Telegram.");return;
        }
        auth(new TdApi.CheckAuthenticationCode(value),cb,
            "Код передано Telegram. Перевір стан входу.");
    }
    public void password(String raw,Callback cb){
        if(!(authorizationState instanceof TdApi.AuthorizationStateWaitPassword)){
            cb.done(false,status());return;
        }
        if(raw==null||raw.isEmpty()||raw.length()>128){
            cb.done(false,"Введи пароль 2FA.");return;
        }
        auth(new TdApi.CheckAuthenticationPassword(raw),cb,
            "Пароль перевірено. Очікую підтвердження Telegram.");
    }

    public void process(TelegramReplyCommands.Request command,Callback cb){
        if(command==null){cb.done(false,"Не розпізнав команду Telegram.");return;}
        if(command.kind==TelegramReplyCommands.Kind.STATUS){
            cb.done(true,status());return;
        }
        if(command.kind==TelegramReplyCommands.Kind.CANCEL){
            cancelDraft(cb);return;
        }
        if(command.kind==TelegramReplyCommands.Kind.HELP){
            cb.done(false,"Скажи: «Напиши в Telegram Яна Привіт, це тест». "+
                "Джарвіс знайде особистий чат за назвою і попросить підтвердження.");
            return;
        }
        if(!isAuthorized()){
            start(null);
            cb.done(false,status()+" Відкрий «💬 Telegram API» в Джарвісі.");
            return;
        }
        if(command.kind==TelegramReplyCommands.Kind.DRAFT ||
            command.kind==TelegramReplyCommands.Kind.DRAFT_AUTO){
            createDraft(command,cb);
            return;
        }
        if(command.kind==TelegramReplyCommands.Kind.SEND){
            sendDraft(cb);
        }
    }
    private void createDraft(TelegramReplyCommands.Request command,Callback cb){
        String lookup=command.recipient;
        if(command.kind==TelegramReplyCommands.Kind.DRAFT_AUTO){
            String speech=command.text==null?"":command.text.trim();
            String[] words=speech.split("\\s+");
            if(words.length<2){
                cb.done(false,"Назви контакт і повідомлення: «Напиши в Telegram Яна Привіт».");return;
            }
            // Look up the first spoken word; an exact private-chat title
            // determines the full contact name (including multiple words).
            lookup=TelegramReplyCommands.nameKey(words[0]);
        }
        if(lookup==null || lookup.trim().isEmpty()){
            cb.done(false,"Назви контакт і повідомлення: «Напиши в Telegram Яна Привіт».");return;
        }
        if(busy){cb.done(false,"Telegram ще шукає контакт. Повтори після завершення.");return;}
        draft=null;
        if(dialogue!=Dialogue.WAIT_CONTACT){
            dialogue=Dialogue.NONE;
            clearCorrection();
        }
        busy=true;
        final int op=++revision;
        final String query=lookup;
        client.send(new TdApi.SearchChats(query,null,50),
            result -> MAIN.post(() -> inspectChats(op,command,query,result,true,cb)));
    }
    private void inspectChats(int op,TelegramReplyCommands.Request command,
                              String query,TdApi.Object object,boolean first,Callback cb){
        if(op!=revision)return;
        if(!(object instanceof TdApi.Chats)){
            if(first){
                client.send(new TdApi.SearchChatsOnServer(query,null,50),
                    result -> MAIN.post(() -> inspectChats(op,command,query,result,false,cb)));
            }else {
                busy=false;
                cb.done(false,resultMessage(object,"Не вдалося знайти чат."));
            }
            return;
        }
        long[] ids=((TdApi.Chats)object).chatIds;
        if(ids==null||ids.length==0){
            if(first){
                client.send(new TdApi.SearchChatsOnServer(query,null,50),
                    result -> MAIN.post(() -> inspectChats(op,command,query,result,false,cb)));
            }else {
                busy=false;
                cb.done(false,"Не знайшов особистого чату, який починається з «"+
                    query+"». Перевір ім'я в Telegram.");
            }
            return;
        }
        ArrayList<ResolvedChat> matches=new ArrayList<>();
        final int[] remaining={ids.length};
        for(long id:ids){
            client.send(new TdApi.GetChat(id),result -> MAIN.post(() ->{
                if(op!=revision)return;
                if(result instanceof TdApi.Chat){
                    TdApi.Chat chat=(TdApi.Chat)result;
                    if(chat.type instanceof TdApi.ChatTypePrivate) {
                        String body=command.kind==TelegramReplyCommands.Kind.DRAFT_AUTO
                            ? TelegramReplyCommands.messageAfterContact(command.text,chat.title)
                            : (TelegramReplyCommands.exactContact(command.recipient,chat.title)
                                ? command.text : null);
                        if(body!=null)matches.add(new ResolvedChat(chat,body));
                    }
                }
                if(--remaining[0]!=0)return;
                if(matches.isEmpty()&&first){
                    client.send(new TdApi.SearchChatsOnServer(query,null,50),
                        found -> MAIN.post(()->inspectChats(op,command,query,found,false,cb)));
                    return;
                }
                busy=false;
                if(matches.size()!=1){
                    cb.done(false,matches.isEmpty()?
                        "Не знайшов особистий чат за продиктованим ім'ям. "+
                        "Назви контакт як у Telegram.":
                        "Підійшло кілька особистих чатів. Уточни контакт, "+
                        "наприклад: «Напиши в Telegram Ім'я Прізвище: текст». "+
                        "Нічого не відправляв.");
                    return;
                }
                ResolvedChat chosen=matches.get(0);
                draft=new Draft(chosen.chat,chosen.text);
                dialogue=Dialogue.CONFIRM;
                clearCorrection();
                cb.done(true,confirmationPrompt());
            }));
        }
    }
    private void sendDraft(Callback cb){
        if(busy){cb.done(false,"Telegram ще виконує попередній запит.");return;}
        if(dialogue!=Dialogue.CONFIRM || draft==null){
            cb.done(false,"Спочатку продиктуй повідомлення та отримай нове підтвердження.");
            return;
        }
        Draft d=draft;
        resetDialogue(); // consume BEFORE network request; never retry automatically
        revision++;
        if(SystemClock.elapsedRealtime()-d.createdAt>DRAFT_MAX_AGE_MS){
            cb.done(false,"Чернетка Telegram застаріла. Продиктуй повідомлення ще раз.");return;
        }
        busy=true;
        final int op=revision;
        client.send(new TdApi.GetChat(d.chatId),result -> MAIN.post(() ->{
            if(op!=revision)return; // cancelled before dispatch: never send
            if(!(result instanceof TdApi.Chat) ||
                !(((TdApi.Chat)result).type instanceof TdApi.ChatTypePrivate) ||
                !TelegramReplyCommands.exactContact(d.recipient,
                    ((TdApi.Chat)result).title)){
                busy=false;
                cb.done(false,"Отримувач змінився. Повідомлення не надсилав.");
                return;
            }
            TdApi.FormattedText formatted=
                new TdApi.FormattedText(d.text,new TdApi.TextEntity[0]);
            TdApi.InputMessageText message=new TdApi.InputMessageText(formatted,null,false);
            // After this point cancellation cannot guarantee unsending.
            sendDispatched=true;
            client.send(new TdApi.SendMessage(d.chatId,null,null,null,null,message),
                response -> MAIN.post(() ->{
                    sendDispatched=false;
                    busy=false;
                    if(response instanceof TdApi.Message)
                        cb.done(true,"Telegram прийняв повідомлення для «"+
                            d.recipient+"» від твого акаунта. Перевір доставку в Telegram.");
                    else cb.done(false,resultMessage(response,
                        "Telegram не підтвердив надсилання. Перевір чат."));
                }));
        }));
    }
}
