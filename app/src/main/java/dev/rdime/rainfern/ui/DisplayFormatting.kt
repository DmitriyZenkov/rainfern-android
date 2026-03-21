package dev.rdime.rainfern.ui

import dev.rdime.rainfern.data.model.AppSettings
import dev.rdime.rainfern.data.model.PressureUnit
import dev.rdime.rainfern.data.model.TemperatureUnit
import dev.rdime.rainfern.data.model.TimeFormatPreference
import dev.rdime.rainfern.data.model.WindUnit
import java.time.Instant
import java.time.LocalDate
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.roundToInt

internal fun formatTemperature(
    value: Double?,
    settings: AppSettings,
): String {
    val reading = value ?: return "--"
    return when (settings.temperatureUnit) {
        TemperatureUnit.CELSIUS -> "${reading.roundToInt()}°C"
        TemperatureUnit.FAHRENHEIT -> "${((reading * 9.0 / 5.0) + 32.0).roundToInt()}°F"
    }
}

internal fun formatWindSpeed(
    valueMps: Double?,
    settings: AppSettings,
): String {
    val reading = valueMps ?: return "--"
    val locale = Locale.getDefault()
    return when (settings.windUnit) {
        WindUnit.MPS -> "${reading.roundToInt()} m/s"
        WindUnit.KPH -> String.format(locale, "%.0f km/h", reading * 3.6)
        WindUnit.MPH -> String.format(locale, "%.0f mph", reading * 2.23694)
    }
}

internal fun formatPressure(
    valueHpa: Double?,
    settings: AppSettings,
): String {
    val reading = valueHpa ?: return "--"
    val locale = Locale.getDefault()
    return when (settings.pressureUnit) {
        PressureUnit.HPA -> "${reading.roundToInt()} hPa"
        PressureUnit.MMHG -> String.format(locale, "%.0f mmHg", reading * 0.750062)
        PressureUnit.INHG -> String.format(locale, "%.2f inHg", reading * 0.02953)
    }
}

internal fun formatClock(
    instant: Instant,
    settings: AppSettings,
    timeZoneId: String? = null,
): String = formatClock(instant.atZone(resolveDisplayZoneId(timeZoneId)), settings)

internal fun formatClock(
    dateTime: ZonedDateTime,
    settings: AppSettings,
): String = clockFormatter(settings).format(dateTime)

internal fun formatDayName(
    date: LocalDate,
    settings: AppSettings,
): String = when (settings.timeFormatPreference) {
    TimeFormatPreference.SYSTEM,
    TimeFormatPreference.H24,
    TimeFormatPreference.H12 -> date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault()))
}

internal fun formatHistoryTimestamp(
    instant: Instant,
    settings: AppSettings,
    timeZoneId: String? = null,
): String {
    val zoned = instant.atZone(resolveDisplayZoneId(timeZoneId))
    val dateLabel = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault()).format(zoned)
    return "$dateLabel ${clockFormatter(settings).format(zoned)}"
}

internal fun formatAge(instant: Instant): String {
    val elapsed = Duration.between(instant, Instant.now()).coerceAtLeast(Duration.ZERO)
    val minutes = elapsed.toMinutes()
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes} min ago"
        minutes < 1_440 -> "${elapsed.toHours()} h ago"
        else -> "${elapsed.toDays()} d ago"
    }
}

private fun clockFormatter(settings: AppSettings): DateTimeFormatter {
    val locale = Locale.getDefault()
    return when (settings.timeFormatPreference) {
        TimeFormatPreference.SYSTEM -> DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        TimeFormatPreference.H24 -> DateTimeFormatter.ofPattern("HH:mm", locale)
        TimeFormatPreference.H12 -> DateTimeFormatter.ofPattern("h:mm a", locale)
    }
}

private fun resolveDisplayZoneId(timeZoneId: String?): ZoneId = runCatching {
    ZoneId.of(timeZoneId.orEmpty().ifBlank { ZoneId.systemDefault().id })
}.getOrElse {
    ZoneId.systemDefault()
}
