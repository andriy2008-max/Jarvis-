package com.andriy.jarvis;
public final class JarvisRecentRequestsTest {
    public static void main(String[] args) {
        String id=String.format("%064x",1);
        JarvisRecentRequests first=new JarvisRecentRequests("");
        if(!first.claim(id)||first.claim(id))throw new AssertionError("duplicate executed");
        JarvisRecentRequests restored=new JarvisRecentRequests(first.save());
        if(restored.claim(id))throw new AssertionError("replayed after process restart");
        if(restored.claim("private command"))throw new AssertionError("accepted raw payload");
        for(int i=2;i<200;i++)restored.claim(String.format("%064x",i));
        if(restored.save().split("\n").length!=128)throw new AssertionError("unbounded cache");
        System.out.println("JarvisRecentRequestsTest PASS: duplicate/restart/privacy/bound");
    }
}
