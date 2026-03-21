package dev.rdime.rainfern.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Foggy
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.rdime.rainfern.data.model.Coordinates
import dev.rdime.rainfern.data.model.CurrentWeather
import dev.rdime.rainfern.data.model.DailyWeather
import dev.rdime.rainfern.data.model.AirQualitySnapshot
import dev.rdime.rainfern.data.model.ForecastMetric
import dev.rdime.rainfern.data.model.ForecastAnomaly
import dev.rdime.rainfern.data.model.HourlyWeather
import dev.rdime.rainfern.data.model.MetricConfidence
import dev.rdime.rainfern.data.model.MetricDisagreement
import dev.rdime.rainfern.data.model.PollenSnapshot
import dev.rdime.rainfern.data.model.PressureUnit
import dev.rdime.rainfern.data.model.WeatherAlert
import dev.rdime.rainfern.data.model.WeatherCondition
import dev.rdime.rainfern.ui.theme.FernMist
import dev.rdime.rainfern.data.model.LocationMode
import dev.rdime.rainfern.data.model.ProviderForecast
import dev.rdime.rainfern.data.model.ProviderId
import dev.rdime.rainfern.data.model.RefreshInterval
import dev.rdime.rainfern.data.model.SavedPlace
import dev.rdime.rainfern.data.model.SlotDiagnostics
import dev.rdime.rainfern.data.model.TemperatureUnit
import dev.rdime.rainfern.data.model.TimeFormatPreference
import dev.rdime.rainfern.data.model.WindUnit
import dev.rdime.rainfern.data.model.WIDGET_ACTIVE_LOCATION_KEY
import dev.rdime.rainfern.data.model.WidgetDiagnosticsMode
import dev.rdime.rainfern.data.model.WidgetThemeIntensity
import dev.rdime.rainfern.ui.theme.AlertRed
import dev.rdime.rainfern.ui.theme.CardNight
import dev.rdime.rainfern.ui.theme.CloudSilver
import dev.rdime.rainfern.ui.theme.DeepPine
import dev.rdime.rainfern.ui.theme.HorizonTeal
import dev.rdime.rainfern.ui.theme.MistGlow
import dev.rdime.rainfern.ui.theme.Moss
import dev.rdime.rainfern.ui.theme.RainBlue
import dev.rdime.rainfern.ui.theme.StormWash
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private enum class Destination(
    val route: String,
    val label: String,
) {
    FORECAST("forecast", "Forecast"),
    RADAR("radar", "Radar"),
    PLACES("places", "Places"),
    SOURCES("sources", "Sources"),
    HISTORY("history", "History"),
    SETTINGS("settings", "Settings"),
}

private enum class ComparisonMode {
    CURRENT,
    HOURLY,
    DAILY,
}

@Composable
fun RainfernApp(
    viewModel: RainfernViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.refresh()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { }

    LaunchedEffect(
        state.payload.cache.latest?.fetchedAt,
        state.payload.places.activePlace?.id,
        state.payload.settings.refreshInterval,
        state.refreshing,
        viewModel.hasForegroundPermission(),
    ) {
        val latest = state.payload.cache.latest
        val canResolveTarget = state.payload.places.activePlace != null || viewModel.hasForegroundPermission()
        val staleOnOpen = latest == null || Duration.between(latest.fetchedAt, Instant.now()).toMinutes() >= state.payload.settings.refreshInterval.minutes
        if (!state.refreshing && staleOnOpen && canResolveTarget) {
            viewModel.refresh()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        AtmosphericBackdrop()
        Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            bottomBar = {
                NavigationBar(containerColor = StormWash.copy(alpha = 0.78f)) {
                    Destination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Text(destination.label.take(1), fontWeight = FontWeight.Bold) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            },
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = Destination.FORECAST.route,
                modifier = Modifier.padding(paddingValues),
            ) {
                composable(Destination.FORECAST.route) {
                    ForecastScreen(
                        state = state,
                        onRefresh = viewModel::refresh,
                        onRequestForegroundLocation = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                        onChooseLocationMode = { mode ->
                            viewModel.chooseLocationMode(mode)
                            viewModel.markLocationPromptShown()
                            if (mode != LocationMode.ASK && !viewModel.hasForegroundPermission()) {
                                locationPermissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION,
                                    ),
                                )
                            }
                        },
                        onOpenPlaces = {
                            viewModel.markLocationPromptShown()
                            navController.navigate(Destination.PLACES.route) {
                                launchSingleTop = true
                            }
                        },
                        onDeferOnboarding = viewModel::markLocationPromptShown,
                    )
                }
                composable(Destination.PLACES.route) {
                    PlacesScreen(
                        state = state,
                        onSearchPlaces = viewModel::searchPlaces,
                        onClearSearch = viewModel::clearPlaceSearch,
                        onSelectPlace = viewModel::selectPlace,
                        onToggleFavorite = viewModel::toggleFavorite,
                        onUseDeviceLocation = viewModel::useDeviceLocation,
                        onRefresh = viewModel::refresh,
                    )
                }
                composable(Destination.RADAR.route) {
                    MapScreen(state = state)
                }
                composable(Destination.SOURCES.route) {
                    SourcesScreen(state = state, catalog = viewModel.providerCatalog)
                }
                composable(Destination.HISTORY.route) {
                    HistoryScreen(state = state)
                }
                composable(Destination.SETTINGS.route) {
                    SettingsScreen(
                        state = state,
                        onChooseLocationMode = viewModel::chooseLocationMode,
                        onChooseRefreshInterval = viewModel::chooseRefreshInterval,
                        onChooseBackgroundRefreshInterval = viewModel::chooseBackgroundRefreshInterval,
                        onToggleBackgroundWifiOnly = viewModel::toggleBackgroundWifiOnly,
                        onToggleBackgroundBatteryAware = viewModel::toggleBackgroundBatteryAware,
                        onChooseTemperatureUnit = viewModel::chooseTemperatureUnit,
                        onChooseWindUnit = viewModel::chooseWindUnit,
                        onChoosePressureUnit = viewModel::choosePressureUnit,
                        onChooseTimeFormatPreference = viewModel::chooseTimeFormatPreference,
                        onChooseWidgetLocationKey = viewModel::chooseWidgetLocationKey,
                        onChooseWidgetThemeIntensity = viewModel::chooseWidgetThemeIntensity,
                        onChooseWidgetDiagnosticsMode = viewModel::chooseWidgetDiagnosticsMode,
                        onToggleNotifyRainSoon = viewModel::toggleNotifyRainSoon,
                        onToggleNotifyFreezing = viewModel::toggleNotifyFreezing,
                        onToggleNotifyStrongWind = viewModel::toggleNotifyStrongWind,
                        onToggleNotifyMorningSummary = viewModel::toggleNotifyMorningSummary,
                        onToggleOpenMeteo = viewModel::toggleOpenMeteo,
                        onToggleMetNorway = viewModel::toggleMetNorway,
                        onToggleWeatherGov = viewModel::toggleWeatherGov,
                        onToggleWeatherApi = viewModel::toggleWeatherApi,
                        onWeatherApiKeyChanged = viewModel::updateWeatherApiKey,
                        onRequestForegroundLocation = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                        },
                        onRequestNotificationPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onOpenAppSettings = {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        },
                        hasForegroundPermission = viewModel.hasForegroundPermission(),
                        hasBackgroundPermission = viewModel.hasBackgroundPermission(),
                        hasNotificationPermission = viewModel.hasNotificationPermission(),
                    )
                }
            }
        }
    }
}

@Composable
private fun AtmosphericBackdrop() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(DeepPine, StormWash, CardNight.copy(alpha = 0.96f)),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(MistGlow.copy(alpha = 0.24f), Color.Transparent),
                        center = Offset(900f, 180f),
                        radius = 760f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(HorizonTeal.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(180f, 1380f),
                        radius = 900f,
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color.Transparent, RainBlue.copy(alpha = 0.08f), Moss.copy(alpha = 0.10f), Color.Transparent),
                        start = Offset(0f, 520f),
                        end = Offset(1080f, 1620f),
                    ),
                ),
        )
    }
}

