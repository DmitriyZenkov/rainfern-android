package dev.rdime.rainfern.data.environment

import dev.rdime.rainfern.data.model.AirQualitySnapshot
import dev.rdime.rainfern.data.model.Coordinates
import dev.rdime.rainfern.data.model.PollenSnapshot
import dev.rdime.rainfern.data.network.provider.WeatherHttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.math.roundToInt

data class EnvironmentDetails(
    val airQuality: AirQualitySnapshot? = null,
    val pollen: PollenSnapshot? = null,
)

class AirQualityRepository(
    private val httpClient: WeatherHttpClient,
) {
    suspend fun fetch(coordinates: Coordinates): EnvironmentDetails? {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", coordinates.latitude.toString())
            .addQueryParameter("longitude", coordinates.longitude.toString())
            .addQueryParameter("timezone", "auto")
            .addQueryParameter(
                "current",
                listOf(
                    "us_aqi",
                    "european_aqi",
                    "pm2_5",
                    "pm10",
                    "ozone",
                    "uv_index",
                    "alder_pollen",
                    "birch_pollen",
                    "grass_pollen",
                    "mugwort_pollen",
                    "ragweed_pollen",
                ).joinToString(","),
            )
            .build()
            .toString()
        val response = httpClient.getDecoded<OpenMeteoAirQualityResponse>(url) ?: return null
        val current = response.current ?: return null
        val airQuality = AirQualitySnapshot(
            sourceName = "Open-Meteo Air Quality",
            usAqi = current.usAqi?.roundToInt(),
            europeanAqi = current.europeanAqi?.roundToInt(),
            pm25 = current.pm25,
            pm10 = current.pm10,
            ozone = current.ozone,
            uvIndex = current.uvIndex,
        )
        val pollenValues = listOf(current.alder, current.birch, current.grass, current.mugwort, current.ragweed)
        val pollen = PollenSnapshot(
            sourceName = "Open-Meteo Air Quality",
            coverageNote = if (pollenValues.any { it != null }) {
                "Pollen values are seasonal and region-dependent."
            } else {
                "Pollen data is not currently available for this region or season from the free source in use."
            },
            alder = current.alder,
            birch = current.birch,
            grass = current.grass,
            mugwort = current.mugwort,
            ragweed = current.ragweed,
        )
        return EnvironmentDetails(
            airQuality = airQuality,
            pollen = pollen,
        )
    }
}

@Serializable
private data class OpenMeteoAirQualityResponse(
    val current: OpenMeteoAirQualityCurrent? = null,
)

@Serializable
private data class OpenMeteoAirQualityCurrent(
    @SerialName("us_aqi")
    val usAqi: Double? = null,
    @SerialName("european_aqi")
    val europeanAqi: Double? = null,
    @SerialName("pm2_5")
    val pm25: Double? = null,
    val pm10: Double? = null,
    val ozone: Double? = null,
    @SerialName("uv_index")
    val uvIndex: Double? = null,
    @SerialName("alder_pollen")
    val alder: Double? = null,
    @SerialName("birch_pollen")
    val birch: Double? = null,
    @SerialName("grass_pollen")
    val grass: Double? = null,
    @SerialName("mugwort_pollen")
    val mugwort: Double? = null,
    @SerialName("ragweed_pollen")
    val ragweed: Double? = null,
)
