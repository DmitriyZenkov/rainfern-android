# Rainfern

Rainfern is a Kotlin + Jetpack Compose Android weather app for personal use. It blends several free weather APIs into one forecast, keeps source transparency visible, stores offline history, and includes small/medium widgets.

## What it does

- Blended forecast screen with current, hourly, and daily weather.
- Source transparency screen that lists each provider and its latest values.
- Offline history with a red offline banner when the app is showing cached data.
- Location mode choice: ask first, refresh while open, or background refresh.
- Settings for refresh cadence, provider toggles, and an optional WeatherAPI.com key.
- Small and medium home screen widgets driven by the same cached blended forecast.

## Free API mix

Rainfern is wired for these free providers:

- Open-Meteo: global, no key, free for non-commercial use.
- MET Norway: global, no key, attribution required.
- weather.gov / NWS: U.S.-only official forecast and alerts, no key.
- WeatherAPI.com: optional global keyed provider on its free tier.

Notes:

- Open-Meteo and MET Norway are enabled by default.
- weather.gov activates automatically when the location resolves to U.S. coverage.
- WeatherAPI.com is optional and only used when you add your free key in settings.

## Aggregation

The blend is not a winner-takes-all source pick.

- Temperature and wind: weighted median.
- Rain probability, precipitation totals, humidity, cloud cover, and pressure: weighted mean.
- Condition label: weighted mode.
- Confidence falls when providers disagree, data is stale, or a provider is incomplete.
- Official regional sources get a location bonus.

See [docs/AGGREGATION.md](/C:/Users/rdime/Desktop/weather/docs/AGGREGATION.md).

## Build

Recommended local setup:

- Android Studio Jellyfish or newer.
- Android SDK Platform 34.
- JDK 17.

From Android Studio:

1. Open `/C:/Users/rdime/Desktop/weather`.
2. Let Gradle sync.
3. Build the debug APK with `Build > Build APK(s)`.

From terminal once the Gradle wrapper is complete and the Android SDK is installed:

```powershell
.\gradlew.bat assembleDebug
```

Expected APK path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install on phone

1. Enable developer mode and USB debugging on the phone, or allow sideloading from files.
2. Copy `app-debug.apk` to the phone, or install over USB:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

3. Open Rainfern and grant location permission.
4. If you want the third global provider, add your free WeatherAPI.com key in Settings.

## Possible next additions

- Air quality and pollen overlays.
- Favorite locations and manual search.
- Radar/cloud map tiles.
- Rain-start notifications.
- Route weather for trips.
- Wear OS tile or watch face.
