package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import java.io.File;
import java.io.FileOutputStream;

/** Generates a small set of original, low-storage aerial landscape wallpapers on-device. */
final class CuratedWallpapers {
    private static final int[][] SKY={
        {0xFF071B36,0xFF5C8FA9,0xFF173E52,0xFF0A2637},
        {0xFF17213F,0xFFB47C68,0xFF48566D,0xFF14283B},
        {0xFF061D2A,0xFF4E9B9C,0xFF1C5960,0xFF092C39},
        {0xFF18213C,0xFFDBA77C,0xFF6C6670,0xFF272C43},
        {0xFF09243A,0xFF83A9B3,0xFF355F70,0xFF102F43},
        {0xFF211C38,0xFFB66E79,0xFF58445E,0xFF201F38},
        {0xFF102D37,0xFF9BB7A6,0xFF4D786E,0xFF183F43},
        {0xFF111E35,0xFF9E9AB1,0xFF4B526C,0xFF202D48},
        {0xFF0A2634,0xFF6C9DA0,0xFF285C62,0xFF0C303A},
        {0xFF28233C,0xFFD49B79,0xFF716074,0xFF302D48},
        {0xFF09243A,0xFF7899B7,0xFF355B78,0xFF102E49},
        {0xFF1B2636,0xFFC4A18A,0xFF746D70,0xFF303543}
    };
    private CuratedWallpapers(){}
    static int install(Context context){
        File root=Config.dir(context); if(root==null)return 0;
        File folder=new File(root,"wallpapers"); if(!folder.exists()&&!folder.mkdirs())return 0;
        int count=0;
        for(int i=0;i<SKY.length;i++){
            File out=new File(folder,String.format(java.util.Locale.ROOT,"aerial-%02d.jpg",i+1));
            if(out.isFile()&&out.length()>10000){count++;continue;}
            Bitmap bitmap=Bitmap.createBitmap(1280,720,Bitmap.Config.RGB_565);
            Canvas c=new Canvas(bitmap); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
            int[] colors=SKY[i];
            p.setShader(new LinearGradient(0,0,0,720,colors[0],colors[1],Shader.TileMode.CLAMP));c.drawRect(0,0,1280,720,p);p.setShader(null);
            p.setColor(0x55FFFFFF);c.drawCircle(980-(i%4)*110,170+(i%3)*35,42+(i%3)*12,p);
            // Layered, broad landforms create the quiet aerial perspective of a screensaver frame.
            for(int layer=0;layer<3;layer++){
                Path path=new Path();float base=330+layer*125;
                path.moveTo(0,base);
                for(int x=0;x<=1280;x+=80){float y=base+(float)Math.sin((x+i*83+layer*127)*0.009)*(55+layer*14)+(float)Math.cos((x+i*41)*0.004)*28;path.lineTo(x,y);}
                path.lineTo(1280,720);path.lineTo(0,720);path.close();
                p.setColor(colors[2+ (layer%2)]);c.drawPath(path,p);
            }
            // A distant pale river / coastline, then a dark foreground ridge.
            Path water=new Path();water.moveTo(0,500);
            for(int x=0;x<=1280;x+=64)water.lineTo(x,500+(float)Math.sin((x+i*90)*0.006)*34);
            for(int x=1280;x>=0;x-=64)water.lineTo(x,560+(float)Math.sin((x+i*90)*0.006)*28);
            water.close();p.setColor(0x554EC4D2);c.drawPath(water,p);
            p.setColor(0x33202028);c.drawRect(0,680,1280,720,p);
            try(FileOutputStream stream=new FileOutputStream(out)){if(bitmap.compress(Bitmap.CompressFormat.JPEG,82,stream))count++;}catch(Exception ignored){}
            bitmap.recycle();
        }
        return count;
    }
}