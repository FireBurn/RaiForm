package uk.co.fireburn.raiform.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import uk.co.fireburn.raiform.domain.model.ExportData
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DataImporter @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        isLenient = true
    }

    suspend fun importFromJson(inputUri: Uri): ExportData {
        context.contentResolver.openInputStream(inputUri)?.use { inputStream ->
            val reader = BufferedReader(InputStreamReader(inputStream))
            val jsonString = reader.readText()
            return json.decodeFromString(jsonString)
        } ?: throw Exception("Failed to open input stream for URI: $inputUri")
    }
}
