package com.aispend.data

import com.aispend.model.DateRange
import com.aispend.model.ProviderId
import com.aispend.model.UsageRecord
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

object DemoData {
    fun records(today: LocalDate, days: Int = 30): List<UsageRecord> {
        val models = listOf(
            Triple(ProviderId.OPENAI, "gpt-5", 1.25),
            Triple(ProviderId.OPENAI, "gpt-4.1-mini", 0.4),
            Triple(ProviderId.ANTHROPIC, "claude-sonnet-4-5", 3.0),
            Triple(ProviderId.ANTHROPIC, "claude-haiku-3.5", 0.8),
            Triple(ProviderId.OPENROUTER, "deepseek/deepseek-v3", 0.27),
            Triple(ProviderId.DEEPSEEK, "deepseek-chat", 0.27),
            Triple(ProviderId.GOOGLE_GEMINI, "gemini-2.5-pro", 1.25),
            Triple(ProviderId.XAI, "grok-4", 3.0),
        )
        val out = mutableListOf<UsageRecord>()
        var seed = 42L
        fun rnd(): Long {
            seed = (seed * 1103515245 + 12345) % 2147483648
            return seed
        }
        for (d in 0 until days) {
            val date = today.minus(DatePeriod(days = d))
            for ((provider, model, _) in models) {
                if (rnd() % 4 == 0L) continue
                val input = 5_000 + rnd() % 400_000
                val output = 1_000 + rnd() % 80_000
                out += UsageRecord(
                    provider = provider,
                    model = model,
                    date = date,
                    inputTokens = input,
                    outputTokens = output,
                    cachedInputTokens = input / 4,
                    costUsd = null,
                )
            }
        }
        return out
    }
}
