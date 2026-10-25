package at.storyforge.editor;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression tests for reverse playback order of a photo montage. */
public final class ReverseOrderTest {
    private static Project.Clip clip(String name,boolean image,String transition,double seconds){
        Project.Clip c=new Project.Clip();
        c.name=name;c.path="/test/"+name;c.image=image;c.sourceDuration=4;c.start=0;c.end=4;
        c.transition=transition;c.transitionSeconds=seconds;
        return c;
    }

    @Test public void reversesPhotosOnlyAndPreservesVideoSlots(){
        Project p=new Project();
        Project.Clip first=clip("photo1",true,"cut",.4);
        Project.Clip video1=clip("video1",false,"fade",.7);
        Project.Clip second=clip("photo2",true,"zoomin",.5);
        Project.Clip video2=clip("video2",false,"wipeleft",.2);
        Project.Clip last=clip("photo3",true,"fadeblack",.6);
        p.clips.add(first);p.clips.add(video1);p.clips.add(second);p.clips.add(video2);p.clips.add(last);
        int changed=p.reversePhotoOrder();
        assertEquals(3,changed);
        assertSame(last,p.clips.get(0));
        assertSame(video1,p.clips.get(1));
        assertSame(second,p.clips.get(2));
        assertSame(video2,p.clips.get(3));
        assertSame(first,p.clips.get(4));
        assertEquals("cut",p.clips.get(0).transition);
        assertEquals("zoomin",p.clips.get(2).transition);
        assertEquals("fadeblack",p.clips.get(4).transition);
        assertEquals(.4,p.clips.get(0).transitionSeconds,1e-9);
        assertEquals(.6,p.clips.get(4).transitionSeconds,1e-9);
        assertEquals("fade",video1.transition);
        assertEquals("wipeleft",video2.transition);
        assertEquals(3,p.reversePhotoOrder());
        assertSame(first,p.clips.get(0));
        assertSame(last,p.clips.get(4));
    }

    @Test public void reverseAllIncludesVideosButDoesNotChangeTheirDurations(){
        Project p=new Project();
        Project.Clip photo=clip("photo",true,"cut",.3);
        Project.Clip movie=clip("movie",false,"fade",.8);
        movie.start=2;movie.end=5;
        Project.Clip extra=clip("extra",true,"dissolve",.6);
        p.clips.add(photo);p.clips.add(movie);p.clips.add(extra);
        assertEquals(3,p.reverseClipOrder());
        assertSame(extra,p.clips.get(0));
        assertSame(movie,p.clips.get(1));
        assertSame(photo,p.clips.get(2));
        assertEquals(2,movie.start,0);
        assertEquals(5,movie.end,0);
        assertEquals("cut",p.clips.get(0).transition);
        assertEquals("fade",p.clips.get(1).transition);
        assertEquals("dissolve",p.clips.get(2).transition);
    }

    @Test public void noChangeWhenTooFewPhotos(){
        Project p=new Project();
        Project.Clip movie=clip("movie",false,"fade",.6);
        Project.Clip photo=clip("photo",true,"fade",.7);
        p.clips.add(movie);p.clips.add(photo);
        assertEquals(0,p.reversePhotoOrder());
        assertSame(movie,p.clips.get(0));
        assertSame(photo,p.clips.get(1));
    }
}
