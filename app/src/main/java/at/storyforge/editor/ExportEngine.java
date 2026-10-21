package at.storyforge.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.os.Build;
import android.os.Environment;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import com.arthenica.ffmpegkit.FFmpegKit;
import com.arthenica.ffmpegkit.FFmpegSession;
import com.arthenica.ffmpegkit.ReturnCode;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Offline export: native FFmpeg xfade/acrossfade, composited text PNGs, audio mix. */
final class ExportEngine {
    interface Listener {
        void progress(int pct);
        void success(Uri galleryUri, File output);
        void failed(String message);
    }
    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile long sessionId=-1;
    private volatile boolean canceled=false;

    ExportEngine(Context ctx){this.ctx=ctx.getApplicationContext();}

    void cancel() {
        canceled=true;
        if(sessionId!=-1) FFmpegKit.cancel(sessionId);
    }
    void start(Project project, Listener listener){startInternal(project,listener,true);}
    void startPreview(Project project, Listener listener){startInternal(project,listener,false);}
    private void startInternal(Project project, Listener listener,boolean publish){
        canceled=false;
        new Thread(() -> {
            try {
                if(project.clips.isEmpty())throw new IllegalStateException("Bitte mindestens ein Foto oder Video auswählen.");
                File dir=new File(ctx.getCacheDir(),"exports");
                if(!dir.exists()&&!dir.mkdirs())throw new Exception("Exportordner konnte nicht angelegt werden.");
                Project temp=project;
                if(!publish) {
                    temp=Project.fromJson(project.toJson());
                    temp.maxSize=720;temp.fps=30;
                    while(temp.clips.size()>5)temp.clips.remove(temp.clips.size()-1);
                }
                final Project rendering=temp;
                File out=new File(dir,(publish?"StoryForge_":"StoryForge_preview_")+System.currentTimeMillis()+".mp4");
                List<String> args=buildCommand(rendering,out,dir);
                if(canceled)return;
                main.post(()->listener.progress(0));
                FFmpegSession session=FFmpegKit.executeWithArgumentsAsync(args.toArray(new String[0]), result -> {
                    sessionId=-1;
                    if (canceled)return;
                    if(ReturnCode.isSuccess(result.getReturnCode()) && out.exists() && out.length()>0) {
                        try {
                            Uri gallery=publish?publishVideo(out):Uri.fromFile(out);
                            main.post(()->listener.success(gallery,out));
                        } catch(Exception ex) {
                            main.post(()->listener.failed("MP4 erstellt, aber Speichern in Galerie fehlgeschlagen: "+ex.getMessage()));
                        }
                    } else {
                        String msg = result.getFailStackTrace();
                        if(msg==null||msg.isEmpty()) {
                            String logs=result.getAllLogsAsString();
                            msg=logs==null?"Unbekannter Exportfehler":logs.substring(Math.max(0,logs.length()-1100));
                        }
                        final String error=msg;
                        main.post(()->listener.failed(error));
                    }
                }, log->{}, stats -> {
                    if(canceled)return;
                    double durationMs=rendering.duration()*1000.0;
                    int percentage=Math.max(0,Math.min(99,(int)Math.round(stats.getTime()*100.0/Math.max(1,durationMs))));
                    main.post(()->listener.progress(percentage));
                });
                sessionId=session.getSessionId();
            }catch(Exception e){if(!canceled)main.post(()->listener.failed(e.getMessage()==null?e.toString():e.getMessage()));}
        },"sf-export").start();
    }

