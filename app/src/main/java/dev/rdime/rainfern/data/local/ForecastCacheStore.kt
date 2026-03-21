package dev.rdime.rainfern.data.local

import android.content.Context
import android.util.AtomicFile
import dev.rdime.rainfern.data.model.AggregatedForecast
import dev.rdime.rainfern.data.model.Coordinates
import dev.rdime.rainfern.data.model.ForecastCache
import dev.rdime.rainfern.data.model.PlaceForecastCache
import dev.rdime.rainfern.data.model.ProviderForecast
import dev.rdime.rainfern.data.model.SnapshotRecord
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class ForecastCacheStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "rainfern-cache.json"))
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun read(): ForecastCache = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) {
            return@withContext ForecastCache()
        }
        runCatching {
            json.decodeFromString<ForecastCache>(file.readFully().decodeToString())
        }.getOrElse { ForecastCache() }
    }

    suspend fun write(cache: ForecastCache) = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(ForecastCache.serializer(), cache).encodeToByteArray()
        var output = file.startWrite()
        try {
            output.write(payload)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }

    suspend fun setActiveLocation(
        locationKey: String,
        locationLabel: String = "",
    ) {
        val current = read()
        val nextEntries = if (current.entries.any { it.locationKey == locationKey } || locationLabel.isBlank()) {
            current.entries
        } else {
            current.entries + PlaceForecastCache(
                locationKey = locationKey,
                locationLabel = locationLabel,
            )
        }
        write(current.copy(activeLocationKey = locationKey, entries = nextEntries))
    }

    suspend fun writeLatest(
        latest: AggregatedForecast,
        providers: List<ProviderForecast>,
        coordinates: Coordinates,
        locationKey: String,
        locationLabel: String,
    ) {
        val current = read()
        val existing = current.entries.firstOrNull { it.locationKey == locationKey }
        val nextHistory = buildList {
            add(
                SnapshotRecord(
                    capturedAt = latest.fetchedAt,
                    locationName = latest.locationName,
                    currentTempC = latest.current.temperatureC,
                    summary = latest.current.conditionText,
                    confidence = latest.details.overallConfidence,
                ),
            )
            addAll(existing?.history.orEmpty())
        }.take(120)
        val nextEntry = PlaceForecastCache(
            locationKey = locationKey,
            locationLabel = locationLabel.ifBlank { existing?.locationLabel ?: latest.locationName },
            latest = latest,
            providers = providers,
            history = nextHistory,
            lastCoordinates = coordinates,
        )
        write(
            current.copy(
                activeLocationKey = locationKey,
                entries = listOf(nextEntry) + current.entries.filterNot { it.locationKey == locationKey },
            ),
        )
    }
}
