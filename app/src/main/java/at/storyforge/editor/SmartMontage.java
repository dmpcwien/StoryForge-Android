package at.storyforge.editor;

import java.util.ArrayList;
import java.util.List;

/** Film templates and tempo-constrained automatic montage. Clips are references to local copies. */
final class SmartMontage {
    static final String[] STYLES={"Geburtstag","Urlaub","Familie","Hochzeit","Musikvideo","Rückblick"};
    static int preset(String name){
        for(int i=0;i<STYLES.length;i++)if(STYLES[i].equals(name))return i;
        return 2;
    }
    static String apply(Project project,int from,String template,double beat){
        int style=preset(template);
        double[] target={4.2,3.2,4.4,5.1,2.4,3.6};
        boolean fast=style==4;
        int start=Math.max(0,Math.min(from,project.clips.size()));
        if(start>=project.clips.size())return "Keine neuen Medien";
        List<Project.Clip> photos=new ArrayList<>(),videos=new ArrayList<>();
        for(int i=start;i<project.clips.size();i++){
            Project.Clip c=project.clips.get(i);
            if(c.image)photos.add(c);else videos.add(c);
        }
        List<Project.Clip> arranged=new ArrayList<>();
        // Interleave photos and clips; keep relative order within each media class.
        int pi=0,vi=0;
        while(pi<photos.size()||vi<videos.size()){
            if(vi<videos.size()){
                Project.Clip clip=videos.get(vi++);
                List<double[]> segments=clip.audio?AudioRhythm.activeRanges(clip.path,0,clip.sourceDuration)
                    :java.util.Collections.singletonList(new double[]{0,clip.sourceDuration});
                for(double[] segment:segments){
                    try{
                        Project.Clip fragment=Project.Clip.fromJson(clip.toJson());
                        fragment.start=Math.max(0,segment[0]);fragment.end=Math.min(clip.sourceDuration,segment[1]);
                        if(fragment.end-fragment.start>.45)arranged.add(fragment);
                    }catch(Exception ignored){arranged.add(clip);break;}
                }
            }
            if(pi<photos.size())arranged.add(photos.get(pi++));
        }
        // Avoid destructive project-wide replacement; preserve clips before the new range.
        while(project.clips.size()>start)project.clips.remove(project.clips.size()-1);
        project.clips.addAll(arranged);
        int cuts=0;
        double snap=(beat>=.32&&beat<=1.0)?beat:.5;
        for(int i=start;i<project.clips.size();i++){
            Project.Clip c=project.clips.get(i);
            double desired=target[style];
            if(fast)desired=2* snap* Math.max(2,Math.round(desired/(2*snap)));
            else desired=snap*Math.max(2,Math.round(desired/snap));
            if(c.image){c.start=0;c.end=desired;c.kenBurns=style!=4;}
            else if(c.end-c.start>desired+1.2){
                double surplus=(c.end-c.start)-desired;
                c.start+=Math.max(0,surplus/2);
                c.end=Math.min(c.sourceDuration,c.start+desired);
                cuts++;
            }
            c.transition=i==0?"cut":(
                style==4?(i%4==0?"zoomin":i%3==0?"wipeleft":"cut"):
                style==3?(i%3==0?"fadeblack":"fade"):
                style==1?(i%4==0?"slideleft":"fade"):
                style==0?(i%5==0?"circleopen":"fade"):
                style==5?(i%3==0?"fadeblack":"fade"):"fade");
            c.transitionSeconds=fast?.23:(style==3?.85:.48);
        }
        project.filmTemplate=template;project.beatSeconds=snap;
        project.originalVolume=fast?.30:.58;
        project.musicVolume=fast?.8:.68;
        return arranged.size()+" Szenen angeordnet; "+cuts+" lange Szenen gekürzt; Musikrhythmus ≈ "+
            Math.round(60/snap)+" BPM";
    }
    private SmartMontage(){}
}
