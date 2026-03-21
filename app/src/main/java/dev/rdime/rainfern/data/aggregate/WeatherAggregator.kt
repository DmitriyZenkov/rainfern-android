package dev.rdime.rainfern.data.aggregate

import dev.rdime.rainfern.data.model.AggregatedForecast
import dev.rdime.rainfern.data.model.AggregationDetails
import dev.rdime.rainfern.data.model.Coordinates
import dev.rdime.rainfern.data.model.ForecastMetric
import dev.rdime.rainfern.data.model.ForecastAnomaly
import dev.rdime.rainfern.data.model.CurrentWeather
import dev.rdime.rainfern.data.model.DailyWeather
import dev.rdime.rainfern.data.model.HourlyWeather
import dev.rdime.rainfern.data.model.MetricConfidence
import dev.rdime.rainfern.data.model.MetricDisagreement
import dev.rdime.rainfern.data.model.ProviderForecast
import dev.rdime.rainfern.data.model.ProviderId
import dev.rdime.rainfern.data.model.RegionalProfile
import dev.rdime.rainfern.data.model.SlotDiagnostics
import dev.rdime.rainfern.data.model.WeatherAlert
import dev.rdime.rainfern.data.model.WeatherCondition
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

object WeatherAggregator {
    fun aggregate(
        providers: List<ProviderForecast>,
        coordinates: Coordinates,
        now: Instant = Instant.now(),
        targetTimeZoneId: String? = null,
    ): AggregatedForecast? {
        val activeProviders = providers.filter { it.current != null || it.hourly.isNotEmpty() || it.daily.isNotEmpty() }
        if (activeProviders.isEmpty()) {
            return null
        }
        val zoneId = resolveZoneId(targetTimeZoneId ?: activeProviders.firstNotNullOfOrNull { it.timeZoneId.takeIf(String::isNotBlank) })

        val locationName = activeProviders.firstNotNullOfOrNull { it.locationName.takeIf(String::isNotBlank) }
            ?: "${coordinates.latitude}, ${coordinates.longitude}"
        val current = aggregateCurrent(activeProviders, coordinates, now) ?: return null
        val hourly = aggregateHourly(activeProviders, coordinates, now)
        val daily = aggregateDaily(activeProviders, coordinates, now, zoneId)
        val alerts = aggregateAlerts(activeProviders, now, coordinates)
        val regionalProfile = resolveRegionalProfile(coordinates)
        val currentMetrics = buildCurrentMetricConfidence(activeProviders, now)
        val currentDisagreements = buildCurrentDisagreements(activeProviders)
        val hourlyDiagnostics = buildHourlyDiagnostics(activeProviders, now)
        val dailyDiagnostics = buildDailyDiagnostics(activeProviders, now, zoneId)
        val anomalies = buildAnomalies(hourly, daily, hourlyDiagnostics, zoneId)

        val agreementScore = computeAgreementScore(activeProviders)
        val freshnessScore = activeProviders.map { freshnessFactor(it, now) }.average()
        val availabilityScore = activeProviders.sumOf { maxOf(it.confidence, 0.65) } / (4.0)
        val overallConfidence = ((agreementScore * 0.5) + (freshnessScore * 0.25) + (availabilityScore * 0.25))
            .coerceIn(0.0, 1.0)

        return AggregatedForecast(
            locationName = locationName,
            coordinates = coordinates,
            timeZoneId = zoneId.id,
            fetchedAt = activeProviders.maxOf { it.fetchedAt },
            current = current,
            hourly = hourly,
            daily = daily,
            alerts = alerts,
            details = AggregationDetails(
                activeProviderIds = activeProviders.map { it.providerId },
                providerWeights = activeProviders.associate { provider ->
                    provider.providerName to providerWeight(provider, WeightMetric.TEMPERATURE, coordinates, 0.0, now)
                },
                regionalProfile = regionalProfile,
                overallConfidence = overallConfidence,
                agreementScore = agreementScore,
                freshnessScore = freshnessScore,
                currentMetrics = currentMetrics,
                currentDisagreements = currentDisagreements,
                hourlyDiagnostics = hourlyDiagnostics,
                dailyDiagnostics = dailyDiagnostics,
                anomalies = anomalies,
                notes = listOf(
                    "Weighted median is used for temperature and wind to resist outliers.",
                    "Precipitation and cloud values are blended using weighted averages.",
                    "Official local sources get regional bonuses when applicable.",
                ),
            ),
        )
    }

