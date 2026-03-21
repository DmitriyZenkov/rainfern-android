# Rainfern Decisions

## 2026-03-20

### Step 1: Use A Separate Place Domain Instead Of Extending Location Permissions

Decision:
Manual place handling lives in `PlacesRepository`/`PlacesStore`, not in `LocationMode`.

Reasoning:
`LocationMode` is about permission and refresh policy. Manual place selection is a forecast target concern. Keeping them separate avoids overloading one setting with two unrelated responsibilities.

### Step 1: Cache Forecasts Per Location Key

Decision:
Forecast cache entries are stored per location key instead of one global `latest/providers/history` record.

Reasoning:
Manual search and favorites become unreliable if switching to a searched place overwrites the device-location forecast. Per-location cache entries preserve offline history and provider payloads for each target independently.

### Step 1: Use Open-Meteo For Geocoding

Decision:
Manual search uses Open-Meteo's free geocoding API.

Reasoning:
It is free, global, does not require a paid subscription, aligns with the existing provider set, and is enough for search before a more advanced geocoding strategy is added later.

### Step 1: Keep Device Location As The Default Active Target

Decision:
The app keeps device location as the active source unless the user explicitly selects a manual place.

Reasoning:
This preserves the original onboarding path while adding manual control for travel, favorites, and permission-light usage.

### Step 2: Make Onboarding Choice-First Instead Of Permission-First

Decision:
First-run onboarding offers forecast-target choices before directly asking for location permission.

Reasoning:
The app now supports two legitimate setup paths: device location and manual place search. A permission-first design would steer users into one path even when they may prefer the other.

### Step 2: Keep `Decide Later` As A First-Class Onboarding Outcome

Decision:
Users can defer onboarding without breaking the rest of the app flow.

Reasoning:
This avoids forcing an irreversible early choice and matches the requirement that location behavior be changeable later. It also keeps the app usable for users who want to inspect sources, settings, or manual places before granting permission.

### Step 3: Add Diagnostics Beside The Forecast Instead Of Embedding Them In Every Weather Model

Decision:
Disagreement data lives under `AggregationDetails` rather than inside `CurrentWeather`, `HourlyWeather`, or `DailyWeather`.

Reasoning:
The blended forecast remains the primary app payload. Diagnostics are supporting metadata, so keeping them in `AggregationDetails` minimizes churn in provider mapping, cache storage, and UI code while still making disagreement explorable.

### Step 3: Show Disagreement In The Transparency Surface First

Decision:
The first disagreement UI lands in `Sources`, not the main forecast hero.

Reasoning:
This keeps the default experience calm and readable while still exposing precision-oriented details for users who want to inspect model conflicts.

### Step 4: Add Metric Confidence As A Separate Diagnostic, Not A Replacement For Overall Confidence

Decision:
Metric-level confidence is added alongside the existing overall confidence instead of replacing it immediately.

Reasoning:
Overall confidence is still useful as a quick summary, but it hides uneven provider agreement across metrics. Keeping both preserves fast readability while exposing the more precise signal needed for detailed inspection.

### Step 4: Reuse The Same Diagnostic Pipeline For Disagreement And Confidence

Decision:
Per-metric confidence is emitted through the same `AggregationDetails` and `SlotDiagnostics` structures used for disagreement.

Reasoning:
This avoids maintaining parallel diagnostic systems and keeps later comparison/anomaly features aligned around one slot-based representation.

### Step 5: Use Internal Metric/Horizon Weighting Categories Instead Of Expanding The Public Forecast Models Again

Decision:
The stricter weighting logic uses internal aggregator categories for metric type and forecast horizon rather than exposing every weighting concern in public models.

Reasoning:
The user-facing data model already grew in steps 3 and 4 for diagnostics. Keeping calibration categories internal avoids unnecessary public API churn while still improving blending quality.

### Step 5: Expose Regional Profile But Not Full Weight Breakdown Yet

Decision:
The resolved regional profile is exposed in `AggregationDetails`, but provider-by-provider weight components are still kept internal for now.

Reasoning:
Regional profile is useful and easy to explain in the UI. Full contribution breakdown is better added when comparison and source drill-down views become richer, rather than exposing partial internals too early.

### Step 6: Merge Similar Alerts Before Rendering Them

Decision:
Alert deduplication now happens at aggregation time using normalized titles plus overlapping time windows.

Reasoning:
Different providers often describe the same event with slightly different text. Merging before the UI layer prevents noisy alert stacks and keeps the forecast screen focused on the most actionable signal.

### Step 6: Present One Priority Alert Before Secondary Alerts

Decision:
The forecast screen elevates the top alert and de-emphasizes the rest instead of rendering all alerts identically.

Reasoning:
This makes the app easier to scan under stress and aligns with the user goal of being user-friendly while still preserving the additional alerts for deeper review.

### Step 7: Derive Rain Onset And Accumulation From The Existing Blended Hourly Forecast

Decision:
Precipitation UX is computed from the blended hourly forecast instead of requiring a separate nowcast provider.

