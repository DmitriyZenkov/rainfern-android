package dev.rdime.rainfern.data.network.provider

import dev.rdime.rainfern.data.model.AppSettings
import dev.rdime.rainfern.data.model.Coordinates
import dev.rdime.rainfern.data.model.CurrentWeather
import dev.rdime.rainfern.data.model.DailyWeather
import dev.rdime.rainfern.data.model.HourlyWeather
import dev.rdime.rainfern.data.model.ProviderForecast
import dev.rdime.rainfern.data.model.ProviderId
import dev.rdime.rainfern.data.model.WeatherCondition
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProviderInfo(
    val id: ProviderId,
    val displayName: String,
    val coverage: String,
    val requiresKey: Boolean,
    val sourceUrl: String,
    val notes: String,
)

val defaultProviderCatalog = listOf(
    ProviderInfo(
        id = ProviderId.OPEN_METEO,
        displayName = "Open-Meteo",
        coverage = "Global",
        requiresKey = false,
        sourceUrl = "https://open-meteo.com/",
        notes = "Free for non-commercial use. Primary global base forecast.",
    ),
    ProviderInfo(
        id = ProviderId.MET_NORWAY,
        displayName = "MET Norway",
        coverage = "Global",
        requiresKey = false,
        sourceUrl = "https://api.met.no/",
        notes = "Global forecast data with stronger weight in northern Europe.",
    ),
    ProviderInfo(
        id = ProviderId.WEATHER_GOV,
        displayName = "weather.gov / NWS",
        coverage = "United States",
        requiresKey = false,
        sourceUrl = "https://api.weather.gov/",
        notes = "Auto-used for U.S. locations. Strongest alert authority in the U.S.",
    ),
    ProviderInfo(
        id = ProviderId.WEATHER_API,
        displayName = "WeatherAPI.com",
        coverage = "Global",
        requiresKey = true,
        sourceUrl = "https://www.weatherapi.com/",
        notes = "Optional free-key provider. Used as a third opinion when you add your key.",
    ),
)

interface WeatherProvider {
    val providerInfo: ProviderInfo

    suspend fun fetch(
        coordinates: Coordinates,
        settings: AppSettings,
        targetTimeZoneId: String? = null,
    ): ProviderForecast?
}

class WeatherHttpClient {
    private val client = OkHttpClient.Builder().build()
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend inline fun <reified T> getDecoded(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): T? {
        val payload = getString(url, headers) ?: return null
        return json.decodeFromString<T>(payload)
    }

    suspend fun getString(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.isSuccessful -> response.body?.string()
                response.code == 404 -> null
                else -> throw IOException("HTTP ${response.code} for $url")
            }
        }
    }
}

