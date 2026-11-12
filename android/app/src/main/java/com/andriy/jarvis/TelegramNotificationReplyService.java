package com.andriy.jarvis;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Reply through Telegram's OWN notification RemoteInput action.
 * Sends from the user's signed-in Telegram account (not a bot).
 * No message bodies are read, stored, logged or forwarded to AI.
 * There is never an autonomous reply: requires an explicit draft
 * voice command followed by an independent SEND voice command.
 *
 * Limitation: only currently-active Telegram notifications with a
 * free-form Reply action; arbitrary chats need Telegram TDLib.
 */
public final class TelegramNotificationReplyService extends NotificationListenerService {
    private static final String TELEGRAM="org.telegram.messenger";
    private static final long DRAFT_LIFETIME_MS=90000L;
    private static final Object LOCK=new Object();
    private static WeakReference<TelegramNotificationReplyService> active=
        new WeakReference<>(null);
    private static PendingDraft draft;

    private static final class Candidate {
        final String key,person;
        final long posted;
        final StatusBarNotification notification;
        final Notification.Action action;
        Candidate(StatusBarNotification sbn,String person,Notification.Action action){
            key=sbn.getKey();this.person=person;posted=sbn.getPostTime();
            notification=sbn;this.action=action;
        }
    }
    private static final class PendingDraft {
        final String key,person,message;
        final long posted,created;
        PendingDraft(Candidate c,String text){
            key=c.key;person=c.person;message=text;
            posted=c.posted;created=SystemClock.elapsedRealtime();
        }
    }

    @Override public void onListenerConnected(){
        super.onListenerConnected();
        synchronized(LOCK){active=new WeakReference<>(this);draft=null;}
    }
    @Override public void onListenerDisconnected(){
        synchronized(LOCK){active=new WeakReference<>(null);draft=null;}
        super.onListenerDisconnected();
    }
    @Override public void onDestroy(){
        synchronized(LOCK){
            if(active.get()==this){active=new WeakReference<>(null);draft=null;}
        }
        super.onDestroy();
    }
    // No onNotificationPosted callback: never capture messages in background.

