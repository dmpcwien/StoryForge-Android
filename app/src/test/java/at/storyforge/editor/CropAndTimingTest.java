package at.storyforge.editor;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class CropAndTimingTest {
    private static Project.Clip clip(){
        Project.Clip clip=new Project.Clip();
        clip.path="/tmp/example.jpg";clip.name="Beispiel";
        clip.image=true;clip.start=0;clip.end=4;clip.sourceDuration=4;
        return clip;
    }
    @Test public void cropDefaultsPreservedForOlderProjects()throws Exception{
        Project.Clip original=clip();
        JSONObject legacy=original.toJson();
        legacy.remove("cropX");legacy.remove("cropY");legacy.remove("cropW");legacy.remove("cropH");
        Project.Clip parsed=Project.Clip.fromJson(legacy);
        assertEquals(0,parsed.cropX,0);
        assertEquals(0,parsed.cropY,0);
        assertEquals(1,parsed.cropW,0);
        assertEquals(1,parsed.cropH,0);
    }
    @Test public void cropBoundsClampedAndRoundtrips()throws Exception{
        Project.Clip source=clip();
        source.setCrop(.8,.9,.6,.4);
        assertEquals(.4,source.cropX,1e-9);
        assertEquals(.6,source.cropY,1e-9);
        Project.Clip restored=Project.Clip.fromJson(source.toJson());
        assertEquals(.4,restored.cropX,1e-9);
        assertEquals(.6,restored.cropY,1e-9);
        assertEquals(.6,restored.cropW,1e-9);
        assertEquals(.4,restored.cropH,1e-9);
    }
    @Test public void invalidCropDoesNotCorruptProject(){
        Project.Clip source=clip();
        source.setCrop(Double.NaN,.2,.5,.5);
        assertEquals(0,source.cropX,0);
        assertEquals(0,source.cropY,0);
        assertEquals(1,source.cropW,0);
        assertEquals(1,source.cropH,0);
    }
    @Test public void shorteningClipChangesLengthButNotOriginalDuration(){
        Project.Clip source=clip();
        source.image=false;source.sourceDuration=28;source.start=4;source.end=12;
        assertEquals(8,source.length(),1e-9);
        assertEquals(28,source.sourceDuration,1e-9);
    }
}