    private fun aggregateCurrent(
        providers: List<ProviderForecast>,
        coordinates: Coordinates,
        now: Instant,
    ): CurrentWeather? {
        val samples = providers.mapNotNull { provider -> provider.current?.let { provider to it } }
        if (samples.isEmpty()) {
            return null
        }
        val temp = weightedMedian(samples, coordinates, now, metric = WeightMetric.TEMPERATURE, threshold = 6.0) { it.temperatureC }
        val feelsLike = weightedMedian(samples, coordinates, now, metric = WeightMetric.TEMPERATURE, threshold = 6.0) { it.feelsLikeC }
        val dewPoint = weightedMean(samples, coordinates, now, metric = WeightMetric.MOISTURE, threshold = 8.0) { it.dewPointC }
        val precipitation = weightedMean(samples, coordinates, now, metric = WeightMetric.PRECIPITATION, threshold = 4.0) { it.precipitationMm }
        val precipitationProbability = weightedMean(samples, coordinates, now, metric = WeightMetric.PRECIPITATION, threshold = 35.0) {
            it.precipitationProbabilityPercent?.toDouble()
        }?.roundToInt()
        val humidity = weightedMean(samples, coordinates, now, metric = WeightMetric.MOISTURE, threshold = 25.0) {
            it.humidityPercent?.toDouble()
        }?.roundToInt()
        val wind = weightedMedian(samples, coordinates, now, metric = WeightMetric.WIND, threshold = 8.0) { it.windSpeedMps }
        val gust = weightedMedian(samples, coordinates, now, metric = WeightMetric.WIND, threshold = 12.0) { it.windGustMps }
        val pressure = weightedMean(samples, coordinates, now, metric = WeightMetric.MOISTURE, threshold = 10.0) { it.pressureHpa }
        val cloud = weightedMean(samples, coordinates, now, metric = WeightMetric.CLOUD, threshold = 30.0) {
            it.cloudCoverPercent?.toDouble()
        }?.roundToInt()
        val visibility = weightedMean(samples, coordinates, now, metric = WeightMetric.CLOUD, threshold = 10.0) { it.visibilityKm }
        val condition = weightedCondition(samples, coordinates, now, metric = WeightMetric.CONDITION) ?: WeatherCondition.UNKNOWN
        val conditionText = weightedConditionText(samples, condition, coordinates, now, metric = WeightMetric.CONDITION)
        val observedAt = samples.maxOf { it.second.observedAt }

        return CurrentWeather(
            observedAt = observedAt,
            temperatureC = temp,
            feelsLikeC = feelsLike,
            dewPointC = dewPoint ?: estimateDewPointC(temp, humidity),
            humidityPercent = humidity,
            precipitationMm = precipitation,
            precipitationProbabilityPercent = precipitationProbability,
            windSpeedMps = wind,
            windGustMps = gust,
            windDirectionDegrees = weightedMean(samples, coordinates, now, metric = WeightMetric.WIND, threshold = 60.0) {
                it.windDirectionDegrees?.toDouble()
            }?.roundToInt(),
            pressureHpa = pressure,
            cloudCoverPercent = cloud,
            visibilityKm = visibility,
            isDaylight = samples.groupBy { it.second.isDaylight }.maxByOrNull { it.value.size }?.key,
            condition = condition,
            conditionText = conditionText,
        )
    }

    private fun aggregateHourly(
        providers: List<ProviderForecast>,
        coordinates: Coordinates,
        now: Instant,
    ): List<HourlyWeather> {
        val timeline = providers
            .flatMap { it.hourly }
            .map { it.time }
            .distinct()
            .sorted()
            .take(48)

        return timeline.mapNotNull { instant ->
            val samples = providers.mapNotNull { provider ->
                provider.hourly.firstOrNull { it.time == instant }?.let { provider to it }
            }
            if (samples.isEmpty()) {
                return@mapNotNull null
            }
            val hoursAhead = Duration.between(now, instant).toHours().coerceAtLeast(0)
            val condition = weightedCondition(samples, coordinates, now, metric = WeightMetric.CONDITION, horizonHours = hoursAhead.toDouble())
                ?: WeatherCondition.UNKNOWN
            HourlyWeather(
                time = instant,
                temperatureC = weightedMedian(samples, coordinates, now, metric = WeightMetric.TEMPERATURE, horizonHours = hoursAhead.toDouble(), threshold = 6.0) {
                    it.temperatureC
                },
                feelsLikeC = weightedMedian(samples, coordinates, now, metric = WeightMetric.TEMPERATURE, horizonHours = hoursAhead.toDouble(), threshold = 6.0) {
                    it.feelsLikeC
                },
                dewPointC = weightedMean(samples, coordinates, now, metric = WeightMetric.MOISTURE, horizonHours = hoursAhead.toDouble(), threshold = 8.0) {
                    it.dewPointC
                },
                precipitationMm = weightedMean(samples, coordinates, now, metric = WeightMetric.PRECIPITATION, horizonHours = hoursAhead.toDouble(), threshold = 4.0) {
                    it.precipitationMm
                },
                precipitationProbabilityPercent = weightedMean(
                    samples,
                    coordinates,
                    now,
                    metric = WeightMetric.PRECIPITATION,
                    horizonHours = hoursAhead.toDouble(),
                    threshold = 35.0,
                ) { it.precipitationProbabilityPercent?.toDouble() }?.roundToInt(),
                windSpeedMps = weightedMedian(samples, coordinates, now, metric = WeightMetric.WIND, horizonHours = hoursAhead.toDouble(), threshold = 8.0) {
                    it.windSpeedMps
                },
                windGustMps = weightedMedian(samples, coordinates, now, metric = WeightMetric.WIND, horizonHours = hoursAhead.toDouble(), threshold = 12.0) {
                    it.windGustMps
                },
                cloudCoverPercent = weightedMean(samples, coordinates, now, metric = WeightMetric.CLOUD, horizonHours = hoursAhead.toDouble(), threshold = 30.0) {
                    it.cloudCoverPercent?.toDouble()
                }?.roundToInt(),
                humidityPercent = weightedMean(samples, coordinates, now, metric = WeightMetric.MOISTURE, horizonHours = hoursAhead.toDouble(), threshold = 25.0) {
                    it.humidityPercent?.toDouble()
                }?.roundToInt(),
                visibilityKm = weightedMean(samples, coordinates, now, metric = WeightMetric.CLOUD, horizonHours = hoursAhead.toDouble(), threshold = 10.0) {
                    it.visibilityKm
                },
                condition = condition,
                conditionText = weightedConditionText(samples, condition, coordinates, now, metric = WeightMetric.CONDITION, horizonHours = hoursAhead.toDouble()),
            )
        }
    }

