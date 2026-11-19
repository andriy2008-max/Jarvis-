package com.andriy.jarvis;
import java.util.LinkedHashSet;
final class JarvisRecentRequests {
    private final LinkedHashSet<String> ids=new LinkedHashSet<>();
    JarvisRecentRequests(String saved) {
        if(saved!=null) for(String id:saved.split("\n")) if(id.matches("[a-f0-9]{64}"))ids.add(id);
        trim();
    }
    boolean claim(String id) {
        if(!id.matches("[a-f0-9]{64}") || !ids.add(id))return false;
        trim();return true;
    }
    private void trim() { while(ids.size()>128)ids.remove(ids.iterator().next()); }
    String save() { return String.join("\n",ids); }
}
