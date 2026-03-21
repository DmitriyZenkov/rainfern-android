package dev.rdime.rainfern.data.repository

import dev.rdime.rainfern.data.local.ForecastCacheStore
import dev.rdime.rainfern.data.local.PlacesStore
import dev.rdime.rainfern.data.model.DEVICE_LOCATION_KEY
import dev.rdime.rainfern.data.model.PlacesState
import dev.rdime.rainfern.data.model.SavedPlace
import dev.rdime.rainfern.data.search.GeocodingRepository
import java.time.Instant

class PlacesRepository(
    private val placesStore: PlacesStore,
    private val cacheStore: ForecastCacheStore,
    private val geocodingRepository: GeocodingRepository,
) {
    suspend fun loadPlaces(): PlacesState = placesStore.read()

    suspend fun searchPlaces(query: String): List<SavedPlace> = geocodingRepository.searchPlaces(query)

    suspend fun selectPlace(place: SavedPlace): PlacesState {
        val current = placesStore.read()
        val existing = current.savedPlaces.firstOrNull { it.id == place.id }
        val selected = place.copy(
            isFavorite = existing?.isFavorite ?: place.isFavorite,
            lastSelectedAt = Instant.now(),
        )
        val next = current.copy(
            activePlaceId = selected.id,
            savedPlaces = upsert(current.savedPlaces, selected),
        )
        placesStore.write(next)
        cacheStore.setActiveLocation(selected.id, selected.label)
        return next
    }

    suspend fun toggleFavorite(place: SavedPlace): PlacesState {
        val current = placesStore.read()
        val existing = current.savedPlaces.firstOrNull { it.id == place.id }
        val base = existing ?: place
        val nextPlace = base.copy(isFavorite = !base.isFavorite)
        val next = current.copy(savedPlaces = upsert(current.savedPlaces, nextPlace))
        placesStore.write(next)
        return next
    }

    suspend fun useDeviceLocation(): PlacesState {
        val current = placesStore.read()
        val next = current.copy(activePlaceId = null)
        placesStore.write(next)
        cacheStore.setActiveLocation(DEVICE_LOCATION_KEY)
        return next
    }

    private fun upsert(
        places: List<SavedPlace>,
        place: SavedPlace,
    ): List<SavedPlace> = listOf(place) + places.filterNot { it.id == place.id }
}
