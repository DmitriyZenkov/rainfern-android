package dev.rdime.rainfern.data.repository

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import dev.rdime.rainfern.data.aggregate.WeatherAggregator
import dev.rdime.rainfern.data.environment.AirQualityRepository
import dev.rdime.rainfern.data.local.ForecastCacheStore
import dev.rdime.rainfern.data.local.PlacesStore
import dev.rdime.rainfern.data.local.SettingsStore
import dev.rdime.rainfern.data.location.DeviceLocationRepository
import dev.rdime.rainfern.data.model.AppSettings
import dev.rdime.rainfern.data.model.DEVICE_LOCATION_KEY
import dev.rdime.rainfern.data.model.DashboardPayload
import dev.rdime.rainfern.data.model.ProviderForecast
import dev.rdime.rainfern.data.network.ConnectivityMonitor
import dev.rdime.rainfern.data.network.provider.WeatherProvider
import dev.rdime.rainfern.data.search.ReverseGeocodingRepository
import dev.rdime.rainfern.widget.RainfernWidgets
import dev.rdime.rainfern.work.RefreshScheduler
import java.time.ZoneId
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope

class WeatherRepository(
    private val appContext: Context,
    private val settingsStore: SettingsStore,
    private val cacheStore: ForecastCacheStore,
    private val placesStore: PlacesStore,
    private val airQualityRepository: AirQualityRepository,
    private val reverseGeocodingRepository: ReverseGeocodingRepository,
    private val locationRepository: DeviceLocationRepository,
    private val connectivityMonitor: ConnectivityMonitor,
    private val providers: List<WeatherProvider>,
) {
    suspend fun loadDashboard(): DashboardPayload {
        val settings = settingsStore.settings.first()
        RefreshScheduler.sync(appContext, settings)
        val places = placesStore.read()
        val activeLocationKey = places.activePlace?.id ?: DEVICE_LOCATION_KEY
        val cache = cacheStore.read().copy(activeLocationKey = activeLocationKey)
        return DashboardPayload(
            cache = cache,
            places = places,
            settings = settings,
            offline = !connectivityMonitor.isOnline(),
            lastError = when {
                cache.latest == null && places.activePlace != null && !connectivityMonitor.isOnline() ->
                    "Offline. No cached weather for ${places.activePlace.label} yet."
                cache.latest == null && places.activePlace == null && !locationRepository.hasForegroundPermission() ->
                    "Location permission is required for live weather, or pick a place manually."
                else -> null
            },
        )
    }

    suspend fun refresh(): DashboardPayload {
        val settings = settingsStore.settings.first()
        val places = placesStore.read()
        val activePlace = places.activePlace
        val activeLocationKey = activePlace?.id ?: DEVICE_LOCATION_KEY
        val cache = cacheStore.read().copy(activeLocationKey = activeLocationKey)
        val targetTimeZoneId = activePlace?.timeZoneId?.takeIf(String::isNotBlank) ?: ZoneId.systemDefault().id

        val coordinates = activePlace?.coordinates ?: locationRepository.getCurrentLocation() ?: cache.lastCoordinates
        if (coordinates == null) {
            return DashboardPayload(
                cache = cache,
                places = places,
                settings = settings,
                offline = !connectivityMonitor.isOnline(),
                lastError = if (activePlace != null) {
                    "No usable coordinates for ${activePlace.label}."
                } else {
                    "Grant location access or pick a place manually to seed a forecast cache."
                },
            )
        }

        if (!connectivityMonitor.isOnline()) {
            return DashboardPayload(
                cache = cache,
                places = places,
                settings = settings,
                offline = true,
                lastError = if (cache.latest == null) {
                    "Offline. No cached weather for ${activePlace?.label ?: "the current device location"} yet."
                } else {
                    "Offline. Showing cached history and the last blended forecast."
                },
            )
        }

        val fetchedProviders = fetchProviders(settings, coordinates, targetTimeZoneId).map { provider ->
            if (activePlace == null) {
                provider
            } else {
                provider.copy(
                    locationName = activePlace.label,
                    timeZoneId = activePlace.timeZoneId.ifBlank { provider.timeZoneId },
                )
            }
        }
        val environmental = runCatching { airQualityRepository.fetch(coordinates) }.getOrNull()
        val aggregated = WeatherAggregator.aggregate(fetchedProviders, coordinates, targetTimeZoneId = targetTimeZoneId)
        val reverseGeocodedLabel = if (activePlace == null) {
            runCatching { reverseGeocodingRepository.reverseGeocode(coordinates)?.label }.getOrNull()
        } else {
            null
        }

        if (aggregated == null) {
            return DashboardPayload(
                cache = cache,
                places = places,
                settings = settings,
                offline = false,
                lastError = "No provider returned usable weather data.",
            )
        }

        val displayForecast = aggregated.copy(
            locationName = activePlace?.label ?: reverseGeocodedLabel ?: aggregated.locationName,
            airQuality = environmental?.airQuality,
            pollen = environmental?.pollen,
        )
        cacheStore.writeLatest(
            latest = displayForecast,
            providers = fetchedProviders,
            coordinates = coordinates,
            locationKey = activeLocationKey,
            locationLabel = displayForecast.locationName,
        )
        RainfernWidgets.updateAll(appContext)
        return DashboardPayload(
            cache = cacheStore.read().copy(activeLocationKey = activeLocationKey),
            places = placesStore.read(),
            settings = settings,
            offline = false,
            lastError = null,
        )
    }

    suspend fun updateLocationMode(mode: dev.rdime.rainfern.data.model.LocationMode) {
        settingsStore.updateLocationMode(mode)
        RefreshScheduler.sync(appContext, settingsStore.settings.first())
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateRefreshInterval(interval: dev.rdime.rainfern.data.model.RefreshInterval) {
        settingsStore.updateRefreshInterval(interval)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateBackgroundRefreshInterval(interval: dev.rdime.rainfern.data.model.RefreshInterval) {
        settingsStore.updateBackgroundRefreshInterval(interval)
        RefreshScheduler.sync(appContext, settingsStore.settings.first())
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateBackgroundWifiOnly(enabled: Boolean) {
        settingsStore.updateBackgroundWifiOnly(enabled)
        RefreshScheduler.sync(appContext, settingsStore.settings.first())
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateBackgroundBatteryAware(enabled: Boolean) {
        settingsStore.updateBackgroundBatteryAware(enabled)
        RefreshScheduler.sync(appContext, settingsStore.settings.first())
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateTemperatureUnit(unit: dev.rdime.rainfern.data.model.TemperatureUnit) {
        settingsStore.updateTemperatureUnit(unit)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateWindUnit(unit: dev.rdime.rainfern.data.model.WindUnit) {
        settingsStore.updateWindUnit(unit)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updatePressureUnit(unit: dev.rdime.rainfern.data.model.PressureUnit) {
        settingsStore.updatePressureUnit(unit)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateTimeFormatPreference(preference: dev.rdime.rainfern.data.model.TimeFormatPreference) {
        settingsStore.updateTimeFormatPreference(preference)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateWidgetLocationKey(locationKey: String) {
        settingsStore.updateWidgetLocationKey(locationKey)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateWidgetThemeIntensity(intensity: dev.rdime.rainfern.data.model.WidgetThemeIntensity) {
        settingsStore.updateWidgetThemeIntensity(intensity)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateWidgetDiagnosticsMode(mode: dev.rdime.rainfern.data.model.WidgetDiagnosticsMode) {
        settingsStore.updateWidgetDiagnosticsMode(mode)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateNotifyRainSoon(enabled: Boolean) {
        settingsStore.updateNotifyRainSoon(enabled)
    }

    suspend fun updateNotifyFreezing(enabled: Boolean) {
        settingsStore.updateNotifyFreezing(enabled)
    }

    suspend fun updateNotifyStrongWind(enabled: Boolean) {
        settingsStore.updateNotifyStrongWind(enabled)
    }

    suspend fun updateNotifyMorningSummary(enabled: Boolean) {
        settingsStore.updateNotifyMorningSummary(enabled)
    }

    suspend fun updateOpenMeteoEnabled(enabled: Boolean) {
        settingsStore.updateOpenMeteoEnabled(enabled)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateMetNorwayEnabled(enabled: Boolean) {
        settingsStore.updateMetNorwayEnabled(enabled)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateWeatherGovEnabled(enabled: Boolean) {
        settingsStore.updateWeatherGovEnabled(enabled)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateWeatherApiEnabled(enabled: Boolean) {
        settingsStore.updateWeatherApiEnabled(enabled)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun updateWeatherApiKey(key: String) {
        settingsStore.updateWeatherApiKey(key)
        RainfernWidgets.updateAll(appContext)
    }

    suspend fun markRequestShown() {
        settingsStore.markRequestShown()
        RainfernWidgets.updateAll(appContext)
    }

    fun hasForegroundLocationPermission(): Boolean = locationRepository.hasForegroundPermission()

    fun hasBackgroundLocationPermission(): Boolean = locationRepository.hasBackgroundPermission()

    fun hasNotificationPermission(): Boolean = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> true
        else -> ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private suspend fun fetchProviders(
        settings: AppSettings,
        coordinates: dev.rdime.rainfern.data.model.Coordinates,
        targetTimeZoneId: String,
    ): List<ProviderForecast> = supervisorScope {
        providers.map { provider ->
            async {
                runCatching { provider.fetch(coordinates, settings, targetTimeZoneId) }.getOrNull()
            }
        }.mapNotNull { it.await() }
    }
}
