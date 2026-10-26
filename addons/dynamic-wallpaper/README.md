# Dynamic Wallpaper

A wallpaper that follows the weather and the time of day. The add-on takes a reading from
[Open-Meteo](https://open-meteo.com/) every 30 minutes (no API key, no account, no location permission: the city is
written in `app/src/main/res/values/options.xml`), picks one of twelve pictures drawn by
`tools/make_wallpapers.py`, and publishes it for the [Clean Your TV](../../README.md) launcher.

The launcher shows it when its `live_wallpaper` switch is on (a theme can set `"live_wallpaper": true`). The launcher
itself still has no network permission: this app owns the fetch, the cache and the choice, and the launcher reads the
last picture published through the app's provider.

- **Twelve pictures**: clear, clouds, rain, storm, fog and snow, each by day and by night, drawn from code in
  `tools/make_wallpapers.py` — this repo's own work, no downloaded photo and no licence question.
- **The weather first, the time of day second**: a WMO code from the reading picks the mood, the reading's own sunrise
  and sunset pick day or night.
- **Offline is fine**: a reading is cached on the TV before it is shown, a failed fetch keeps the last one, and the
  picture already published stays on Home. Home never waits for the network.
- **Small cache**: only the picture in use is copied into the app's private storage (20 to 100 KB), replaced in one
  step so a reader never sees half a picture.
- **The page** (the card) shows the reading the picture came from: temperature, feels-like, high and low, humidity,
  wind, rain chance, which picture is up and when it was updated, with **Refresh now**.

## Build

```bash
cd addons/dynamic-wallpaper
./gradlew assembleRelease     # JDK 17, Android SDK platform 34
```

Or let CI build it: `gh workflow run "Add-on APK" -f addon=dynamic-wallpaper`.

## Permissions

`INTERNET` only, for the reading. No location permission: the place is a build setting, not something the TV is asked
for. No accessibility service, no overlay, no foreground service, no boot receiver.

## Another city

Change `place_name`, `place_latitude`, `place_longitude` and `place_timezone` in
`app/src/main/res/values/options.xml` and rebuild. The pictures do not depend on the place.

## Tested on

POLYTRON 2K Google TV (device `nagai`, MediaTek mt5867), Android 14.
