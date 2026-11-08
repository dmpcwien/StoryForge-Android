package at.storyforge.editor;

/** Bounds and minimum-length rules for nondestructive video trimming. */
final class TrimRange {
    static final double MIN_SECONDS=.15;

    static double clamp(double number,double minimum,double maximum){
        if(Double.isNaN(number)||number==Double.NEGATIVE_INFINITY)return minimum;
        if(number==Double.POSITIVE_INFINITY)return maximum;
        return Math.max(minimum,Math.min(maximum,number));
    }
    static double[] normalize(double duration,double start,double end){
        double total=Math.max(MIN_SECONDS,duration);
        double a=clamp(start,0,total-MIN_SECONDS);
        double b=clamp(end,a+MIN_SECONDS,total);
        return new double[]{a,b};
    }
    static double[] moveStart(double duration,double start,double end){
        double[] current=normalize(duration,0,end);
        return new double[]{clamp(start,0,current[1]-MIN_SECONDS),current[1]};
    }
    static double[] moveEnd(double duration,double start,double end){
        double[] current=normalize(duration,start,duration);
        return new double[]{current[0],clamp(end,current[0]+MIN_SECONDS,duration)};
    }
    private TrimRange(){}
}
