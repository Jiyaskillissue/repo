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
import java.io.InputStream;
import java.io.OutputStream;

/** Installs twelve offline-ready landscape images; uses a lightweight fallback if a CDN asset is absent. */
final class CuratedWallpapers {
    private static final int COUNT = 12;
    private static final int[][] PALETTES = {
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
    private CuratedWallpapers() {}

    static int install(Context context) {
        File root = Config.dir(context);
        if (root == null) return 0;
        File folder = new File(root, "wallpapers");
        if (!folder.isDirectory() && !folder.mkdirs()) return 0;

        // Remove only the 3.4 procedural pack introduced by the earlier preview build.
        for (int i = 1; i <= COUNT; i++) {
            File legacy = new File(folder, String.format(java.util.Locale.ROOT, "aerial-%02d.jpg", i));
            if (legacy.isFile()) legacy.delete();
        }

        int installed = 0;
        for (int i = 0; i < COUNT; i++) {
            String name = String.format(java.util.Locale.ROOT, "curated-%02d.jpg", i + 1);
            File destination = new File(folder, name);
            if (destination.isFile() && destination.length() > 20_000) {
                installed++;
                continue;
            }
            if (copyAsset(context, "wallpapers/" + name, destination)
                    || createFallback(destination, i)) installed++;
        }
        return installed;
    }

    private static boolean copyAsset(Context context, String asset, File destination) {
        try (InputStream in = context.getAssets().open(asset);
             OutputStream out = new FileOutputStream(destination)) {
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            return destination.length() > 20_000;
        } catch (Exception failure) {
            destination.delete();
            return false;
        }
    }

    /** Offline backup, used only if one of the photo downloads was unavailable at build time. */
    private static boolean createFallback(File destination, int index) {
        Bitmap bitmap = null;
        try {
            int[] colors = PALETTES[index % PALETTES.length];
            bitmap = Bitmap.createBitmap(1280, 720, Bitmap.Config.RGB_565);
            Canvas canvas = new Canvas(bitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new LinearGradient(0, 0, 0, 720, colors[0], colors[1], Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, 1280, 720, paint);
            paint.setShader(null);
            for (int layer = 0; layer < 4; layer++) {
                Path ridge = new Path();
                float base = 260 + layer * 140;
                ridge.moveTo(0, base);
                for (int x = 0; x <= 1280; x += 48) {
                    float y = base + (float)Math.sin((x + index * 83 + layer * 127) * 0.009) * (50 + layer * 16)
                            + (float)Math.cos((x + index * 41) * 0.004) * 26;
                    ridge.lineTo(x, y);
                }
                ridge.lineTo(1280, 720); ridge.lineTo(0, 720); ridge.close();
                paint.setColor(colors[2 + (layer % 2)]);
                canvas.drawPath(ridge, paint);
            }
            Path water = new Path(); water.moveTo(0, 480);
            for (int x = 0; x <= 1280; x += 48)
                water.lineTo(x, 480 + (float)Math.sin((x + index * 90) * 0.006) * 34);
            for (int x = 1280; x >= 0; x -= 48)
                water.lineTo(x, 535 + (float)Math.sin((x + index * 90) * 0.006) * 26);
            water.close(); paint.setColor(0x664EC4D2); canvas.drawPath(water, paint);
            paint.setColor(0x30FFFFFF);
            for (int n = 0; n < 16; n++) {
                float x = 70 + ((n * 173 + index * 91) % 1120);
                float y = 80 + ((n * 67 + index * 23) % 250);
                canvas.drawCircle(x, y, 22 + (n % 3) * 10, paint);
            }
            try (FileOutputStream out = new FileOutputStream(destination)) {
                return bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out);
            }
        } catch (Exception | OutOfMemoryError failure) {
            destination.delete();
            return false;
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }
}