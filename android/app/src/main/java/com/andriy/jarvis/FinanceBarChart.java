package com.andriy.jarvis;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import java.time.*;

/** Small local chart; never sums non-UAH money without user-specified FX rates. */
public final class FinanceBarChart extends View {
    private final long[] months=new long[6];
    private final String[] captions=new String[6];
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean hasRecords;
    public FinanceBarChart(Context context,FinanceLedger ledger){
        super(context);
        LocalDate first=LocalDate.now().withDayOfMonth(1);
        for(int i=0;i<6;i++){
            LocalDate month=first.minusMonths(5-i);
            captions[i]=String.format(java.util.Locale.ROOT,"%02d.%02d",month.getMonthValue(),month.getYear()%100);
            long from=month.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            long to=month.plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            try(Cursor c=ledger.getReadableDatabase().rawQuery(
                "SELECT COUNT(*),COALESCE(-SUM(e.amount),0) FROM entries e JOIN accounts a ON a.id=e.account "+
                "WHERE e.deleted=0 AND e.kind IN ('expense','refund') AND a.currency='UAH' "+
                "AND e.happened>=? AND e.happened<?",new String[]{""+from,""+to})){
                c.moveToFirst();
                if(c.getLong(0)>0){hasRecords=true;months[i]=Math.max(0,c.getLong(1));}
            }
        }
        setContentDescription("Графік витрат UAH за шість місяців. Точні суми наведені у текстовому звіті.");
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float d=getResources().getDisplayMetrics().density;
        float width=getWidth(),height=getHeight();
        canvas.drawColor(Color.rgb(10,18,30));
        pen.setColor(Color.WHITE);pen.setTextSize(15*d);
        canvas.drawText("Витрати UAH · 6 місяців",12*d,22*d,pen);
        if(!hasRecords){
            pen.setTextSize(13*d);canvas.drawText("Немає записів у гривнях",12*d,height/2,pen);
            return;
        }
        long max=1;for(long v:months)max=Math.max(max,v);
        float top=42*d,bottom=height-38*d,plotHeight=Math.max(1,bottom-top);
        float slot=(width-24*d)/6;
        for(int i=0;i<6;i++){
            float x=12*d+i*slot+slot*.17f,barWidth=slot*.66f;
            float barHeight=months[i]*plotHeight/max;
            if(months[i]>0)barHeight=Math.max(2*d,barHeight);
            pen.setColor(Color.rgb(73,170,236));
            canvas.drawRoundRect(new RectF(x,bottom-barHeight,x+barWidth,bottom),4*d,4*d,pen);
            pen.setColor(Color.LTGRAY);pen.setTextSize(10*d);
            canvas.drawText(captions[i],12*d+i*slot+2*d,height-14*d,pen);
        }
        pen.setColor(Color.LTGRAY);pen.setTextSize(10*d);
        canvas.drawText("Макс. "+FinanceLedger.money(max)+" ₴",12*d,height-2*d,pen);
    }
}
