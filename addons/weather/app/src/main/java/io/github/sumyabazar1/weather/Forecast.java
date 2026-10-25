package io.github.sumyabazar1.weather;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * OpenWeatherMap, with an API key from the build ({@code BuildConfig.WEATHER_API_KEY}): the place search, the
 * weather now and for the next days, and the air quality read turned into the US air quality index. The only thing
 * sent is the place name typed in the search, then that place's latitude and longitude. Every call blocks, so
 * WeatherActivity makes them off the UI thread.
 */
final class Forecast {
    static final long REFRESH_MS = 30 * 60_000L;
    static final long RETRY_MS = 5 * 60_000L;
    private static final int TIMEOUT_MS = 8_000;
    /** Days after today the forecast row can show (options.xml, forecast_days). */
    static final int MIN_DAYS = 3, MAX_DAYS = 7;

    private static final String API = "https://api.openweathermap.org";

    private Forecast() { }

    /** A place from the search: its name, region and country for the list, and where it is. */
    static final class Place {
        final String name;
        final String detail;
        final double latitude;
        final double longitude;

        Place(String name, String detail, double latitude, double longitude) {
            this.name = name;
            this.detail = detail;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    /**
     * The weather now: degrees Celsius as read (air and feels-like; {@link #degrees} rounds them for the page), the
     * WMO weather code, day or night, the relative humidity in % and the wind speed in km/h.
     */
    static final class Report {
        final double tempC;
        final double feelsC;
        /** The WMO code, rain turned into snow where the air is cold enough for it (see {@link #snowIfCold}). */
        final int code;
        final boolean day;
        /** The next days, tomorrow first. */
        final List<Day> days;
        /** US AQI, or -1 when the air quality read failed. */
        final int aqi;
        final int humidity;
        final int windKmh;

        Report(double tempC, double feelsC, int code, boolean day, List<Day> days, int aqi, int humidity, int windKmh) {
            this.tempC = tempC;
            this.feelsC = feelsC;
            this.code = code;
            this.day = day;
            this.days = days;
            this.aqi = aqi;
            this.humidity = humidity;
            this.windKmh = windKmh;
        }
    }

    /** One forecast day: its date, the WMO code and the high and low in degrees Celsius as read. */
    static final class Day {
        final LocalDate date;
        final int code;
        final double maxC;
        final double minC;

        Day(LocalDate date, int code, double maxC, double minC) {
            this.date = date;
            this.code = code;
            this.maxC = maxC;
            this.minC = minC;
        }
    }

    /** False when the build carries no OpenWeatherMap key, so nothing is fetched. */
    static boolean hasKey() {
        String key = BuildConfig.WEATHER_API_KEY;
        return key != null && !key.trim().isEmpty() && !"PLACEHOLDER".equals(key.trim());
    }

    /** Up to five places matching what was typed, best match first. Empty when nothing matches or there is no key. */
    static List<Place> search(String name) throws IOException, JSONException {
        List<Place> out = new ArrayList<>();
        if (!hasKey()) return out;
        JSONArray results = getArray(API + "/geo/1.0/direct?q=" + URLEncoder.encode(name.trim(), "UTF-8")
                + "&limit=5&appid=" + key());
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            StringBuilder detail = new StringBuilder(r.optString("state"));
            String country = r.optString("country");
            if (!country.isEmpty()) detail.append(detail.length() > 0 ? ", " : "").append(country);
            out.add(new Place(r.getString("name"), detail.toString(), r.getDouble("lat"), r.getDouble("lon")));
        }
        return out;
    }

    /** Days after today to show: the option, kept between {@link #MIN_DAYS} and {@link #MAX_DAYS}. */
    static int forecastDays(int wanted) {
        return Math.max(MIN_DAYS, Math.min(MAX_DAYS, wanted));
    }

