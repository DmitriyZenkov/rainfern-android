package dev.rdime.rainfern.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.rdime.rainfern.MainActivity
import dev.rdime.rainfern.data.local.ForecastCacheStore
import dev.rdime.rainfern.data.local.SettingsStore
import dev.rdime.rainfern.data.model.AggregatedForecast
import dev.rdime.rainfern.data.model.AppSettings
import dev.rdime.rainfern.data.model.DEVICE_LOCATION_KEY
import dev.rdime.rainfern.data.model.ForecastCache
import dev.rdime.rainfern.data.model.WIDGET_ACTIVE_LOCATION_KEY
import dev.rdime.rainfern.data.model.WidgetDiagnosticsMode
import dev.rdime.rainfern.data.model.WidgetThemeIntensity
import dev.rdime.rainfern.ui.formatClock
import dev.rdime.rainfern.ui.formatPressure
import dev.rdime.rainfern.ui.formatTemperature
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

object RainfernWidgets {
    internal val smallWidget = RainfernSmallWidget()
    internal val mediumWidget = RainfernMediumWidget()

    suspend fun updateAll(context: Context) {
        smallWidget.updateAll(context)
        mediumWidget.updateAll(context)
    }
}

class RainfernSmallWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val cache = ForecastCacheStore(context).read()
        val settings = SettingsStore(context).settings.first()
        provideContent {
            WidgetContent(
                forecast = resolveWidgetForecast(cache, settings),
                settings = settings,
                compact = true,
            )
        }
    }
}

class RainfernMediumWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: androidx.glance.GlanceId) {
        val cache = ForecastCacheStore(context).read()
        val settings = SettingsStore(context).settings.first()
        provideContent {
            WidgetContent(
                forecast = resolveWidgetForecast(cache, settings),
                settings = settings,
                compact = false,
            )
        }
    }
}

class RainfernSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RainfernWidgets.smallWidget
}

class RainfernMediumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RainfernWidgets.mediumWidget
}

@androidx.compose.runtime.Composable
private fun WidgetContent(
    forecast: AggregatedForecast?,
    settings: AppSettings,
    compact: Boolean,
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(widgetBackground(settings)))
            .padding(16.dp)
            .clickable(actionStartActivity<MainActivity>()),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (forecast == null) {
            Column {
                Text("Rainfern", style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(8.dp))
                Text(
                    "Open the app and refresh the selected widget location once.",
                    style = TextStyle(color = ColorProvider(widgetSupportColor(settings))),
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.Horizontal.Start) {
                Text(
                    "Rainfern",
                    style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold),
                )
                Text(
                    forecast.locationName,
                    style = TextStyle(color = ColorProvider(widgetMutedColor(settings))),
                )
                Spacer(GlanceModifier.height(8.dp))
                if (compact) {
                    Text(
                        formatTemperature(forecast.current.temperatureC, settings),
                        style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold),
                    )
                    Text(
                        widgetCompactDetail(forecast, settings),
                        style = TextStyle(color = ColorProvider(widgetSupportColor(settings))),
                    )
                } else {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Column {
                            Text(
                                formatTemperature(forecast.current.temperatureC, settings),
                                style = TextStyle(color = ColorProvider(Color.White), fontWeight = FontWeight.Bold),
                            )
                            Text(
                                forecast.current.conditionText,
                                style = TextStyle(color = ColorProvider(widgetSupportColor(settings))),
                            )
                        }
                        Spacer(GlanceModifier.width(16.dp))
                        Column {
                            Text(
                                forecast.hourly.firstOrNull()?.let {
                                    "Next: ${formatClock(it.time, settings)}"
                                } ?: "Next: --",
                                style = TextStyle(color = ColorProvider(widgetMutedColor(settings))),
                            )
                            Text(
                                widgetMediumDetail(forecast, settings),
                                style = TextStyle(color = ColorProvider(widgetMutedColor(settings))),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun resolveWidgetForecast(
    cache: ForecastCache,
    settings: AppSettings,
): AggregatedForecast? = when (settings.widgetLocationKey) {
    WIDGET_ACTIVE_LOCATION_KEY -> cache.latest
    else -> cache.entries.firstOrNull { it.locationKey == settings.widgetLocationKey }?.latest
        ?: cache.entries.firstOrNull { it.locationKey == DEVICE_LOCATION_KEY }?.latest
}

private fun widgetCompactDetail(
    forecast: AggregatedForecast,
    settings: AppSettings,
): String = when (settings.widgetDiagnosticsMode) {
    WidgetDiagnosticsMode.CLEAN -> forecast.current.conditionText
    WidgetDiagnosticsMode.SOURCES -> "Sources ${forecast.details.activeProviderIds.size}"
    WidgetDiagnosticsMode.CONFIDENCE -> "Confidence ${(forecast.details.overallConfidence * 100).roundToInt()}%"
}

private fun widgetMediumDetail(
    forecast: AggregatedForecast,
    settings: AppSettings,
): String = when (settings.widgetDiagnosticsMode) {
    WidgetDiagnosticsMode.CLEAN -> forecast.current.pressureHpa?.let { "Pressure ${formatPressure(it, settings)}" } ?: "Blended forecast"
    WidgetDiagnosticsMode.SOURCES -> "Sources ${forecast.details.activeProviderIds.size}"
    WidgetDiagnosticsMode.CONFIDENCE -> "Confidence ${(forecast.details.overallConfidence * 100).roundToInt()}%"
}

private fun widgetBackground(settings: AppSettings): Color = when (settings.widgetThemeIntensity) {
    WidgetThemeIntensity.SOFT -> Color(0xFF20313B)
    WidgetThemeIntensity.BALANCED -> Color(0xFF112737)
    WidgetThemeIntensity.VIVID -> Color(0xFF0A3150)
}

private fun widgetMutedColor(settings: AppSettings): Color = when (settings.widgetThemeIntensity) {
    WidgetThemeIntensity.SOFT -> Color(0xFFC2D6DC)
    WidgetThemeIntensity.BALANCED -> Color(0xFFBFD4E1)
    WidgetThemeIntensity.VIVID -> Color(0xFFC4E6FF)
}

private fun widgetSupportColor(settings: AppSettings): Color = when (settings.widgetThemeIntensity) {
    WidgetThemeIntensity.SOFT -> Color(0xFFD8EBDD)
    WidgetThemeIntensity.BALANCED -> Color(0xFFD2F1E2)
    WidgetThemeIntensity.VIVID -> Color(0xFFE2F6FF)
}
