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
    /** Gap 0 is before the first clip; gap size is after the last clip.
     * The dragged clip is removed before inserting into its new position.
     */
    static int moveToGap(List<Project.Clip> clips,int from,int gap){
        if(clips==null || clips.isEmpty() || from<0 || from>=clips.size()
            || gap<0 || gap>clips.size())return -1;
        int position=gap-(from<gap?1:0);
        if(position==from)return -1;
        Project.Clip item=clips.remove(from);
        clips.add(position,item);
        return position;
    }
    private TimelineMove(){}
}
