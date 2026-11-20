package com.andriy.jarvis;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Non-destructive import of the current month's first-party, pre-ledger
 * budget settings from FinanceInsightsController.
 *
 * Does not read or delete data from the external Money Manager, does not
 * overwrite existing secure budgets, and keeps the original preferences.
 */
public final class FinanceBudgetMigration {
    private static final String OLD="jarvis_finance_monthly_budgets_v1";
    private static final String MARKER="finance_monthly_budgets_import_v1";
    private FinanceBudgetMigration() {}

    public static synchronized void migrate(Context context,FinanceLedger ledger) {
        if("true".equals(ledger.setting(MARKER,"false")))return;
        SharedPreferences old=context.getApplicationContext()
            .getSharedPreferences(OLD,Context.MODE_PRIVATE);
        YearMonth current=YearMonth.now();
        String month=String.format(Locale.ROOT,"%04d-%02d",
            current.getYear(),current.getMonthValue());
        String prefix=month+"/";
        SQLiteDatabase db=ledger.getWritableDatabase();
        db.beginTransaction();
        try {
            for(Map.Entry<String,?> item:old.getAll().entrySet()) {
                String key=item.getKey();
                if(key==null || !key.startsWith(prefix) ||
                   !(item.getValue() instanceof String))continue;
                String part=key.substring(prefix.length());
                String category;
                if("total".equals(part))category="";
                else if(part.startsWith("category/") &&
                        part.length()>"category/".length()) {
                    category=part.substring("category/".length()).trim();
                    if(category.length()>80)continue;
                } else continue;
                String value=(String)item.getValue();
                final long minor;
                try {
                    BigDecimal decimal=new BigDecimal(value.trim().replace(',','.'));
                    if(decimal.signum()<=0 || decimal.scale()>2)continue;
                    minor=decimal.movePointRight(2).longValueExact();
                    if(minor<=0)continue;
                } catch(RuntimeException bad) {continue;}
                // Existing plans from 0.1.33 have priority; never duplicate
                // or overwrite them merely because a legacy budget exists.
                try(Cursor exists=db.rawQuery(
                    "SELECT 1 FROM plans WHERE type='budget' "+
                    "AND currency='UAH' AND budget_period='month' "+
                    "AND category=? LIMIT 1",new String[]{category})) {
                    if(exists.moveToFirst())continue;
                }
                ContentValues budget=new ContentValues();
                budget.put("id",UUID.randomUUID().toString());
                budget.put("type","budget");
                budget.put("name",category.isEmpty()
                    ?"Перенесений загальний бюджет "+month
                    :"Перенесений бюджет "+category+" "+month);
                budget.put("currency","UAH");
                budget.put("amount",minor);
                budget.put("category",category);
                budget.put("due","");
                budget.put("budget_period","month");
                db.insertOrThrow("plans",null,budget);
            }
            db.execSQL("INSERT OR REPLACE INTO settings(name,value) VALUES(?,?)",
                new Object[]{MARKER,"true"});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
