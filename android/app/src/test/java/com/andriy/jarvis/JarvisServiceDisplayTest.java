package com.andriy.jarvis;

/** Service-context exception, missing service, and fallback behaviour regression. */
public final class JarvisServiceDisplayTest {
    private static void check(boolean ok){if(!ok)throw new AssertionError();}
    public static void main(String[] args){
        Object display=new Object();
        check(JarvisServiceDisplay.firstAvailable(()->display,()->{throw new AssertionError("Unexpected fallback");})==display);
        check(JarvisServiceDisplay.firstAvailable(()->null,()->display)==display);
        check(JarvisServiceDisplay.firstAvailable(()->{throw new UnsupportedOperationException("Service has no display");},()->display)==display);
        check(JarvisServiceDisplay.firstAvailable(()->{throw new SecurityException();},()->{throw new UnsupportedOperationException();})==null);
        check(JarvisServiceDisplay.firstAvailable(()->null,()->null)==null);
        System.out.println("PASS JarvisServiceDisplayTest (5 cases)");
    }
}
