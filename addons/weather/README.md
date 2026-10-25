# Weather

The weather for one city, full screen on the TV: now (temperature, feels-like, conditions), the US air quality
index as a coloured pill, and the next three days. For the [Clean Your TV](../../README.md) launcher it declares a
[module card](../../docs/modules.md), so once you answer **Show card** its tile reads "Weather" with a
sky-blue glow; on any other Android TV home it is an ordinary app.

![The weather page](https://raw.githubusercontent.com/Sumyabazar1/cleanyourtv/pr-assets/addon-weather/addon-3-weather.png)

- **The city** is chosen once: type its name with the remote's keyboard (or voice), pick it from the matches,
  and it is kept. **Change city** at the bottom chooses another.
- **Data** comes from [OpenWeatherMap](https://openweathermap.org/), on its free tier. A key is required; it
  is your own key, and it never sits in this repo (see [the key](#the-key) below).
  - **The city search** is the [Geocoding API](https://openweathermap.org/api/geocoding-api): the typed name
    gives the matches, and the one you pick gives its latitude and longitude.
  - **Now** is the [Current weather API](https://openweathermap.org/current): temperature, feels-like,
    conditions, humidity and wind.
  - **The next days** are the [5 day / 3 hour Forecast API](https://openweathermap.org/forecast5), five days
    in three-hour steps. The app groups the steps into the TV's own local days, and the step nearest midday
    picks each day's sky, so the icon is the one for the middle of that day rather than whichever step happens
    to come first.
  - **The air quality** is the [Air Pollution API](https://openweathermap.org/api/air-pollution): the
    pollutants for the city are converted in the app, offline, to the US AQI scale the page shows.
- **Snow, not rain, near freezing.** A provider types precipitation by the air temperature alone and can call
  snow "light rain" at 1 °C. The app decides by the wet-bulb temperature instead, which counts how dry the air
  is: at 1 °C or below, rain and drizzle are shown as snow.
- It reads OpenWeatherMap only while the page is on screen, every 30 minutes, or 5 minutes after a failure.
- Degrees Celsius by default (Fahrenheit is an option below), English words; day names follow the TV's language.

## The key

The key is never in the repo, and never in a file you commit. `app/build.gradle` injects it at build time
from the Gradle property or the environment variable `OPENWEATHER_API_KEY`, and without one the build still
succeeds: the app then says the key is missing instead of fetching.

```bash
OPENWEATHER_API_KEY=<key> ./gradlew assembleRelease
```

A free key takes a minute to make on [your OpenWeatherMap account](https://home.openweathermap.org/api_keys).
The free tier is enough for this app's calls. Pass the key where the build runs: an environment variable as
above, or `-POPENWEATHER_API_KEY=<key>` for Gradle, or `~/.gradle/gradle.properties`, which Gradle keeps out of
a project's git history.

## Options

Set in `app/src/main/res/values/options.xml` before building. With the defaults the page is as above.

| Option | Default | What it does |
|---|---|---|
| `forecast_days` | `3` | Days in the forecast row after today, 3 to 7. Above 3 the day cards share the row, with short day names, so a week fits on screen. |
| `show_humidity` | `false` | The relative humidity now, after "Feels like". |
| `show_wind` | `false` | The wind speed now in km/h, after "Feels like" (mph with `temperature_unit` `f`). |
| `temperature_unit` | `c` | `f` shows degrees Fahrenheit for the temperature now, feels-like and the days' highs and lows, converted from the reading before rounding. |
| `show_both_units` | `false` | The temperature now in the other unit too, at the start of the "Feels like" line ("22°C · Feels like 70°"). |

![A week, with humidity and wind](https://raw.githubusercontent.com/majnioui/cleanyourtv-contrib/pr-assets/addon-weather-week/weather-options.png)

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | OpenWeatherMap: the place search, the weather now, the forecast and the air quality. |

That is all. What is sent: the city name you type in the search, then that place's latitude and longitude for
the weather and the air, plus your key with every call. No account on the TV, no telemetry, no ads, no
background service, no location permission. The chosen city stays in the app's own storage (backups are off).

## Build and install

JDK 17 and Android SDK platform 34 with build-tools 34.0.0, as for the launcher:

```bash
cd addons/weather && OPENWEATHER_API_KEY=<key> ./gradlew assembleRelease
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py --push app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

Press Home: the launcher asks once whether to show the card. Change the answer later by holding OK on
Settings, then Module cards.

No JDK or Android SDK on your machine? The **Add-on APK** workflow builds this add-on in GitHub Actions and
publishes the release APK as a build artifact (a workflow artifact, not a release: nothing prebuilt lives in
this repo):

```bash
gh workflow run "Add-on APK" -f addon=weather
gh run watch
gh run download <run id> -n weather-apk -D out
```

That build has no key, so the APK from it starts with the "no key" message. To hand out a working build,
build it yourself with `OPENWEATHER_API_KEY` set, as above.

The release build is signed with the debug key so it installs over ADB like the launcher. Sign your own builds
with your own key if you hand them out; the launcher pins its Show answer to the signing key.

## Tested on

Built by CI (the Add-on APK workflow, `assembleRelease` and the unit tests) rather than on a TV: this
OpenWeatherMap version has not been run on a set yet.

The earlier Open-Meteo build was tried on a Hisense SmartTV 4K FFM (lushan, MediaTek MT9676), Google TV 12 /
Android 12 (city search with the TV keyboard, the weather page, the card prompt, and the card shown in the
launcher's dock), and its options on a Xiaomi TV (MiTV-MOOR2), Google TV 11, with `forecast_days` 7,
`show_humidity` and `show_wind` on.

## Maintainer

[@Sumyabazar1](https://github.com/Sumyabazar1)

OpenWeatherMap rebuild: Bangsat132
