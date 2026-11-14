package com.andriy.jarvis;

public final class JarvisWearReplyPagesTest {
    private static void yes(boolean ok,String why) {
        if(!ok)throw new AssertionError(why);
    }
    public static void main(String[] args) {
        String id="123e4567-e89b-12d3-a456-426614174000";
        StringBuilder original=new StringBuilder();
        for(int i=0;i<800;i++)original.append("Нотатка ").append(i)
            .append(" 💡 українська мова.\n");
        String all=original.toString();
        JarvisWearReplyPages.Page first=JarvisWearReplyPages.save("watch-1",id,all);
        yes(first!=null,"saved reply");
        yes(first.count>1,"several pages");
        StringBuilder roundTrip=new StringBuilder();
        for(int i=0;i<first.count;i++) {
            JarvisWearReplyPages.Page page=JarvisWearReplyPages.read("watch-1",id,i);
            yes(page!=null && page.index==i,"page position");
            yes(page.text.codePointCount(0,page.text.length())<=
                JarvisWearReplyPages.PAGE_CHARS,"bounded page");
            roundTrip.append(page.text);
        }
        yes(all.equals(roundTrip.toString()),"no missing or repeated emoji");
        yes(JarvisWearReplyPages.read("watch-2",id,0)==null,
            "another watch cannot read notes");
        yes(JarvisWearReplyPages.read("watch-1",id,-1)==null,
            "negative page rejected");
        yes(JarvisWearReplyPages.read("watch-1",id,first.count)==null,
            "past-last page rejected");
        yes(JarvisWearReplyPages.save("watch-1","not-a-uuid","x")==null,
            "reject invalid document id");
        // Paragraph-aware chunking should not alter the source.
        String paragraphs="A. ".repeat(160)+"\n"+"Б. ".repeat(170)+"\n";
        String id2="123e4567-e89b-12d3-a456-426614174001";
        JarvisWearReplyPages.Page paragraphsFirst=
            JarvisWearReplyPages.save("watch-1",id2,paragraphs);
        StringBuilder paragraphsActual=new StringBuilder();
        for(int i=0;i<paragraphsFirst.count;i++) {
            JarvisWearReplyPages.Page page=JarvisWearReplyPages.read("watch-1",id2,i);
            yes(page!=null,"paragraph page available");
            paragraphsActual.append(page.text);
        }
        yes(paragraphs.equals(paragraphsActual.toString()),
            "paragraph-aware split preserves every character");

        // Extremely long answers must be explicitly marked as shortened.
        String oversized="🙂".repeat(33000);
        String id3="123e4567-e89b-12d3-a456-426614174002";
        JarvisWearReplyPages.Page truncated=
            JarvisWearReplyPages.save("watch-1",id3,oversized);
        StringBuilder truncatedText=new StringBuilder();
        for(int i=0;i<truncated.count;i++)
            truncatedText.append(JarvisWearReplyPages.read("watch-1",id3,i).text);
        yes(truncatedText.toString().contains("Відповідь занадто довга"),
            "long answer explicitly marked rather than silently dropped");
        yes(!truncatedText.toString().contains("\uFFFD"),
            "no replacement characters in long emoji reply");
        System.out.println("PASS JarvisWearReplyPagesTest");
    }
}
