package com.aispend.providers

import com.aispend.model.ProviderConnector
import com.aispend.model.ProviderId
import io.ktor.client.HttpClient

fun defaultConnectors(client: HttpClient): List<ProviderConnector> = listOf(
    OpenAiConnector(client),
    AnthropicConnector(client),
    OpenRouterConnector(client),
    DeepSeekConnector(client),
    UnsupportedConnector(
        ProviderId.GOOGLE_GEMINI,
        "Google Gemini",
        "No usage/billing API for AI Studio keys; use manual entry",
    ),
    UnsupportedConnector(
        ProviderId.MISTRAL,
        "Mistral",
        "No usage API for plain API keys; use manual entry",
    ),
    UnsupportedConnector(
        ProviderId.GROQ,
        "Groq",
        "No billing/usage API for API keys; use manual entry",
    ),
    UnsupportedConnector(
        ProviderId.XAI,
        "xAI",
        "No usage API for API keys; use manual entry",
    ),
    UnsupportedConnector(
        ProviderId.TOGETHER,
        "Together",
        "No usage/billing API for API keys; use manual entry",
    ),
)