@Composable
private fun ForecastScreen(
    state: RainfernUiState,
    onRefresh: () -> Unit,
    onRequestForegroundLocation: () -> Unit,
    onChooseLocationMode: (LocationMode) -> Unit,
    onOpenPlaces: () -> Unit,
    onDeferOnboarding: () -> Unit,
) {
    val latest = state.payload.cache.latest
    val activePlace = state.payload.places.activePlace
    var showAdvancedDetails by rememberSaveable(latest?.fetchedAt?.toEpochMilli()) { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            HeaderRow(
                title = "Rainfern",
                subtitle = latest?.locationName ?: "Blended forecast",
                onRefresh = onRefresh,
                refreshing = state.refreshing,
            )
        }

        if (state.payload.offline) {
            item { StatusBanner("Offline. Showing cached weather.", AlertRed) }
            state.payload.cache.latest?.let { latest ->
                item {
                    OfflineStatusCard(
                        latest = latest,
                        providers = state.payload.cache.providers,
                        settings = state.payload.settings,
                    )
                }
            }
        }
        state.payload.lastError?.let { message ->
            item { StatusBanner(message, AlertRed.copy(alpha = 0.84f)) }
        }

        if (latest == null) {
            item {
                if (activePlace != null) {
                    ManualPlaceEmptyCard(place = activePlace, offline = state.payload.offline)
                } else {
                    EmptyStateCard(
                        requestShown = state.payload.settings.requestShown,
                        onRequestForegroundLocation = onRequestForegroundLocation,
                        onChooseLocationMode = onChooseLocationMode,
                        onOpenPlaces = onOpenPlaces,
                        onDecideLater = onDeferOnboarding,
                    )
                }
            }
        } else {
            if (state.payload.settings.locationMode == LocationMode.ASK && !state.payload.settings.requestShown) {
                item {
                    OnboardingLocationCard(
                        onChooseLocationMode = onChooseLocationMode,
                        onOpenPlaces = onOpenPlaces,
                        onDecideLater = onDeferOnboarding,
                    )
                }
            }

            item {
                HeroCard(
                    current = latest.current,
                    confidence = latest.details.overallConfidence,
                    providerCount = latest.details.activeProviderIds.size,
                    metricConfidence = latest.details.currentMetrics,
                    settings = state.payload.settings,
                )
            }
            item {
                PrecipitationInsightCard(
                    current = latest.current,
                    hourly = latest.hourly,
                )
            }
            latest.details.anomalies.takeIf { it.isNotEmpty() }?.let { anomalies ->
                item {
                    ForecastAnomaliesCard(anomalies = anomalies)
                }
            }
            item {
                ActivitySuitabilityCard(
                    current = latest.current,
                    hourly = latest.hourly,
                    daily = latest.daily,
                    airQuality = latest.airQuality,
                    alerts = latest.alerts,
                    timeZoneId = latest.timeZoneId,
                    settings = state.payload.settings,
                )
            }
            item {
                ProgressiveDetailsCard(
                    airQuality = latest.airQuality,
                    today = latest.daily.firstOrNull(),
                    timeZoneId = latest.timeZoneId,
                    settings = state.payload.settings,
                    expanded = showAdvancedDetails,
                    onToggle = { showAdvancedDetails = !showAdvancedDetails },
                )
            }
            if (showAdvancedDetails) {
                item {
                    AirQualityCard(
                        airQuality = latest.airQuality,
                        pollen = latest.pollen,
                    )
                }
                item {
                    SkyDetailCard(
                        current = latest.current,
                        today = latest.daily.firstOrNull(),
                        airQuality = latest.airQuality,
                        timeZoneId = latest.timeZoneId,
                        settings = state.payload.settings,
                    )
                }
            }

            if (latest.alerts.isNotEmpty()) {
                item { SectionTitle("Priority Alert") }
                item { AlertCard(latest.alerts.first(), highlighted = true) }
                if (latest.alerts.size > 1) {
                    item { SectionTitle("Other Alerts") }
                }
                items(latest.alerts.drop(1).take(2)) { alert ->
                    AlertCard(alert, highlighted = false)
                }
            }

            item { SectionTitle("Hourly Blend") }
            item { HourlyStrip(hourly = latest.hourly, settings = state.payload.settings, timeZoneId = latest.timeZoneId) }
            item { SectionTitle("Daily Blend") }
            items(latest.daily) { day ->
                DailyCard(day, settings = state.payload.settings)
            }
        }
    }
}

@Composable
private fun PlacesScreen(
    state: RainfernUiState,
    onSearchPlaces: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectPlace: (SavedPlace) -> Unit,
    onToggleFavorite: (SavedPlace) -> Unit,
    onUseDeviceLocation: () -> Unit,
    onRefresh: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val places = state.payload.places
    val activePlace = places.activePlace
    val savedPlaces = places.savedPlaces.associateBy { it.id }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionIntro(
                title = "Places",
                body = "Search any city manually, keep favorites, and switch the blended forecast target without depending on GPS.",
            )
        }
        item {
            ActivePlaceCard(
                activePlace = activePlace,
                onUseDeviceLocation = onUseDeviceLocation,
                onRefresh = onRefresh,
            )
        }
        item {
            PlaceSearchCard(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                searching = state.placeSearchInProgress,
                error = state.placeSearchError,
                onSearch = { onSearchPlaces(searchQuery) },
                onClear = {
                    searchQuery = ""
                    onClearSearch()
                },
            )
        }
        if (state.placeSearchResults.isNotEmpty()) {
            item { SectionTitle("Search Results") }
            items(state.placeSearchResults) { result ->
                val stored = savedPlaces[result.id]
                PlaceCard(
                    place = stored ?: result,
                    isActive = activePlace?.id == result.id,
                    onSelectPlace = { onSelectPlace(stored ?: result) },
                    onToggleFavorite = { onToggleFavorite(stored ?: result) },
                )
            }
        }
        if (places.favoritePlaces.isNotEmpty()) {
            item { SectionTitle("Favorites") }
            items(places.favoritePlaces) { place ->
                PlaceCard(
                    place = place,
                    isActive = activePlace?.id == place.id,
                    onSelectPlace = { onSelectPlace(place) },
                    onToggleFavorite = { onToggleFavorite(place) },
                )
            }
        }
        val recents = places.recentPlaces.filterNot { it.isFavorite }
        if (recents.isNotEmpty()) {
            item { SectionTitle("Recent Places") }
            items(recents) { place ->
                PlaceCard(
                    place = place,
                    isActive = activePlace?.id == place.id,
                    onSelectPlace = { onSelectPlace(place) },
                    onToggleFavorite = { onToggleFavorite(place) },
                )
            }
        }
    }
}

@Composable
private fun MapScreen(
    state: RainfernUiState,
) {
    val coordinates = state.payload.cache.latest?.coordinates
        ?: state.payload.places.activePlace?.coordinates
        ?: state.payload.cache.lastCoordinates
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionIntro(
                title = "Radar Map",
                body = "Rainfern keeps maps lightweight: this view centers on your active place and shows recent precipitation radar from a free personal-use source.",
            )
        }
        if (coordinates == null) {
            item {
                StatusBanner(
                    text = "A location is needed before the radar map can center itself. Pick a place or fetch one device-location forecast first.",
                    color = CloudSilver.copy(alpha = 0.26f),
                )
            }
        } else {
            item {
                RadarMapCard(coordinates = coordinates)
            }
            item {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Map source notes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "RainViewer is free for personal use, limited to recent past radar frames, and caps tile access to zoom level 7. Rainfern uses it as an interpretive precipitation layer, not as another forecast provider.",
                            color = CloudSilver,
                        )
                        Text(
                            "OpenStreetMap supplies the base map tiles. The location marker stays on the blended forecast target.",
                            color = FernMist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcesScreen(
    state: RainfernUiState,
    catalog: List<dev.rdime.rainfern.data.network.provider.ProviderInfo>,
) {
    val activeProviders = state.payload.cache.providers.associateBy { it.providerId }
    val details = state.payload.cache.latest?.details
    var comparisonMode by rememberSaveable { mutableStateOf(ComparisonMode.CURRENT) }
    var selectedHourlyIndex by rememberSaveable { mutableStateOf(0) }
    var selectedDailyIndex by rememberSaveable { mutableStateOf(0) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionIntro(
                title = "Source Transparency",
                body = "Rainfern shows the blended forecast by default, but every provider stays visible with its own values and confidence.",
            )
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Aggregation algorithm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Weighted median for temperature and wind, weighted mean for precipitation and cloud metrics, weighted mode for condition labels. Regional official sources receive a location bonus. Freshness, completeness, and disagreement reduce confidence.",
                        color = CloudSilver,
                    )
                    details?.let {
                        Text(
                            "Regional calibration: ${it.regionalProfile.name.replace('_', ' ')}",
                            color = FernMist,
                        )
                    }
                }
            }
        }
        state.payload.cache.latest?.let { latest ->
            item {
                ForecastComparisonCard(
                    blended = latest,
                    providers = state.payload.cache.providers,
                    settings = state.payload.settings,
                    timeZoneId = latest.timeZoneId,
                    comparisonMode = comparisonMode,
                    onModeChange = { comparisonMode = it },
                    selectedHourlyIndex = selectedHourlyIndex,
                    onSelectHourlyIndex = { selectedHourlyIndex = it },
                    selectedDailyIndex = selectedDailyIndex,
                    onSelectDailyIndex = { selectedDailyIndex = it },
                )
            }
        }
        details?.takeIf { it.currentDisagreements.isNotEmpty() }?.let { diagnostics ->
            item {
                DisagreementOverviewCard(disagreements = diagnostics.currentDisagreements)
            }
        }
        details?.takeIf { it.currentMetrics.isNotEmpty() }?.let { diagnostics ->
            item {
                MetricConfidenceOverviewCard(metricConfidence = diagnostics.currentMetrics)
            }
        }
        details?.hourlyDiagnostics
            ?.sortedByDescending(::slotConflictScore)
            ?.take(4)
            ?.takeIf { it.isNotEmpty() }
            ?.let { slots ->
                item { SectionTitle("Most disputed hours") }
                items(slots) { slot ->
                    DisagreementSlotCard(
                        slot = slot,
                        label = formatSlotLabel(slot, state.payload.settings, state.payload.cache.latest?.timeZoneId),
                    )
                }
            }
        details?.dailyDiagnostics
            ?.sortedByDescending(::slotConflictScore)
            ?.take(4)
            ?.takeIf { it.isNotEmpty() }
            ?.let { slots ->
                item { SectionTitle("Most disputed days") }
                items(slots) { slot ->
                    DisagreementSlotCard(
                        slot = slot,
                        label = formatSlotLabel(slot, state.payload.settings, state.payload.cache.latest?.timeZoneId),
                    )
                }
            }
        item { SectionTitle("Forecast providers") }
        items(catalog) { info ->
            ProviderCard(
                info = info,
                provider = activeProviders[info.id],
                settings = state.payload.settings,
                enabled = when (info.id) {
                    ProviderId.OPEN_METEO -> state.payload.settings.openMeteoEnabled
                    ProviderId.MET_NORWAY -> state.payload.settings.metNorwayEnabled
                    ProviderId.WEATHER_GOV -> state.payload.settings.weatherGovEnabled
                    ProviderId.WEATHER_API -> state.payload.settings.weatherApiEnabled
                },
            )
        }
        item { SectionTitle("Supporting data services") }
        items(supportingSourceMetadata()) { metadata ->
            SupportingSourceCard(metadata = metadata)
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun RadarMapCard(
    coordinates: Coordinates,
) {
    val html = buildRadarMapHtml(
        latitude = coordinates.latitude,
        longitude = coordinates.longitude,
    )
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Recent precipitation radar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${"%.3f".format(coordinates.latitude)}, ${"%.3f".format(coordinates.longitude)}",
                color = FernMist,
            )
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                factory = { context ->
                    WebView(context).apply {
                        val targetTag = "${coordinates.latitude}:${coordinates.longitude}"
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.loadsImagesAutomatically = true
                        settings.allowContentAccess = false
                        settings.allowFileAccess = false
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        tag = targetTag
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        loadDataWithBaseURL("https://www.rainviewer.com", html, "text/html", "utf-8", null)
                    }
                },
                update = { webView ->
                    val targetTag = "${coordinates.latitude}:${coordinates.longitude}"
                    if (webView.tag != targetTag) {
                        webView.tag = targetTag
                        webView.loadDataWithBaseURL("https://www.rainviewer.com", html, "text/html", "utf-8", null)
                    }
                },
            )
        }
    }
}

