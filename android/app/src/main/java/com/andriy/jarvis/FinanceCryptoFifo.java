package com.andriy.jarvis;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Pure accounting for documented buys/sells; opening lots without costs remain unknown. */
public final class FinanceCryptoFifo {
    private static final MathContext MATH=MathContext.DECIMAL128;
    private FinanceCryptoFifo(){}
    public static final class Trade {
        public final boolean buy;
        public final BigDecimal quantity;
        public final long grossMinor,feeMinor;
        public Trade(boolean buy,String qty,long grossMinor,long feeMinor){
            this.buy=buy;this.quantity=new BigDecimal(qty);
            this.grossMinor=grossMinor;this.feeMinor=feeMinor;
            if(this.quantity.signum()<=0||grossMinor<=0||feeMinor<0||!buy&&feeMinor>grossMinor)
                throw new IllegalArgumentException("Неправильна криптоугода");
        }
    }
    private static final class Lot {
        BigDecimal quantity,costMinor;
        Lot(BigDecimal qty,BigDecimal cost){quantity=qty;costMinor=cost;}
    }
    public static final class Result {
        public final BigDecimal knownRemainingCostMinor,realizedMinor;
        public final boolean complete;
        public final BigDecimal unknownQuantity;
        Result(BigDecimal cost,BigDecimal realized,boolean complete,BigDecimal unknown){
            knownRemainingCostMinor=cost;realizedMinor=realized;this.complete=complete;unknownQuantity=unknown;
        }
        public String format(String ticker){
            StringBuilder b=new StringBuilder(ticker).append(" · FIFO, лише введені угоди\n");
            b.append("Відома собівартість залишку: ")
                .append(knownRemainingCostMinor.movePointLeft(2).setScale(2,RoundingMode.HALF_UP).toPlainString()).append(" ₴\n");
            if(complete)b.append("Реалізований результат: ")
                .append(realizedMinor.movePointLeft(2).setScale(2,RoundingMode.HALF_UP).toPlainString()).append(" ₴");
            else b.append("Реалізований результат неповний: відсутня ціна купівлі первинних активів");
            return b.toString();
        }
    }
    public static Result compute(List<Trade> chronological,BigDecimal unknownOpeningQty){
        if(unknownOpeningQty.signum()<0)throw new IllegalArgumentException("Некоректна первинна кількість");
        Deque<Lot> known=new ArrayDeque<>();
        BigDecimal unpriced=unknownOpeningQty;
        BigDecimal realized=BigDecimal.ZERO;
        boolean complete=true;
        for(Trade t:chronological){
            if(t.buy){
                known.addLast(new Lot(t.quantity,BigDecimal.valueOf(Math.addExact(t.grossMinor,t.feeMinor))));
                continue;
            }
            BigDecimal remaining=t.quantity;
            BigDecimal cost=BigDecimal.ZERO;
            if(unpriced.signum()>0){
                BigDecimal consumed=unpriced.min(remaining);
                remaining=remaining.subtract(consumed);unpriced=unpriced.subtract(consumed);
                if(consumed.signum()>0)complete=false;
            }
            while(remaining.signum()>0&&!known.isEmpty()){
                Lot lot=known.peekFirst();
                BigDecimal consumed=lot.quantity.min(remaining);
                BigDecimal portion=lot.costMinor.multiply(consumed,MATH).divide(lot.quantity,MATH);
                cost=cost.add(portion);lot.quantity=lot.quantity.subtract(consumed);
                lot.costMinor=lot.costMinor.subtract(portion);
                remaining=remaining.subtract(consumed);
                if(lot.quantity.signum()==0)known.removeFirst();
            }
            if(remaining.signum()>0)complete=false; // Incomplete imported history.
            if(complete)realized=realized.add(BigDecimal.valueOf(t.grossMinor-t.feeMinor)).subtract(cost);
        }
        BigDecimal basis=BigDecimal.ZERO;
        for(Lot lot:known)basis=basis.add(lot.costMinor);
        return new Result(basis,realized,complete,unpriced);
    }
}
