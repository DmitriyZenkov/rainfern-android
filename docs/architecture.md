# Rainfern Architecture

## Current Shape

Rainfern is a Kotlin + Jetpack Compose Android app with a small repository-based data layer:

- `WeatherRepository` orchestrates provider refreshes, aggregation, cache writes, widget updates, and settings-aware refresh scheduling.
- `PlacesRepository` owns manual place search, favorites, active manual-place selection, and switching back to device location.
- `ForecastCacheStore` persists forecast data per location key so device-based weather and manually selected places do not overwrite each other.
- `PlacesStore` persists the active saved place and the user's saved/favorite places.
- `GeocodingRepository` searches free place results through Open-Meteo geocoding.
- `RainfernViewModel` combines weather payload state with transient place-search UI state.
- `RainfernApp` currently keeps feature UI in one Compose file with destinations for forecast, places, sources, history, and settings.

## Step Log

### Step 1: Manual Location Search And Favorites

This step introduced a place-centric path alongside device location:

- Added `SavedPlace` and `PlacesState` models to represent user-selected places and favorites.
- Reworked `ForecastCache` into per-location cache entries keyed by `device_location` or a saved-place id.
- Added `PlacesStore` for persisted manual place state.
- Added `PlacesRepository` for selecting a place, toggling favorites, and restoring device location mode.
- Added `GeocodingRepository` using Open-Meteo's free geocoding endpoint for manual search.
- Updated `WeatherRepository` so forecast refreshes resolve coordinates from the active manual place first, then device location, then cached coordinates for that location.
- Added a dedicated `Places` navigation destination with search results, favorites, recent places, and active-location switching.

### Step 2: Better Location Onboarding

This step changed first-run and pre-consent guidance from a permission-first prompt to a choice-first flow:

- Forecast onboarding now presents `while open`, `background`, `search manually`, and `decide later` as first-run options.
- Manual search onboarding routes straight into the `Places` screen instead of forcing location permission first.
- Deferred onboarding is explicitly supported through `requestShown`, so users can skip initial location setup and return later through Settings or Places.
- Empty-state messaging now explains the target-selection model instead of only asking for permission.

### Step 3: Provider Disagreement Visualization

This step added additive forecast diagnostics around the existing blended forecast:

- Added `ForecastMetric`, `MetricDisagreement`, and `SlotDiagnostics` models.
- Extended `AggregationDetails` with current, hourly, and daily disagreement diagnostics.
- Updated `WeatherAggregator` to emit disagreement summaries for current conditions, the next 24 hourly slots, and the next 7 daily slots.
- Added a disagreement section to the `Sources` screen showing current spreads plus the most disputed hours and days.

### Step 4: Confidence Per Metric

This step expanded diagnostics from disagreement-only to metric-level trust:

- Added `MetricConfidence` and attached it to current diagnostics plus hourly/daily slot diagnostics.
- Updated `WeatherAggregator` to compute separate confidence scores for temperature, rain chance, wind, cloud cover, and condition.
- Updated the forecast hero to show metric-level confidence for the most important current metrics.
- Added a metric-confidence section to the `Sources` screen so users can compare confidence by weather dimension rather than relying on one overall score.

### Step 5: More Rigorous Aggregation

This step strengthened the blending engine itself:

- Added regional-profile diagnostics (`GLOBAL`, `US`, `NORTHERN_EUROPE`).
- Refactored provider weighting to account for metric type, forecast horizon bucket, freshness sensitivity, completeness, and regional calibration.
- Gave official and regional providers stronger influence where they are most credible, while reducing their weight on longer horizons where their coverage is weaker.
- Surfaced the resolved regional calibration in the `Sources` screen alongside the existing weighting explanation.

### Step 6: Alert Prioritization And Deduplication

This step upgraded alert handling from an exact-field union to a prioritized merge:

- Alert aggregation now clusters near-duplicate alerts by normalized title and overlapping time windows.
- Merged alerts preserve the most actionable alert as the lead entry while combining source names.
- Alert ordering now accounts for severity plus near-term/actionable wording instead of severity alone.
- The forecast screen now shows a single priority alert first, followed by quieter secondary alerts.

### Step 7: Better Precipitation Experience

This step added a precipitation-focused interpretation layer to the forecast screen:

- Added a precipitation insight card to the main forecast.
- The card summarizes current intensity, short-horizon accumulation, peak chance, and approximate rain onset timing from the hourly blend.
- The feature is UI-only and uses already-aggregated hourly/current data, so it stays provider-agnostic and cheap to maintain.

### Step 8: Air Quality And Pollen

This step added a secondary environmental-data path:

- Added `AirQualitySnapshot` and `PollenSnapshot` to the cached aggregated forecast.
- Added `AirQualityRepository`, which calls Open-Meteo’s free Air Quality API.
- Weather refreshes now enrich the blended forecast with AQI, PM2.5/PM10/ozone/UV, and pollen where the source provides it.
- The forecast screen now shows an air-quality and pollen card, including a coverage note when pollen is unavailable for the current region/season.

### Step 9: Radar Map Layer

This step added a lightweight map surface without changing the forecast-provider blend:

- Added a dedicated `Radar` navigation destination.
- Added `RadarMapCard`, which embeds a WebView-based Leaflet map centered on the active forecast target.
- Added `RadarMapHtml.kt`, which builds a self-contained map document using OpenStreetMap base tiles plus RainViewer recent past radar tiles.
- The map uses the active forecast coordinates from the current blended forecast, active manual place, or cached location fallback.
- Radar source limits are surfaced directly in the UI so users understand the free-source zoom and history constraints.

### Step 10: Astronomy And Fine-Grained Atmospherics

This step expanded the forecast model and main dashboard with more situational detail:

- Extended `CurrentWeather` and `HourlyWeather` with dew point and visibility fields.
- Extended `DailyWeather` with `uvIndexMax`, while reusing the existing sunrise and sunset fields.
- Updated Open-Meteo, MET Norway, and WeatherAPI mappings to capture dew point, visibility, and UV where those APIs expose them.
- Updated `WeatherAggregator` to blend or backfill dew point, visibility, and daily UV.
- Added `SkyDetailCard` to the forecast screen to show sunrise, sunset, UV, dew point, visibility, and derived morning/evening golden-hour windows.

### Step 11: Unit And Locale Customization

This step added a first formatting layer on top of the raw metric data:

- Added `TemperatureUnit`, `WindUnit`, `PressureUnit`, and `TimeFormatPreference` to `AppSettings`.
- Extended `SettingsStore`, `WeatherRepository`, and `RainfernViewModel` with update flows for those preferences.
- Added `DisplayFormatting.kt` to centralize temperature, wind, pressure, clock, and history timestamp formatting.
- Updated the main forecast, history, source transparency, and sky-details surfaces to respect the selected display settings.
- Kept formatting locale-driven through the device locale while allowing explicit unit and 12/24-hour overrides.

### Step 12: Widget Tightening

This step made the existing widgets more configurable without changing the widget footprint:

- Added shared widget preferences to `AppSettings` for location target, theme intensity, and diagnostic detail mode.
- Extended the settings flow so widget preferences are persisted through `SettingsStore`, exposed by `RainfernViewModel`, and trigger widget refreshes immediately.
- Updated both Glance widgets to resolve their own forecast target from cache instead of always using the currently active forecast.
- Reused the new display-formatting helpers inside widgets so temperature, clock, and pressure formatting stay aligned with app settings.
- Added widget visual intensity modes plus clean/source/confidence secondary detail modes.

### Step 13: Smarter Offline Mode

This step made offline behavior more explicit at the UI level:

- Added an `OfflineStatusCard` that surfaces cached forecast age, last successful blended refresh time, and the providers represented in the cached payload.
- Reused the same offline diagnostics on both the forecast and history screens.
- Added a lightweight relative-age helper so offline trust signals read as `1h 20m old` instead of only raw timestamps.

### Step 14: Background Refresh Policy Refinement

This step split refresh behavior into foreground and background policy paths:

- Added separate background refresh settings for cadence, Wi-Fi-only execution, and battery-aware behavior.
- Updated the foreground app flow so Rainfern auto-refreshes on open only when the cached forecast is older than the foreground cadence threshold.
- Updated `RefreshScheduler` to use the new background cadence and map the policy into WorkManager network/battery constraints.
- Ensured settings changes reschedule background work and refresh widgets immediately.

### Step 15: Search And Geocoding Provider Strategy

This step completed the free geocoding stack beyond manual search:

- Kept Open-Meteo geocoding as the primary free forward-search provider for manual place lookup.
- Added Nominatim fallback search for low-volume cases where the primary search returns weak coverage.
- Added `ReverseGeocodingRepository` backed by Nominatim for reverse lookup of device-selected coordinates.
- Updated `WeatherRepository` to apply reverse-geocoded labels to device-location forecasts before caching when that label is available.
- The resulting provider strategy is now explicit: Open-Meteo primary search, Nominatim fallback/reverse geocoding, and forecast-provider location names only as fallback.

### Step 16: Home Screen Simplification

This step reduced the amount of advanced information shown by default on the forecast screen:

- Added `ProgressiveDetailsCard` as a single summary/disclosure surface for air-quality and astronomy detail.
- Kept the hero, alerts, precipitation, hourly blend, and daily blend directly visible.
- Moved the heavier secondary cards (`AirQualityCard` and `SkyDetailCard`) behind one explicit reveal action.
- Preserved access to the same data while making the default scroll path calmer and easier to scan.

### Step 17: Accessibility Pass

This step improved core accessibility without changing the overall visual direction:

- Added heading semantics to major section titles and screen intros.
- Added screen-reader summaries to the forecast hero and alert cards.
- Kept alert importance readable through explicit textual severity/source cues instead of relying on color alone.
- Focused on high-traffic forecast surfaces first so later UI refactors can build on accessible defaults.

### Step 18: Forecast Comparison Mode

This step turned the source-transparency screen into an actual comparison surface:

- Added a `ForecastComparisonCard` to the `Sources` screen.
- Users can switch between `Current`, `Hourly`, and `Daily` comparison modes.
- Hourly and daily comparisons let the user pick a specific blended slot, then line up `Blended` against each provider’s matching value.
- Comparison formatting reuses the app’s display-unit helpers so source inspection stays consistent with the chosen unit settings.

### Step 19: Severe-Weather Trust Rules

This step tightened alert handling around official regional sources:

- Alert prioritization now includes an official-source trust bonus based on the resolved regional profile.
- In the U.S., `weather.gov / NWS` alerts can now outrank conflicting generic-provider alerts for the same event cluster.
- In northern Europe, MET Norway gets a similar but smaller regional trust boost.
- Added a unit test covering the official-source override behavior for a U.S. alert conflict.

### Step 20: Basic Anomaly Detection

This step added a small interpretation layer on top of the blended forecast:

- Added `ForecastAnomaly` and an `anomalies` list under `AggregationDetails`.
- The aggregator now emits lightweight anomaly items for strong provider disagreement, sharp rain-signal shifts, rapid temperature swings, and large day-night spreads.
- Added a `Watch for` card on the forecast screen to surface those anomalies in plain language.
- Added a unit test covering anomaly emission for a sharp precipitation-probability jump.

### Step 21: Historical Snapshots And Trend Recall

This step made cached history more comparative instead of purely archival:

- Added `HistoryTrendCard` to the history screen.
- The card compares the latest snapshot against an earlier snapshot from the same day and against the latest prior-day snapshot when available.
- Trend recall currently focuses on temperature delta and summary changes, which are already available in `SnapshotRecord`.
- This builds on the existing local snapshot history without adding another persistence path.

### Step 22: Custom Notifications

This step turned notification settings into actual behavior:

- Added `WeatherNotificationManager`, which evaluates the blended forecast after background refreshes.
- Implemented throttled rules for rain soon, freezing conditions, strong wind, and a daily morning summary.
- Added Android notification channel setup plus Android 13+ notification-permission handling.
- Added notification toggles to the settings screen so the feature is user-configurable instead of hidden in storage only.

### Step 23: Personal Activity Modules

This step added a practical decision layer on top of the blended forecast:

- Added an `Activity outlook` card to the forecast screen.
- The card scores walking, running, gardening, laundry, and stargazing from the current blend plus the next forecast hours.
- Scoring considers temperature comfort, rain probability and accumulation, wind, AQI, cloud cover, and active severe alerts.
- The feature stays local to the UI layer and reuses the existing aggregated payload instead of introducing another persistence or provider path.

### Step 24: Theme Polish

This step tightened the app-wide visual atmosphere instead of redesigning individual screens:

- Added an `AtmosphericBackdrop` composable with layered vertical, radial, and diagonal gradients.
- Refined the color palette with softer mist and teal highlight tones while keeping the dark weather-rich direction.
- Updated the Material color scheme so surfaces and accents align better with the new backdrop.
- Tuned the bottom navigation bar to sit more lightly over the background instead of reading as a heavy opaque slab.

### Step 25: Attribution And Provider Metadata Screen

This step completed the source-transparency surface into a fuller provenance screen:

- Split the `Sources` screen into explicit forecast-provider and supporting-service sections.
- Expanded provider cards to show attribution, coverage, key status, last fetch age, provider-specific location labels, and fetch timestamps.
- Added supporting metadata cards for AQI/pollen, geocoding, reverse geocoding, radar, and base-map services.
- Kept the aggregated forecast as the default experience while making the full free-source stack inspectable in one destination.

## Known Structural Debt

- UI remains concentrated in `RainfernApp.kt`; later steps should split forecast, places, settings, alerts, and diagnostics into separate modules.
- Forecast diagnostics, notifications, AQI/pollen, map behavior, and accessibility-specific UI behavior are not yet modeled separately.