    private fun aggregateDaily(
        providers: List<ProviderForecast>,
        coordinates: Coordinates,
        now: Instant,
        zoneId: ZoneId,
    ): List<DailyWeather> {
        val today = LocalDate.now(zoneId)
        val dates = providers
            .flatMap { it.daily }
            .map { it.date }
            .filter { !it.isBefore(today) }
            .distinct()
            .sorted()
            .take(7)

        return dates.mapNotNull { date ->
            val samples = providers.mapNotNull { provider ->
                provider.daily.firstOrNull { it.date == date }?.let { provider to it }
            }
            if (samples.isEmpty()) {
                return@mapNotNull null
            }
            val daysAhead = Duration.between(now, date.atStartOfDay().atZone(zoneId).toInstant())
                .toHours()
                .coerceAtLeast(0)
                .toDouble()
            val condition = weightedCondition(samples, coordinates, now, metric = WeightMetric.CONDITION, horizonHours = daysAhead) ?: WeatherCondition.UNKNOWN
            DailyWeather(
                date = date,
                minTempC = weightedMedian(samples, coordinates, now, metric = WeightMetric.TEMPERATURE, horizonHours = daysAhead, threshold = 6.0) { it.minTempC },
                maxTempC = weightedMedian(samples, coordinates, now, metric = WeightMetric.TEMPERATURE, horizonHours = daysAhead, threshold = 6.0) { it.maxTempC },
                precipitationMm = weightedMean(samples, coordinates, now, metric = WeightMetric.PRECIPITATION, horizonHours = daysAhead, threshold = 6.0) { it.precipitationMm },
                precipitationProbabilityPercent = weightedMean(samples, coordinates, now, metric = WeightMetric.PRECIPITATION, horizonHours = daysAhead, threshold = 35.0) {
                    it.precipitationProbabilityPercent?.toDouble()
                }?.roundToInt(),
                maxWindSpeedMps = weightedMedian(samples, coordinates, now, metric = WeightMetric.WIND, horizonHours = daysAhead, threshold = 8.0) {
                    it.maxWindSpeedMps
                },
                sunriseIso = samples.firstNotNullOfOrNull { it.second.sunriseIso },
                sunsetIso = samples.firstNotNullOfOrNull { it.second.sunsetIso },
                uvIndexMax = weightedMean(samples, coordinates, now, metric = WeightMetric.CLOUD, horizonHours = daysAhead, threshold = 6.0) {
                    it.uvIndexMax
                },
                condition = condition,
                conditionText = weightedConditionText(samples, condition, coordinates, now, metric = WeightMetric.CONDITION, horizonHours = daysAhead),
            )
        }
    }

    private fun aggregateAlerts(
        providers: List<ProviderForecast>,
        now: Instant,
        coordinates: Coordinates,
    ): List<WeatherAlert> =
        providers
            .let { alertProviders ->
                val region = resolveRegionalProfile(coordinates)
                alertProviders
                    .flatMap { it.alerts }
                    .fold(mutableListOf<MutableList<WeatherAlert>>()) { clusters, alert ->
                        val existing = clusters.firstOrNull { cluster -> sameAlertCluster(cluster.first(), alert) }
                        if (existing != null) {
                            existing += alert
                        } else {
                            clusters += mutableListOf(alert)
                        }
                        clusters
                    }
                    .map { cluster -> mergeAlertCluster(cluster, now, region) }
                    .sortedByDescending { alertPriority(it, now, region) }
            }

    private fun buildCurrentDisagreements(providers: List<ProviderForecast>): List<MetricDisagreement> {
        val samples = providers.mapNotNull { provider -> provider.current?.let { provider to it } }
        return buildDisagreements(
            samples = samples,
            temperature = { it.temperatureC },
            precipitationProbability = { it.precipitationProbabilityPercent?.toDouble() },
            windSpeed = { it.windSpeedMps },
            cloudCover = { it.cloudCoverPercent?.toDouble() },
            condition = { it.condition },
        )
    }

    private fun buildCurrentMetricConfidence(
        providers: List<ProviderForecast>,
        now: Instant,
    ): List<MetricConfidence> {
        val samples = providers.mapNotNull { provider -> provider.current?.let { provider to it } }
        return buildMetricConfidence(
            samples = samples,
            now = now,
            temperature = { it.temperatureC },
            precipitationProbability = { it.precipitationProbabilityPercent?.toDouble() },
            windSpeed = { it.windSpeedMps },
            cloudCover = { it.cloudCoverPercent?.toDouble() },
            condition = { it.condition },
        )
    }

    private fun buildHourlyDiagnostics(
        providers: List<ProviderForecast>,
        now: Instant,
    ): List<SlotDiagnostics> {
        val timeline = providers
            .flatMap { it.hourly }
            .map { it.time }
            .distinct()
            .sorted()
            .take(24)

        return timeline.mapNotNull { instant ->
            val samples = providers.mapNotNull { provider ->
                provider.hourly.firstOrNull { it.time == instant }?.let { provider to it }
            }
            val disagreements = buildDisagreements(
                samples = samples,
                temperature = { it.temperatureC },
                precipitationProbability = { it.precipitationProbabilityPercent?.toDouble() },
                windSpeed = { it.windSpeedMps },
                cloudCover = { it.cloudCoverPercent?.toDouble() },
                condition = { it.condition },
            )
            val metricConfidence = buildMetricConfidence(
                samples = samples,
                now = now,
                temperature = { it.temperatureC },
                precipitationProbability = { it.precipitationProbabilityPercent?.toDouble() },
                windSpeed = { it.windSpeedMps },
                cloudCover = { it.cloudCoverPercent?.toDouble() },
                condition = { it.condition },
            )
            if (disagreements.isEmpty()) {
                null
            } else {
                SlotDiagnostics(
                    timestampEpochMillis = instant.toEpochMilli(),
                    metricConfidence = metricConfidence,
                    disagreements = disagreements,
                    summary = disagreementSummary(disagreements),
                )
            }
        }
    }

