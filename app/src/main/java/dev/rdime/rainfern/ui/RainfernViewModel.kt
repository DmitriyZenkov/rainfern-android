package dev.rdime.rainfern.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.rdime.rainfern.data.model.DashboardPayload
import dev.rdime.rainfern.data.model.LocationMode
import dev.rdime.rainfern.data.model.PressureUnit
import dev.rdime.rainfern.data.model.RefreshInterval
import dev.rdime.rainfern.data.model.SavedPlace
import dev.rdime.rainfern.data.model.TemperatureUnit
import dev.rdime.rainfern.data.model.TimeFormatPreference
import dev.rdime.rainfern.data.model.WindUnit
import dev.rdime.rainfern.data.model.WidgetDiagnosticsMode
import dev.rdime.rainfern.data.model.WidgetThemeIntensity
import dev.rdime.rainfern.data.network.provider.defaultProviderCatalog
import dev.rdime.rainfern.data.repository.PlacesRepository
import dev.rdime.rainfern.data.repository.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RainfernUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val payload: DashboardPayload = DashboardPayload(),
    val placeSearchResults: List<SavedPlace> = emptyList(),
    val placeSearchInProgress: Boolean = false,
    val placeSearchError: String? = null,
    val featureIdeas: List<String> = listOf(
        "Pollen and air quality overlays from free public APIs.",
        "Travel mode with a route weather strip for the next few hours.",
        "Camera-aware sunrise and golden-hour planner.",
        "Radar or cloud map layer using open satellite tiles.",
        "Anomaly view that shows where providers disagree the most.",
        "Wear OS companion with rain-onset alerts.",
    ),
)