class OpenMeteoWeatherProvider(
    private val httpClient: WeatherHttpClient,
) : WeatherProvider {
    override val providerInfo: ProviderInfo = defaultProviderCatalog.first { it.id == ProviderId.OPEN_METEO }

    override suspend fun fetch(
        coordinates: Coordinates,
        settings: AppSettings,
        targetTimeZoneId: String?,
    ): ProviderForecast? {
        if (!settings.openMeteoEnabled) {
            return null
        }
        val url = "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", coordinates.latitude.toString())
            .addQueryParameter("longitude", coordinates.longitude.toString())
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("forecast_days", "10")
            .addQueryParameter(
                "current",
                listOf(
                    "temperature_2m",
                    "apparent_temperature",
                    "dew_point_2m",
                    "relative_humidity_2m",
                    "precipitation",
                    "weather_code",
                    "cloud_cover",
                    "pressure_msl",
                    "visibility",
                    "wind_speed_10m",
                    "wind_direction_10m",
                    "wind_gusts_10m",
                    "is_day",
                ).joinToString(","),
            )
            .addQueryParameter(
                "hourly",
                listOf(
                    "temperature_2m",
                    "apparent_temperature",
                    "dew_point_2m",
                    "relative_humidity_2m",
                    "precipitation_probability",
                    "precipitation",
                    "weather_code",
                    "cloud_cover",
                    "visibility",
                    "wind_speed_10m",
                    "wind_gusts_10m",
                ).joinToString(","),
            )
            .addQueryParameter(
                "daily",
                listOf(
                    "weather_code",
                    "temperature_2m_max",
                    "temperature_2m_min",
                    "precipitation_sum",
                    "precipitation_probability_max",
                    "wind_speed_10m_max",
                    "sunrise",
                    "sunset",
                    "uv_index_max",
                ).joinToString(","),
            )
            .build()
            .toString()

        val response = httpClient.getDecoded<OpenMeteoResponse>(url) ?: return null
        val hourlySource = response.hourly ?: OpenMeteoHourly()
        val dailySource = response.daily ?: OpenMeteoDaily()
        val current = response.current?.let {
            CurrentWeather(
                observedAt = Instant.parse(it.time),
                temperatureC = it.temperature,
                feelsLikeC = it.feelsLike,
                dewPointC = it.dewPoint,
                humidityPercent = it.humidity?.roundToInt(),
                precipitationMm = it.precipitation,
                windSpeedMps = it.windSpeed,
                windGustMps = it.windGust,
                windDirectionDegrees = it.windDirection?.roundToInt(),
                pressureHpa = it.pressure,
                cloudCoverPercent = it.cloudCover?.roundToInt(),
                visibilityKm = it.visibility?.div(1000.0),
                isDaylight = it.isDay?.let { day -> day == 1 },
                condition = fromWmoCode(it.weatherCode),
                conditionText = describeWmoCode(it.weatherCode),
            )
        }
        val hourly = hourlySource.time.indices.mapNotNull { index ->
            val timestamp = hourlySource.time.getOrNull(index) ?: return@mapNotNull null
            HourlyWeather(
                time = Instant.parse(timestamp),
                temperatureC = hourlySource.temperature.getOrNull(index),
                feelsLikeC = hourlySource.feelsLike.getOrNull(index),
                dewPointC = hourlySource.dewPoint.getOrNull(index),
                precipitationMm = hourlySource.precipitation.getOrNull(index),
                precipitationProbabilityPercent = hourlySource.precipitationProbability.getOrNull(index)?.roundToInt(),
                windSpeedMps = hourlySource.windSpeed.getOrNull(index),
                windGustMps = hourlySource.windGust.getOrNull(index),
                cloudCoverPercent = hourlySource.cloudCover.getOrNull(index)?.roundToInt(),
                humidityPercent = hourlySource.humidity.getOrNull(index)?.roundToInt(),
                visibilityKm = hourlySource.visibility.getOrNull(index)?.div(1000.0),
                condition = fromWmoCode(hourlySource.weatherCode.getOrNull(index)),
                conditionText = describeWmoCode(hourlySource.weatherCode.getOrNull(index)),
            )
        }.take(48)

        val daily = dailySource.time.indices.mapNotNull { index ->
            val date = dailySource.time.getOrNull(index) ?: return@mapNotNull null
            DailyWeather(
                date = LocalDate.parse(date),
                minTempC = dailySource.temperatureMin.getOrNull(index),
                maxTempC = dailySource.temperatureMax.getOrNull(index),
                precipitationMm = dailySource.precipitationSum.getOrNull(index),
                precipitationProbabilityPercent = dailySource.precipitationProbabilityMax.getOrNull(index)?.roundToInt(),
                maxWindSpeedMps = dailySource.windSpeedMax.getOrNull(index),
                sunriseIso = dailySource.sunrise.getOrNull(index),
                sunsetIso = dailySource.sunset.getOrNull(index),
                uvIndexMax = dailySource.uvIndexMax.getOrNull(index),
                condition = fromWmoCode(dailySource.weatherCode.getOrNull(index)),
                conditionText = describeWmoCode(dailySource.weatherCode.getOrNull(index)),
            )
        }.take(7)

        return ProviderForecast(
            providerId = providerInfo.id,
            providerName = providerInfo.displayName,
            sourceUrl = providerInfo.sourceUrl,
            attribution = "Open-Meteo, CC BY 4.0, free for non-commercial use",
            coverage = providerInfo.coverage,
            locationName = "",
            timeZoneId = response.timezone ?: targetTimeZoneId.orEmpty(),
            coordinates = coordinates,
            fetchedAt = Instant.now(),
            statusNote = providerInfo.notes,
            confidence = 0.92,
            current = current,
            hourly = hourly,
            daily = daily,
        )
    }
}

