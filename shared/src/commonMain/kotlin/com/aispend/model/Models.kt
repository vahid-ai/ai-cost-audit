package com.aispend.model

import kotlinx.datetime.LocalDate

enum class ProviderId {
    OPENAI,
    ANTHROPIC,
    OPENROUTER,
    GOOGLE_GEMINI,
    MISTRAL,
    DEEPSEEK,
    GROQ,
    XAI,
    TOGETHER,
}

data class UsageRecord(
    val provider: ProviderId,
    val model: String,
    val date: LocalDate,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedInputTokens: Long = 0,
    /** Null means the cost should be estimated via PricingTable. */
    val costUsd: Double? = null,
)

data class DateRange(val start: LocalDate, val endInclusive: LocalDate)

sealed interface FetchResult {
    data class Success(val records: List<UsageRecord>) : FetchResult
    data class Failure(val message: String) : FetchResult
    data object NotConfigured : FetchResult
    data object Unsupported : FetchResult
}

data class ProviderCredentials(
    val apiKey: String,
    val organizationId: String? = null,
)

interface ProviderConnector {
    val id: ProviderId
    val displayName: String
    val credentialHint: String
    val supportsUsageApi: Boolean

    suspend fun fetchUsage(credentials: ProviderCredentials, range: DateRange): FetchResult
}
