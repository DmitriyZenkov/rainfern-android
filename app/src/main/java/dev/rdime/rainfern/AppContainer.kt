package dev.rdime.rainfern

import android.content.Context
import dev.rdime.rainfern.data.environment.AirQualityRepository
import dev.rdime.rainfern.data.local.ForecastCacheStore
import dev.rdime.rainfern.data.local.PlacesStore
import dev.rdime.rainfern.data.local.SettingsStore
import dev.rdime.rainfern.data.location.DeviceLocationRepository
import dev.rdime.rainfern.data.network.ConnectivityMonitor
import dev.rdime.rainfern.data.network.provider.MetNorwayWeatherProvider
import dev.rdime.rainfern.data.network.provider.OpenMeteoWeatherProvider
import dev.rdime.rainfern.data.network.provider.WeatherApiComProvider
import dev.rdime.rainfern.data.network.provider.WeatherGovProvider
import dev.rdime.rainfern.data.network.provider.WeatherHttpClient
import dev.rdime.rainfern.data.repository.PlacesRepository
import dev.rdime.rainfern.data.repository.WeatherRepository
import dev.rdime.rainfern.data.search.GeocodingRepository
import dev.rdime.rainfern.data.search.ReverseGeocodingRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val httpClient = WeatherHttpClient()
    private val settingsStore = SettingsStore(appContext)
    private val cacheStore = ForecastCacheStore(appContext)
    private val placesStore = PlacesStore(appContext)
    private val locationRepository = DeviceLocationRepository(appContext)
    private val connectivityMonitor = ConnectivityMonitor(appContext)
    private val geocodingRepository = GeocodingRepository(httpClient)
    private val reverseGeocodingRepository = ReverseGeocodingRepository(httpClient)
    private val airQualityRepository = AirQualityRepository(httpClient)
    private val providers = listOf(
        OpenMeteoWeatherProvider(httpClient),
        MetNorwayWeatherProvider(httpClient),
        WeatherGovProvider(httpClient),
        WeatherApiComProvider(httpClient),
    )

    val placesRepository = PlacesRepository(
        placesStore = placesStore,
        cacheStore = cacheStore,
        geocodingRepository = geocodingRepository,
    )

    val weatherRepository = WeatherRepository(
        appContext = appContext,
        settingsStore = settingsStore,
        cacheStore = cacheStore,
        placesStore = placesStore,
        airQualityRepository = airQualityRepository,
        reverseGeocodingRepository = reverseGeocodingRepository,
        locationRepository = locationRepository,
        connectivityMonitor = connectivityMonitor,
        providers = providers,
    )
}