    private fun buildDailyDiagnostics(
        providers: List<ProviderForecast>,
        now: Instant,
        zoneId: ZoneId,
    ): List<SlotDiagnostics> {
        val today = LocalDate.now(zoneId)
        val dates = providers
            .flatMap { it.daily }
            .map { it.date }
            .filter { !it.isBefore(today) }
            .distinct()
            .sorted()
            .take(7)

        return dates.mapNotNull { date ->
            val samples = providers.mapNotNull { provider ->
                provider.daily.firstOrNull { it.date == date }?.let { provider to it }
            }
            val disagreements = buildDisagreements(
                samples = samples,
                temperature = { it.maxTempC },
                precipitationProbability = { it.precipitationProbabilityPercent?.toDouble() },
                windSpeed = { it.maxWindSpeedMps },
                cloudCover = { null },
                condition = { it.condition },
            )
            val metricConfidence = buildMetricConfidence(
                samples = samples,
                now = now,
                temperature = { it.maxTempC },
                precipitationProbability = { it.precipitationProbabilityPercent?.toDouble() },
                windSpeed = { it.maxWindSpeedMps },
                cloudCover = { null },
                condition = { it.condition },
            )
            if (disagreements.isEmpty()) {
                null
            } else {
                SlotDiagnostics(
                    dateIso = date.toString(),
                    metricConfidence = metricConfidence,
                    disagreements = disagreements,
                    summary = disagreementSummary(disagreements),
                )
            }
        }
    }

    private fun buildAnomalies(
        hourly: List<HourlyWeather>,
        daily: List<DailyWeather>,
        hourlyDiagnostics: List<SlotDiagnostics>,
        zoneId: ZoneId,
    ): List<ForecastAnomaly> {
        val anomalies = mutableListOf<ForecastAnomaly>()

        hourlyDiagnostics
            .maxByOrNull(::slotConflictScore)
            ?.takeIf { slotConflictScore(it) >= 0.55 }
            ?.let { slot ->
                anomalies += ForecastAnomaly(
                    title = "High forecast uncertainty",
                    detail = "Providers disagree strongly around ${formatAnomalyTime(slot.timestampEpochMillis, zoneId)}.",
                    severity = "Watch",
                )
            }

        hourly.zipWithNext()
            .maxByOrNull { (left, right) ->
                abs((right.precipitationProbabilityPercent ?: 0) - (left.precipitationProbabilityPercent ?: 0))
            }
            ?.takeIf { (left, right) ->
                abs((right.precipitationProbabilityPercent ?: 0) - (left.precipitationProbabilityPercent ?: 0)) >= 35
            }
            ?.let { (_, right) ->
                anomalies += ForecastAnomaly(
                    title = "Rain signal shifts quickly",
                    detail = "Precipitation odds change sharply near ${formatAnomalyInstant(right.time, zoneId)}.",
                    severity = "Watch",
                )
            }

        hourly.windowed(size = 6, step = 1, partialWindows = false)
            .maxByOrNull { window ->
                val values = window.mapNotNull { it.temperatureC }
                if (values.isEmpty()) 0.0 else (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)
            }
            ?.let { window ->
                val values = window.mapNotNull { it.temperatureC }
                val spread = if (values.isEmpty()) 0.0 else (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)
                if (spread >= 8.0) {
                    anomalies += ForecastAnomaly(
                        title = "Rapid temperature swing",
                        detail = "Temperatures shift by about ${spread.roundToInt()}° within roughly six hours.",
                        severity = "Info",
                    )
                }
            }

        daily.firstOrNull()
            ?.takeIf { day ->
                val min = day.minTempC
                val max = day.maxTempC
                min != null && max != null && (max - min) >= 12.0
            }
            ?.let { day ->
                anomalies += ForecastAnomaly(
                    title = "Large day-night spread",
                    detail = "Today's forecast spans ${formatTemperatureSpread(day.maxTempC, day.minTempC)}.",
                    severity = "Info",
                )
            }

        return anomalies.distinctBy { it.title }.take(3)
    }

    private fun slotConflictScore(slot: SlotDiagnostics): Double =
        slot.disagreements.maxOfOrNull { it.normalizedSpread } ?: 0.0

    private fun formatAnomalyTime(
        timestampEpochMillis: Long?,
        zoneId: ZoneId,
    ): String =
        timestampEpochMillis?.let {
            Instant.ofEpochMilli(it)
                .atZone(zoneId)
                .format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm"))
        } ?: "this period"

    private fun formatAnomalyInstant(
        instant: Instant,
        zoneId: ZoneId,
    ): String =
        instant.atZone(zoneId)
            .format(java.time.format.DateTimeFormatter.ofPattern("EEE HH:mm"))

    private fun formatTemperatureSpread(
        maxTempC: Double?,
        minTempC: Double?,
    ): String {
        val max = maxTempC ?: return "--"
        val min = minTempC ?: return "--"
        return "${max.roundToInt()}° to ${min.roundToInt()}°"
    }

    private fun resolveZoneId(timeZoneId: String?): ZoneId = runCatching {
        ZoneId.of(timeZoneId.orEmpty().ifBlank { ZoneId.systemDefault().id })
    }.getOrElse {
        ZoneId.systemDefault()
    }

    private fun <T> buildDisagreements(
        samples: List<Pair<ProviderForecast, T>>,
        temperature: (T) -> Double?,
        precipitationProbability: (T) -> Double?,
        windSpeed: (T) -> Double?,
        cloudCover: (T) -> Double?,
        condition: (T) -> WeatherCondition,
    ): List<MetricDisagreement> = listOfNotNull(
        numericDisagreement(samples, ForecastMetric.TEMPERATURE, scale = 8.0, selector = temperature),
        numericDisagreement(
            samples,
            ForecastMetric.PRECIPITATION_PROBABILITY,
            scale = 50.0,
            selector = precipitationProbability,
        ),
        numericDisagreement(samples, ForecastMetric.WIND_SPEED, scale = 12.0, selector = windSpeed),
        numericDisagreement(samples, ForecastMetric.CLOUD_COVER, scale = 60.0, selector = cloudCover),
        conditionDisagreement(samples, condition),
    ).sortedByDescending { it.normalizedSpread }

