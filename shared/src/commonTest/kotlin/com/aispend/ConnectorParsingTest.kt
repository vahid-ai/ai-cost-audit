package com.aispend

import com.aispend.model.FetchResult
import com.aispend.model.ProviderCredentials
import com.aispend.model.ProviderId
import com.aispend.model.DateRange
import com.aispend.providers.AnthropicConnector
import com.aispend.providers.DeepSeekConnector
import com.aispend.providers.OpenAiConnector
import com.aispend.providers.OpenRouterConnector
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
private val range = DateRange(LocalDate(2026, 9, 1), LocalDate(2026, 9, 3))
private val creds = ProviderCredentials(apiKey = "test-key")

private fun mockClient(vararg bodies: String, status: HttpStatusCode = HttpStatusCode.OK): HttpClient {
    val queue = ArrayDeque(bodies.toList())
    val engine = MockEngine { _ ->
        respond(
            content = queue.removeFirstOrNull() ?: "{}",
            status = status,
            headers = jsonHeaders,
        )
    }
    return HttpClient(engine)
}

class OpenAiConnectorTest {
    private val usagePage = """
        {
          "object": "page",
          "data": [
            {
              "object": "bucket",
              "start_time": 1788307200,
              "end_time": 1788393600,
              "results": [
                {"object":"organization.usage.completions.result","model":"gpt-5","input_tokens":1000,"output_tokens":500,"input_cached_tokens":200,"num_model_requests":3}
              ]
            }
          ],
          "has_more": false,
          "next_page": null
        }
    """.trimIndent()

    private val costPage = """
        {
          "object": "page",
          "data": [
            {
              "object": "bucket",
              "start_time": 1788307200,
              "end_time": 1788393600,
              "results": [
                {"object":"organization.costs.result","amount":{"value":1.25,"currency":"usd"},"line_item":"gpt-5"}
              ]
            }
          ],
          "has_more": false,
          "next_page": null
        }
    """.trimIndent()

    @Test
    fun parsesUsageAndCosts() = runTest {
        val connector = OpenAiConnector(mockClient(usagePage, costPage))
        val result = connector.fetchUsage(creds, range)
        assertIs<FetchResult.Success>(result)
        assertEquals(2, result.records.size)
        val usage = result.records.first { it.inputTokens > 0 }
        assertEquals("gpt-5", usage.model)
        assertEquals(1000, usage.inputTokens)
        assertEquals(500, usage.outputTokens)
        assertEquals(200, usage.cachedInputTokens)
        // Cost report present => usage records carry tokens only, no estimation.
        assertEquals(0.0, usage.costUsd)
        val cost = result.records.first { it.costUsd != null && it.costUsd > 0 }
        assertEquals(1.25, cost.costUsd)
        // Cost-report records keep their bucket date so byDay works.
        assertEquals(usage.date, cost.date)
        assertEquals(1.25, result.records.sumOf { it.costUsd ?: 0.0 })
    }

    @Test
    fun costReportFailureKeepsEstimation() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/costs")) {
                respond("forbidden", HttpStatusCode.Forbidden, jsonHeaders)
            } else {
                respond(usagePage, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val connector = OpenAiConnector(HttpClient(engine))
        val result = connector.fetchUsage(creds, range)
        assertIs<FetchResult.Success>(result)
        assertEquals(1, result.records.size)
        assertEquals(null, result.records.first().costUsd)
    }

    @Test
    fun notConfiguredWithoutKey() = runTest {
        val connector = OpenAiConnector(mockClient())
        assertEquals(FetchResult.NotConfigured, connector.fetchUsage(ProviderCredentials(""), range))
    }

    @Test
    fun failureOnHttpError() = runTest {
        val connector = OpenAiConnector(mockClient("""{"error":"unauthorized"}""", status = HttpStatusCode.Unauthorized))
        assertIs<FetchResult.Failure>(connector.fetchUsage(creds, range))
    }
}

class AnthropicConnectorTest {
    private val usagePage = """
        {
          "data": [
            {
              "starting_at": "2026-09-02T00:00:00Z",
              "ending_at": "2026-09-03T00:00:00Z",
              "results": [
                {"model":"claude-sonnet-4-5","uncached_input_tokens":800,"output_tokens":400,
                 "cache_creation":{"ephemeral_5m_input_tokens":50,"ephemeral_1h_input_tokens":10},
                 "cache_read_input_tokens":300}
              ]
            }
          ],
          "has_more": false,
          "next_page": null
        }
    """.trimIndent()