@Composable
private fun OfflineStatusCard(
    latest: dev.rdime.rainfern.data.model.AggregatedForecast,
    providers: List<ProviderForecast>,
    settings: dev.rdime.rainfern.data.model.AppSettings,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = AlertRed.copy(alpha = 0.84f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cached forecast details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Last successful blend ${formatHistoryTimestamp(latest.fetchedAt, settings, latest.timeZoneId)} (${relativeAge(latest.fetchedAt)} old).",
                color = FernMist,
            )
            Text(
                "Providers in cache: ${providers.map { it.providerName }.distinct().ifEmpty { listOf("Unknown") }.joinToString(", ")}",
                color = FernMist,
            )
        }
    }
}

@Composable
private fun HistoryTrendCard(
    history: List<dev.rdime.rainfern.data.model.SnapshotRecord>,
    timeZoneId: String?,
) {
    val latest = history.firstOrNull() ?: return
    val zone = resolveUiZoneId(timeZoneId)
    val latestDate = latest.capturedAt.atZone(zone).toLocalDate()
    val morningBaseline = history.drop(1).lastOrNull { it.capturedAt.atZone(zone).toLocalDate() == latestDate }
    val yesterdayBaseline = history.drop(1).firstOrNull { it.capturedAt.atZone(zone).toLocalDate().isBefore(latestDate) }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Forecast recall", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            morningBaseline?.let {
                Text("Since this morning: ${snapshotDeltaLine(latest, it)}", color = CloudSilver)
            }
            yesterdayBaseline?.let {
                Text("Since yesterday: ${snapshotDeltaLine(latest, it)}", color = FernMist)
            }
        }
    }
}

