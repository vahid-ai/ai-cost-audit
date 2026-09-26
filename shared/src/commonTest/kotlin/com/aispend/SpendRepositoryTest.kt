package com.aispend

import com.aispend.data.CredentialsStore
import com.aispend.data.ManualUsageStore
import com.aispend.data.SpendRepository
import com.aispend.model.DateRange
import com.aispend.model.FetchResult
import com.aispend.model.ProviderConnector
import com.aispend.model.ProviderCredentials
import com.aispend.model.ProviderId
import com.aispend.model.UsageRecord
import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeConnector(
    override val id: ProviderId,
    private val result: FetchResult,
) : ProviderConnector {
    override val displayName = id.name
    override val credentialHint = ""
    override val supportsUsageApi = true
    override suspend fun fetchUsage(credentials: ProviderCredentials, range: DateRange) = result
}

class SpendRepositoryTest {
    private val range = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 2))

    @Test
    fun aggregatesAcrossConnectorsAndStatuses() = runTest {
        val settings = MapSettings()
        val creds = CredentialsStore(settings)
        // Configure keys for all connectors so they actually get called.
        listOf(ProviderId.OPENAI, ProviderId.ANTHROPIC, ProviderId.DEEPSEEK, ProviderId.XAI).forEach {
            creds.save(it, ProviderCredentials("key-$it"))
        }
        val connectors = listOf(
            FakeConnector(
                ProviderId.OPENAI,
                FetchResult.Success(
                    listOf(
                        UsageRecord(ProviderId.OPENAI, "gpt-5", LocalDate(2026, 9, 1), 1_000_000, 1_000_000),
                        UsageRecord(ProviderId.OPENAI, "gpt-5", LocalDate(2026, 9, 2), 1_000_000, 0),
                    )
                ),
            ),
            FakeConnector(
                ProviderId.ANTHROPIC,
                FetchResult.Success(
                    listOf(
                        UsageRecord(ProviderId.ANTHROPIC, "claude-sonnet-4-5", LocalDate(2026, 9, 1), 0, 0, costUsd = 4.50)
                    )
                ),
            ),
            FakeConnector(ProviderId.DEEPSEEK, FetchResult.Failure("HTTP 500: boom")),
            FakeConnector(ProviderId.XAI, FetchResult.NotConfigured),
        )
        val repo = SpendRepository(connectors, creds, ManualUsageStore(MapSettings()))
        val summary = repo.summary(range)

        // gpt-5: (1M in + 1M out) => 11.25, + (1M in) => 1.25 ; anthropic 4.50
        assertEquals(17.0, summary.totalCostUsd, 0.001)
        assertEquals(2, summary.byProvider.size)
        assertEquals("gpt-5", summary.byModel.first().model)
        assertEquals(2, summary.byDay.size)
        assertEquals("HTTP 500: boom", summary.errors[ProviderId.DEEPSEEK])
        assertTrue(summary.notConfigured.contains(ProviderId.XAI))
        assertTrue(summary.unsupported.isEmpty())
    }

    @Test
    fun uncalledConnectorWithoutKeyIsNotConfigured() = runTest {
        val creds = CredentialsStore(MapSettings())
        val connectors = listOf(FakeConnector(ProviderId.OPENAI, FetchResult.Failure("should not be called")))
        val repo = SpendRepository(connectors, creds, ManualUsageStore(MapSettings()))
        val summary = repo.summary(range)
        assertTrue(summary.notConfigured.contains(ProviderId.OPENAI))
        assertTrue(summary.errors.isEmpty())
    }
}
