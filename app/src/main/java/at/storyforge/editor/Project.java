package at.storyforge.editor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class Project {
    final List<Clip> clips = new ArrayList<>();
    final List<Caption> captions = new ArrayList<>();
    final List<AudioTrack> audioTracks = new ArrayList<>();
    final List<Sticker> stickers = new ArrayList<>();
    double timelineZoom = 1.0;
    String musicPath = "";
    String musicName = "";
    double musicVolume = 0.55;
    double originalVolume = 0.85;
    int maxSize = 1080; // 720, 1080, 2160
    int fps = 30;
    int crf = 17;
    String aspect = "original"; // original, 16:9, 9:16, 1:1

    double duration() {
        double d=0;
        for (int i=0;i<clips.size();i++) {
            d+=clips.get(i).length();
            if (i>0 && !"cut".equals(clips.get(i).transition))
                d-=Math.min(clips.get(i).transitionSeconds, Math.min(clips.get(i-1).length(),clips.get(i).length())*0.45);
        }
        return Math.max(0,d);
    }
    JSONObject toJson() throws Exception {
        JSONObject j = new JSONObject();
        JSONArray cs=new JSONArray(),ts=new JSONArray(),aus=new JSONArray(),sts=new JSONArray();
        for (Clip c:clips) cs.put(c.toJson());
        for (Caption c:captions) ts.put(c.toJson());
        for (AudioTrack c:audioTracks) aus.put(c.toJson());
        for (Sticker c:stickers) sts.put(c.toJson());
        return j.put("clips",cs).put("captions",ts).put("musicPath",musicPath)
            .put("musicName",musicName).put("musicVolume",musicVolume)
            .put("originalVolume",originalVolume).put("maxSize",maxSize)
            .put("fps",fps).put("crf",crf).put("aspect",aspect)
            .put("audioTracks",aus).put("stickers",sts).put("timelineZoom",timelineZoom);
    }
    static Project fromJson(JSONObject j) throws Exception {
        Project p=new Project();
        JSONArray a=j.optJSONArray("clips");
        if(a!=null)for(int i=0;i<a.length();i++)p.clips.add(Clip.fromJson(a.getJSONObject(i)));
        a=j.optJSONArray("captions");
        if(a!=null)for(int i=0;i<a.length();i++)p.captions.add(Caption.fromJson(a.getJSONObject(i)));
        p.musicPath=j.optString("musicPath","");p.musicName=j.optString("musicName","");
        p.musicVolume=j.optDouble("musicVolume",.55);p.originalVolume=j.optDouble("originalVolume",.85);
        p.maxSize=j.optInt("maxSize",1080);p.fps=j.optInt("fps",30);p.crf=j.optInt("crf",17);
        p.aspect=j.optString("aspect","original");
        a=j.optJSONArray("audioTracks");if(a!=null)for(int i=0;i<a.length();i++)p.audioTracks.add(AudioTrack.fromJson(a.getJSONObject(i)));
        a=j.optJSONArray("stickers");if(a!=null)for(int i=0;i<a.length();i++)p.stickers.add(Sticker.fromJson(a.getJSONObject(i)));
        p.timelineZoom=Math.max(.65,Math.min(2.5,j.optDouble("timelineZoom",1)));return p;
    }
    static final class Clip {
        String path,name; boolean image,audio;
        double sourceDuration, start=0, end=0;
        double speed=1,brightness=0,contrast=1,saturation=1;
        boolean kenBurns=false;
        String transition="fade";double transitionSeconds=.65;
        double length(){return Math.max(.1,(end-start)/Math.max(.25,speed));}
        JSONObject toJson()throws Exception{return new JSONObject().put("path",path).put("name",name)
            .put("image",image).put("audio",audio).put("sourceDuration",sourceDuration)
            .put("start",start).put("end",end).put("transition",transition)
            .put("transitionSeconds",transitionSeconds).put("speed",speed)
            .put("brightness",brightness).put("contrast",contrast).put("saturation",saturation)
            .put("kenBurns",kenBurns);}
        static Clip fromJson(JSONObject j){Clip c=new Clip();c.path=j.optString("path");c.name=j.optString("name");
            c.image=j.optBoolean("image");c.audio=j.optBoolean("audio");c.sourceDuration=j.optDouble("sourceDuration",4);
            c.start=j.optDouble("start");c.end=j.optDouble("end",4);
            c.transition=j.optString("transition","fade");c.transitionSeconds=j.optDouble("transitionSeconds",.65);
            c.speed=Math.max(.25,Math.min(4,j.optDouble("speed",1)));c.brightness=j.optDouble("brightness",0);
            c.contrast=j.optDouble("contrast",1);c.saturation=j.optDouble("saturation",1);
            c.kenBurns=j.optBoolean("kenBurns",false);return c;}
    }
    static final class AudioTrack {
        String path="",name=""; double start=0,offset=0,end=0,volume=.6,fadeIn=.25,fadeOut=.5;
        JSONObject toJson()throws Exception{return new JSONObject().put("path",path).put("name",name)
            .put("start",start).put("offset",offset).put("end",end).put("volume",volume)
            .put("fadeIn",fadeIn).put("fadeOut",fadeOut);}
        static AudioTrack fromJson(JSONObject j){AudioTrack a=new AudioTrack();a.path=j.optString("path");
            a.name=j.optString("name");a.start=j.optDouble("start");a.offset=j.optDouble("offset");
            a.end=j.optDouble("end");a.volume=j.optDouble("volume",.6);
            a.fadeIn=j.optDouble("fadeIn",.25);a.fadeOut=j.optDouble("fadeOut",.5);return a;}
    }
    static final class Sticker {
        String path="",name="";double start=0,end=4,x=.7,y=.15,scale=.3;
        JSONObject toJson()throws Exception{return new JSONObject().put("path",path).put("name",name)
            .put("start",start).put("end",end).put("x",x).put("y",y).put("scale",scale);}
        static Sticker fromJson(JSONObject j){Sticker s=new Sticker();s.path=j.optString("path");
            s.name=j.optString("name");s.start=j.optDouble("start");s.end=j.optDouble("end",4);
            s.x=j.optDouble("x",.7);s.y=j.optDouble("y",.15);s.scale=j.optDouble("scale",.3);return s;}
    }
    static final class Caption {
        String text; double start,end;
        int color=0xFFFFFFFF;float sizeSp=44;double x=.5,y=.78;
        JSONObject toJson()throws Exception{return new JSONObject().put("text",text).put("start",start).put("end",end)
            .put("color",color).put("sizeSp",sizeSp).put("x",x).put("y",y);}
        static Caption fromJson(JSONObject j){Caption c=new Caption();c.text=j.optString("text");c.start=j.optDouble("start");c.end=j.optDouble("end");
            c.color=j.optInt("color",0xFFFFFFFF);c.sizeSp=(float)j.optDouble("sizeSp",44);c.x=j.optDouble("x",.5);c.y=j.optDouble("y",.78);return c;}
    }
}
