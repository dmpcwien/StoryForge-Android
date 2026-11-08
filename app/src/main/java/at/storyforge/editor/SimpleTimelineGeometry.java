package at.storyforge.editor;

import java.util.List;

/**
 * Coordinates for the simple editor's scrollable filmstrip. A centered fixed
 * playhead maps to the underlying clip even when crossing transition controls.
 */
final class SimpleTimelineGeometry {
    private final float[] offsets;
    private final float[] widths;
    private final double[] times;
    private final double[] durations;
    private final float transitionWidth;
    private final float totalWidth;
    private final double totalSeconds;

    SimpleTimelineGeometry(List<Project.Clip> clips,float minWidth,float pxPerSecond,
                           float maxWidth,float transitionPx){
        int size=clips.size();
        offsets=new float[size];widths=new float[size];
        times=new double[size];durations=new double[size];
        transitionWidth=Math.max(0,transitionPx);
        float cursor=0;double seconds=0;
        for(int i=0;i<size;i++){
            if(i>0)cursor+=transitionWidth;
            offsets[i]=cursor;
            times[i]=seconds;
            double length=Math.max(.01,clips.get(i).length());
            durations[i]=length;
            widths[i]=Math.max(minWidth,Math.min(maxWidth,(float)(length*pxPerSecond)));
            cursor+=widths[i];
            seconds+=length;
        }
        totalWidth=cursor;totalSeconds=seconds;
    }
    int size(){return offsets.length;}
    float contentWidth(){return totalWidth;}
    double totalTime(){return totalSeconds;}
    float startX(int index){return offsets[index];}
    float width(int index){return widths[index];}
    float transitionWidth(){return transitionWidth;}
    double clipStart(int index){return times[index];}
    double clipDuration(int index){return durations[index];}
    int clipAt(float x){
        if(offsets.length==0)return -1;
        for(int i=0;i<offsets.length;i++){
            if(x<offsets[i]+widths[i])return i;
            if(i+1<offsets.length&&x<offsets[i+1])
                return i+1;
        }
        return offsets.length-1;
    }
    double timeAt(float x){
        int index=clipAt(x);
        if(index<0)return 0;
        double fraction=Math.max(0,Math.min(1,(x-offsets[index])/Math.max(1,widths[index])));
        return times[index]+fraction*durations[index];
    }
    float xAt(double seconds){
        if(offsets.length==0)return 0;
        double t=Math.max(0,Math.min(totalSeconds,seconds));
        for(int i=0;i<offsets.length;i++){
            double end=times[i]+durations[i];
            if(t<=end||i==offsets.length-1)
                return offsets[i]+widths[i]*(float)(Math.max(0,Math.min(1,
                    (t-times[i])/durations[i])));
        }
        return totalWidth;
    }
    double localTimeFor(int index,double timelineSeconds){
        if(index<0||index>=size())return 0;
        return Math.max(0,Math.min(durations[index],timelineSeconds-times[index]));
    }
}
