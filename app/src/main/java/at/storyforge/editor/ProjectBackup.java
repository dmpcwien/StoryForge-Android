package at.storyforge.editor;

import android.content.Context;
import android.net.Uri;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/** Self-contained user-selected project ZIP including imported media. */
final class ProjectBackup {
    static void write(Context context,Project project,Uri uri)throws Exception{
        JSONObject document=project.toJson();
        Map<String,String> originals=new LinkedHashMap<>();
        collect(document,"clips","path",originals);
        collect(document,"audioTracks","path",originals);
        collect(document,"stickers","path",originals);
        collectString(document,"musicPath",originals);
        int count=0;
        Map<String,String> assets=new LinkedHashMap<>();
        for(Map.Entry<String,String> e:originals.entrySet())
            if(new File(e.getKey()).isFile())assets.put(e.getKey(),"media/file_"+(count++));
        replacePaths(document,assets);
        try(OutputStream out=context.getContentResolver().openOutputStream(uri)){
            if(out==null)throw new IOException("Sicherungsdatei nicht beschreibbar.");
            try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(out))){
                byte[] metadata=document.toString().getBytes(StandardCharsets.UTF_8);
                zip.putNextEntry(new ZipEntry("project.json"));zip.write(metadata);zip.closeEntry();
                byte[] buffer=new byte[65536];
                for(Map.Entry<String,String> e:assets.entrySet()){
                    zip.putNextEntry(new ZipEntry(e.getValue()));
                    try(InputStream input=new BufferedInputStream(new FileInputStream(e.getKey()))){
                        int n;while((n=input.read(buffer))!=-1)zip.write(buffer,0,n);
                    }
                    zip.closeEntry();
                }
            }
        }
    }
    static Project read(Context context,Uri uri)throws Exception{
        File directory=new File(context.getFilesDir(),"backup_import_"+System.currentTimeMillis());
        if(!directory.mkdirs())throw new IOException("Sicherungsordner nicht erstellt");
        byte[] buffer=new byte[65536];
        JSONObject document=null;
        Map<String,String> imported=new LinkedHashMap<>();
        long total=0;
        try(InputStream in=context.getContentResolver().openInputStream(uri)){
            if(in==null)throw new IOException("Sicherung nicht lesbar");
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(in))){
                ZipEntry entry;
                while((entry=zip.getNextEntry())!=null){
                    String name=entry.getName();
                    if(name.equals("project.json")){
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                        int n;
                        while((n=zip.read(buffer))!=-1){
                            total+=n;if(total>3L*1024*1024*1024)throw new IOException("Sicherung zu groß");
                            bytes.write(buffer,0,n);
                        }
                        document=new JSONObject(bytes.toString("UTF-8"));
                    }else if(name.startsWith("media/file_")&&!entry.isDirectory()){
                        String leaf=name.substring("media/".length());
                        if(!leaf.matches("file_[0-9]+"))throw new IOException("Ungültiger Dateiname");
                        File file=new File(directory,leaf);
                        try(OutputStream output=new FileOutputStream(file)){
                            int n;while((n=zip.read(buffer))!=-1){
                                total+=n;if(total>3L*1024*1024*1024)throw new IOException("Sicherung zu groß");
                                output.write(buffer,0,n);
                            }
                        }
                        imported.put(name,file.getAbsolutePath());
                    }
                    zip.closeEntry();
                }
            }
        }
        if(document==null)throw new IOException("project.json fehlt.");
        replacePaths(document,imported);
        Project project=Project.fromJson(document);
        if(project.clips.isEmpty())throw new IOException("Sicherung enthält keine Clips.");
        for(Project.Clip clip:project.clips)
            if(!new File(clip.path).isFile())throw new IOException("Unvollständige Sicherung: "+clip.name);
        return project;
    }
    private static void collect(JSONObject object,String key,String pathKey,Map<String,String> originals)throws Exception{
        JSONArray items=object.optJSONArray(key);
        if(items==null)return;
        for(int i=0;i<items.length();i++){
            String p=items.getJSONObject(i).optString(pathKey);
            if(!p.isEmpty())originals.put(p,p);
        }
    }
    private static void collectString(JSONObject o,String key,Map<String,String> paths){
        String p=o.optString(key);if(!p.isEmpty())paths.put(p,p);
    }
    private static void replacePaths(JSONObject json,Map<String,String> paths)throws Exception{
        for(String key:new String[]{"clips","audioTracks","stickers"}){
            JSONArray items=json.optJSONArray(key);
            if(items==null)continue;
            for(int i=0;i<items.length();i++){
                JSONObject item=items.getJSONObject(i);
                String original=item.optString("path");
                item.put("path",paths.getOrDefault(original,original));
            }
        }
        String old=json.optString("musicPath");
        json.put("musicPath",paths.getOrDefault(old,old));
    }
    private ProjectBackup(){}
}
