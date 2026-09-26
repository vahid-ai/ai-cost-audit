package com.aispend.providers

import com.aispend.model.DateRange
import com.aispend.model.FetchResult
import com.aispend.model.ProviderConnector
import com.aispend.model.ProviderCredentials
import com.aispend.model.ProviderId

/**
 * Providers that expose no usage/billing endpoint for ordinary API keys;
 * the UI offers manual entry for these.
 */
class UnsupportedConnector(
    override val id: ProviderId,
    override val displayName: String,
    override val credentialHint: String,
) : ProviderConnector {
    override val supportsUsageApi = false

    override suspend fun fetchUsage(credentials: ProviderCredentials, range: DateRange): FetchResult =
        FetchResult.Unsupported
}
