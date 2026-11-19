package com.andriy.jarvis;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;

/** Compact offline six-month spending chart: one currency at a time, refunds netted. */
public final class FinanceTrendChart extends View {
    private final String[] months=new String[6];
    private final long[] values=new long[6];
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final String currency;
    private final boolean hasAccounts;

    public FinanceTrendChart(Context context,FinanceLedger ledger){
        super(context);
        density=getResources().getDisplayMetrics().density;
        String selected=null;
        try(Cursor c=ledger.getReadableDatabase().rawQuery(
            "SELECT currency FROM accounts GROUP BY currency ORDER BY CASE WHEN currency='UAH' THEN 0 ELSE 1 END, currency LIMIT 1",null)){
            if(c.moveToFirst())selected=c.getString(0);
        }
        currency=selected==null?"UAH":selected;
        hasAccounts=selected!=null;
        LocalDate first=LocalDate.now().withDayOfMonth(1);
        StringBuilder accessibility=new StringBuilder("Графік чистих витрат за шість місяців, ").append(currency).append(". ");
        for(int i=0;i<6;i++){
            LocalDate month=first.minusMonths(5-i),next=month.plusMonths(1);
            months[i]=month.toString().substring(2,7);
            long from=month.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long to=next.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if(hasAccounts){
                try(Cursor c=ledger.getReadableDatabase().rawQuery(
                    "SELECT COALESCE(-SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id=e.account "+
                    "WHERE e.deleted=0 AND e.kind IN ('expense','refund') AND a.currency=? "+
                    "AND e.happened>=? AND e.happened<?",
                    new String[]{currency,""+from,""+to})){
                    if(c.moveToFirst())values[i]=Math.max(0,c.getLong(0));
                }
            }
            accessibility.append(months[i]).append(": ").append(FinanceLedger.money(values[i])).append(". ");
        }
        setContentDescription(accessibility.toString());
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    private float dp(float size){return size*density;}
    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        setMeasuredDimension(resolveSize(dpInt(320),widthMeasureSpec),
            resolveSize(dpInt(240),heightMeasureSpec));
    }
    private int dpInt(float size){return Math.round(dp(size));}
    private static String compact(long amount){
        double value=amount/100.0;
        if(value>=1_000_000)return String.format(Locale.ROOT,"%.1fм",value/1_000_000);
        if(value>=10_000)return String.format(Locale.ROOT,"%.0fтис",value/1_000);
        if(value>=1_000)return String.format(Locale.ROOT,"%.1fтис",value/1_000);
        return String.format(Locale.ROOT,"%.0f",value);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth(),h=getHeight();
        paint.setStyle(Paint.Style.FILL);paint.setColor(Color.rgb(236,242,251));
        paint.setTextSize(dp(15));paint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("Чисті витрати · 6 місяців · "+currency,dp(12),dp(28),paint);
        if(!hasAccounts){
            canvas.drawText("Створи рахунок, щоб побачити тренд.",dp(12),dp(74),paint);return;
        }
        float left=dp(12),right=w-dp(12),top=dp(62),bottom=h-dp(40);
        if(right<=left||bottom<=top)return;
        paint.setColor(Color.rgb(51,68,87));paint.setStrokeWidth(dp(1));
        for(int j=0;j<=3;j++){
            float y=top+(bottom-top)*j/3f;
            canvas.drawLine(left,y,right,y,paint);
        }
        long maximum=1;
        for(long value:values)maximum=Math.max(maximum,value);
        float step=(right-left)/6f,barWidth=Math.min(dp(36),step*0.58f);
        for(int i=0;i<6;i++){
            float center=left+step*(i+0.5f);
            float barHeight=(bottom-top)*values[i]/maximum;
            paint.setColor(Color.rgb(65,164,223));
            if(values[i]>0)canvas.drawRoundRect(center-barWidth/2,bottom-barHeight,
                center+barWidth/2,bottom,dp(4),dp(4),paint);
            paint.setColor(Color.rgb(224,232,243));paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(dp(10));
            canvas.drawText(compact(values[i]),center,
                Math.max(dp(48),bottom-barHeight-dp(6)),paint);
            paint.setTextSize(dp(11));canvas.drawText(months[i],center,bottom+dp(20),paint);
        }
    }
}