class MetNorwayWeatherProvider(
    private val httpClient: WeatherHttpClient,
) : WeatherProvider {
    override val providerInfo: ProviderInfo = defaultProviderCatalog.first { it.id == ProviderId.MET_NORWAY }

    override suspend fun fetch(
        coordinates: Coordinates,
        settings: AppSettings,
        targetTimeZoneId: String?,
    ): ProviderForecast? {
        if (!settings.metNorwayEnabled) {
            return null
        }
        val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact".toHttpUrl().newBuilder()
            .addQueryParameter("lat", coordinates.latitude.toString())
            .addQueryParameter("lon", coordinates.longitude.toString())
            .build()
            .toString()
        val response = httpClient.getDecoded<MetNorwayResponse>(
            url,
            headers = mapOf(
                "User-Agent" to "Rainfern/0.1 (personal-use Android weather app)",
                "Accept" to "application/json",
            ),
        ) ?: return null

        val series = response.properties.timeseries
        val currentPoint = series.firstOrNull() ?: return null
        val current = CurrentWeather(
            observedAt = Instant.parse(currentPoint.time),
            temperatureC = currentPoint.data.instant.details.airTemperature,
            dewPointC = currentPoint.data.instant.details.dewPoint,
            humidityPercent = currentPoint.data.instant.details.relativeHumidity?.roundToInt(),
            pressureHpa = currentPoint.data.instant.details.pressureAtSeaLevel,
            windSpeedMps = currentPoint.data.instant.details.windSpeed,
            windDirectionDegrees = currentPoint.data.instant.details.windDirection?.roundToInt(),
            cloudCoverPercent = currentPoint.data.instant.details.cloudAreaFraction?.roundToInt(),
            precipitationMm = currentPoint.data.next1Hours?.details?.precipitationAmount,
            precipitationProbabilityPercent = currentPoint.data.next1Hours?.details?.precipitationProbability?.roundToInt(),
            condition = fromMetSymbol(currentPoint.data.next1Hours?.summary?.symbolCode),
            conditionText = describeMetSymbol(currentPoint.data.next1Hours?.summary?.symbolCode),
        )

        val hourly = series.take(48).map { point ->
            HourlyWeather(
                time = Instant.parse(point.time),
                temperatureC = point.data.instant.details.airTemperature,
                feelsLikeC = point.data.instant.details.airTemperature,
                dewPointC = point.data.instant.details.dewPoint,
                humidityPercent = point.data.instant.details.relativeHumidity?.roundToInt(),
                precipitationMm = point.data.next1Hours?.details?.precipitationAmount,
                precipitationProbabilityPercent = point.data.next1Hours?.details?.precipitationProbability?.roundToInt(),
                windSpeedMps = point.data.instant.details.windSpeed,
                cloudCoverPercent = point.data.instant.details.cloudAreaFraction?.roundToInt(),
                condition = fromMetSymbol(point.data.next1Hours?.summary?.symbolCode),
                conditionText = describeMetSymbol(point.data.next1Hours?.summary?.symbolCode),
            )
        }

        val zoneId = resolveZoneId(targetTimeZoneId)
        val daily = hourly.groupBy {
            it.time.atZone(zoneId).toLocalDate()
        }.entries
            .sortedBy { it.key }
            .take(7)
            .map { (date, items) ->
                DailyWeather(
                    date = date,
                    minTempC = items.mapNotNull { it.temperatureC }.minOrNull(),
                    maxTempC = items.mapNotNull { it.temperatureC }.maxOrNull(),
                    precipitationMm = items.sumOf { it.precipitationMm ?: 0.0 },
                    precipitationProbabilityPercent = items.mapNotNull { it.precipitationProbabilityPercent }.maxOrNull(),
                    maxWindSpeedMps = items.mapNotNull { it.windSpeedMps }.maxOrNull(),
                    condition = items.groupingBy { it.condition }.eachCount().maxByOrNull { it.value }?.key
                        ?: WeatherCondition.UNKNOWN,
                    conditionText = items.groupingBy { it.conditionText }.eachCount().maxByOrNull { it.value }?.key
                        ?: "Unknown",
                )
            }

        return ProviderForecast(
            providerId = providerInfo.id,
            providerName = providerInfo.displayName,
            sourceUrl = providerInfo.sourceUrl,
            attribution = "MET Norway, CC BY 4.0 / NLOD 2.0",
            coverage = providerInfo.coverage,
            locationName = "",
            timeZoneId = zoneId.id,
            coordinates = coordinates,
            fetchedAt = Instant.parse(response.properties.meta.updatedAt),
            statusNote = providerInfo.notes,
            confidence = 0.88,
            current = current,
            hourly = hourly,
            daily = daily,
        )
    }
}

private fun fromWmoCode(code: Int?): WeatherCondition = when (code) {
    0 -> WeatherCondition.CLEAR
    1, 2 -> WeatherCondition.PARTLY_CLOUDY
    3 -> WeatherCondition.CLOUDY
    45, 48 -> WeatherCondition.FOG
    51, 53, 55 -> WeatherCondition.DRIZZLE
    56, 57 -> WeatherCondition.SLEET
    61, 63, 65, 80, 81, 82 -> WeatherCondition.RAIN
    66, 67 -> WeatherCondition.SLEET
    71, 73, 75, 77, 85, 86 -> WeatherCondition.SNOW
    95, 96, 99 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.UNKNOWN
}

private fun describeWmoCode(code: Int?): String = when (fromWmoCode(code)) {
    WeatherCondition.CLEAR -> "Clear"
    WeatherCondition.PARTLY_CLOUDY -> "Partly cloudy"
    WeatherCondition.CLOUDY -> "Cloudy"
    WeatherCondition.FOG -> "Fog"
    WeatherCondition.DRIZZLE -> "Drizzle"
    WeatherCondition.RAIN -> "Rain"
    WeatherCondition.SLEET -> "Sleet"
    WeatherCondition.SNOW -> "Snow"
    WeatherCondition.THUNDERSTORM -> "Thunderstorm"
    else -> "Unknown"
}

