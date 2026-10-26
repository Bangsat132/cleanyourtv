package io.github.cleanyourtv.dynamicwallpaper;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Publishes the chosen picture and the line beside it for the launcher and for this app's own page.
 *
 * The twelve pictures ship as assets (about 500 KB together) and are copied into the app's private storage one at a
 * time: only the one in use is written, so the cache stays at one picture (about 20 to 100 KB) however long the app
 * runs. Both files are replaced in one step — a temporary file renamed over the last one — so a reader never sees a
 * half picture or a half line.
 */
final class Wallpapers {
    /** The picture the launcher reads through the provider. */
    static final String PICTURE = "wallpaper.png";
    /** The line beside it: place|picture|temperature|condition|updatedAtMillis. */
    static final String INFO = "wallpaper_info.txt";
    private static final String ASSET_DIR = "wallpapers";

    private Wallpapers() {
    }

    /** The picture currently published, or null before the first reading. */
    static File picture(Context ctx) {
        return new File(ctx.getFilesDir(), PICTURE);
    }

    /**
     * Copies the asset for this reading into the cache and writes the line, then returns true. False when the asset is
     * missing or the copy failed: the last picture then stays, which is what Home should show.
     */
    static boolean publish(Context ctx, Weather.Reading reading, String place, String condition) {
        Context app = ctx.getApplicationContext();
        String asset = ASSET_DIR + "/" + reading.picture() + ".png";
        try {
            copyAsset(app, asset, new File(app.getFilesDir(), PICTURE));
        } catch (IOException e) {
            Log.w(Weather.TAG, "could not publish " + asset + ": " + e);
            return false;
        }
        String line = place + '|' + reading.picture() + '|' + reading.tempC + '|' + condition + '|' + reading.takenAt;
        try {
            write(app, INFO, line);
        } catch (IOException e) {
            // The picture is the part Home needs; a missing line only leaves the page without its caption.
            Log.w(Weather.TAG, "could not write the wallpaper line: " + e);
        }
        return true;
    }

    /** Copies an asset into a file, in one step; the same bytes are not copied twice. */
    private static void copyAsset(Context ctx, String asset, File target) throws IOException {
        try (InputStream in = ctx.getAssets().open(asset)) {
            byte[] bytes = readAll(in);
            if (target.isFile() && target.length() == bytes.length && sameBytes(target, bytes)) return;
            File temp = new File(target.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(temp)) {
                out.write(bytes);
                out.flush();
            }
            if (!temp.renameTo(target)) throw new IOException("rename to " + target.getName() + " failed");
        }
    }

    private static boolean sameBytes(File file, byte[] bytes) {
        try (InputStream in = new java.io.FileInputStream(file)) {
            byte[] have = readAll(in);
            if (have.length != bytes.length) return false;
            for (int i = 0; i < have.length; i++) if (have[i] != bytes[i]) return false;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
        return out.toByteArray();
    }

    private static void write(Context ctx, String name, String text) throws IOException {
        File target = new File(ctx.getFilesDir(), name);
        File temp = new File(ctx.getFilesDir(), name + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        if (!temp.renameTo(target)) throw new IOException("rename to " + target.getName() + " failed");
    }
}