class RainfernViewModel(
    private val repository: WeatherRepository,
    private val placesRepository: PlacesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RainfernUiState())
    val uiState: StateFlow<RainfernUiState> = _uiState.asStateFlow()
    val providerCatalog = defaultProviderCatalog
    private var placeSearchJob: Job? = null
    private var placeSearchRequestId: Long = 0L

    init {
        viewModelScope.launch {
            val payload = repository.loadDashboard()
            _uiState.update { it.copy(loading = false, payload = payload) }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refreshContent()
        }
    }

    fun chooseLocationMode(mode: LocationMode) {
        viewModelScope.launch {
            repository.updateLocationMode(mode)
            refreshPayload()
        }
    }

    fun chooseRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch {
            repository.updateRefreshInterval(interval)
            refreshPayload()
        }
    }

    fun chooseBackgroundRefreshInterval(interval: RefreshInterval) {
        viewModelScope.launch {
            repository.updateBackgroundRefreshInterval(interval)
            refreshPayload()
        }
    }

    fun toggleBackgroundWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateBackgroundWifiOnly(enabled)
            refreshPayload()
        }
    }

    fun toggleBackgroundBatteryAware(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateBackgroundBatteryAware(enabled)
            refreshPayload()
        }
    }

    fun chooseTemperatureUnit(unit: TemperatureUnit) {
        viewModelScope.launch {
            repository.updateTemperatureUnit(unit)
            refreshPayload()
        }
    }

    fun chooseWindUnit(unit: WindUnit) {
        viewModelScope.launch {
            repository.updateWindUnit(unit)
            refreshPayload()
        }
    }

    fun choosePressureUnit(unit: PressureUnit) {
        viewModelScope.launch {
            repository.updatePressureUnit(unit)
            refreshPayload()
        }
    }

    fun chooseTimeFormatPreference(preference: TimeFormatPreference) {
        viewModelScope.launch {
            repository.updateTimeFormatPreference(preference)
            refreshPayload()
        }
    }

    fun chooseWidgetLocationKey(locationKey: String) {
        viewModelScope.launch {
            repository.updateWidgetLocationKey(locationKey)
            refreshPayload()
        }
    }

    fun chooseWidgetThemeIntensity(intensity: WidgetThemeIntensity) {
        viewModelScope.launch {
            repository.updateWidgetThemeIntensity(intensity)
            refreshPayload()
        }
    }

    fun chooseWidgetDiagnosticsMode(mode: WidgetDiagnosticsMode) {
        viewModelScope.launch {
            repository.updateWidgetDiagnosticsMode(mode)
            refreshPayload()
        }
    }

    fun toggleNotifyRainSoon(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotifyRainSoon(enabled)
            refreshPayload()
        }
    }

    fun toggleNotifyFreezing(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotifyFreezing(enabled)
            refreshPayload()
        }
    }

    fun toggleNotifyStrongWind(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotifyStrongWind(enabled)
            refreshPayload()
        }
    }

    fun toggleNotifyMorningSummary(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateNotifyMorningSummary(enabled)
            refreshPayload()
        }
    }

    fun toggleOpenMeteo(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateOpenMeteoEnabled(enabled)
            refreshPayload()
        }
    }

    fun toggleMetNorway(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateMetNorwayEnabled(enabled)
            refreshPayload()
        }
    }

    fun toggleWeatherGov(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateWeatherGovEnabled(enabled)
            refreshPayload()
        }
    }

    fun toggleWeatherApi(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateWeatherApiEnabled(enabled)
            refreshPayload()
        }
    }

    fun updateWeatherApiKey(key: String) {
        viewModelScope.launch {
            repository.updateWeatherApiKey(key)
            refreshPayload()
        }
    }

    fun markLocationPromptShown() {
        viewModelScope.launch {
            repository.markRequestShown()
            refreshPayload()
        }
    }

    fun searchPlaces(query: String) {
        val normalizedQuery = query.trim()
        placeSearchRequestId += 1
        val requestId = placeSearchRequestId
        if (normalizedQuery.length < 2) {
            placeSearchJob?.cancel()
            _uiState.update {
                it.copy(
                    placeSearchResults = emptyList(),
                    placeSearchInProgress = false,
                    placeSearchError = null,
                )
            }
            return
        }
        placeSearchJob?.cancel()
        placeSearchJob = viewModelScope.launch {
            _uiState.update { it.copy(placeSearchInProgress = true, placeSearchError = null) }
            val results = runCatching { placesRepository.searchPlaces(normalizedQuery) }.getOrElse { emptyList() }
            if (requestId != placeSearchRequestId) {
                return@launch
            }
            _uiState.update {
                it.copy(
                    placeSearchResults = results,
                    placeSearchInProgress = false,
                    placeSearchError = if (results.isEmpty()) "No places found for \"$normalizedQuery\"." else null,
                )
            }
        }
    }

    fun clearPlaceSearch() {
        placeSearchRequestId += 1
        placeSearchJob?.cancel()
        _uiState.update {
            it.copy(
                placeSearchResults = emptyList(),
                placeSearchInProgress = false,
                placeSearchError = null,
            )
        }
    }

    fun selectPlace(place: SavedPlace) {
        placeSearchRequestId += 1
        placeSearchJob?.cancel()
        viewModelScope.launch {
            placesRepository.selectPlace(place)
            _uiState.update {
                it.copy(
                    placeSearchResults = emptyList(),
                    placeSearchInProgress = false,
                    placeSearchError = null,
                )
            }
            refreshContent()
        }
    }

    fun toggleFavorite(place: SavedPlace) {
        viewModelScope.launch {
            placesRepository.toggleFavorite(place)
            refreshPayload()
        }
    }

    fun useDeviceLocation() {
        placeSearchRequestId += 1
        placeSearchJob?.cancel()
        viewModelScope.launch {
            placesRepository.useDeviceLocation()
            refreshContent()
        }
    }

    fun hasForegroundPermission(): Boolean = repository.hasForegroundLocationPermission()

    fun hasBackgroundPermission(): Boolean = repository.hasBackgroundLocationPermission()

    fun hasNotificationPermission(): Boolean = repository.hasNotificationPermission()

    private suspend fun refreshPayload() {
        val payload = repository.loadDashboard()
        _uiState.update { it.copy(payload = payload) }
    }

    private suspend fun refreshContent() {
        _uiState.update { it.copy(refreshing = true) }
        val payload = repository.refresh()
        _uiState.update { it.copy(refreshing = false, payload = payload) }
    }
}

class RainfernViewModelFactory(
    private val repository: WeatherRepository,
    private val placesRepository: PlacesRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RainfernViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RainfernViewModel(repository, placesRepository) as T
        }
        error("Unknown ViewModel class: ${modelClass.name}")
    }
}