private fun fromMetSymbol(symbol: String?): WeatherCondition {
    val value = symbol.orEmpty()
    return when {
        "thunder" in value -> WeatherCondition.THUNDERSTORM
        "sleet" in value -> WeatherCondition.SLEET
        "snow" in value -> WeatherCondition.SNOW
        "rain" in value -> WeatherCondition.RAIN
        "drizzle" in value -> WeatherCondition.DRIZZLE
        "fog" in value -> WeatherCondition.FOG
        "cloudy" in value -> WeatherCondition.CLOUDY
        "fair" in value || "partlycloudy" in value -> WeatherCondition.PARTLY_CLOUDY
        "clearsky" in value -> WeatherCondition.CLEAR
        else -> WeatherCondition.UNKNOWN
    }
}

private fun describeMetSymbol(symbol: String?): String = symbol
    ?.substringBefore("_")
    ?.replace("partlycloudy", "Partly cloudy")
    ?.replace("clearsky", "Clear")
    ?.replaceFirstChar { it.uppercase() }
    ?: "Unknown"

@Serializable
private data class OpenMeteoResponse(
    val timezone: String? = null,
    val current: OpenMeteoCurrent? = null,
    val hourly: OpenMeteoHourly? = null,
    val daily: OpenMeteoDaily? = null,
)

@Serializable
private data class OpenMeteoCurrent(
    val time: String,
    @SerialName("temperature_2m")
    val temperature: Double? = null,
    @SerialName("apparent_temperature")
    val feelsLike: Double? = null,
    @SerialName("dew_point_2m")
    val dewPoint: Double? = null,
    @SerialName("relative_humidity_2m")
    val humidity: Double? = null,
    val precipitation: Double? = null,
    @SerialName("weather_code")
    val weatherCode: Int? = null,
    @SerialName("cloud_cover")
    val cloudCover: Double? = null,
    @SerialName("pressure_msl")
    val pressure: Double? = null,
    val visibility: Double? = null,
    @SerialName("wind_speed_10m")
    val windSpeed: Double? = null,
    @SerialName("wind_direction_10m")
    val windDirection: Double? = null,
    @SerialName("wind_gusts_10m")
    val windGust: Double? = null,
    @SerialName("is_day")
    val isDay: Int? = null,
)

@Serializable
private data class OpenMeteoHourly(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m")
    val temperature: List<Double?> = emptyList(),
    @SerialName("apparent_temperature")
    val feelsLike: List<Double?> = emptyList(),
    @SerialName("dew_point_2m")
    val dewPoint: List<Double?> = emptyList(),
    @SerialName("relative_humidity_2m")
    val humidity: List<Double?> = emptyList(),
    @SerialName("precipitation_probability")
    val precipitationProbability: List<Double?> = emptyList(),
    val precipitation: List<Double?> = emptyList(),
    @SerialName("weather_code")
    val weatherCode: List<Int?> = emptyList(),
    @SerialName("cloud_cover")
    val cloudCover: List<Double?> = emptyList(),
    val visibility: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m")
    val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_gusts_10m")
    val windGust: List<Double?> = emptyList(),
)

@Serializable
private data class OpenMeteoDaily(
    val time: List<String> = emptyList(),
    @SerialName("temperature_2m_max")
    val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min")
    val temperatureMin: List<Double?> = emptyList(),
    @SerialName("precipitation_sum")
    val precipitationSum: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max")
    val precipitationProbabilityMax: List<Double?> = emptyList(),
    @SerialName("weather_code")
    val weatherCode: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m_max")
    val windSpeedMax: List<Double?> = emptyList(),
    val sunrise: List<String?> = emptyList(),
    val sunset: List<String?> = emptyList(),
    @SerialName("uv_index_max")
    val uvIndexMax: List<Double?> = emptyList(),
)

@Serializable
private data class MetNorwayResponse(
    val properties: MetProperties,
)

@Serializable
private data class MetProperties(
    val meta: MetMeta,
    val timeseries: List<MetTimeseries>,
)

@Serializable
private data class MetMeta(
    @SerialName("updated_at")
    val updatedAt: String,
)

@Serializable
private data class MetTimeseries(
    val time: String,
    val data: MetPointData,
)

@Serializable
private data class MetPointData(
    val instant: MetInstantData,
    @SerialName("next_1_hours")
    val next1Hours: MetNextHours? = null,
)

@Serializable
private data class MetInstantData(
    val details: MetInstantDetails,
)

