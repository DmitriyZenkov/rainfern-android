package dev.rdime.rainfern.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.rdime.rainfern.RainfernApplication
import dev.rdime.rainfern.data.notification.WeatherNotificationManager
import dev.rdime.rainfern.widget.RainfernWidgets

class RefreshWeatherWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = runCatching {
        val application = applicationContext as RainfernApplication
        val payload = application.container.weatherRepository.refresh()
        WeatherNotificationManager(applicationContext).evaluate(payload)
        RainfernWidgets.updateAll(applicationContext)
        Result.success()
    }.getOrElse {
        Result.retry()
    }
}
