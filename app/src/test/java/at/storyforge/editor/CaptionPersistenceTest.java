package at.storyforge.editor;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Saved caption geometry must be identical in the film preview and MP4 export. */
public final class CaptionPersistenceTest {
    @Test public void captionPositionFontSizeAndTimingRoundTrip()throws Exception{
        Project project=new Project();
        Project.Caption caption=new Project.Caption();
        caption.text="Urlaub 2026";
        caption.start=1.5;
        caption.end=4.25;
        caption.x=.23;
        caption.y=.65;
        caption.sizeSp=92;
        caption.color=0xFFFFCC33;
        project.captions.add(caption);
        Project copy=Project.fromJson(new JSONObject(project.toJson().toString()));
        assertEquals(1,copy.captions.size());
        Project.Caption saved=copy.captions.get(0);
        assertEquals("Urlaub 2026",saved.text);
        assertEquals(1.5,saved.start,1e-9);
        assertEquals(4.25,saved.end,1e-9);
        assertEquals(.23,saved.x,1e-9);
        assertEquals(.65,saved.y,1e-9);
        assertEquals(92,saved.sizeSp,1e-6);
        assertEquals(0xFFFFCC33,saved.color);
    }
    @Test public void legacyCaptionsHaveSafeDefaultGeometry()throws Exception{
        JSONObject old=new JSONObject()
            .put("text","Alt")
            .put("start",0)
            .put("end",2);
        Project.Caption restored=Project.Caption.fromJson(old);
        assertEquals(.5,restored.x,1e-9);
        assertEquals(.78,restored.y,1e-9);
        assertEquals(44,restored.sizeSp,1e-6);
        assertEquals(0xFFFFFFFF,restored.color);
    }
    @Test public void changingCaptionDoesNotAlterTimelineMedia()throws Exception{
        Project project=new Project();
        Project.Clip c=new Project.Clip();
        c.path="/media/example.jpg";
        c.name="Beispiel";
        c.image=true;
        c.end=5;
        project.clips.add(c);
        Project.Caption text=new Project.Caption();
        text.text="Hallo";text.start=0;text.end=4;
        text.x=.84;text.y=.12;text.sizeSp=60;
        project.captions.add(text);
        Project restored=Project.fromJson(project.toJson());
        assertEquals(1,restored.clips.size());
        assertEquals("/media/example.jpg",restored.clips.get(0).path);
        assertEquals(5,restored.clips.get(0).end,1e-9);
        assertEquals(.84,restored.captions.get(0).x,1e-9);
    }
}