@Serializable
private data class MetInstantDetails(
    @SerialName("air_temperature")
    val airTemperature: Double? = null,
    @SerialName("dew_point_temperature")
    val dewPoint: Double? = null,
    @SerialName("relative_humidity")
    val relativeHumidity: Double? = null,
    @SerialName("air_pressure_at_sea_level")
    val pressureAtSeaLevel: Double? = null,
    @SerialName("wind_speed")
    val windSpeed: Double? = null,
    @SerialName("wind_from_direction")
    val windDirection: Double? = null,
    @SerialName("cloud_area_fraction")
    val cloudAreaFraction: Double? = null,
)

@Serializable
private data class MetNextHours(
    val summary: MetSummary? = null,
    val details: MetNextHoursDetails? = null,
)

@Serializable
private data class MetSummary(
    @SerialName("symbol_code")
    val symbolCode: String? = null,
)

@Serializable
private data class MetNextHoursDetails(
    @SerialName("precipitation_amount")
    val precipitationAmount: Double? = null,
    @SerialName("probability_of_precipitation")
    val precipitationProbability: Double? = null,
)

class WeatherGovProvider(
    private val httpClient: WeatherHttpClient,
) : WeatherProvider {
    override val providerInfo: ProviderInfo = defaultProviderCatalog.first { it.id == ProviderId.WEATHER_GOV }

    override suspend fun fetch(
        coordinates: Coordinates,
        settings: AppSettings,
        targetTimeZoneId: String?,
    ): ProviderForecast? {
        if (!settings.weatherGovEnabled) {
            return null
        }
        val headers = mapOf(
            "User-Agent" to "Rainfern/0.1 (personal-use Android weather app)",
            "Accept" to "application/geo+json",
        )
        val pointsUrl = "https://api.weather.gov/points/${coordinates.latitude},${coordinates.longitude}"
        val point = httpClient.getDecoded<WeatherGovPointResponse>(pointsUrl, headers) ?: return null
        val hourlyResponse = httpClient.getDecoded<WeatherGovForecastResponse>(point.properties.forecastHourly, headers)
            ?: return null
        val locationName = listOfNotNull(
            point.properties.relativeLocation?.properties?.city,
            point.properties.relativeLocation?.properties?.state,
        ).joinToString(", ")

        val zoneId = resolveZoneId(point.properties.timeZone ?: targetTimeZoneId)
        val hourly = hourlyResponse.properties.periods.map { period ->
            HourlyWeather(
                time = java.time.OffsetDateTime.parse(period.startTime).toInstant(),
                temperatureC = normalizeTemperatureToCelsius(period.temperature, period.temperatureUnit),
                precipitationProbabilityPercent = period.probabilityOfPrecipitation?.value?.roundToInt(),
                windSpeedMps = parseWindSpeedMps(period.windSpeed),
                condition = fromTextForecast(period.shortForecast),
                conditionText = period.shortForecast.orEmpty().ifBlank { "Unknown" },
            )
        }.take(48)

        val current = hourly.firstOrNull()?.let {
            CurrentWeather(
                observedAt = it.time,
                temperatureC = it.temperatureC,
                precipitationProbabilityPercent = it.precipitationProbabilityPercent,
                windSpeedMps = it.windSpeedMps,
                isDaylight = hourlyResponse.properties.periods.firstOrNull()?.isDaytime,
                condition = it.condition,
                conditionText = "Near-term forecast: ${it.conditionText}",
            )
        }

        val daily = hourly.groupBy { it.time.atZone(zoneId).toLocalDate() }
            .entries
            .sortedBy { it.key }
            .take(7)
            .map { (date, items) ->
                DailyWeather(
                    date = date,
                    minTempC = items.mapNotNull { it.temperatureC }.minOrNull(),
                    maxTempC = items.mapNotNull { it.temperatureC }.maxOrNull(),
                    precipitationProbabilityPercent = items.mapNotNull { it.precipitationProbabilityPercent }.maxOrNull(),
                    maxWindSpeedMps = items.mapNotNull { it.windSpeedMps }.maxOrNull(),
                    condition = items.groupingBy { it.condition }.eachCount().maxByOrNull { it.value }?.key
                        ?: WeatherCondition.UNKNOWN,
                    conditionText = items.groupingBy { it.conditionText }.eachCount().maxByOrNull { it.value }?.key
                        ?: "Unknown",
                )
            }

        val alertsUrl = "https://api.weather.gov/alerts/active?point=${coordinates.latitude},${coordinates.longitude}"
        val alertsResponse = httpClient.getDecoded<WeatherGovAlertsResponse>(alertsUrl, headers)
        val alerts = alertsResponse?.features.orEmpty().map { feature ->
            dev.rdime.rainfern.data.model.WeatherAlert(
                title = feature.properties.headline.orEmpty().ifBlank { "Weather alert" },
                severity = feature.properties.severity.orEmpty().ifBlank { "Unknown" },
                description = feature.properties.description.orEmpty(),
                startsAt = feature.properties.onset?.let { java.time.OffsetDateTime.parse(it).toInstant() },
                endsAt = feature.properties.ends?.let { java.time.OffsetDateTime.parse(it).toInstant() },
                sourceName = providerInfo.displayName,
            )
        }

        return ProviderForecast(
            providerId = providerInfo.id,
            providerName = providerInfo.displayName,
            sourceUrl = providerInfo.sourceUrl,
            attribution = "weather.gov / National Weather Service public API",
            coverage = providerInfo.coverage,
            locationName = locationName,
            timeZoneId = zoneId.id,
            coordinates = coordinates,
            fetchedAt = Instant.now(),
            statusNote = "${providerInfo.notes} Current conditions are approximated from the next hourly forecast period when live observations are unavailable.",
            confidence = 0.96,
            current = current,
            hourly = hourly,
            daily = daily,
            alerts = alerts,
        )
    }
}

