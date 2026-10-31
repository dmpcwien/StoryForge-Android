package at.storyforge.editor;

import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Read-only search for recoverable projects, including undo snapshots and interrupted writes. */
final class ProjectRecovery {
    static final class Candidate {
        final File source;
        final Project project;
        final long modified;
        final int mediaCount;
        final int availableMediaCount;
        Candidate(File source,Project project,long modified){
            this.source=source;this.project=project;this.modified=modified;
            mediaCount=project.clips.size();
            int found=0;
            for(Project.Clip clip:project.clips)
                if(clip.path!=null&&new File(clip.path).isFile())found++;
            availableMediaCount=found;
        }
    }
    static List<Candidate> scan(File filesDir) {
        List<File> sources=new ArrayList<>();
        sources.add(new File(filesDir,"project.json"));
        sources.add(new File(filesDir,"project.json.next"));
        for(String kind:new String[]{"undo","redo"})
            for(int i=1;i<=8;i++)sources.add(new File(filesDir,"project-"+kind+"-"+i+".json"));
        File saved=new File(filesDir,"saved_projects");
        File[] names=saved.listFiles((dir,n)->n.matches("[a-f0-9]{32}\\.json(\\.next)?"));
        if(names!=null)for(File f:names)sources.add(f);
        List<Candidate> matches=new ArrayList<>();
        Set<String> seen=new HashSet<>();
        for(File file:sources){
            if(!file.isFile()||file.length()<5||file.length()>16*1024*1024)continue;
            try{
                JSONObject doc=new JSONObject(new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
                JSONObject projectData=doc.optJSONObject("project");
                if(projectData==null)projectData=doc;
                Project project=Project.fromJson(projectData);
                if(project.clips.isEmpty())continue;
                String key=project.toJson().toString();
                if(!seen.add(key))continue;
                matches.add(new Candidate(file,project,file.lastModified()));
            }catch(Exception ignored){
                // Never delete or overwrite a damaged snapshot during recovery search.
            }
        }
        matches.sort(Comparator.comparingLong((Candidate c)->c.modified).reversed());
        return matches;
    }
    private ProjectRecovery(){}
}
