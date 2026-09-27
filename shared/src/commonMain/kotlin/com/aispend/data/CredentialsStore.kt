package com.aispend.data

import com.aispend.model.ProviderCredentials
import com.aispend.model.ProviderId
import com.russhwolf.settings.Settings

/** Stores provider credentials in platform settings (plain prefs on desktop). */
class CredentialsStore(private val settings: Settings) {

    fun get(provider: ProviderId): ProviderCredentials? {
        val key = settings.getStringOrNull("${provider.name}.apiKey") ?: return null
        if (key.isBlank()) return null
        val org = settings.getStringOrNull("${provider.name}.orgId")?.takeIf { it.isNotBlank() }
        return ProviderCredentials(apiKey = key, organizationId = org)
    }

    fun save(provider: ProviderId, credentials: ProviderCredentials) {
        settings.putString("${provider.name}.apiKey", credentials.apiKey)
        credentials.organizationId?.let { settings.putString("${provider.name}.orgId", it) }
            ?: settings.remove("${provider.name}.orgId")
    }

    fun clear(provider: ProviderId) {
        settings.remove("${provider.name}.apiKey")
        settings.remove("${provider.name}.orgId")
    }

    var demoMode: Boolean
        get() = settings.getBoolean("demoMode", false)
        set(value) = settings.putBoolean("demoMode", value)
}
