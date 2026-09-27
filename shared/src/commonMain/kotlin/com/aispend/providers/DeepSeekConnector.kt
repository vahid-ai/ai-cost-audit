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
import kotlinx.serialization.json.JsonObject

/**
 * DeepSeek API. Bearer key (sk-...).
 * GET /user/balance -> { is_available, balance_infos: [{ currency, total_balance,
 *                        granted_balance, topped_up_balance }] }
 * There is no per-day usage API, so we report consumed balance (granted + topped_up - total)
 * as a single record on the range end date.
 */
class DeepSeekConnector(private val client: HttpClient) : ProviderConnector {
    override val id = ProviderId.DEEPSEEK
    override val displayName = "DeepSeek"
    override val credentialHint = "DeepSeek API key required; only balance data is exposed"
    override val supportsUsageApi = true

    override suspend fun fetchUsage(credentials: ProviderCredentials, range: DateRange): FetchResult {
        if (credentials.apiKey.isBlank()) return FetchResult.NotConfigured
        return try {
            val response = client.get("https://api.deepseek.com/user/balance") {
                header(HttpHeaders.Authorization, "Bearer ${credentials.apiKey}")
            }
            val body = response.bodyAsText()
            if (!response.status.isSuccess()) {
                return FetchResult.Failure("HTTP ${response.status.value}: ${body.take(300)}")
            }
            val root = lenientJson.parseToJsonElement(body) as JsonObject
            val records = mutableListOf<UsageRecord>()
            root.arr("balance_infos")?.forEach { el ->
                val r = el.asObjectOrNull() ?: return@forEach
                val total = r.money("total_balance") ?: return@forEach
                val granted = r.money("granted_balance") ?: 0.0
                val toppedUp = r.money("topped_up_balance") ?: 0.0
                val consumed = (granted + toppedUp) - total
                if (consumed > 0) {
                    records += UsageRecord(
                        provider = id,
                        model = "balance-consumed",
                        date = range.endInclusive,
                        inputTokens = 0,
                        outputTokens = 0,
                        costUsd = consumed,
                    )
                }
            }
            FetchResult.Success(records)
        } catch (e: Exception) {
            FetchResult.Failure(e.message ?: e.toString())
        }
    }
}