    private fun <T> buildMetricConfidence(
        samples: List<Pair<ProviderForecast, T>>,
        now: Instant,
        temperature: (T) -> Double?,
        precipitationProbability: (T) -> Double?,
        windSpeed: (T) -> Double?,
        cloudCover: (T) -> Double?,
        condition: (T) -> WeatherCondition,
    ): List<MetricConfidence> = listOfNotNull(
        numericConfidence(samples, now, ForecastMetric.TEMPERATURE, scale = 8.0, selector = temperature),
        numericConfidence(
            samples,
            now,
            ForecastMetric.PRECIPITATION_PROBABILITY,
            scale = 50.0,
            selector = precipitationProbability,
        ),
        numericConfidence(samples, now, ForecastMetric.WIND_SPEED, scale = 12.0, selector = windSpeed),
        numericConfidence(samples, now, ForecastMetric.CLOUD_COVER, scale = 60.0, selector = cloudCover),
        conditionConfidence(samples, now, condition),
    )

    private fun <T> numericDisagreement(
        samples: List<Pair<ProviderForecast, T>>,
        metric: ForecastMetric,
        scale: Double,
        selector: (T) -> Double?,
    ): MetricDisagreement? {
        val values = samples.mapNotNull { (provider, point) ->
            selector(point)?.let { value -> provider to value }
        }
        if (values.size < 2) {
            return null
        }
        val minEntry = values.minByOrNull { it.second } ?: return null
        val maxEntry = values.maxByOrNull { it.second } ?: return null
        val spread = maxEntry.second - minEntry.second
        val center = values.map { it.second }.average()
        val divergentProvider = values.maxByOrNull { (_, value) -> abs(value - center) }?.first?.providerId
        return MetricDisagreement(
            metric = metric,
            minValue = minEntry.second,
            maxValue = maxEntry.second,
            spread = spread,
            normalizedSpread = (spread / scale).coerceIn(0.0, 1.0),
            mostDivergentProviderId = divergentProvider,
        )
    }

    private fun <T> conditionDisagreement(
        samples: List<Pair<ProviderForecast, T>>,
        selector: (T) -> WeatherCondition,
    ): MetricDisagreement? {
        if (samples.size < 2) {
            return null
        }
        val tallies = samples.groupingBy { selector(it.second) }.eachCount()
        val dominantCondition = tallies.maxByOrNull { it.value }?.key ?: WeatherCondition.UNKNOWN
        val dominantShare = tallies[dominantCondition]?.toDouble()?.div(samples.size) ?: 0.0
        val disagreement = (1.0 - dominantShare).coerceIn(0.0, 1.0)
        val divergentProvider = samples.firstOrNull { selector(it.second) != dominantCondition }?.first?.providerId
        return MetricDisagreement(
            metric = ForecastMetric.CONDITION,
            spread = disagreement,
            normalizedSpread = disagreement,
            mostDivergentProviderId = divergentProvider,
        )
    }

    private fun <T> numericConfidence(
        samples: List<Pair<ProviderForecast, T>>,
        now: Instant,
        metric: ForecastMetric,
        scale: Double,
        selector: (T) -> Double?,
    ): MetricConfidence? {
        val values = samples.mapNotNull { (provider, point) ->
            selector(point)?.let { value -> provider to value }
        }
        if (values.isEmpty()) {
            return null
        }
        val spread = if (values.size < 2) {
            0.0
        } else {
            (values.maxOf { it.second } - values.minOf { it.second }).coerceAtLeast(0.0)
        }
        val agreementScore = (1.0 - (spread / scale)).coerceIn(0.0, 1.0)
        val providerCountScore = (values.size / 3.0).coerceIn(0.0, 1.0)
        val freshnessScore = values.map { freshnessFactor(it.first, now) }.average()
        val availabilityScore = values.map { it.first.confidence.coerceAtLeast(0.65) }.average().coerceIn(0.0, 1.0)
        val score = (
            agreementScore * 0.5 +
                freshnessScore * 0.2 +
                providerCountScore * 0.15 +
                availabilityScore * 0.15
            ).coerceIn(0.0, 1.0)
        return MetricConfidence(
            metric = metric,
            score = score,
            spread = spread,
            providerCount = values.size,
        )
    }

    private fun <T> conditionConfidence(
        samples: List<Pair<ProviderForecast, T>>,
        now: Instant,
        selector: (T) -> WeatherCondition,
    ): MetricConfidence? {
        if (samples.isEmpty()) {
            return null
        }
        val tallies = samples.groupingBy { selector(it.second) }.eachCount()
        val dominantShare = tallies.maxOf { it.value }.toDouble() / samples.size
        val providerCountScore = (samples.size / 3.0).coerceIn(0.0, 1.0)
        val freshnessScore = samples.map { freshnessFactor(it.first, now) }.average()
        val availabilityScore = samples.map { it.first.confidence.coerceAtLeast(0.65) }.average().coerceIn(0.0, 1.0)
        val score = (
            dominantShare * 0.5 +
                freshnessScore * 0.2 +
                providerCountScore * 0.15 +
                availabilityScore * 0.15
            ).coerceIn(0.0, 1.0)
        return MetricConfidence(
            metric = ForecastMetric.CONDITION,
            score = score,
            spread = 1.0 - dominantShare,
            providerCount = samples.size,
        )
    }

    private fun disagreementSummary(disagreements: List<MetricDisagreement>): String =
        disagreements
            .sortedByDescending { it.normalizedSpread }
            .take(2)
            .joinToString(" • ") { disagreement ->
                when (disagreement.metric) {
                    ForecastMetric.TEMPERATURE ->
                        "Temp spread ${disagreement.spread.roundToInt()}°"
                    ForecastMetric.PRECIPITATION_PROBABILITY ->
                        "Rain chance spread ${disagreement.spread.roundToInt()}%"
                    ForecastMetric.WIND_SPEED ->
                        "Wind spread ${disagreement.spread.roundToInt()} m/s"
                    ForecastMetric.CLOUD_COVER ->
                        "Cloud spread ${disagreement.spread.roundToInt()}%"
                    ForecastMetric.CONDITION ->
                        "Condition split ${(disagreement.normalizedSpread * 100).roundToInt()}%"
                }
            }

