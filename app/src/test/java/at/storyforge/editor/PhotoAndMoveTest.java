package at.storyforge.editor;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public final class PhotoAndMoveTest {
    private Project.Clip c(String name, boolean photo, String path){
        Project.Clip clip=new Project.Clip();clip.name=name;clip.image=photo;
        clip.path=path;clip.end=3;clip.sourceDuration=3;return clip;
    }
    @Test public void movesFirstAfterLastAndCanUndoViaOppositeMove(){
        Project.Clip a=c("a",true,"/a"),b=c("b",false,"/b"),cc=c("c",true,"/c");
        List<Project.Clip> xs=new ArrayList<>(Arrays.asList(a,b,cc));
        assertEquals(2,TimelineMove.move(xs,0,2,true));
        assertSame(b,xs.get(0));assertSame(cc,xs.get(1));assertSame(a,xs.get(2));
        assertEquals(0,TimelineMove.move(xs,2,0,false));
        assertSame(a,xs.get(0));
    }
    @Test public void noOpInvalidDragDoesNotMutate(){
        List<Project.Clip> xs=new ArrayList<>(Arrays.asList(c("a",true,"/a"),c("b",true,"/b")));
        assertEquals(-1,TimelineMove.move(xs,0,0,false));
        assertEquals(-1,TimelineMove.move(xs,-1,0,true));
        assertEquals(-1,TimelineMove.move(xs,0,4,true));
        assertEquals("a",xs.get(0).name);
    }
    @Test public void exactDuplicatePhotosDetectedButVideosExcluded()throws Exception{
        File dir=Files.createTempDirectory("storyforge-dup-").toFile();
        try{
            File a=new File(dir,"a.jpg"),b=new File(dir,"b.jpg"),c=new File(dir,"c.jpg");
            Files.write(a.toPath(),"same content".getBytes(StandardCharsets.UTF_8));
            Files.write(b.toPath(),"same content".getBytes(StandardCharsets.UTF_8));
            Files.write(c.toPath(),"different content".getBytes(StandardCharsets.UTF_8));
            List<Project.Clip> xs=Arrays.asList(c("one",true,a.getAbsolutePath()),c("two",true,b.getAbsolutePath()),c("video",false,a.getAbsolutePath()),c("other",true,c.getAbsolutePath()));
            List<DuplicatePhotoScanner.Duplicate> duplicates=DuplicatePhotoScanner.scan(xs);
            assertEquals(1,duplicates.size());
            assertEquals(1,duplicates.get(0).index);
            assertEquals(0,duplicates.get(0).firstIndex);
            assertTrue(a.exists());assertTrue(b.exists());
        } finally {
            File[] children=dir.listFiles();
            if(children!=null)for(File f:children)f.delete();
            dir.delete();
        }
    }

    @Test public void dropIntoGapBetweenClipsAndAtEitherEnd(){
        Project.Clip a=c("A",true,"/a"),b=c("B",true,"/b"),
            cc=c("C",false,"/c"),d=c("D",true,"/d");
        List<Project.Clip> list=new ArrayList<>(Arrays.asList(a,b,cc,d));
        assertEquals(2,TimelineMove.moveToGap(list,0,3));
        assertEquals(Arrays.asList(b,cc,a,d),list);
        assertEquals(0,TimelineMove.moveToGap(list,2,0));
        assertEquals(Arrays.asList(a,b,cc,d),list);
        assertEquals(3,TimelineMove.moveToGap(list,0,4));
        assertEquals(Arrays.asList(b,cc,d,a),list);
        assertEquals(1,TimelineMove.moveToGap(list,3,1));
        assertEquals(Arrays.asList(b,a,cc,d),list);
    }
    @Test public void droppingToOwnBoundaryOrInvalidGapDoesNothing(){
        List<Project.Clip> xs=new ArrayList<>(Arrays.asList(
            c("A",true,"/a"),c("B",true,"/b"),c("C",true,"/c")));
        assertEquals(-1,TimelineMove.moveToGap(xs,1,1)); // left of self
        assertEquals(-1,TimelineMove.moveToGap(xs,1,2)); // right of self
        assertEquals(-1,TimelineMove.moveToGap(xs,1,-1));
        assertEquals(-1,TimelineMove.moveToGap(xs,1,4));
        assertEquals("A",xs.get(0).name);
        assertEquals("B",xs.get(1).name);
    }
}
