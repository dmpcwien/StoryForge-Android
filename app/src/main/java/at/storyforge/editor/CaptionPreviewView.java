package at.storyforge.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;

/** Draggable caption layout preview with the same 1080-wide coordinates as MP4 export. */
final class CaptionPreviewView extends View {
    interface PositionChanged { void moved(double x,double y); }
    private final Paint imagePaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final TextPaint textPaint=new TextPaint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
    private final Bitmap frame;
    private String caption="";
    private float fontSize=44;
    private int captionColor=Color.WHITE;
    private double x=.5,y=.78;
    private float lastX,lastY;
    private PositionChanged listener;
    CaptionPreviewView(Context context,Bitmap bitmap){
        super(context);frame=bitmap;setBackgroundColor(Color.BLACK);
        setContentDescription("Text-Vorschau: Mit dem Finger ziehen, um die Textposition zu ändern.");
    }
    void setPositionChanged(PositionChanged callback){listener=callback;}
    void update(String content,float size,int color,double xx,double yy){
        caption=content==null?"":content;
        fontSize=Math.max(16,Math.min(140,size));
        captionColor=color;
        x=clamp(xx,.0,1.0);y=clamp(yy,.0,1.0);
        invalidate();
    }
    private static double clamp(double n,double low,double high){
        return Math.max(low,Math.min(high,n));
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        int w=getWidth(),h=getHeight();if(w<1||h<1)return;
        if(frame!=null&&!frame.isRecycled()){
            float scale=Math.min(w/(float)frame.getWidth(),h/(float)frame.getHeight());
            float bw=frame.getWidth()*scale,bh=frame.getHeight()*scale;
            canvas.drawBitmap(frame,null,new RectF((w-bw)/2,(h-bh)/2,
                (w+bw)/2,(h+bh)/2),imagePaint);
        }
        if(caption.isEmpty())return;
        float factor=w/1080f;
        textPaint.setColor(captionColor);
        textPaint.setTextSize(fontSize*factor);
        textPaint.setTypeface(android.graphics.Typeface.create(
            "sans-serif-black",android.graphics.Typeface.BOLD));
        textPaint.setShadowLayer(4*factor,1*factor,3*factor,Color.BLACK);
        int maxWidth=Math.max(1,(int)(w*.88));
        StaticLayout layout=StaticLayout.Builder.obtain(caption,0,caption.length(),
            textPaint,maxWidth).setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(false).build();
        float left=(float)clamp(w*x-maxWidth/2.0,0,Math.max(0,w-maxWidth));
        float top=(float)clamp(h*y-layout.getHeight()/2.0,0,Math.max(0,h-layout.getHeight()));
        canvas.save();canvas.translate(left,top);layout.draw(canvas);canvas.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent event){
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:
                lastX=event.getX();lastY=event.getY();
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if(getWidth()>0&&getHeight()>0){
                    x=clamp(x+(event.getX()-lastX)/getWidth(),0,1);
                    y=clamp(y+(event.getY()-lastY)/getHeight(),0,1);
                    lastX=event.getX();lastY=event.getY();
                    invalidate();
                    if(listener!=null)listener.moved(x,y);
                }
                return true;
            case MotionEvent.ACTION_UP:
                performClick();
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default:return true;
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