    /**
     * The weather and air quality at a place, with this many days after today. The air quality is a separate
     * call: if it fails, aqi is -1.
     */
    static Report fetch(double latitude, double longitude, int days) throws IOException, JSONException {
        if (!hasKey()) throw new IOException("no key");
        String where = String.format(Locale.ROOT, "lat=%.4f&lon=%.4f", latitude, longitude);
        JSONObject now = get(API + "/data/2.5/weather?" + where + "&units=metric&appid=" + key());
        JSONObject main = now.getJSONObject("main");
        double temp = main.getDouble("temp");
        double humidity = main.optDouble("humidity", 0);
        JSONObject today = now.optJSONArray("weather") == null || now.optJSONArray("weather").length() == 0
                ? null : now.optJSONArray("weather").optJSONObject(0);
        int code = snowIfCold(owmCode(today == null ? 800 : today.optInt("id", 800)), temp, humidity);
        JSONObject wind = now.optJSONObject("wind");
        double metresPerSecond = wind == null ? 0 : wind.optDouble("speed", 0);
        return new Report(temp, main.optDouble("feels_like", temp),
                code, isDay(now),
                days(get(API + "/data/2.5/forecast?" + where + "&units=metric&appid=" + key()), forecastDays(days)),
                aqiOrUnknown(where),
                (int) Math.round(humidity), (int) Math.round(metresPerSecond * 3.6));
    }

    /** Day when now falls between the place's local sunrise and sunset, night otherwise. */
    private static boolean isDay(JSONObject now) {
        JSONObject sys = now.optJSONObject("sys");
        if (sys == null) return true;
        long sunrise = sys.optLong("sunrise", 0), sunset = sys.optLong("sunset", 0);
        if (sunrise == 0 && sunset == 0) return true;
        long at = now.optLong("dt", 0) == 0 ? System.currentTimeMillis() / 1000 : now.optLong("dt");
        return at >= sunrise && at <= sunset;
    }

    /**
     * The models type precipitation by the air temperature, and around freezing they call rain what reaches the
     * ground as snow (ICON and GFS: "light rain" at 1 °C in a Ulaanbaatar blizzard). The wet-bulb temperature,
     * which counts how dry the air is, decides better: at 1 °C or below, rain and drizzle become snow of the same
     * strength. Freezing rain and drizzle stay, as they are what falls.
     */
    static int snowIfCold(int code, double tempC, double humidity) {
        if (wetBulb(tempC, humidity) > 1.0) return code;
        switch (code) {
            case 51: case 53: case 61: case 80: return 71;
            case 55: case 63: case 81: return 73;
            case 65: case 82: return 75;
            default: return code;
        }
    }

    /** Stull (2011), from the air temperature in °C and the relative humidity in %; within 1 °C at sea level. */
    static double wetBulb(double t, double rh) {
        return t * Math.atan(0.151977 * Math.sqrt(rh + 8.313659)) + Math.atan(t + rh) - Math.atan(rh - 1.676331)
                + 0.00391838 * Math.pow(rh, 1.5) * Math.atan(0.023101 * rh) - 4.686035;
    }

    /** OpenWeatherMap's condition id, into the WMO code the words and colours are written for. */
    static int owmCode(int id) {
        if (id >= 200 && id <= 232) return 95;
        if (id >= 300 && id <= 321) return 51;
        if (id == 500) return 61;
        if (id == 501) return 63;
        if (id >= 502 && id <= 504) return 65;
        if (id == 511) return 66;
        if (id == 520 || id == 521) return 80;
        if (id == 522 || id == 531) return 82;
        if (id == 600) return 71;
        if (id == 601) return 73;
        if (id == 602) return 75;
        if (id >= 611 && id <= 616) return 66;
        if (id == 620 || id == 621) return 85;
        if (id == 622) return 86;
        if (id == 701 || id == 711 || id == 721 || id == 731 || id == 741 || id == 751 || id == 761
                || id == 762 || id == 771) return 45;
        if (id == 781) return 95;
        if (id == 800) return 0;
        if (id == 801) return 1;
        if (id == 802) return 2;
        if (id == 803 || id == 804) return 3;
        return 3;
    }

