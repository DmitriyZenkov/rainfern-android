package dev.rdime.rainfern.data.local

import android.content.Context
import android.util.AtomicFile
import dev.rdime.rainfern.data.model.PlacesState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class PlacesStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "rainfern-places.json"))
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun read(): PlacesState = withContext(Dispatchers.IO) {
        if (!file.baseFile.exists()) {
            return@withContext PlacesState()
        }
        runCatching {
            json.decodeFromString<PlacesState>(file.readFully().decodeToString())
        }.getOrElse { PlacesState() }
    }

    suspend fun write(state: PlacesState) = withContext(Dispatchers.IO) {
        val payload = json.encodeToString(PlacesState.serializer(), state).encodeToByteArray()
        var output = file.startWrite()
        try {
            output.write(payload)
            file.finishWrite(output)
        } catch (error: Throwable) {
            file.failWrite(output)
            throw error
        }
    }
}
