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
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.datetime.DatePeriod
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.JsonObject

/**
 * OpenAI Admin API. Requires an organisation admin key (sk-admin-...).
 * Costs:   GET /v1/organization/costs            (daily buckets, group_by=line_item)
 * Usage:   GET /v1/organization/usage/completions (daily buckets, group_by=model)
 */
class OpenAiConnector(private val client: HttpClient) : ProviderConnector {
    override val id = ProviderId.OPENAI
    override val displayName = "OpenAI"
    override val credentialHint = "OpenAI Admin API key (sk-admin-...) required; org ID optional"
    override val supportsUsageApi = true

    private val base = "https://api.openai.com/v1"
    private val utc = TimeZone.UTC

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
            header(HttpHeaders.Authorization, "Bearer ${credentials.apiKey}")
            credentials.organizationId?.takeIf { it.isNotBlank() }?.let {
                header("OpenAI-Organization", it)
            }
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
                "start_time" to range.start.atStartOfDayIn(utc).epochSeconds.toString(),
                "end_time" to range.endInclusive.plus(DatePeriod(days = 1)).atStartOfDayIn(utc).epochSeconds.toString(),
                "bucket_width" to "1d",
                "group_by[]" to "model",
                "limit" to "31",
            )
            page?.let { params["page"] = it }
            val root = getJson("$base/organization/usage/completions", credentials, params)
            root.arr("data")?.forEach { bucket ->
                val bucketObj = bucket.asObjectOrNull() ?: return@forEach
                val date = epochToDate(bucketObj.long("start_time")) ?: return@forEach
                bucketObj.arr("results")?.forEach { el ->
                    val r = el.asObjectOrNull() ?: return@forEach
                    records += UsageRecord(
                        provider = id,
                        model = r.text("model") ?: "unknown",
                        date = date,
                        inputTokens = r.long("input_tokens") ?: 0,
                        outputTokens = r.long("output_tokens") ?: 0,
                        cachedInputTokens = r.long("input_cached_tokens") ?: 0,
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
                "start_time" to range.start.atStartOfDayIn(utc).epochSeconds.toString(),
                "end_time" to range.endInclusive.plus(DatePeriod(days = 1)).atStartOfDayIn(utc).epochSeconds.toString(),
                "bucket_width" to "1d",
                "group_by[]" to "line_item",
                "limit" to "31",
            )
            page?.let { params["page"] = it }
            val root = getJson("$base/organization/costs", credentials, params)
            root.arr("data")?.forEach { bucket ->
                val bucketObj = bucket.asObjectOrNull() ?: return@forEach
                val date = epochToDate(bucketObj.long("start_time")) ?: return@forEach
                bucketObj.arr("results")?.forEach { el ->
                    val r = el.asObjectOrNull() ?: return@forEach
                    val amount = r.obj("amount")?.money("value") ?: 0.0
                    records += UsageRecord(
                        provider = id,
                        model = r.text("line_item") ?: "unknown",
                        date = date,
                        inputTokens = 0,
                        outputTokens = 0,
                        costUsd = amount,
                    )
                }
            }
            page = if (root.bool("has_more") == true) root.text("next_page") else null
        } while (page != null)
        return records
    }

    private fun epochToDate(epochSeconds: Long?): LocalDate? = epochSeconds?.let {
        Instant.fromEpochSeconds(it).toLocalDateTime(utc).date
    }
}

internal class ApiException(val status: Int, message: String) : Exception(message)
