package com.skylarapp.neondrift;

import com.skylarapp.neondrift.R;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.MediaPlayer;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.graphics.Typeface;
import java.util.ArrayList;
import java.util.Random;

public class MainActivity extends Activity {
    private MediaPlayer musicPlayer;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setNavigationBarColor(Color.rgb(5, 8, 20));
        setContentView(new NeonRunView());
        musicPlayer = MediaPlayer.create(this, R.raw.background_music);
        if (musicPlayer != null) musicPlayer.setLooping(true);
    }

    @Override protected void onStart() {
        super.onStart();
        if (musicPlayer != null && !musicPlayer.isPlaying()) musicPlayer.start();
    }

    @Override protected void onStop() {
        if (musicPlayer != null && musicPlayer.isPlaying()) musicPlayer.pause();
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (musicPlayer != null) {
            musicPlayer.release();
            musicPlayer = null;
        }
        super.onDestroy();
    }

    class NeonRunView extends View {
        int withAlpha(int color, int alpha) { return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)); }
        final float density = getResources().getDisplayMetrics().density;
        final Paint p = new Paint(3);
        final Random random = new Random();
        final ArrayList<Obstacle> obstacles = new ArrayList<>();
        long last = 0;
        float playerLane = 0, targetLane = 0, speed = .34f, spawn = 0, score = 0, pulse = 0;
        int bestScore, wave = 1, blocksInWave = 4, blocksSpawned = 0, lastObstacleLane = 99;
        float waveIntroTimer = 0;
        boolean started = false, dead = false;
        float downX, downY;
        int cyan = Color.rgb(42, 240, 255), pink = Color.rgb(255, 55, 170), violet = Color.rgb(115, 70, 255);

        NeonRunView() {
            super(MainActivity.this);
            bestScore = getSharedPreferences("neon_drift", MODE_PRIVATE).getInt("best_score", 0);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            c.save();
            c.scale(density, density);
            float w=getWidth()/density, h=getHeight()/density, horizon=h*.31f;
            long now=System.nanoTime();
            float dt=last==0?0:Math.min(.05f,(now-last)/1_000_000_000f); last=now;
            pulse += dt;
            drawBackdrop(c,w,h,horizon);
            if (started && !dead) update(dt);
            drawRoad(c,w,h,horizon);
            drawObstacles(c,w,h,horizon);
            drawPlayer(c,w,h,horizon);
            drawHud(c,w,h);
            if (started && waveIntroTimer > 0) drawWaveIntro(c,w,h);
            if (!started && !dead) drawOverlay(c,w,h,"NEON//DRIFT","BEST RUN  "+bestScore+" m","TAP TO START");
            if (dead) drawOverlay(c,w,h,"CRASHED","DISTANCE  "+(int)score+" m     BEST  "+bestScore+" m","TAP TO RUN AGAIN");
            c.restore();
            postInvalidateOnAnimation();
        }

        void drawBackdrop(Canvas c,float w,float h,float hz) {
            c.drawColor(Color.rgb(5,8,21));
            p.setShader(new android.graphics.LinearGradient(0,0,0,h,new int[]{Color.rgb(13,15,47),Color.rgb(20,10,49),Color.rgb(5,8,20)},null,android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0,0,w,h,p); p.setShader(null);
            p.setColor(0x1e2af0ff); p.setStyle(Paint.Style.FILL);
            for(int i=0;i<36;i++){float x=(i*97f+17)%w;float y=(i*53f+8+pulse*(8+i%4)*speed)%(hz+20);float r=(i%3)+.6f;c.drawCircle(x,y,r,p);}
            p.setColor(0x32ff37aa); p.setStrokeWidth(1);
            for(int i=0;i<5;i++){float y=hz+20+i*24;c.drawLine(w*.15f,y,w*.85f,y,p);}
        }
        void drawRoad(Canvas c,float w,float h,float hz) {
            Path road=new Path(); road.moveTo(w*.5f,hz);road.lineTo(w*.04f,h);road.lineTo(w*.96f,h);road.close();
            p.setColor(Color.rgb(10,13,32));c.drawPath(road,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(3);p.setColor(cyan);c.drawLine(w*.5f,hz,w*.04f,h,p);c.drawLine(w*.5f,hz,w*.96f,h,p);
            p.setColor(withAlpha(pink,100));p.setStrokeWidth(1);
            for(int lane=-1;lane<=1;lane+=2){float x=w*.5f+lane*w*.155f;c.drawLine(w*.5f,hz,x,h,p);}
            // Perspective floor bars animate toward the player.
            p.setColor(withAlpha(cyan,45));p.setStrokeWidth(1);
            float phase=(pulse*speed*360)%70;
            for(float y=hz+phase;y<h;y+=70){float t=(y-hz)/(h-hz);float half=w*(.03f+.43f*t);c.drawLine(w*.5f-half,y,w*.5f+half,y,p);}
            p.setStyle(Paint.Style.FILL);
            // Horizon glow
            p.setColor(withAlpha(violet,95));p.setStrokeWidth(2);c.drawLine(w*.18f,hz,w*.82f,hz,p);
        }
        void update(float dt) {
            score += dt * speed * 95;
            speed = Math.min(.82f, .34f + (wave - 1) * .03f + score / 40000f);
            playerLane += (targetLane - playerLane) * (1f - (float)Math.exp(-dt * 11f));
            waveIntroTimer = Math.max(0, waveIntroTimer - dt);

            if (blocksSpawned < blocksInWave) {
                spawn -= dt;
                if (spawn <= 0) {
                    int lane = random.nextInt(3) - 1;
                    if (lane == lastObstacleLane) lane = lane == 1 ? -1 : lane + 1;
                    lastObstacleLane = lane;
                    obstacles.add(new Obstacle(lane, -.08f));
                    blocksSpawned++;
                    spawn = Math.max(.5f, 1.05f - wave * .035f);
                }
            }

            for (int i = obstacles.size() - 1; i >= 0; i--) {
                Obstacle o = obstacles.get(i);
                o.z += dt * speed;
                if (o.z > 1.04f) { obstacles.remove(i); continue; }
                if (o.z > .72f && o.z < .86f && o.lane == Math.round(playerLane)) {
                    dead = true;
                    started = false;
                    bestScore = Math.max(bestScore, (int) score);
                    getSharedPreferences("neon_drift", MODE_PRIVATE).edit().putInt("best_score", bestScore).apply();
                }
            }

            if (blocksSpawned >= blocksInWave && obstacles.isEmpty()) {
                spawn -= dt;
                if (spawn <= -.8f) {
                    wave++;
                    blocksInWave = 3 + wave;
                    blocksSpawned = 0;
                    spawn = .5f;
                    waveIntroTimer = 2.0f;
                }
            }
        }
        float[] center(float w,float hz,float z,float lane){float t=Math.max(0,Math.min(1,z));return new float[]{w*.5f+lane*w*.30f*t,hz+(getHeight()/density-hz)*t};}
        void drawObstacles(Canvas c,float w,float h,float hz){
            for(Obstacle o:obstacles){
                if(o.z < -.08f || o.z > 1.04f) continue;
                float t=Math.max(0,Math.min(1,o.z));
                float fadeIn=Math.max(0,Math.min(1,(o.z+.08f)/.18f));
                float fadeOut=Math.max(0,Math.min(1,(1.04f-o.z)/.18f));
                int alpha=(int)(255*Math.min(fadeIn,fadeOut));
                float[] q=center(w,hz,o.z,o.lane);float breathe=1f+(float)Math.sin(pulse*5f+o.z*8f)*.025f;float size=(14+t*42)*breathe, hh=size*1.35f;
                p.setColor(withAlpha(pink,(int)(alpha*.22f)));c.drawRoundRect(q[0]-size*.7f,q[1]-hh*.7f,q[0]+size*.7f,q[1]+size*.15f,size,size,p);
                p.setColor(withAlpha(Color.rgb(30,15,45),alpha));c.drawRoundRect(q[0]-size/2,q[1]-hh,q[0]+size/2,q[1],7,7,p);
                p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(1,t*2.5f));p.setColor(withAlpha(pink,alpha));c.drawRoundRect(q[0]-size/2,q[1]-hh,q[0]+size/2,q[1],7,7,p);p.setStyle(Paint.Style.FILL);
                p.setColor(withAlpha(Color.WHITE,alpha));c.drawRect(q[0]-size*.22f,q[1]-hh*.68f,q[0]+size*.22f,q[1]-hh*.58f,p);
            }
        }
        void drawPlayer(Canvas c,float w,float h,float hz){
            float[] q=center(w,hz,.98f,playerLane);float x=q[0],y=h*.84f;float bob=(float)Math.sin(pulse*6f)*2.5f;
            float lean=Math.max(-1f,Math.min(1f,targetLane-playerLane));
            p.setColor(withAlpha(cyan,75));c.drawOval(x-50,y+25+bob,x+50,y+50+bob,p);
            c.save();c.rotate(-lean*16f,x,y+bob);
            float flame=10f+(float)(Math.sin(pulse*20f)+1f)*5f;
            Path exhaust=new Path();exhaust.moveTo(x-9,y+23+bob);exhaust.lineTo(x,y+31+flame+bob);exhaust.lineTo(x+9,y+23+bob);exhaust.close();
            p.setColor(withAlpha(pink,180));c.drawPath(exhaust,p);
            Path core=new Path();core.moveTo(x-4,y+24+bob);core.lineTo(x,y+29+flame*.65f+bob);core.lineTo(x+4,y+24+bob);core.close();
            p.setColor(withAlpha(Color.WHITE,220));c.drawPath(core,p);
            Path ship=new Path();ship.moveTo(x,y-52+bob);ship.lineTo(x+39,y+31+bob);ship.lineTo(x+10,y+22+bob);ship.lineTo(x,y+38+bob);ship.lineTo(x-10,y+22+bob);ship.lineTo(x-39,y+31+bob);ship.close();
            p.setColor(Color.rgb(10,55,78));c.drawPath(ship,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(4);p.setColor(cyan);c.drawPath(ship,p);p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);c.drawCircle(x,y-7+bob,6,p);
            c.restore();
        }
        void drawHud(Canvas c,float w,float h){
            p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));p.setTextAlign(Paint.Align.LEFT);p.setTextSize(17);p.setColor(cyan);c.drawText(String.format("WAVE  %02d",wave),22,34,p);
            p.setTextSize(10);p.setColor(withAlpha(Color.WHITE,175));c.drawText(String.format("%d / %d BLOCKS",blocksSpawned,blocksInWave),22,51,p);
            p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(27);p.setColor(Color.WHITE);c.drawText(String.format("%05d",(int)score),w-22,40,p);
            p.setTextSize(10);p.setColor(withAlpha(cyan,190));c.drawText("BEST  "+bestScore,w-22,56,p);
            p.setTextAlign(Paint.Align.LEFT);p.setColor(withAlpha(Color.WHITE,130));p.setTextSize(11);c.drawText("←   SWIPE TO SHIFT   →",22,h-24,p);
        }
        void drawWaveIntro(Canvas c,float w,float h){
            float progress=1f-waveIntroTimer/2f;
            float enter=Math.min(1f,progress/.22f);
            float fadeOut=progress>.78f?Math.max(0f,(1f-progress)/.22f):1f;
            int alpha=(int)(255f*enter*fadeOut);
            float overshoot=1f+(float)Math.sin(enter*Math.PI)*.09f;
            float centerX=w*.5f, centerY=h*.235f+24f*(1f-enter);
            c.save();c.translate(centerX,centerY);c.scale(overshoot,overshoot);
            p.setStyle(Paint.Style.FILL);
            p.setColor(withAlpha(cyan,(int)(alpha*.10f)));
            c.drawRoundRect(-132,-38,132,42,24,24,p);
            p.setShader(new android.graphics.LinearGradient(0,-31,0,35,new int[]{withAlpha(Color.rgb(26,42,82),(int)(alpha*.98f)),withAlpha(Color.rgb(10,15,39),(int)(alpha*.98f))},null,android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(-124,-31,124,35,20,20,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.4f);p.setColor(withAlpha(cyan,(int)(alpha*.78f)));
            c.drawRoundRect(-124,-31,124,35,20,20,p);p.setStyle(Paint.Style.FILL);
            p.setColor(withAlpha(pink,(int)(alpha*.9f)));c.drawRoundRect(-103,-10,-101,21,1,1,p);
            p.setTextAlign(Paint.Align.LEFT);p.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            p.setTextSize(9);p.setColor(withAlpha(cyan,(int)(alpha*.86f)));c.drawText("NEXT SECTOR",-88,-12,p);
            p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));p.setTextSize(24);p.setColor(withAlpha(Color.WHITE,alpha));
            c.drawText(String.format("WAVE  %02d",wave),-88,14,p);
            p.setTextAlign(Paint.Align.RIGHT);p.setTextSize(10);p.setColor(withAlpha(cyan,alpha));
            c.drawText(blocksInWave+" BLOCKS",106,-10,p);
            p.setTextSize(8);p.setColor(withAlpha(Color.WHITE,(int)(alpha*.65f)));c.drawText("SPEED INCREASED",106,9,p);
            float sweep=(progress*1.8f)%1f;
            p.setColor(withAlpha(Color.WHITE,(int)(alpha*.22f)));
            c.drawRoundRect(-112+sweep*224,28,-72+sweep*224,30,1,1,p);
            c.restore();
        }

        void drawOverlay(Canvas c,float w,float h,String title,String subtitle,String action){
            p.setColor(Color.argb(170,2,4,17));c.drawRect(0,0,w,h,p);
            float cardW=Math.min(w-36,360), cardH=220, left=(w-cardW)/2, top=h*.34f-cardH*.35f;
            p.setColor(Color.argb(230,12,17,39));c.drawRoundRect(left,top,left+cardW,top+cardH,22,22,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.5f);p.setColor(withAlpha(cyan,150));c.drawRoundRect(left,top,left+cardW,top+cardH,22,22,p);p.setStyle(Paint.Style.FILL);
            p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.create("sans-serif-condensed",Typeface.BOLD));
            p.setTextSize(Math.min(52,w*.13f));p.setColor(cyan);c.drawText(title,w/2,top+66,p);
            p.setTextSize(14);p.setColor(Color.WHITE);c.drawText(subtitle,w/2,top+108,p);
            p.setColor(pink);p.setTextSize(14);c.drawRoundRect(w*.5f-94,top+136,w*.5f+94,top+174,19,19,p);
            p.setColor(Color.WHITE);p.setTextSize(12);c.drawText(action,w/2,top+160,p);
            p.setColor(withAlpha(cyan,190));p.setTextSize(11);c.drawText("NEON ARCADE  ·  ENDLESS RUN",w/2,top+201,p);
        }
        @Override public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){downX=e.getX();downY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP){
                if(!started){started=true;dead=false;score=0;speed=.34f;wave=1;blocksInWave=4;blocksSpawned=0;lastObstacleLane=99;waveIntroTimer=2f;spawn=.6f;obstacles.clear();targetLane=0;}
                else {float dx=e.getX()-downX;if(Math.abs(dx)>24*density)targetLane=Math.max(-1,Math.min(1,targetLane+(dx>0?1:-1)));else if(e.getX()<getWidth()*.4f)targetLane=Math.max(-1,targetLane-1);else if(e.getX()>getWidth()*.6f)targetLane=Math.min(1,targetLane+1);}
                return true;
            }return true;
        }
        class Obstacle {int lane;float z;Obstacle(int l,float d){lane=l;z=d;}}
    }
}