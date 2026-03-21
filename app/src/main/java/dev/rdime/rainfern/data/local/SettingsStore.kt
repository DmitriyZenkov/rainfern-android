package dev.rdime.rainfern.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.rdime.rainfern.data.model.AppSettings
import dev.rdime.rainfern.data.model.LocationMode
import dev.rdime.rainfern.data.model.PressureUnit
import dev.rdime.rainfern.data.model.RefreshInterval
import dev.rdime.rainfern.data.model.TemperatureUnit
import dev.rdime.rainfern.data.model.TimeFormatPreference
import dev.rdime.rainfern.data.model.WindUnit
import dev.rdime.rainfern.data.model.WIDGET_ACTIVE_LOCATION_KEY
import dev.rdime.rainfern.data.model.WidgetDiagnosticsMode
import dev.rdime.rainfern.data.model.WidgetThemeIntensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsStore(context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile("rainfern_settings.preferences_pb") },
    )

    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            locationMode = prefs[Keys.LOCATION_MODE]?.let(LocationMode::valueOf) ?: LocationMode.ASK,
            refreshInterval = prefs[Keys.REFRESH_INTERVAL]?.let(RefreshInterval::valueOf)
                ?: RefreshInterval.HOURLY,
            backgroundRefreshInterval = prefs[Keys.BACKGROUND_REFRESH_INTERVAL]?.let(RefreshInterval::valueOf)
                ?: RefreshInterval.EVERY_3_HOURS,
            backgroundWifiOnly = prefs[Keys.BACKGROUND_WIFI_ONLY] ?: false,
            backgroundBatteryAware = prefs[Keys.BACKGROUND_BATTERY_AWARE] ?: true,
            temperatureUnit = prefs[Keys.TEMPERATURE_UNIT]?.let(TemperatureUnit::valueOf)
                ?: TemperatureUnit.CELSIUS,
            windUnit = prefs[Keys.WIND_UNIT]?.let(WindUnit::valueOf)
                ?: WindUnit.KPH,
            pressureUnit = prefs[Keys.PRESSURE_UNIT]?.let(PressureUnit::valueOf)
                ?: PressureUnit.HPA,
            timeFormatPreference = prefs[Keys.TIME_FORMAT_PREFERENCE]?.let(TimeFormatPreference::valueOf)
                ?: TimeFormatPreference.SYSTEM,
            widgetLocationKey = prefs[Keys.WIDGET_LOCATION_KEY] ?: WIDGET_ACTIVE_LOCATION_KEY,
            widgetThemeIntensity = prefs[Keys.WIDGET_THEME_INTENSITY]?.let(WidgetThemeIntensity::valueOf)
                ?: WidgetThemeIntensity.BALANCED,
            widgetDiagnosticsMode = prefs[Keys.WIDGET_DIAGNOSTICS_MODE]?.let(WidgetDiagnosticsMode::valueOf)
                ?: WidgetDiagnosticsMode.SOURCES,
            notifyRainSoon = prefs[Keys.NOTIFY_RAIN_SOON] ?: true,
            notifyFreezing = prefs[Keys.NOTIFY_FREEZING] ?: true,
            notifyStrongWind = prefs[Keys.NOTIFY_STRONG_WIND] ?: true,
            notifyMorningSummary = prefs[Keys.NOTIFY_MORNING_SUMMARY] ?: false,
            openMeteoEnabled = prefs[Keys.OPEN_METEO_ENABLED] ?: true,
            metNorwayEnabled = prefs[Keys.MET_NORWAY_ENABLED] ?: true,
            weatherGovEnabled = prefs[Keys.WEATHER_GOV_ENABLED] ?: true,
            weatherApiEnabled = prefs[Keys.WEATHER_API_ENABLED] ?: true,
            weatherApiKey = prefs[Keys.WEATHER_API_KEY] ?: "",
            requestShown = prefs[Keys.REQUEST_SHOWN] ?: false,
        )
    }

    suspend fun updateLocationMode(mode: LocationMode) {
        dataStore.edit { it[Keys.LOCATION_MODE] = mode.name }
    }

    suspend fun updateRefreshInterval(interval: RefreshInterval) {
        dataStore.edit { it[Keys.REFRESH_INTERVAL] = interval.name }
    }

    suspend fun updateBackgroundRefreshInterval(interval: RefreshInterval) {
        dataStore.edit { it[Keys.BACKGROUND_REFRESH_INTERVAL] = interval.name }
    }

    suspend fun updateBackgroundWifiOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_WIFI_ONLY] = enabled }
    }

    suspend fun updateBackgroundBatteryAware(enabled: Boolean) {
        dataStore.edit { it[Keys.BACKGROUND_BATTERY_AWARE] = enabled }
    }

    suspend fun updateTemperatureUnit(unit: TemperatureUnit) {
        dataStore.edit { it[Keys.TEMPERATURE_UNIT] = unit.name }
    }

    suspend fun updateWindUnit(unit: WindUnit) {
        dataStore.edit { it[Keys.WIND_UNIT] = unit.name }
    }

    suspend fun updatePressureUnit(unit: PressureUnit) {
        dataStore.edit { it[Keys.PRESSURE_UNIT] = unit.name }
    }

    suspend fun updateTimeFormatPreference(preference: TimeFormatPreference) {
        dataStore.edit { it[Keys.TIME_FORMAT_PREFERENCE] = preference.name }
    }

    suspend fun updateWidgetLocationKey(locationKey: String) {
        dataStore.edit { it[Keys.WIDGET_LOCATION_KEY] = locationKey.trim().ifBlank { WIDGET_ACTIVE_LOCATION_KEY } }
    }

    suspend fun updateWidgetThemeIntensity(intensity: WidgetThemeIntensity) {
        dataStore.edit { it[Keys.WIDGET_THEME_INTENSITY] = intensity.name }
    }

    suspend fun updateWidgetDiagnosticsMode(mode: WidgetDiagnosticsMode) {
        dataStore.edit { it[Keys.WIDGET_DIAGNOSTICS_MODE] = mode.name }
    }

    suspend fun updateNotifyRainSoon(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_RAIN_SOON] = enabled }
    }

    suspend fun updateNotifyFreezing(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_FREEZING] = enabled }
    }

    suspend fun updateNotifyStrongWind(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_STRONG_WIND] = enabled }
    }

    suspend fun updateNotifyMorningSummary(enabled: Boolean) {
        dataStore.edit { it[Keys.NOTIFY_MORNING_SUMMARY] = enabled }
    }

    suspend fun updateOpenMeteoEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.OPEN_METEO_ENABLED] = enabled }
    }

    suspend fun updateMetNorwayEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MET_NORWAY_ENABLED] = enabled }
    }

    suspend fun updateWeatherGovEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WEATHER_GOV_ENABLED] = enabled }
    }

    suspend fun updateWeatherApiEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.WEATHER_API_ENABLED] = enabled }
    }

    suspend fun updateWeatherApiKey(key: String) {
        dataStore.edit { it[Keys.WEATHER_API_KEY] = key.trim() }
    }

    suspend fun markRequestShown() {
        dataStore.edit { it[Keys.REQUEST_SHOWN] = true }
    }

    private object Keys {
        val LOCATION_MODE = stringPreferencesKey("location_mode")
        val REFRESH_INTERVAL = stringPreferencesKey("refresh_interval")
        val BACKGROUND_REFRESH_INTERVAL = stringPreferencesKey("background_refresh_interval")
        val BACKGROUND_WIFI_ONLY = booleanPreferencesKey("background_wifi_only")
        val BACKGROUND_BATTERY_AWARE = booleanPreferencesKey("background_battery_aware")
        val TEMPERATURE_UNIT = stringPreferencesKey("temperature_unit")
        val WIND_UNIT = stringPreferencesKey("wind_unit")
        val PRESSURE_UNIT = stringPreferencesKey("pressure_unit")
        val TIME_FORMAT_PREFERENCE = stringPreferencesKey("time_format_preference")
        val WIDGET_LOCATION_KEY = stringPreferencesKey("widget_location_key")
        val WIDGET_THEME_INTENSITY = stringPreferencesKey("widget_theme_intensity")
        val WIDGET_DIAGNOSTICS_MODE = stringPreferencesKey("widget_diagnostics_mode")
        val NOTIFY_RAIN_SOON = booleanPreferencesKey("notify_rain_soon")
        val NOTIFY_FREEZING = booleanPreferencesKey("notify_freezing")
        val NOTIFY_STRONG_WIND = booleanPreferencesKey("notify_strong_wind")
        val NOTIFY_MORNING_SUMMARY = booleanPreferencesKey("notify_morning_summary")
        val OPEN_METEO_ENABLED = booleanPreferencesKey("open_meteo_enabled")
        val MET_NORWAY_ENABLED = booleanPreferencesKey("met_norway_enabled")
        val WEATHER_GOV_ENABLED = booleanPreferencesKey("weather_gov_enabled")
        val WEATHER_API_ENABLED = booleanPreferencesKey("weather_api_enabled")
        val WEATHER_API_KEY = stringPreferencesKey("weather_api_key")
        val REQUEST_SHOWN = booleanPreferencesKey("request_shown")
    }
}
