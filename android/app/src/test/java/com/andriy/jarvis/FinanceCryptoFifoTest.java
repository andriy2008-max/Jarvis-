package com.andriy.jarvis;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;

public final class FinanceCryptoFifoTest {
    private static void eq(String expected,String observed){
        if(!expected.equals(observed))throw new AssertionError(expected+" != "+observed);
    }
    public static void main(String[] args){
        // Buy 1 unit for 1000 UAH+10 fee; sell 0.5 for 700-7 fee.
        FinanceCryptoFifo.Result partial=FinanceCryptoFifo.compute(Arrays.asList(
            new FinanceCryptoFifo.Trade(true,"1",100000,1000),
            new FinanceCryptoFifo.Trade(false,"0.5",70000,700)),
            BigDecimal.ZERO);
        if(!partial.complete)throw new AssertionError("Known trades should be priced");
        eq("50500",partial.knownRemainingCostMinor.stripTrailingZeros().toPlainString());
        eq("18800",partial.realizedMinor.stripTrailingZeros().toPlainString());
        FinanceCryptoFifo.Result unknown=FinanceCryptoFifo.compute(Arrays.asList(
            new FinanceCryptoFifo.Trade(true,"1",50000,0),
            new FinanceCryptoFifo.Trade(false,"0.5",30000,0)),
            new BigDecimal("0.25"));
        if(unknown.complete)throw new AssertionError("Unpriced opening lots must block PnL claim");
        eq("37500.00",unknown.knownRemainingCostMinor.setScale(2).toPlainString());
        FinanceCryptoFifo.Result zero=FinanceCryptoFifo.compute(Collections.emptyList(),BigDecimal.ZERO);
        if(!zero.complete)throw new AssertionError("Empty trade history should be internally consistent");
        System.out.println("PASS FinanceCryptoFifoTest");
    }
}