class WeatherApiComProvider(
    private val httpClient: WeatherHttpClient,
) : WeatherProvider {
    override val providerInfo: ProviderInfo = defaultProviderCatalog.first { it.id == ProviderId.WEATHER_API }

    override suspend fun fetch(
        coordinates: Coordinates,
        settings: AppSettings,
        targetTimeZoneId: String?,
    ): ProviderForecast? {
        if (!settings.weatherApiEnabled || settings.weatherApiKey.isBlank()) {
            return null
        }
        val url = "https://api.weatherapi.com/v1/forecast.json".toHttpUrl().newBuilder()
            .addQueryParameter("key", settings.weatherApiKey)
            .addQueryParameter("q", "${coordinates.latitude},${coordinates.longitude}")
            .addQueryParameter("days", "3")
            .addQueryParameter("aqi", "no")
            .addQueryParameter("alerts", "yes")
            .build()
            .toString()
        val response = httpClient.getDecoded<WeatherApiResponse>(url) ?: return null

        val current = response.current?.let {
            CurrentWeather(
                observedAt = Instant.ofEpochSecond(it.lastUpdatedEpoch),
                temperatureC = it.tempC,
                feelsLikeC = it.feelsLikeC,
                dewPointC = it.dewPointC,
                humidityPercent = it.humidity,
                precipitationMm = it.precipMm,
                windSpeedMps = it.windKph?.div(3.6),
                windGustMps = it.gustKph?.div(3.6),
                windDirectionDegrees = it.windDegree,
                pressureHpa = it.pressureMb,
                cloudCoverPercent = it.cloud,
                visibilityKm = it.visibilityKm,
                condition = fromWeatherApiCode(it.condition?.code),
                conditionText = it.condition?.text ?: "Unknown",
            )
        }

        val hourly = response.forecast?.forecastDays.orEmpty().flatMap { day ->
            day.hours.map { hour ->
                HourlyWeather(
                    time = Instant.ofEpochSecond(hour.timeEpoch),
                    temperatureC = hour.tempC,
                    feelsLikeC = hour.feelsLikeC,
                    dewPointC = hour.dewPointC,
                    precipitationMm = hour.precipMm,
                    precipitationProbabilityPercent = hour.chanceOfRain,
                    windSpeedMps = hour.windKph?.div(3.6),
                    windGustMps = hour.gustKph?.div(3.6),
                    cloudCoverPercent = hour.cloud,
                    humidityPercent = hour.humidity,
                    visibilityKm = hour.visibilityKm,
                    condition = fromWeatherApiCode(hour.condition?.code),
                    conditionText = hour.condition?.text ?: "Unknown",
                )
            }
        }.take(48)

        val daily = response.forecast?.forecastDays.orEmpty().map { day ->
            DailyWeather(
                date = LocalDate.parse(day.date),
                minTempC = day.day?.minTempC,
                maxTempC = day.day?.maxTempC,
                precipitationMm = day.day?.totalPrecipMm,
                precipitationProbabilityPercent = day.day?.dailyChanceOfRain,
                maxWindSpeedMps = day.day?.maxWindKph?.div(3.6),
                sunriseIso = day.astro?.sunrise,
                sunsetIso = day.astro?.sunset,
                uvIndexMax = day.day?.uvIndex,
                condition = fromWeatherApiCode(day.day?.condition?.code),
                conditionText = day.day?.condition?.text ?: "Unknown",
            )
        }

        val alerts = response.alerts?.alerts.orEmpty().map { alert ->
            dev.rdime.rainfern.data.model.WeatherAlert(
                title = alert.headline.orEmpty().ifBlank { "Weather alert" },
                severity = alert.severity.orEmpty().ifBlank { "Unknown" },
                description = alert.desc.orEmpty(),
                startsAt = parseBestEffortInstant(alert.effective, response.location?.timeZoneId ?: targetTimeZoneId),
                endsAt = parseBestEffortInstant(alert.expires, response.location?.timeZoneId ?: targetTimeZoneId),
                sourceName = providerInfo.displayName,
            )
        }

        return ProviderForecast(
            providerId = providerInfo.id,
            providerName = providerInfo.displayName,
            sourceUrl = providerInfo.sourceUrl,
            attribution = "WeatherAPI.com free plan",
            coverage = providerInfo.coverage,
            locationName = listOfNotNull(response.location?.name, response.location?.country).joinToString(", "),
            timeZoneId = response.location?.timeZoneId ?: targetTimeZoneId.orEmpty(),
            coordinates = coordinates,
            fetchedAt = Instant.now(),
            statusNote = providerInfo.notes,
            confidence = 0.84,
            current = current,
            hourly = hourly,
            daily = daily,
            alerts = alerts,
        )
    }
}

