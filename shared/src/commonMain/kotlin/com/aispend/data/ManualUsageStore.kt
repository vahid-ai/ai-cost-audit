package com.aispend.data

import com.aispend.model.DateRange
import com.aispend.model.ProviderId
import com.aispend.model.UsageRecord
import com.russhwolf.settings.Settings
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** User-entered usage for providers without a usage API. Stored as JSON in settings. */
class ManualUsageStore(private val settings: Settings) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ManualEntry.serializer())

    @Serializable
    private data class ManualEntry(
        val provider: String,
        val model: String,
        val date: String,
        val inputTokens: Long,
        val outputTokens: Long,
        val cachedInputTokens: Long = 0,
    )

    fun records(range: DateRange): List<UsageRecord> = loadAll().mapNotNull { e ->
        val provider = runCatching { ProviderId.valueOf(e.provider) }.getOrNull() ?: return@mapNotNull null
        val date = runCatching { LocalDate.parse(e.date) }.getOrNull() ?: return@mapNotNull null
        if (date < range.start || date > range.endInclusive) return@mapNotNull null
        UsageRecord(
            provider = provider,
            model = e.model,
            date = date,
            inputTokens = e.inputTokens,
            outputTokens = e.outputTokens,
            cachedInputTokens = e.cachedInputTokens,
            costUsd = null,
        )
    }

    fun add(record: UsageRecord) {
        val all = loadAll().toMutableList()
        all += ManualEntry(
            provider = record.provider.name,
            model = record.model,
            date = record.date.toString(),
            inputTokens = record.inputTokens,
            outputTokens = record.outputTokens,
            cachedInputTokens = record.cachedInputTokens,
        )
        settings.putString(KEY, json.encodeToString(serializer, all))
    }

    fun clear() = settings.remove(KEY)

    private fun loadAll(): List<ManualEntry> {
        val raw = settings.getStringOrNull(KEY) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private companion object {
        const val KEY = "manualUsageEntries"
    }
}
