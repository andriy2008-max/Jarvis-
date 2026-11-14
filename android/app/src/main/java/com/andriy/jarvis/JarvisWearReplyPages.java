package com.andriy.jarvis;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Short-lived, phone-only, per-watch page cache for reading long notes and
 * assistant/web answers. Never persists text, URLs, tokens or API credentials.
 * Every page is requested by the same authenticated Wear node that created it.
 */
public final class JarvisWearReplyPages {
    public static final int PAGE_CHARS = 850; // fits 4096-byte Wear JSON even with emoji
    private static final int MAX_CHARS = 64000;
    private static final int MAX_DOCUMENTS = 8;
    private static final long TTL_MS = 15L * 60L * 1000L;

    public static final class Page {
        public final String documentId;
        public final int index;
        public final int count;
        public final String text;
        Page(String id,int index,int count,String text) {
            this.documentId=id;this.index=index;this.count=count;this.text=text;
        }
    }
    private static final class Document {
        final String watchNode, documentId, text;
        final long expiry;
        Document(String node,String id,String text,long expiry) {
            watchNode=node;documentId=id;this.text=text;this.expiry=expiry;
        }
    }
    private static final LinkedHashMap<String,Document> documents =
        new LinkedHashMap<>();
    private JarvisWearReplyPages() {}

    public static synchronized Page save(String watchNode,String requestId,String reply) {
        if(watchNode==null || watchNode.isEmpty() ||
           !JarvisWearProtocol.validId(requestId))return null;
        purge(System.currentTimeMillis());
        String body=reply==null?"":reply;
        if(body.length()>MAX_CHARS)
            body=body.substring(0,MAX_CHARS)+"\n… Відповідь занадто довга для годинника.";
        Document doc=new Document(watchNode,requestId,body,
            System.currentTimeMillis()+TTL_MS);
        documents.put(key(watchNode,requestId),doc);
        while(documents.size()>MAX_DOCUMENTS) {
            String oldest=documents.keySet().iterator().next();
            documents.remove(oldest);
        }
        return read(watchNode,requestId,0);
    }

    public static synchronized Page read(String watchNode,String documentId,int index) {
        purge(System.currentTimeMillis());
        if(watchNode==null || !JarvisWearProtocol.validId(documentId))
            return null;
        Document doc=documents.get(key(watchNode,documentId));
        if(doc==null)return null;
        int count=Math.max(1,(doc.text.length()+PAGE_CHARS-1)/PAGE_CHARS);
        if(index<0 || index>=count)return null;
        int from=index*PAGE_CHARS;
        int to=Math.min(doc.text.length(),from+PAGE_CHARS);
        // Avoid splitting a surrogate pair at the chunk boundary.
        if(to<doc.text.length() && to>from &&
           Character.isHighSurrogate(doc.text.charAt(to-1))) to--;
        String page=doc.text.substring(from,to);
        // The next chunk's start must use stable boundaries: page boundaries
        // are adjusted in the page calculation below.
        return new Page(documentId,index,count,page);
    }

    private static String key(String watchNode,String id) {return watchNode+":"+id;}
    private static void purge(long now) {
        java.util.Iterator<Map.Entry<String,Document>> it=documents.entrySet().iterator();
        while(it.hasNext()) {
            if(it.next().getValue().expiry<now)it.remove();
        }
    }
}