@Composable
private fun HistoryScreen(
    state: RainfernUiState,
) {
    val history = state.payload.cache.history
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionIntro(
                title = "Offline History",
                body = "Every successful blended refresh is saved locally. If the device is offline, Rainfern surfaces the last forecast and this snapshot history.",
            )
        }
        if (state.payload.offline) {
            state.payload.cache.latest?.let { latest ->
                item {
                    OfflineStatusCard(
                        latest = latest,
                        providers = state.payload.cache.providers,
                        settings = state.payload.settings,
                    )
                }
            }
        }
        if (history.size >= 2) {
            item { HistoryTrendCard(history = history, timeZoneId = state.payload.cache.latest?.timeZoneId) }
        }
        if (history.isEmpty()) {
                item { StatusBanner("No history yet. Refresh once online to seed local snapshots.", CloudSilver.copy(alpha = 0.26f)) }
        } else {
            items(history) { record ->
                HistoryCard(
                    locationName = record.locationName,
                    summary = record.summary,
                    temperatureLabel = formatTemperature(record.currentTempC, state.payload.settings),
                    capturedAtMillis = record.capturedAt.toEpochMilli(),
                    settings = state.payload.settings,
                    timeZoneId = state.payload.cache.latest?.timeZoneId,
                )
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    state: RainfernUiState,
    onChooseLocationMode: (LocationMode) -> Unit,
    onChooseRefreshInterval: (RefreshInterval) -> Unit,
    onChooseBackgroundRefreshInterval: (RefreshInterval) -> Unit,
    onToggleBackgroundWifiOnly: (Boolean) -> Unit,
    onToggleBackgroundBatteryAware: (Boolean) -> Unit,
    onChooseTemperatureUnit: (TemperatureUnit) -> Unit,
    onChooseWindUnit: (WindUnit) -> Unit,
    onChoosePressureUnit: (PressureUnit) -> Unit,
    onChooseTimeFormatPreference: (TimeFormatPreference) -> Unit,
    onChooseWidgetLocationKey: (String) -> Unit,
    onChooseWidgetThemeIntensity: (WidgetThemeIntensity) -> Unit,
    onChooseWidgetDiagnosticsMode: (WidgetDiagnosticsMode) -> Unit,
    onToggleNotifyRainSoon: (Boolean) -> Unit,
    onToggleNotifyFreezing: (Boolean) -> Unit,
    onToggleNotifyStrongWind: (Boolean) -> Unit,
    onToggleNotifyMorningSummary: (Boolean) -> Unit,
    onToggleOpenMeteo: (Boolean) -> Unit,
    onToggleMetNorway: (Boolean) -> Unit,
    onToggleWeatherGov: (Boolean) -> Unit,
    onToggleWeatherApi: (Boolean) -> Unit,
    onWeatherApiKeyChanged: (String) -> Unit,
    onRequestForegroundLocation: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    hasForegroundPermission: Boolean,
    hasBackgroundPermission: Boolean,
    hasNotificationPermission: Boolean,
) {
    var apiKeyDraft by rememberSaveable(state.payload.settings.weatherApiKey) {
        mutableStateOf(state.payload.settings.weatherApiKey)
    }
    val widgetPlaces = state.payload.places.favoritePlaces.ifEmpty {
        state.payload.places.recentPlaces.filter { it.id != state.payload.places.activePlaceId }.take(4)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            SectionIntro(
                title = "Settings",
                body = "Tune how often Rainfern refreshes, which providers participate, and how location access works.",
            )
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Location mode", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LocationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.payload.settings.locationMode == mode,
                                onClick = { onChooseLocationMode(mode) },
                                label = { Text(mode.name.replace('_', ' ')) },
                            )
                        }
                    }
                    if (!hasForegroundPermission) {
                        TextButton(onClick = onRequestForegroundLocation) { Text("Grant foreground location") }
                    }
                    if (state.payload.settings.locationMode == LocationMode.BACKGROUND && !hasBackgroundPermission) {
                        TextButton(onClick = onOpenAppSettings) { Text("Open app settings for background location") }
                    }
                }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Foreground refresh cadence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("When Rainfern opens, it auto-refreshes if the cached forecast is older than this threshold.", color = CloudSilver)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RefreshInterval.entries.forEach { interval ->
                            FilterChip(
                                selected = state.payload.settings.refreshInterval == interval,
                                onClick = { onChooseRefreshInterval(interval) },
                                label = { Text(interval.label) },
                            )
                        }
                    }
                }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Background refresh policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Background work only runs when location mode is set to Background.", color = CloudSilver)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        RefreshInterval.entries.forEach { interval ->
                            FilterChip(
                                selected = state.payload.settings.backgroundRefreshInterval == interval,
                                onClick = { onChooseBackgroundRefreshInterval(interval) },
                                label = { Text(interval.label) },
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Wi-Fi only", fontWeight = FontWeight.SemiBold)
                            Text("Require unmetered network for background refreshes.", color = CloudSilver)
                        }
                        Switch(
                            checked = state.payload.settings.backgroundWifiOnly,
                            onCheckedChange = onToggleBackgroundWifiOnly,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Battery aware", fontWeight = FontWeight.SemiBold)
                            Text("Avoid background refresh when Android reports low battery.", color = CloudSilver)
                        }
                        Switch(
                            checked = state.payload.settings.backgroundBatteryAware,
                            onCheckedChange = onToggleBackgroundBatteryAware,
                        )
                    }
                }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Display units", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Formatting follows the system locale. Units and time style can still be overridden here.", color = CloudSilver)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TemperatureUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = state.payload.settings.temperatureUnit == unit,
                                onClick = { onChooseTemperatureUnit(unit) },
                                label = { Text(unit.label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WindUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = state.payload.settings.windUnit == unit,
                                onClick = { onChooseWindUnit(unit) },
                                label = { Text(unit.label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PressureUnit.entries.forEach { unit ->
                            FilterChip(
                                selected = state.payload.settings.pressureUnit == unit,
                                onClick = { onChoosePressureUnit(unit) },
                                label = { Text(unit.label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TimeFormatPreference.entries.forEach { preference ->
                            FilterChip(
                                selected = state.payload.settings.timeFormatPreference == preference,
                                onClick = { onChooseTimeFormatPreference(preference) },
                                label = { Text(preference.label) },
                            )
                        }
                    }
                }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Widgets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Both widget sizes use this shared configuration for location, color intensity, and secondary detail.", color = CloudSilver)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.payload.settings.widgetLocationKey == WIDGET_ACTIVE_LOCATION_KEY,
                            onClick = { onChooseWidgetLocationKey(WIDGET_ACTIVE_LOCATION_KEY) },
                            label = { Text("Active forecast") },
                        )
                        FilterChip(
                            selected = state.payload.settings.widgetLocationKey == dev.rdime.rainfern.data.model.DEVICE_LOCATION_KEY,
                            onClick = { onChooseWidgetLocationKey(dev.rdime.rainfern.data.model.DEVICE_LOCATION_KEY) },
                            label = { Text("Device") },
                        )
                    }
                    if (widgetPlaces.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            widgetPlaces.forEach { place ->
                                FilterChip(
                                    selected = state.payload.settings.widgetLocationKey == place.id,
                                    onClick = { onChooseWidgetLocationKey(place.id) },
                                    label = { Text(place.name) },
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetThemeIntensity.entries.forEach { intensity ->
                            FilterChip(
                                selected = state.payload.settings.widgetThemeIntensity == intensity,
                                onClick = { onChooseWidgetThemeIntensity(intensity) },
                                label = { Text(intensity.label) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WidgetDiagnosticsMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.payload.settings.widgetDiagnosticsMode == mode,
                                onClick = { onChooseWidgetDiagnosticsMode(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Rainfern evaluates notification rules after background refreshes and throttles repeated alerts automatically.", color = CloudSilver)
                    if (!hasNotificationPermission) {
                        TextButton(onClick = onRequestNotificationPermission) { Text("Grant notification permission") }
                    }
                    NotificationToggleRow(
                        title = "Rain soon",
                        subtitle = "Alert when the blended forecast sees imminent rain.",
                        enabled = state.payload.settings.notifyRainSoon,
                        onToggle = onToggleNotifyRainSoon,
                    )
                    NotificationToggleRow(
                        title = "Freezing conditions",
                        subtitle = "Warn when the next 12 hours may drop to freezing.",
                        enabled = state.payload.settings.notifyFreezing,
                        onToggle = onToggleNotifyFreezing,
                    )
                    NotificationToggleRow(
                        title = "Strong wind",
                        subtitle = "Warn when high wind is forecast in the next 12 hours.",
                        enabled = state.payload.settings.notifyStrongWind,
                        onToggle = onToggleNotifyStrongWind,
                    )
                    NotificationToggleRow(
                        title = "Morning summary",
                        subtitle = "Send one daily morning weather summary after an eligible refresh.",
                        enabled = state.payload.settings.notifyMorningSummary,
                        onToggle = onToggleNotifyMorningSummary,
                    )
                }
            }
        }
        item { ProviderToggleCard("Open-Meteo", state.payload.settings.openMeteoEnabled, onToggleOpenMeteo) }
        item { ProviderToggleCard("MET Norway", state.payload.settings.metNorwayEnabled, onToggleMetNorway) }
        item { ProviderToggleCard("weather.gov / NWS", state.payload.settings.weatherGovEnabled, onToggleWeatherGov) }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("WeatherAPI.com", fontWeight = FontWeight.SemiBold)
                            Text("Optional keyed provider", color = CloudSilver)
                        }
                        Switch(checked = state.payload.settings.weatherApiEnabled, onCheckedChange = onToggleWeatherApi)
                    }
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = apiKeyDraft,
                        onValueChange = {
                            apiKeyDraft = it
                            onWeatherApiKeyChanged(it)
                        },
                        label = { Text("Free API key") },
                        supportingText = { Text("Stored locally on your device for personal use.") },
                    )
                }
            }
        }
        item {
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Feature ideas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    state.featureIdeas.forEach { idea -> Text("- $idea", color = CloudSilver) }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    title: String,
    subtitle: String,
    onRefresh: () -> Unit,
    refreshing: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.semantics { heading() }) {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, color = CloudSilver)
        }
        if (refreshing) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
private fun OnboardingLocationCard(
    onChooseLocationMode: (LocationMode) -> Unit,
    onOpenPlaces: () -> Unit,
    onDecideLater: () -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Choose how Rainfern should find weather", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("While open checks your location only when you use the app. Background keeps weather fresher in widgets and notifications, but needs broader permission.", color = CloudSilver)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = false, onClick = { onChooseLocationMode(LocationMode.ON_OPEN) }, label = { Text("While open") })
                FilterChip(selected = false, onClick = { onChooseLocationMode(LocationMode.BACKGROUND) }, label = { Text("Background") })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onOpenPlaces) { Text("Search manually") }
                TextButton(onClick = onDecideLater) { Text("Decide later") }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(
    requestShown: Boolean,
    onRequestForegroundLocation: () -> Unit,
    onChooseLocationMode: (LocationMode) -> Unit,
    onOpenPlaces: () -> Unit,
    onDecideLater: () -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Rainfern needs a first location fix", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "Pick how the app should find weather: use device location, search manually, or skip for now and come back later.",
                color = CloudSilver,
                textAlign = TextAlign.Center,
            )
            if (!requestShown) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { onChooseLocationMode(LocationMode.ON_OPEN) }, label = { Text("While open") })
                    FilterChip(selected = false, onClick = { onChooseLocationMode(LocationMode.BACKGROUND) }, label = { Text("Background") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onOpenPlaces) { Text("Search manually") }
                    TextButton(onClick = onDecideLater) { Text("Decide later") }
                }
            } else {
                Text(
                    "You can still grant location access now or open Places and pick a city manually.",
                    color = FernMist,
                    textAlign = TextAlign.Center,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRequestForegroundLocation) { Text("Grant location access") }
                    TextButton(onClick = onOpenPlaces) { Text("Open Places") }
                }
            }
        }
    }
}

@Composable
private fun ManualPlaceEmptyCard(
    place: SavedPlace,
    offline: Boolean,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("No forecast cached yet", style = MaterialTheme.typography.titleLarge)
            Text(
                "Rainfern is pointed at ${place.label}. ${if (offline) "Reconnect and refresh to fetch its forecast." else "Refresh once to download its blended weather."}",
                color = CloudSilver,
            )
        }
    }
}

@Composable
private fun HeroCard(
    current: CurrentWeather,
    confidence: Double,
    providerCount: Int,
    metricConfidence: List<MetricConfidence>,
    settings: dev.rdime.rainfern.data.model.AppSettings,
) {
    val nearTermBlend = current.observedAt.isAfter(Instant.now().plusSeconds(20 * 60))
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.82f))) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .semantics {
                    contentDescription = buildString {
                        append(if (nearTermBlend) "Near-term weather blend. " else "Current weather blend. ")
                        append(current.conditionText)
                        current.temperatureC?.let { append(". Temperature ${formatTemperature(it, settings)}.") }
                        current.windSpeedMps?.let { append(" Wind ${formatWindSpeed(it, settings)}.") }
                        append(" Confidence ${(confidence * 100).roundToInt()} percent.")
                    }
                },
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = weatherIcon(current.condition),
                    contentDescription = current.conditionText,
                    modifier = Modifier.size(36.dp),
                    tint = RainBlue,
                )
                Column {
                    Text(current.conditionText, color = FernMist, style = MaterialTheme.typography.titleLarge)
                    Text(if (nearTermBlend) "Near-term blend from $providerCount sources" else "Current blend from $providerCount sources", color = CloudSilver)
                }
            }
            Text(
                text = formatTemperature(current.temperatureC, settings),
                fontSize = 54.sp,
                fontWeight = FontWeight.Bold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MetricColumn("Feels like", formatTemperature(current.feelsLikeC, settings))
                MetricColumn("Rain", current.precipitationProbabilityPercent?.let { "$it%" } ?: "--")
                MetricColumn("Wind", formatWindSpeed(current.windSpeedMps, settings))
            }
            if (metricConfidence.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    metricConfidence
                        .filter { it.metric in setOf(ForecastMetric.TEMPERATURE, ForecastMetric.PRECIPITATION_PROBABILITY, ForecastMetric.WIND_SPEED) }
                        .sortedBy { heroMetricOrder(it.metric) }
                        .forEach { metric ->
                            MetricColumn(metricShortLabel(metric.metric), "${(metric.score * 100).roundToInt()}%")
                        }
                }
            }
            LinearProgressIndicator(progress = confidence.toFloat(), modifier = Modifier.fillMaxWidth())
            Text("Confidence ${(confidence * 100).roundToInt()}%", color = CloudSilver)
        }
    }
}

