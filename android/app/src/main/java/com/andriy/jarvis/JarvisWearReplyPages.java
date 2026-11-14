package com.andriy.jarvis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Short-lived, phone-only, per-watch page cache for reading long notes and
 * assistant/web answers. Never persists text, URLs, tokens or API credentials.
 * Every page is requested by the same authenticated Wear node that created it.
 */
public final class JarvisWearReplyPages {
    public static final int PAGE_CHARS = 600; // safe Unicode/UTF-8/JSON envelope under 4096 bytes
    private static final int MIN_PAGE_CHARS = 450;
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
        final String watchNode, documentId;
        final List<String> pages;
        long expiry;
        Document(String node,String id,List<String> pages,long expiry) {
            watchNode=node;documentId=id;this.pages=pages;this.expiry=expiry;
        }
    }

    private static final LinkedHashMap<String,Document> documents=new LinkedHashMap<>();

    private JarvisWearReplyPages() {}

    /** Stable Unicode-aware pages. Prefer line/word breaks near a boundary,
     * so the watch doesn't cut notes or citations in the middle of a word. */
    private static List<String> paginate(String input) {
        ArrayList<String> pages=new ArrayList<>();
        int cursor=0;
        while(cursor<input.length()) {
            int remaining=input.codePointCount(cursor,input.length());
            if(remaining<=PAGE_CHARS) {
                pages.add(input.substring(cursor));
                break;
            }
            int limit=input.offsetByCodePoints(cursor,PAGE_CHARS);
            int minimum=input.offsetByCodePoints(cursor,MIN_PAGE_CHARS);
            int cut=limit;
            // Prefer a paragraph/line break. Otherwise split at whitespace.
            for(int i=limit-1;i>=minimum;i--) {
                char ch=input.charAt(i);
                if(ch=='\n') {
                    cut=i+1;
                    break;
                }
            }
            if(cut==limit) {
                for(int i=limit-1;i>=minimum;i--) {
                    if(Character.isWhitespace(input.charAt(i))) {
                        cut=i+1;
                        break;
                    }
                }
            }
            pages.add(input.substring(cursor,cut));
            cursor=cut;
        }
        if(pages.isEmpty())pages.add("");
        return pages;
    }

    public static synchronized Page save(String watchNode,String requestId,String reply) {
        if(watchNode==null || watchNode.isEmpty() ||
           !JarvisWearProtocol.validId(requestId))return null;
        long now=System.currentTimeMillis();
        purge(now);
        String body=reply==null?"":reply;
        if(body.length()>MAX_CHARS) {
            // Don't cut through a UTF-16 surrogate pair.
            int end=MAX_CHARS;
            if(Character.isHighSurrogate(body.charAt(end-1)) &&
               Character.isLowSurrogate(body.charAt(end))) end--;
            body=body.substring(0,end)+"\n… Відповідь занадто довга для годинника.";
        }
        Document doc=new Document(watchNode,requestId,paginate(body),now+TTL_MS);
        documents.put(key(watchNode,requestId),doc);
        while(documents.size()>MAX_DOCUMENTS) {
            String oldest=documents.keySet().iterator().next();
            documents.remove(oldest);
        }
        return read(watchNode,requestId,0);
    }

    public static synchronized Page read(String watchNode,String documentId,int index) {
        long now=System.currentTimeMillis();
        purge(now);
        if(watchNode==null || !JarvisWearProtocol.validId(documentId))
            return null;
        Document doc=documents.get(key(watchNode,documentId));
        if(doc==null || index<0 || index>=doc.pages.size())return null;
        doc.expiry=now+TTL_MS; // Active reading doesn't expire mid-document.
        return new Page(documentId,index,doc.pages.size(),doc.pages.get(index));
    }

    private static String key(String watchNode,String id) {return watchNode+":"+id;}

    private static void purge(long now) {
        java.util.Iterator<Map.Entry<String,Document>> it=documents.entrySet().iterator();
        while(it.hasNext())if(it.next().getValue().expiry<now)it.remove();
    }
}
