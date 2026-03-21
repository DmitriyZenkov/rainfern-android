package dev.rdime.rainfern.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.rdime.rainfern.data.model.AppSettings
import dev.rdime.rainfern.data.model.LocationMode
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    private const val WORK_NAME = "rainfern-periodic-refresh"

    fun sync(context: Context, settings: AppSettings) {
        val workManager = WorkManager.getInstance(context)
        if (settings.locationMode != LocationMode.BACKGROUND) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }

        val request = PeriodicWorkRequestBuilder<RefreshWeatherWorker>(
            settings.backgroundRefreshInterval.minutes,
            TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(
                        if (settings.backgroundWifiOnly) {
                            NetworkType.UNMETERED
                        } else {
                            NetworkType.CONNECTED
                        },
                    )
                    .setRequiresBatteryNotLow(settings.backgroundBatteryAware)
                    .build(),
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }
}