@Composable
private fun PrecipitationInsightCard(
    current: CurrentWeather,
    hourly: List<HourlyWeather>,
) {
    val nextWetHour = hourly.firstOrNull {
        (it.precipitationProbabilityPercent ?: 0) >= 45 || (it.precipitationMm ?: 0.0) >= 0.2
    }
    val accumulation12h = hourly.take(12).sumOf { it.precipitationMm ?: 0.0 }
    val peakProbability = hourly.take(12).mapNotNull { it.precipitationProbabilityPercent }.maxOrNull() ?: 0
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Precipitation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MetricColumn("Now", precipitationBand(current.precipitationMm))
                MetricColumn("12h total", if (accumulation12h > 0.0) "${accumulation12h.roundToInt()} mm" else "Dry")
                MetricColumn("Peak chance", "$peakProbability%")
            }
            Text(
                text = nextWetHour?.let {
                    val minutesAway = Duration.between(Instant.now(), it.time).toMinutes().coerceAtLeast(0)
                    when {
                        minutesAway < 60 -> "Rain may start in about $minutesAway min."
                        else -> "Rain may start in about ${minutesAway / 60} h."
                    }
                } ?: "No meaningful precipitation signal in the next 12 hours.",
                color = CloudSilver,
            )
        }
    }
}

@Composable
private fun AirQualityCard(
    airQuality: AirQualitySnapshot?,
    pollen: PollenSnapshot?,
) {
    if (airQuality == null && pollen == null) {
        return
    }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Air quality and pollen", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            airQuality?.let {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    MetricColumn("US AQI", it.usAqi?.toString() ?: "--")
                    MetricColumn("PM2.5", it.pm25?.roundToInt()?.toString() ?: "--")
                    MetricColumn("UV", it.uvIndex?.roundToInt()?.toString() ?: "--")
                }
                Text(
                    "AQI ${aqiBand(it.usAqi)} from ${it.sourceName}",
                    color = FernMist,
                )
            }
            pollen?.let {
                val strongest = listOfNotNull(
                    it.alder?.let { value -> "Alder ${value.roundToInt()}" },
                    it.birch?.let { value -> "Birch ${value.roundToInt()}" },
                    it.grass?.let { value -> "Grass ${value.roundToInt()}" },
                    it.mugwort?.let { value -> "Mugwort ${value.roundToInt()}" },
                    it.ragweed?.let { value -> "Ragweed ${value.roundToInt()}" },
                ).take(3)
                Text(
                    if (strongest.isNotEmpty()) {
                        strongest.joinToString(" • ")
                    } else {
                        it.coverageNote
                    },
                    color = CloudSilver,
                )
            }
        }
    }
}

@Composable
private fun ProgressiveDetailsCard(
    airQuality: AirQualitySnapshot?,
    today: DailyWeather?,
    timeZoneId: String,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val summaryParts = buildList {
        airQuality?.usAqi?.let { add("AQI $it") }
        (airQuality?.uvIndex ?: today?.uvIndexMax)?.let { add("UV ${formatUvValue(it)}") }
        formatSunEvent(today?.date, today?.sunsetIso, settings, timeZoneId).takeIf { it != "--" }?.let { add("Sunset $it") }
    }.ifEmpty { listOf("Air, UV, and astronomy details are available here.") }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.68f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("More details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(summaryParts.joinToString(" | "), color = CloudSilver)
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Hide advanced details" else "Show advanced details")
            }
        }
    }
}

@Composable
private fun ForecastAnomaliesCard(
    anomalies: List<ForecastAnomaly>,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Watch for", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            anomalies.forEach { anomaly ->
                Text("${anomaly.severity}: ${anomaly.title} - ${anomaly.detail}", color = CloudSilver)
            }
        }
    }
}

@Composable
private fun ActivitySuitabilityCard(
    current: CurrentWeather,
    hourly: List<HourlyWeather>,
    daily: List<DailyWeather>,
    airQuality: AirQualitySnapshot?,
    alerts: List<WeatherAlert>,
    timeZoneId: String,
    settings: dev.rdime.rainfern.data.model.AppSettings,
) {
    val activities = remember(
        current.observedAt.toEpochMilli(),
        hourly.firstOrNull()?.time?.toEpochMilli(),
        daily.firstOrNull()?.date?.toString(),
        airQuality?.usAqi,
        alerts.firstOrNull()?.title,
        timeZoneId,
    ) {
        buildActivitySuitability(current, hourly, daily, airQuality, alerts, timeZoneId, settings)
    }
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activity outlook", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Rainfern scores common activities from the blended forecast so you can make a quick plan without losing the underlying detail.",
                color = CloudSilver,
            )
            activities.forEach { activity ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(activity.name, fontWeight = FontWeight.SemiBold)
                        Text(activity.summary, color = suitabilityColor(activity.score))
                        Text(activity.detail, color = CloudSilver)
                    }
                    Text(
                        "${activity.score}",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = suitabilityColor(activity.score),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkyDetailCard(
    current: CurrentWeather,
    today: DailyWeather?,
    airQuality: AirQualitySnapshot?,
    timeZoneId: String,
    settings: dev.rdime.rainfern.data.model.AppSettings,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Sky details", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MetricColumn("Sunrise", formatSunEvent(today?.date, today?.sunriseIso, settings, timeZoneId))
                MetricColumn("Sunset", formatSunEvent(today?.date, today?.sunsetIso, settings, timeZoneId))
                MetricColumn("UV", formatUvValue(airQuality?.uvIndex ?: today?.uvIndexMax))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MetricColumn("Dew point", formatTemperature(current.dewPointC, settings))
                MetricColumn("Visibility", current.visibilityKm?.let { "${it.roundToInt()} km" } ?: "--")
                MetricColumn("Pressure", formatPressure(current.pressureHpa, settings))
            }
            Text(
                text = "${upcomingGoldenHourLabel(today, timeZoneId)} golden hour. ${goldenHourWindowLine(today, settings, timeZoneId)}",
                color = CloudSilver,
            )
        }
    }
}

@Composable
private fun HourlyStrip(
    hourly: List<HourlyWeather>,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    timeZoneId: String,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(hourly.take(24)) { hour ->
            ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(formatClock(hour.time, settings, timeZoneId), color = CloudSilver)
                    Icon(weatherIcon(hour.condition), contentDescription = hour.conditionText, tint = RainBlue)
                    Text(formatTemperature(hour.temperatureC, settings), fontWeight = FontWeight.Bold)
                    Text(hour.precipitationProbabilityPercent?.let { "$it%" } ?: "--", color = FernMist)
                }
            }
        }
    }
}

@Composable
private fun DailyCard(
    day: DailyWeather,
    settings: dev.rdime.rainfern.data.model.AppSettings,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(weatherIcon(day.condition), contentDescription = day.conditionText, tint = RainBlue)
                Column {
                    Text(formatDayName(day.date, settings), fontWeight = FontWeight.SemiBold)
                    Text(day.conditionText, color = CloudSilver)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MetricColumn("Min", formatTemperature(day.minTempC, settings))
                MetricColumn("Max", formatTemperature(day.maxTempC, settings))
                MetricColumn("Rain", day.precipitationProbabilityPercent?.let { "$it%" } ?: "--")
            }
        }
    }
}

@Composable
private fun ActivePlaceCard(
    activePlace: SavedPlace?,
    onUseDeviceLocation: () -> Unit,
    onRefresh: () -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    imageVector = if (activePlace == null) Icons.Rounded.MyLocation else Icons.Rounded.LocationOn,
                    contentDescription = null,
                    tint = RainBlue,
                )
                Column {
                    Text(
                        text = activePlace?.label ?: "Using device location",
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (activePlace == null) {
                            "Rainfern follows the phone's current position."
                        } else {
                            "Manual place override is active."
                        },
                        color = CloudSilver,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activePlace != null) {
                    TextButton(onClick = onUseDeviceLocation) { Text("Use device location") }
                }
                TextButton(onClick = onRefresh) { Text("Refresh") }
            }
        }
    }
}

@Composable
private fun PlaceSearchCard(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    error: String?,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Search for a place", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                label = { Text("City or place name") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                supportingText = {
                    Text("Search uses Open-Meteo first with a Nominatim fallback, then Rainfern blends weather from all enabled forecast sources.")
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onSearch) { Text("Search") }
                if (query.isNotBlank() || error != null) {
                    TextButton(onClick = onClear) { Text("Clear") }
                }
                if (searching) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            error?.let {
                Text(it, color = CloudSilver)
            }
        }
    }
}