    private val costPage = """
        {
          "data": [
            {
              "starting_at": "2026-09-02T00:00:00Z",
              "ending_at": "2026-09-03T00:00:00Z",
              "results": [
                {"amount":"2.50","currency":"USD","description":"claude-sonnet-4-5","cost_type":"tokens"}
              ]
            }
          ],
          "has_more": false,
          "next_page": null
        }
    """.trimIndent()

    @Test
    fun parsesUsageReport() = runTest {
        val connector = AnthropicConnector(mockClient(usagePage, costPage))
        val result = connector.fetchUsage(creds, range)
        assertIs<FetchResult.Success>(result)
        val usage = result.records.first { it.inputTokens > 0 }
        assertEquals(ProviderId.ANTHROPIC, usage.provider)
        assertEquals("claude-sonnet-4-5", usage.model)
        assertEquals(LocalDate(2026, 9, 2), usage.date)
        assertEquals(860, usage.inputTokens) // 800 + 50 + 10 cache write
        assertEquals(300, usage.cachedInputTokens)
        // Cost report present => usage records carry tokens only.
        assertEquals(0.0, usage.costUsd)
        val cost = result.records.first { it.costUsd != null && it.costUsd > 0 }
        assertEquals(2.50, cost.costUsd)
        assertEquals(LocalDate(2026, 9, 2), cost.date)
        assertEquals(2.50, result.records.sumOf { it.costUsd ?: 0.0 })
    }

    @Test
    fun costReportForbiddenStillSucceeds() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("cost_report")) {
                respond("forbidden", HttpStatusCode.Forbidden, jsonHeaders)
            } else {
                respond(usagePage, HttpStatusCode.OK, jsonHeaders)
            }
        }
        val connector = AnthropicConnector(HttpClient(engine))
        val result = connector.fetchUsage(creds, range)
        assertIs<FetchResult.Success>(result)
        assertEquals(1, result.records.size)
        assertEquals(null, result.records.first().costUsd)
    }
}

class OpenRouterConnectorTest {
    private val activity = """
        {
          "data": [
            {"date":"2026-09-02","model_permaslug":"deepseek/deepseek-v3","usage":0.42,"prompt_tokens":10000,"completion_tokens":2000},
            {"date":"2026-08-01","model_permaslug":"openai/gpt-5","usage":9.99}
          ]
        }
    """.trimIndent()

    @Test
    fun parsesActivityAndFiltersRange() = runTest {
        val connector = OpenRouterConnector(mockClient(activity))
        val result = connector.fetchUsage(creds, range)
        assertIs<FetchResult.Success>(result)
        assertEquals(1, result.records.size)
        assertEquals("deepseek/deepseek-v3", result.records.first().model)
        assertEquals(0.42, result.records.first().costUsd)
        assertEquals(10000, result.records.first().inputTokens)
    }

    @Test
    fun fallsBackToCredits() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/activity")) {
                respond("not found", HttpStatusCode.NotFound, jsonHeaders)
            } else {
                respond("""{"data":{"total_credits":10.0,"total_usage":7.5}}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val connector = OpenRouterConnector(HttpClient(engine))
        val result = connector.fetchUsage(creds, range)
        assertIs<FetchResult.Success>(result)
        assertEquals(1, result.records.size)
        assertEquals(7.5, result.records.first().costUsd)
    }
}

class DeepSeekConnectorTest {
    private val balance = """
        {"is_available":true,"balance_infos":[{"currency":"USD","total_balance":"25.50","granted_balance":"10.00","topped_up_balance":"40.00"}]}
    """.trimIndent()

    @Test
    fun parsesBalanceToSpendRecord() = runTest {
        val connector = DeepSeekConnector(mockClient(balance))
        val result = connector.fetchUsage(creds, range)
        assertIs<FetchResult.Success>(result)
        assertEquals(1, result.records.size)
        val r = result.records.first()
        assertEquals(ProviderId.DEEPSEEK, r.provider)
        assertEquals(24.50, r.costUsd)
        assertEquals(range.endInclusive, r.date)
    }
}