    private fun <T> weightedCondition(
        samples: List<Pair<ProviderForecast, T>>,
        coordinates: Coordinates,
        now: Instant,
        metric: WeightMetric,
        horizonHours: Double = 0.0,
        selector: (T) -> WeatherCondition = {
            when (it) {
                is CurrentWeather -> it.condition
                is HourlyWeather -> it.condition
                is DailyWeather -> it.condition
                else -> WeatherCondition.UNKNOWN
            }
        },
    ): WeatherCondition? {
        val weights = mutableMapOf<WeatherCondition, Double>()
        samples.forEach { (provider, point) ->
            val condition = selector(point)
            val weight = providerWeight(provider, metric, coordinates, horizonHours, now)
            weights[condition] = (weights[condition] ?: 0.0) + weight
        }
        return weights.maxByOrNull { it.value }?.key
    }

    private fun <T> weightedConditionText(
        samples: List<Pair<ProviderForecast, T>>,
        fallbackCondition: WeatherCondition,
        coordinates: Coordinates,
        now: Instant,
        metric: WeightMetric,
        horizonHours: Double = 0.0,
    ): String {
        val weights = mutableMapOf<String, Double>()
        samples.forEach { (provider, point) ->
            val text = when (point) {
                is CurrentWeather -> point.conditionText
                is HourlyWeather -> point.conditionText
                is DailyWeather -> point.conditionText
                else -> fallbackCondition.name.replace('_', ' ').lowercase()
            }
            weights[text] = (weights[text] ?: 0.0) + providerWeight(provider, metric, coordinates, horizonHours, now)
        }
        return weights.maxByOrNull { it.value }?.key ?: prettyCondition(fallbackCondition)
    }

    private fun <T> weightedMean(
        samples: List<Pair<ProviderForecast, T>>,
        coordinates: Coordinates,
        now: Instant,
        metric: WeightMetric,
        horizonHours: Double = 0.0,
        threshold: Double,
        selector: (T) -> Double?,
    ): Double? {
        val values = samples.mapNotNull { (provider, point) ->
            selector(point)?.let { value -> WeightedValue(value, providerWeight(provider, metric, coordinates, horizonHours, now)) }
        }
        if (values.isEmpty()) {
            return null
        }
        val median = values.map { it.value }.sorted().let { sorted ->
            sorted[sorted.size / 2]
        }
        val adjusted = values.map {
            it.copy(weight = if (abs(it.value - median) > threshold) it.weight * 0.45 else it.weight)
        }
        val weightSum = adjusted.sumOf { it.weight }.takeIf { it > 0.0 } ?: return null
        return adjusted.sumOf { it.value * it.weight } / weightSum
    }

    private fun <T> weightedMedian(
        samples: List<Pair<ProviderForecast, T>>,
        coordinates: Coordinates,
        now: Instant,
        metric: WeightMetric,
        horizonHours: Double = 0.0,
        threshold: Double,
        selector: (T) -> Double?,
    ): Double? {
        val values = samples.mapNotNull { (provider, point) ->
            selector(point)?.let { value -> WeightedValue(value, providerWeight(provider, metric, coordinates, horizonHours, now)) }
        }
        if (values.isEmpty()) {
            return null
        }
        val sortedByValue = values.sortedBy { it.value }
        val plainMedian = sortedByValue[sortedByValue.size / 2].value
        val adjusted = sortedByValue.map {
            it.copy(weight = if (abs(it.value - plainMedian) > threshold) it.weight * 0.45 else it.weight)
        }
        val total = adjusted.sumOf { it.weight }
        var cumulative = 0.0
        adjusted.forEach { entry ->
            cumulative += entry.weight
            if (cumulative >= total / 2.0) {
                return entry.value
            }
        }
        return adjusted.last().value
    }

    private fun providerWeight(
        provider: ProviderForecast,
        metric: WeightMetric,
        coordinates: Coordinates,
        horizonHours: Double,
        now: Instant,
    ): Double {
        val region = resolveRegionalProfile(coordinates)
        val horizon = resolveHorizon(horizonHours)
        val base = when (provider.providerId) {
            ProviderId.OPEN_METEO -> 1.02
            ProviderId.MET_NORWAY -> 1.0
            ProviderId.WEATHER_GOV -> 1.12
            ProviderId.WEATHER_API -> 0.94
        }
        val freshness = freshnessFactor(provider, now) * freshnessAdjustment(metric, freshnessFactor(provider, now))
        val completeness = completenessFactor(provider, horizon)
        val confidence = provider.confidence.coerceAtLeast(0.65)
        val horizonFactor = horizonCalibration(provider.providerId, horizon)
        val regional = regionalBonus(provider.providerId, region, metric)
        val metricFactor = metricAffinity(provider.providerId, metric, horizon)
        return base * freshness * completeness * confidence * horizonFactor * regional * metricFactor
    }

    private fun resolveRegionalProfile(coordinates: Coordinates): RegionalProfile = when {
        isLikelyUs(coordinates) -> RegionalProfile.US
        isNorthernEurope(coordinates) -> RegionalProfile.NORTHERN_EUROPE
        else -> RegionalProfile.GLOBAL
    }

    private fun resolveHorizon(horizonHours: Double): WeightHorizon = when {
        horizonHours <= 0.0 -> WeightHorizon.CURRENT
        horizonHours <= 12.0 -> WeightHorizon.HOURLY_NEAR
        horizonHours <= 48.0 -> WeightHorizon.HOURLY_MID
        horizonHours <= 96.0 -> WeightHorizon.DAILY_NEAR
        else -> WeightHorizon.DAILY_FAR
    }

