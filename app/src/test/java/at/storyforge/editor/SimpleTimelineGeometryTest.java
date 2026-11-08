package at.storyforge.editor;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

/** Simple editing timeline corresponds to the clip/time under a fixed playhead. */
public final class SimpleTimelineGeometryTest {
    private Project.Clip clip(double sourceStart,double sourceEnd,double speed){
        Project.Clip clip=new Project.Clip();
        clip.name="Clip";
        clip.path="/media/test.mp4";
        clip.start=sourceStart;clip.end=sourceEnd;clip.sourceDuration=100;
        clip.speed=speed;
        return clip;
    }
    @Test public void swipeMapsToFrameInCorrectClip(){
        List<Project.Clip> clips=Arrays.asList(clip(2,8,1),clip(0,4,1),clip(3,11,2));
        SimpleTimelineGeometry g=new SimpleTimelineGeometry(clips,70,12,172,30);
        assertEquals(3,g.size());
        assertEquals(14,g.totalTime(),1e-8); // 6s + 4s + 4s
        assertEquals(0,g.clipAt(g.xAt(3)));
        assertEquals(1,g.clipAt(g.xAt(8)));
        assertEquals(2,g.clipAt(g.xAt(11)));
        assertEquals(8,g.timeAt(g.xAt(8)),1e-6);
        assertEquals(12.5,g.timeAt(g.xAt(12.5)),1e-6);
    }
    @Test public void transitionGapSnapsToNextClipStart(){
        List<Project.Clip> clips=Arrays.asList(clip(0,5,1),clip(0,6,1));
        SimpleTimelineGeometry g=new SimpleTimelineGeometry(clips,80,10,180,28);
        float lastEnd=g.startX(0)+g.width(0);
        float gapMiddle=lastEnd+14;
        assertEquals(1,g.clipAt(gapMiddle));
        assertEquals(5,g.timeAt(gapMiddle),1e-6);
        assertEquals(0,g.timeAt(-200),1e-6);
        assertEquals(11,g.timeAt(999999),1e-6);
    }
    @Test public void positionsStayBoundedAndOrderIsPreserved(){
        List<Project.Clip> clips=Arrays.asList(clip(0,1,1),clip(0,40,1),clip(0,1,1));
        SimpleTimelineGeometry g=new SimpleTimelineGeometry(clips,64,15,120,26);
        assertTrue(g.width(0)>=64);
        assertEquals(120,g.width(1),0);
        assertTrue(g.startX(1)>g.startX(0)+g.width(0));
        assertTrue(g.startX(2)>g.startX(1)+g.width(1));
        for(double time=0;time<g.totalTime();time+=.4){
            double restored=g.timeAt(g.xAt(time));
            assertEquals(time,restored,1e-5);
        }
    }
}
