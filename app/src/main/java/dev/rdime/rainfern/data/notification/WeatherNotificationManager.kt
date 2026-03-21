package dev.rdime.rainfern.data.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.rdime.rainfern.MainActivity
import dev.rdime.rainfern.R
import dev.rdime.rainfern.data.model.AggregatedForecast
import dev.rdime.rainfern.data.model.DashboardPayload
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.roundToInt

class WeatherNotificationManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val store = NotificationStore(appContext)

    fun evaluate(payload: DashboardPayload) {
        if (!hasNotificationPermission() || !NotificationManagerCompat.from(appContext).areNotificationsEnabled()) {
            return
        }
        val latest = payload.cache.latest ?: return
        ensureChannel()
        if (payload.settings.notifyRainSoon) {
            maybeSendRainSoon(latest)
        }
        if (payload.settings.notifyFreezing) {
            maybeSendFreezing(latest)
        }
        if (payload.settings.notifyStrongWind) {
            maybeSendStrongWind(latest)
        }
        if (payload.settings.notifyMorningSummary) {
            maybeSendMorningSummary(latest)
        }
    }

    private fun maybeSendRainSoon(forecast: AggregatedForecast) {
        val target = upcomingHours(forecast, 6).firstOrNull {
            (it.precipitationProbabilityPercent ?: 0) >= 65 || (it.precipitationMm ?: 0.0) >= 0.6
        } ?: return
        val key = "rain_soon:${forecast.locationName}"
        if (!store.canSendWithinWindow(key, minimumMinutes = 180)) {
            return
        }
        val minutesAway = java.time.Duration.between(java.time.Instant.now(), target.time).toMinutes().coerceAtLeast(0)
        send(
            key = key,
            title = "Rain soon in ${forecast.locationName}",
            text = if (minutesAway < 60) {
                "Rain may start in about $minutesAway min."
            } else {
                "Rain signal is building in about ${minutesAway / 60} h."
            },
        )
    }

    private fun maybeSendFreezing(forecast: AggregatedForecast) {
        val coldest = upcomingHours(forecast, 12).mapNotNull { it.temperatureC }.minOrNull() ?: return
        if (coldest > 0.0) {
            return
        }
        val key = "freezing:${forecast.locationName}"
        if (!store.canSendWithinWindow(key, minimumMinutes = 360)) {
            return
        }
        send(
            key = key,
            title = "Freezing conditions near ${forecast.locationName}",
            text = "The next 12 hours may reach ${coldest.roundToInt()}°C. Watch for icy surfaces.",
        )
    }

    private fun maybeSendStrongWind(forecast: AggregatedForecast) {
        val strongest = upcomingHours(forecast, 12).mapNotNull { it.windSpeedMps }.maxOrNull() ?: return
        if (strongest < 12.0) {
            return
        }
        val key = "strong_wind:${forecast.locationName}"
        if (!store.canSendWithinWindow(key, minimumMinutes = 360)) {
            return
        }
        send(
            key = key,
            title = "Strong wind near ${forecast.locationName}",
            text = "Peak wind in the next 12 hours may reach ${(strongest * 3.6).roundToInt()} km/h.",
        )
    }

    private fun maybeSendMorningSummary(forecast: AggregatedForecast) {
        val zoneId = forecastZoneId(forecast)
        val now = LocalTime.now(zoneId)
        if (now.isBefore(LocalTime.of(6, 0)) || now.isAfter(LocalTime.of(10, 30))) {
            return
        }
        val today = forecast.daily.firstOrNull()
        val key = "morning_summary:${forecast.locationName}"
        val todayDate = LocalDate.now(zoneId)
        if (!store.canSendDaily(key, todayDate)) {
            return
        }
        val currentTemp = forecast.current.temperatureC?.roundToInt()?.let { "$it°C" } ?: "--"
        val high = today?.maxTempC?.roundToInt()?.let { "$it°C" } ?: "--"
        val low = today?.minTempC?.roundToInt()?.let { "$it°C" } ?: "--"
        send(
            key = key,
            title = "Morning weather for ${forecast.locationName}",
            text = "$currentTemp now, ${forecast.current.conditionText.lowercase()}. Today: $low to $high.",
            daily = true,
            dailyDate = todayDate,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rainfern weather alerts",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Rainfern weather alerts and summaries"
        }
        manager.createNotificationChannel(channel)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private fun send(
        key: String,
        title: String,
        text: String,
        daily: Boolean = false,
        dailyDate: LocalDate? = null,
    ) {
        val intent = Intent(appContext, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(appContext).notify(key.hashCode(), notification)
        if (daily) {
            store.markSentDaily(key, dailyDate ?: LocalDate.now())
        } else {
            store.markSent(key)
        }
    }

    private fun upcomingHours(
        forecast: AggregatedForecast,
        count: Int,
    ) = forecast.hourly
        .filter { !it.time.isBefore(Instant.now()) }
        .take(count)

    private fun forecastZoneId(forecast: AggregatedForecast): ZoneId = runCatching {
        ZoneId.of(forecast.timeZoneId.ifBlank { ZoneId.systemDefault().id })
    }.getOrElse {
        ZoneId.systemDefault()
    }

    private fun hasNotificationPermission(): Boolean = when {
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU -> true
        else -> ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val CHANNEL_ID = "rainfern_weather"
    }
}
