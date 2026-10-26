package io.github.cleanyourtv.dynamicwallpaper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The WMO table, the picture names and the reading's day/night test; all of it plain Java, no TV needed. */
public class MoodTest {

    @Test
    public void clearCodes() {
        assertEquals(Mood.CLEAR, Mood.of(0));
        assertEquals(Mood.CLEAR, Mood.of(1));
    }

    @Test
    public void cloudCodes() {
        assertEquals(Mood.CLOUDS, Mood.of(2));
        assertEquals(Mood.CLOUDS, Mood.of(3));
    }

    @Test
    public void fogCodes() {
        assertEquals(Mood.FOG, Mood.of(45));
        assertEquals(Mood.FOG, Mood.of(48));
    }

    @Test
    public void drizzleAndRainShareTheRainPicture() {
        for (int code = 51; code <= 67; code++) assertEquals("code " + code, Mood.RAIN, Mood.of(code));
        assertEquals(Mood.RAIN, Mood.of(80));
        assertEquals(Mood.RAIN, Mood.of(82));
    }

    @Test
    public void snowCodes() {
        for (int code = 71; code <= 77; code++) assertEquals("code " + code, Mood.SNOW, Mood.of(code));
        assertEquals(Mood.SNOW, Mood.of(85));
        assertEquals(Mood.SNOW, Mood.of(86));
    }

    @Test
    public void thunderstorm() {
        for (int code = 95; code <= 99; code++) assertEquals("code " + code, Mood.STORM, Mood.of(code));
    }

    @Test
    public void unknownCodesFallBackToClouds() {
        assertEquals(Mood.CLOUDS, Mood.of(-1));
        assertEquals(Mood.CLOUDS, Mood.of(4));
        assertEquals(Mood.CLOUDS, Mood.of(1000));
    }

    @Test
    public void pictureNamesMatchTheAssets() {
        assertEquals("clear_day", Mood.picture(Mood.CLEAR, true));
        assertEquals("storm_night", Mood.picture(Mood.STORM, false));
        assertEquals("snow_day", Mood.assetName(Mood.SNOW, true));
        assertEquals("mood_fog", Mood.labelKey(Mood.FOG));
    }

    @Test
    public void dayRunsFromSunriseToSunset() {
        Weather.Reading before = reading(5 * 60 + 59, 6 * 60, 18 * 60);
        Weather.Reading morning = reading(6 * 60, 6 * 60, 18 * 60);
        Weather.Reading evening = reading(17 * 60 + 59, 6 * 60, 18 * 60);
        Weather.Reading night = reading(18 * 60, 6 * 60, 18 * 60);
        assertFalse(before.day());
        assertTrue(morning.day());
        assertTrue(evening.day());
        assertFalse(night.day());
    }

    @Test
    public void readingChoosesItsPicture() {
        Weather.Reading rain = reading(9 * 60, 6 * 60, 18 * 60, 61);
        assertEquals(Mood.RAIN, rain.mood());
        assertEquals("rain_day", rain.picture());
        Weather.Reading clearNight = reading(22 * 60, 6 * 60, 18 * 60, 0);
        assertEquals("clear_night", clearNight.picture());
    }

    @Test
    public void minuteOfReadsOpenMeteoTimes() {
        assertEquals(5 * 60 + 52, Weather.minuteOf("2026-10-26T05:52", -1));
        assertEquals(18 * 60 + 7, Weather.minuteOf("2026-10-26T18:07", -1));
        assertEquals(-1, Weather.minuteOf("", -1));
        assertEquals(-1, Weather.minuteOf("2026-10-26", -1));
    }

    private static Weather.Reading reading(int now, int sunrise, int sunset) {
        return reading(now, sunrise, sunset, 3);
    }

    private static Weather.Reading reading(int now, int sunrise, int sunset, int code) {
        return new Weather.Reading(30, 34, 78, 6, code, 33, 24, 60, sunrise, sunset, now,
                System.currentTimeMillis(), false);
    }
}
