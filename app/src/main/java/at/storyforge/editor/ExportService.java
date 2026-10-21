package at.storyforge.editor;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Foreground MP4 export with notification and crash-readable status. */
public final class ExportService extends Service {
    private static final String CHANNEL="storyforge_exports";
    private static final int NOTIFICATION=1804;
    public static final String START="at.storyforge.editor.START_EXPORT";
    public static final String CANCEL="at.storyforge.editor.CANCEL_EXPORT";
    private ExportEngine engine;
    private volatile boolean exporting=false;
    private volatile int lastShown=-1;

    @Override public void onCreate(){
        super.onCreate();
        NotificationChannel channel=new NotificationChannel(CHANNEL,
            "StoryForge Export",NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Fortschritt der MP4-Erstellung");
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(channel);
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private PendingIntent contentIntent(){
        Intent i=new Intent(this,MainActivity.class);
        return PendingIntent.getActivity(this,100,i,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
    }
    private Notification notification(String title,String message,int progress,boolean ongoing){
        Notification.Builder b=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle(title).setContentText(message).setContentIntent(contentIntent())
            .setOnlyAlertOnce(true).setAutoCancel(!ongoing).setOngoing(ongoing);
        if(ongoing){
            b.setProgress(100,Math.max(0,progress),progress==0);
            Intent cancel=new Intent(this,ExportService.class).setAction(CANCEL);
            PendingIntent action=PendingIntent.getService(this,101,cancel,
                PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
            b.addAction(android.R.drawable.ic_menu_close_clear_cancel,"Abbrechen",action);
        }
        return b.build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null && CANCEL.equals(intent.getAction())){
            if(engine!=null)engine.cancel();
            finish("Abgebrochen","Export wurde abgebrochen.",false);
            return START_NOT_STICKY;
        }
        if(exporting)return START_NOT_STICKY;
        exporting=true;
        Notification n=notification("StoryForge exportiert","MP4 wird vorbereitet",0,true);
        if(Build.VERSION.SDK_INT>=35)
            startForeground(NOTIFICATION,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING);
        else if(Build.VERSION.SDK_INT>=29)
            startForeground(NOTIFICATION,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIFICATION,n);
        new Thread(()->{
            try{
                File pending=new File(getFilesDir(),"export-pending.json");
                byte[] contents=Files.readAllBytes(pending.toPath());
                Project project=Project.fromJson(new JSONObject(new String(contents,StandardCharsets.UTF_8)));
                engine=new ExportEngine(this);
                engine.start(project,new ExportEngine.Listener(){
                    @Override public void progress(int pct){
                        if(pct-lastShown>=2||pct>=99){
                            lastShown=pct;
                            NotificationManager mgr=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
                            mgr.notify(NOTIFICATION,notification("StoryForge exportiert",pct+" % abgeschlossen",pct,true));
                            status("rendering",pct,"MP4 wird erstellt.");
                        }
                    }
                    @Override public void success(Uri uri,File file){
                        status("success",100,"In Galerie gespeichert: "+file.getName());
                        finish("Export abgeschlossen","Video in Filme / StoryForge gespeichert.",true);
                    }
                    @Override public void failed(String msg){
                        status("error",0,msg);
                        finish("Export fehlgeschlagen",msg,false);
                    }
                });
            }catch(Exception ex){status("error",0,String.valueOf(ex.getMessage()));finish("Export fehlgeschlagen",String.valueOf(ex.getMessage()),false);}
        },"storyforge-background-export").start();
        return START_NOT_STICKY;
    }
    private void status(String state,int progress,String message){
        try{
            JSONObject json=new JSONObject().put("state",state).put("progress",progress)
                .put("message",message).put("time",System.currentTimeMillis());
            Files.write(new File(getFilesDir(),"export-status.json").toPath(),
                json.toString().getBytes(StandardCharsets.UTF_8));
        }catch(Exception ignored){}
    }
    private void finish(String title,String message,boolean success){
        exporting=false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        Notification.Builder b=new Notification.Builder(this,CHANNEL)
            .setSmallIcon(success?android.R.drawable.stat_sys_upload_done:android.R.drawable.stat_notify_error)
            .setContentTitle(title).setContentText(message).setContentIntent(contentIntent())
            .setAutoCancel(true);
        ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTIFICATION+1,b.build());
        stopSelf();
    }
}
