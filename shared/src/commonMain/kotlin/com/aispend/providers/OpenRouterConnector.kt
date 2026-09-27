package com.aispend.providers

import com.aispend.model.DateRange
import com.aispend.model.FetchResult
import com.aispend.model.ProviderConnector
import com.aispend.model.ProviderCredentials
import com.aispend.model.ProviderId
import com.aispend.model.UsageRecord
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject

/**
 * OpenRouter API. Bearer key (sk-or-...).
 * Credits:  GET /api/v1/credits  -> { data: { total_credits, total_usage } }
 * Activity: GET /api/v1/activity -> per-day per-model usage rows.
 */
class OpenRouterConnector(private val client: HttpClient) : ProviderConnector {
    override val id = ProviderId.OPENROUTER
    override val displayName = "OpenRouter"
    override val credentialHint = "OpenRouter API key (sk-or-...) required"
    override val supportsUsageApi = true

    private val base = "https://openrouter.ai/api/v1"

    override suspend fun fetchUsage(credentials: ProviderCredentials, range: DateRange): FetchResult {
        if (credentials.apiKey.isBlank()) return FetchResult.NotConfigured
        return try {
            val records = fetchActivity(credentials, range)
            if (records != null) {
                FetchResult.Success(records)
            } else {
                // Fall back to lifetime credit usage as a single record.
                val data = getJson("$base/credits", credentials).obj("data")
                val totalUsage = data?.money("total_usage")
                if (totalUsage != null && totalUsage > 0) {
                    FetchResult.Success(
                        listOf(
                            UsageRecord(
                                provider = id,
                                model = "all-models",
                                date = range.endInclusive,
                                inputTokens = 0,
                                outputTokens = 0,
                                costUsd = totalUsage,
                            )
                        )
                    )
                } else {
                    FetchResult.Success(emptyList())
                }
            }
        } catch (e: ApiException) {
            FetchResult.Failure(e.message ?: "HTTP ${e.status}")
        } catch (e: Exception) {
            FetchResult.Failure(e.message ?: e.toString())
        }
    }

    private suspend fun getJson(url: String, credentials: ProviderCredentials): JsonObject {
        val response = client.get(url) {
            header(HttpHeaders.Authorization, "Bearer ${credentials.apiKey}")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, "HTTP ${response.status.value}: ${body.take(300)}")
        }
        return lenientJson.parseToJsonElement(body) as JsonObject
    }

    /** Returns null when the activity endpoint is unavailable (404 etc.). */
    private suspend fun fetchActivity(credentials: ProviderCredentials, range: DateRange): List<UsageRecord>? {
        val response = client.get("$base/activity") {
            header(HttpHeaders.Authorization, "Bearer ${credentials.apiKey}")
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) return null
        val root = lenientJson.parseToJsonElement(body) as? JsonObject ?: return null
        val rows = root.arr("activity") ?: root.arr("data") ?: return null
        val records = mutableListOf<UsageRecord>()
        rows.forEach { el ->
            val r = el.asObjectOrNull() ?: return@forEach
            val date = r.text("date")?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return@forEach
            if (date < range.start || date > range.endInclusive) return@forEach
            val model = r.text("model_permaslug") ?: r.text("model") ?: "unknown"
            val cost = r.money("usage") ?: r.money("cost") ?: r.money("cost_usd")
            records += UsageRecord(
                provider = id,
                model = model,
                date = date,
                inputTokens = r.long("prompt_tokens") ?: r.long("input_tokens") ?: 0,
                outputTokens = r.long("completion_tokens") ?: r.long("output_tokens") ?: 0,
                costUsd = cost,
            )
        }
        return records
    }
}
