package at.storyforge.editor;

import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Locally named, editable projects. Files only reference the app's retained
 * imported media; use ProjectBackup for a portable backup including media.
 * An unreadable save is never silently discarded or overwritten on load.
 */
final class ProjectLibrary {
    static final class Entry {
        final String id, name;
        final long modified;
        final int clipCount;
        Entry(String id,String name,long modified,int clipCount){
            this.id=id;this.name=name;this.modified=modified;this.clipCount=clipCount;
        }
    }
    private final File directory;
    ProjectLibrary(File filesDir){directory=new File(filesDir,"saved_projects");}
    private File file(String id){
        if(id==null||!id.matches("[a-f0-9]{32}"))
            throw new IllegalArgumentException("Ungültige Projektkennung.");
        return new File(directory,id+".json");
    }
    private void ensureDirectory()throws IOException{
        if(!directory.isDirectory()&&!directory.mkdirs())
            throw new IOException("Projektordner konnte nicht angelegt werden.");
    }
    static String cleanName(String name){
        if(name==null)return "";
        return name.trim().replaceAll("\\s+"," ");
    }
    synchronized Entry save(String id,String name,Project project)throws Exception{
        name=cleanName(name);
        if(name.isEmpty()||name.length()>75)
            throw new IllegalArgumentException("Bitte einen Projektnamen mit maximal 75 Zeichen eingeben.");
        if(project==null)throw new IllegalArgumentException("Projekt fehlt.");
        ensureDirectory();
        String key=id==null||id.isEmpty()?UUID.randomUUID().toString().replace("-",""):id;
        File target=file(key);
        long now=System.currentTimeMillis();
        JSONObject payload=new JSONObject()
            .put("formatVersion",1)
            .put("id",key)
            .put("name",name)
            .put("updatedAt",now)
            .put("project",project.toJson());
        ProjectHistory.atomicWrite(target,payload.toString().getBytes(StandardCharsets.UTF_8));
        return new Entry(key,name,now,project.clips.size());
    }
    synchronized Entry rename(String id,String name)throws Exception{
        Project project=load(id);
        return save(id,name,project);
    }
    synchronized Project load(String id)throws Exception{
        File target=file(id);
        if(!target.isFile())throw new IOException("Das Projekt wurde nicht gefunden.");
        byte[] bytes=Files.readAllBytes(target.toPath());
        if(bytes.length==0||bytes.length>16*1024*1024)
            throw new IOException("Projektdatei ist leer oder zu groß.");
        JSONObject root=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
        if(!id.equals(root.optString("id"))||root.optJSONObject("project")==null)
            throw new IOException("Ungültige Projektdatei.");
        return Project.fromJson(root.getJSONObject("project"));
    }
    synchronized Entry entry(String id)throws Exception{
        File target=file(id);
        JSONObject doc=new JSONObject(new String(Files.readAllBytes(target.toPath()),StandardCharsets.UTF_8));
        if(!id.equals(doc.optString("id"))||doc.optJSONObject("project")==null)
            throw new IOException("Ungültige Projektdatei.");
        return new Entry(id,doc.optString("name","Projekt"),
            doc.optLong("updatedAt",target.lastModified()),
            doc.getJSONObject("project").optJSONArray("clips")==null?0:
            doc.getJSONObject("project").getJSONArray("clips").length());
    }
    synchronized List<Entry> list(){
        List<Entry> result=new ArrayList<>();
        File[] files=directory.listFiles((dir,name)->name.matches("[a-f0-9]{32}\\.json"));
        if(files==null)return result;
        for(File f:files){
            try{result.add(entry(f.getName().substring(0,32)));}
            catch(Exception ignored){/* invalid files preserved for manual recovery */}
        }
        result.sort(Comparator.comparingLong((Entry e)->e.modified).reversed());
        return result;
    }
}
