package at.storyforge.editor;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Finds byte-for-byte duplicate photos already imported into the timeline.
 * No original files are ever modified. Work should run off the UI thread. */
final class DuplicatePhotoScanner {
    static final class Duplicate {
        final int index;
        final int firstIndex;
        Duplicate(int index,int firstIndex){this.index=index;this.firstIndex=firstIndex;}
    }
    static List<Duplicate> scan(List<Project.Clip> clips){
        List<Duplicate> found=new ArrayList<>();
        Map<String,Integer> first=new HashMap<>();
        Map<String,String> fileCache=new HashMap<>();
        for(int i=0;i<clips.size();i++){
            Project.Clip clip=clips.get(i);
            if(!clip.image||clip.path==null||clip.path.isEmpty())continue;
            File file=new File(clip.path);
            if(!file.isFile())continue;
            try {
                String hash=fileCache.get(file.getAbsolutePath());
                if(hash==null){hash=file.length()+":"+sha256(file);fileCache.put(file.getAbsolutePath(),hash);}
                Integer original=first.putIfAbsent(hash,i);
                if(original!=null)found.add(new Duplicate(i,original));
            }catch(Exception ignored){/* Leave unreadable originals untouched. */}
        }
        return found;
    }
    private static String sha256(File file)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        byte[] buffer=new byte[128*1024];
        try(BufferedInputStream in=new BufferedInputStream(new FileInputStream(file))){
            int n;while((n=in.read(buffer))!=-1)digest.update(buffer,0,n);
        }
        StringBuilder output=new StringBuilder(64);
        for(byte b:digest.digest())output.append(String.format(java.util.Locale.US,"%02x",b&0xff));
        return output.toString();
    }
    private DuplicatePhotoScanner(){}
}
