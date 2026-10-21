package at.storyforge.editor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.FaceDetector;
import androidx.exifinterface.media.ExifInterface;

/**
 * Works on a downsampled copy. Originals are never modified.
 * EXIF takes priority, while upright faces provide a conservative fallback
 * for photos with missing EXIF orientation. Detection is not guaranteed.
 */
final class PhotoOrientation {
    static final class Result {
        final int degrees;
        final String source;
        Result(int degrees,String source){this.degrees=degrees;this.source=source;}
    }

    static Result inspect(String path) {
        int degrees=0;
        try {
            int orientation=new ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);
            if(orientation==ExifInterface.ORIENTATION_ROTATE_90
                ||orientation==ExifInterface.ORIENTATION_TRANSPOSE)degrees=90;
            else if(orientation==ExifInterface.ORIENTATION_ROTATE_180
                ||orientation==ExifInterface.ORIENTATION_FLIP_VERTICAL)degrees=180;
            else if(orientation==ExifInterface.ORIENTATION_ROTATE_270
                ||orientation==ExifInterface.ORIENTATION_TRANSVERSE)degrees=270;
            if(degrees!=0)return new Result(degrees,"Fotometadaten (EXIF)");
        }catch(Exception ignored){}

        // If the photo was already physically rotated, EXIF cannot tell.
        // Face orientation is a limited heuristic; don't rotate landscapes,
        // buildings or unclear images without an upright-face signal.
        try {
            BitmapFactory.Options o=new BitmapFactory.Options();
            o.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(path,o);
            if(o.outWidth<40||o.outHeight<40)return new Result(0,"");
            int sample=1;
            while(Math.max(o.outWidth,o.outHeight)/sample>600)sample*=2;
            o.inSampleSize=sample;
            o.inJustDecodeBounds=false;
            Bitmap original=BitmapFactory.decodeFile(path,o);
            if(original==null)return new Result(0,"");
            try {
                int[] degreesToTry={0,90,180,270};
                double[] scores=new double[4];
                for(int i=0;i<4;i++) {
                    Bitmap rotated=rotate(original,degreesToTry[i]);
                    try{scores[i]=faceScore(rotated);}
                    finally{if(rotated!=original)rotated.recycle();}
                }
                int best=0;
                for(int i=1;i<4;i++)if(scores[i]>scores[best])best=i;
                if(best>0 && scores[best]>=0.60 && scores[best]>scores[0]+0.28)
                    return new Result(degreesToTry[best],"Gesichtserkennung");
            }finally{original.recycle();}
        }catch(Exception ignored){}
        return new Result(0,"");
    }

    private static double faceScore(Bitmap bmp) {
        int width=bmp.getWidth()&~1,height=bmp.getHeight();
        if(width<2||height<2)return 0;
        Bitmap rgb=Bitmap.createBitmap(width,height,Bitmap.Config.RGB_565);
        try {
            android.graphics.Canvas canvas=new android.graphics.Canvas(rgb);
            canvas.drawBitmap(bmp,0,0,null);
            FaceDetector.Face[] faces=new FaceDetector.Face[3];
            FaceDetector detector=new FaceDetector(width,height,faces.length);
            int found=detector.findFaces(rgb,faces);
            double score=0;
            for(int i=0;i<found;i++)
                if(faces[i]!=null && faces[i].confidence()>.48f)
                    score+=faces[i].confidence();
            return score;
        }finally{rgb.recycle();}
    }

    static Bitmap rotate(Bitmap image,int degrees){
        if(image==null||degrees%360==0)return image;
        Matrix transform=new Matrix();
        transform.postRotate(degrees);
        return Bitmap.createBitmap(image,0,0,image.getWidth(),image.getHeight(),transform,true);
    }
    private PhotoOrientation(){}
}
