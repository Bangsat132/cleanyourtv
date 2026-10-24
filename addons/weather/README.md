# Weather Card Addon

Module card for the CleanYourTV launcher showing real-time weather.

## Features
- **OpenWeatherMap API** — Free tier: 1000 calls/day
- **Location**: GPS (precise), IP-based (approximate), or manual city
- **Refresh**: 5 min default (configurable 5/15/30/60 min)
- **Units**: Metric (°C) or Imperial (°F)
- **Module Card spec** — Launcher detects and prompts "Show on Home?"
- **Settings UI** — API key, location mode, units, refresh interval

## Build

```bash
cd addons/weather
../../gradlew assembleRelease
```

Output: `addons/weather/build/outputs/apk/release/weather-release.apk`

## Install on TV

```bash
TV_HOST=192.168.100.44 ../../tools/venv/bin/python -I ../../tools/b64push.py build/outputs/apk/release/weather-release.apk /data/local/tmp/weather.apk
TV_HOST=192.168.100.44 ../../tools/venv/bin/python -I ../../tools/tv.py 'pm install -r /data/local/tmp/weather.apk'
```

## Setup

1. Open **Weather Card** from app library or sideload launcher
2. Enter OpenWeatherMap API key (get free at openweathermap.org/api)
3. Choose location mode: GPS / IP / Manual
4. Launcher will prompt: "Weather card wants to show on Home. Allow?"

## Permissions
- `INTERNET` — API calls
- `ACCESS_FINE_LOCATION` — GPS location
- `ACCESS_COARSE_LOCATION` — Network location
- `FOREGROUND_SERVICE` — Periodic sync
- `RECEIVE_BOOT_COMPLETED` — Restart sync after reboot

## Card Spec
- `refreshIntervalMinutes`: 5 (default, configurable)
- `glowColor`: #4FC3F7 (light blue)
- `cardColor`: #0D1B2A (dark navy)
- `cardTextColor`: #FFFFFF
- Action: Opens SettingsActivity

## Uninstall
```bash
TV_HOST=192.168.100.44 ../../tools/venv/bin/python -I ../../tools/tv.py 'pm uninstall com.gathin.weather'
```