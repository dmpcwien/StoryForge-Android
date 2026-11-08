package at.storyforge.editor;

import org.junit.Test;
import static org.junit.Assert.*;

public final class TrimRangeTest {
    private static final double EPS=1e-9;
    @Test public void trimWindowStaysInsideOriginalVideo(){
        assertArrayEquals(new double[]{5,18},TrimRange.normalize(30,5,18),EPS);
        assertArrayEquals(new double[]{0,30},TrimRange.normalize(30,-5,120),EPS);
        assertArrayEquals(new double[]{0,30},TrimRange.normalize(30,0,30),EPS);
    }
    @Test public void draggingStartEndMaintainsMinimumLength(){
        double[] movedStart=TrimRange.moveStart(20,25,13);
        assertEquals(13-TrimRange.MIN_SECONDS,movedStart[0],EPS);
        assertEquals(13,movedStart[1],EPS);
        double[] movedEnd=TrimRange.moveEnd(20,8,2);
        assertEquals(8,movedEnd[0],EPS);
        assertEquals(8+TrimRange.MIN_SECONDS,movedEnd[1],EPS);
    }
    @Test public void clampsInvalidNumbersRatherThanPersistingCorruptTrim(){
        double[] values=TrimRange.normalize(15,Double.NaN,Double.POSITIVE_INFINITY);
        assertEquals(0,values[0],EPS);
        assertEquals(15,values[1],EPS);
        values=TrimRange.normalize(15,15,15);
        assertEquals(15-TrimRange.MIN_SECONDS,values[0],EPS);
        assertEquals(15,values[1],EPS);
    }
    @Test public void existingSourceDurationRemainsUnmodifiedWhenTrimming(){
        Project.Clip clip=new Project.Clip();
        clip.sourceDuration=90;clip.start=0;clip.end=90;
        double[] range=TrimRange.normalize(clip.sourceDuration,12.5,26.2);
        clip.start=range[0];clip.end=range[1];
        assertEquals(90,clip.sourceDuration,EPS);
        assertEquals(13.7,clip.length(),EPS);
    }
}