Reasoning:
This delivers a practical rain-start and accumulation summary immediately, stays within the current free-source stack, and avoids adding another dependency before map/radar work is in place.

### Step 8: Use Open-Meteo For AQI And Region-Gated Pollen

Decision:
AQI and pollen are sourced from Open-Meteo’s free Air Quality API.

Reasoning:
It provides a free, no-key path for AQI globally and exposes pollen where it is officially supported. This fits the free-only constraint and avoids relying on pollen features that are paywalled on other vendors.

### Step 8: Show Unavailable Pollen Honestly Instead Of Guessing

Decision:
When the free source does not provide pollen for the selected region or season, the UI shows an explicit coverage note.

Reasoning:
The app should remain precise and user-friendly. Pretending to have pollen coverage everywhere would degrade trust more than a clear limitation message.

### Step 9: Use RainViewer For The Map Layer Instead Of Adding Another Forecast Vendor

Decision:
The first in-app weather map uses RainViewer recent radar tiles over OpenStreetMap in an embedded WebView.

Reasoning:
RainViewer still offers a free personal-use radar path in 2026, and the feature request only requires a precipitation or cloud map layer. Using it as a map overlay avoids polluting the forecast blend with another provider while keeping the implementation lightweight and free-only.

### Step 9: Treat The Radar Map As An Interpretive Surface, Not A New Forecast Primitive

Decision:
The radar view reads the current active coordinates and stays separate from provider aggregation and cache structures.

Reasoning:
Radar tiles are useful for user interpretation, but they are not part of the blended forecast model and do not share the same time horizon or data shape. Keeping the map isolated avoids unnecessary coupling with the aggregation pipeline.

### Step 10: Use Provider-Supplied Dew Point And Visibility First, Then Derive Dew Point Only As Fallback

Decision:
Dew point and visibility are pulled from forecast providers where available, with dew point estimated from blended temperature and humidity only when needed.

Reasoning:
These metrics are part of the precision story, so source-native values are preferred. Dew point has a reasonable physical fallback, while visibility is too provider- and observation-dependent to infer reliably from other blended fields.

### Step 10: Keep Astronomy And Fine-Air Details In One Card

Decision:
Sunrise, sunset, golden hour, UV, dew point, and visibility are grouped into a single `Sky details` card on the forecast screen.

Reasoning:
These are secondary but high-value details. Grouping them keeps the home screen readable while still giving power users a single place to inspect daily sky conditions and timing.

### Step 11: Add A Dedicated Formatting Layer Instead Of Ad-Hoc Unit Conversion In Each Card

Decision:
Display conversion and time formatting now flow through shared helpers in `DisplayFormatting.kt`.

Reasoning:
Unit and locale customization touches many screens and will also be needed by widgets and notifications. Centralizing it now avoids copy-pasted conversions and makes later UI tightening safer.

### Step 11: Use System Locale For Language-Ready Formatting, But Expose Explicit Unit And Clock Overrides

Decision:
The app keeps locale-sensitive dates/times/numbers tied to the device locale, while unit families and 12/24-hour preference are configurable in settings.

Reasoning:
This gives useful customization immediately without pretending to offer full translation support before the string resources are localized. It also satisfies the user-friendly requirement for familiar units and time formatting.

### Step 12: Use Shared Widget Preferences Instead Of Per-Instance Configuration For The First Tightening Pass

Decision:
Rainfern widgets now share one configuration set for location, theme intensity, and secondary diagnostics.

Reasoning:
Per-instance Glance configuration would require a more invasive widget-configuration flow and more Android-specific plumbing. Shared preferences deliver real control now with much lower implementation risk and still satisfy the need for configurable widget behavior on a personal-use app.

### Step 12: Resolve Widget Forecasts From Cache, Not Live Repository Calls

Decision:
Widgets choose among cached forecast entries rather than triggering their own live provider fetch path.

Reasoning:
This keeps widgets fast, cheap on background work, and consistent with the existing offline-first cache model. It also avoids duplicating refresh logic outside `WeatherRepository`.

### Step 13: Explain Offline Cache Freshness Directly In The Forecast Surface

Decision:
Offline mode now shows cache age, last successful blend time, and cached providers directly in the UI instead of only a generic offline banner.

Reasoning:
Offline trust depends on recency and provenance. Users need to know how old the cached blend is and which providers contributed to it before they can judge whether it is still actionable.

### Step 14: Split Foreground And Background Refresh Policy

Decision:
Foreground cadence now controls on-open staleness refresh, while background cadence and constraints are configured separately for WorkManager.

Reasoning:
Foreground use and background battery/network policy are different concerns. Splitting them gives users more precise control and maps better to Android’s actual execution model.

### Step 14: Approximate Battery Saver With WorkManager Battery-Not-Low Constraints

Decision:
The battery-aware background toggle is implemented with `setRequiresBatteryNotLow(true)`.

Reasoning:
WorkManager does not expose a direct “battery saver” constraint. `battery not low` is the closest stable scheduling control available without adding more platform-specific background logic.

