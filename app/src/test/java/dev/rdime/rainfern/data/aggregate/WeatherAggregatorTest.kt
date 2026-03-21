package dev.rdime.rainfern.data.aggregate

import dev.rdime.rainfern.data.model.Coordinates
import dev.rdime.rainfern.data.model.CurrentWeather
import dev.rdime.rainfern.data.model.HourlyWeather
import dev.rdime.rainfern.data.model.ProviderForecast
import dev.rdime.rainfern.data.model.ProviderId
import dev.rdime.rainfern.data.model.WeatherAlert
import dev.rdime.rainfern.data.model.WeatherCondition
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherAggregatorTest {
    @Test
    fun aggregateCurrentDownweightsTemperatureOutlier() {
        val now = Instant.parse("2026-03-20T12:00:00Z")
        val coordinates = Coordinates(55.0, 82.9)

        val providers = listOf(
            provider(ProviderId.OPEN_METEO, 18.5, WeatherCondition.RAIN, now),
            provider(ProviderId.MET_NORWAY, 19.0, WeatherCondition.RAIN, now),
            provider(ProviderId.WEATHER_API, 38.0, WeatherCondition.CLEAR, now),
        )

        val result = WeatherAggregator.aggregate(providers, coordinates, now)

        assertTrue(result != null)
        assertEquals(19.0, result!!.current.temperatureC!!, 1.5)
        assertEquals(WeatherCondition.RAIN, result.current.condition)
    }

    @Test
    fun aggregateProducesConfidenceWithinBounds() {
        val now = Instant.parse("2026-03-20T12:00:00Z")
        val coordinates = Coordinates(40.7, -74.0)
        val providers = listOf(
            provider(ProviderId.OPEN_METEO, 10.0, WeatherCondition.CLOUDY, now),
            provider(ProviderId.WEATHER_GOV, 10.5, WeatherCondition.CLOUDY, now),
        )

        val result = WeatherAggregator.aggregate(providers, coordinates, now)

        assertTrue(result != null)
        assertTrue(result!!.details.overallConfidence in 0.0..1.0)
        assertTrue(result.details.currentMetrics.isNotEmpty())
        assertEquals(WeatherCondition.CLOUDY, result.current.condition)
    }

    @Test
    fun aggregateEmitsCurrentDisagreementDiagnostics() {
        val now = Instant.parse("2026-03-20T12:00:00Z")
        val coordinates = Coordinates(40.7, -74.0)
        val providers = listOf(
            provider(ProviderId.OPEN_METEO, 8.0, WeatherCondition.CLOUDY, now),
            provider(ProviderId.WEATHER_GOV, 14.0, WeatherCondition.RAIN, now),
        )

        val result = WeatherAggregator.aggregate(providers, coordinates, now)

        assertTrue(result != null)
        assertTrue(result!!.details.currentDisagreements.isNotEmpty())
        assertTrue(result.details.currentDisagreements.any { it.spread > 0.0 })
    }

    @Test
    fun aggregateBoostsOfficialUsWeighting() {
        val now = Instant.parse("2026-03-20T12:00:00Z")
        val coordinates = Coordinates(40.7, -74.0)
        val providers = listOf(
            provider(ProviderId.OPEN_METEO, 10.0, WeatherCondition.CLOUDY, now),
            provider(ProviderId.WEATHER_GOV, 10.5, WeatherCondition.CLOUDY, now),
        )

        val result = WeatherAggregator.aggregate(providers, coordinates, now)

        assertTrue(result != null)
        assertTrue(result!!.details.providerWeights.getValue(ProviderId.WEATHER_GOV.name) > result.details.providerWeights.getValue(ProviderId.OPEN_METEO.name))
        assertEquals("US", result.details.regionalProfile.name)
    }

    @Test
    fun aggregateMergesDuplicateAlertsAcrossSources() {
        val now = Instant.parse("2026-03-20T12:00:00Z")
        val coordinates = Coordinates(40.7, -74.0)
        val providers = listOf(
            provider(
                ProviderId.OPEN_METEO,
                10.0,
                WeatherCondition.RAIN,
                now,
                alerts = listOf(
                    WeatherAlert(
                        title = "Flood Warning",
                        severity = "Severe",
                        description = "River flooding expected.",
                        startsAt = now.plusSeconds(1800),
                        endsAt = now.plusSeconds(21600),
                        sourceName = "Open-Meteo",
                    ),
                ),
            ),
            provider(
                ProviderId.WEATHER_GOV,
                10.5,
                WeatherCondition.RAIN,
                now,
                alerts = listOf(
                    WeatherAlert(
                        title = "Flood warning",
                        severity = "Moderate",
                        description = "Flood risk from heavy rain.",
                        startsAt = now.plusSeconds(1200),
                        endsAt = now.plusSeconds(21600),
                        sourceName = "weather.gov / NWS",
                    ),
                ),
            ),
        )

        val result = WeatherAggregator.aggregate(providers, coordinates, now)

        assertTrue(result != null)
        assertEquals(1, result!!.alerts.size)
        assertTrue(result.alerts.first().sourceName.contains("Open-Meteo"))
        assertTrue(result.alerts.first().sourceName.contains("weather.gov / NWS"))
    }

    @Test
    fun aggregatePrefersOfficialRegionalAlertWhenConflicting() {
        val now = Instant.parse("2026-03-20T12:00:00Z")
        val coordinates = Coordinates(40.7, -74.0)
        val providers = listOf(
            provider(
                ProviderId.OPEN_METEO,
                10.0,
                WeatherCondition.RAIN,
                now,
                alerts = listOf(
                    WeatherAlert(
                        title = "Flood Warning",
                        severity = "Severe",
                        description = "Generic flood warning.",
                        startsAt = now.plusSeconds(900),
                        endsAt = now.plusSeconds(21600),
                        sourceName = "Open-Meteo",
                    ),
                ),
            ),
            provider(
                ProviderId.WEATHER_GOV,
                10.5,
                WeatherCondition.RAIN,
                now,
                alerts = listOf(
                    WeatherAlert(
                        title = "Flood warning",
                        severity = "Moderate",
                        description = "Official NWS warning.",
                        startsAt = now.plusSeconds(1200),
                        endsAt = now.plusSeconds(21600),
                        sourceName = "weather.gov / NWS",
                    ),
                ),
            ),
        )

        val result = WeatherAggregator.aggregate(providers, coordinates, now)

        assertTrue(result != null)
        assertEquals(1, result!!.alerts.size)
        assertEquals("Moderate", result.alerts.first().severity)
    }

    @Test
    fun aggregateProducesForecastAnomaliesForSharpRainShift() {
        val now = Instant.parse("2026-03-20T12:00:00Z")
        val coordinates = Coordinates(55.0, 82.9)
        val providers = listOf(
            provider(
                ProviderId.OPEN_METEO,
                12.0,
                WeatherCondition.CLOUDY,
                now,
                hourly = listOf(
                    HourlyWeather(time = now.plusSeconds(3600), temperatureC = 12.0, precipitationProbabilityPercent = 5, condition = WeatherCondition.CLOUDY, conditionText = "Cloudy"),
                    HourlyWeather(time = now.plusSeconds(7200), temperatureC = 12.0, precipitationProbabilityPercent = 75, condition = WeatherCondition.RAIN, conditionText = "Rain"),
                ),
            ),
            provider(
                ProviderId.MET_NORWAY,
                12.0,
                WeatherCondition.CLOUDY,
                now,
                hourly = listOf(
                    HourlyWeather(time = now.plusSeconds(3600), temperatureC = 12.0, precipitationProbabilityPercent = 10, condition = WeatherCondition.CLOUDY, conditionText = "Cloudy"),
                    HourlyWeather(time = now.plusSeconds(7200), temperatureC = 12.0, precipitationProbabilityPercent = 80, condition = WeatherCondition.RAIN, conditionText = "Rain"),
                ),
            ),
        )

        val result = WeatherAggregator.aggregate(providers, coordinates, now)

        assertTrue(result != null)
        assertTrue(result!!.details.anomalies.any { it.title.contains("Rain signal") })
    }

    private fun provider(
        id: ProviderId,
        temperatureC: Double,
        condition: WeatherCondition,
        now: Instant,
        alerts: List<WeatherAlert> = emptyList(),
        hourly: List<HourlyWeather> = emptyList(),
    ) = ProviderForecast(
        providerId = id,
        providerName = id.name,
        sourceUrl = "https://example.test",
        attribution = "Test",
        coverage = "Global",
        locationName = "Test",
        coordinates = Coordinates(0.0, 0.0),
        fetchedAt = now,
        confidence = 0.9,
        current = CurrentWeather(
            observedAt = now,
            temperatureC = temperatureC,
            feelsLikeC = temperatureC,
            precipitationProbabilityPercent = if (condition == WeatherCondition.RAIN) 80 else 10,
            windSpeedMps = 4.0,
            condition = condition,
            conditionText = condition.name,
        ),
        hourly = hourly,
        alerts = alerts,
    )
}
