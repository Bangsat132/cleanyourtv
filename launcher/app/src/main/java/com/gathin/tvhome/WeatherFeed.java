package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The weather for one place, fetched by the launcher itself from Open-Meteo and kept on the TV.
 *
 * The launcher asks for a reading at most every {@link #REFRESH_MS}, always on a background thread, and writes it to
 * its own preferences before it is shown, so Home draws the last reading across a reboot and with no network at all.
 * A failed fetch never clears the cache and never throws at the caller: the cached reading comes back with
 * {@link Reading#stale} set, or null when there has never been one.
 *
 * Open-Meteo needs no key and no account, which is why it is the source: nothing secret has to be built into an APK
 * people sideload. The place is config.xml's {@code weather_latitude}, {@code weather_longitude} and
 * {@code weather_timezone} (Palembang, Asia/Jakarta by default), so the TV is never asked where it is.
 *
 * The reading is what the header line shows and what {@link WeatherBackdrop} draws its picture from.
 */
final class WeatherFeed {
    static final String TAG = Cards.TAG;
    /** How often a reading is taken: the line and the backdrop both follow it. */
    static final long REFRESH_MS = 30 * 60_000L;
    /** A fetch that takes longer than this is given up on: Home must never wait for the network. */
    private static final int TIMEOUT_MS = 8000;
    /** An answer larger than this is not one of ours. */
    private static final int MAX_BYTES = 64 * 1024;
    private static final String PREFS = "weather_feed";
    private static final String KEY_READING = "reading";
    private static final String KEY_AT = "at";

    private WeatherFeed() {
    }

    /** One reading, with the fields the header line and the backdrop use. */
    static final class Reading {
        final int tempC, feelsC, humidity, windKmh, code, highC, lowC, rainChance;
        final int sunriseMinute, sunsetMinute, nowMinute;
        final long takenAt;
        /** True when this is the cached reading because the fetch failed or is not due. */
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

        /** Day runs from the reading's own sunrise to its sunset. */
        boolean day() {
            return nowMinute >= sunriseMinute && nowMinute < sunsetMinute;
        }

        /** The picture's mood: clear, clouds, rain, storm, fog or snow (WMO code). */
        String mood() {
            if (code == 0 || code == 1) return WeatherBackdrop.CLEAR;
            if (code == 2 || code == 3) return WeatherBackdrop.CLOUDS;
            if (code == 45 || code == 48) return WeatherBackdrop.FOG;
            if (code >= 51 && code <= 67) return WeatherBackdrop.RAIN;
            if (code >= 71 && code <= 77) return WeatherBackdrop.SNOW;
            if (code == 85 || code == 86) return WeatherBackdrop.SNOW;
            if (code >= 80 && code <= 82) return WeatherBackdrop.RAIN;
            if (code >= 95 && code <= 99) return WeatherBackdrop.STORM;
            return WeatherBackdrop.CLOUDS;
        }
    }

    /** The URL for a place: Celsius, km/h, and the place's own timezone. */
    static String url(String latitude, String longitude, String timezone) {
        return "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + latitude + "&longitude=" + longitude
                + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m"
                + "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset"
                + "&timezone=" + timezone + "&forecast_days=1";
    }

    /** The cached reading, or null when there is none: the caller then shows no line and no backdrop. */
    static Reading cached(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString(KEY_READING, null);
        if (json == null) return null;
        try {
            return parse(new JSONObject(json), prefs.getLong(KEY_AT, 0), true);
        } catch (Exception e) {
            Log.w(TAG, "unreadable cached weather: " + e);
            return null;
        }
    }

    /** True when the cache is older than {@link #REFRESH_MS}, or missing: a fetch is then worth a thread. */
    static boolean due(Context ctx) {
        long at = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_AT, 0);
        return at == 0 || System.currentTimeMillis() - at >= REFRESH_MS;
    }

    /**
     * Fetches one reading and caches it. Blocking: call it off the UI thread. Returns null when the answer is not a
     * reading, and leaves the cache exactly as it was.
     */
    static Reading fetch(Context ctx) {
        String lat = ctx.getString(R.string.weather_latitude);
        String lon = ctx.getString(R.string.weather_longitude);
        String zone = ctx.getString(R.string.weather_timezone);
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
            Reading reading;
            try (InputStream in = connection.getInputStream()) {
                reading = parse(new JSONObject(read(in)), System.currentTimeMillis(), false);
            }
            store(ctx, reading);
            return reading;
        } catch (Exception | OutOfMemoryError e) {
            Log.w(TAG, "weather fetch failed, keeping the last reading: " + e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /**
     * The fields of Open-Meteo's answer. A missing or malformed field is not fatal: the reading keeps a sensible value
     * there and the line leaves that part out.
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
            return Integer.parseInt(iso.substring(t + 1, t + 3)) * 60
                    + Integer.parseInt(iso.substring(t + 4, t + 6));
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

    /** The reading is written in one step (a preferences commit), so a half reading is never read back. */
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
            daily.put("temperature_2m_max", new JSONArray().put(reading.highC));
            daily.put("temperature_2m_min", new JSONArray().put(reading.lowC));
            daily.put("precipitation_probability_max", new JSONArray().put(reading.rainChance));
            daily.put("sunrise", new JSONArray().put(isoOf(reading.sunriseMinute)));
            daily.put("sunset", new JSONArray().put(isoOf(reading.sunsetMinute)));
            json.put("current", current);
            json.put("daily", daily);
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(KEY_READING, json.toString())
                    .putLong(KEY_AT, reading.takenAt)
                    .apply();
        } catch (Exception e) {
            Log.w(TAG, "could not cache the reading: " + e);
        }
    }

    private static String isoOf(int minute) {
        return String.format(java.util.Locale.ROOT, "2000-01-01T%02d:%02d", minute / 60, minute % 60);
    }

    /** Reads a stream whole, bounded: an answer that is not ours is refused rather than loaded. */
    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length = 0;
        for (int n; (n = in.read(buffer)) != -1; ) {
            length += n;
            if (length > MAX_BYTES) throw new Exception("answer over " + MAX_BYTES + " bytes");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }
}
