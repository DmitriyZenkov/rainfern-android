package dev.rdime.rainfern.data.search

import dev.rdime.rainfern.data.model.Coordinates
import dev.rdime.rainfern.data.model.SavedPlace
import dev.rdime.rainfern.data.network.provider.WeatherHttpClient
import java.util.Locale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

class ReverseGeocodingRepository(
    private val httpClient: WeatherHttpClient,
) {
    suspend fun reverseGeocode(coordinates: Coordinates): SavedPlace? {
        val url = "https://nominatim.openstreetmap.org/reverse".toHttpUrl().newBuilder()
            .addQueryParameter("lat", coordinates.latitude.toString())
            .addQueryParameter("lon", coordinates.longitude.toString())
            .addQueryParameter("format", "jsonv2")
            .addQueryParameter("accept-language", Locale.getDefault().toLanguageTag())
            .build()
            .toString()
        val response = httpClient.getDecoded<NominatimReverseResponse>(
            url,
            headers = mapOf(
                "User-Agent" to "Rainfern/0.1 (personal-use Android weather app)",
                "Accept-Language" to Locale.getDefault().toLanguageTag(),
            ),
        ) ?: return null
        val address = response.address ?: return null
        val name = listOfNotNull(
            address.city,
            address.town,
            address.village,
            address.municipality,
            address.county,
            response.name,
        ).firstOrNull { it.isNotBlank() } ?: return null
        return SavedPlace(
            id = buildPlaceId(
                name = name,
                latitude = coordinates.latitude,
                longitude = coordinates.longitude,
                countryCode = address.countryCode.orEmpty(),
            ),
            name = name,
            adminArea = listOfNotNull(address.state, address.county).firstOrNull().orEmpty(),
            country = address.country.orEmpty(),
            countryCode = address.countryCode.orEmpty().uppercase(Locale.US),
            coordinates = coordinates,
        )
    }
}

@Serializable
private data class NominatimReverseResponse(
    val name: String? = null,
    val address: NominatimAddress? = null,
)

@Serializable
private data class NominatimAddress(
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