    /** Tomorrow and the days after it; today (index 0) is the current reading. Never more than the option asks. */
    private static List<Day> days(JSONObject reply, int wanted) throws JSONException {
        JSONArray list = reply.optJSONArray("list");
        List<Day> out = new ArrayList<>();
        if (list == null) return out;
        long zone = reply.optJSONObject("city") == null ? 0 : reply.optJSONObject("city").optLong("timezone", 0);
        LocalDate today = LocalDate.now(java.time.ZoneOffset.ofTotalSeconds((int) zone));
        // The steps of one local day, walked in order, so a gap in the three-hour steps still gives one day.
        LocalDate day = null;
        int code = 0;
        double max = 0, min = 0;
        long nearest = Long.MAX_VALUE;
        for (int i = 0; i < list.length(); i++) {
            JSONObject step = list.optJSONObject(i);
            if (step == null) continue;
            long at = step.optLong("dt", 0) + zone;
            LocalDate on = LocalDate.ofEpochDay(Math.floorDiv(at, 86400L));
            if (on.isBefore(today) || on.isEqual(today)) continue;
            JSONObject main = step.optJSONObject("main");
            if (main == null) continue;
            double temp = main.getDouble("temp");
            if (!on.equals(day)) {
                if (day != null) {
                    out.add(new Day(day, snowIfCold(code, max, 100), max, min));
                    if (out.size() >= wanted) return out;
                }
                day = on;
                code = 0;
                max = min = temp;
                nearest = Long.MAX_VALUE;
            } else {
                max = Math.max(max, temp);
                min = Math.min(min, temp);
            }
            // The step nearest local midday says what the day is: fair weather in the morning does not.
            long toNoon = Math.abs(Math.floorMod(at, 86400L) - 43200L);
            if (toNoon < nearest) {
                nearest = toNoon;
                JSONArray weather = step.optJSONArray("weather");
                code = weather == null || weather.length() == 0 ? 3
                        : owmCode(weather.optJSONObject(0) == null ? 3 : weather.optJSONObject(0).optInt("id", 800));
            }
        }
        if (day != null && out.size() < wanted) out.add(new Day(day, snowIfCold(code, max, 100), max, min));
        return out;
    }

    /** The air quality read, as a US AQI; -1 when it fails or has no reading. */
    private static int aqiOrUnknown(String where) {
        try {
            JSONArray list = get(API + "/data/2.5/air_pollution?" + where + "&appid=" + key()).optJSONArray("list");
            if (list == null || list.length() == 0) return -1;
            JSONObject components = list.optJSONObject(0) == null ? null
                    : list.optJSONObject(0).optJSONObject("components");
            if (components == null || !components.has("pm2_5") || !components.has("pm10")) return -1;
            return usAqi(components.getDouble("pm2_5"), components.getDouble("pm10"));
        } catch (IOException | JSONException | RuntimeException e) {
            return -1;
        }
    }

    /**
     * A US AQI from the two particulates: each one's breakpoints on the US EPA scale, the worse of the two. Open
     * WeatherMap's own index is a 1 to 5 band, too coarse for the page, so it is not used.
     */
    static int usAqi(double pm25, double pm10) {
        return Math.max(pm25Aqi(pm25), pm10Aqi(pm10));
    }

    private static int pm25Aqi(double v) {
        if (v <= 12.0) return upTo(v, 0, 12.0, 0, 50);
        if (v <= 35.4) return upTo(v, 12.1, 35.4, 51, 100);
        if (v <= 55.4) return upTo(v, 35.5, 55.4, 101, 150);
        if (v <= 150.4) return upTo(v, 55.5, 150.4, 151, 200);
        if (v <= 250.4) return upTo(v, 150.5, 250.4, 201, 300);
        if (v <= 500.4) return upTo(v, 250.5, 500.4, 301, 500);
        return 500;
    }

    private static int pm10Aqi(double v) {
        if (v <= 54) return upTo(v, 0, 54, 0, 50);
        if (v <= 154) return upTo(v, 55, 154, 51, 100);
        if (v <= 254) return upTo(v, 155, 254, 101, 150);
        if (v <= 354) return upTo(v, 255, 354, 151, 200);
        if (v <= 424) return upTo(v, 355, 424, 201, 300);
        if (v <= 604) return upTo(v, 425, 604, 301, 500);
        return 500;
    }

    /** Linear between the band's ends: the reading's share of the band, spread over the band's index. */
    private static int upTo(double v, double low, double high, int indexLow, int indexHigh) {
        double share = high > low ? (v - low) / (high - low) : 1;
        return (int) Math.round(indexLow + Math.max(0, Math.min(1, share)) * (indexHigh - indexLow));
    }

    /** The key, for a URL; only called once {@link #hasKey} says there is one. */
    private static String key() {
        return BuildConfig.WEATHER_API_KEY == null ? "" : BuildConfig.WEATHER_API_KEY.trim();
    }

    private static JSONObject get(String url) throws IOException, JSONException {
        return new JSONObject(body(url));
    }

    /** A reply whose root is an array, as the place search's is; null when the reply is empty or not an array. */
    private static JSONArray getArray(String url) throws IOException, JSONException {
        String text = body(url).trim();
        return text.isEmpty() ? null : new JSONArray(text);
    }

