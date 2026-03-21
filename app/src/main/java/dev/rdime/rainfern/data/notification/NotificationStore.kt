package dev.rdime.rainfern.data.notification

import android.content.Context
import java.time.Instant
import java.time.LocalDate

class NotificationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("rainfern_notifications", Context.MODE_PRIVATE)

    fun canSendWithinWindow(key: String, minimumMinutes: Long): Boolean {
        val lastEpoch = prefs.getLong(key, 0L)
        if (lastEpoch == 0L) {
            return true
        }
        val elapsedMinutes = java.time.Duration.between(Instant.ofEpochMilli(lastEpoch), Instant.now()).toMinutes()
        return elapsedMinutes >= minimumMinutes
    }

    fun markSent(key: String) {
        prefs.edit().putLong(key, System.currentTimeMillis()).apply()
    }

    fun canSendDaily(key: String, date: LocalDate): Boolean =
        prefs.getString(key, "") != date.toString()

    fun markSentDaily(key: String, date: LocalDate) {
        prefs.edit().putString(key, date.toString()).apply()
    }
}
