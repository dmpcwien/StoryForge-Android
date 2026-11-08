package at.storyforge.editor;

import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Crash/ANR resilience. Keep a serialized copy from the UI thread and flush it
 * on an independent IO thread: a blocked main thread cannot generate new data.
 * One previous copy is retained and corrupted backups are never deleted here.
 */
final class CrashAutosave implements Thread.UncaughtExceptionHandler {
    static final String FILE="project-autosave.json";
    static final String PREVIOUS="project-autosave-previous.json";
    private final File directory;
    private final ExecutorService writer=Executors.newSingleThreadExecutor(r->{
        Thread thread=new Thread(r,"storyforge-autosave");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicReference<byte[]> pending=new AtomicReference<>();
    private final AtomicBoolean enqueued=new AtomicBoolean(false);
    private final Object fileLock=new Object();
    private volatile boolean closed;
    private final Thread.UncaughtExceptionHandler previousHandler;
    private byte[] written;

    CrashAutosave(File filesDir) {
        directory=filesDir;
        previousHandler=Thread.getDefaultUncaughtExceptionHandler();
    }

    void installCrashHandler() {
        Thread.setDefaultUncaughtExceptionHandler(this);
    }

    void capture(byte[] data) {
        if(closed||data==null||data.length==0)return;
        pending.set(Arrays.copyOf(data,data.length));
        schedule();
    }

    void capture(Project project)throws Exception {
        capture(project.toJson().toString(2).getBytes(StandardCharsets.UTF_8));
    }

    private void schedule(){
        if(closed||!enqueued.compareAndSet(false,true))return;
        try{
            writer.execute(()->{
                try{
                    while(true){
                        byte[] newest=pending.get();
                        if(newest==null)break;
                        persist(newest);
                        if(pending.get()==newest)break;
                    }
                }catch(Exception e){
                    android.util.Log.e("StoryForge","Notfallsicherung",e);
                }finally{
                    enqueued.set(false);
                    if(!closed&&pending.get()!=null&&
                        !Arrays.equals(pending.get(),written))schedule();
                }
            });
        }catch(java.util.concurrent.RejectedExecutionException ignored){
            enqueued.set(false);
        }
    }

    private void persist(byte[] snapshot)throws Exception {
        synchronized(fileLock){
            File current=new File(directory,FILE);
            if(written==null && current.isFile())
                written=Files.readAllBytes(current.toPath());
            if(Arrays.equals(written,snapshot))return;
            // Previous version is retained in case a write or the new document fails.
            if(written!=null&&written.length>0)
                ProjectHistory.atomicWrite(new File(directory,PREVIOUS),written);
            ProjectHistory.atomicWrite(current,snapshot);
            written=snapshot;
        }
    }

    /** Best effort: the latest already-serialized snapshot, not unsafe UI state. */
    @Override public void uncaughtException(Thread thread,Throwable error){
        try{
            byte[] data=pending.get();
            if(data!=null)persist(data);
        }catch(Throwable ignored){}
        if(previousHandler!=null&&previousHandler!=this)
            previousHandler.uncaughtException(thread,error);
        else System.exit(10);
    }

    void close(){
        closed=true;
        if(Thread.getDefaultUncaughtExceptionHandler()==this)
            Thread.setDefaultUncaughtExceptionHandler(previousHandler);
        writer.shutdown();
    }

    /**
     * Read a newer, independently durable recovery without modifying project.json.
     * A valid empty autosave is ignored; the user can choose an older recovery
     * from the normal recovery menu instead of it being silently overwritten.
     */
    static Project newerRecovery(File filesDir,File workingFile){
        long workingTime=workingFile.isFile()?workingFile.lastModified():0;
        for(File file:new File[]{new File(filesDir,FILE),new File(filesDir,PREVIOUS)}){
            if(!file.isFile()||file.lastModified()<=workingTime||
                file.length()<=0||file.length()>16*1024*1024)continue;
            try{
                Project restored=Project.fromJson(new JSONObject(
                    new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8)));
                if(!restored.clips.isEmpty())return restored;
            }catch(Exception ignored){}
        }
        return null;
    }
}
