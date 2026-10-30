package at.storyforge.editor;

import java.util.List;

/** Move the dragged clip before or after a target clip, preserving references and
 * associated per-clip metadata. Returns the new index, or -1 if unchanged/invalid. */
final class TimelineMove {
    static int move(List<Project.Clip> clips,int from,int target,boolean after){
        if(clips==null || from<0 || from>=clips.size() || target<0 || target>=clips.size()) return -1;
        int insertion=target+(after?1:0);
        if(from<insertion) insertion--;
        insertion=Math.max(0,Math.min(clips.size()-1,insertion));
        if(from==insertion) return -1;
        Project.Clip moving=clips.remove(from);
        clips.add(insertion,moving);
        return insertion;
    }
    private TimelineMove(){}
}
