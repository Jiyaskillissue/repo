package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Captures the launcher's own screen at low resolution and blurs it for the app library and settings.
 * This avoids a RenderScript/RenderEffect dependency and keeps the temporary image below one megabyte.
 */
final class SettingsBackdrop {
    private static final int WIDTH = 512;
    private static final int BLUR_RADIUS = 9;
    private static final int BLUR_PASSES = 3;
    private static final String FILE_NAME = "launcher-settings-backdrop.jpg";

    private SettingsBackdrop() { }

    /**
     * Captures only this app's content view. PopupWindow content is in a separate window, so the
     * library itself is not baked into its backdrop; the home screen remains visible behind it.
     */
    static String capture(Activity activity) {
        Bitmap snapshot = null;
        try {
            View decor = activity.getWindow().getDecorView();
            int sourceWidth = decor.getWidth();
            int sourceHeight = decor.getHeight();
            if (sourceWidth <= 0 || sourceHeight <= 0) {
                sourceWidth = activity.getResources().getDisplayMetrics().widthPixels;
                sourceHeight = activity.getResources().getDisplayMetrics().heightPixels;
            }
            if (sourceWidth <= 0 || sourceHeight <= 0) return null;

            int width = Math.min(WIDTH, sourceWidth);
            int height = Math.max(1, Math.round(width * (sourceHeight / (float) sourceWidth)));
            snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(snapshot);
            canvas.drawColor(0xFF07111F);
            canvas.scale(width / (float) sourceWidth, height / (float) sourceHeight);
            decor.draw(canvas);

            int[] pixels = new int[width * height];
            snapshot.getPixels(pixels, 0, width, 0, 0, width, height);
            blur(pixels, width, height, BLUR_RADIUS, BLUR_PASSES);
            snapshot.setPixels(pixels, 0, width, 0, 0, width, height);

            File file = new File(activity.getCacheDir(), FILE_NAME);
            try (FileOutputStream output = new FileOutputStream(file)) {
                if (!snapshot.compress(Bitmap.CompressFormat.JPEG, 78, output)) {
                    file.delete();
                    return null;
                }
                output.flush();
            }
            return file.isFile() && file.length() > 1000 ? file.getAbsolutePath() : null;
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        } finally {
            if (snapshot != null && !snapshot.isRecycled()) snapshot.recycle();
        }
    }

    /** Adds the blurred home snapshot beneath the rounded translucent app-library surface. */
    static void applyLibraryGlass(Activity activity, View panel) {
        String path = capture(activity);
        if (path == null) return;
        Bitmap bitmap = decode(path);
        if (bitmap == null) return;

        BitmapDrawable photo = new BitmapDrawable(activity.getResources(), bitmap);
        photo.setFilterBitmap(true);
        float density = activity.getResources().getDisplayMetrics().density;
        GradientDrawable tint = new GradientDrawable();
        tint.setColor(0xA52A394F);
        tint.setCornerRadius(28 * density);
        tint.setStroke(Math.max(1, Math.round(density)), 0x66FFFFFF);
        Drawable[] layers = {photo, tint};
        panel.setBackground(new LayerDrawable(layers));
        panel.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), 28 * density);
            }
        });
        panel.setClipToOutline(true);
    }

    /** Places the same low-resolution blurred snapshot behind the settings' frosted glass panels. */
    static void applySettingsGlass(Activity activity, String path) {
        if (path == null || path.trim().isEmpty()) return;
        Bitmap bitmap = decode(path);
        if (bitmap == null) return;

        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null || content.getChildCount() == 0
                || !(content.getChildAt(0) instanceof FrameLayout)) {
            bitmap.recycle();
            return;
        }
        FrameLayout backdropRoot = (FrameLayout) content.getChildAt(0);
        backdropRoot.setBackgroundColor(0xFF07111F);

        ImageView image = new ImageView(activity);
        image.setImageBitmap(bitmap);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setAlpha(0.82f);
        image.setFocusable(false);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        backdropRoot.addView(image, 0, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        View tint = new View(activity);
        tint.setBackgroundColor(0x66111D30);
        tint.setFocusable(false);
        tint.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        backdropRoot.addView(tint, 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private static Bitmap decode(String path) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            options.inScaled = false;
            return BitmapFactory.decodeFile(path, options);
        } catch (Exception | OutOfMemoryError failure) {
            return null;
        }
    }

    /** Three sliding-window box-blur passes approximate a soft Gaussian blur in linear time. */
    private static void blur(int[] pixels, int width, int height, int radius, int passes) {
        int[] horizontal = new int[pixels.length];
        int[] vertical = new int[pixels.length];
        int[] source = pixels;
        int[] spare = vertical;
        for (int pass = 0; pass < passes; pass++) {
            blurHorizontal(source, horizontal, width, height, radius);
            blurVertical(horizontal, spare, width, height, radius);
            int[] previous = source;
            source = spare;
            spare = previous;
        }
        if (source != pixels) System.arraycopy(source, 0, pixels, 0, pixels.length);
    }

    private static void blurHorizontal(int[] source, int[] target, int width, int height, int radius) {
        int divisor = radius * 2 + 1;
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int red = 0, green = 0, blue = 0, alpha = 0;
            for (int k = -radius; k <= radius; k++) {
                int color = source[row + clamp(k, 0, width - 1)];
                alpha += (color >>> 24) & 255;
                red += (color >>> 16) & 255;
                green += (color >>> 8) & 255;
                blue += color & 255;
            }
            for (int x = 0; x < width; x++) {
                target[row + x] = ((alpha / divisor) << 24) | ((red / divisor) << 16)
                        | ((green / divisor) << 8) | (blue / divisor);
                int remove = source[row + clamp(x - radius, 0, width - 1)];
                int add = source[row + clamp(x + radius + 1, 0, width - 1)];
                alpha += ((add >>> 24) & 255) - ((remove >>> 24) & 255);
                red += ((add >>> 16) & 255) - ((remove >>> 16) & 255);
                green += ((add >>> 8) & 255) - ((remove >>> 8) & 255);
                blue += (add & 255) - (remove & 255);
            }
        }
    }

    private static void blurVertical(int[] source, int[] target, int width, int height, int radius) {
        int divisor = radius * 2 + 1;
        for (int x = 0; x < width; x++) {
            int red = 0, green = 0, blue = 0, alpha = 0;
            for (int k = -radius; k <= radius; k++) {
                int color = source[clamp(k, 0, height - 1) * width + x];
                alpha += (color >>> 24) & 255;
                red += (color >>> 16) & 255;
                green += (color >>> 8) & 255;
                blue += color & 255;
            }
            for (int y = 0; y < height; y++) {
                target[y * width + x] = ((alpha / divisor) << 24) | ((red / divisor) << 16)
                        | ((green / divisor) << 8) | (blue / divisor);
                int remove = source[clamp(y - radius, 0, height - 1) * width + x];
                int add = source[clamp(y + radius + 1, 0, height - 1) * width + x];
                alpha += ((add >>> 24) & 255) - ((remove >>> 24) & 255);
                red += ((add >>> 16) & 255) - ((remove >>> 16) & 255);
                green += ((add >>> 8) & 255) - ((remove >>> 8) & 255);
                blue += (add & 255) - (remove & 255);
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