    private fun freshnessAdjustment(
        metric: WeightMetric,
        freshness: Double,
    ): Double = when (metric) {
        WeightMetric.PRECIPITATION, WeightMetric.WIND -> when {
            freshness >= 0.95 -> 1.03
            freshness >= 0.88 -> 0.96
            else -> 0.84
        }

        WeightMetric.CONDITION -> when {
            freshness >= 0.95 -> 1.01
            freshness >= 0.88 -> 0.95
            else -> 0.88
        }

        WeightMetric.TEMPERATURE, WeightMetric.MOISTURE, WeightMetric.CLOUD -> when {
            freshness >= 0.95 -> 1.0
            freshness >= 0.88 -> 0.98
            else -> 0.92
        }
    }

    private fun completenessFactor(
        provider: ProviderForecast,
        horizon: WeightHorizon,
    ): Double = when (horizon) {
        WeightHorizon.CURRENT ->
            if (provider.current != null) 1.0 else 0.72

        WeightHorizon.HOURLY_NEAR, WeightHorizon.HOURLY_MID ->
            if (provider.hourly.size >= 12) 1.0 else 0.86

        WeightHorizon.DAILY_NEAR, WeightHorizon.DAILY_FAR ->
            if (provider.daily.size >= 3) 1.0 else 0.84
    }

    private fun horizonCalibration(
        providerId: ProviderId,
        horizon: WeightHorizon,
    ): Double = when (providerId) {
        ProviderId.OPEN_METEO -> when (horizon) {
            WeightHorizon.CURRENT -> 1.0
            WeightHorizon.HOURLY_NEAR -> 1.0
            WeightHorizon.HOURLY_MID -> 0.98
            WeightHorizon.DAILY_NEAR -> 0.96
            WeightHorizon.DAILY_FAR -> 0.94
        }

        ProviderId.MET_NORWAY -> when (horizon) {
            WeightHorizon.CURRENT -> 1.0
            WeightHorizon.HOURLY_NEAR -> 1.0
            WeightHorizon.HOURLY_MID -> 0.97
            WeightHorizon.DAILY_NEAR -> 0.95
            WeightHorizon.DAILY_FAR -> 0.91
        }

        ProviderId.WEATHER_GOV -> when (horizon) {
            WeightHorizon.CURRENT -> 1.08
            WeightHorizon.HOURLY_NEAR -> 1.05
            WeightHorizon.HOURLY_MID -> 0.96
            WeightHorizon.DAILY_NEAR -> 0.88
            WeightHorizon.DAILY_FAR -> 0.78
        }

        ProviderId.WEATHER_API -> when (horizon) {
            WeightHorizon.CURRENT -> 0.96
            WeightHorizon.HOURLY_NEAR -> 0.97
            WeightHorizon.HOURLY_MID -> 0.98
            WeightHorizon.DAILY_NEAR -> 0.97
            WeightHorizon.DAILY_FAR -> 0.96
        }
    }

    private fun regionalBonus(
        providerId: ProviderId,
        region: RegionalProfile,
        metric: WeightMetric,
    ): Double = when (region) {
        RegionalProfile.US -> when (providerId) {
            ProviderId.WEATHER_GOV -> when (metric) {
                WeightMetric.PRECIPITATION, WeightMetric.WIND, WeightMetric.CONDITION -> 1.22
                WeightMetric.TEMPERATURE -> 1.16
                WeightMetric.MOISTURE, WeightMetric.CLOUD -> 1.08
            }

            else -> 1.0
        }

        RegionalProfile.NORTHERN_EUROPE -> when (providerId) {
            ProviderId.MET_NORWAY -> when (metric) {
                WeightMetric.TEMPERATURE, WeightMetric.WIND, WeightMetric.CONDITION -> 1.12
                WeightMetric.PRECIPITATION -> 1.08
                WeightMetric.MOISTURE, WeightMetric.CLOUD -> 1.05
            }

            else -> 1.0
        }

        RegionalProfile.GLOBAL -> 1.0
    }

    private fun metricAffinity(
        providerId: ProviderId,
        metric: WeightMetric,
        horizon: WeightHorizon,
    ): Double = when (providerId) {
        ProviderId.OPEN_METEO -> when (metric) {
            WeightMetric.TEMPERATURE -> 1.03
            WeightMetric.PRECIPITATION -> 1.01
            WeightMetric.WIND -> 1.0
            WeightMetric.MOISTURE -> 0.99
            WeightMetric.CLOUD -> 1.01
            WeightMetric.CONDITION -> 0.98
        }

        ProviderId.MET_NORWAY -> when (metric) {
            WeightMetric.TEMPERATURE -> 1.0
            WeightMetric.PRECIPITATION -> 1.0
            WeightMetric.WIND -> 1.03
            WeightMetric.MOISTURE -> 1.0
            WeightMetric.CLOUD -> 0.99
            WeightMetric.CONDITION -> 1.01
        }

        ProviderId.WEATHER_GOV -> when (metric) {
            WeightMetric.TEMPERATURE -> 1.01
            WeightMetric.PRECIPITATION -> 1.05
            WeightMetric.WIND -> 1.05
            WeightMetric.MOISTURE -> 0.98
            WeightMetric.CLOUD -> 0.97
            WeightMetric.CONDITION -> 1.06
        }

        ProviderId.WEATHER_API -> when (metric) {
            WeightMetric.TEMPERATURE -> if (horizon == WeightHorizon.DAILY_FAR) 1.02 else 0.98
            WeightMetric.PRECIPITATION -> if (horizon == WeightHorizon.DAILY_FAR) 1.01 else 0.97
            WeightMetric.WIND -> 0.97
            WeightMetric.MOISTURE -> 0.98
            WeightMetric.CLOUD -> 0.98
            WeightMetric.CONDITION -> if (horizon == WeightHorizon.DAILY_FAR) 1.01 else 0.97
        }
    }

    private fun freshnessFactor(provider: ProviderForecast, now: Instant): Double {
        val ageMinutes = Duration.between(provider.fetchedAt, now).toMinutes().coerceAtLeast(0)
        return when {
            ageMinutes <= 20 -> 1.0
            ageMinutes <= 60 -> 0.95
            ageMinutes <= 180 -> 0.88
            ageMinutes <= 360 -> 0.78
            else -> 0.68
        }
    }

