package io.github.sumyabazar1.weather;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The one line of weather the launcher draws under its date, published next to the card picture so a launcher with no
 * network permission can show the reading without fetching anything itself.
 *
 * The format is fixed and deliberately dull, one line, nine pipe separated fields (see the launcher's WeatherLine):
 *
 * <pre>
 * place|temperature|sky|feels|humidity|wind|aqi|aqiLabel|publishedAtMillis
 * </pre>
 *
 * No newlines in a field and no pipes either: the place name is stripped of both, and the sky words and the air
 * quality words come from this build's own strings, which have neither. The file goes to the app's private storage and
 * reaches the launcher through {@link CardArtProvider}.
 */
final class WeatherInfo {
    /** The file the launcher reads, beside the card picture. */
    static final String FILE = "card_info.txt";

    private WeatherInfo() { }

    /** Writes the reading, in one step: a temporary file renamed over the last one, so no half line is ever read. */
    static void publish(Context ctx, Forecast.Report r, String placeName, boolean fahrenheit) {
        if (r == null) return;
        Context app = ctx.getApplicationContext();
        String line = line(app, r, placeName, fahrenheit);
        File target = new File(app.getFilesDir(), FILE);
        File temp = new File(app.getFilesDir(), FILE + ".tmp");
        try {
            try (OutputStream out = new FileOutputStream(temp)) {
                out.write(line.getBytes(StandardCharsets.UTF_8));
                out.flush();
            }
            if (!temp.renameTo(target)) throw new IOException("rename to " + target.getName() + " failed");
        } catch (IOException e) {
            // Not fatal: the launcher simply keeps the last line it read.
            android.util.Log.w("WeatherCard", "could not write the weather line: " + e.getMessage());
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
        }
    }

    /** The line itself, built from the reading; the words are this build's strings, so it reads in the TV's language. */
    static String line(Context ctx, Forecast.Report r, String placeName, boolean fahrenheit) {
        String place = clean(placeName);
        if (place.isEmpty()) place = "—";
        String sky = clean(ctx.getString(Forecast.sky(r.code, r.day)));
        String aqiLabel = r.aqi >= 0 ? clean(ctx.getString(Forecast.aqiLabel(r.aqi))) : "";
        int wind = fahrenheit ? Forecast.mph(r.windKmh) : r.windKmh;
        return place + '|' + Forecast.degrees(r.tempC, fahrenheit) + '|' + sky + '|'
                + Forecast.degrees(r.feelsC, fahrenheit) + '|' + r.humidity + '|' + wind + '|' + r.aqi + '|'
                + aqiLabel + '|' + System.currentTimeMillis();
    }

    /** One field: no line breaks, no field separators, no runs of spaces, and never empty. */
    static String clean(String text) {
        if (text == null) return "";
        String out = text.replace('|', ' ').replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        while (out.contains("  ")) out = out.replace("  ", " ");
        return out.trim();
    }
}