private fun parseBestEffortInstant(
    value: String?,
    timeZoneId: String? = null,
): Instant? {
    if (value.isNullOrBlank()) {
        return null
    }
    return runCatching {
        java.time.OffsetDateTime.parse(value).toInstant()
    }.recoverCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
            .atZone(resolveZoneId(timeZoneId))
            .toInstant()
    }.getOrNull()
}

private fun fromWeatherApiCode(code: Int?): WeatherCondition = when (code) {
    1000 -> WeatherCondition.CLEAR
    1003 -> WeatherCondition.PARTLY_CLOUDY
    1006, 1009 -> WeatherCondition.CLOUDY
    1030, 1135, 1147 -> WeatherCondition.FOG
    1063, 1150, 1153, 1180, 1183, 1240 -> WeatherCondition.DRIZZLE
    1186, 1189, 1192, 1195, 1243, 1246 -> WeatherCondition.RAIN
    1066, 1114, 1117, 1210, 1213, 1216, 1219, 1222, 1225, 1255, 1258 -> WeatherCondition.SNOW
    1069, 1072, 1168, 1171, 1198, 1201, 1204, 1207, 1249, 1252, 1261, 1264 -> WeatherCondition.SLEET
    1087, 1273, 1276, 1279, 1282 -> WeatherCondition.THUNDERSTORM
    else -> WeatherCondition.UNKNOWN
}

private fun fromTextForecast(text: String?): WeatherCondition {
    val value = text.orEmpty().lowercase()
    return when {
        "thunder" in value -> WeatherCondition.THUNDERSTORM
        "sleet" in value -> WeatherCondition.SLEET
        "snow" in value || "blizzard" in value -> WeatherCondition.SNOW
        "rain" in value || "showers" in value -> WeatherCondition.RAIN
        "drizzle" in value -> WeatherCondition.DRIZZLE
        "fog" in value || "haze" in value -> WeatherCondition.FOG
        "wind" in value || "breezy" in value -> WeatherCondition.WINDY
        "cloud" in value || "overcast" in value -> WeatherCondition.CLOUDY
        "partly" in value || "mostly sunny" in value || "mostly clear" in value -> WeatherCondition.PARTLY_CLOUDY
        "sunny" in value || "clear" in value -> WeatherCondition.CLEAR
        else -> WeatherCondition.UNKNOWN
    }
}

private fun fToC(value: Double): Double = (value - 32.0) * 5.0 / 9.0

private fun normalizeTemperatureToCelsius(
    value: Int?,
    unit: String?,
): Double? {
    val reading = value?.toDouble() ?: return null
    return when (unit.orEmpty().trim().uppercase()) {
        "C" -> reading
        "F" -> fToC(reading)
        else -> fToC(reading)
    }
}

private fun resolveZoneId(timeZoneId: String?): ZoneId = runCatching {
    ZoneId.of(timeZoneId.orEmpty().ifBlank { ZoneId.systemDefault().id })
}.getOrElse {
    ZoneId.systemDefault()
}

private fun parseWindSpeedMps(raw: String?): Double? {
    val numbers = Regex("""\d+""").findAll(raw.orEmpty()).map { it.value.toDouble() }.toList()
    if (numbers.isEmpty()) {
        return null
    }
    return numbers.average() * 0.44704
}

@Serializable
private data class WeatherGovPointResponse(
    val properties: WeatherGovPointProperties,
)

@Serializable
private data class WeatherGovPointProperties(
    @SerialName("forecastHourly")
    val forecastHourly: String,
    val timeZone: String? = null,
    val relativeLocation: WeatherGovRelativeLocation? = null,
)

@Serializable
private data class WeatherGovRelativeLocation(
    val properties: WeatherGovRelativeLocationProperties,
)

@Serializable
private data class WeatherGovRelativeLocationProperties(
    val city: String? = null,
    val state: String? = null,
)

