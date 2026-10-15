package com.mandarin.tonecoach;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    private final String[] hanzi={"妈","麻","马","骂","吗","你好","谢谢","中国","老师","朋友","我很好","你好吗"};
    private final String[] pinyin={"mā","má","mǎ","mà","ma","nǐ hǎo","xiè xie","zhōng guó","lǎo shī","péng you","wǒ hěn hǎo","nǐ hǎo ma"};
    private final String[] meanings={"мама","конопля","лошадь","ругать","вопросительная частица","привет","спасибо","Китай","учитель","друг","у меня всё хорошо","как дела?"};
    private final int[] tones={1,2,3,4,5,3,4,1,3,2,3,3};
    private final int bg=Color.rgb(15,20,38), surface=Color.rgb(29,36,60), accent=Color.rgb(124,111,255), white=Color.WHITE;
    private LinearLayout root, body; private TextView status; private PitchView pitchView;
    private TextToSpeech tts; private AudioRecord recorder; private Thread recordThread; private volatile boolean recording=false;
    private short[] audio=new short[16000*4]; private int count=0, index=0, correct=0, quizCount=0; private int mode=0;
    private float[] lastContour=new float[0]; private final Random random=new Random();
    @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);quizCount=getPreferences(0).getInt("quiz",0);correct=getPreferences(0).getInt("correct",0);tts=new TextToSpeech(this, s->{if(s==TextToSpeech.SUCCESS && tts!=null)tts.setLanguage(Locale.SIMPLIFIED_CHINESE);});show(0);}
    private TextView text(String s,int size,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(white);v.setPadding(4,9,4,9);if(bold)v.setTypeface(null,1);return v;}
    private void add(TextView v){body.addView(v);}
    private Button button(String label,Runnable click){Button b=new Button(this);b.setText(label);b.setTextColor(white);b.setAllCaps(false);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(accent));b.setOnClickListener(v->click.run());body.addView(b);return b;}
    private void show(int tab){mode=tab;root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);setContentView(root);
        TextView title=text("Mandarin Tone Coach",25,true);title.setPadding(20,25,12,12);root.addView(title);
        HorizontalScrollView nav=new HorizontalScrollView(this);LinearLayout tabs=new LinearLayout(this);tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] names={"Тоны","Слова","Фразы","Викторина","Прогресс"};for(int i=0;i<names.length;i++){final int t=i;Button b=new Button(this);b.setText(names[i]);b.setAllCaps(false);b.setTextColor(white);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(i==tab?accent:surface));b.setOnClickListener(v->show(t));tabs.addView(b);}nav.addView(tabs);root.addView(nav);
        ScrollView scroll=new ScrollView(this);body=new LinearLayout(this);body.setPadding(20,18,20,24);body.setOrientation(LinearLayout.VERTICAL);scroll.addView(body);root.addView(scroll);
        if(tab==0)toneScreen();else if(tab==1)lessonScreen(false);else if(tab==2)lessonScreen(true);else if(tab==3)quizScreen();else progressScreen();
    }
    private void toneScreen(){add(text("Пять тонов путунхуа",22,true));String[] names={"1 — ровный высокий  ˉ","2 — восходящий  ˊ","3 — нисходяще-восходящий  ˇ","4 — резкий нисходящий  ˋ","5 — нейтральный"};for(int i=0;i<5;i++){final int t=i;button(names[i],()->{index=t;lessonScreenContent();});}add(text("Выбери тон, послушай образец и повтори его.",15,false));}
    private void lessonScreen(boolean phrases){index=phrases?5:0;lessonScreenContent();}
    private void lessonScreenContent(){body.removeAllViews();boolean phrases=mode==2;int start=phrases?5:0,end=phrases?hanzi.length:5;if(mode==0){start=0;end=5;}
        add(text(mode==0?"Тренировка тонов":phrases?"Фразы":"Слова",22,true));
        Spinner spinner=new Spinner(this);String[] entries=new String[end-start];for(int i=start;i<end;i++)entries[i-start]=hanzi[i]+"   "+pinyin[i]+" — "+meanings[i];ArrayAdapter<String> adapter=new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,entries);spinner.setAdapter(adapter);spinner.setSelection(Math.max(0,index-start));body.addView(spinner);
        final int offset=start;spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> p){}public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){int next=offset+pos;if(next!=index){index=next;lastContour=new float[0];lessonScreenContent();}}});
        TextView word=text(hanzi[index],56,true);word.setGravity(Gravity.CENTER);add(word);TextView pron=text(pinyin[index]+"   ·   "+meanings[index],19,false);pron.setGravity(Gravity.CENTER);add(pron);
        button("▶ Прослушать",()->{if(tts!=null)tts.speak(hanzi[index],TextToSpeech.QUEUE_FLUSH,null,"sample");});
        pitchView=new PitchView();body.addView(pitchView,new LinearLayout.LayoutParams(-1,dp(220)));
        status=text("Нажми «Записать», произнеси слово и останови запись.",15,false);add(status);
        button("🎙 Записать / Остановить",()->{if(recording)stopRecording();else startRecording();});
        add(text("Оценка приблизительная: график отражает высоту голоса, но не проверяет согласные, гласные и правильность всей фразы.",13,false));
    }
    private void startRecording(){if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},100);status.setText("Разреши доступ к микрофону и повтори попытку.");return;}
        int min=AudioRecord.getMinBufferSize(16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,16000,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(min,4096));if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("AudioRecord unavailable");count=0;recording=true;recorder.startRecording();status.setText("Идёт запись… нажми ещё раз, чтобы остановить.");recordThread=new Thread(()->{short[] buffer=new short[1024];while(recording&&count<audio.length){int n=recorder.read(buffer,0,Math.min(buffer.length,audio.length-count));if(n>0){System.arraycopy(buffer,0,audio,count,n);count+=n;}else if(n<0)break;}runOnUiThread(()->{if(recording)stopRecording();});});recordThread.start();}catch(Exception e){recording=false;status.setText("Не удалось открыть микрофон: "+e.getMessage());}}
    private void stopRecording(){if(!recording)return;recording=false;try{recorder.stop();recorder.release();}catch(Exception ignored){}recorder=null;int len=count;status.setText("Анализируем запись…");new Thread(()->{float[] contour=PitchAnalyzer.contour(audio,len);int score=PitchAnalyzer.score(contour,tones[index]);runOnUiThread(()->{lastContour=contour;pitchView.invalidate();if(score==0)status.setText("Не удалось выделить голос. Попробуй записать чёткий слог в тихой комнате.");else{status.setText("Сходство контура: "+score+"/100"+(tones[index]==5?" (для нейтрального тона оценка условная)":""));getPreferences(0).edit().putInt("attempts",getPreferences(0).getInt("attempts",0)+1).putInt("total",getPreferences(0).getInt("total",0)+score).apply();}});}).start();}
    private void quizScreen(){body.removeAllViews();add(text("Угадай тон на слух",22,true));int q=random.nextInt(5);add(text("Прослушай слог и выбери его тон.",17,false));button("▶ Воспроизвести",()->{if(tts!=null)tts.speak(hanzi[q],TextToSpeech.QUEUE_FLUSH,null,"quiz");});for(int i=1;i<=5;i++){final int answer=i;button("Тон "+i,()->{quizCount++;if(answer==q+1)correct++;getPreferences(0).edit().putInt("quiz",quizCount).putInt("correct",correct).apply();Toast.makeText(this,answer==q+1?"Верно!":"Правильный ответ: "+(q+1),Toast.LENGTH_LONG).show();quizScreen();});}}
    private void progressScreen(){int attempts=getPreferences(0).getInt("attempts",0),total=getPreferences(0).getInt("total",0);add(text("Твой прогресс",24,true));add(text("Записей: "+attempts,20,false));add(text("Среднее сходство: "+(attempts==0?"—":total/attempts+"/100"),20,false));int quiz=getPreferences(0).getInt("quiz",0),wins=getPreferences(0).getInt("correct",0);add(text("Викторина: "+wins+" / "+quiz,20,false));button("Сбросить статистику",()->{getPreferences(0).edit().clear().apply();correct=quizCount=0;show(4);});}
    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density);}
    private class PitchView extends View{Paint p=new Paint(3);PitchView(){super(MainActivity.this);setBackgroundColor(surface);}protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();p.setStrokeWidth(dp(1));p.setColor(0xFF48506A);for(int i=1;i<5;i++)c.drawLine(0,h*i/5f,w,h*i/5f,p);drawLine(c,PitchAnalyzer.target(tones[index],64),0xFF9C91FF,3);if(lastContour.length>1)drawLine(c,lastContour,0xFF50D5B7,3);p.setColor(white);p.setTextSize(dp(12));c.drawText("Фиолетовый — цель  ·  Бирюзовый — голос",dp(10),dp(20),p);}void drawLine(Canvas c,float[] a,int color,int width){if(a.length<2)return;p.setColor(color);p.setStrokeWidth(dp(width));p.setStyle(Paint.Style.STROKE);Path path=new Path();for(int i=0;i<a.length;i++){float x=dp(12)+(getWidth()-dp(24))*i/(a.length-1f),y=getHeight()/2f-a[i]*getHeight()/22f;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}c.drawPath(path,p);p.setStyle(Paint.Style.FILL);}}
    @Override protected void onDestroy(){recording=false;if(recorder!=null){try{recorder.stop();recorder.release();}catch(Exception ignored){}}if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
}
