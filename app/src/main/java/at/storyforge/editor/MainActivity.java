package at.storyforge.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.content.ClipData;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.ScaleGestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Touch-first editor, persistent projects, native offline export. */
public final class MainActivity extends Activity {
    private static final int PICK_MEDIA=410,PICK_MUSIC=411,PICK_AUTO_MEDIA=412,PICK_AUTO_MUSIC=413,PICK_STICKER=414,PICK_LAYER_AUDIO=415,PICK_PLAYLIST=416,PICK_BACKUP_SAVE=417,PICK_BACKUP_LOAD=418;
    private static final int BG=0xFF0D0F13, SURFACE=0xFF1A1D24,
        SURFACE_2=0xFF292E39,PURPLE=0xFF9A78FF,
        CYAN=0xFF85DCE1, WHITE=0xFFF5F6FA, MUTED=0xFFA4ACB9;
    private final ExecutorService fileQueue=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private Project project=new Project();
    private final android.util.LruCache<String,Bitmap> thumbCache=new android.util.LruCache<String,Bitmap>(10 * 1024 * 1024){
        @Override protected int sizeOf(String key,Bitmap value){return value.getByteCount();}
    };
    private int selected=0;
    private LinearLayout screen,timeline,captionList,audioList,stickerList;
    private int autoStyle=0,autoStartClipCount=0,autoMusicStartIndex=0;
    private boolean autoRequested=false;
    private FrameLayout previewContainer;
    private ImageView previewImage;
    private VideoView previewVideo;
    private TextView previewLabel,previewText,totalLabel,musicLabel,selectionLabel,playButton;
    private ProgressBar loading;
    private AlertDialog exportDialog;
    private ExportEngine exporter,previewRenderer;
    private boolean historyRestoring=false;
    private Uri latestExport;
    private boolean playing=false;
    private MediaPlayer musicPreview,playlistPreview;
    private int currentPlaylistIndex=-1;
    private int previewIndex=0;
    private long previewStartedAt=0;
    private long previewElapsedOffsetMs=0;
    private Runnable playbackTick;

