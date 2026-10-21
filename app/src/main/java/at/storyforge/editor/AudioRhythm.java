package at.storyforge.editor;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/** Bounded, on-device PCM analysis, used to estimate rhythmic cuts and silence.
 * The analysis is approximate; unsupported codecs safely fall back to timed edits. */
final class AudioRhythm {
    static final class Signal {
        final double step;
        final double[] level;
        Signal(double step,double[] level){this.step=step;this.level=level;}
    }
    static Signal read(String path,double maxSeconds) {
        MediaExtractor extractor=new MediaExtractor();
        MediaCodec decoder=null;
        try{
            extractor.setDataSource(path);
            int track=-1;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat format=extractor.getTrackFormat(i);
                String mime=format.getString(MediaFormat.KEY_MIME);
                if(mime!=null&&mime.startsWith("audio/")){track=i;break;}
            }
            if(track<0)return new Signal(.10,new double[0]);
            extractor.selectTrack(track);
            MediaFormat fmt=extractor.getTrackFormat(track);
            String mime=fmt.getString(MediaFormat.KEY_MIME);
            decoder=MediaCodec.createDecoderByType(mime);
            decoder.configure(fmt,null,null,0);
            decoder.start();
            final double step=.10;
            int count=(int)Math.min(1200,Math.ceil(Math.min(120,maxSeconds)/step));
            double[] power=new double[count],samples=new double[count];
            MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            boolean endInput=false,endOutput=false;
            int idle=0;
            long maxUs=(long)(Math.min(120,maxSeconds)*1000000);
            while(!endOutput&&idle<200){
                if(!endInput){
                    int index=decoder.dequeueInputBuffer(10000);
                    if(index>=0){
                        ByteBuffer input=decoder.getInputBuffer(index);
                        input.clear();
                        int size=extractor.readSampleData(input,0);
                        long pts=extractor.getSampleTime();
                        if(size<0||pts<0||pts>=maxUs){
                            decoder.queueInputBuffer(index,0,0,Math.max(0,pts),MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            endInput=true;
                        }else{
                            decoder.queueInputBuffer(index,0,size,pts,0);
                            extractor.advance();
                        }
                    }
                }
                int out=decoder.dequeueOutputBuffer(info,10000);
                if(out>=0){
                    idle=0;
                    if(info.size>0){
                        ByteBuffer pcm=decoder.getOutputBuffer(out);
                        if(pcm!=null){
                            pcm.order(ByteOrder.LITTLE_ENDIAN);
                            pcm.position(info.offset);
                            pcm.limit(info.offset+info.size);
                            int rate=fmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)?fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE):44100;
                            int channels=fmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT):2;
                            if(rate<1000)rate=44100;
                            int cursor=0;
                            while(pcm.remaining()>=2){
                                short sample=pcm.getShort();
                                double seconds=info.presentationTimeUs/1000000.0+
                                    (double)(cursor/Math.max(1,channels))/rate;
                                int bin=(int)(seconds/step);
                                if(bin>=0&&bin<count){
                                    double amp=(double)sample/32768.0;
                                    power[bin]+=amp*amp;
                                    samples[bin]++;
                                }
                                cursor++;
                            }
                        }
                    }
                    decoder.releaseOutputBuffer(out,false);
                    if((info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0)endOutput=true;
                }else if(out==MediaCodec.INFO_TRY_AGAIN_LATER)idle++;
            }
            double[] rms=new double[count];
            for(int i=0;i<count;i++)rms[i]=samples[i]>0?Math.sqrt(power[i]/samples[i]):0;
            return new Signal(step,rms);
        }catch(Exception ignored){return new Signal(.10,new double[0]);}
        finally{
            if(decoder!=null){
                try{decoder.stop();}catch(Exception ignored){}
                try{decoder.release();}catch(Exception ignored){}
            }
            try{extractor.release();}catch(Exception ignored){}
        }
    }
    static double estimateBeat(String path){
        Signal sig=read(path,42);
        double[] r=sig.level;
        if(r.length<40)return .5;
        double[] onsets=new double[r.length];
        for(int i=1;i<r.length;i++)onsets[i]=Math.max(0,r[i]-r[i-1]);
        double best=-1,period=.5;
        // 65-180 BPM, reward onset correlation across the time axis.
        for(int lag=3;lag<=9;lag++){
            double score=0,energy=0;
            for(int i=lag;i<onsets.length;i++){
                score+=onsets[i]*onsets[i-lag];
                energy+=onsets[i]*onsets[i];
            }
            if(energy>1e-7 && score/energy>best){
                best=score/energy;period=lag*sig.step;
            }
        }
        return best>.08?period:.5;
    }
    /** Silent interior stretches only; never drop an entire clip or its soundtrack blindly. */
    static List<double[]> activeRanges(String path,double start,double end){
        List<double[]> out=new ArrayList<>();
        if(end-start<5){out.add(new double[]{start,end});return out;}
        Signal signal=read(path,Math.min(120,end));
        double[] a=signal.level;
        if(a.length==0){out.add(new double[]{start,end});return out;}
        double peak=0;
        for(double v:a)peak=Math.max(peak,v);
        if(peak<.008){out.add(new double[]{start,end});return out;}
        double threshold=Math.max(.006,peak*.075);
        double begin=start;
        int silence=-1;
        for(int i=(int)Math.floor(start/signal.step);i<a.length&&i*signal.step<end;i++){
            boolean quiet=a[i]<threshold;
            if(quiet&&silence<0)silence=i;
            if(!quiet&&silence>=0){
                double q0=silence*signal.step,q1=i*signal.step;
                if(q1-q0>1.4&&q0-begin>.7){
                    out.add(new double[]{begin,Math.min(end,q0+.12)});
                    begin=Math.min(end,q1-.12);
                }
                silence=-1;
            }
        }
        if(end-begin>.6)out.add(new double[]{begin,end});
        if(out.isEmpty())out.add(new double[]{start,end});
        return out;
    }
    private AudioRhythm(){}
}
