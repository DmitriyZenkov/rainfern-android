package dev.rdime.rainfern.data.model

import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object InstantAsEpochMillisSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("InstantAsEpochMillis", PrimitiveKind.LONG)

    override fun deserialize(decoder: Decoder): Instant = Instant.ofEpochMilli(decoder.decodeLong())

    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeLong(value.toEpochMilli())
    }
}

object LocalDateIsoSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateIso", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())

    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }
}

@Serializable
data class Coordinates(
    val latitude: Double,
    val longitude: Double,
)

const val DEVICE_LOCATION_KEY = "device_location"
const val WIDGET_ACTIVE_LOCATION_KEY = "widget_active_forecast"

@Serializable
data class SavedPlace(
    val id: String,
    val name: String,
    val adminArea: String = "",
    val country: String = "",
    val countryCode: String = "",
    val timeZoneId: String = "",
    val coordinates: Coordinates,
    val isFavorite: Boolean = false,
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val lastSelectedAt: Instant = Instant.EPOCH,
) {
    val subtitle: String
        get() = listOfNotNull(
            adminArea.takeIf(String::isNotBlank),
            country.takeIf(String::isNotBlank),
        ).joinToString(", ")

    val label: String
        get() = listOfNotNull(
            name.takeIf(String::isNotBlank),
            subtitle.takeIf(String::isNotBlank),
        ).joinToString(", ").ifBlank {
            "${"%.3f".format(coordinates.latitude)}, ${"%.3f".format(coordinates.longitude)}"
        }
}

@Serializable
data class PlacesState(
    val activePlaceId: String? = null,
    val savedPlaces: List<SavedPlace> = emptyList(),
) {
    val activePlace: SavedPlace?
        get() = activePlaceId?.let { selectedId -> savedPlaces.firstOrNull { it.id == selectedId } }

    val favoritePlaces: List<SavedPlace>
        get() = savedPlaces.filter { it.isFavorite }.sortedBy { it.label.lowercase() }

    val recentPlaces: List<SavedPlace>
        get() = savedPlaces.sortedByDescending { it.lastSelectedAt }.take(6)
}

@Serializable
enum class LocationMode {
    ASK,
    ON_OPEN,
    BACKGROUND,
}

@Serializable
enum class RefreshInterval(val minutes: Long, val label: String) {
    FIFTEEN_MINUTES(15, "Every 15 min"),
    THIRTY_MINUTES(30, "Every 30 min"),
    HOURLY(60, "Every 1 hour"),
    EVERY_3_HOURS(180, "Every 3 hours"),
    EVERY_6_HOURS(360, "Every 6 hours"),
}

@Serializable
enum class TemperatureUnit(val label: String) {
    CELSIUS("Celsius"),
    FAHRENHEIT("Fahrenheit"),
}

@Serializable
enum class WindUnit(val label: String) {
    MPS("m/s"),
    KPH("km/h"),
    MPH("mph"),
}

@Serializable
enum class PressureUnit(val label: String) {
    HPA("hPa"),
    MMHG("mmHg"),
    INHG("inHg"),
}

@Serializable
enum class TimeFormatPreference(val label: String) {
    SYSTEM("System"),
    H24("24-hour"),
    H12("12-hour"),
}

@Serializable
enum class WidgetThemeIntensity(val label: String) {
    SOFT("Soft"),
    BALANCED("Balanced"),
    VIVID("Vivid"),
}

@Serializable
enum class WidgetDiagnosticsMode(val label: String) {
    CLEAN("Clean"),
    SOURCES("Sources"),
    CONFIDENCE("Confidence"),
}

@Serializable
enum class ProviderId {
    OPEN_METEO,
    MET_NORWAY,
    WEATHER_GOV,
    WEATHER_API,
}

@Serializable
enum class ForecastMetric {
    TEMPERATURE,
    PRECIPITATION_PROBABILITY,
    WIND_SPEED,
    CLOUD_COVER,
    CONDITION,
}

@Serializable
enum class RegionalProfile {
    GLOBAL,
    US,
    NORTHERN_EUROPE,
}

@Serializable
enum class WeatherCondition {
    CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SHOWERS,
    SNOW,
    SLEET,
    THUNDERSTORM,
    WINDY,
    HAIL,
    UNKNOWN,
}