    private fun computeAgreementScore(providers: List<ProviderForecast>): Double {
        val temps = providers.mapNotNull { it.current?.temperatureC }
        val precip = providers.mapNotNull { it.current?.precipitationProbabilityPercent?.toDouble() }
        val wind = providers.mapNotNull { it.current?.windSpeedMps }
        val conditionAgreement = providers.mapNotNull { it.current?.condition }.let { conditions ->
            if (conditions.isEmpty()) 0.5 else conditions.groupingBy { it }.eachCount().maxOf { count -> count.value }.toDouble() / conditions.size
        }

        val tempAgreement = scoreFromSpread(temps, 8.0)
        val precipAgreement = scoreFromSpread(precip, 50.0)
        val windAgreement = scoreFromSpread(wind, 12.0)
        return listOf(tempAgreement, precipAgreement, windAgreement, conditionAgreement).average()
    }

    private fun scoreFromSpread(values: List<Double>, spreadAtZero: Double): Double {
        if (values.size < 2) {
            return 0.85
        }
        val spread = (values.maxOrNull() ?: 0.0) - (values.minOrNull() ?: 0.0)
        return (1.0 - (spread / spreadAtZero)).coerceIn(0.0, 1.0)
    }

    private fun estimateDewPointC(
        temperatureC: Double?,
        humidityPercent: Int?,
    ): Double? {
        val temp = temperatureC ?: return null
        val humidity = humidityPercent?.coerceIn(1, 100) ?: return null
        val a = 17.27
        val b = 237.7
        val gamma = (a * temp / (b + temp)) + kotlin.math.ln(humidity / 100.0)
        return (b * gamma) / (a - gamma)
    }

    private fun severityRank(severity: String): Int = when (severity.lowercase()) {
        "extreme" -> 4
        "severe" -> 3
        "moderate" -> 2
        "minor" -> 1
        else -> 0
    }

    private fun sameAlertCluster(
        left: WeatherAlert,
        right: WeatherAlert,
    ): Boolean {
        val leftKey = normalizedAlertKey(left.title)
        val rightKey = normalizedAlertKey(right.title)
        val similarTitle = leftKey == rightKey || leftKey.contains(rightKey) || rightKey.contains(leftKey)
        val overlappingTime = alertsOverlap(left, right)
        return similarTitle && overlappingTime
    }

    private fun normalizedAlertKey(title: String): String = title
        .lowercase()
        .replace(Regex("[^a-z0-9 ]"), " ")
        .split(" ")
        .filter { token -> token.isNotBlank() && token !in setOf("the", "a", "an", "for", "and", "of", "in") }
        .take(6)
        .joinToString(" ")

    private fun alertsOverlap(
        left: WeatherAlert,
        right: WeatherAlert,
    ): Boolean {
        val leftStart = left.startsAt ?: Instant.EPOCH
        val rightStart = right.startsAt ?: Instant.EPOCH
        val leftEnd = left.endsAt ?: leftStart.plus(Duration.ofHours(12))
        val rightEnd = right.endsAt ?: rightStart.plus(Duration.ofHours(12))
        return leftStart <= rightEnd && rightStart <= leftEnd
    }

    private fun mergeAlertCluster(
        cluster: List<WeatherAlert>,
        now: Instant,
        region: RegionalProfile,
    ): WeatherAlert {
        val lead = cluster.maxByOrNull { alertPriority(it, now, region) } ?: cluster.first()
        val sources = cluster.map { it.sourceName }.distinct().sorted()
        val mergedDescription = buildString {
            if (sources.size > 1) {
                append("Merged from ")
                append(sources.joinToString(", "))
                append(". ")
            }
            append(lead.description)
        }
        return lead.copy(
            sourceName = sources.joinToString(" + "),
            description = mergedDescription,
        )
    }

    private fun alertPriority(
        alert: WeatherAlert,
        now: Instant,
        region: RegionalProfile,
    ): Int {
        val startsSoon = if (alert.startsAt != null && alert.startsAt.isBefore(now.plus(Duration.ofHours(6)))) 2 else 0
        val actionWords = listOf("warning", "evacuation", "flood", "storm", "tornado", "ice", "wind")
        val actionable = if (actionWords.any { it in alert.title.lowercase() }) 2 else 0
        return (severityRank(alert.severity) * 10) + startsSoon + actionable + officialAlertTrustBonus(alert, region)
    }

    private fun officialAlertTrustBonus(
        alert: WeatherAlert,
        region: RegionalProfile,
    ): Int {
        val source = alert.sourceName.lowercase()
        return when (region) {
            RegionalProfile.US ->
                if ("weather.gov" in source || "national weather service" in source) 12 else 0

            RegionalProfile.NORTHERN_EUROPE ->
                if ("met norway" in source || "yr" in source) 8 else 0

            RegionalProfile.GLOBAL -> 0
        }
    }

    private fun prettyCondition(condition: WeatherCondition): String =
        condition.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }

    private fun isLikelyUs(coordinates: Coordinates): Boolean =
        coordinates.latitude in 18.0..72.0 && coordinates.longitude in -170.0..-60.0

    private fun isNorthernEurope(coordinates: Coordinates): Boolean =
        coordinates.latitude in 48.0..72.5 && coordinates.longitude in -12.0..42.0

    private enum class WeightMetric {
        TEMPERATURE,
        PRECIPITATION,
        WIND,
        MOISTURE,
        CLOUD,
        CONDITION,
    }

    private enum class WeightHorizon {
        CURRENT,
        HOURLY_NEAR,
        HOURLY_MID,
        DAILY_NEAR,
        DAILY_FAR,
    }

    private data class WeightedValue(
        val value: Double,
        val weight: Double,
    )
}
