package com.gathin.tvhome;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * The weather line under the date: one line the weather add-on publishes, read back through the provider the add-on
 * exports ({@code content://<package>.cardart/card_info.txt}). The launcher has no network permission and fetches
 * nothing itself: the add-on owns the reading, and this class only shows the last one it wrote.
 *
 * The file is the add-on's own format, one reading per line:
 *
 * <pre>
 * place|temperature|feels|sky|humidity|wind|aqi|aqiLabel|publishedAtMillis
 * </pre>
 *
 * A line that does not hold nine fields, or whose numbers do not parse, shows nothing at all: a broken reading must
 * never reach the header text. The whole read is bounded (bytes and time) and every failure is silent apart from one
 * log line, so a missing or misbehaving add-on leaves Home exactly as it was.
 */
final class WeatherLine {
    /** The provider's authority suffix; the package name comes first. */
    static final String AUTHORITY_SUFFIX = ".cardart";
    /** The one path the add-on serves under that authority. */
    static final String PATH = "card_info.txt";
    /** How many bytes the reading may take; a line of nine short fields is a few hundred. */
    private static final int MAX_BYTES = 4096;
    /** Read at most this many times over the 30 minutes: a package manager query per resume is enough. */
    static final long REFRESH_MS = 30 * 60_000L;

    private WeatherLine() { }

    /** A reading, already formatted for the header line, and when the add-on took it. */
    static final class Reading {
        final String text;
        /** The add-on's own reading time, in epoch milliseconds; 0 when it did not say. */
        final long publishedAt;

        Reading(String text, long publishedAt) {
            this.text = text;
            this.publishedAt = publishedAt;
        }
    }

    /**
     * The reading the given package publishes, or null when it publishes none, exports no provider, or the line does
     * not parse. Call it off the UI thread only when the caller can wait; it returns long before a timeout otherwise.
     */
    static Reading read(Context ctx, String pkg) {
        if (pkg == null || pkg.isEmpty()) return null;
        Uri uri = Uri.parse("content://" + pkg + AUTHORITY_SUFFIX + "/" + PATH);
        try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            String line;
            try (InputStream bounded = new BufferedInputStream(in, 1024)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[512];
                int length = 0;
                for (int n; (n = bounded.read(buffer)) != -1; ) {
                    length += n;
                    if (length > MAX_BYTES) return null;   // not a reading the add-on wrote: refuse it, do not crop
                    out.write(buffer, 0, n);
                }
                line = new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
            }
            return parse(line);
        } catch (Exception | OutOfMemoryError e) {
            // Not a failure of Home: no line is drawn and the header looks exactly as before.
            Log.w(Cards.TAG, "no weather line from " + pkg + ": " + e);
            return null;
        }
    }

    /** The published line, split and checked; null when it is not nine fields of the expected shapes. */
    static Reading parse(String line) {
        if (line == null) return null;
        int end = line.indexOf('\n');
        if (end >= 0) line = line.substring(0, end).trim();
        String[] f = line.split("\\|", -1);
        if (f.length != 9) return null;
        String place = f[0].trim(), sky = f[2].trim(), aqiLabel = f[7].trim();
        int temperature, feels, humidity, wind, aqi;
        long publishedAt;
        try {
            temperature = Integer.parseInt(f[1].trim());
            feels = Integer.parseInt(f[3].trim());
            humidity = Integer.parseInt(f[4].trim());
            wind = Integer.parseInt(f[5].trim());
            aqi = Integer.parseInt(f[6].trim());
            publishedAt = Long.parseLong(f[8].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (place.isEmpty() || sky.isEmpty()) return null;
        StringBuilder out = new StringBuilder(place.toUpperCase(Locale.getDefault()));
        out.append(' ').append(temperature).append('°').append(' ').append(sky.toUpperCase(Locale.getDefault()));
        // The five readings on the line: the same five the add-on's card draws, so the two agree.
        out.append("  ·  ").append("SUHU ").append(temperature).append('°');
        out.append("  ·  ").append("TERASA ").append(feels).append('°');
        out.append("  ·  ").append("LEMBAB ").append(humidity).append('%');
        out.append("  ·  ").append("ANGIN ").append(wind);
        if (aqi >= 0) {
            out.append("  ·  ").append("AQI ").append(aqi);
            if (!aqiLabel.isEmpty()) out.append(' ').append(aqiLabel.toUpperCase(Locale.getDefault()));
        }
        return new Reading(out.toString(), publishedAt);
    }
}