@Serializable
private data class WeatherGovForecastResponse(
    val properties: WeatherGovForecastProperties,
)

@Serializable
private data class WeatherGovForecastProperties(
    val periods: List<WeatherGovPeriod>,
)

@Serializable
private data class WeatherGovPeriod(
    val startTime: String,
    val temperature: Int? = null,
    val temperatureUnit: String? = null,
    val windSpeed: String? = null,
    val shortForecast: String? = null,
    val isDaytime: Boolean? = null,
    val probabilityOfPrecipitation: WeatherGovProbability? = null,
)

@Serializable
private data class WeatherGovProbability(
    val value: Double? = null,
)

@Serializable
private data class WeatherGovAlertsResponse(
    val features: List<WeatherGovAlertFeature> = emptyList(),
)

@Serializable
private data class WeatherGovAlertFeature(
    val properties: WeatherGovAlertProperties,
)

@Serializable
private data class WeatherGovAlertProperties(
    val headline: String? = null,
    val severity: String? = null,
    val description: String? = null,
    val onset: String? = null,
    val ends: String? = null,
)

@Serializable
private data class WeatherApiResponse(
    val location: WeatherApiLocation? = null,
    val current: WeatherApiCurrent? = null,
    val forecast: WeatherApiForecast? = null,
    val alerts: WeatherApiAlerts? = null,
)

@Serializable
private data class WeatherApiLocation(
    val name: String? = null,
    val country: String? = null,
    @SerialName("tz_id")
    val timeZoneId: String? = null,
)

@Serializable
private data class WeatherApiCurrent(
    @SerialName("last_updated_epoch")
    val lastUpdatedEpoch: Long,
    @SerialName("temp_c")
    val tempC: Double? = null,
    @SerialName("feelslike_c")
    val feelsLikeC: Double? = null,
    @SerialName("dewpoint_c")
    val dewPointC: Double? = null,
    val humidity: Int? = null,
    @SerialName("precip_mm")
    val precipMm: Double? = null,
    @SerialName("wind_kph")
    val windKph: Double? = null,
    @SerialName("gust_kph")
    val gustKph: Double? = null,
    @SerialName("wind_degree")
    val windDegree: Int? = null,
    @SerialName("pressure_mb")
    val pressureMb: Double? = null,
    val cloud: Int? = null,
    @SerialName("vis_km")
    val visibilityKm: Double? = null,
    val condition: WeatherApiCondition? = null,
)

@Serializable
private data class WeatherApiForecast(
    @SerialName("forecastday")
    val forecastDays: List<WeatherApiForecastDay> = emptyList(),
)

@Serializable
private data class WeatherApiForecastDay(
    val date: String,
    val day: WeatherApiDay? = null,
    val astro: WeatherApiAstro? = null,
    @SerialName("hour")
    val hours: List<WeatherApiHour> = emptyList(),
)

@Serializable
private data class WeatherApiDay(
    @SerialName("maxtemp_c")
    val maxTempC: Double? = null,
    @SerialName("mintemp_c")
    val minTempC: Double? = null,
    @SerialName("totalprecip_mm")
    val totalPrecipMm: Double? = null,
    @SerialName("daily_chance_of_rain")
    val dailyChanceOfRain: Int? = null,
    @SerialName("maxwind_kph")
    val maxWindKph: Double? = null,
    @SerialName("uv")
    val uvIndex: Double? = null,
    val condition: WeatherApiCondition? = null,
)

@Serializable
private data class WeatherApiAstro(
    val sunrise: String? = null,
    val sunset: String? = null,
)

@Serializable
private data class WeatherApiHour(
    @SerialName("time_epoch")
    val timeEpoch: Long,
    @SerialName("temp_c")
    val tempC: Double? = null,
    @SerialName("feelslike_c")
    val feelsLikeC: Double? = null,
    @SerialName("dewpoint_c")
    val dewPointC: Double? = null,
    @SerialName("precip_mm")
    val precipMm: Double? = null,
    @SerialName("chance_of_rain")
    val chanceOfRain: Int? = null,
    @SerialName("wind_kph")
    val windKph: Double? = null,
    @SerialName("gust_kph")
    val gustKph: Double? = null,
    val humidity: Int? = null,
    val cloud: Int? = null,
    @SerialName("vis_km")
    val visibilityKm: Double? = null,
    val condition: WeatherApiCondition? = null,
)

@Serializable
private data class WeatherApiCondition(
    val text: String? = null,
    val code: Int? = null,
)

@Serializable
private data class WeatherApiAlerts(
    @SerialName("alert")
    val alerts: List<WeatherApiAlert> = emptyList(),
)

@Serializable
private data class WeatherApiAlert(
    val headline: String? = null,
    val severity: String? = null,
    val desc: String? = null,
    val effective: String? = null,
    val expires: String? = null,
)
