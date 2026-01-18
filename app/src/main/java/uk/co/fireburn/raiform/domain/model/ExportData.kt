package uk.co.fireburn.raiform.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ExportData(
    val clients: List<Client>,
    val sessions: List<Session>,
    val historyLogs: List<HistoryLog>,
    val bodyMeasurements: List<BodyMeasurement> = emptyList(),
    val exerciseDefinitions: Map<String, String> = emptyMap()
)
