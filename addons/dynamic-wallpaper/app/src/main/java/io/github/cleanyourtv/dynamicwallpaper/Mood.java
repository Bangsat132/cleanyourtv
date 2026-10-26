package io.github.cleanyourtv.dynamicwallpaper;

import java.util.Locale;

/**
 * Which of the twelve pictures fits a reading: the weather first, then the time of day. Pure logic, no Android, so the
 * table is unit-tested (src/test) without a TV.
 *
 * The weather comes as a WMO code, which is what Open-Meteo answers with: 0 and 1 clear, 2 and 3 cloud, 45 and 48 fog,
 * 51 to 67 drizzle and rain, 71 to 77 and 85 and 86 snow, 80 to 82 rain showers, 95 to 99 thunderstorm. Anything else
 * counts as cloud, which is the calmest picture to be wrong with.
 */
final class Mood {
    static final String CLEAR = "clear", CLOUDS = "clouds", RAIN = "rain", STORM = "storm", FOG = "fog", SNOW = "snow";
    /** The file name in assets/wallpapers, without the .png. */
    private Mood() {
    }

    /** The mood for a WMO weather code. */
    static String of(int code) {
        if (code == 0 || code == 1) return CLEAR;
        if (code == 2 || code == 3) return CLOUDS;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 67) return RAIN;
        if (code >= 71 && code <= 77) return SNOW;
        if (code == 85 || code == 86) return SNOW;
        if (code >= 80 && code <= 82) return RAIN;
        if (code >= 95 && code <= 99) return STORM;
        return CLOUDS;
    }

    /** The picture for a mood and whether it is day: clear_day, storm_night, and so on. */
    static String picture(String mood, boolean day) {
        return mood + (day ? "_day" : "_night");
    }

    /** A reading's own words for the mood, in the TV's language where strings exist; the mood name otherwise. */
    static String labelKey(String mood) {
        return "mood_" + mood;
    }

    /** "Palembang" from a place name as the options file writes it; the asset names stay ASCII. */
    static String assetName(String mood, boolean day) {
        return picture(mood, day).toLowerCase(Locale.ROOT);
    }
}