    private String n(double x){return String.format(Locale.US,"%.4f",x);}
    private int even(double number){return Math.max(2,((int)Math.round(number))&~1);}
    private int[] sizeFor(Project p) {
        int iw=1920,ih=1080;
        int longest=2;
        for(int index=0;index<p.clips.size();index++) {
            Project.Clip c=p.clips.get(index);
            int[] dim=dimensions(c);
            int w=dim[0],h=dim[1];
            if(index==0){iw=w;ih=h;}
            longest=Math.max(longest,Math.max(w,h));
        }
        double ratio=(double)iw/Math.max(1,ih);
        if("16:9".equals(p.aspect))ratio=16.0/9;
        else if("9:16".equals(p.aspect))ratio=9.0/16;
        else if("1:1".equals(p.aspect))ratio=1;
        else if("4:5".equals(p.aspect))ratio=.8;
        int max=p.maxSize==720?1280:p.maxSize==2160?3840:1920;
        int longSide=even(Math.min(max,longest));
        // Do not upscale beyond the largest imported source's pixel dimensions.
        if(ratio>=1)return new int[]{longSide,even(longSide/ratio)};
        return new int[]{even(longSide*ratio),longSide};
    }
    private int[] dimensions(Project.Clip c) {
        if(c.image) {
            BitmapFactory.Options opts=new BitmapFactory.Options(); opts.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(c.path,opts);
            int imgW=Math.max(2,opts.outWidth),imgH=Math.max(2,opts.outHeight);
            return c.rotationDegrees==90||c.rotationDegrees==270
                ?new int[]{imgH,imgW}:new int[]{imgW,imgH};
        }
        MediaMetadataRetriever m=new MediaMetadataRetriever();
        try {
            m.setDataSource(c.path);
            int w=Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h=Integer.parseInt(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            String rotation=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if("90".equals(rotation)||"270".equals(rotation))return new int[]{h,w};
            return new int[]{Math.max(2,w),Math.max(2,h)};
        }catch(Exception e){return new int[]{1920,1080};}finally{try{m.release();}catch(Exception ignored){}}
    }

    private List<String> buildCommand(Project p,File out,File tempDir)throws Exception {
        int[] wh=sizeFor(p);
        int w=wh[0],h=wh[1];
        int frameRate=p.fps;
        List<String> args=new ArrayList<>();
        args.add("-hide_banner");args.add("-y");
        List<Integer> videoInputs=new ArrayList<>(),audioInputs=new ArrayList<>(),layerAudioInputs=new ArrayList<>(),stickerInputs=new ArrayList<>();
        int idx=0;
        for(Project.Clip c:p.clips) {
            if(!new File(c.path).exists())throw new Exception("Datei nicht gefunden: "+c.name);
            if(c.image){
                add(args,"-noautorotate","-loop","1","-framerate",Integer.toString(frameRate),"-t",n(c.length()),"-i",c.path);
            }else{
                add(args,"-ss",n(c.start),"-t",n(c.end-c.start),"-i",c.path);
            }
            videoInputs.add(idx);
            if(!c.image && c.audio)audioInputs.add(idx);
            else{
                add(args,"-f","lavfi","-t",n(c.length()),"-i","anullsrc=r=48000:cl=stereo");
                audioInputs.add(idx+1);idx++;
            }
            idx++;
        }
        int musicIndex=-1;
        if(!TextUtils.isEmpty(p.musicPath)){
            File f=new File(p.musicPath);
            if(f.exists()){musicIndex=idx; add(args,"-stream_loop","-1","-i",f.getAbsolutePath());idx++;}
        }
        for(Project.AudioTrack track:p.audioTracks){
            File audioFile=new File(track.path);
            if(audioFile.isFile()){layerAudioInputs.add(idx);add(args,"-i",audioFile.getAbsolutePath());idx++;}
            else layerAudioInputs.add(-1);
        }
        int baseOverlayIndex=idx;
        List<File> overlays=new ArrayList<>();
        for(int i=0;i<p.captions.size();i++){
            Project.Caption caption=p.captions.get(i);
            if(TextUtils.isEmpty(caption.text)||caption.end<=caption.start)continue;
            File png=new File(tempDir,"text_"+System.currentTimeMillis()+"_"+i+".png");
            makeCaptionBitmap(caption,w,h,png);
            overlays.add(png);
            add(args,"-loop","1","-framerate",Integer.toString(frameRate),"-i",png.getAbsolutePath());
            idx++;
        }
        for(Project.Sticker sticker:p.stickers) {
            if(!new File(sticker.path).isFile()){stickerInputs.add(-1);continue;}
            stickerInputs.add(idx);
            add(args,"-loop","1","-framerate",Integer.toString(frameRate),"-i",sticker.path);
            idx++;
        }
        StringBuilder filter=new StringBuilder();
        for(int i=0;i<p.clips.size();i++){
            Project.Clip c=p.clips.get(i);
            String video="["+videoInputs.get(i)+":v]";
            String audio="["+audioInputs.get(i)+":a]";
            filter.append(video)
                .append(c.image?orientationFilter(c.rotationDegrees):"")
                .append("scale=").append(w).append(":").append(h)
                .append(":force_original_aspect_ratio=decrease:flags=lanczos")
                .append(",pad=").append(w).append(":").append(h)
                .append(":(ow-iw)/2:(oh-ih)/2:color=black")
                .append(",setsar=1")
                .append(c.image && c.kenBurns ? ",zoompan=z='min(zoom+0.0015,1.14)':d=1:s="+w+"x"+h+":fps="+frameRate : "")
                .append(",eq=brightness=").append(n(c.brightness))
                .append(":contrast=").append(n(c.contrast))
                .append(":saturation=").append(n(c.saturation))
                .append(",format=yuv420p")
                .append(",setpts=(PTS-STARTPTS)/").append(n(c.image?1:c.speed))
                .append(",trim=duration=").append(n(c.length()))
                .append(",fps=").append(frameRate).append(",settb=AVTB[v").append(i).append("];\n");
            filter.append(audio)
                .append("aresample=48000,aformat=channel_layouts=stereo,apad,atrim=duration=")
                .append(n(c.image?c.length():c.end-c.start)).append(",asetpts=PTS-STARTPTS")
                .append(!c.image && c.speed!=1 ? speedAudio(c.speed) : "")
                .append(",atrim=duration=").append(n(c.length())).append("[a").append(i).append("];\n");
        }
        String v="v0",a="a0";
        double joined=p.clips.get(0).length();
        for(int i=1;i<p.clips.size();i++){
            Project.Clip c=p.clips.get(i);
            boolean cut="cut".equals(c.transition);
            String nextV="mergedv"+i,nextA="mergeda"+i;
            if(cut){
                filter.append("[").append(v).append("][v").append(i)
                    .append("]concat=n=2:v=1:a=0[").append(nextV).append("];\n");
                filter.append("[").append(a).append("][a").append(i)
                    .append("]concat=n=2:v=0:a=1[").append(nextA).append("];\n");
                joined+=c.length();
            }else{
                double duration=Math.min(c.transitionSeconds,Math.min(p.clips.get(i-1).length(),c.length())*.45);
                duration=Math.max(.04,duration);
                double offset=joined-duration;
                filter.append("[").append(v).append("][v").append(i)
                    .append("]xfade=transition=").append(transition(c.transition))
                    .append(":duration=").append(n(duration)).append(":offset=").append(n(offset))
                    .append("[").append(nextV).append("];\n");
                filter.append("[").append(a).append("][a").append(i)
                    .append("]acrossfade=d=").append(n(duration))
                    .append(":c1=tri:c2=tri[").append(nextA).append("];\n");
                joined+=c.length()-duration;
            }
            v=nextV;a=nextA;
        }
        int overlayIdx=0;
        for(Project.Caption caption:p.captions) {
            if(TextUtils.isEmpty(caption.text)||caption.end<=caption.start)continue;
            String outV="textv"+overlayIdx;
            filter.append("[").append(v).append("][").append(baseOverlayIndex+overlayIdx)
                .append(":v]overlay=x=0:y=0:format=auto:eof_action=repeat:enable='between(t,")
                .append(n(Math.max(0,caption.start))).append(",")
                .append(n(Math.min(joined,caption.end)))
                .append(")'[").append(outV).append("];\n");
            v=outV;overlayIdx++;
        }
        int stickerNum=0;
        for(int j=0;j<p.stickers.size();j++){
            Project.Sticker sticker=p.stickers.get(j);
            int input=stickerInputs.get(j);
            if(input<0||sticker.end<=sticker.start)continue;
            String sk="sticker"+stickerNum,outV="stickerComposite"+stickerNum;
            filter.append("[").append(input).append(":v]format=rgba,scale=")
                .append(Math.max(8,even(w*sticker.scale))).append(":-1[").append(sk).append("];\n");
            filter.append("[").append(v).append("][").append(sk).append("]overlay=x=")
                .append(n(sticker.x)).append("*(W-w):y=").append(n(sticker.y))
                .append("*(H-h):enable='between(t,").append(n(sticker.start)).append(",")
                .append(n(sticker.end)).append(")'[").append(outV).append("];\n");
            v=outV;stickerNum++;
        }
        String aout="audiofinal";
        if(musicIndex>=0) {
            filter.append("[").append(a).append("]volume=").append(n(p.originalVolume)).append("[originalvol];\n");
            filter.append("[").append(musicIndex).append(":a]aresample=48000,aformat=channel_layouts=stereo,")
                .append("volume=").append(n(p.musicVolume)).append(",atrim=duration=").append(n(joined))
                .append(",asetpts=PTS-STARTPTS[musicvol];\n");
            if(p.automaticDucking){
                filter.append("[musicvol]asplit=2[musicSide][musicMix];\n");
                filter.append("[originalvol][musicSide]sidechaincompress=threshold=0.065:ratio=5:attack=20:release=300[ducked];\n");
                filter.append("[ducked][musicMix]amix=inputs=2:duration=first:dropout_transition=0[")
                    .append(aout).append("]");
            }else{
                filter.append("[originalvol][musicvol]amix=inputs=2:duration=first:dropout_transition=0[")
                    .append(aout).append("]");
            }
        }else{
            filter.append("[").append(a).append("]volume=").append(n(p.originalVolume)).append("[").append(aout).append("]");
        }
        for(int k=0;k<p.audioTracks.size();k++) {
            Project.AudioTrack t=p.audioTracks.get(k);
            int src=layerAudioInputs.get(k);
            if(src<0||t.start>=joined)continue;
            if(aout.equals("audiofinal"))filter.append(";\n");
            double clipLen=Math.min(Math.max(.1,t.end-t.offset),joined-t.start);
            if(clipLen<=.05)continue;
            String tr="track"+k,next="mix"+k;
            filter.append("[").append(src).append(":a]atrim=start=").append(n(t.offset))
                .append(":duration=").append(n(clipLen)).append(",asetpts=PTS-STARTPTS")
                .append(",aresample=48000,aformat=channel_layouts=stereo,volume=").append(n(t.volume));
            if(t.fadeIn>0)filter.append(",afade=t=in:st=0:d=").append(n(Math.min(t.fadeIn,clipLen/2)));
            if(t.fadeOut>0)filter.append(",afade=t=out:st=").append(n(Math.max(0,clipLen-Math.min(t.fadeOut,clipLen/2))))
                .append(":d=").append(n(Math.min(t.fadeOut,clipLen/2)));
            long ms=Math.max(0,Math.round(t.start*1000));
            filter.append(",adelay=").append(ms).append("|").append(ms).append("[").append(tr).append("];\n");
            if(p.automaticDucking && t.sequential){
                filter.append("[").append(tr).append("]asplit=2[").append(tr).append("side][")
                    .append(tr).append("mix];\n");
                filter.append("[").append(aout).append("][").append(tr).append("side]")
                    .append("sidechaincompress=threshold=0.07:ratio=4:attack=20:release=250[")
                    .append(tr).append("duck];\n");
                filter.append("[").append(tr).append("duck][").append(tr)
                    .append("mix]amix=inputs=2:duration=first:dropout_transition=0[").append(next).append("];\n");
            }else{
                filter.append("[").append(aout).append("][").append(tr)
                    .append("]amix=inputs=2:duration=first:dropout_transition=0[").append(next).append("];\n");
            }
            aout=next;
        }
        if(filter.length()>1 && filter.charAt(filter.length()-2)==';')filter.setLength(filter.length()-2);
        add(args,"-filter_complex",filter.toString(),"-map","["+v+"]","-map","["+aout+"]");
        add(args,"-c:v","libx264","-preset","medium","-crf",Integer.toString(p.crf),
            "-pix_fmt","yuv420p","-c:a","aac","-b:a","320k","-ar","48000",
            "-movflags","+faststart","-shortest",out.getAbsolutePath());
        return args;
    }
    private String orientationFilter(int degrees){
        switch(((degrees%360)+360)%360){
            case 90:return "transpose=clock,";
            case 180:return "hflip,vflip,";
            case 270:return "transpose=cclock,";
            default:return "";
        }
    }
    private String speedAudio(double rate){
        if(rate<.5)return ",atempo=0.5,atempo="+n(rate*2);
        if(rate>2)return ",atempo=2,atempo="+n(rate/2);
        return ",atempo="+n(rate);
    }
    private String transition(String s){
        switch(s){
            case "wipeleft": case "wiperight": case "slideleft": case "slideright":
            case "circleopen": case "circleclose": case "pixelize": case "dissolve":
            case "fadeblack": case "fadewhite": case "zoomin": case "smoothleft": return s;
            default:return "fade";
        }
    }
    private void makeCaptionBitmap(Project.Caption caption,int width,int height,File file)throws Exception{
        Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        try{
            Canvas canvas=new Canvas(b);
            float factor=width/1080f;
            TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
            paint.setColor(caption.color);paint.setTextSize(caption.sizeSp*factor);paint.setTypeface(android.graphics.Typeface.create("sans-serif-black",android.graphics.Typeface.BOLD));
            paint.setTextAlign(Paint.Align.LEFT);paint.setShadowLayer(4*factor,1*factor,3*factor,Color.BLACK);
            int maxWidth=(int)(width*.88);
            StaticLayout text=StaticLayout.Builder.obtain(caption.text,0,caption.text.length(),paint,maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setIncludePad(false).build();
            int x=(int)(width*caption.x-maxWidth/2.0);
            int y=(int)(height*caption.y-text.getHeight()/2.0);
            canvas.save();canvas.translate(Math.max(0,Math.min(width-maxWidth,x)),Math.max(0,Math.min(height-text.getHeight(),y)));
            text.draw(canvas);canvas.restore();
            try(FileOutputStream out=new FileOutputStream(file)){
                if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new Exception("Textbild konnte nicht erstellt werden");
            }
        }finally{b.recycle();}
    }
    private void add(List<String> a,String... values){java.util.Collections.addAll(a,values);}
    private Uri publishVideo(File src)throws Exception{
        ContentValues values=new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME,src.getName());
        values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");
        values.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES+"/StoryForge");
        values.put(MediaStore.Video.Media.IS_PENDING,1);
        Uri uri=ctx.getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);
        if(uri==null)throw new Exception("Medienspeicher ist nicht verfügbar.");
        try{
            try(InputStream in=new java.io.FileInputStream(src);
                OutputStream os=ctx.getContentResolver().openOutputStream(uri)){
                if(os==null)throw new Exception("Galerie verweigert Zugriff.");
                byte[] buf=new byte[131072];int n;
                while((n=in.read(buf))!=-1)os.write(buf,0,n);
            }
            values.clear();values.put(MediaStore.Video.Media.IS_PENDING,0);
            ctx.getContentResolver().update(uri,values,null,null);
            return uri;
        }catch(Exception ex){ctx.getContentResolver().delete(uri,null,null);throw ex;}
    }
}