    @Override public void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(0);
        loadProject();exporter=new ExportEngine(this);previewRenderer=new ExportEngine(this);
        buildUi();refresh();
        if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},101);
    }
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,float sp,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextColor(color);t.setTextSize(TypedValue.COMPLEX_UNIT_SP,sp);
        if(bold)t.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private GradientDrawable panel(int color,float radius){
        GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
    }
    private GradientDrawable stroke(int fill,int border,float radius){
        GradientDrawable g=panel(fill,radius);g.setStroke(dp(1),border);return g;
    }
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int width,int height){return new LinearLayout.LayoutParams(width<0?width:dp(width),height<0?height:dp(height));}
    private LinearLayout.LayoutParams margin(int width,int height,int top){LinearLayout.LayoutParams p=lp(width,height);p.topMargin=dp(top);return p;}
    private void addSpacer(LinearLayout l,int h){View v=new View(this);l.addView(v,lp(1,h));}
    private TextView button(String label,int color,Runnable click){
        TextView b=text(label,13.5f,WHITE,true);b.setGravity(Gravity.CENTER);b.setBackground(panel(color,12));
        b.setPadding(dp(10),dp(12),dp(10),dp(12));b.setOnClickListener(v->click.run());return b;
    }
    private TextView pill(String s,int color,Runnable r){
        TextView t=text(s,12.5f,WHITE,true);t.setGravity(Gravity.CENTER);t.setBackground(stroke(color,0xFF555A76,12));
        t.setPadding(dp(13),dp(12),dp(13),dp(12));t.setOnClickListener(v->r.run());return t;
    }
    private void buildUi(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        scroll.setVerticalScrollBarEnabled(false);
        screen=vertical();screen.setPadding(dp(16),dp(12),dp(16),dp(34));scroll.addView(screen);setContentView(scroll);

        LinearLayout brand=horizontal();
        LinearLayout brandLeft=vertical();
        TextView eyebrow=text("STORYFORGE   /   PREMIUM STUDIO 1.1",10,CYAN,true);eyebrow.setLetterSpacing(.14f);
        brandLeft.addView(eyebrow);
        TextView title=text("Dein Film. Dein Stil.",25,WHITE,true);brandLeft.addView(title,margin(-1,-2,4));
        brand.addView(brandLeft,new LinearLayout.LayoutParams(0,-2,1));
        TextView export=button("↗ Export",0xFF744DD7,this::startExport);
        brand.addView(export,lp(108,44));screen.addView(brand);
        addSpacer(screen,18);

        previewContainer=new FrameLayout(this);previewContainer.setBackground(panel(Color.BLACK,18));
        previewContainer.setClipToOutline(true);
        previewImage=new ImageView(this);previewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        previewVideo=new VideoView(this);
        previewContainer.addView(previewImage,new FrameLayout.LayoutParams(-1,-1));
        previewContainer.addView(previewVideo,new FrameLayout.LayoutParams(-1,-1));
        previewVideo.setVisibility(View.GONE);
        previewText=text("",27,WHITE,true);previewText.setShadowLayer(dp(5),0,dp(2),Color.BLACK);
        previewText.setGravity(Gravity.CENTER);FrameLayout.LayoutParams textPos=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        textPos.setMargins(dp(14),0,dp(14),dp(60));previewContainer.addView(previewText,textPos);
        previewLabel=text("MEDIEN HINZUFÜGEN",11,WHITE,true);previewLabel.setLetterSpacing(.1f);
        previewLabel.setBackground(panel(0xBB101523,6));previewLabel.setPadding(dp(8),dp(6),dp(8),dp(6));
        FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        badgeParams.setMargins(dp(12),dp(12),0,0);previewContainer.addView(previewLabel,badgeParams);
        LinearLayout.LayoutParams pp=lp(-1,-2);screen.addView(previewContainer,pp);
        // 16:9 preview; keeps portrait video letterboxed without cropping.
        previewContainer.getLayoutParams().height=(int)(getResources().getDisplayMetrics().widthPixels*.62f)-dp(18);

        LinearLayout transport=horizontal();transport.setPadding(0,dp(10),0,dp(8));
        playButton=button("▶ Film abspielen",0xFF3C325D,this::togglePreview);
        transport.addView(playButton,lp(150,43));
        totalLabel=text("00:00 / 00:00",12,MUTED,false);totalLabel.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        transport.addView(totalLabel,new LinearLayout.LayoutParams(0,dp(43),1));
        screen.addView(transport);
        LinearLayout tools=horizontal();
        tools.addView(pill("◉ Effektvorschau",SURFACE,this::renderEffectPreview),new LinearLayout.LayoutParams(0,-2,1));
        TextView status=pill("▤ Exportstatus",SURFACE,this::showExportStatus);
        LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(0,-2,1);st.leftMargin=dp(8);tools.addView(status,st);
        screen.addView(tools,margin(-1,-2,8));

        LinearLayout header=horizontal();TextView tl=text("ZEITLEISTE",11,CYAN,true);tl.setLetterSpacing(.12f);
        header.addView(tl,new LinearLayout.LayoutParams(0,-2,1));
        TextView reorder=text("Lange halten = ziehen · 2 Finger = Zoom",10,MUTED,false);header.addView(reorder);
        screen.addView(header,margin(-1,-2,18));
        HorizontalScrollView timelineScroll=new HorizontalScrollView(this);timelineScroll.setHorizontalScrollBarEnabled(false);
        timeline=horizontal();timeline.setPadding(0,dp(9),dp(12),dp(9));timelineScroll.addView(timeline);
        ScaleGestureDetector zoom=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector d){project.timelineZoom=Math.min(2.8,Math.max(.7,project.timelineZoom*d.getScaleFactor()));refresh();return true;}
            @Override public void onScaleEnd(ScaleGestureDetector d){saveProject();}
        });
        timelineScroll.setOnTouchListener((v,event)->{zoom.onTouchEvent(event);return zoom.isInProgress();});
        screen.addView(timelineScroll,margin(-1,112,5));
        selectionLabel=text("Noch keine Medien ausgewählt",12,MUTED,false);
        screen.addView(selectionLabel,margin(-1,-2,4));

        LinearLayout row1=horizontal();row1.setGravity(Gravity.CENTER);
        TextView media=button("＋ Fotos / Videos",0xFF6841BB,this::pickMedia);
        row1.addView(media,new LinearLayout.LayoutParams(0,dp(46),1));
        TextView edit=button("✂ Clip ändern",SURFACE_2,this::editSelected);
        LinearLayout.LayoutParams e=new LinearLayout.LayoutParams(0,dp(46),1);e.leftMargin=dp(8);row1.addView(edit,e);
        screen.addView(row1,margin(-1,-2,8));
        TextView reversePhotos=pill("↩  Reihenfolge umkehren",SURFACE_2,this::reverseMediaDialog);
        screen.addView(reversePhotos,margin(-1,-2,8));
        TextView auto=button("✦  AUTOMATISCHEN FILM ERSTELLEN",0xFF393052,this::startAutoWizard);
        screen.addView(auto,margin(-1,47,7));
        TextView undoAuto=pill("↶ Letzten automatischen Schnitt rückgängig",SURFACE,this::undoAutoFilm);
        screen.addView(undoAuto,margin(-1,-2,5));
        LinearLayout toolHistory=horizontal();
        toolHistory.addView(pill("↶ Rückgängig",SURFACE,this::undoProject),new LinearLayout.LayoutParams(0,-2,1));
        TextView redo=pill("↷ Wiederholen",SURFACE,this::redoProject);
        LinearLayout.LayoutParams rdo=new LinearLayout.LayoutParams(0,-2,1);rdo.leftMargin=dp(6);toolHistory.addView(redo,rdo);
        TextView backup=pill("⤓ Backup",SURFACE,this::saveBackup);
        LinearLayout.LayoutParams bkp=new LinearLayout.LayoutParams(0,-2,1);bkp.leftMargin=dp(6);toolHistory.addView(backup,bkp);
        TextView restore=pill("⤒ Laden",SURFACE,this::loadBackup);
        LinearLayout.LayoutParams rst=new LinearLayout.LayoutParams(0,-2,1);rst.leftMargin=dp(6);toolHistory.addView(restore,rst);
        screen.addView(toolHistory,margin(-1,-2,8));
        LinearLayout row2=horizontal();
        row2.addView(pill("⇄ Übergang",SURFACE,this::chooseTransition),new LinearLayout.LayoutParams(0,-2,1));
        TextView addTitle=pill("T  Text",SURFACE,this::addCaption);
        LinearLayout.LayoutParams pc=new LinearLayout.LayoutParams(0,-2,1);pc.leftMargin=dp(8);row2.addView(addTitle,pc);
        TextView settings=pill("⚙ Qualität",SURFACE,this::qualityDialog);
        LinearLayout.LayoutParams qc=new LinearLayout.LayoutParams(0,-2,1);qc.leftMargin=dp(8);row2.addView(settings,qc);
        screen.addView(row2,margin(-1,-2,8));
        LinearLayout row3=horizontal();
        row3.addView(pill("✂ Teilen",SURFACE,this::splitSelected),new LinearLayout.LayoutParams(0,-2,1));
        TextView fx=pill("◈ Farbe/Tempo",SURFACE,this::editEffects);
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.leftMargin=dp(7);row3.addView(fx,fp);
        TextView ov=pill("▧ Overlay",SURFACE,this::pickSticker);
        LinearLayout.LayoutParams op=new LinearLayout.LayoutParams(0,-2,1);op.leftMargin=dp(7);row3.addView(ov,op);
        screen.addView(row3,margin(-1,-2,8));

        LinearLayout musicBar=horizontal();musicBar.setPadding(dp(14),dp(10),dp(10),dp(10));musicBar.setBackground(stroke(SURFACE,0xFF353957,14));
        LinearLayout musicTitle=vertical();musicTitle.addView(text("♪  SOUNDTRACK",11,CYAN,true));
        musicLabel=text("Noch keine Musik gewählt",13,WHITE,false);musicTitle.addView(musicLabel,margin(-1,-2,4));
        musicBar.addView(musicTitle,new LinearLayout.LayoutParams(0,-2,1));
        musicBar.addView(button("Wählen",SURFACE_2,this::pickMusic),lp(85,38));
        screen.addView(musicBar,margin(-1,-2,14));
        LinearLayout aux=horizontal();
        aux.addView(pill("♫ Lautstärke",SURFACE,this::soundDialog),new LinearLayout.LayoutParams(0,-2,1));
        TextView remove=pill("× Musik entfernen",SURFACE,()->{project.musicPath="";project.musicName="";saveProject();refresh();});
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-2,1);rp.leftMargin=dp(8);aux.addView(remove,rp);
        screen.addView(aux,margin(-1,-2,8));

        LinearLayout audioHeading=horizontal();audioHeading.addView(text("♫  AUDIOSPUREN",11,CYAN,true),new LinearLayout.LayoutParams(0,-2,1));
        audioHeading.addView(pill("Playlist +",0xFF393052,this::pickPlaylist),lp(97,-2));
        audioHeading.addView(pill("SFX +",SURFACE_2,this::pickLayerAudio),lp(74,-2));
        screen.addView(audioHeading,margin(-1,-2,14));
        audioList=vertical();screen.addView(audioList,margin(-1,-2,7));
        screen.addView(text("▧  BILD-OVERLAYS",11,CYAN,true),margin(-1,-2,14));
        stickerList=vertical();screen.addView(stickerList,margin(-1,-2,7));
        TextView captionsHeading=text("TEXT-EINBLENDUNGEN",11,CYAN,true);captionsHeading.setLetterSpacing(.1f);
        screen.addView(captionsHeading,margin(-1,-2,17));
        captionList=vertical();screen.addView(captionList,margin(-1,-2,7));

        loading=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);loading.setVisibility(View.GONE);
        loading.setIndeterminate(true);loading.getProgressDrawable().setColorFilter(PURPLE,PorterDuff.Mode.SRC_IN);
        screen.addView(loading,margin(-1,5,15));
        TextView info=text("Offline · Originale bleiben unverändert · MP4 in Galerie/Movies/StoryForge",11,MUTED,false);
        info.setGravity(Gravity.CENTER);screen.addView(info,margin(-1,-2,20));
        TextView previewNote=text("Schnittfolge in Echtzeit; Übergänge und Effekte werden beim Export vollständig gerendert.",11,MUTED,false);
        previewNote.setGravity(Gravity.CENTER);screen.addView(previewNote,margin(-1,-2,7));
    }
    private String time(double seconds){int s=(int)Math.max(0,seconds);return String.format(Locale.GERMANY,"%02d:%02d",s/60,s%60);}
    private void refresh(){
        if(timeline==null)return;
        timeline.removeAllViews();
        if(project.clips.isEmpty()){
            TextView hint=text("  +  Fotos und Videos hinzufügen, um deinen Film zu starten",13,MUTED,false);
            hint.setBackground(panel(SURFACE,12));timeline.addView(hint,lp(295,88));
        }
        for(int i=0;i<project.clips.size();i++){
            final int index=i;Project.Clip c=project.clips.get(i);
            LinearLayout card=vertical();
            card.setBackground(stroke(selected==i?0xFF49366F:SURFACE,selected==i?PURPLE:0xFF42475F,12));
            card.setPadding(dp(7),dp(7),dp(7),dp(6));
            ImageView thumb=new ImageView(this);thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try{Bitmap bm=thumbnail(c);if(bm!=null)thumb.setImageBitmap(bm);else thumb.setBackground(panel(SURFACE_2,7));}
            catch(Exception ignored){thumb.setBackground(panel(SURFACE_2,7));}
            card.addView(thumb,lp((int)(110*project.timelineZoom),61));
            String nm=c.name.length()>17?c.name.substring(0,14)+"…":c.name;
            TextView name=text((c.image?"▣ ":"▶ ")+nm,10.5f,WHITE,true);name.setSingleLine(true);
            card.addView(name,margin((int)(110*project.timelineZoom),20,3));
            card.addView(text(time(c.length())+"   "+(i==0?"START":transitionLabel(c.transition)),10,MUTED,false));
            LinearLayout.LayoutParams p=lp((int)(124*project.timelineZoom),112);p.rightMargin=dp(7);timeline.addView(card,p);
            card.setOnClickListener(v->{stopPreview();selected=index;showSelected();refresh();});
            card.setOnLongClickListener(v->{
                v.startDragAndDrop(ClipData.newPlainText("clip",Integer.toString(index)),new View.DragShadowBuilder(v),null,0);return true;
            });
            card.setOnDragListener((v,event)->{
                if(event.getAction()!=DragEvent.ACTION_DROP)return true;
                try{
                    int from=Integer.parseInt(event.getClipData().getItemAt(0).getText().toString());
                    if(from>=0&&from<project.clips.size()&&from!=index){
                        Project.Clip moving=project.clips.remove(from);
                        project.clips.add(Math.min(index,project.clips.size()),moving);
                        selected=Math.min(index,project.clips.size()-1);saveProject();refresh();
                    }
                }catch(Exception ignored){toast("Verschieben fehlgeschlagen");}
                return true;
            });
        }
        selected=Math.max(0,Math.min(selected,Math.max(0,project.clips.size()-1)));
        selectionLabel.setText(project.clips.isEmpty()?"Noch keine Medien ausgewählt":
            "Clip "+(selected+1)+" von "+project.clips.size()+"  ·  "+project.clips.get(selected).name);
        totalLabel.setText(project.clips.size()+" Clips   ·   "+time(project.duration()));
        musicLabel.setText(TextUtils.isEmpty(project.musicPath)?"Noch keine Musik gewählt":project.musicName);
        captionList.removeAllViews();
        if(project.captions.isEmpty()){captionList.addView(text("Noch keine Texte. Tippe auf „T Text“.",12,MUTED,false));}
        for(int i=0;i<project.captions.size();i++){
            final int at=i;Project.Caption c=project.captions.get(i);
            LinearLayout r=horizontal();r.setPadding(dp(12),dp(9),dp(7),dp(9));r.setBackground(panel(SURFACE,10));
            String show=c.text.length()>32?c.text.substring(0,29)+"…":c.text;
            TextView t=text("T   "+show+"\n     "+time(c.start)+" – "+time(c.end),12,WHITE,false);
            r.addView(t,new LinearLayout.LayoutParams(0,-2,1));
            r.addView(pill("Bearbeiten",SURFACE_2,()->editCaption(at)),lp(90,-2));
            captionList.addView(r,margin(-1,-2,5));
        }
        if(audioList!=null){
            audioList.removeAllViews();
            if(project.audioTracks.isEmpty())audioList.addView(text("Playlist + fügt mehrere Lieder hintereinander ein.",11,MUTED,false));
            int songNumber=0;
            for(int i=0;i<project.audioTracks.size();i++){
                final int at=i;Project.AudioTrack a=project.audioTracks.get(i);
                LinearLayout row=horizontal();
                if(a.sequential) songNumber++;
                row.addView(text((a.sequential?"♫ "+songNumber+". ":"♪ ")+a.name+"  ·  "+time(a.start),12,a.sequential?CYAN:WHITE,false),
                    new LinearLayout.LayoutParams(0,-2,1));
                if(a.sequential) {
                    row.addView(pill("↑",SURFACE_2,()->movePlaylist(at,-1)),lp(33,-2));
                    row.addView(pill("↓",SURFACE_2,()->movePlaylist(at,1)),lp(33,-2));
                }
                row.addView(pill("Ändern",SURFACE_2,()->editAudio(at)),lp(77,-2));
                audioList.addView(row,margin(-1,-2,6));
            }
        }
        if(stickerList!=null){
            stickerList.removeAllViews();
            for(int i=0;i<project.stickers.size();i++){
                final int at=i;Project.Sticker st=project.stickers.get(i);
                LinearLayout row=horizontal();
                row.addView(text("▧  "+st.name+"  ·  "+time(st.start)+"–"+time(st.end),12,WHITE,false),new LinearLayout.LayoutParams(0,-2,1));
                row.addView(pill("Bearbeiten",SURFACE_2,()->editSticker(at)),lp(90,-2));
                stickerList.addView(row,margin(-1,-2,6));
            }
        }
        if(!playing)showSelected();
    }
    private Bitmap thumbnail(Project.Clip c){
        String key=c.path+":"+c.start+":"+c.rotationDegrees;
        Bitmap cached=thumbCache.get(key);
        if(cached!=null)return cached;
        Bitmap raw;
        if(c.image){BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(c.path,o);
            o.inSampleSize=Math.max(1,Math.max(o.outWidth/350,o.outHeight/250));o.inJustDecodeBounds=false;
            raw=BitmapFactory.decodeFile(c.path,o);
        }else{
            MediaMetadataRetriever r=new MediaMetadataRetriever();
            try{r.setDataSource(c.path);raw=r.getFrameAtTime((long)(c.start*1_000_000),MediaMetadataRetriever.OPTION_CLOSEST_SYNC);}
            finally{try{r.release();}catch(Exception ignored){}}
        }
        if(raw==null)return null;
        if(c.rotationDegrees!=0){
            Bitmap oriented=PhotoOrientation.rotate(raw,c.rotationDegrees);
            if(oriented!=raw)raw.recycle();
            raw=oriented;
        }
        Bitmap scaled=Bitmap.createScaledBitmap(raw,200,130,true);
        if(raw!=scaled)raw.recycle();
        thumbCache.put(key,scaled);return scaled;
    }
    private void showSelected(){
        previewText.setText("");
        if(project.clips.isEmpty()){
            previewImage.setImageDrawable(null);previewImage.setVisibility(View.VISIBLE);previewVideo.setVisibility(View.GONE);
            previewLabel.setText("START / LEERES PROJEKT");return;
        }
        showClip(selected,false);
    }
    private void showClip(int index,boolean autoPlay){
        if(index<0||index>=project.clips.size())return;
        Project.Clip c=project.clips.get(index);
        previewLabel.setText((index+1)+" / "+project.clips.size()+"     "+time(c.length())
            +(c.rotationDegrees!=0?"  ↻ "+c.rotationDegrees+"°":""));
        if(c.image){
            previewVideo.setRotation(0);
            try{previewVideo.stopPlayback();}catch(Exception ignored){}
            previewVideo.setVisibility(View.GONE);previewImage.setVisibility(View.VISIBLE);
            BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(c.path,o);
            o.inSampleSize=Math.max(1,Math.max(o.outWidth/1400,o.outHeight/1400));o.inJustDecodeBounds=false;
            Bitmap image=BitmapFactory.decodeFile(c.path,o);
            if(image!=null && c.rotationDegrees!=0) {
                Bitmap corrected=PhotoOrientation.rotate(image,c.rotationDegrees);
                if(corrected!=image)image.recycle();
                image=corrected;
            }
            previewImage.setImageBitmap(image);
        }else{
            previewImage.setVisibility(View.GONE);previewVideo.setVisibility(View.VISIBLE);
            previewVideo.setRotation(c.rotationDegrees);
            previewVideo.setVideoPath(c.path);
            previewVideo.setOnPreparedListener(mp->{
                try{mp.setVolume((float)project.originalVolume,(float)project.originalVolume);
                    previewVideo.seekTo((int)(c.start*1000));
                    if(autoPlay&&playing)previewVideo.start();}catch(Exception ignored){}
            });
        }
    }
    private String transitionLabel(String s){
        switch(s){case "cut":return "Schnitt";case "fade":return "Überblenden";case "fadeblack":return "Schwarz";
            case "fadewhite":return "Weiß";case "dissolve":return "Auflösen";case "wipeleft":return "Wischen ←";
            case "wiperight":return "Wischen →";case "slideleft":return "Schieben ←";
            case "slideright":return "Schieben →";case "circleopen":return "Kreis";
            case "pixelize":return "Pixel";case "smoothleft":return "Sanft";
            case "zoomin":return "Zoom";default:return s;}
    }
    private void pickMedia(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_MEDIA);
    }
    private void pickPlaylist(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
        startActivityForResult(i,PICK_PLAYLIST);
    }
    private void reflowPlaylist(){
        double cursor=0;
        int number=0;
        for(Project.AudioTrack song:project.audioTracks){
            if(!song.sequential)continue;
            double length=Math.max(.1,song.end-song.offset);
            double fade=number==0?0:Math.min(project.musicCrossfade,Math.min(length/3,cursor/3));
            song.start=Math.max(0,cursor-fade);
            song.fadeIn=number==0?.15:fade;
            song.fadeOut=Math.min(project.musicCrossfade,length/3);
            cursor=song.start+length;
            number++;
        }
    }
    private void movePlaylist(int index,int direction){
        int neighbour=index+direction;
        while(neighbour>=0 && neighbour<project.audioTracks.size()
                && !project.audioTracks.get(neighbour).sequential)neighbour+=direction;
        if(neighbour<0||neighbour>=project.audioTracks.size())return;
        java.util.Collections.swap(project.audioTracks,index,neighbour);
        reflowPlaylist();saveProject();refresh();
    }
    private double audioDuration(String path){
        MediaMetadataRetriever meta=new MediaMetadataRetriever();
        try{
            meta.setDataSource(path);
            String duration=meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration==null?120:Math.max(.1,Double.parseDouble(duration)/1000);
        }catch(Exception ignored){return 120;}
        finally{try{meta.release();}catch(Exception ignored){}}
    }
    private void pickLayerAudio(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("audio/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_LAYER_AUDIO);
    }
    private void pickSticker(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("image/*");startActivityForResult(i,PICK_STICKER);
    }
    private void startAutoWizard(){
        new AlertDialog.Builder(this).setTitle("Automatisch Film erstellen")
            .setSingleChoiceItems(SmartMontage.STYLES,SmartMontage.preset(project.filmTemplate),(d,which)->autoStyle=which)
            .setPositiveButton("Medien auswählen",(d,w)->{
                autoStartClipCount=project.clips.size();autoRequested=true;
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","video/*"});
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,PICK_AUTO_MEDIA);
            })
            .setNeutralButton("Vorhandene Medien nutzen",(d,w)->{
                if(project.clips.isEmpty()){toast("Bitte zuerst Bilder und Videos hinzufügen.");return;}
                autoStartClipCount=0;autoRequested=true;openAutoMusicPicker();
            }).setNegativeButton("Abbrechen",null).show();
    }
    private void openAutoMusicPicker(){
        autoMusicStartIndex=project.audioTracks.size();
        new AlertDialog.Builder(this).setTitle("Musik für den Film")
            .setMessage("Wähle ein oder mehrere Lieder. Du kannst den Film auch ohne Musik erstellen.")
            .setPositiveButton("Musik auswählen",(d,w)->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/*");i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
                startActivityForResult(i,PICK_AUTO_MUSIC);
            })
            .setNegativeButton("Ohne Musik",(d,w)->applyAutoFilm()).show();
    }
    private void pickMusic(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("audio/*");
        startActivityForResult(i,PICK_MUSIC);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null){
            if(request==PICK_AUTO_MUSIC&&autoRequested)applyAutoFilm();
            if(request==PICK_AUTO_MEDIA)autoRequested=false;
            return;
        }
        if(request==PICK_BACKUP_SAVE){
            Uri target=data.getData();
            if(target!=null){
                loading.setVisibility(View.VISIBLE);
                fileQueue.execute(()->{
                    String message;
                    try{ProjectBackup.write(this,project,target);message="Projekt inklusive Medien gesichert.";}
                    catch(Exception ex){message="Backup-Fehler: "+ex.getMessage();}
                    final String done=message;
                    runOnUiThread(()->{loading.setVisibility(View.GONE);toast(done);});
                });
            }
            return;
        }
        if(request==PICK_BACKUP_LOAD){
            Uri target=data.getData();
            if(target!=null){
                loading.setVisibility(View.VISIBLE);
                fileQueue.execute(()->{
                    try{
                        Project restored=ProjectBackup.read(this,target);
                        runOnUiThread(()->{
                            project=restored;selected=0;stopPreview();saveProject();refresh();
                            loading.setVisibility(View.GONE);toast("Projektsicherung wiederhergestellt.");
                        });
                    }catch(Exception ex){
                        String msg=ex.getMessage();
                        runOnUiThread(()->{loading.setVisibility(View.GONE);toast("Sicherung ungültig: "+msg);});
                    }
                });
            }
            return;
        }
        List<Uri> all=new ArrayList<>();
        if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)all.add(data.getClipData().getItemAt(i).getUri());
        else if(data.getData()!=null)all.add(data.getData());
        if(all.isEmpty()){if(request==PICK_AUTO_MEDIA)autoRequested=false;return;}
        loading.setVisibility(View.VISIBLE);
        fileQueue.execute(()->{
            String error=null;int count=0;
            for(Uri u:all){
                try{
                    String mime=getContentResolver().getType(u);
                    boolean music=request==PICK_MUSIC||request==PICK_LAYER_AUDIO||request==PICK_AUTO_MUSIC||request==PICK_PLAYLIST;
                    String name=fileName(u);
                    String lower=name.toLowerCase(Locale.ROOT);
                    boolean image=request==PICK_STICKER || (!music && ((mime!=null&&mime.startsWith("image/")) ||
                        lower.endsWith(".jpg")||lower.endsWith(".jpeg")||lower.endsWith(".png")||lower.endsWith(".webp")||lower.endsWith(".heic")));
                    boolean video=!music && ((mime!=null&&mime.startsWith("video/")) ||
                        lower.endsWith(".mp4")||lower.endsWith(".mov")||lower.endsWith(".mkv")||lower.endsWith(".webm"));
                    if(!music&&!image&&!video)throw new Exception("Nicht unterstützte Datei: "+u);
                    File copy=importFile(u,name);
                    if(request==PICK_STICKER){
                        Project.Sticker st=new Project.Sticker();st.path=copy.getAbsolutePath();st.name=name;
                        st.end=Math.min(project.duration(),5);project.stickers.add(st);
                    } else if(music){
                        if(request==PICK_MUSIC){project.musicPath=copy.getAbsolutePath();project.musicName=name;}
                        else {
                            Project.AudioTrack a=new Project.AudioTrack();a.path=copy.getAbsolutePath();a.name=name;
                            a.end=audioDuration(a.path);
                            if(request==PICK_PLAYLIST){
                                if(!project.musicPath.isEmpty()){
                                    Project.AudioTrack prev=new Project.AudioTrack();
                                    prev.path=project.musicPath;prev.name=project.musicName;
                                    prev.end=audioDuration(prev.path);prev.sequential=true;
                                    project.audioTracks.add(0,prev);
                                    project.musicPath="";project.musicName="";
                                }
                                a.sequential=true;
                            }
                            if(request==PICK_AUTO_MUSIC){
                                for(Project.AudioTrack old:project.audioTracks)
                                    a.start=Math.max(a.start,old.start+old.end-old.offset);
                            }
                            project.audioTracks.add(a);
                            if(request==PICK_PLAYLIST)reflowPlaylist();
                        }
                    } else{
                        Project.Clip c=new Project.Clip();c.path=copy.getAbsolutePath();c.name=name;c.image=image;
                        if(image){
                            c.sourceDuration=4;c.end=4;
                            PhotoOrientation.Result orientation=PhotoOrientation.inspect(c.path);
                            c.rotationDegrees=orientation.degrees;
                            c.rotationSource=orientation.source;
                        }
                        else{
                            MediaMetadataRetriever r=new MediaMetadataRetriever();
                            try{r.setDataSource(c.path);
                                String millis=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                                if(millis==null)throw new Exception("Videolänge konnte nicht gelesen werden: "+name);
                                c.sourceDuration=Double.parseDouble(millis)/1000;
                                if(c.sourceDuration<.2)throw new Exception("Video ist zu kurz: "+name);
                                String has=r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
                                c.audio="yes".equalsIgnoreCase(has)||"1".equals(has);
                                c.end=c.sourceDuration;
                            }finally{r.release();}
                        }
                        project.clips.add(c);
                    }
                    count++;
                }catch(Exception ex){error=ex.getMessage();}
            }
            saveProject();String issue=error;int added=count;
            runOnUiThread(()->{
                loading.setVisibility(View.GONE);refresh();
                if(request==PICK_AUTO_MEDIA&&autoRequested){if(added>0)openAutoMusicPicker();else{autoRequested=false;toast("Keine Medien importiert.");}}
                if(request==PICK_AUTO_MUSIC&&autoRequested)applyAutoFilm();
                if(request==PICK_PLAYLIST&&added>0)toast("Playlist erstellt. Mit ↑ ↓ kannst du die Reihenfolge ändern.");
                Toast.makeText(this,added+" Datei(en) übernommen"+(issue!=null?" · Fehler: "+issue:""),Toast.LENGTH_LONG).show();
            });
        });
    }
    private String fileName(Uri uri){
        String name=null;
        try(Cursor cursor=getContentResolver().query(uri,null,null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){
                int column=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if(column>=0)name=cursor.getString(column);
            }
        }catch(Exception ignored){}
        if(name==null||name.isEmpty())name="Medium_"+System.currentTimeMillis();
        return name;
    }
    private File importFile(Uri uri,String name)throws Exception{
        File dir=new File(getFilesDir(),"media");if(!dir.exists()&&!dir.mkdirs())throw new Exception("Kein Speicherplatz verfügbar.");
        String ext="";int point=name.lastIndexOf('.');if(point>=0&&name.length()-point<=8)ext=name.substring(point);
        else {String mime=getContentResolver().getType(uri);ext=mime!=null&&mime.startsWith("image/")?".jpg":mime!=null&&mime.startsWith("audio/")?".mp3":".mp4";}
        File dst=File.createTempFile("sf_",ext,dir);
        try(InputStream input=getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(dst)){
            if(input==null)throw new Exception("Datei konnte nicht geöffnet werden.");
            byte[] buffer=new byte[128*1024];int read;
            while((read=input.read(buffer))!=-1)output.write(buffer,0,read);
        }catch(Exception e){dst.delete();throw e;}
        return dst;
    }
    private void applyAutoFilm(){
        if(project.clips.isEmpty()){autoRequested=false;toast("Keine Medien für den Film vorhanden.");return;}
        loading.setVisibility(View.VISIBLE);
        // Preserve the previous project for one-click rollback and ordinary undo.
        saveProject();
        try{
            java.nio.file.Files.copy(projectFile().toPath(),
                new File(getFilesDir(),"project-before-auto.json").toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }catch(Exception ignored){}
        final int begin=Math.max(0,autoStartClipCount);
        final int fromSong=autoMusicStartIndex;
        final String style=SmartMontage.STYLES[Math.max(0,Math.min(SmartMontage.STYLES.length-1,autoStyle))];
        autoRequested=false;
        fileQueue.execute(()->{
            String result;
            try{
                String firstSong=project.musicPath;
                if(fromSong<project.audioTracks.size())firstSong=project.audioTracks.get(fromSong).path;
                double period=firstSong==null||firstSong.isEmpty()?.5:AudioRhythm.estimateBeat(firstSong);
                result=SmartMontage.apply(project,begin,style,period);
                int count=project.audioTracks.size()-fromSong;
                if(count>0){
                    double slot=project.duration()/count;
                    for(int j=0;j<count;j++){
                        Project.AudioTrack song=project.audioTracks.get(fromSong+j);
                        double available=song.end-song.offset;
                        double overlap=project.musicCrossfade;
                        song.start=Math.max(0,j*slot-(j==0?0:overlap));
                        song.end=song.offset+Math.min(available,slot+(j==0?0:overlap));
                        song.sequential=true;
                        song.fadeIn=j==0?.15:Math.min(.4,overlap);
                        song.fadeOut=Math.min(.4,overlap);
                    }
                }
            }catch(Exception ex){result="Automatik fehlgeschlagen: "+ex.getMessage();}
            final String message=result;
            runOnUiThread(()->{
                loading.setVisibility(View.GONE);saveProject();refresh();
                new AlertDialog.Builder(this).setTitle("Automatischer Film · "+style)
                    .setMessage(message+"\n\nAlle Clips bleiben in der Zeitleiste bearbeitbar.")
                    .setPositiveButton("Effektvorschau",(d,w)->renderEffectPreview())
                    .setNegativeButton("Weiter bearbeiten",null).show();
            });
        });
    }
    private void undoAutoFilm(){
        File f=new File(getFilesDir(),"project-before-auto.json");
        if(!f.exists()){toast("Kein vorheriger Automatik-Schnitt vorhanden.");return;}
        new AlertDialog.Builder(this).setTitle("Automatik-Schnitt rückgängig?")
            .setMessage("Der zuvor gespeicherte Bearbeitungsstand wird wiederhergestellt.")
            .setPositiveButton("Wiederherstellen",(d,w)->{
                try{
                    byte[] bytes=java.nio.file.Files.readAllBytes(f.toPath());
                    project=Project.fromJson(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));
                    saveProject();f.delete();selected=0;stopPreview();refresh();
                }catch(Exception ex){toast("Wiederherstellung fehlgeschlagen.");}
            }).setNegativeButton("Abbrechen",null).show();
    }
    private void splitSelected(){
        if(project.clips.isEmpty())return;
        Project.Clip c=project.clips.get(selected);
        if(c.image){toast("Fotos können über ihre Dauer angepasst werden.");return;}
        double length=c.end-c.start;
        if(length<.5){toast("Clip ist zu kurz.");return;}
        double middle=c.start+length/2;
        try {
            Project.Clip tail=Project.Clip.fromJson(c.toJson());
            tail.start=middle;tail.transition="cut";c.end=middle;
            project.clips.add(selected+1,tail);selected++;saveProject();refresh();
        }catch(Exception ex){toast("Clip konnte nicht geteilt werden.");}
    }
    private void editEffects(){
        if(project.clips.isEmpty()){toast("Zuerst Medien hinzufügen.");return;}
        Project.Clip c=project.clips.get(selected);
        LinearLayout l=vertical();l.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText speed=number(c.speed),brightness=number(c.brightness),contrast=number(c.contrast),saturation=number(c.saturation);
        l.addView(field("Tempo (0,25 bis 4)",speed));
        l.addView(field("Helligkeit (-1 bis 1)",brightness));
        l.addView(field("Kontrast (0,1 bis 3)",contrast));
        l.addView(field("Sättigung (0 bis 3)",saturation));
        android.widget.CheckBox kb=new android.widget.CheckBox(this);
        kb.setText("Foto langsam zoomen (Ken Burns)");kb.setChecked(c.kenBurns);
        if(c.image)l.addView(kb);
        new AlertDialog.Builder(this).setTitle("Farbe / Geschwindigkeit").setView(l)
            .setPositiveButton("Speichern",(d,w)->{
                try{
                    c.speed=Math.max(.25,Math.min(4,parse(speed)));c.brightness=Math.max(-1,Math.min(1,parse(brightness)));
                    c.contrast=Math.max(.1,Math.min(3,parse(contrast)));c.saturation=Math.max(0,Math.min(3,parse(saturation)));
                    c.kenBurns=kb.isChecked();saveProject();refresh();
                }catch(Exception ex){toast("Ungültige Eingabe.");}
            }).setNegativeButton("Abbrechen",null).show();
    }
    private void editAudio(int index){
        Project.AudioTrack a=project.audioTracks.get(index);
        LinearLayout l=vertical();l.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText start=number(a.start),offset=number(a.offset),end=number(a.end),volume=number(a.volume),
            fadeIn=number(a.fadeIn),fadeOut=number(a.fadeOut);
        l.addView(field("Beginn im Film (Sek.)",start));l.addView(field("Quelle ab (Sek.)",offset));
        l.addView(field("Quelle bis (Sek.)",end));l.addView(field("Lautstärke 0–1,5",volume));
        l.addView(field("Einblenden (Sek.)",fadeIn));l.addView(field("Ausblenden (Sek.)",fadeOut));
        new AlertDialog.Builder(this).setTitle(a.name).setView(l)
            .setPositiveButton("Speichern",(d,w)->{
                try{double st=parse(start),off=parse(offset),en=parse(end);
                    if(st<0||off<0||en<=off)throw new Exception();
                    a.start=a.sequential?a.start:st;a.offset=off;a.end=en;
                    a.volume=Math.max(0,Math.min(1.5,parse(volume)));
                    a.fadeIn=Math.max(0,parse(fadeIn));a.fadeOut=Math.max(0,parse(fadeOut));
                    if(a.sequential)reflowPlaylist();
                    saveProject();refresh();
                }catch(Exception ex){toast("Ungültige Audiozeiten.");}
            }).setNeutralButton("Löschen",(d,w)->{project.audioTracks.remove(index);reflowPlaylist();saveProject();refresh();})
            .setNegativeButton("Abbrechen",null).show();
    }
    private void editSticker(int index){
        Project.Sticker s=project.stickers.get(index);
        LinearLayout l=vertical();l.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText start=number(s.start),end=number(s.end),x=number(s.x*100),y=number(s.y*100),scale=number(s.scale*100);
        l.addView(field("Beginn in Sekunden",start));l.addView(field("Ende in Sekunden",end));
        l.addView(field("Position X in %",x));l.addView(field("Position Y in %",y));
        l.addView(field("Breite in %",scale));
        new AlertDialog.Builder(this).setTitle(s.name).setView(l)
            .setPositiveButton("Speichern",(d,w)->{
                try{s.start=Math.max(0,parse(start));s.end=Math.max(s.start+.1,parse(end));
                    s.x=Math.max(0,Math.min(1,parse(x)/100));s.y=Math.max(0,Math.min(1,parse(y)/100));
                    s.scale=Math.max(.05,Math.min(1,parse(scale)/100));saveProject();refresh();
                }catch(Exception ex){toast("Ungültige Werte.");}
            }).setNeutralButton("Löschen",(d,w)->{project.stickers.remove(index);saveProject();refresh();})
            .setNegativeButton("Abbrechen",null).show();
    }
    private void reverseMediaDialog(){
        if(project.clips.size()<2){
            toast("Für eine umgekehrte Reihenfolge brauchst du mindestens zwei Clips.");
            return;
        }
        // AlertDialog cannot display both setMessage() and setItems() simultaneously
        // on common Android themes: the text replaces the choice list.
        new AlertDialog.Builder(this)
            .setTitle("Was soll rückwärts laufen?")
            .setItems(new String[]{
                "Letzten Clip zuerst – ganzen Film umkehren",
                "Nur Fotos umkehren – Videos am selben Platz"
            },(dialog,which)->{
                if(which==1){
                    int photos=0;
                    for(Project.Clip clip:project.clips)if(clip.image)photos++;
                    if(photos<2){
                        toast("Mindestens zwei Fotos erforderlich.");
                        return;
                    }
                }
                stopPreview();
                int changed=which==0?project.reverseClipOrder():project.reversePhotoOrder();
                if(changed<2){
                    toast("Keine Änderung notwendig.");
                    return;
                }
                selected=0;
                saveProject();
                refresh();
                toast("Reihenfolge umgekehrt. Mit Rückgängig wiederherstellen.");
            })
            .setNegativeButton("Abbrechen",null)
            .show();
    }
    private void editSelected(){
        if(project.clips.isEmpty()){toast("Bitte zuerst Medien hinzufügen.");return;}
        Project.Clip c=project.clips.get(selected);
        String[] actions=c.image
            ? new String[]{"Anzeigedauer festlegen","Nach links verschieben","Nach rechts verschieben",
                "Bild 90° nach rechts drehen","Bild 90° nach links drehen","Bild um 180° drehen",
                "Bildausrichtung erneut prüfen","Clip entfernen"}
            : new String[]{"Anfang und Ende kürzen","Nach links verschieben","Nach rechts verschieben",
                "Video 90° rechts drehen","Video 90° links drehen","Video 180° drehen",
                "Originalausrichtung verwenden","Clip entfernen"};
        new AlertDialog.Builder(this).setTitle(c.name).setItems(actions,(dialog,which)->{
            if(which==0)clipTimingDialog(c);
            if(which==1&&selected>0){java.util.Collections.swap(project.clips,selected,selected-1);selected--;saveProject();refresh();}
            if(which==2&&selected<project.clips.size()-1){java.util.Collections.swap(project.clips,selected,selected+1);selected++;saveProject();refresh();}
            if(which>=3 && which<=5){
                int delta=which==3?90:which==4?270:180;
                c.rotationDegrees=(c.rotationDegrees+delta)%360;
                c.rotationSource="Manuell";
                thumbCache.evictAll();saveProject();refresh();
            }
            if(c.image && which==6){
                loading.setVisibility(View.VISIBLE);
                fileQueue.execute(()->{
                    PhotoOrientation.Result orientation=PhotoOrientation.inspect(c.path);
                    runOnUiThread(()->{
                        c.rotationDegrees=orientation.degrees;c.rotationSource=orientation.source;
                        thumbCache.evictAll();loading.setVisibility(View.GONE);saveProject();refresh();
                        toast(orientation.source.isEmpty()?"Keine eindeutige Ausrichtung erkannt. Bitte manuell drehen.":"Ausrichtung: "+orientation.source);
                    });
                });
            }
            if(!c.image && which==6){
                c.rotationDegrees=0;c.rotationSource="Originalausrichtung";
                thumbCache.evictAll();saveProject();refresh();
            }
            if(which==7)new AlertDialog.Builder(this).setTitle("Clip entfernen?")
                .setMessage("Das Original bleibt auf dem Smartphone gespeichert.")
                .setPositiveButton("Entfernen",(d,w)->{project.clips.remove(selected);selected=Math.max(0,selected-1);saveProject();refresh();})
                .setNegativeButton("Abbrechen",null).show();
        }).show();
    }
    private EditText number(double value){
        EditText e=new EditText(this);e.setText(String.format(Locale.US,"%.2f",value));
        e.setSingleLine(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);return e;
    }
    private LinearLayout field(String title,EditText edit){LinearLayout l=vertical();l.addView(text(title,13,0xFFAEB3C8,false));l.addView(edit);return l;}
    private double parse(EditText e){return Double.parseDouble(e.getText().toString().trim().replace(',','.'));}
    private void clipTimingDialog(Project.Clip c){
        LinearLayout wrap=vertical();wrap.setPadding(dp(20),dp(8),dp(20),0);
        EditText start=number(c.start),end=number(c.end);
        if(!c.image)wrap.addView(field("Beginn in Sekunden",start));
        wrap.addView(field(c.image?"Anzeigedauer in Sekunden":"Ende in Sekunden (Original: "+time(c.sourceDuration)+")",end));
        new AlertDialog.Builder(this).setTitle(c.image?"Bilddauer":"Video schneiden").setView(wrap)
            .setPositiveButton("Speichern",(d,w)->{
                try{
                    double a=c.image?0:parse(start),b=parse(end);
                    if(a<0||b<=a+.1||(!c.image&&b>c.sourceDuration+.05))throw new Exception("Ungültiger Zeitbereich.");
                    c.start=a;c.end=b;stopPreview();saveProject();refresh();
                }catch(Exception ex){toast("Bitte gültige Zeiten angeben.");}
            }).setNegativeButton("Abbrechen",null).show();
    }
    private void chooseTransition(){
        if(project.clips.size()<2){toast("Für einen Übergang brauchst du mindestens zwei Clips.");return;}
        if(selected==0){toast("Bitte den zweiten oder einen späteren Clip auswählen.");return;}
        Project.Clip c=project.clips.get(selected);
        String[] ids={"cut","fade","dissolve","fadeblack","fadewhite","wipeleft","wiperight","slideleft","slideright","smoothleft","circleopen","pixelize","zoomin"};
        String[] names={"Direkter Schnitt","Sanftes Überblenden","Auflösen","Über Schwarz","Über Weiß","Wischen nach links","Wischen nach rechts","Schieben nach links","Schieben nach rechts","Weiche Verschiebung","Kreisöffnung","Pixelübergang","Zoom"};
        int current=0;for(int i=0;i<ids.length;i++)if(ids[i].equals(c.transition))current=i;
        new AlertDialog.Builder(this).setTitle("Übergang vor Clip "+(selected+1))
            .setSingleChoiceItems(names,current,(dlg,which)->{
                c.transition=ids[which];dlg.dismiss();
                if(!"cut".equals(c.transition))transitionDurationDialog(c);
                else{saveProject();refresh();}
            }).setNegativeButton("Abbrechen",null).show();
    }
    private void transitionDurationDialog(Project.Clip c){
        EditText secs=number(c.transitionSeconds);LinearLayout l=vertical();l.setPadding(dp(22),dp(5),dp(22),dp(5));
        l.addView(text("Dauer in Sekunden (z. B. 0,7)",13,0xFF343449,false));l.addView(secs);
        new AlertDialog.Builder(this).setTitle("Übergang: "+transitionLabel(c.transition)).setView(l)
            .setPositiveButton("Speichern",(d,w)->{
                try{c.transitionSeconds=Math.max(.1,Math.min(3,parse(secs)));}catch(Exception ex){c.transitionSeconds=.65;}
                saveProject();refresh();
            }).setNegativeButton("Standard (0,65 s)",(d,w)->{c.transitionSeconds=.65;saveProject();refresh();}).show();
    }
    private void addCaption(){
        if(project.clips.isEmpty()){toast("Bitte zuerst Medien hinzufügen.");return;}
        Project.Caption c=new Project.Caption();c.text="Mein Text";c.start=0;c.end=Math.min(4,project.duration());
        captionDialog(c,-1);
    }
    private void editCaption(int idx){captionDialog(project.captions.get(idx),idx);}
    private void captionDialog(Project.Caption old,int index){
        LinearLayout l=vertical();l.setPadding(dp(20),dp(5),dp(20),dp(10));
        EditText title=new EditText(this);title.setText(old.text);title.setHint("Text eingeben");
        EditText start=number(old.start),end=number(old.end),size=number(old.sizeSp);
        EditText vertical=number(old.y*100);
        l.addView(field("Dein Text",title));l.addView(field("Einblenden ab (Sekunden)",start));
        l.addView(field("Ausblenden bei (Sekunden)",end));l.addView(field("Schriftgröße",size));
        l.addView(field("Höhe auf dem Bild (0–100 %, unten = 85)",vertical));
        new AlertDialog.Builder(this).setTitle(index<0?"Text einfügen":"Text bearbeiten").setView(l)
            .setPositiveButton("Speichern",(dialog,which)->{
                try{
                    String t=title.getText().toString().trim();double a=parse(start),b=parse(end);
                    if(t.isEmpty()||a<0||b<=a||b>project.duration()+.2)throw new Exception("Ungültiger Text oder Zeitbereich.");
                    old.text=t;old.start=a;old.end=b;
                    old.sizeSp=(float)Math.max(16,Math.min(140,parse(size)));
                    old.y=Math.max(.1,Math.min(.9,parse(vertical)/100));
                    if(index<0)project.captions.add(old);
                    saveProject();refresh();
                }catch(Exception ex){toast("Text/Zeit ungültig. Ende darf die Filmlänge nicht überschreiten.");}
            })
            .setNeutralButton(index<0?"Abbrechen":"Löschen",(d,w)->{
                if(index>=0){project.captions.remove(index);saveProject();refresh();}
            }).setNegativeButton("Zurück",null).show();
    }
    private void soundDialog(){
        LinearLayout l=vertical();l.setPadding(dp(22),dp(15),dp(22),dp(15));
        TextView orig=text("Originalton: "+Math.round(project.originalVolume*100)+" %",14,0xFFCBD0DF,true);
        SeekBar original=new SeekBar(this);original.setMax(150);original.setProgress((int)(project.originalVolume*100));
        original.setOnSeekBarChangeListener(seekListener(v->orig.setText("Originalton: "+v+" %")));
        TextView mus=text("Musik: "+Math.round(project.musicVolume*100)+" %",14,0xFFCBD0DF,true);
        SeekBar music=new SeekBar(this);music.setMax(150);music.setProgress((int)(project.musicVolume*100));
        music.setOnSeekBarChangeListener(seekListener(v->mus.setText("Musik: "+v+" %")));
        l.addView(orig);l.addView(original);l.addView(mus,margin(-1,-2,15));l.addView(music);
        new AlertDialog.Builder(this).setTitle("Tonmischung").setView(l).setPositiveButton("Speichern",(d,w)->{
            project.originalVolume=original.getProgress()/100.0;project.musicVolume=music.getProgress()/100.0;
            saveProject();refresh();
        }).setNegativeButton("Abbrechen",null).show();
    }
    private interface ProgressChange{void change(int n);}
    private SeekBar.OnSeekBarChangeListener seekListener(ProgressChange callback){
        return new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean user){callback.change(p);}
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}};
    }
    private void qualityDialog(){
        LinearLayout l=vertical();l.setPadding(dp(20),dp(5),dp(20),dp(10));
        TextView resTitle=text("Auflösung",14,0xFFCBD0DF,true);l.addView(resTitle);
        String[] resolutions={"HD · 720p","Full HD · 1080p","Ultra HD · 4K"};
        android.widget.Spinner res=new android.widget.Spinner(this);
        res.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,resolutions));
        res.setSelection(project.maxSize==720?0:project.maxSize==2160?2:1);l.addView(res);
        l.addView(text("Bildformat",14,0xFFCBD0DF,true),margin(-1,-2,13));
        String[] modes={"Wie Original","Querformat 16:9","Hochformat 9:16","Quadratisch 1:1","Social 4:5"};
        android.widget.Spinner aspect=new android.widget.Spinner(this);
        aspect.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,modes));
        aspect.setSelection("16:9".equals(project.aspect)?1:"9:16".equals(project.aspect)?2:"1:1".equals(project.aspect)?3:"4:5".equals(project.aspect)?4:0);l.addView(aspect);
        l.addView(text("Bildrate",14,0xFFCBD0DF,true),margin(-1,-2,13));
        android.widget.Spinner fps=new android.widget.Spinner(this);
        fps.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"30 FPS","60 FPS"}));
        fps.setSelection(project.fps==60?1:0);l.addView(fps);
        l.addView(text("Kompressionsqualität",14,0xFFCBD0DF,true),margin(-1,-2,13));
        android.widget.Spinner quality=new android.widget.Spinner(this);
        quality.setAdapter(new android.widget.ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Maximal (CRF 15)","Sehr hoch (CRF 17)","Hoch (CRF 20)"}));
        quality.setSelection(project.crf<=15?0:project.crf>=20?2:1);l.addView(quality);
        l.addView(text("Die Auflösung wird anhand der Quellen begrenzt. Aktuell H.264 / SDR: HDR-Metadaten und 10-Bit-HDR werden nicht erhalten.",12,0xFFA1A6BD,false),margin(-1,-2,12));
        new AlertDialog.Builder(this).setTitle("Export-Qualität").setView(l)
            .setPositiveButton("Übernehmen",(d,w)->{
                project.maxSize=res.getSelectedItemPosition()==0?720:res.getSelectedItemPosition()==2?2160:1080;
                project.aspect=new String[]{"original","16:9","9:16","1:1","4:5"}[aspect.getSelectedItemPosition()];
                project.fps=fps.getSelectedItemPosition()==0?30:60;
                project.crf=new int[]{15,17,20}[quality.getSelectedItemPosition()];saveProject();
                toast("Qualität gespeichert.");
            }).setNegativeButton("Abbrechen",null).show();
    }
    private void startExport(){
        if(project.clips.isEmpty()){toast("Bitte zuerst Fotos oder Videos hinzufügen.");return;}
        try{
            saveProject();
            java.nio.file.Files.write(new File(getFilesDir(),"export-pending.json").toPath(),
                project.toJson().toString().getBytes(StandardCharsets.UTF_8));
            Intent intent=new Intent(this,ExportService.class).setAction(ExportService.START);
            startForegroundService(intent);
            toast("Export im Hintergrund gestartet. Fortschritt in den Benachrichtigungen.");
        }catch(Exception ex){
            new AlertDialog.Builder(this).setTitle("Export nicht gestartet")
                .setMessage(ex.getMessage()).setPositiveButton("OK",null).show();
        }
    }
    private void showExportStatus(){
        try{
            File file=new File(getFilesDir(),"export-status.json");
            if(!file.exists()){toast("Bisher kein Hintergrundexport.");return;}
            JSONObject status=new JSONObject(new String(java.nio.file.Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8));
            new AlertDialog.Builder(this).setTitle("Exportstatus · "+status.optString("state"))
                .setMessage(status.optInt("progress",0)+" %\n"+status.optString("message"))
                .setPositiveButton("OK",null).show();
        }catch(Exception e){toast("Exportstatus nicht verfügbar.");}
    }
    private void renderEffectPreview(){
        if(project.clips.isEmpty()){toast("Bitte zuerst Medien importieren.");return;}
        if(loading.getVisibility()==View.VISIBLE){toast("Bereits eine Bearbeitung aktiv.");return;}
        loading.setVisibility(View.VISIBLE);
        previewRenderer.startPreview(project,new ExportEngine.Listener(){
            @Override public void progress(int value){if(previewLabel!=null)previewLabel.setText("Effektvorschau · "+value+" %");}
            @Override public void success(Uri uri,File file){
                loading.setVisibility(View.GONE);
                stopPreview();
                previewImage.setVisibility(View.GONE);previewVideo.setVisibility(View.VISIBLE);
                previewVideo.setOnPreparedListener(player->player.start());
                previewVideo.setVideoPath(file.getAbsolutePath());
                previewVideo.start();
                toast("Gerenderte Vorschau: erste fünf Szenen.");
            }
            @Override public void failed(String error){
                loading.setVisibility(View.GONE);
                new AlertDialog.Builder(MainActivity.this).setTitle("Effektvorschau fehlgeschlagen")
                    .setMessage(error).setPositiveButton("OK",null).show();
            }
        });
    }
    private void saveBackup(){
        if(project.clips.isEmpty()){toast("Bitte zuerst ein Projekt erstellen.");return;}
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/zip");
        i.putExtra(Intent.EXTRA_TITLE,"StoryForge-Backup-"+System.currentTimeMillis()+".zip");
        startActivityForResult(i,PICK_BACKUP_SAVE);
    }
    private void loadBackup(){
        new AlertDialog.Builder(this).setTitle("Projektsicherung wiederherstellen?")
            .setMessage("Das aktuelle Projekt wird ersetzt. Du kannst es vorher als ZIP sichern.")
            .setPositiveButton("ZIP auswählen",(d,w)->{
                Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/zip");
                startActivityForResult(i,PICK_BACKUP_LOAD);
            }).setNegativeButton("Abbrechen",null).show();
    }
    private void undoProject(){
        try{stopPreview();project=ProjectHistory.travel(getFilesDir(),true);selected=0;refresh();}
        catch(Exception ex){toast(ex.getMessage());}
    }
    private void redoProject(){
        try{stopPreview();project=ProjectHistory.travel(getFilesDir(),false);selected=0;refresh();}
        catch(Exception ex){toast(ex.getMessage());}
    }
    private void share(Uri uri){
        Intent share=new Intent(Intent.ACTION_SEND);share.setType("video/mp4");share.putExtra(Intent.EXTRA_STREAM,uri);
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(share,"Video teilen"));
    }
    private void togglePreview(){if(playing){stopPreview();showSelected();return;}
        if(project.clips.isEmpty()){toast("Bitte erst Medien hinzufügen.");return;}
        playing=true;previewIndex=0;previewElapsedOffsetMs=0;playButton.setText("■ Vorschau stoppen");
        beginClipPreview();
        if(!TextUtils.isEmpty(project.musicPath)){
            try{musicPreview=new MediaPlayer();musicPreview.setDataSource(project.musicPath);
                musicPreview.setLooping(true);musicPreview.setVolume((float)project.musicVolume,(float)project.musicVolume);
                musicPreview.prepare();musicPreview.start();}catch(Exception ignored){releaseMusicPreview();}
        }
        playbackTick=new Runnable(){public void run(){
            if(!playing)return;
            Project.Clip c=project.clips.get(previewIndex);
            long elapsed=SystemClock.elapsedRealtime()-previewStartedAt;
            double global=previewElapsedOffsetMs/1000.0+elapsed/1000.0;
            String title="";
            for(Project.Caption caption:project.captions)if(global>=caption.start&&global<=caption.end){title=caption.text;break;}
            previewText.setText(title);
            totalLabel.setText(time(global)+" / "+time(project.duration()));
            syncPlaylistPreview(global);
            if(elapsed>=c.length()*1000){
                previewElapsedOffsetMs+=(long)(c.length()*1000);
                previewIndex++;
                if(previewIndex>=project.clips.size()){stopPreview();showSelected();return;}
                beginClipPreview();
            }
            main.postDelayed(this,100);
        }};
        main.postDelayed(playbackTick,100);
    }
    private void beginClipPreview(){
        if(previewIndex>=project.clips.size())return;
        previewStartedAt=SystemClock.elapsedRealtime();
        showClip(previewIndex,true);
    }
    private void syncPlaylistPreview(double position){
        int songIndex=-1;
        for(int i=0;i<project.audioTracks.size();i++){
            Project.AudioTrack a=project.audioTracks.get(i);
            if(a.sequential && position>=a.start && position<a.start+Math.max(0,a.end-a.offset)){
                songIndex=i;break;
            }
        }
        if(songIndex==currentPlaylistIndex)return;
        releasePlaylistPreview();
        if(songIndex<0)return;
        Project.AudioTrack a=project.audioTracks.get(songIndex);
        try{
            MediaPlayer player=new MediaPlayer();
            player.setDataSource(a.path);
            player.setVolume((float)a.volume,(float)a.volume);
            player.prepare();
            int fromMs=(int)Math.max(0,Math.min(2_000_000_000,(position-a.start+a.offset)*1000));
            player.seekTo(fromMs);
            player.start();
            playlistPreview=player;
            currentPlaylistIndex=songIndex;
        }catch(Exception ignored){releasePlaylistPreview();}
    }
    private void releasePlaylistPreview(){
        currentPlaylistIndex=-1;
        if(playlistPreview!=null){
            try{playlistPreview.stop();}catch(Exception ignored){}
            try{playlistPreview.release();}catch(Exception ignored){}
            playlistPreview=null;
        }
    }
    private void releaseMusicPreview(){if(musicPreview!=null){try{musicPreview.release();}catch(Exception ignored){}musicPreview=null;}}
    private void stopPreview(){
        playing=false;
        if(playbackTick!=null)main.removeCallbacks(playbackTick);
        if(previewVideo!=null){try{previewVideo.stopPlayback();}catch(Exception ignored){}}
        releaseMusicPreview();
        releasePlaylistPreview();
        if(playButton!=null)playButton.setText("▶ Film abspielen");
        if(totalLabel!=null)totalLabel.setText(project.clips.size()+" Clips   ·   "+time(project.duration()));
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private File projectFile(){return new File(getFilesDir(),"project.json");}
    private synchronized void saveProject(){
        try{
            byte[] data=project.toJson().toString(2).getBytes(StandardCharsets.UTF_8);
            ProjectHistory.saveSnapshot(getFilesDir(),projectFile(),data);
            ProjectHistory.atomicWrite(projectFile(),data);
        }catch(Exception ex){android.util.Log.e("StoryForge","Projekt speichern",ex);}
    }
    private void loadProject(){
        try{File f=projectFile();if(!f.exists())return;
            byte[] bytes=new byte[(int)f.length()];try(FileInputStream input=new FileInputStream(f)){
                int offset=0,n;while(offset<bytes.length&&(n=input.read(bytes,offset,bytes.length-offset))!=-1)offset+=n;
            }
            Project loaded=Project.fromJson(new JSONObject(new String(bytes,StandardCharsets.UTF_8)));
            // If originals were erased, keep only accessible clips.
            loaded.clips.removeIf(c->!new File(c.path).isFile());
            if(!TextUtils.isEmpty(loaded.musicPath)&&!new File(loaded.musicPath).exists()){loaded.musicPath="";loaded.musicName="";}
            project=loaded;
        }catch(Exception ex){android.util.Log.e("StoryForge","Projekt laden",ex);}
    }
    @Override protected void onPause(){super.onPause();stopPreview();}
    @Override protected void onDestroy(){stopPreview();fileQueue.shutdown();super.onDestroy();}
}
