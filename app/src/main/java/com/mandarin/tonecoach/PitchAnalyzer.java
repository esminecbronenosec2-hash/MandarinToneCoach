package com.mandarin.tonecoach;

import java.util.ArrayList;
import java.util.List;

/** Offline normalized autocorrelation pitch tracker for voiced Mandarin syllables. */
public final class PitchAnalyzer {
    public static final int SAMPLE_RATE = 16000;
    public static float[] contour(short[] pcm, int length) {
        int frame = 640, hop = 160;
        List<Float> pitches = new ArrayList<>();
        for (int pos = 0; pos + frame <= length; pos += hop) {
            double mean = 0, energy = 0;
            for (int i = 0; i < frame; i++) mean += pcm[pos+i];
            mean /= frame;
            double[] x = new double[frame];
            for (int i = 0; i < frame; i++) { x[i] = pcm[pos+i] - mean; energy += x[i]*x[i]; }
            if (Math.sqrt(energy/frame) < 240) { pitches.add(Float.NaN); continue; }
            int bestLag = 0; double best = 0;
            for (int lag = 36; lag <= 160; lag++) {
                double numerator=0, a=0, b=0;
                for (int i=0; i<frame-lag; i++) { numerator += x[i]*x[i+lag]; a += x[i]*x[i]; b += x[i+lag]*x[i+lag]; }
                double corr = numerator / (Math.sqrt(a*b)+1e-9);
                if (corr > best) { best = corr; bestLag = lag; }
            }
            pitches.add(best > .64 && bestLag > 0 ? (float)SAMPLE_RATE/bestLag : Float.NaN);
        }
        List<Float> valid = new ArrayList<>();
        for (float p:pitches) if (!Float.isNaN(p)) valid.add(p);
        if (valid.size() < 8) return new float[0];
        // Remove implausible octave jumps by reference to the median pitch.
        valid.sort(Float::compare);
        float median = valid.get(valid.size()/2);
        List<Float> cleaned = new ArrayList<>();
        for (float p:pitches) if (!Float.isNaN(p) && p > median*.65 && p < median*1.55) cleaned.add(p);
        if (cleaned.size()<8) return new float[0];
        float[] result = new float[cleaned.size()];
        for (int i=0;i<result.length;i++) result[i]=(float)(12*Math.log(cleaned.get(i)/median)/Math.log(2));
        return result;
    }
    public static float[] target(int tone, int n) {
        float[] a = new float[n];
        for(int i=0;i<n;i++) {
            float t = n==1?0:(float)i/(n-1);
            switch(tone) {
                case 1: a[i]=0; break;
                case 2: a[i]=-3+6*t; break;
                case 3: a[i]= t<.55f ? 2-7*t/.55f : -5+7*(t-.55f)/.45f; break;
                case 4: a[i]=4-8*t; break;
                default: a[i]=0;
            }
        }
        return a;
    }
    public static int score(float[] actual, int tone) {
        if(actual.length<8) return 0;
        if(tone==5) return 65; // neutral tone needs duration/context; do not claim precision
        int n=32; float[] sampled=new float[n];
        for(int i=0;i<n;i++) {
            float idx=(float)i*(actual.length-1)/(n-1); int lo=(int)idx; int hi=Math.min(lo+1,actual.length-1);
            sampled[i]=actual[lo]+(actual[hi]-actual[lo])*(idx-lo);
        }
        float[] ideal=target(tone,n);
        // Compare contour shapes, accounting for speaker register and scale.
        float ma=0,mt=0;
        for(int i=0;i<n;i++){ma+=sampled[i];mt+=ideal[i];} ma/=n;mt/=n;
        double err=0;
        for(int i=0;i<n;i++){double d=(sampled[i]-ma)-(ideal[i]-mt);err+=d*d;}
        return Math.max(0,Math.min(100,(int)Math.round(100-14*Math.sqrt(err/n))));
    }
}
