package at.storyforge.editor;

import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Eight levels of per-project undo/redo, plus the existing atomic project.json. */
final class ProjectHistory {
    private static final int LIMIT=8;
    private static File path(File dir,String kind,int index){
        return new File(dir,"project-"+kind+"-"+index+".json");
    }
    private static void push(File dir,String kind,byte[] data)throws Exception{
        for(int i=LIMIT;i>=2;i--){
            File older=path(dir,kind,i-1),newer=path(dir,kind,i);
            if(older.isFile())Files.copy(older.toPath(),newer.toPath(),StandardCopyOption.REPLACE_EXISTING);
            else newer.delete();
        }
        Files.write(path(dir,kind,1).toPath(),data);
    }
    static void saveSnapshot(File directory,File current,byte[] replacement)throws Exception{
        if(!current.isFile())return;
        byte[] previous=Files.readAllBytes(current.toPath());
        if(Arrays.equals(previous,replacement))return;
        push(directory,"undo",previous);
        for(int i=1;i<=LIMIT;i++)path(directory,"redo",i).delete();
    }
    static Project travel(File directory,boolean undo)throws Exception{
        String source=undo?"undo":"redo",target=undo?"redo":"undo";
        File latest=path(directory,source,1);
        if(!latest.isFile())throw new Exception("Kein weiterer Schritt verfügbar.");
        File current=new File(directory,"project.json");
        if(!current.isFile())throw new Exception("Kein aktuelles Projekt.");
        byte[] payload=Files.readAllBytes(latest.toPath());
        Project result=Project.fromJson(new JSONObject(new String(payload,StandardCharsets.UTF_8)));
        push(directory,target,Files.readAllBytes(current.toPath()));
        for(int i=1;i<LIMIT;i++){
            File next=path(directory,source,i+1);
            if(next.isFile())Files.copy(next.toPath(),path(directory,source,i).toPath(),StandardCopyOption.REPLACE_EXISTING);
            else {path(directory,source,i).delete();break;}
        }
        path(directory,source,LIMIT).delete();
        atomicWrite(current,payload);
        return result;
    }
    static void clear(File directory){
        for(String kind:new String[]{"undo","redo"})
            for(int index=1;index<=LIMIT;index++)path(directory,kind,index).delete();
    }
    static void atomicWrite(File destination,byte[] data)throws Exception{
        File tmp=new File(destination.getParentFile(),destination.getName()+".next");
        Files.write(tmp.toPath(),data);
        try{
            Files.move(tmp.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
        }catch(java.nio.file.AtomicMoveNotSupportedException ignored){
            Files.move(tmp.toPath(),destination.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }
    }
    private ProjectHistory(){}
}
