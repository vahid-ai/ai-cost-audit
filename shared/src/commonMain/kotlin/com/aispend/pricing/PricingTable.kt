package com.aispend.pricing

import com.aispend.model.UsageRecord

/** USD per 1M tokens. */
data class ModelPrice(
    val inputPer1M: Double,
    val outputPer1M: Double,
    val cachedInputPer1M: Double? = null,
)

object PricingTable {
    // Prices are the provider-published list prices per 1M tokens (USD).
    private val prices: Map<String, ModelPrice> = mapOf(
        // OpenAI
        "gpt-5" to ModelPrice(1.25, 10.0, 0.125),
        "gpt-5-mini" to ModelPrice(0.25, 2.0, 0.025),
        "gpt-5-nano" to ModelPrice(0.05, 0.4, 0.005),
        "gpt-4.1" to ModelPrice(2.0, 8.0, 0.5),
        "gpt-4.1-mini" to ModelPrice(0.4, 1.6, 0.1),
        "gpt-4.1-nano" to ModelPrice(0.1, 0.4, 0.025),
        "gpt-4o" to ModelPrice(2.5, 10.0, 1.25),
        "gpt-4o-mini" to ModelPrice(0.15, 0.6, 0.075),
        "o3" to ModelPrice(2.0, 8.0, 0.5),
        "o4-mini" to ModelPrice(1.1, 4.4, 0.275),
        // Anthropic
        "claude-opus-4" to ModelPrice(15.0, 75.0, 1.5),
        "claude-sonnet-4" to ModelPrice(3.0, 15.0, 0.3),
        "claude-haiku-3.5" to ModelPrice(0.8, 4.0, 0.08),
        "claude-3-haiku" to ModelPrice(0.25, 1.25, 0.03),
        // Google Gemini
        "gemini-2.5-pro" to ModelPrice(1.25, 10.0, 0.31),
        "gemini-2.5-flash" to ModelPrice(0.3, 2.5, 0.075),
        "gemini-2.0-flash" to ModelPrice(0.1, 0.4, 0.025),
        // Mistral
        "mistral-large" to ModelPrice(2.0, 6.0),
        "mistral-medium" to ModelPrice(0.4, 2.0),
        "mistral-small" to ModelPrice(0.1, 0.3),
        "codestral" to ModelPrice(0.3, 0.9),
        // DeepSeek
        "deepseek-chat" to ModelPrice(0.27, 1.1, 0.07),
        "deepseek-reasoner" to ModelPrice(0.55, 2.19, 0.14),
        "deepseek-v3" to ModelPrice(0.27, 1.1, 0.07),
        "deepseek-r1" to ModelPrice(0.55, 2.19, 0.14),
        // Groq (hosted open models)
        "llama-3.3-70b" to ModelPrice(0.59, 0.79),
        "llama-3.1-8b" to ModelPrice(0.05, 0.08),
        "llama-3.1-70b" to ModelPrice(0.59, 0.79),
        "mixtral-8x7b" to ModelPrice(0.24, 0.24),
        // xAI
        "grok-4" to ModelPrice(3.0, 15.0, 0.75),
        "grok-3" to ModelPrice(3.0, 15.0),
        "grok-3-mini" to ModelPrice(0.3, 0.5),
        "grok-2" to ModelPrice(2.0, 10.0),
        // Together (hosted open models)
        "meta-llama/llama-4" to ModelPrice(0.18, 0.59),
        "meta-llama/llama-3.3-70b" to ModelPrice(0.88, 0.88),
        "meta-llama/llama-3.1-405b" to ModelPrice(3.5, 3.5),
        "deepseek-ai/deepseek-v3" to ModelPrice(1.25, 1.25),
        "deepseek-ai/deepseek-r1" to ModelPrice(3.0, 7.0),
        // OpenRouter (aggregator; same underlying list prices apply)
        "openai/gpt-5-mini" to ModelPrice(0.25, 2.0, 0.025),
        "openai/gpt-5" to ModelPrice(1.25, 10.0, 0.125),
        "openai/gpt-4.1" to ModelPrice(2.0, 8.0, 0.5),
        "openai/gpt-4o-mini" to ModelPrice(0.15, 0.6, 0.075),
        "openai/gpt-4o" to ModelPrice(2.5, 10.0, 1.25),
        "anthropic/claude-sonnet" to ModelPrice(3.0, 15.0, 0.3),
        "anthropic/claude-opus" to ModelPrice(15.0, 75.0, 1.5),
        "anthropic/claude-haiku" to ModelPrice(0.8, 4.0, 0.08),
        "google/gemini-2.5" to ModelPrice(1.25, 10.0, 0.31),
        "deepseek/" to ModelPrice(0.27, 1.1, 0.07),
    )

    // Longest keys first so prefix matching prefers the most specific entry.
    private val sortedKeys: List<String> = prices.keys.sortedByDescending { it.length }

    /** Exact match, then longest-prefix match (e.g. "gpt-4.1-2025-04-14" -> "gpt-4.1"). */
    fun priceFor(model: String): ModelPrice? {
        prices[model]?.let { return it }
        val lower = model.lowercase()
        for (key in sortedKeys) {
            if (lower.startsWith(key.lowercase())) return prices[key]
        }
        return null
    }
}

object CostEstimator {
    /** Estimated cost in USD, or null when the model is unpriced. */
    fun estimate(record: UsageRecord): Double? {
        record.costUsd?.let { return it }
        val price = PricingTable.priceFor(record.model) ?: return null
        val cachedPrice = price.cachedInputPer1M ?: price.inputPer1M
        return (record.inputTokens * price.inputPer1M +
            record.cachedInputTokens * cachedPrice +
            record.outputTokens * price.outputPer1M) / 1_000_000.0
    }
}
