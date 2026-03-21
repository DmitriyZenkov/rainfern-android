# Rainfern Aggregation Algorithm

Rainfern normalizes each provider response into one internal weather model and then blends the overlapping values.

## Provider set

- `OPEN_METEO`
- `MET_NORWAY`
- `WEATHER_GOV`
- `WEATHER_API`

Any provider can be unavailable for a given location or request. The blend only uses the providers that returned usable data.

## Weight formula

Each provider gets a dynamic weight:

```text
weight =
  base_provider_weight
  * freshness_factor
  * completeness_factor
  * provider_confidence
  * horizon_factor
  * regional_factor
```

Where:

- `base_provider_weight` prefers official or stronger baseline sources.
- `freshness_factor` drops when the payload is older.
- `completeness_factor` drops if current/hourly/daily blocks are missing.
- `provider_confidence` is the provider-specific trust score assigned by the adapter.
- `horizon_factor` slightly reduces weight further into the future.
- `regional_factor` boosts location-specific official coverage, such as `WEATHER_GOV` inside the U.S.

## Metric blend rules

- Temperature and wind use a weighted median.
- Rain probability, precipitation totals, humidity, cloud cover, and pressure use a weighted mean.
- Condition text uses a weighted mode.
- Alerts are unioned and sorted by severity.

## Conflict handling

Before averaging numeric metrics, Rainfern computes the plain median and downweights values that are far from it.

Examples:

- A single provider reading `38C` while the others read `18C` and `19C` will be heavily downweighted.
- A rain probability that differs sharply from the rest will still count, but with less influence.

This is how Rainfern avoids one noisy provider taking over the final forecast.

## Confidence score

The final confidence score is:

```text
0.50 * agreement_score
+ 0.25 * freshness_score
+ 0.25 * availability_score
```

Where:

- `agreement_score` measures how close providers are to each other.
- `freshness_score` measures response age.
- `availability_score` reflects how many providers participated and how complete they were.

The result is clamped to `0.0..1.0`.
