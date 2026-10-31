package at.storyforge.editor;

import org.json.JSONObject;
import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

/** Recovery may inspect local snapshots but must never mutate them. */
public final class ProjectRecoveryTest {
    private Project film(String path) {
        Project project=new Project();
        Project.Clip c=new Project.Clip();c.path=path;c.name="Clip";
        c.image=true;c.sourceDuration=4;c.end=4;
        project.clips.add(c);
        return project;
    }
    private static void delete(File f) {
        if(f.isDirectory()){
            File[] children=f.listFiles();
            if(children!=null)for(File child:children)delete(child);
        }
        f.delete();
    }
    @Test public void findsFormerUndoFilmEvenWhenCurrentProjectWasEmptied()throws Exception{
        File dir=Files.createTempDirectory("sf-recovery").toFile();
        try{
            File media=new File(dir,"family.jpg");Files.write(media.toPath(),new byte[]{1,2,3});
            File current=new File(dir,"project.json");
            Files.write(current.toPath(),new Project().toJson().toString().getBytes(StandardCharsets.UTF_8));
            File undo=new File(dir,"project-undo-1.json");
            byte[] before=film(media.getAbsolutePath()).toJson().toString().getBytes(StandardCharsets.UTF_8);
            Files.write(undo.toPath(),before);
            List<ProjectRecovery.Candidate> found=ProjectRecovery.scan(dir);
            assertEquals(1,found.size());
            assertEquals("project-undo-1.json",found.get(0).source.getName());
            assertEquals(1,found.get(0).mediaCount);
            assertEquals(1,found.get(0).availableMediaCount);
            assertArrayEquals(before,Files.readAllBytes(undo.toPath()));
        }finally{delete(dir);}
    }
    @Test public void findsSavedProjectsAndInterruptedWriteWithoutDeletingBrokenFiles()throws Exception{
        File dir=Files.createTempDirectory("sf-recovery-wrap").toFile();
        try{
            ProjectLibrary lib=new ProjectLibrary(dir);
            ProjectLibrary.Entry named=lib.save(null,"Mein Film",film("/missing/clip.jpg"));
            File saved=new File(new File(dir,"saved_projects"),named.id+".json");
            byte[] original=Files.readAllBytes(saved.toPath());
            Files.write(new File(dir,"project-undo-1.json").toPath(),
                "{broken json".getBytes(StandardCharsets.UTF_8));
            File next=new File(dir,"project.json.next");
            Files.write(next.toPath(),film("/another/clip.jpg").toJson().toString().getBytes(StandardCharsets.UTF_8));
            List<ProjectRecovery.Candidate> found=ProjectRecovery.scan(dir);
            assertEquals(2,found.size());
            for(ProjectRecovery.Candidate c:found){
                assertEquals(1,c.mediaCount);
                assertEquals(0,c.availableMediaCount);
            }
            assertArrayEquals(original,Files.readAllBytes(saved.toPath()));
            assertTrue(new File(dir,"project-undo-1.json").isFile());
        }finally{delete(dir);}
    }
    @Test public void doesNotReportEmptyOrInvalidProjects()throws Exception{
        File dir=Files.createTempDirectory("sf-recovery-empty").toFile();
        try{
            Files.write(new File(dir,"project.json").toPath(),
                new Project().toJson().toString().getBytes(StandardCharsets.UTF_8));
            assertTrue(ProjectRecovery.scan(dir).isEmpty());
        }finally{delete(dir);}
    }
}
