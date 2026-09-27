package com.aispend.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.aispend.data.CredentialsStore
import com.aispend.data.DemoData
import com.aispend.data.ManualUsageStore
import com.aispend.data.SpendRepository
import com.aispend.data.SpendSummary
import com.aispend.model.DateRange
import com.aispend.model.ProviderConnector
import com.aispend.model.UsageRecord
import com.aispend.net.createHttpClient
import com.aispend.platform.platformSettings
import com.aispend.providers.defaultConnectors
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

enum class RangeOption(val label: String) {
    DAYS_7("7d"),
    DAYS_30("30d"),
    DAYS_90("90d"),
    MTD("MTD"),
}

sealed interface Screen {
    data object Dashboard : Screen
    data object Settings : Screen
    data object ManualEntry : Screen
}

class AppState(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
    httpClient: HttpClient = createHttpClient(),
) {
    val settings = platformSettings()
    val credentialsStore = CredentialsStore(settings)
    val manualUsageStore = ManualUsageStore(settings)
    val connectors: List<ProviderConnector> = defaultConnectors(httpClient)

    private val demoRecords = mutableListOf<UsageRecord>()

    val repository = SpendRepository(
        connectors = connectors,
        credentialsStore = credentialsStore,
        manualUsageStore = manualUsageStore,
        extraRecords = { range ->
            demoRecords.filter { it.date >= range.start && it.date <= range.endInclusive }
        },
    )

    var screen by mutableStateOf<Screen>(Screen.Dashboard)
    var rangeOption by mutableStateOf(RangeOption.DAYS_30)
    var summary by mutableStateOf<SpendSummary?>(null)
    var loading by mutableStateOf(false)
    var demoMode by mutableStateOf(credentialsStore.demoMode)

    fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    fun currentRange(): DateRange {
        val today = today()
        return when (rangeOption) {
            RangeOption.DAYS_7 -> DateRange(today.minus(DatePeriod(days = 6)), today)
            RangeOption.DAYS_30 -> DateRange(today.minus(DatePeriod(days = 29)), today)
            RangeOption.DAYS_90 -> DateRange(today.minus(DatePeriod(days = 89)), today)
            RangeOption.MTD -> DateRange(LocalDate(today.year, today.month, 1), today)
        }
    }

    init {
        if (demoMode) loadDemo()
        refresh()
    }

    fun refresh(force: Boolean = true) {
        scope.launch {
            loading = true
            try {
                summary = repository.summary(currentRange(), forceRefresh = force)
            } finally {
                loading = false
            }
        }
    }

    fun setRange(option: RangeOption) {
        rangeOption = option
        refresh(force = false)
    }

    fun enableDemoMode(enabled: Boolean) {
        demoMode = enabled
        credentialsStore.demoMode = enabled
        if (enabled) loadDemo() else demoRecords.clear()
        repository.invalidate()
        refresh()
    }

    fun saveCredentials(provider: com.aispend.model.ProviderId, credentials: com.aispend.model.ProviderCredentials) {
        credentialsStore.save(provider, credentials)
        repository.invalidate()
        refresh()
    }

    fun clearCredentials(provider: com.aispend.model.ProviderId) {
        credentialsStore.clear(provider)
        repository.invalidate()
        refresh()
    }

    fun addManualRecord(record: UsageRecord) {
        manualUsageStore.add(record)
        repository.invalidate()
        refresh()
    }

    private fun loadDemo() {
        demoRecords.clear()
        demoRecords += DemoData.records(today(), 90)
    }
}
