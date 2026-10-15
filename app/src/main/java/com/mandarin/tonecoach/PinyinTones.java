package com.mandarin.tonecoach;

import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Pinyin4j provides dictionary candidates; polyphonic characters require user review. */
public final class PinyinTones {
    public static final class Reading {
        public final String[] syllables;
        public final int[] tones;
        Reading(String[] syllables, int[] tones) { this.syllables=syllables;this.tones=tones; }
    }
    public static Reading parse(String text) {
        List<String> syl=new ArrayList<>();List<Integer> tones=new ArrayList<>();
        HanyuPinyinOutputFormat fmt=new HanyuPinyinOutputFormat();
        fmt.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        fmt.setToneType(HanyuPinyinToneType.WITH_TONE_NUMBER);
        fmt.setVCharType(HanyuPinyinVCharType.WITH_V);
        if (text==null) text="";
        // Accept numbered Hanyu pinyin as well (ni3 hao3).
        if (!text.matches("(?s).*[^a-zA-Z0-9\\s:üÜvV\\.,!?-].*")) {
            Matcher match=Pattern.compile("(?i)([a-züv:]+)([1-5])").matcher(text);
            while(match.find()){syl.add(match.group(1));tones.add(Integer.parseInt(match.group(2)));}
        }
        if (!syl.isEmpty()) {
            String[] s=syl.toArray(new String[0]);int[] t=new int[tones.size()];
            for(int i=0;i<t.length;i++)t[i]=tones.get(i);
            return new Reading(s,t);
        }
        for (int i=0;i<text.length();) {
            int cp=text.codePointAt(i);i+=Character.charCount(cp);
            if(cp>=0x3400 && cp<=0x9FFF && cp<=0xFFFF) {
                String syllable="?";int tone=5;
                try {
                    String[] arr=PinyinHelper.toHanyuPinyinStringArray((char)cp, fmt);
                    if(arr!=null && arr.length>0){
                        String first=arr[0];
                        if(first.length()>0 && Character.isDigit(first.charAt(first.length()-1))){
                            tone=first.charAt(first.length()-1)-'0';
                            first=first.substring(0,first.length()-1);
                        }
                        syllable=first;
                    }
                } catch(Exception ignored) {}
                syl.add(syllable);tones.add(tone>=1&&tone<=5?tone:5);
            }
        }
        String[] syllables=syl.toArray(new String[0]);int[] numbers=new int[tones.size()];
        for(int i=0;i<numbers.length;i++)numbers[i]=tones.get(i);
        return new Reading(syllables,numbers);
    }
    public static String numbers(int[] tones){StringBuilder b=new StringBuilder();for(int n:tones){if(b.length()>0)b.append(' ');b.append(n);}return b.toString();}
    public static int[] corrected(String text,int[] defaults){
        if(text==null||text.trim().isEmpty())return defaults.clone();
        String squashed=text.replaceAll("[^1-5]", "");
        if(squashed.length()!=defaults.length)return defaults.clone();
        int[] result=new int[defaults.length];for(int i=0;i<result.length;i++)result[i]=squashed.charAt(i)-'0';
        return result;
    }
    public static int[] sandhi(String hanzi,int[] tones){
        // Rules are informative approximations; context and morphology can differ.
        int[] result=tones.clone();
        if(hanzi==null||hanzi.length()!=result.length)return result;
        for(int i=0;i<result.length-1;i++) {
            if(result[i]==3 && result[i+1]==3)result[i]=2;
            if(hanzi.charAt(i)=='不' && result[i+1]==4)result[i]=2;
            if(hanzi.charAt(i)=='一' && result[i+1]==4)result[i]=2;
            else if(hanzi.charAt(i)=='一' && result[i+1]>=1&&result[i+1]<=3)result[i]=4;
        }
        return result;
    }
    private PinyinTones(){}
}
