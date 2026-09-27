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
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject

/**
 * Anthropic Admin API. Requires an admin key (sk-ant-admin...).
 * Usage: GET /v1/organizations/usage_report/messages (bucket_width=1d, group_by[]=model)
 * Cost:  GET /v1/organizations/cost_report           (bucket_width=1d, group_by[]=description)
 */
class AnthropicConnector(private val client: HttpClient) : ProviderConnector {
    override val id = ProviderId.ANTHROPIC
    override val displayName = "Anthropic"
    override val credentialHint = "Anthropic Admin API key (sk-ant-admin...) required"
    override val supportsUsageApi = true

    private val base = "https://api.anthropic.com/v1"

    override suspend fun fetchUsage(credentials: ProviderCredentials, range: DateRange): FetchResult {
        if (credentials.apiKey.isBlank()) return FetchResult.NotConfigured
        return try {
            val usage = fetchUsagePages(credentials, range)
            // Cost report is authoritative when available; otherwise fall back to
            // pricing estimation (costUsd stays null). A 403 etc. must not fail the provider.
            val costs = try {
                fetchCostPages(credentials, range)
            } catch (e: Exception) {
                emptyList()
            }
            val records = if (costs.isNotEmpty()) {
                usage.map { it.copy(costUsd = 0.0) } + costs
            } else {
                usage
            }
            FetchResult.Success(records)
        } catch (e: ApiException) {
            FetchResult.Failure(e.message ?: "HTTP ${e.status}")
        } catch (e: Exception) {
            FetchResult.Failure(e.message ?: e.toString())
        }
    }

    private suspend fun getJson(url: String, credentials: ProviderCredentials, params: Map<String, String>): JsonObject {
        val response = client.get(url) {
            header("x-api-key", credentials.apiKey)
            header("anthropic-version", "2023-06-01")
            params.forEach { (k, v) -> parameter(k, v) }
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) {
            throw ApiException(response.status.value, "HTTP ${response.status.value}: ${body.take(300)}")
        }
        return lenientJson.parseToJsonElement(body) as JsonObject
    }

    private suspend fun fetchUsagePages(credentials: ProviderCredentials, range: DateRange): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        var page: String? = null
        do {
            val params = mutableMapOf(
                "starting_at" to "${range.start}T00:00:00Z",
                "ending_at" to "${range.endInclusive}T23:59:59Z",
                "bucket_width" to "1d",
                "group_by[]" to "model",
                "limit" to "31",
            )
            page?.let { params["page"] = it }
            val root = getJson("$base/organizations/usage_report/messages", credentials, params)
            root.arr("data")?.forEach { bucket ->
                val bucketObj = bucket.asObjectOrNull() ?: return@forEach
                val date = bucketObj.text("starting_at")?.take(10)?.let(LocalDate::parse) ?: return@forEach
                bucketObj.arr("results")?.forEach { el ->
                    val r = el.asObjectOrNull() ?: return@forEach
                    val cacheCreation = r.obj("cache_creation")
                    val cacheWrite = (cacheCreation?.long("ephemeral_5m_input_tokens") ?: 0) +
                        (cacheCreation?.long("ephemeral_1h_input_tokens") ?: 0)
                    records += UsageRecord(
                        provider = id,
                        model = r.text("model") ?: "unknown",
                        date = date,
                        inputTokens = (r.long("uncached_input_tokens") ?: 0) + cacheWrite,
                        outputTokens = r.long("output_tokens") ?: 0,
                        cachedInputTokens = r.long("cache_read_input_tokens") ?: 0,
                        costUsd = null,
                    )
                }
            }
            page = if (root.bool("has_more") == true) root.text("next_page") else null
        } while (page != null)
        return records
    }

    private suspend fun fetchCostPages(credentials: ProviderCredentials, range: DateRange): List<UsageRecord> {
        val records = mutableListOf<UsageRecord>()
        var page: String? = null
        do {
            val params = mutableMapOf(
                "starting_at" to "${range.start}T00:00:00Z",
                "ending_at" to "${range.endInclusive}T23:59:59Z",
                "bucket_width" to "1d",
                "group_by[]" to "description",
                "limit" to "31",
            )
            page?.let { params["page"] = it }
            val root = getJson("$base/organizations/cost_report", credentials, params)
            root.arr("data")?.forEach { bucket ->
                val bucketObj = bucket.asObjectOrNull() ?: return@forEach
                val date = bucketObj.text("starting_at")?.take(10)?.let(LocalDate::parse) ?: return@forEach
                bucketObj.arr("results")?.forEach { el ->
                    val r = el.asObjectOrNull() ?: return@forEach
                    records += UsageRecord(
                        provider = id,
                        model = r.text("description") ?: r.text("cost_type") ?: "other",
                        date = date,
                        inputTokens = 0,
                        outputTokens = 0,
                        costUsd = r.money("amount") ?: r.money("cost_usd"),
                    )
                }
            }
            page = if (root.bool("has_more") == true) root.text("next_page") else null
        } while (page != null)
        return records
    }
}
