package com.andriy.jarvis;

import android.content.Context;

/** Single dispatch for on-device or Wear voice commands. */
public final class TelegramReplyController {
    private TelegramReplyController(){}

    public static String perform(Context context,TelegramReplyCommands.Request command){
        if(context==null || command==null)return "Невідома команда Telegram.";
        if(context.getPackageManager().hasSystemFeature(
            android.content.pm.PackageManager.FEATURE_WATCH))
            return "На годиннику Telegram обробляється через підключений телефон.";
        switch(command.kind) {
            case STATUS:return TelegramNotificationReplyService.status(context);
            case DRAFT:return TelegramNotificationReplyService.prepare(context,command);
            case SEND:return TelegramNotificationReplyService.send(context);
            case CANCEL:return TelegramNotificationReplyService.cancel();
            default:return "Команда Telegram не підтримується.";
        }
    }
}