@Serializable
data class WeatherAlert(
    val title: String,
    val severity: String,
    val description: String,
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val startsAt: Instant? = null,
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val endsAt: Instant? = null,
    val sourceName: String,
)

@Serializable
data class CurrentWeather(
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val observedAt: Instant,
    val temperatureC: Double? = null,
    val feelsLikeC: Double? = null,
    val dewPointC: Double? = null,
    val humidityPercent: Int? = null,
    val precipitationMm: Double? = null,
    val precipitationProbabilityPercent: Int? = null,
    val windSpeedMps: Double? = null,
    val windGustMps: Double? = null,
    val windDirectionDegrees: Int? = null,
    val pressureHpa: Double? = null,
    val cloudCoverPercent: Int? = null,
    val visibilityKm: Double? = null,
    val isDaylight: Boolean? = null,
    val condition: WeatherCondition = WeatherCondition.UNKNOWN,
    val conditionText: String = "Unknown",
)

@Serializable
data class HourlyWeather(
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val time: Instant,
    val temperatureC: Double? = null,
    val feelsLikeC: Double? = null,
    val dewPointC: Double? = null,
    val precipitationMm: Double? = null,
    val precipitationProbabilityPercent: Int? = null,
    val windSpeedMps: Double? = null,
    val windGustMps: Double? = null,
    val cloudCoverPercent: Int? = null,
    val humidityPercent: Int? = null,
    val visibilityKm: Double? = null,
    val condition: WeatherCondition = WeatherCondition.UNKNOWN,
    val conditionText: String = "Unknown",
)

@Serializable
data class DailyWeather(
    @Serializable(with = LocalDateIsoSerializer::class)
    val date: LocalDate,
    val minTempC: Double? = null,
    val maxTempC: Double? = null,
    val precipitationMm: Double? = null,
    val precipitationProbabilityPercent: Int? = null,
    val maxWindSpeedMps: Double? = null,
    val sunriseIso: String? = null,
    val sunsetIso: String? = null,
    val uvIndexMax: Double? = null,
    val condition: WeatherCondition = WeatherCondition.UNKNOWN,
    val conditionText: String = "Unknown",
)

@Serializable
data class ProviderForecast(
    val providerId: ProviderId,
    val providerName: String,
    val sourceUrl: String,
    val attribution: String,
    val coverage: String,
    val locationName: String,
    val timeZoneId: String = "",
    val coordinates: Coordinates,
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val fetchedAt: Instant,
    val statusNote: String = "",
    val confidence: Double = 0.0,
    val current: CurrentWeather? = null,
    val hourly: List<HourlyWeather> = emptyList(),
    val daily: List<DailyWeather> = emptyList(),
    val alerts: List<WeatherAlert> = emptyList(),
)

@Serializable
data class MetricDisagreement(
    val metric: ForecastMetric,
    val minValue: Double? = null,
    val maxValue: Double? = null,
    val spread: Double = 0.0,
    val normalizedSpread: Double = 0.0,
    val mostDivergentProviderId: ProviderId? = null,
)

@Serializable
data class MetricConfidence(
    val metric: ForecastMetric,
    val score: Double = 0.0,
    val spread: Double = 0.0,
    val providerCount: Int = 0,
)

@Serializable
data class SlotDiagnostics(
    val timestampEpochMillis: Long? = null,
    val dateIso: String? = null,
    val metricConfidence: List<MetricConfidence> = emptyList(),
    val disagreements: List<MetricDisagreement> = emptyList(),
    val summary: String = "",
)

@Serializable
data class ForecastAnomaly(
    val title: String,
    val detail: String,
    val severity: String = "Info",
)

@Serializable
data class AggregationDetails(
    val activeProviderIds: List<ProviderId> = emptyList(),
    val providerWeights: Map<String, Double> = emptyMap(),
    val regionalProfile: RegionalProfile = RegionalProfile.GLOBAL,
    val overallConfidence: Double = 0.0,
    val agreementScore: Double = 0.0,
    val freshnessScore: Double = 0.0,
    val currentMetrics: List<MetricConfidence> = emptyList(),
    val currentDisagreements: List<MetricDisagreement> = emptyList(),
    val hourlyDiagnostics: List<SlotDiagnostics> = emptyList(),
    val dailyDiagnostics: List<SlotDiagnostics> = emptyList(),
    val anomalies: List<ForecastAnomaly> = emptyList(),
    val notes: List<String> = emptyList(),
)

