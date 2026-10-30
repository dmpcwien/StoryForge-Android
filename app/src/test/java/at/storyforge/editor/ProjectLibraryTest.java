package at.storyforge.editor;

import org.junit.Test;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

/** Tests do not need Android runtime; the saved files are regular JSON. */
public final class ProjectLibraryTest {
    private Project project(String clipName){
        Project p=new Project();
        Project.Clip clip=new Project.Clip();
        clip.path="/internal/media/"+clipName+".mp4";
        clip.name=clipName;clip.sourceDuration=13;clip.start=2;clip.end=11;
        clip.speed=.8;clip.transition="wipeleft";clip.cropX=.1;clip.cropW=.8;
        p.clips.add(clip);
        Project.Caption caption=new Project.Caption();
        caption.text="Mein Film";caption.start=0;caption.end=3;
        p.captions.add(caption);
        Project.AudioTrack song=new Project.AudioTrack();
        song.path="/internal/media/sound.mp3";song.name="Musik";song.end=34;
        p.audioTracks.add(song);
        p.musicVolume=.72;p.fps=60;p.maxSize=2160;
        return p;
    }
    private void deleteRecursively(File f){
        if(f.isDirectory()){
            File[] children=f.listFiles();
            if(children!=null)for(File child:children)deleteRecursively(child);
        }
        f.delete();
    }
    @Test public void savesAndReopensEntireEditableTimeline()throws Exception{
        File root=Files.createTempDirectory("storyforge-saves").toFile();
        try{
            ProjectLibrary library=new ProjectLibrary(root);
            ProjectLibrary.Entry entry=library.save(null,"  Familienfilm   2026  ",project("ersterClip"));
            assertEquals("Familienfilm 2026",entry.name);
            assertEquals(1,entry.clipCount);
            Project opened=library.load(entry.id);
            assertEquals("ersterClip",opened.clips.get(0).name);
            assertEquals(2,opened.clips.get(0).start,0);
            assertEquals(11,opened.clips.get(0).end,0);
            assertEquals(.8,opened.clips.get(0).speed,0);
            assertEquals("wipeleft",opened.clips.get(0).transition);
            assertEquals(.1,opened.clips.get(0).cropX,0);
            assertEquals(1,opened.captions.size());
            assertEquals("Mein Film",opened.captions.get(0).text);
            assertEquals(1,opened.audioTracks.size());
            assertEquals("Musik",opened.audioTracks.get(0).name);
            assertEquals(60,opened.fps);
            assertEquals(2160,opened.maxSize);
            assertEquals(1,library.list().size());
        }finally{deleteRecursively(root);}
    }
    @Test public void independentlySavesMultipleFilmsAndAutosaveUpdatesOnlyActiveFilm()throws Exception{
        File root=Files.createTempDirectory("storyforge-multi").toFile();
        try{
            ProjectLibrary lib=new ProjectLibrary(root);
            ProjectLibrary.Entry first=lib.save(null,"Film A",project("A"));
            ProjectLibrary.Entry second=lib.save(null,"Film B",project("B"));
            assertNotEquals(first.id,second.id);
            Project editing=lib.load(first.id);
            editing.clips.get(0).end=6;
            lib.save(first.id,"Film A",editing);
            assertEquals(6,lib.load(first.id).clips.get(0).end,0);
            assertEquals("B",lib.load(second.id).clips.get(0).name);
            assertEquals(11,lib.load(second.id).clips.get(0).end,0);
            ProjectLibrary.Entry copy=lib.save(null,"Film A – Kopie",editing);
            assertNotEquals(first.id,copy.id);
            assertEquals(3,lib.list().size());
        }finally{deleteRecursively(root);}
    }
    @Test public void rejectsInvalidNamesAndIdentifiersWithoutTouchingOtherProjects()throws Exception{
        File root=Files.createTempDirectory("storyforge-safety").toFile();
        try{
            ProjectLibrary lib=new ProjectLibrary(root);
            ProjectLibrary.Entry saved=lib.save(null,"Sicher",project("clip"));
            try{lib.save(null," ",project("clip"));fail("blank name accepted");}
            catch(IllegalArgumentException expected){}
            try{lib.load("../../outside");fail("path traversal accepted");}
            catch(IllegalArgumentException expected){}
            assertEquals("clip",lib.load(saved.id).clips.get(0).name);
        }finally{deleteRecursively(root);}
    }
}
