package io.github.cleanyourtv.dynamicwallpaper;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * One weather reading, from Open-Meteo, cached on the TV.
 *
 * The reading is fetched at most every {@link #REFRESH_MS} and always written to disk before it is shown, so Home's
 * wallpaper and the page survive a reboot with no network at all: the last good reading is used and the picture it
 * chose stays. A failed fetch never clears the cache and never throws at the caller — it returns the cached reading
 * with {@link Reading#stale} set, or null when there has never been one.
 *
 * Open-Meteo needs no key and no account, which is why this add-on uses it: nothing secret has to be built into an APK
 * that a TV owner sideloads.
 */
final class Weather {
    static final String TAG = "DynamicWallpaper";
    /** How often the reading is taken; the launcher shows the picture that came with it. */
    static final long REFRESH_MS = 30 * 60_000L;
    /** A fetch that takes longer than this is given up on: Home must never wait for the network. */
    private static final int TIMEOUT_MS = 8000;
    /** A reading larger than this is not one of ours. */
    private static final int MAX_BYTES = 64 * 1024;
    private static final String CACHE = "weather.json";
    private static final String CACHE_TIME = "weather_at.txt";

    private Weather() {
    }

    /** A reading, with the fields the page and the picture need. */
    static final class Reading {
        final int tempC, feelsC, humidity, windKmh, code, highC, lowC, rainChance;
        final int sunriseMinute, sunsetMinute, nowMinute;
        final long takenAt;
        /** True when this is the cached reading because the fetch failed. */
        final boolean stale;

        Reading(int tempC, int feelsC, int humidity, int windKmh, int code, int highC, int lowC, int rainChance,
                int sunriseMinute, int sunsetMinute, int nowMinute, long takenAt, boolean stale) {
            this.tempC = tempC;
            this.feelsC = feelsC;
            this.humidity = humidity;
            this.windKmh = windKmh;
            this.code = code;
            this.highC = highC;
            this.lowC = lowC;
            this.rainChance = rainChance;
            this.sunriseMinute = sunriseMinute;
            this.sunsetMinute = sunsetMinute;
            this.nowMinute = nowMinute;
            this.takenAt = takenAt;
            this.stale = stale;
        }

        boolean day() {
            return nowMinute >= sunriseMinute && nowMinute < sunsetMinute;
        }

        String mood() {
            return Mood.of(code);
        }

        String picture() {
            return Mood.picture(mood(), day());
        }
    }

    /** The URL for a place, in the units the page shows: Celsius, km/h, the place's own timezone. */
    static String url(String latitude, String longitude, String timezone) {
        return "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + latitude + "&longitude=" + longitude
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
                + "&timezone=" + timezone + "&forecast_days=1";
    }

    /**
     * The reading to show: fetched when the cache is older than {@link #REFRESH_MS} (or when {@code force} is set),
     * the cache otherwise. Never null once a reading has ever been taken on this TV.
     */
    static Reading get(Context ctx, boolean force) {
        Context app = ctx.getApplicationContext();
        Reading cached = cached(app);
        long takenAt = cacheTime(app);
        if (!force && cached != null && System.currentTimeMillis() - takenAt < REFRESH_MS) return cached;
        try {
            Reading fresh = fetch(app);
            if (fresh != null) {
                store(app, fresh);
                return fresh;
            }
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "weather fetch failed, keeping the last reading: " + e);
        }
        return cached;
    }

    /** The last reading, read back from the cache; null when there is none or it does not parse. */
    private static Reading cached(Context ctx) {
        File file = new File(ctx.getFilesDir(), CACHE);
        if (!file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) {
            String json = read(in);
            return parse(new JSONObject(json), cacheTime(ctx), true);
        } catch (Exception e) {
            Log.w(TAG, "unreadable cached reading: " + e);
            return null;
        }
    }

    private static long cacheTime(Context ctx) {
        File file = new File(ctx.getFilesDir(), CACHE_TIME);
        if (!file.isFile()) return 0;
        try (InputStream in = new FileInputStream(file)) {
            return Long.parseLong(read(in).trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /** Fetches and parses one reading; null when the answer is not a reading. */
    private static Reading fetch(Context ctx) throws Exception {
        String lat = ctx.getString(R.string.place_latitude);
        String lon = ctx.getString(R.string.place_longitude);
        String zone = ctx.getString(R.string.place_timezone);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url(lat, lon, zone)).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            if (connection.getResponseCode() != 200) {
                Log.w(TAG, "Open-Meteo answered " + connection.getResponseCode());
                return null;
            }
            try (InputStream in = connection.getInputStream()) {
                return parse(new JSONObject(read(in)), System.currentTimeMillis(), false);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * The fields of Open-Meteo's answer, in the shape this app uses. A missing or malformed field is not fatal: the
     * reading keeps 0 there and the page simply omits that line.
     */
    static Reading parse(JSONObject json, long takenAt, boolean stale) throws Exception {
        JSONObject current = json.getJSONObject("current");
        JSONObject daily = json.optJSONObject("daily");
        int code = current.optInt("weather_code", -1);
        int now = minuteOf(current.optString("time", ""), 12 * 60);
        int sunrise = daily == null ? 6 * 60 : firstMinute(daily, "sunrise", 6 * 60);
        int sunset = daily == null ? 18 * 60 : firstMinute(daily, "sunset", 18 * 60);
        return new Reading(
                (int) Math.round(current.optDouble("temperature_2m", 0)),
                (int) Math.round(current.optDouble("apparent_temperature", 0)),
                current.optInt("relative_humidity_2m", 0),
                (int) Math.round(current.optDouble("wind_speed_10m", 0)),
                code < 0 ? 3 : code,
                daily == null ? 0 : (int) Math.round(firstDouble(daily, "temperature_2m_max")),
                daily == null ? 0 : (int) Math.round(firstDouble(daily, "temperature_2m_min")),
                daily == null ? -1 : (int) Math.round(firstDouble(daily, "precipitation_probability_max")),
                sunrise, sunset, now, takenAt, stale);
    }

    /** "2026-10-26T05:52" as minutes of the day; {@code fallback} when it is not that shape. */
    static int minuteOf(String iso, int fallback) {
        int t = iso.indexOf('T');
        if (t < 0 || iso.length() < t + 6) return fallback;
        try {
            int hour = Integer.parseInt(iso.substring(t + 1, t + 3));
            int minute = Integer.parseInt(iso.substring(t + 4, t + 6));
            return hour * 60 + minute;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int firstMinute(JSONObject daily, String key, int fallback) {
        try {
            return minuteOf(daily.getJSONArray(key).getString(0), fallback);
        } catch (Exception e) {
            return fallback;
        }
    }

    private static double firstDouble(JSONObject daily, String key) {
        try {
            return daily.getJSONArray(key).getDouble(0);
        } catch (Exception e) {
            return 0;
        }
    }

    /** The cache is written in one step: a temporary file renamed over the last one, so a half file is never read. */
    private static void store(Context ctx, Reading reading) {
        JSONObject json = new JSONObject();
        try {
            JSONObject current = new JSONObject();
            current.put("time", isoOf(reading.nowMinute));
            current.put("temperature_2m", reading.tempC);
            current.put("apparent_temperature", reading.feelsC);
            current.put("relative_humidity_2m", reading.humidity);
            current.put("wind_speed_10m", reading.windKmh);
            current.put("weather_code", reading.code);
            JSONObject daily = new JSONObject();
            daily.put("temperature_2m_max", new org.json.JSONArray().put(reading.highC));
            daily.put("temperature_2m_min", new org.json.JSONArray().put(reading.lowC));
            daily.put("precipitation_probability_max", new org.json.JSONArray().put(reading.rainChance));
            daily.put("sunrise", new org.json.JSONArray().put(isoOf(reading.sunriseMinute)));
            daily.put("sunset", new org.json.JSONArray().put(isoOf(reading.sunsetMinute)));
            json.put("current", current);
            json.put("daily", daily);
            write(ctx, CACHE, json.toString());
            write(ctx, CACHE_TIME, Long.toString(reading.takenAt));
        } catch (Exception e) {
            Log.w(TAG, "could not cache the reading: " + e);
        }
    }

    private static String isoOf(int minute) {
        return String.format(java.util.Locale.ROOT, "2000-01-01T%02d:%02d", minute / 60, minute % 60);
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

    /** Reads a stream whole, bounded: a response that is not ours is refused rather than loaded. */
    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length = 0;
        for (int n; (n = in.read(buffer)) != -1; ) {
            length += n;
            if (length > MAX_BYTES) throw new IOException("response over " + MAX_BYTES + " bytes");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }
}
