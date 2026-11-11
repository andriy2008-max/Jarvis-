package com.andriy.jarvis;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** Pixel drawings based on the user's phone and cupped-ear references. */
public final class JarvisPixelPhone {
    private static final int CYAN = Color.rgb(127,231,255);
    private static final int LIGHT = Color.rgb(217,254,255);
    private static final int NAVY = Color.rgb(6,22,41);
    private static final int GREEN = Color.rgb(107,232,191);
    private JarvisPixelPhone() {}

    private static void rect(Canvas c, Paint p, float ox, float oy, float u,
                             int x, int y, int w, int h) {
        c.drawRect(ox+x*u,oy+y*u,ox+(x+w)*u,oy+(y+h)*u,p);
    }

    // Ear on the left, upright cupped hand on the right: no font/emoji fallback.
    private static final String[] LISTEN = {
        "....................##........",
        "...................####.......",
        "................##.####.##....",
        "...............###########....",
        "...............###########.##.",
        ".....######....###############",
        "...##########..###############",
        "..####....####.###############",
        ".###........##################",
        ".##...####...#################",
        "##..########..################",
        "##..##....##..################",
        "##..##....##..################",
        "##...###..##..################",
        "##.....##.##..################",
        "##..##..####..################",
        "##.####..###..################",
        "##.##.##..##..################",
        "##..#..##.##..################",
        "##....##..##..################",
        ".##..##..##...################",
        ".##.##..###...################",
        "..####.###....################",
        "...######.....################",
        "....####......################",
        "...............##############.",
        "................############..",
        ".................##########..."
    };

    public static void drawListening(Canvas c, Paint p, float cx, float cy, float radius) {
        boolean aa = p.isAntiAlias();
        p.setAntiAlias(false);
        p.setStyle(Paint.Style.FILL);
        float u = radius*2f/30f;
        float ox=cx-15*u, oy=cy-14*u;
        p.setColor(CYAN);
        for(int y=0;y<LISTEN.length;y++) for(int x=0;x<LISTEN[y].length();x++)
            if(LISTEN[y].charAt(x)=='#') rect(c,p,ox,oy,u,x,y,1,1);
        // Dark separations make all four raised fingers distinct at small sizes.
        p.setColor(NAVY);
        rect(c,p,ox,oy,u,18,4,1,12);
        rect(c,p,ox,oy,u,22,4,1,12);
        rect(c,p,ox,oy,u,26,6,1,11);
        rect(c,p,ox,oy,u,15,10,1,10);
        p.setAntiAlias(aa);
    }

    public static void draw(Canvas c, Paint p, float cx, float cy, float radius,
                            boolean listening, JarvisControlFeedback.Phase phase, long now) {
        boolean aa=p.isAntiAlias();
        p.setAntiAlias(false);
        p.setStyle(Paint.Style.FILL);
        float u=radius*1.82f/32f, ox=cx-10*u, oy=cy-16*u;
        // Stepped rectangular silhouette, earpiece, large screen and hollow home key.
        p.setColor(CYAN);
        rect(c,p,ox,oy,u,4,1,12,1);
        rect(c,p,ox,oy,u,3,2,14,1);
        rect(c,p,ox,oy,u,2,3,16,26);
        rect(c,p,ox,oy,u,3,29,14,1);
        rect(c,p,ox,oy,u,4,30,12,1);
        p.setColor(Color.rgb(13,46,84));
        rect(c,p,ox,oy,u,4,2,12,28);
        rect(c,p,ox,oy,u,3,3,14,26);
        p.setColor(LIGHT);
        rect(c,p,ox,oy,u,9,3,2,1);
        p.setColor(NAVY);
        rect(c,p,ox,oy,u,4,5,12,18);
        p.setColor(CYAN);
        rect(c,p,ox,oy,u,9,25,2,1);
        rect(c,p,ox,oy,u,8,26,1,2);
        rect(c,p,ox,oy,u,11,26,1,2);
        rect(c,p,ox,oy,u,9,28,2,1);
        float screenY=oy+14*u;
        if(phase==JarvisControlFeedback.Phase.RUNNING) {
            int[][] points={{9,9},{12,10},{13,13},{12,16},{9,17},{6,16},{5,13},{6,10}};
            int frame=(int)((now/100L)%8);
            for(int i=0;i<8;i++) {
                int age=(frame-i+8)%8;
                p.setColor(Color.argb(255-age*25,127,231,255));
                rect(c,p,ox,oy,u,points[i][0],points[i][1],2,2);
            }
        } else if(phase==JarvisControlFeedback.Phase.SUCCESS) {
            p.setColor(GREEN);
            int[][] pixels={{5,14},{6,15},{7,16},{8,15},{9,14},{10,13},{11,12},{12,11}};
            for(int[] point:pixels) rect(c,p,ox,oy,u,point[0],point[1],2,2);
        } else if(phase==JarvisControlFeedback.Phase.FAILURE) {
            p.setColor(Color.rgb(255,133,145));
            for(int i=0;i<6;i++) {
                rect(c,p,ox,oy,u,6+i,11+i,2,2);
                rect(c,p,ox,oy,u,11-i,11+i,2,2);
            }
        } else if(listening) {
            drawListening(c,p,cx,screenY,5.6f*u);
        }
        p.setAntiAlias(aa);
    }
}