    public static boolean permitted(Context c){
        try {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O_MR1){
                NotificationManager manager=(NotificationManager)c.getSystemService(
                    Context.NOTIFICATION_SERVICE);
                return manager!=null && manager.isNotificationListenerAccessGranted(
                    new ComponentName(c,TelegramNotificationReplyService.class));
            }
            String listeners=Settings.Secure.getString(c.getContentResolver(),
                "enabled_notification_listeners");
            return listeners!=null && listeners.contains(c.getPackageName()+
                "/"+TelegramNotificationReplyService.class.getName());
        }catch(RuntimeException exception){return false;}
    }

    private static String title(Notification n){
        if(n==null||n.extras==null)return "";
        CharSequence group=n.extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
        CharSequence direct=n.extras.getCharSequence(Notification.EXTRA_TITLE);
        String person=group!=null?group.toString().trim():
            direct==null?"":direct.toString().trim();
        if(person.isEmpty() || TelegramReplyCommands.nameKey(person).equals("telegram") ||
            person.length()>100)return "";
        return person;
    }
    private static Notification.Action findReply(Notification n) {
        if(n==null||n.actions==null)return null;
        Notification.Action selected=null;
        for(Notification.Action action:n.actions) {
            if(action==null||action.actionIntent==null)continue;
            RemoteInput[] fields=action.getRemoteInputs();
            if(fields==null||fields.length==0)continue;
            boolean free=false;
            for(RemoteInput field:fields)
                if(field!=null && field.getAllowFreeFormInput())free=true;
            if(!free)continue;
            // Ambiguous multiple reply actions: refuse.
            if(selected!=null)return null;
            selected=action;
        }
        return selected;
    }
    private static List<Candidate> eligible(TelegramNotificationReplyService listener){
        List<Candidate> list=new ArrayList<>();
        if(listener==null)return list;
        StatusBarNotification[] notifications;
        try {notifications=listener.getActiveNotifications();}
        catch(RuntimeException error){return list;}
        if(notifications==null)return list;
        for(StatusBarNotification sbn:notifications) {
            if(sbn==null || !TELEGRAM.equals(sbn.getPackageName()) ||
                sbn.getKey()==null)continue;
            Notification n=sbn.getNotification();
            if(n==null || (n.flags & Notification.FLAG_GROUP_SUMMARY)!=0)continue;
            String person=title(n);
            if(person.isEmpty())continue;
            Notification.Action action=findReply(n);
            if(action==null)continue;
            list.add(new Candidate(sbn,person,action));
        }
        return list;
    }

    public static String status(Context c){
        if(!permitted(c))return "Telegram: увімкни для Джарвіса доступ до сповіщень.";
        synchronized(LOCK) {
            TelegramNotificationReplyService service=active.get();
            if(service==null)return "Доступ надано, але служба сповіщень ще не підключилася.";
            int count=eligible(service).size();
            return "Telegram підключений. Активних сповіщень з кнопкою «Відповісти»: "+
                count+". Без сповіщення пряме надсилання недоступне.";
        }
    }

    public static String prepare(Context c,TelegramReplyCommands.Request request){
        if(request==null || request.kind!=TelegramReplyCommands.Kind.DRAFT)
            return "Невідома команда Telegram.";
        if(!permitted(c))
            return "Для відповіді від твого Telegram увімкни доступ Джарвіса "+
                "до сповіщень у налаштуваннях Telegram в Джарвісі.";
        synchronized(LOCK){
            draft=null;
            TelegramNotificationReplyService service=active.get();
            if(service==null)return "Служба сповіщень Джарвіса не підключена.";
            List<Candidate> matches=new ArrayList<>();
            HashSet<String> people=new HashSet<>();
            for(Candidate candidate:eligible(service)){
                if(request.recipient==null ||
                    TelegramReplyCommands.exactContact(request.recipient,candidate.person)){
                    matches.add(candidate);
                    people.add(TelegramReplyCommands.nameKey(candidate.person));
                }
            }
            if(matches.isEmpty())return request.recipient==null?
                "Немає активного повідомлення Telegram з дією «Відповісти». "+
                "Для нових чатів без сповіщень потрібен TDLib.":
                "Немає доступного сповіщення Telegram від «"+request.recipient+
                "». Ім'я має збігатися з назвою чату в сповіщенні.";
            if(people.size()!=1)
                return "Є кілька чатів Telegram. Уточни ім'я після "+
                    "«Відповідай у телеграм», щоб не відправити не туди.";
            Candidate newest=matches.get(0);
            for(Candidate candidate:matches)
                if(candidate.posted>newest.posted)newest=candidate;
            draft=new PendingDraft(newest,request.text);
            return "Підготував відповідь для «"+newest.person+"»: «"+request.text+
                "». Для відправлення окремо скажи «Надішли в телеграм» "+
                "або «Скасуй відповідь».";
        }
    }

    public static String cancel(){
        synchronized(LOCK){
            boolean existed=draft!=null;
            draft=null;
            return existed?"Чернетку Telegram скасовано. Нічого не відправив.":
                "Немає підготовленої відповіді Telegram.";
        }
    }

    public static String send(Context c){
        if(!permitted(c))return "Доступ до сповіщень Telegram вимкнений.";
        synchronized(LOCK){
            PendingDraft pending=draft;
            draft=null; // consumed BEFORE sending: no accidental duplicates
            if(pending==null)return "Спочатку продиктуй відповідь у Telegram.";
            if(SystemClock.elapsedRealtime()-pending.created>DRAFT_LIFETIME_MS)
                return "Чернетка Telegram застаріла. Продиктуй відповідь знову.";
            TelegramNotificationReplyService service=active.get();
            if(service==null)return "Служба сповіщень Telegram відключилася.";
            Candidate match=null;
            for(Candidate candidate:eligible(service)){
                if(pending.key.equals(candidate.key) &&
                    pending.posted==candidate.posted &&
                    TelegramReplyCommands.exactContact(pending.person,candidate.person)) {
                    match=candidate;break;
                }
            }
            if(match==null)return "Сповіщення або отримувач змінилися. "+
                "Нічого не відправив. Продиктуй відповідь ще раз.";
            RemoteInput[] fields=match.action.getRemoteInputs();
            if(fields==null||fields.length==0)return "Telegram більше не пропонує відповісти.";
            Bundle values=new Bundle();
            boolean hasFreeform=false;
            for(RemoteInput field:fields) {
                if(field!=null && field.getAllowFreeFormInput()){
                    values.putCharSequence(field.getResultKey(),pending.message);
                    hasFreeform=true;
                }
            }
            if(!hasFreeform)return "Telegram більше не пропонує текстової відповіді.";
            Intent intent=new Intent();
            RemoteInput.addResultsToIntent(fields,intent,values);
            if(Build.VERSION.SDK_INT>=28)
                RemoteInput.setResultsSource(intent,RemoteInput.SOURCE_FREE_FORM_INPUT);
            try {
                match.action.actionIntent.send(service,0,intent);
                return "Передав Telegram відповідь для «"+pending.person+
                    "» від твого акаунта. Перевір статус доставки в Telegram.";
            }catch(PendingIntent.CanceledException|RuntimeException error){
                return "Telegram відхилив відправлення. "+
                    "Повторно автоматично не надсилаю — перевір чат.";
            }
        }
    }
}
