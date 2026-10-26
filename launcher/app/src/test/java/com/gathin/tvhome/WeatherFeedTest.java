package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The WMO table, the day/night test and the Open-Meteo parsing: plain Java, no TV needed. */
public class WeatherFeedTest {

    @Test
    public void clearCodes() {
        assertEquals(WeatherBackdrop.CLEAR, reading(9 * 60, 0).mood());
        assertEquals(WeatherBackdrop.CLEAR, reading(9 * 60, 1).mood());
    }

    @Test
    public void cloudCodes() {
        assertEquals(WeatherBackdrop.CLOUDS, reading(9 * 60, 2).mood());
        assertEquals(WeatherBackdrop.CLOUDS, reading(9 * 60, 3).mood());
    }

    @Test
    public void fogCodes() {
        assertEquals(WeatherBackdrop.FOG, reading(9 * 60, 45).mood());
        assertEquals(WeatherBackdrop.FOG, reading(9 * 60, 48).mood());
    }

    @Test
    public void rainAndShowers() {
        for (int code = 51; code <= 67; code++) assertEquals("code " + code, WeatherBackdrop.RAIN, reading(9 * 60, code).mood());
        for (int code = 80; code <= 82; code++) assertEquals("code " + code, WeatherBackdrop.RAIN, reading(9 * 60, code).mood());
    }

    @Test
    public void snow() {
        for (int code = 71; code <= 77; code++) assertEquals("code " + code, WeatherBackdrop.SNOW, reading(9 * 60, code).mood());
        assertEquals(WeatherBackdrop.SNOW, reading(9 * 60, 85).mood());
        assertEquals(WeatherBackdrop.SNOW, reading(9 * 60, 86).mood());
    }

    @Test
    public void thunderstormOnlyInItsOwnRange() {
        for (int code = 95; code <= 99; code++) assertEquals("code " + code, WeatherBackdrop.STORM, reading(9 * 60, code).mood());
        // Above the WMO range is an unknown code, not a storm.
        assertEquals(WeatherBackdrop.CLOUDS, reading(9 * 60, 1000).mood());
    }

    @Test
    public void unknownCodesFallBackToClouds() {
        assertEquals(WeatherBackdrop.CLOUDS, reading(9 * 60, 4).mood());
        assertEquals(WeatherBackdrop.CLOUDS, reading(9 * 60, -5).mood());
    }

    @Test
    public void dayRunsFromSunriseToSunset() {
        assertFalse(reading(5 * 60 + 59, 0).day());
        assertTrue(reading(6 * 60, 0).day());
        assertTrue(reading(17 * 60 + 59, 0).day());
        assertFalse(reading(18 * 60, 0).day());
    }

    @Test
    public void minuteOfReadsOpenMeteoTimes() {
        assertEquals(5 * 60 + 52, WeatherFeed.minuteOf("2026-10-26T05:52", -1));
        assertEquals(18 * 60 + 7, WeatherFeed.minuteOf("2026-10-26T18:07", -1));
        assertEquals(-1, WeatherFeed.minuteOf("", -1));
        assertEquals(-1, WeatherFeed.minuteOf("2026-10-26", -1));
    }

    @Test
    public void everyMoodHasAPictureAndAColour() {
        String[] moods = {WeatherBackdrop.CLEAR, WeatherBackdrop.CLOUDS, WeatherBackdrop.RAIN,
                WeatherBackdrop.STORM, WeatherBackdrop.FOG, WeatherBackdrop.SNOW};
        for (String mood : moods) {
            for (boolean day : new boolean[]{true, false}) {
                assertTrue(mood + (day ? " day" : " night"), WeatherBackdrop.labelOf(mood, day) != 0);
            }
            assertTrue(mood, WeatherBackdrop.colorOf(mood) != 0);
        }
    }

    /** A reading at a minute of the day, with sunrise 06:00 and sunset 18:00 unless the test says otherwise. */
    private static WeatherFeed.Reading reading(int now, int code) {
        return new WeatherFeed.Reading(30, 34, 78, 6, code, 33, 24, 60, 6 * 60, 18 * 60, now,
                System.currentTimeMillis(), false);
    }
}
