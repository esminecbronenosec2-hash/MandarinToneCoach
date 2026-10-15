package com.mandarin.tonecoach;
import org.junit.Test;
import static org.junit.Assert.*;
public class PitchEngineTest {
    @Test public void templatesSlope() {
        float[] rising=PitchEngine.template(2,40), falling=PitchEngine.template(4,40);
        assertTrue(rising[39]>rising[0]);assertTrue(falling[39]<falling[0]);
    }
    @Test public void silenceDoesNotProduceFalseScore() {
        PitchEngine.Result r=PitchEngine.analyze(new short[16000],16000,new int[]{2});
        assertEquals(0,r.shapeScore);assertEquals(0,r.voicedPercent);
    }
    @Test public void flatSineProducesPitch() {
        short[] a=new short[16000];
        for(int i=0;i<a.length;i++)a[i]=(short)(9000*Math.sin(2*Math.PI*200*i/16000.0));
        PitchEngine.Result r=PitchEngine.analyze(a,a.length,new int[]{1});
        assertTrue(r.voicedPercent>60);
        assertEquals(1,r.segments.length);
    }
}