@Composable
private fun PlaceCard(
    place: SavedPlace,
    isActive: Boolean,
    onSelectPlace: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(place.name, fontWeight = FontWeight.SemiBold)
                    if (place.subtitle.isNotBlank()) {
                        Text(place.subtitle, color = CloudSilver)
                    }
                    Text(
                        "${"%.3f".format(place.coordinates.latitude)}, ${"%.3f".format(place.coordinates.longitude)}",
                        color = FernMist,
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (place.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (place.isFavorite) "Remove favorite" else "Add favorite",
                        tint = if (place.isFavorite) RainBlue else CloudSilver,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (isActive) {
                    Text("Active", color = FernMist, fontWeight = FontWeight.Medium)
                }
                TextButton(onClick = onSelectPlace) {
                    Text(if (isActive) "Selected" else "Use this place")
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(
    info: dev.rdime.rainfern.data.network.provider.ProviderInfo,
    provider: ProviderForecast?,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    enabled: Boolean,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(info.displayName, fontWeight = FontWeight.SemiBold)
                    Text(info.coverage, color = CloudSilver)
                }
                Text(
                    when {
                        provider != null -> "Active"
                        enabled -> "Waiting"
                        else -> "Disabled"
                    },
                    color = FernMist,
                )
            }
            Text(info.notes, color = CloudSilver)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                MetricColumn("Coverage", info.coverage)
                MetricColumn("Key", providerKeyStatus(info, settings, enabled))
                MetricColumn(
                    "Last fetch",
                    provider?.fetchedAt?.let { formatAge(it) } ?: if (enabled) "Waiting" else "Off",
                )
            }
            Text("Attribution: ${provider?.attribution ?: info.displayName}", color = CloudSilver)
            provider?.locationName?.takeIf { it.isNotBlank() }?.let { locationName ->
                Text("Provider location label: $locationName", color = FernMist)
            }
            provider?.statusNote?.takeIf { it.isNotBlank() }?.let { note ->
                Text(note, color = FernMist)
            }
            provider?.fetchedAt?.let { fetchedAt ->
                Text("Fetched ${formatHistoryTimestamp(fetchedAt, settings, provider.timeZoneId)}", color = CloudSilver)
            }
            Text(info.sourceUrl, color = RainBlue)
            if (provider != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    MetricColumn("Temp", formatTemperature(provider.current?.temperatureC, settings))
                    MetricColumn("Condition", provider.current?.conditionText ?: "--")
                    MetricColumn("Confidence", "${(provider.confidence * 100).roundToInt()}%")
                }
                provider.alerts.firstOrNull()?.let { alert ->
                    Text("Top alert: ${alert.title}", color = FernMist)
                }
            }
        }
    }
}

@Composable
private fun SupportingSourceCard(
    metadata: SupportingSourceMetadata,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(metadata.name, fontWeight = FontWeight.SemiBold)
                    Text(metadata.role, color = CloudSilver)
                }
                Text(if (metadata.requiresKey) "Key-capable" else "Free", color = FernMist)
            }
            Text(metadata.summary, color = CloudSilver)
            Text("Attribution: ${metadata.attribution}", color = FernMist)
            Text(metadata.sourceUrl, color = RainBlue)
        }
    }
}

@Composable
private fun DisagreementOverviewCard(
    disagreements: List<MetricDisagreement>,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Provider disagreement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "This shows where the enabled providers are furthest apart before Rainfern blends the result.",
                color = CloudSilver,
            )
            disagreements.take(4).forEach { disagreement ->
                Text(disagreementLine(disagreement), color = FernMist)
            }
        }
    }
}

@Composable
private fun MetricConfidenceOverviewCard(
    metricConfidence: List<MetricConfidence>,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Metric confidence", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Confidence is computed separately for each metric, so stable temperature can coexist with uncertain precipitation.",
                color = CloudSilver,
            )
            metricConfidence
                .sortedBy { heroMetricOrder(it.metric) }
                .forEach { confidence ->
                    Text(
                        "${metricLabel(confidence.metric)} ${(confidence.score * 100).roundToInt()}% from ${confidence.providerCount} source(s)",
                        color = FernMist,
                    )
                }
        }
    }
}

@Composable
private fun ForecastComparisonCard(
    blended: dev.rdime.rainfern.data.model.AggregatedForecast,
    providers: List<ProviderForecast>,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    timeZoneId: String,
    comparisonMode: ComparisonMode,
    onModeChange: (ComparisonMode) -> Unit,
    selectedHourlyIndex: Int,
    onSelectHourlyIndex: (Int) -> Unit,
    selectedDailyIndex: Int,
    onSelectDailyIndex: (Int) -> Unit,
) {
    val selectedHourly = blended.hourly.getOrNull(selectedHourlyIndex)
    val selectedDaily = blended.daily.getOrNull(selectedDailyIndex)
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Forecast comparison", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Inspect the blended forecast next to each provider for one selected time block.", color = CloudSilver)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ComparisonMode.entries.forEach { mode ->
                    FilterChip(
                        selected = comparisonMode == mode,
                        onClick = { onModeChange(mode) },
                        label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            when (comparisonMode) {
                ComparisonMode.CURRENT -> {
                    ComparisonRow(label = "Blended", detail = comparisonDetail(blended.current, settings))
                    providers.forEach { provider ->
                        ComparisonRow(
                            label = provider.providerName,
                            detail = provider.current?.let { comparisonDetail(it, settings) } ?: "No current data",
                        )
                    }
                }

                ComparisonMode.HOURLY -> {
                    if (blended.hourly.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            blended.hourly.take(8).forEachIndexed { index, slot ->
                                FilterChip(
                                    selected = selectedHourlyIndex == index,
                                    onClick = { onSelectHourlyIndex(index) },
                                    label = { Text(formatClock(slot.time, settings, timeZoneId)) },
                                )
                            }
                        }
                        selectedHourly?.let { hourly ->
                            ComparisonRow(label = "Blended", detail = comparisonDetail(hourly, settings))
                            providers.forEach { provider ->
                                val providerHourly = provider.hourly.firstOrNull { it.time == hourly.time }
                                ComparisonRow(
                                    label = provider.providerName,
                                    detail = providerHourly?.let { comparisonDetail(it, settings) } ?: "No hourly data",
                                )
                            }
                        }
                    }
                }

                ComparisonMode.DAILY -> {
                    if (blended.daily.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            blended.daily.take(7).forEachIndexed { index, day ->
                                FilterChip(
                                    selected = selectedDailyIndex == index,
                                    onClick = { onSelectDailyIndex(index) },
                                    label = { Text(formatDayName(day.date, settings).take(3)) },
                                )
                            }
                        }
                        selectedDaily?.let { day ->
                            ComparisonRow(label = "Blended", detail = comparisonDetail(day, settings))
                            providers.forEach { provider ->
                                val providerDaily = provider.daily.firstOrNull { it.date == day.date }
                                ComparisonRow(
                                    label = provider.providerName,
                                    detail = providerDaily?.let { comparisonDetail(it, settings) } ?: "No daily data",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComparisonRow(
    label: String,
    detail: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Text(detail, color = CloudSilver, textAlign = TextAlign.End)
    }
}

@Composable
private fun DisagreementSlotCard(
    slot: SlotDiagnostics,
    label: String,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            if (slot.summary.isNotBlank()) {
                Text(slot.summary, color = FernMist)
            }
            slot.disagreements.take(3).forEach { disagreement ->
                Text(disagreementLine(disagreement), color = CloudSilver)
            }
        }
    }
}

@Composable
private fun AlertCard(
    alert: WeatherAlert,
    highlighted: Boolean,
) {
    ElevatedCard(
        modifier = Modifier.semantics {
            contentDescription = "Alert. ${alert.severity}. ${alert.title}. ${alert.sourceName}."
        },
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (highlighted) {
                AlertRed.copy(alpha = 0.92f)
            } else {
                AlertRed.copy(alpha = 0.74f)
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(alert.title, fontWeight = FontWeight.Bold)
            Text("${alert.severity} | ${alert.sourceName}", color = FernMist)
            Text(alert.description.take(220), color = FernMist)
        }
    }
}

@Composable
private fun ProviderToggleCard(
    name: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(name, fontWeight = FontWeight.SemiBold)
                Text("Included in the blended forecast", color = CloudSilver)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = CloudSilver)
        }
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun HistoryCard(
    locationName: String,
    summary: String,
    temperatureLabel: String,
    capturedAtMillis: Long,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    timeZoneId: String?,
) {
    val timestamp = formatHistoryTimestamp(Instant.ofEpochMilli(capturedAtMillis), settings, timeZoneId)
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = CardNight.copy(alpha = 0.72f))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(locationName, fontWeight = FontWeight.Medium)
                Text(timestamp, color = CloudSilver)
                Text(summary, color = FernMist)
            }
            Text(temperatureLabel, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MetricColumn(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = CloudSilver, fontSize = 12.sp)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatSunEvent(
    date: java.time.LocalDate?,
    raw: String?,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    timeZoneId: String?,
): String = parseSunEvent(date, raw, timeZoneId)?.let { formatClock(it, settings) } ?: "--"

private fun formatUvValue(value: Double?): String = value?.let { "%.1f".format(it) } ?: "--"

private fun relativeAge(instant: Instant): String {
    val duration = Duration.between(instant, Instant.now()).abs()
    val hours = duration.toHours()
    val minutes = duration.minusHours(hours).toMinutes()
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "${minutes}m"
    }
}

private fun parseSunEvent(
    date: java.time.LocalDate?,
    raw: String?,
    timeZoneId: String?,
): ZonedDateTime? {
    val normalized = raw?.trim().orEmpty()
    if (normalized.isBlank()) {
        return null
    }
    val zoneId = resolveUiZoneId(timeZoneId)
    return runCatching {
        Instant.parse(normalized).atZone(zoneId)
    }.recoverCatching {
        OffsetDateTime.parse(normalized).toInstant().atZone(zoneId)
    }.recoverCatching {
        LocalDateTime.parse(normalized).atZone(zoneId)
    }.recoverCatching {
        val day = requireNotNull(date)
        val parsedTime = LocalTime.parse(
            normalized.uppercase(Locale.ENGLISH),
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH),
        )
        day.atTime(parsedTime).atZone(zoneId)
    }.getOrNull()
}

