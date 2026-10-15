package com.mandarin.tonecoach;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/** Fully local Android bridge. UI is packaged HTML/CSS and never connects to a server. */
public class MainActivity extends Activity {
    private static final int SAMPLE_RATE=16000, MAX_SECONDS=14;
    private final short[] pcm=new short[SAMPLE_RATE*MAX_SECONDS];
    private final OfflineModel recognition=new OfflineModel();
    private WebView web;
    private TextToSpeech tts;
    private AudioRecord recorder;
    private volatile boolean recording=false, processing=false;
    private volatile int sampleCount=0;
    private Thread captureThread;
    private String currentText="你好", currentToneOverride="";
    private boolean pageReady=false;
    private String modelStatus="loading",modelMessage="Локальная модель загружается";

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(10,14,30));
        getWindow().setNavigationBarColor(Color.rgb(10,14,30));
        web=new WebView(this);
        web.setBackgroundColor(Color.rgb(10,14,30));
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(false);
        web.getSettings().setAllowFileAccess(true);
        web.getSettings().setAllowFileAccessFromFileURLs(false);
        web.getSettings().setAllowUniversalAccessFromFileURLs(false);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public void onPageFinished(WebView view,String url){pageReady=true;sendBoot();}
        });
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
        tts=new TextToSpeech(this,result->{
            if(result==TextToSpeech.SUCCESS && tts!=null){
                int status=tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
                emit("tts", object("available",status!=TextToSpeech.LANG_NOT_SUPPORTED&&status!=TextToSpeech.LANG_MISSING_DATA));
            }
        });
        recognition.initialize(this,(status,message)->{
            modelStatus=status;modelMessage=message;
            emit("model",object("status",status,"message",message));
        });
    }
    private SharedPreferences prefs(){return getSharedPreferences("mtc_v2",MODE_PRIVATE);}
    private JSONObject object(Object... args){JSONObject obj=new JSONObject();try{for(int i=0;i<args.length;i+=2)obj.put(String.valueOf(args[i]),args[i+1]);}catch(JSONException ignored){}return obj;}
    private JSONArray stored(String key){try{return new JSONArray(prefs().getString(key,"[]"));}catch(Exception e){return new JSONArray();}}
    private void save(String key,JSONArray data){prefs().edit().putString(key,data.toString()).apply();}
    private void emit(String type,JSONObject payload){
        if(web==null||!pageReady)return;
        JSONObject msg=object("type",type,"payload",payload);
        runOnUiThread(()->{if(web!=null)web.evaluateJavascript("window.MTC&&window.MTC.onNative("+JSONObject.quote(msg.toString())+")",null);});
    }
    private void sendBoot(){
        JSONObject state=object("saved",stored("saved"),"history",stored("history"),"modelStatus",modelStatus,"modelMessage",modelMessage,"recording",recording,"quizAttempts",prefs().getInt("quizAttempts",0),"quizCorrect",prefs().getInt("quizCorrect",0));
        emit("boot",state);
        emitPreview(currentText);
    }
    private void emitPreview(String text){
        String val=text==null?"":text.trim();
        PinyinTones.Reading reading=PinyinTones.parse(val);
        int[] sandhi=PinyinTones.sandhi(val,reading.tones);
        JSONArray syllables=new JSONArray(),tones=new JSONArray(),base=new JSONArray();
        for(String item:reading.syllables)syllables.put(item);
        for(int item:sandhi)tones.put(item);
        for(int item:reading.tones)base.put(item);
        emit("preview",object("text",val,"syllables",syllables,"tones",tones,"dictionaryTones",base,"toneString",PinyinTones.numbers(sandhi)));
    }
    private void speak(String text){
        if(tts==null){emit("notice",object("text","Китайский TTS ещё не загружен."));return;}
        if(text==null||text.isEmpty())return;
        tts.setSpeechRate(.84f);
        int result=tts.speak(text,TextToSpeech.QUEUE_FLUSH,null,"mandarin_sample");
        if(result==TextToSpeech.ERROR)emit("notice",object("text","Установи китайский голос в настройках синтеза речи Android."));
    }
    private void start(String text,String toneOverride){
        if(processing){emit("notice",object("text","Подожди окончания анализа записи."));return;}
        if(recording){stop();return;}
        String s=text==null?"":text.trim();
        PinyinTones.Reading reading=PinyinTones.parse(s);
        if(s.isEmpty()||reading.tones.length==0){emit("notice",object("text","Введи китайские иероглифы или пиньинь с цифрами тонов."));return;}
        if(reading.tones.length>18){emit("notice",object("text","Для точного разбора введи до 18 слогов."));return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},123);
            emit("notice",object("text","Разреши доступ к микрофону и нажми запись ещё раз."));
            return;
        }
        currentText=s;currentToneOverride=toneOverride==null?"":toneOverride;
        if(tts!=null)tts.stop();
        int min=AudioRecord.getMinBufferSize(SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
        try{
            recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,SAMPLE_RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(4096,min));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("AudioRecord не инициализирован");
            sampleCount=0;recorder.startRecording();recording=true;
            emit("recording",object("active",true));
            captureThread=new Thread(()->{
                short[] tmp=new short[2048];long lastSignal=0;
                while(recording&&sampleCount<pcm.length){
                    AudioRecord r=recorder;if(r==null)break;
                    int max=Math.min(tmp.length,pcm.length-sampleCount);
                    int n;try{n=r.read(tmp,0,max);}catch(Exception e){break;}
                    if(n<=0)break;
                    System.arraycopy(tmp,0,pcm,sampleCount,n);sampleCount+=n;
                    long now=System.currentTimeMillis();
                    if(now-lastSignal>=110){
                        double power=0;for(int i=0;i<n;i++)power+=tmp[i]*(double)tmp[i];
                        double rms=Math.sqrt(power/n)/9000;
                        emit("level",object("level",Math.max(0,Math.min(1,rms)),"seconds",sampleCount/(double)SAMPLE_RATE));
                        lastSignal=now;
                    }
                }
                if(recording)runOnUiThread(this::stop);
            },"mic-capture");
            captureThread.start();
        }catch(Exception e){recording=false;
            if(recorder!=null){try{recorder.release();}catch(Exception ignored){}}
            recorder=null;emit("notice",object("text","Микрофон недоступен: "+e.getMessage()));
        }
    }
    private void stop(){
        if(!recording)return;
        recording=false;
        AudioRecord r=recorder;recorder=null;
        if(r!=null){try{r.stop();}catch(Exception ignored){}try{r.release();}catch(Exception ignored){}}
        Thread t=captureThread;
        if(t!=null&&t!=Thread.currentThread())try{t.join(400);}catch(InterruptedException ignored){}
        int len=sampleCount;
        emit("recording",object("active",false));
        if(len<3200){emit("notice",object("text","Запись слишком короткая, попробуй ещё раз."));return;}
        processing=true;
        emit("working",object("active",true));
        final short[] audio=new short[len];System.arraycopy(pcm,0,audio,0,len);
        final String text=currentText;
        final PinyinTones.Reading readings=PinyinTones.parse(text);
        final int[] target=PinyinTones.corrected(currentToneOverride,PinyinTones.sandhi(text,readings.tones));
        new Thread(()->{
            try{
                PitchEngine.Result result=PitchEngine.analyze(audio,audio.length,target);
                String spoken=recognition.recognize(audio,audio.length);
                int matches=0;
                if(!spoken.isEmpty()){
                    int common=lcs(text.replaceAll("\\s+",""),spoken.replaceAll("\\s+",""));
                    matches=(int)Math.round(200.0*common/Math.max(1,text.length()+spoken.length()));
                }
                JSONObject payload=object("text",text,"recognized",spoken,"recognitionReady",recognition.isReady(),"recognitionSimilarity",matches,
                    "shapeScore",result.shapeScore,"voicedPercent",result.voicedPercent,"note",result.note,"seconds",len/(double)SAMPLE_RATE);
                JSONArray actual=new JSONArray(),ideal=new JSONArray(),hz=new JSONArray(),segments=new JSONArray();
                int stride=Math.max(1,result.normalized.length/180);
                for(int i=0;i<result.normalized.length;i+=stride){
                    actual.put(round(result.normalized[i]));ideal.put(round(result.target[i]));hz.put(round(result.pitchHz[i]));
                }
                for(int i=0;i<result.segments.length;i++){
                    PitchEngine.Segment seg=result.segments[i];
                    segments.put(object("syllable",i<readings.syllables.length?readings.syllables[i]:"?","expected",seg.expected,
                        "guessed",seg.guessed,"score",seg.score,"confident",seg.confident));
                }
                payload.put("curve",actual);payload.put("targetCurve",ideal);payload.put("hertz",hz);payload.put("segments",segments);
                JSONArray history=stored("history");
                JSONObject summary=object("id",System.currentTimeMillis(),"text",text,"score",result.shapeScore,"recognized",spoken,"date",System.currentTimeMillis(),"syllables",target.length,"perTone",segments);
                JSONArray revised=new JSONArray();revised.put(summary);
                for(int i=0;i<Math.min(149,history.length());i++)revised.put(history.opt(i));
                save("history",revised);
                emit("analysis",payload);
                emit("history",object("items",revised));
            }catch(Exception e){emit("notice",object("text","Ошибка анализа: "+e.getMessage()));}
            finally{processing=false;emit("working",object("active",false));}
        },"tone-analyzer").start();
    }
    private static int round(float n){if(!Float.isFinite(n))return 0;return (int)Math.round(n*100f)/1;}
    private static int lcs(String a,String b){
        int[][] dp=new int[a.length()+1][b.length()+1];
        for(int i=1;i<=a.length();i++)for(int j=1;j<=b.length();j++)dp[i][j]=a.charAt(i-1)==b.charAt(j-1)?dp[i-1][j-1]+1:Math.max(dp[i-1][j],dp[i][j-1]);
        return dp[a.length()][b.length()];
    }
    private void savePhrase(String text,String customTones){
        String s=text==null?"":text.trim();if(s.isEmpty())return;
        JSONArray existing=stored("saved"),out=new JSONArray();
        out.put(object("text",s,"tones",customTones==null?"":customTones,"date",System.currentTimeMillis()));
        for(int i=0;i<Math.min(existing.length(),99);i++){
            JSONObject item=existing.optJSONObject(i);
            if(item!=null&&!s.equals(item.optString("text")))out.put(item);
        }
        save("saved",out);emit("saved",object("items",out));emit("notice",object("text","Добавлено в мою коллекцию"));
    }
    private void removePhrase(String text){
        JSONArray original=stored("saved"),out=new JSONArray();
        for(int i=0;i<original.length();i++){
            JSONObject item=original.optJSONObject(i);if(item!=null&&!item.optString("text").equals(text))out.put(item);
        }
        save("saved",out);emit("saved",object("items",out));
    }
    private void quizAnswer(boolean correct){
        int attempts=prefs().getInt("quizAttempts",0)+1;
        int wins=prefs().getInt("quizCorrect",0)+(correct?1:0);
        prefs().edit().putInt("quizAttempts",attempts).putInt("quizCorrect",wins).apply();
        emit("quizStats",object("attempts",attempts,"correct",wins));
    }
    public class Bridge {
        @JavascriptInterface public void ready(){runOnUiThread(MainActivity.this::sendBoot);}
        @JavascriptInterface public void preview(String text){runOnUiThread(()->emitPreview(text));}
        @JavascriptInterface public void speak(String text){runOnUiThread(()->MainActivity.this.speak(text));}
        @JavascriptInterface public void record(String text,String tones){runOnUiThread(()->{if(recording)stop();else start(text,tones);});}
        @JavascriptInterface public void savePhrase(String text,String tones){runOnUiThread(()->MainActivity.this.savePhrase(text,tones));}
        @JavascriptInterface public void removePhrase(String text){runOnUiThread(()->MainActivity.this.removePhrase(text));}
        @JavascriptInterface public void quizAnswer(boolean correct){runOnUiThread(()->MainActivity.this.quizAnswer(correct));}
        @JavascriptInterface public void clearHistory(){runOnUiThread(()->{save("history",new JSONArray());emit("history",object("items",new JSONArray()));});}
    }
    @Override protected void onPause(){if(recording)stop();super.onPause();}
    @Override protected void onDestroy(){
        recording=false;
        if(recorder!=null){try{recorder.stop();recorder.release();}catch(Exception ignored){}}
        if(tts!=null){tts.stop();tts.shutdown();}
        recognition.close();
        if(web!=null){web.removeJavascriptInterface("Native");web.destroy();web=null;}
        super.onDestroy();
    }
}