### Step 15: Use Open-Meteo First And Nominatim As The Geocoding Fallback/Reverse Layer

Decision:
Open-Meteo remains the primary manual-search geocoder, while Nominatim is used for low-volume fallback search and reverse geocoding.

Reasoning:
This keeps the geocoding stack explicitly free, works for both forward and reverse lookup, and avoids relying on opaque platform geocoder backends when the app’s source policy is meant to stay transparent.

### Step 16: Collapse Secondary Environment Cards Behind One Disclosure

Decision:
Air quality, UV, and astronomy details are no longer shown by default as separate cards at the top of the forecast screen; they now sit behind a single summary/disclosure card.

Reasoning:
The home screen had become too top-heavy as precision features accumulated. Progressive disclosure preserves the data without forcing every user to parse advanced context before reaching the forecast itself.

### Step 17: Prioritize Semantics On High-Traffic Forecast Surfaces First

Decision:
The first accessibility pass targets headings, the forecast hero, and alerts before broader component-by-component semantics coverage.

Reasoning:
These surfaces carry the most important information and are encountered immediately. Improving them first gives a real accessibility gain without stalling the rest of the feature sequence on a full app-wide audit.

### Step 18: Put Side-By-Side Provider Comparison In The Sources Screen

Decision:
Forecast comparison mode lives in the `Sources` destination rather than on the main forecast screen.

Reasoning:
Comparison is a precision-oriented inspection tool, not a default user flow. Keeping it in the transparency surface preserves a calm main forecast while still making provider-by-provider differences easy to inspect.

### Step 19: Let Official Regional Alerts Override Generic Alert Clusters

Decision:
When alert clusters conflict in supported regions, official regional sources receive a large enough priority bonus to become the lead alert.

Reasoning:
The requirement is about trust, not just visibility. A small bonus would still leave generic alerts in charge when severity text differs, which is not the intended behavior for U.S. NWS or regionally authoritative providers.

### Step 20: Keep Anomaly Detection Rule-Based And Human-Readable

Decision:
Anomalies are generated from explicit heuristics over disagreement, rain jumps, and temperature swings rather than a learned or opaque anomaly score.

Reasoning:
This feature is meant to improve trust and explainability. Users should be able to understand why Rainfern is warning about forecast instability instead of seeing another abstract confidence number.

### Step 21: Derive Trend Recall From Existing Snapshot Records

Decision:
Forecast-change recall is computed from the existing `SnapshotRecord` history rather than adding a second history model.

Reasoning:
The current snapshot store already captures the key user-facing deltas needed for a first recall pass: timestamp, temperature, summary, and confidence. Reusing it keeps the feature cheap and aligned with offline history.

### Step 22: Evaluate Notifications From The Blended Forecast After Background Refresh

Decision:
Notification rules are evaluated after the background worker refreshes the forecast, not from a separate notification polling pipeline.

Reasoning:
The blended forecast already represents the app’s final weather view and is refreshed on a controlled schedule. Reusing that path avoids duplicate background logic and keeps notifications aligned with what the user sees in the app.

### Step 22: Keep Notification Rules Simple And Throttled

Decision:
Initial notifications cover rain soon, freezing, strong wind, and one daily morning summary, all protected by `NotificationStore` rate limits.

Reasoning:
The feature should be useful without becoming noisy. A small set of high-signal notifications with clear throttling is a safer first pass than exposing many overlapping alerts immediately.

### Step 23: Keep Activity Modules Rule-Based And Forecast-Derived

Decision:
Personal activity guidance is computed directly from the blended forecast in the UI layer rather than stored as a new domain model or fetched from a third-party activity API.

Reasoning:
The requirement is to make the app more user-friendly while staying precise. Transparent heuristics over rain, wind, temperature, AQI, cloud cover, and alerts are explainable, cheap to maintain, and stay aligned with the same forecast the user already sees.

### Step 24: Polish The Visual System Globally Instead Of Restyling Each Card Individually

Decision:
Theme polish is implemented as a backdrop and palette refinement at the app shell/theme level rather than a card-by-card redesign.

Reasoning:
This is the safest way to improve cohesion and calmness late in the sequence. A global atmospheric backdrop and refined surface colors improve the look of every screen immediately without risking regressions across the many already-added forecast features.

### Step 25: Extend The Existing Sources Screen Instead Of Adding Another Destination

Decision:
Attribution and provider metadata are added to the existing `Sources` destination rather than introducing a separate metadata tab.

Reasoning:
The app already had a source-transparency surface, so the cleanest design is to deepen it into a full provenance view. This keeps forecast comparison, disagreement, attribution, and supporting-service metadata together in one place instead of scattering trust-related information across multiple screens.

### Validation Note

Gradle project evaluation for this step was validated with:

- workspace-local `GRADLE_USER_HOME`
- workspace-local `ANDROID_USER_HOME`
- JetBrains Rider bundled JBR 21 as `JAVA_HOME`

This is an environment constraint for this workspace session, not an application runtime requirement.