    private static String body(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(TIMEOUT_MS);
        connection.setReadTimeout(TIMEOUT_MS);
        try {
            int status = connection.getResponseCode();
            InputStream in = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            if (in == null) throw new IOException("HTTP " + status + " from OpenWeatherMap");
            try (InputStream stream = in) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                for (int n; (n = stream.read(buffer)) != -1; ) out.write(buffer, 0, n);
                String text = new String(out.toByteArray(), StandardCharsets.UTF_8);
                // A wrong, unconfirmed or spent key answers 401 with {"cod":401,...}: say so instead of a parse error.
                if (status >= 400) throw new IOException("HTTP " + status + " from OpenWeatherMap: " + text);
                return text;
            }
        } finally {
            connection.disconnect();
        }
    }

    /** Whole degrees for the page: Celsius as read, or Fahrenheit converted first (options.xml temperature_unit). */
    static int degrees(double celsius, boolean fahrenheit) {
        return (int) Math.round(fahrenheit ? celsius * 9 / 5 + 32 : celsius);
    }

    /** Whole miles per hour from km/h. */
    static int mph(int kmh) {
        return (int) Math.round(kmh / 1.609344);
    }

    // ---- words and colours -------------------------------------------------------------------------

    /** The words for a WMO weather code, as fine as the code goes ("Light snow", not "Snow"). */
    static int sky(int code, boolean day) {
        switch (code) {
            case 0: return day ? R.string.sky_sunny : R.string.sky_clear;
            case 1: return day ? R.string.sky_mostly_sunny : R.string.sky_mostly_clear;
            case 2: return R.string.sky_partly_cloudy;
            case 3: return R.string.sky_cloudy;
            case 45: case 48: return R.string.sky_fog;
            case 51: case 53: case 55: return R.string.sky_drizzle;
            case 56: case 57: return R.string.sky_freezing_drizzle;
            case 61: return R.string.sky_light_rain;
            case 63: return R.string.sky_rain;
            case 65: return R.string.sky_heavy_rain;
            case 66: case 67: return R.string.sky_freezing_rain;
            case 71: return R.string.sky_light_snow;
            case 73: return R.string.sky_snow;
            case 75: return R.string.sky_heavy_snow;
            case 77: return R.string.sky_snow_grains;
            case 80: case 81: case 82: return R.string.sky_showers;
            case 85: case 86: return R.string.sky_snow_showers;
            default: return code >= 95 ? R.string.sky_thunderstorm : R.string.sky_cloudy;
        }
    }

    /** US AQI bands: upper bound, words, pill colour. */
    private static final int[] AQI_MAX = {50, 100, 150, 200, 300, Integer.MAX_VALUE};
    private static final int[] AQI_LABEL = {R.string.aqi_good, R.string.aqi_moderate, R.string.aqi_sensitive,
            R.string.aqi_unhealthy, R.string.aqi_very_unhealthy, R.string.aqi_hazardous};
    private static final int[] AQI_COLOUR = {0xE643A047, 0xE6FBC02D, 0xE6FB8C00, 0xE6E53935, 0xE68E24AA, 0xE67E0023};

    static int aqiBand(int aqi) {
        int band = 0;
        while (aqi > AQI_MAX[band]) band++;
        return band;
    }

    static int aqiLabel(int aqi) {
        return AQI_LABEL[aqiBand(aqi)];
    }

    static int aqiColour(int aqi) {
        return AQI_COLOUR[aqiBand(aqi)];
    }

    /** Dark text on the yellow band, white on the others. */
    static int aqiTextColour(int aqi) {
        return aqiBand(aqi) == 1 ? 0xFF1A1A1A : 0xFFFFFFFF;
    }

    /** A full-screen gradient in the weather's colours, dark at the bottom. */
    static Drawable backdrop(int code, boolean day) {
        int[] colours;
        if (code <= 1) {
            colours = day ? new int[]{0xFF2A6AA8, 0xFF0A1626} : new int[]{0xFF0D1B3E, 0xFF020308};
        } else if (code == 45 || code == 48) {
            colours = new int[]{0xFF5A6068, 0xFF101113};
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            colours = new int[]{0xFF6F8497, 0xFF101820};
        } else if (code >= 95) {
            colours = new int[]{0xFF2D2140, 0xFF07050C};
        } else if (code >= 51) {
            colours = new int[]{0xFF1E2C3D, 0xFF06090E};
        } else {
            colours = new int[]{0xFF3B4654, 0xFF0C0F14};
        }
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, colours);
    }
}
