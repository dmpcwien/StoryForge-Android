package at.storyforge.editor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

final class Project {
    final List<Clip> clips = new ArrayList<>();
    final List<Caption> captions = new ArrayList<>();
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
        JSONArray cs=new JSONArray(),ts=new JSONArray();
        for (Clip c:clips) cs.put(c.toJson());
        for (Caption c:captions) ts.put(c.toJson());
        return j.put("clips",cs).put("captions",ts).put("musicPath",musicPath)
            .put("musicName",musicName).put("musicVolume",musicVolume)
            .put("originalVolume",originalVolume).put("maxSize",maxSize)
            .put("fps",fps).put("crf",crf).put("aspect",aspect);
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
        p.aspect=j.optString("aspect","original");return p;
    }
    static final class Clip {
        String path,name; boolean image,audio;
        double sourceDuration, start=0, end=0;
        String transition="fade";double transitionSeconds=.65;
        double length(){return Math.max(.1,end-start);}
        JSONObject toJson()throws Exception{return new JSONObject().put("path",path).put("name",name)
            .put("image",image).put("audio",audio).put("sourceDuration",sourceDuration)
            .put("start",start).put("end",end).put("transition",transition)
            .put("transitionSeconds",transitionSeconds);}
        static Clip fromJson(JSONObject j){Clip c=new Clip();c.path=j.optString("path");c.name=j.optString("name");
            c.image=j.optBoolean("image");c.audio=j.optBoolean("audio");c.sourceDuration=j.optDouble("sourceDuration",4);
            c.start=j.optDouble("start");c.end=j.optDouble("end",4);
            c.transition=j.optString("transition","fade");c.transitionSeconds=j.optDouble("transitionSeconds",.65);return c;}
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
