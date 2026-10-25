package io.github.sumyabazar1.weather;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** The tables that turn OpenWeatherMap's numbers into words and colours. */
public class ForecastTest {
    @Test
    public void openWeatherMapCodesLandInTheWmoSpace() {
        assertEquals(95, Forecast.owmCode(200));   // thunderstorm with light rain
        assertEquals(51, Forecast.owmCode(300));   // light drizzle
        assertEquals(61, Forecast.owmCode(500));   // light rain
        assertEquals(63, Forecast.owmCode(501));   // moderate rain
        assertEquals(65, Forecast.owmCode(504));   // extreme rain
        assertEquals(66, Forecast.owmCode(511));   // freezing rain
        assertEquals(80, Forecast.owmCode(520));   // light shower rain
        assertEquals(82, Forecast.owmCode(522));   // heavy shower rain
        assertEquals(71, Forecast.owmCode(600));   // light snow
        assertEquals(73, Forecast.owmCode(601));   // snow
        assertEquals(75, Forecast.owmCode(602));   // heavy snow
        assertEquals(66, Forecast.owmCode(611));   // sleet
        assertEquals(85, Forecast.owmCode(620));   // light shower sleet
        assertEquals(86, Forecast.owmCode(622));   // heavy shower snow
        assertEquals(45, Forecast.owmCode(741));   // fog
        assertEquals(95, Forecast.owmCode(781));   // tornado
        assertEquals(0, Forecast.owmCode(800));    // clear
        assertEquals(1, Forecast.owmCode(801));    // few clouds
        assertEquals(2, Forecast.owmCode(802));    // scattered clouds
        assertEquals(3, Forecast.owmCode(803));    // broken clouds
        assertEquals(3, Forecast.owmCode(804));    // overcast
        assertEquals(3, Forecast.owmCode(999));    // not a code OpenWeatherMap uses
    }

    @Test
    public void aqiComesFromTheParticulatesOnTheUsScale() {
        assertEquals(50, Forecast.usAqi(12.0, 0));
        assertEquals(51, Forecast.usAqi(12.1, 0));
        assertEquals(100, Forecast.usAqi(35.4, 0));
        assertEquals(151, Forecast.usAqi(55.5, 0));
        assertEquals(301, Forecast.usAqi(250.5, 0));
        assertEquals(500, Forecast.usAqi(600, 0));
        // The worse of the two reads decides: coarse dust beside clean air.
        assertEquals(100, Forecast.usAqi(0, 154));
        assertEquals(50, Forecast.usAqi(0, 54));
    }

    @Test
    public void aqiBandsFollowTheUsScale() {
        assertEquals(0, Forecast.aqiBand(0));
        assertEquals(0, Forecast.aqiBand(50));
        assertEquals(1, Forecast.aqiBand(51));
        assertEquals(1, Forecast.aqiBand(100));
        assertEquals(2, Forecast.aqiBand(150));
        assertEquals(3, Forecast.aqiBand(151));
        assertEquals(4, Forecast.aqiBand(300));
        assertEquals(5, Forecast.aqiBand(500));
    }

    @Test
    public void onlyTheYellowBandGetsDarkText() {
        assertEquals(0xFF1A1A1A, Forecast.aqiTextColour(55));
        assertEquals(0xFFFFFFFF, Forecast.aqiTextColour(20));
        assertEquals(0xFFFFFFFF, Forecast.aqiTextColour(180));
    }

    @Test
    public void skyWordsAreAsFineAsTheCode() {
        assertEquals(R.string.sky_sunny, Forecast.sky(0, true));
        assertEquals(R.string.sky_clear, Forecast.sky(0, false));
        assertEquals(R.string.sky_partly_cloudy, Forecast.sky(2, true));
        assertEquals(R.string.sky_light_snow, Forecast.sky(71, true));
        assertEquals(R.string.sky_heavy_snow, Forecast.sky(75, true));
        assertEquals(R.string.sky_snow_showers, Forecast.sky(86, true));
        assertEquals(R.string.sky_thunderstorm, Forecast.sky(99, true));
        assertEquals(R.string.sky_cloudy, Forecast.sky(42, true));   // a code WMO does not use
    }

    @Test
    public void rainNearFreezingInDryAirIsSnow() {
        // The Ulaanbaatar blizzard: ICON said light rain (61) at 1.1 °C and 86 % humidity.
        assertEquals(71, Forecast.snowIfCold(61, 1.1, 86));
        assertEquals(73, Forecast.snowIfCold(63, 0.5, 80));
        assertEquals(75, Forecast.snowIfCold(82, -1, 90));
        // Warm rain stays rain; freezing rain stays freezing rain; dry codes are untouched.
        assertEquals(61, Forecast.snowIfCold(61, 6, 90));
        assertEquals(66, Forecast.snowIfCold(66, 0, 95));
        assertEquals(3, Forecast.snowIfCold(3, -5, 60));
    }

    @Test
    public void forecastDaysStayBetweenThreeAndSeven() {
        assertEquals(3, Forecast.forecastDays(3));
        assertEquals(7, Forecast.forecastDays(7));
        assertEquals(3, Forecast.forecastDays(0));
        assertEquals(7, Forecast.forecastDays(16));
    }

    @Test
    public void wetBulbMatchesStull() {
        // Stull (2011): 20 °C and 50 % give a wet-bulb of about 13.7 °C.
        assertEquals(13.7, Forecast.wetBulb(20, 50), 0.1);
        assertEquals(true, Forecast.wetBulb(1.1, 86) < 1.0);
    }

    @Test
    public void degreesRoundOnlyOnceInEitherUnit() {
        assertEquals(22, Forecast.degrees(21.6, false));
        assertEquals(71, Forecast.degrees(21.6, true));   // 70.88, not 22 °C rounded first (71.6 -> 72)
        assertEquals(32, Forecast.degrees(0, true));
        assertEquals(-40, Forecast.degrees(-40, true));
        assertEquals(212, Forecast.degrees(100, true));
    }

    @Test
    public void windInMilesPerHour() {
        assertEquals(0, Forecast.mph(0));
        assertEquals(10, Forecast.mph(16));
        assertEquals(62, Forecast.mph(100));
    }

    @Test
    public void theWeatherLineIsNineCleanFields() {
        // The launcher splits on '|' and reads nine fields: a pipe or a line break inside a field would break it.
        assertEquals("Bandung", WeatherInfo.clean("  Bandung  "));
        assertEquals("Kota Bandung", WeatherInfo.clean("Kota|Bandung"));
        assertEquals("a b", WeatherInfo.clean("a\nb"));
        assertEquals("New York", WeatherInfo.clean("New   York"));
        assertEquals("", WeatherInfo.clean(null));
        String[] fields = "Bandung|30|Light rain|34|65|11|42|Good|1792929600000".split("\\|", -1);
        assertEquals(9, fields.length);
    }
}
