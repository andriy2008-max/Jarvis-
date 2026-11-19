package com.andriy.jarvis;
public final class JarvisCallbackGateTest {
    public static void main(String[] args) throws Exception {
        JarvisCallbackGate gate=new JarvisCallbackGate();
        long old=gate.begin();
        if(!gate.finish(old) || gate.finish(old))throw new AssertionError("terminal must be once");
        long next=gate.begin();
        if(gate.finish(old) || !gate.active(next))throw new AssertionError("old completion killed new action");
        gate.cancel();
        if(gate.finish(next))throw new AssertionError("cancelled callback accepted");
        for(int i=0;i<100;i++) {
            long token=gate.begin();
            java.util.concurrent.atomic.AtomicInteger wins=new java.util.concurrent.atomic.AtomicInteger();
            Thread a=new Thread(()->{if(gate.finish(token))wins.incrementAndGet();});
            Thread b=new Thread(()->{if(gate.finish(token))wins.incrementAndGet();});
            a.start();b.start();a.join();b.join();
            if(wins.get()!=1)throw new AssertionError("callback race");
        }
        System.out.println("JarvisCallbackGateTest: 100 races, stale and duplicate callbacks PASS");
    }
}
