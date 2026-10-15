package com.mandarin.tonecoach;

import android.content.Context;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Chinese Vosk model is bundled in assets by CI, extracted once on the phone. */
public final class OfflineModel {
    private static final String NAME="vosk-model-small-cn-0.22";
    private volatile Model model;
    private volatile String status="loading";
    public interface Callback {void update(String status, String message);}
    public void initialize(Context context, Callback callback){
        new Thread(()->{
            File modelDir=new File(new File(context.getFilesDir(),"models"),NAME);
            try {
                if(!new File(modelDir,".ready").isFile()) {
                    callback.update("loading","Подготавливаю локальную китайскую модель — это делается один раз…");
                    File models=new File(context.getFilesDir(),"models");
                    if(!models.exists()&&!models.mkdirs())throw new IOException("Can't create model directory");
                    InputStream input=context.getAssets().open(NAME+".zip");
                    try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
                        ZipEntry entry;byte[] buffer=new byte[32768];
                        String rootCanonical=models.getCanonicalPath()+File.separator;
                        while((entry=zip.getNextEntry())!=null){
                            File file=new File(models,entry.getName());
                            if(!file.getCanonicalPath().startsWith(rootCanonical))throw new IOException("Unsafe model ZIP entry");
                            if(entry.isDirectory()){if(!file.exists()&&!file.mkdirs())throw new IOException("Cannot make folder");}
                            else{
                                File parent=file.getParentFile();
                                if(!parent.exists()&&!parent.mkdirs())throw new IOException("Cannot make model folder");
                                try(OutputStream out=new BufferedOutputStream(new FileOutputStream(file))){
                                    int n;while((n=zip.read(buffer))!=-1)out.write(buffer,0,n);
                                }
                            }
                            zip.closeEntry();
                        }
                    }
                    if(!new File(modelDir,"am/final.mdl").isFile())throw new IOException("Model incomplete: am/final.mdl missing");
                    if(!new File(modelDir,".ready").createNewFile())throw new IOException("Cannot mark model ready");
                }
                callback.update("loading","Загружаю модель распознавания…");
                model=new Model(modelDir.getAbsolutePath());
                status="ready";callback.update("ready","Китайская модель доступна офлайн");
            } catch(Exception e){status="error";callback.update("error","Распознавание пока недоступно: "+e.getMessage());}
        },"vosk-prepare").start();
    }
    public String recognize(short[] samples,int length){
        Model m=model;if(m==null||length<1600)return "";
        // 'new Recognizer' must not run on UI thread.
        try(Recognizer rec=new Recognizer(m,16000.0f)){
            byte[] chunk=new byte[4096];
            int read=0;
            while(read<length){
                int n=Math.min(length-read,chunk.length/2);
                for(int i=0;i<n;i++){
                    short s=samples[read+i];chunk[2*i]=(byte)(s&0xFF);chunk[2*i+1]=(byte)((s>>8)&0xFF);
                }
                rec.acceptWaveForm(chunk,n*2);read+=n;
            }
            return new JSONObject(rec.getFinalResult()).optString("text","").replace(" ","");
        }catch(Exception e){return "";}
    }
    public boolean isReady(){return model!=null;}
    public void close(){Model m=model;model=null;if(m!=null)try{m.close();}catch(Exception ignored){}}
}
