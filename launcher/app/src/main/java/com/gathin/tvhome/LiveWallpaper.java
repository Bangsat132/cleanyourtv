package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;

/**
 * A wallpaper an add-on draws: {@code content://<package>.cardart/wallpaper.png}, read from the first shown module card
 * that serves one (config {@code live_wallpaper}). The launcher has no network permission and downloads nothing, so the
 * add-on owns the fetch, the cache and the choice of picture — for instance a landscape that follows the weather and
 * the time of day — and this class only shows the last one it published.
 *
 * Every read is bounded in bytes and every failure is silent apart from one log line: a missing, slow or misbehaving
 * add-on leaves Home with the wallpaper it would have had without this feature (the theme's collection, or the plain
 * background colour), never with an empty screen or a crash.
 */
final class LiveWallpaper {
    /** The provider's authority suffix; the package name comes first. */
    static final String AUTHORITY_SUFFIX = ".cardart";
    /** The one path this class reads. */
    static final String PATH = "wallpaper.png";
    /** How many bytes a published picture may take; a 1280x720 PNG is well under this. */
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    /** How often the add-on is asked again: the reading behind the picture is taken every 30 minutes. */
    static final long REFRESH_MS = 30 * 60_000L;

    private LiveWallpaper() {
    }

    /** The shown module cards, in card order, whose pictures are looked at. */
    interface Sources {
        List<String> packages();
    }

    /**
     * The first picture an add-on publishes, decoded for this screen, or null when none of them publishes one. Reads at
     * most {@link #MAX_BYTES} per package and stops at the first that answers.
     */
    static Bitmap read(Context ctx, List<String> packages, int width, int height, Bitmap.Config config) {
        if (packages == null) return null;
        for (String pkg : packages) {
            if (pkg == null || pkg.isEmpty()) continue;
            byte[] bytes = bytes(ctx, pkg);
            if (bytes == null) continue;
            Bitmap bitmap = decode(bytes, width, height, config);
            if (bitmap != null) return bitmap;
        }
        return null;
    }

    /** The published file, read whole and bounded; null when the add-on serves nothing. */
    private static byte[] bytes(Context ctx, String pkg) {
        Uri uri = Uri.parse("content://" + pkg + AUTHORITY_SUFFIX + "/" + PATH);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            try (InputStream bounded = new BufferedInputStream(in, 4096)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int length = 0;
                for (int n; (n = bounded.read(buffer)) != -1; ) {
                    length += n;
                    if (length > MAX_BYTES) return null;   // not a picture this add-on wrote: refuse it, do not crop
                    out.write(buffer, 0, n);
                }
                return out.toByteArray();
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.w(Cards.TAG, "no live wallpaper from " + pkg + ": " + e);
            return null;
        }
    }

    /** The bytes as a bitmap for this screen, downsampled the way a theme picture is (WallpaperSize). */
    static Bitmap decode(byte[] bytes, int width, int height, Bitmap.Config config) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = WallpaperSize.sampleSize(bounds.outWidth, bounds.outHeight, width, height);
            if (!WallpaperSize.accepted(bounds.outWidth / sample, bounds.outHeight / sample)) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            int[] scale = WallpaperSize.coverScale(bounds.outWidth / sample, bounds.outHeight / sample, width, height);
            if (scale != null) {
                options.inScaled = true;
                options.inDensity = scale[0];
                options.inTargetDensity = scale[1];
            }
            options.inPreferredConfig = config;
            Bitmap decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            if (decoded == null) return null;
            Bitmap gpu = decoded.copy(Bitmap.Config.HARDWARE, false);
            if (gpu == null) return decoded;
            decoded.recycle();
            return gpu;
        } catch (Exception | OutOfMemoryError e) {
            Log.w(Cards.TAG, "unreadable live wallpaper: " + e);
            return null;
        }
    }
}
