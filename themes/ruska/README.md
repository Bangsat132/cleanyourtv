# Ruska

A Finnish autumn ("ruska") dusk over a lake: a spruce shoreline, birches in autumn colour and a few
falling leaves, drawn in code by `make_wallpaper.py`. No photos, artwork or fonts are used, so the
picture is the theme's own and anyone can redraw it.

![Ruska theme](screenshot.jpg)

**Author:** CleanYourTV contributors · OpenWeatherMap weather add-on rebuild by Bangsat132

## What it changes

| Key | Value | Effect |
|---|---|---|
| `background_color` | `#07080D` | Near-black blue behind everything, so the page stays dark around the picture |
| `wallpaper` | `true` | The lake dusk fills the screen |
| `ambient_glow` | `false` | No full-screen colour wash: the autumn colours keep their own light |
| `app_glow` | `true` | The focus highlight glows in the focused app's colour |
| `dock_opacity` | `70` | The dock band is 70% opaque, so the lake shows through it |
| `focus_overshoot` | `false` | The highlight glides straight to the tile instead of swinging past it |

Every other key keeps the value from `config.xml`. The dock is left to `fill_dock`, so your own apps
fill it; set `dock_apps` in `config.json` or in Launcher settings to pick an order.

## The wallpaper

`wallpaper.jpg` is 1920×1080 and under 300 KB, drawn from nothing but code. To redraw it, or to change
it, you need Python 3 with Pillow and NumPy:

```bash
python3 -m venv /tmp/ruska && /tmp/ruska/bin/pip install pillow numpy
/tmp/ruska/bin/python themes/ruska/make_wallpaper.py
```

Run it from the repo root: it writes `themes/ruska/wallpaper.jpg` next to itself. The random seed is
fixed (1409), so a redraw gives the same picture; change the seed in `make_wallpaper.py` for a
different shore. `--night` draws `wallpaper_night.jpg` instead (the same shore under a crescent moon),
which the launcher has no key to show, so it is not shipped. Keep the result under about 300 KB and at
1920×1080, as [themes/README.md](../README.md#wallpapers) asks: a 1 GB TV decodes it on every Home
start.

The picture stays dark on purpose at the top corners and the bottom third, where the clock, the hero
name and the dock band sit.

## Addons

**Weather.** Build it with your own free OpenWeatherMap key, as
[addons/weather/README.md](../../addons/weather/README.md) describes: the key is never in the repo, it
is passed to the build as `OPENWEATHER_API_KEY`. From the repo root:

```bash
cd addons/weather && OPENWEATHER_API_KEY=<key> ./gradlew assembleRelease && cd ../..
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push addons/weather/app/build/outputs/apk/release/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

No key yet? The **Add-on APK** workflow builds the APK for you; download it from the run's artifacts
and install it, then let the launcher ask about the card:

```bash
gh workflow run "Add-on APK" -f addon=weather
gh run watch
gh run download <run id> -n weather-apk -D out
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py --push out/app-release.apk /data/local/tmp/addon.apk
TV_HOST=<tv ip> tools/venv/bin/python -I tools/tv.py 'pm install -r /data/local/tmp/addon.apk; rm -f /data/local/tmp/addon.apk'
```

That build carries no key and says so on screen; build it yourself, as above, for a working card.
Either way, press Home after installing and answer **Show card**: the Weather tile then sits in the
grid beside the ruska dock, on the same page as the lake, with the app's own sky-blue glow (the dock
band stays 70% opaque behind it). Answer again later by holding OK on Settings, then Module cards.

## Install

```bash
TV_HOST=<tv ip> tools/theme.sh themes/ruska
# Windows
powershell -ExecutionPolicy Bypass -File tools\theme.ps1 -TvHost <tv ip> themes\ruska
```

Push it from the repo root. Undo with `tools/theme.sh --reset`.

## Tested on

The theme itself: a real TV, with its `screenshot.jpg` of Home taken there. Nothing is written up by
model yet; say which TV you tried it on in a pull request and it goes here.

CI builds the OpenWeatherMap weather add-on but has not run it on a set, so the Weather card beside
the dock is untried as of this version.

## Credit and forks

You may copy, change and reuse this theme and its wallpaper. If you publish a modified version, use a
different folder name and identifier so people can tell them apart.