private fun upcomingGoldenHourLabel(
    today: DailyWeather?,
    timeZoneId: String?,
): String {
    val windows = goldenHourWindows(today, timeZoneId)
    if (windows.isEmpty()) {
        return "--"
    }
    val now = ZonedDateTime.now(resolveUiZoneId(timeZoneId))
    val next = windows.firstOrNull { it.second.isAfter(now.minusMinutes(15)) } ?: windows.last()
    return next.first
}

private fun goldenHourWindowLine(
    today: DailyWeather?,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    timeZoneId: String?,
): String {
    val windows = goldenHourWindows(today, timeZoneId)
    if (windows.isEmpty()) {
        return "Sunrise and sunset are unavailable for this day."
    }
    return windows.joinToString(" | ") { (label, start, end) ->
        "$label ${formatClock(start, settings)}-${formatClock(end, settings)}"
    }
}

private fun goldenHourWindows(
    today: DailyWeather?,
    timeZoneId: String?,
): List<Triple<String, ZonedDateTime, ZonedDateTime>> {
    val day = today ?: return emptyList()
    val sunrise = parseSunEvent(day.date, day.sunriseIso, timeZoneId)
    val sunset = parseSunEvent(day.date, day.sunsetIso, timeZoneId)
    return listOfNotNull(
        sunrise?.let { Triple("Morning", it.minusMinutes(45), it.plusMinutes(45)) },
        sunset?.let { Triple("Evening", it.minusMinutes(45), it.plusMinutes(45)) },
    )
}

private fun snapshotDeltaLine(
    latest: dev.rdime.rainfern.data.model.SnapshotRecord,
    baseline: dev.rdime.rainfern.data.model.SnapshotRecord,
): String {
    val tempDelta = if (latest.currentTempC != null && baseline.currentTempC != null) {
        val delta = latest.currentTempC - baseline.currentTempC
        when {
            delta > 0.4 -> "+${delta.roundToInt()}°"
            delta < -0.4 -> "${delta.roundToInt()}°"
            else -> "steady temp"
        }
    } else {
        "temp unavailable"
    }
    val summaryDelta = if (latest.summary != baseline.summary) {
        "${baseline.summary} -> ${latest.summary}"
    } else {
        latest.summary
    }
    return "$tempDelta, $summaryDelta"
}

private data class ActivitySuitability(
    val name: String,
    val score: Int,
    val summary: String,
    val detail: String,
)

private data class SupportingSourceMetadata(
    val name: String,
    val role: String,
    val sourceUrl: String,
    val attribution: String,
    val summary: String,
    val requiresKey: Boolean = false,
)

private fun buildActivitySuitability(
    current: CurrentWeather,
    hourly: List<HourlyWeather>,
    daily: List<DailyWeather>,
    airQuality: AirQualitySnapshot?,
    alerts: List<WeatherAlert>,
    timeZoneId: String?,
    settings: dev.rdime.rainfern.data.model.AppSettings,
): List<ActivitySuitability> {
    val zoneId = resolveUiZoneId(timeZoneId)
    val next6Hours = hourly.take(6)
    val today = daily.firstOrNull()
    val severeAlertPenalty = if (alerts.any { it.severity.contains("severe", ignoreCase = true) || it.severity.contains("extreme", ignoreCase = true) }) 18 else 0
    val rainPeak = next6Hours.mapNotNull { it.precipitationProbabilityPercent }.maxOrNull() ?: (current.precipitationProbabilityPercent ?: 0)
    val rainAmount = next6Hours.sumOf { it.precipitationMm ?: 0.0 }
    val windPeak = next6Hours.mapNotNull { it.windSpeedMps }.maxOrNull() ?: current.windSpeedMps ?: 0.0
    val cloudPeak = next6Hours.mapNotNull { it.cloudCoverPercent }.average().takeIf { !it.isNaN() }?.roundToInt()
    val evening = hourly.filter { slot ->
        val hour = slot.time.atZone(zoneId).hour
        hour in 19..23
    }.ifEmpty { hourly.takeLast(6) }
    val eveningCloud = evening.mapNotNull { it.cloudCoverPercent }.average().takeIf { !it.isNaN() }?.roundToInt() ?: current.cloudCoverPercent ?: 0
    val eveningRain = evening.mapNotNull { it.precipitationProbabilityPercent }.maxOrNull() ?: rainPeak
    val aqi = airQuality?.usAqi

    fun clampScore(value: Int): Int = value.coerceIn(0, 100)

    fun temperaturePenalty(comfortableMin: Double, comfortableMax: Double, strongPenalty: Int): Int {
        val temp = current.temperatureC ?: return 0
        return when {
            temp < comfortableMin - 8 || temp > comfortableMax + 8 -> strongPenalty
            temp < comfortableMin - 3 || temp > comfortableMax + 3 -> strongPenalty / 2
            else -> 0
        }
    }

    val walkingScore = clampScore(
        84 -
            temperaturePenalty(7.0, 24.0, 34) -
            if (rainPeak >= 70 || rainAmount >= 4.0) 28 else if (rainPeak >= 40) 14 else 0 -
            if (windPeak >= 12.0) 18 else if (windPeak >= 8.0) 8 else 0 -
            severeAlertPenalty,
    )
    val runningScore = clampScore(
        82 -
            temperaturePenalty(5.0, 18.0, 38) -
            if (rainPeak >= 65) 24 else if (rainPeak >= 35) 10 else 0 -
            if (windPeak >= 11.0) 18 else if (windPeak >= 7.0) 8 else 0 -
            when {
                (aqi ?: 0) >= 120 -> 26
                (aqi ?: 0) >= 80 -> 12
                else -> 0
            } -
            severeAlertPenalty,
    )
    val gardeningScore = clampScore(
        78 -
            temperaturePenalty(10.0, 28.0, 28) -
            if (rainAmount >= 10.0) 26 else if (rainAmount >= 4.0) 12 else 0 -
            if (windPeak >= 10.0) 10 else 0 -
            severeAlertPenalty +
            if ((today?.precipitationMm ?: 0.0) in 1.0..6.0 && rainPeak < 45) 8 else 0,
    )
    val laundryScore = clampScore(
        88 -
            if (rainPeak >= 60 || rainAmount >= 3.0) 42 else if (rainPeak >= 30) 18 else 0 -
            if ((current.humidityPercent ?: 0) >= 85) 18 else if ((current.humidityPercent ?: 0) >= 70) 8 else 0 -
            if (windPeak >= 14.0) 10 else 0 -
            severeAlertPenalty,
    )
    val stargazingScore = clampScore(
        80 -
            if (eveningCloud >= 80) 42 else if (eveningCloud >= 55) 20 else if (eveningCloud >= 30) 8 else 0 -
            if (eveningRain >= 45) 24 else if (eveningRain >= 20) 10 else 0 -
            if (windPeak >= 12.0) 10 else 0 -
            severeAlertPenalty +
            if (current.isDaylight == false || current.condition == WeatherCondition.CLEAR) 6 else 0,
    )

    return listOf(
        ActivitySuitability(
            name = "Walking",
            score = walkingScore,
            summary = suitabilitySummary(walkingScore),
            detail = buildString {
                append("Best when rain stays low and wind remains manageable.")
                append(" Now ${formatTemperature(current.temperatureC, settings)}, wind ${formatWindSpeed(current.windSpeedMps, settings)}.")
            },
        ),
        ActivitySuitability(
            name = "Running",
            score = runningScore,
            summary = suitabilitySummary(runningScore),
            detail = buildString {
                append("Heat, wind, rain, and AQI weigh more heavily here.")
                aqi?.let { append(" AQI $it.") }
            },
        ),
        ActivitySuitability(
            name = "Gardening",
            score = gardeningScore,
            summary = suitabilitySummary(gardeningScore),
            detail = buildString {
                append("Light moisture can help, but strong wind or sustained rain drags the score down.")
                today?.precipitationMm?.let { append(" Today ${it.roundToInt()} mm forecast.") }
            },
        ),
        ActivitySuitability(
            name = "Laundry",
            score = laundryScore,
            summary = suitabilitySummary(laundryScore),
            detail = "This favors dry hours, lower humidity, and a calm forecast so clothes can actually dry outside.",
        ),
        ActivitySuitability(
            name = "Stargazing",
            score = stargazingScore,
            summary = suitabilitySummary(stargazingScore),
            detail = "This mostly tracks tonight's cloud cover and rain signal, with a small penalty for rough wind and active alerts.",
        ),
    )
}

