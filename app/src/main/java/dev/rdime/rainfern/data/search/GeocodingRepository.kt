package dev.rdime.rainfern.data.search

import dev.rdime.rainfern.data.model.Coordinates
import dev.rdime.rainfern.data.model.SavedPlace
import dev.rdime.rainfern.data.network.provider.WeatherHttpClient
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

class GeocodingRepository(
    private val httpClient: WeatherHttpClient,
) {
    suspend fun searchPlaces(
        query: String,
        limit: Int = 8,
    ): List<SavedPlace> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 2) {
            return emptyList()
        }
        val primaryResults = searchOpenMeteo(normalizedQuery, limit)
        val fallbackResults = if (primaryResults.size < (limit / 2)) {
            searchNominatim(normalizedQuery, limit)
        } else {
            emptyList()
        }
        return (primaryResults + fallbackResults)
            .distinctBy { it.id }
            .take(limit)
    }

    private suspend fun searchOpenMeteo(
        query: String,
        limit: Int,
    ): List<SavedPlace> {
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("count", limit.toString())
            .addQueryParameter("language", Locale.getDefault().language.ifBlank { "en" })
            .addQueryParameter("format", "json")
            .build()
            .toString()
        val response = httpClient.getDecoded<OpenMeteoGeocodingResponse>(url) ?: return emptyList()
        return response.results.orEmpty().mapNotNull { result ->
            val name = result.name?.trim().orEmpty()
            if (name.isBlank()) {
                return@mapNotNull null
            }
            SavedPlace(
                id = buildPlaceId(
                    name = name,
                    latitude = result.latitude,
                    longitude = result.longitude,
                    countryCode = result.countryCode.orEmpty(),
                ),
                name = name,
                adminArea = result.admin1.orEmpty(),
                country = result.country.orEmpty(),
                countryCode = result.countryCode.orEmpty(),
                timeZoneId = result.timezone.orEmpty(),
                coordinates = Coordinates(
                    latitude = result.latitude,
                    longitude = result.longitude,
                ),
            )
        }
    }

    private suspend fun searchNominatim(
        query: String,
        limit: Int,
    ): List<SavedPlace> {
        val url = "https://nominatim.openstreetmap.org/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("addressdetails", "1")
            .addQueryParameter("accept-language", Locale.getDefault().toLanguageTag())
            .build()
            .toString()
        val response = httpClient.getDecoded<List<NominatimSearchResult>>(
            url,
            headers = mapOf(
                "User-Agent" to "Rainfern/0.1 (personal-use Android weather app)",
                "Accept-Language" to Locale.getDefault().toLanguageTag(),
            ),
        ) ?: return emptyList()
        return response.mapNotNull { result ->
            val latitude = result.latitude?.toDoubleOrNull() ?: return@mapNotNull null
            val longitude = result.longitude?.toDoubleOrNull() ?: return@mapNotNull null
            val address = result.address
            val name = listOfNotNull(
                address?.city,
                address?.town,
                address?.village,
                address?.municipality,
                address?.county,
                result.name,
            ).firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
            SavedPlace(
                id = buildPlaceId(
                    name = name,
                    latitude = latitude,
                    longitude = longitude,
                    countryCode = address?.countryCode.orEmpty(),
                ),
                name = name,
                adminArea = listOfNotNull(address?.state, address?.county).firstOrNull().orEmpty(),
                country = address?.country.orEmpty(),
                countryCode = address?.countryCode.orEmpty().uppercase(Locale.US),
                coordinates = Coordinates(
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
        }
    }
}

internal fun buildPlaceId(
    name: String,
    latitude: Double,
    longitude: Double,
    countryCode: String,
): String = listOf(
    countryCode.lowercase(Locale.US),
    name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-'),
    "%.4f".format(Locale.US, latitude),
    "%.4f".format(Locale.US, longitude),
).joinToString(":")

@Serializable
private data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoGeocodingResult>? = null,
)

@Serializable
private data class OpenMeteoGeocodingResult(
    val name: String? = null,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
    @SerialName("country_code")
    val countryCode: String? = null,
    @SerialName("admin1")
    val admin1: String? = null,
    val timezone: String? = null,
)

@Serializable
private data class NominatimSearchResult(
    @SerialName("lat")
    val latitude: String? = null,
    @SerialName("lon")
    val longitude: String? = null,
    @SerialName("name")
    val name: String? = null,
    val address: NominatimSearchAddress? = null,
)

@Serializable
private data class NominatimSearchAddress(
    val city: String? = null,
    val town: String? = null,
    val village: String? = null,
    val municipality: String? = null,
    val county: String? = null,
    val state: String? = null,
    val country: String? = null,
    @SerialName("country_code")
    val countryCode: String? = null,
)