@Serializable
data class AirQualitySnapshot(
    val sourceName: String = "",
    val usAqi: Int? = null,
    val europeanAqi: Int? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
    val uvIndex: Double? = null,
)

@Serializable
data class PollenSnapshot(
    val sourceName: String = "",
    val coverageNote: String = "",
    val alder: Double? = null,
    val birch: Double? = null,
    val grass: Double? = null,
    val mugwort: Double? = null,
    val ragweed: Double? = null,
)

@Serializable
data class AggregatedForecast(
    val locationName: String,
    val coordinates: Coordinates,
    val timeZoneId: String = "",
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val fetchedAt: Instant,
    val current: CurrentWeather,
    val hourly: List<HourlyWeather>,
    val daily: List<DailyWeather>,
    val airQuality: AirQualitySnapshot? = null,
    val pollen: PollenSnapshot? = null,
    val alerts: List<WeatherAlert> = emptyList(),
    val details: AggregationDetails,
)

@Serializable
data class SnapshotRecord(
    @Serializable(with = InstantAsEpochMillisSerializer::class)
    val capturedAt: Instant,
    val locationName: String,
    val currentTempC: Double? = null,
    val summary: String = "Unknown",
    val confidence: Double = 0.0,
)

@Serializable
data class PlaceForecastCache(
    val locationKey: String,
    val locationLabel: String = "",
    val latest: AggregatedForecast? = null,
    val providers: List<ProviderForecast> = emptyList(),
    val history: List<SnapshotRecord> = emptyList(),
    val lastCoordinates: Coordinates? = null,
)

@Serializable
data class ForecastCache(
    val activeLocationKey: String = DEVICE_LOCATION_KEY,
    val entries: List<PlaceForecastCache> = emptyList(),
) {
    val activeEntry: PlaceForecastCache?
        get() = entries.firstOrNull { it.locationKey == activeLocationKey } ?: entries.firstOrNull()

    val latest: AggregatedForecast?
        get() = activeEntry?.latest

    val providers: List<ProviderForecast>
        get() = activeEntry?.providers.orEmpty()

    val history: List<SnapshotRecord>
        get() = activeEntry?.history.orEmpty()

    val lastCoordinates: Coordinates?
        get() = activeEntry?.lastCoordinates
}

@Serializable
data class AppSettings(
    val locationMode: LocationMode = LocationMode.ASK,
    val refreshInterval: RefreshInterval = RefreshInterval.HOURLY,
    val backgroundRefreshInterval: RefreshInterval = RefreshInterval.EVERY_3_HOURS,
    val backgroundWifiOnly: Boolean = false,
    val backgroundBatteryAware: Boolean = true,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KPH,
    val pressureUnit: PressureUnit = PressureUnit.HPA,
    val timeFormatPreference: TimeFormatPreference = TimeFormatPreference.SYSTEM,
    val widgetLocationKey: String = WIDGET_ACTIVE_LOCATION_KEY,
    val widgetThemeIntensity: WidgetThemeIntensity = WidgetThemeIntensity.BALANCED,
    val widgetDiagnosticsMode: WidgetDiagnosticsMode = WidgetDiagnosticsMode.SOURCES,
    val notifyRainSoon: Boolean = true,
    val notifyFreezing: Boolean = true,
    val notifyStrongWind: Boolean = true,
    val notifyMorningSummary: Boolean = false,
    val openMeteoEnabled: Boolean = true,
    val metNorwayEnabled: Boolean = true,
    val weatherGovEnabled: Boolean = true,
    val weatherApiEnabled: Boolean = true,
    val weatherApiKey: String = "",
    val requestShown: Boolean = false,
)

@Serializable
data class DashboardPayload(
    val cache: ForecastCache = ForecastCache(),
    val places: PlacesState = PlacesState(),
    val settings: AppSettings = AppSettings(),
    val offline: Boolean = false,
    val lastError: String? = null,
)
