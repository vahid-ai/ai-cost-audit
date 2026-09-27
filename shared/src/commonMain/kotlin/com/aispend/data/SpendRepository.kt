package com.aispend.data

import com.aispend.model.DateRange
import com.aispend.model.FetchResult
import com.aispend.model.ProviderConnector
import com.aispend.model.ProviderId
import com.aispend.model.UsageRecord
import com.aispend.pricing.CostEstimator
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.LocalDate

data class ProviderSpend(
    val costUsd: Double,
    val inputTokens: Long,
    val outputTokens: Long,
    val unpricedModels: Set<String>,
    /** True when at least one record's cost came from the pricing table, not the provider. */
    val estimated: Boolean,
)

data class ModelSpend(val model: String, val provider: ProviderId, val costUsd: Double, val tokens: Long)
data class DaySpend(val date: LocalDate, val costUsd: Double)

data class SpendSummary(
    val totalCostUsd: Double,
    val byProvider: Map<ProviderId, ProviderSpend>,
    val byModel: List<ModelSpend>,
    val byDay: List<DaySpend>,
    val errors: Map<ProviderId, String>,
    val notConfigured: Set<ProviderId>,
    val unsupported: Set<ProviderId>,
)

enum class ProviderStatus { OK, ERROR, NOT_CONFIGURED, UNSUPPORTED }

class SpendRepository(
    private val connectors: List<ProviderConnector>,
    private val credentialsStore: CredentialsStore,
    private val manualUsageStore: ManualUsageStore,
    private val extraRecords: suspend (DateRange) -> List<UsageRecord> = { emptyList() },
) {
    private val cache = mutableMapOf<DateRange, SpendSummary>()

    /** Drops all cached summaries; call after records change (manual entry, demo data). */
    fun invalidate() = cache.clear()

    suspend fun summary(range: DateRange, forceRefresh: Boolean = false): SpendSummary {
        if (!forceRefresh) cache[range]?.let { return it }
        val (records, errors, notConfigured, unsupported) = fetchAll(range)
        val manual = manualUsageStore.records(range)
        val all = records + manual + extraRecords(range)
        val summary = aggregate(all, errors, notConfigured, unsupported)
        cache[range] = summary
        return summary
    }

    private data class FetchOutcome(
        val records: List<UsageRecord>,
        val errors: Map<ProviderId, String>,
        val notConfigured: Set<ProviderId>,
        val unsupported: Set<ProviderId>,
    )

    private suspend fun fetchAll(range: DateRange): FetchOutcome = coroutineScope {
        val deferred = connectors.map { connector ->
            async {
                val creds = credentialsStore.get(connector.id)
                val result = when {
                    !connector.supportsUsageApi -> FetchResult.Unsupported
                    creds == null -> FetchResult.NotConfigured
                    else -> connector.fetchUsage(creds, range)
                }
                connector.id to result
            }
        }
        val records = mutableListOf<UsageRecord>()
        val errors = mutableMapOf<ProviderId, String>()
        val notConfigured = mutableSetOf<ProviderId>()
        val unsupported = mutableSetOf<ProviderId>()
        deferred.awaitAll().forEach { (id, result) ->
            when (result) {
                is FetchResult.Success -> records += result.records
                is FetchResult.Failure -> errors[id] = result.message
                FetchResult.NotConfigured -> notConfigured += id
                FetchResult.Unsupported -> unsupported += id
            }
        }
        FetchOutcome(records, errors, notConfigured, unsupported)
    }

    internal fun aggregate(
        records: List<UsageRecord>,
        errors: Map<ProviderId, String>,
        notConfigured: Set<ProviderId>,
        unsupported: Set<ProviderId>,
    ): SpendSummary {
        val costOf = { r: UsageRecord -> CostEstimator.estimate(r) ?: 0.0 }
        val byProvider = records.groupBy { it.provider }.mapValues { (_, recs) ->
            ProviderSpend(
                costUsd = recs.sumOf(costOf),
                inputTokens = recs.sumOf { it.inputTokens },
                outputTokens = recs.sumOf { it.outputTokens },
                unpricedModels = recs.filter { CostEstimator.estimate(it) == null }.mapTo(mutableSetOf()) { it.model },
                estimated = recs.any { it.costUsd == null && CostEstimator.estimate(it) != null },
            )
        }
        val byModel = records.groupBy { it.provider to it.model }.map { (key, recs) ->
            ModelSpend(
                model = key.second,
                provider = key.first,
                costUsd = recs.sumOf(costOf),
                tokens = recs.sumOf { it.inputTokens + it.outputTokens + it.cachedInputTokens },
            )
        }.sortedByDescending { it.costUsd }
        val byDay = records.groupBy { it.date }.map { (date, recs) ->
            DaySpend(date, recs.sumOf(costOf))
        }.sortedBy { it.date }
        return SpendSummary(
            totalCostUsd = byProvider.values.sumOf { it.costUsd },
            byProvider = byProvider,
            byModel = byModel,
            byDay = byDay,
            errors = errors,
            notConfigured = notConfigured,
            unsupported = unsupported,
        )
    }
}
