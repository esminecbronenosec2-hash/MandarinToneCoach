package com.mandarin.tonecoach;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Local DSP F0 pitch tracker and shape comparator.
 * Returns an estimated pitch-shape match, NOT phonetic correctness or an ASR confidence.
 * Uses a normalized difference (YIN-inspired) estimator with voiced/unvoiced gating.
 */
public final class PitchEngine {
    public static final int RATE=16000;
    public static final class Segment {
        public final int expected, guessed, score;
        public final boolean confident;
        public Segment(int e,int g,int s,boolean c){expected=e;guessed=g;score=s;confident=c;}
    }
    public static final class Result {
        public final float[] pitchHz, normalized, target;
        public final Segment[] segments;
        public final int shapeScore, voicedPercent;
        public final String note;
        Result(float[] h,float[] n,float[] t,Segment[] s,int score,int voiced,String note){pitchHz=h;normalized=n;target=t;segments=s;shapeScore=score;voicedPercent=voiced;this.note=note;}
    }
    public static float[] template(int tone,int n){
        float[] a=new float[n];
        for(int i=0;i<n;i++){
            float x=n<2?0f:(float)i/(n-1);
            switch(tone){
                case 1: a[i]=0; break;
                case 2: a[i]=-3f+6f*x;break;
                case 3: a[i]=x<0.55f?2f-7f*x/.55f:-5f+7f*(x-.55f)/.45f;break;
                case 4: a[i]=3.5f-7f*x;break;
                default: a[i]=0;break;
            }
        }
        return a;
    }
    public static Result analyze(short[] data,int length,int[] targetTones){
        if(data==null || length<3200 || targetTones==null || targetTones.length==0)
            return empty("Слишком короткая запись.");
        int frame=640,hop=320;
        int frames=1+(length-frame)/hop;
        float[] contour=new float[frames];
        int voiced=0;
        for(int f=0;f<frames;f++){
            contour[f]=estimate(data,f*hop,frame);
            if(contour[f]>0)voiced++;
        }
        int voicedPct=Math.round(100f*voiced/frames);
        if(voiced<Math.max(5,frames/6))return empty("Не удалось уверенно выделить голос. Запиши речь без фонового шума.");
        float median=medianPositive(contour);
        float[] semitones=new float[frames];
        for(int i=0;i<frames;i++)semitones[i]=contour[i]>0?(float)(12*Math.log(contour[i]/median)/Math.log(2)):Float.NaN;
        fillGaps(semitones);
        int syllables=targetTones.length;
        Segment[] segments=new Segment[syllables];
        float[] ideal=new float[frames];
        double scoredSum=0;int validCount=0;
        for(int k=0;k<syllables;k++){
            int from=(int)((long)k*frames/syllables);
            int to=(int)((long)(k+1)*frames/syllables);
            if(to<=from)to=from+1;
            int window=to-from;
            float[] one=Arrays.copyOfRange(semitones,from,to);
            int realN=0;for(int f=from;f<to;f++)if(contour[f]>0)realN++;
            boolean reliable=window>=6 && realN>=4 && realN>=window*.38f;
            double[] scores=new double[4];
            for(int tone=1;tone<=4;tone++)scores[tone-1]=shapeCost(one,template(tone,window));
            int bestTone=1;double best=Double.POSITIVE_INFINITY;
            for(int tone=1;tone<=4;tone++)if(scores[tone-1]<best){best=scores[tone-1];bestTone=tone;}
            int expected=targetTones[k];
            double wanted=expected>=1&&expected<=4?scores[expected-1]:0;
            int score=expected==5 ? -1 : reliable?clamp((int)Math.round(100-18*wanted),0,100):-1;
            if(score>=0){scoredSum+=score;validCount++;}
            segments[k]=new Segment(expected,reliable?bestTone:0,score,reliable);
            float[] template=template(expected,window);
            // Anchor the target to the median pitch of the user's voice for an interpretable overlay.
            for(int j=from;j<to;j++) ideal[j]=template[j-from];
        }
        String note;
        if(syllables>1)note="Границы слогов оценены приблизительно по времени; для слитных фраз баллы условные. Тоновые изменения и ударение зависят от контекста.";
        else note="Оценка описывает форму F0, а не согласные, гласные или смысл произнесённого.";
        int score=validCount>0?(int)Math.round(scoredSum/validCount):-1;
        return new Result(contour,semitones,ideal,segments,score,voicedPct,note);
    }
    private static Result empty(String msg){return new Result(new float[0],new float[0],new float[0],new Segment[0],0,0,msg);}
    private static float estimate(short[] pcm,int start,int size){
        double mean=0,power=0;
        for(int i=0;i<size;i++)mean+=pcm[start+i];
        mean/=size;
        double[] buf=new double[size];
        for(int i=0;i<size;i++){double y=pcm[start+i]-mean;buf[i]=y;power+=y*y;}
        if(Math.sqrt(power/size)<170)return 0;
        int min=32,max=267;
        double[] d=new double[max+1];
        for(int tau=min;tau<=max;tau++){
            double sum=0;
            for(int i=0;i<size-tau;i++){double v=buf[i]-buf[i+tau];sum+=v*v;}
            d[tau]=sum;
        }
        double cumulative=0;int chosen=0;double best=1;
        for(int tau=min;tau<=max;tau++){
            cumulative+=d[tau];
            double norm=cumulative>0?d[tau]*((tau-min)+1)/cumulative:1;
            if(norm<.15){chosen=tau;best=norm;break;}
            if(norm<best){best=norm;chosen=tau;}
        }
        if(chosen==0||best>.29)return 0;
        if(chosen>min && chosen<max){
            // Lag parabolic interpolation on raw difference function.
            double a=d[chosen-1],b=d[chosen],c=d[chosen+1];
            double delta=(a-c)/(2*(a-2*b+c)+1e-8);
            delta=Math.max(-.5,Math.min(.5,delta));
            return (float)(RATE/(chosen+delta));
        }
        return RATE/(float)chosen;
    }
    private static float medianPositive(float[] x){
        float[] vals=new float[x.length];int n=0;for(float v:x)if(v>0)vals[n++]=v;
        if(n==0)return 160;Arrays.sort(vals,0,n);return vals[n/2];
    }
    private static void fillGaps(float[] a){
        int prev=-1;
        for(int i=0;i<a.length;i++)if(!Float.isNaN(a[i])){
            if(prev<0)Arrays.fill(a,0,i,a[i]);
            else if(i-prev>1){float start=a[prev],end=a[i];for(int j=prev+1;j<i;j++)a[j]=start+(end-start)*(j-prev)/(i-prev);}
            prev=i;
        }
        if(prev>=0)Arrays.fill(a,prev+1,a.length,a[prev]);
    }
    private static double shapeCost(float[] a,float[] b){
        if(a.length<2)return 50;
        double meanA=0,meanB=0;for(int i=0;i<a.length;i++){meanA+=a[i];meanB+=b[i];}meanA/=a.length;meanB/=a.length;
        double err=0;for(int i=0;i<a.length;i++){double delta=(a[i]-meanA)-(b[i]-meanB);err+=delta*delta;}
        return Math.sqrt(err/a.length);
    }
    private static int clamp(int x,int lo,int hi){return Math.min(hi,Math.max(lo,x));}
    private PitchEngine(){}
}
