package io.github.sumyabazar1.weather;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * The launcher tile's picture, kept current. A card's banner is a drawable the Clean Your TV launcher reads through the
 * package manager when Home comes to the front, so it cannot be a view: the weather now is drawn into a PNG in the app's
 * own files, at {@link #FILE}. After a write, {@link #invalidate} toggles the card activity's enabled state so the
 * package manager drops what it cached and Home looks the package up again.
 *
 * The manifest's banner is {@code @drawable/card_banner_placeholder}, a static 16:9 drawing of the same card, and it is
 * what a TV shows until this add-on is opened once; a live picture drawn at runtime cannot become a resource of the
 * APK it is already running from.
 *
 * Nothing runs in the background: no service, no alarm, no boot receiver. The card is written while the weather page is
 * on screen, after each good reading.
 */
final class WeatherWidget {
    private static final String TAG = "WeatherCard";
    /** The PNG of the card, in the app's private files. */
    static final String FILE = "card_banner.png";

    private WeatherWidget() { }

    /**
     * Draws the weather now and writes it as a PNG, in one step: the bitmap goes to a temporary file first and is
     * renamed over the last one, so the launcher never reads half a picture. Call it off the UI thread.
     */
    static void publish(Context ctx, Forecast.Report r, String placeName, boolean fahrenheit) {
        if (r == null) return;
        Context app = ctx.getApplicationContext();
        Bitmap bitmap = WeatherArt.draw(app, r, placeName, fahrenheit);
        File target = new File(app.getFilesDir(), FILE);
        File temp = new File(app.getFilesDir(), FILE + ".tmp");
        try {
            write(bitmap, temp);
            if (!temp.renameTo(target)) {
                throw new IOException("rename to " + target.getName() + " failed");
            }
        } catch (IOException e) {
            Log.w(TAG, "could not write the card picture: " + e.getMessage());
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        } finally {
            bitmap.recycle();
        }
    }

    /**
     * Asks the package manager to forget the banner it cached, so the next pass over the tiles reads the picture just
     * written: the card activity is disabled and enabled again, which the system never kills for, and nothing else
     * changes — the activity stays enabled and exported, and no tile comes or goes.
     */
    static void invalidate(Context ctx) {
        Context app = ctx.getApplicationContext();
        PackageManager pm = app.getPackageManager();
        ComponentName card = new ComponentName(app, CardActivity.class);
        try {
            pm.setComponentEnabledSetting(card, PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
            pm.setComponentEnabledSetting(card, PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (RuntimeException e) {
            Log.w(TAG, "could not refresh the card: " + e);
        }
    }

    /** The PNG encoder: flushes and closes the stream before returning, so the rename sees a whole file. */
    private static void write(Bitmap bitmap, File file) throws IOException {
        try (OutputStream out = new FileOutputStream(file)) {
            if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                throw new IOException("the encoder refused the bitmap");
            }
            out.flush();
        }
    }
}
