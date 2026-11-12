package com.andriy.jarvis;

import android.content.Context;

/** Direct user-authorized MTProto actions; no notification access required. */
public final class TelegramReplyController {
    private TelegramReplyController(){}
    public static void perform(Context context,TelegramReplyCommands.Request command,
                               TelegramTdClient.Callback callback) {
        if(callback==null)return;
        if(context==null||command==null) {
            callback.done(false,"Невідома команда Telegram.");return;
        }
        if(context.getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_WATCH)) {
            callback.done(false,"Telegram API виконується на підключеному телефоні.");
            return;
        }
        TelegramTdClient.get(context).process(command,callback);
    }
}
