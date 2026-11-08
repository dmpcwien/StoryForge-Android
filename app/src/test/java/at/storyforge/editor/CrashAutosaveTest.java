package at.storyforge.editor;

import org.junit.Test;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import static org.junit.Assert.*;

public final class CrashAutosaveTest {
    private static Project film(String text){
        Project project=new Project();
        Project.Clip clip=new Project.Clip();
        clip.name=text;
        clip.path="/media/"+text+".mp4";
        clip.sourceDuration=25;
        clip.end=10;
        clip.start=1;
        project.clips.add(clip);
        return project;
    }
    private static void remove(File file){
        if(file.isDirectory()){
            File[] files=file.listFiles();
            if(files!=null)for(File f:files)remove(f);
        }
        file.delete();
    }
    private static void await(File target)throws Exception{
        for(int i=0;i<140&&!target.isFile();i++)Thread.sleep(25);
        assertTrue("Autosave was not flushed",target.isFile());
    }
    @Test public void preservesTwoIndependentCrashRecoveryGenerations()throws Exception{
        File dir=Files.createTempDirectory("storyforge-crash-save").toFile();
        CrashAutosave autosave=new CrashAutosave(dir);
        try{
            autosave.capture(film("first"));
            File current=new File(dir,CrashAutosave.FILE);
            await(current);
            File working=new File(dir,"project.json");
            ProjectHistory.atomicWrite(working,new Project().toJson().toString().getBytes(StandardCharsets.UTF_8));
            working.setLastModified(1000L);
            autosave.capture(film("second"));
            File previous=new File(dir,CrashAutosave.PREVIOUS);
            await(previous);
            for(int i=0;i<80;i++){
                Project recovered=CrashAutosave.newerRecovery(dir,working);
                if(recovered!=null&&"second".equals(recovered.clips.get(0).name))break;
                Thread.sleep(25);
            }
            assertEquals("second",CrashAutosave.newerRecovery(dir,working).clips.get(0).name);
            Project old=Project.fromJson(new org.json.JSONObject(
                new String(Files.readAllBytes(previous.toPath()),StandardCharsets.UTF_8)));
            assertEquals("first",old.clips.get(0).name);
            List<ProjectRecovery.Candidate> found=ProjectRecovery.scan(dir);
            assertEquals(2,found.size());
        }finally{
            autosave.close();remove(dir);
        }
    }
    @Test public void recoveryNeverReplacesNewerWorkingFile()throws Exception{
        File dir=Files.createTempDirectory("storyforge-recovery-safe").toFile();
        CrashAutosave autosave=new CrashAutosave(dir);
        try{
            autosave.capture(film("older"));
            File recovery=new File(dir,CrashAutosave.FILE);
            await(recovery);
            File current=new File(dir,"project.json");
            ProjectHistory.atomicWrite(current,film("newer").toJson().toString().getBytes(StandardCharsets.UTF_8));
            current.setLastModified(System.currentTimeMillis()+10000);
            assertNull(CrashAutosave.newerRecovery(dir,current));
        }finally{
            autosave.close();remove(dir);
        }
    }
}
