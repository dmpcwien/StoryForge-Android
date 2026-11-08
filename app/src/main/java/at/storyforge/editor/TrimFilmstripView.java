package at.storyforge.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/** Visual full-video filmstrip with independent start/end handles. */
final class TrimFilmstripView extends View {
    interface OnTrimChanged { void changed(double start,double end,double seekSeconds); }
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Paint overlay=new Paint(Paint.ANTI_ALIAS_FLAG);
    private Bitmap[] thumbnails=new Bitmap[0];
    private final double duration;
    private double start,end;
    private int selectedHandle=-1; // 0=start, 1=end
    private OnTrimChanged callback;
    private float downX;
    private static final int BLUE=0xFF9A78FF;
    TrimFilmstripView(Context context,double seconds,double initialStart,double initialEnd){
        super(context);
        duration=Math.max(TrimRange.MIN_SECONDS,seconds);
        double[] values=TrimRange.normalize(duration,initialStart,initialEnd);
        start=values[0];end=values[1];
        setContentDescription("Videobildstreifen. Ziehe den linken Griff für den Anfang und den rechten Griff für das Ende.");
        setMinimumHeight((int)(96*getResources().getDisplayMetrics().density));
    }
    void setOnTrimChanged(OnTrimChanged listener){callback=listener;}
    void setThumbnails(Bitmap[] frames){
        if(thumbnails!=null)for(Bitmap bitmap:thumbnails)
            if(bitmap!=null && !bitmap.isRecycled())bitmap.recycle();
        thumbnails=frames==null?new Bitmap[0]:frames;
        invalidate();
    }
    void release(){
        if(thumbnails!=null)for(Bitmap bitmap:thumbnails)
            if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();
        thumbnails=new Bitmap[0];
    }
    double start(){return start;}
    double end(){return end;}
    void setRange(double from,double to){
        double[] next=TrimRange.normalize(duration,from,to);
        start=next[0];end=next[1];invalidate();
    }
    private float xFor(double value){
        float w=Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight());
        return getPaddingLeft()+(float)(value/duration)*w;
    }
    private double secondAt(float x){
        return TrimRange.clamp(((x-getPaddingLeft())/
            (float)Math.max(1,getWidth()-getPaddingLeft()-getPaddingRight()))*duration,0,duration);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float left=getPaddingLeft(),right=getWidth()-getPaddingRight();
        float top=getPaddingTop()+5,bottom=getHeight()-getPaddingBottom()-5;
        if(right<=left||bottom<=top)return;
        canvas.drawColor(0xFF151820);
        int count=thumbnails.length;
        if(count>0){
            float frameWidth=(right-left)/count;
            for(int i=0;i<count;i++){
                Bitmap bitmap=thumbnails[i];
                if(bitmap==null||bitmap.isRecycled())continue;
                float l=left+i*frameWidth;
                canvas.drawBitmap(bitmap,null,new RectF(l,top,l+frameWidth+.5f,bottom),paint);
            }
        }
        float x1=xFor(start),x2=xFor(end);
        overlay.setStyle(Paint.Style.FILL);overlay.setColor(0xB8000000);
        canvas.drawRect(left,top,x1,bottom,overlay);
        canvas.drawRect(x2,top,right,bottom,overlay);
        overlay.setColor(0x2A9A78FF);
        canvas.drawRect(x1,top,x2,bottom,overlay);
        overlay.setStyle(Paint.Style.STROKE);overlay.setStrokeWidth(dp(3));
        overlay.setColor(BLUE);
        canvas.drawRect(x1,top,x2,bottom,overlay);
        overlay.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(x1-dp(6),top,x1+dp(6),bottom,dp(4),dp(4),overlay);
        canvas.drawRoundRect(x2-dp(6),top,x2+dp(6),bottom,dp(4),dp(4),overlay);
        overlay.setColor(Color.WHITE);overlay.setStrokeWidth(dp(2));
        canvas.drawLine(x1,top+dp(12),x1,bottom-dp(12),overlay);
        canvas.drawLine(x2,top+dp(12),x2,bottom-dp(12),overlay);
    }
    private float dp(float value){return value*getResources().getDisplayMetrics().density;}
    @Override public boolean onTouchEvent(MotionEvent event){
        float x=event.getX();
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                downX=x;
                float distA=Math.abs(x-xFor(start));
                float distB=Math.abs(x-xFor(end));
                selectedHandle=distA<=distB?0:1;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
                if(selectedHandle<0)return true;
                double seconds=secondAt(x);
                if(selectedHandle==0){
                    double[] values=TrimRange.moveStart(duration,seconds,end);
                    start=values[0];end=values[1];
                }else{
                    double[] values=TrimRange.moveEnd(duration,start,seconds);
                    start=values[0];end=values[1];
                }
                invalidate();
                if(callback!=null)callback.changed(start,end,
                    selectedHandle==0?start:end);
                if(event.getActionMasked()==MotionEvent.ACTION_UP){
                    selectedHandle=-1;getParent().requestDisallowInterceptTouchEvent(false);
                    performClick();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                selectedHandle=-1;getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