private fun suitabilitySummary(score: Int): String = when {
    score >= 82 -> "Excellent"
    score >= 68 -> "Good"
    score >= 50 -> "Fair"
    score >= 32 -> "Poor"
    else -> "Avoid if possible"
}

private fun suitabilityColor(score: Int): Color = when {
    score >= 82 -> Moss
    score >= 68 -> RainBlue
    score >= 50 -> CloudSilver
    score >= 32 -> AlertRed.copy(alpha = 0.86f)
    else -> AlertRed
}

private fun providerKeyStatus(
    info: dev.rdime.rainfern.data.network.provider.ProviderInfo,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    enabled: Boolean,
): String = when {
    !info.requiresKey -> "No key"
    !enabled -> "Disabled"
    settings.weatherApiKey.isBlank() -> "Key missing"
    else -> "Key set"
}

private fun supportingSourceMetadata(): List<SupportingSourceMetadata> = listOf(
    SupportingSourceMetadata(
        name = "Open-Meteo Air Quality",
        role = "AQI and pollen enrichment",
        sourceUrl = "https://open-meteo.com/en/docs/air-quality-api",
        attribution = "Open-Meteo, free non-commercial usage",
        summary = "Used for AQI, PM2.5, PM10, ozone, UV, and region-limited pollen data outside the main forecast-provider blend.",
    ),
    SupportingSourceMetadata(
        name = "Open-Meteo Geocoding",
        role = "Primary place search",
        sourceUrl = "https://open-meteo.com/en/docs/geocoding-api",
        attribution = "Open-Meteo geocoding service",
        summary = "Primary forward geocoder for city and place search, chosen to keep manual lookup free and globally available.",
    ),
    SupportingSourceMetadata(
        name = "Nominatim",
        role = "Fallback search and reverse geocoding",
        sourceUrl = "https://nominatim.openstreetmap.org/",
        attribution = "OpenStreetMap Nominatim",
        summary = "Provides fallback search coverage and reverse labels for device-based coordinates when Rainfern needs a human-readable place name.",
    ),
    SupportingSourceMetadata(
        name = "RainViewer",
        role = "Radar overlay",
        sourceUrl = "https://www.rainviewer.com/api.html",
        attribution = "RainViewer personal-use radar tiles",
        summary = "Supplies recent radar frames for the map view only; it is not used as another forecast provider inside the blended weather result.",
    ),
    SupportingSourceMetadata(
        name = "OpenStreetMap",
        role = "Map tiles",
        sourceUrl = "https://www.openstreetmap.org/",
        attribution = "OpenStreetMap contributors",
        summary = "Provides the lightweight base map shown underneath RainViewer radar in the Radar screen.",
    ),
)

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SectionIntro(
    title: String,
    body: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(body, color = CloudSilver)
    }
}

@Composable
private fun StatusBanner(
    text: String,
    color: Color,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = color,
        shape = MaterialTheme.shapes.medium,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            color = FernMist,
        )
    }
}

private fun weatherIcon(condition: WeatherCondition): ImageVector = when (condition) {
    WeatherCondition.CLEAR -> Icons.Rounded.WbSunny
    WeatherCondition.PARTLY_CLOUDY -> Icons.Rounded.WbCloudy
    WeatherCondition.CLOUDY -> Icons.Rounded.Cloud
    WeatherCondition.FOG -> Icons.Rounded.Foggy
    WeatherCondition.DRIZZLE, WeatherCondition.RAIN, WeatherCondition.SHOWERS -> Icons.Rounded.Umbrella
    WeatherCondition.SNOW, WeatherCondition.SLEET, WeatherCondition.HAIL -> Icons.Rounded.AcUnit
    WeatherCondition.THUNDERSTORM -> Icons.Rounded.Thunderstorm
    WeatherCondition.WINDY -> Icons.Rounded.Air
    else -> Icons.Rounded.WaterDrop
}

private fun heroMetricOrder(metric: ForecastMetric): Int = when (metric) {
    ForecastMetric.TEMPERATURE -> 0
    ForecastMetric.PRECIPITATION_PROBABILITY -> 1
    ForecastMetric.WIND_SPEED -> 2
    ForecastMetric.CLOUD_COVER -> 3
    ForecastMetric.CONDITION -> 4
}

private fun slotConflictScore(slot: SlotDiagnostics): Double =
    slot.disagreements.maxOfOrNull { it.normalizedSpread } ?: 0.0

private fun formatSlotLabel(
    slot: SlotDiagnostics,
    settings: dev.rdime.rainfern.data.model.AppSettings,
    timeZoneId: String?,
): String = when {
    slot.timestampEpochMillis != null -> Instant.ofEpochMilli(slot.timestampEpochMillis)
        .atZone(resolveUiZoneId(timeZoneId))
        .let { "${it.format(DateTimeFormatter.ofPattern("EEE", Locale.getDefault()))} ${formatClock(it, settings)}" }

    slot.dateIso != null -> runCatching {
        formatDayName(java.time.LocalDate.parse(slot.dateIso), settings)
    }.getOrElse { slot.dateIso }

    else -> "Forecast slot"
}

private fun resolveUiZoneId(timeZoneId: String?): ZoneId = runCatching {
    ZoneId.of(timeZoneId.orEmpty().ifBlank { ZoneId.systemDefault().id })
}.getOrElse {
    ZoneId.systemDefault()
}

private fun disagreementLine(disagreement: MetricDisagreement): String = when (disagreement.metric) {
    ForecastMetric.TEMPERATURE ->
        "Temperature ${valueRange(disagreement, suffix = "°C")} spread ${disagreement.spread.roundToInt()}°"

    ForecastMetric.PRECIPITATION_PROBABILITY ->
        "Rain chance ${valueRange(disagreement, suffix = "%")} spread ${disagreement.spread.roundToInt()}%"

    ForecastMetric.WIND_SPEED ->
        "Wind ${valueRange(disagreement, suffix = " m/s")} spread ${disagreement.spread.roundToInt()} m/s"

    ForecastMetric.CLOUD_COVER ->
        "Cloud cover ${valueRange(disagreement, suffix = "%")} spread ${disagreement.spread.roundToInt()}%"

    ForecastMetric.CONDITION ->
        "Condition disagreement ${(disagreement.normalizedSpread * 100).roundToInt()}%"
}

private fun metricShortLabel(metric: ForecastMetric): String = when (metric) {
    ForecastMetric.TEMPERATURE -> "Temp"
    ForecastMetric.PRECIPITATION_PROBABILITY -> "Rain"
    ForecastMetric.WIND_SPEED -> "Wind"
    ForecastMetric.CLOUD_COVER -> "Cloud"
    ForecastMetric.CONDITION -> "Sky"
}

private fun metricLabel(metric: ForecastMetric): String = when (metric) {
    ForecastMetric.TEMPERATURE -> "Temperature"
    ForecastMetric.PRECIPITATION_PROBABILITY -> "Rain chance"
    ForecastMetric.WIND_SPEED -> "Wind"
    ForecastMetric.CLOUD_COVER -> "Cloud cover"
    ForecastMetric.CONDITION -> "Condition"
}

private fun comparisonDetail(
    current: CurrentWeather,
    settings: dev.rdime.rainfern.data.model.AppSettings,
): String = listOf(
    formatTemperature(current.temperatureC, settings),
    current.precipitationProbabilityPercent?.let { "$it%" } ?: "--",
    formatWindSpeed(current.windSpeedMps, settings),
    current.conditionText,
).joinToString(" | ")

private fun comparisonDetail(
    hourly: HourlyWeather,
    settings: dev.rdime.rainfern.data.model.AppSettings,
): String = listOf(
    formatTemperature(hourly.temperatureC, settings),
    hourly.precipitationProbabilityPercent?.let { "$it%" } ?: "--",
    formatWindSpeed(hourly.windSpeedMps, settings),
    hourly.conditionText,
).joinToString(" | ")

private fun comparisonDetail(
    daily: DailyWeather,
    settings: dev.rdime.rainfern.data.model.AppSettings,
): String = listOf(
    "${formatTemperature(daily.minTempC, settings)} / ${formatTemperature(daily.maxTempC, settings)}",
    daily.precipitationProbabilityPercent?.let { "$it%" } ?: "--",
    formatWindSpeed(daily.maxWindSpeedMps, settings),
    daily.conditionText,
).joinToString(" | ")

private fun valueRange(
    disagreement: MetricDisagreement,
    suffix: String,
): String = if (disagreement.minValue == null || disagreement.maxValue == null) {
    "--"
} else {
    "${disagreement.minValue.roundToInt()}$suffix to ${disagreement.maxValue.roundToInt()}$suffix"
}

private fun precipitationBand(precipitationMm: Double?): String = when {
    precipitationMm == null || precipitationMm < 0.1 -> "Dry"
    precipitationMm < 0.5 -> "Light"
    precipitationMm < 2.0 -> "Steady"
    else -> "Heavy"
}

private fun aqiBand(aqi: Int?): String = when {
    aqi == null -> "unavailable"
    aqi <= 50 -> "good"
    aqi <= 100 -> "moderate"
    aqi <= 150 -> "unhealthy for sensitive groups"
    aqi <= 200 -> "unhealthy"
    else -> "very unhealthy"
}
