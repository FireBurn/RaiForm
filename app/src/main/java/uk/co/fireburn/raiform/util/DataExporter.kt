package uk.co.fireburn.raiform.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import uk.co.fireburn.raiform.domain.model.ExportData
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        prettyPrint = true // For human-readable export
        ignoreUnknownKeys = true
    }

    suspend fun exportToJson(data: ExportData, outputUri: Uri) {
        context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
            val jsonString = json.encodeToString(data)
            outputStream.write(jsonString.toByteArray())
        } ?: throw Exception("Failed to open output stream for URI: $outputUri")
    }
}
