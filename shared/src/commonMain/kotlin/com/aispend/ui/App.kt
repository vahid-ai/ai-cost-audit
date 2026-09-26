package com.aispend.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.aispend.data.ProviderStatus
import com.aispend.model.ProviderId

@Composable
fun App(state: AppState = remember { AppState() }) {
    MaterialTheme {
        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = state.screen == Screen.Dashboard,
                        onClick = { state.screen = Screen.Dashboard },
                        icon = { Text("\uD83D\uDCCA") },
                        label = { Text("Dashboard") },
                    )
                    NavigationBarItem(
                        selected = state.screen == Screen.ManualEntry,
                        onClick = { state.screen = Screen.ManualEntry },
                        icon = { Text("✏️") },
                        label = { Text("Manual") },
                    )
                    NavigationBarItem(
                        selected = state.screen == Screen.Settings,
                        onClick = { state.screen = Screen.Settings },
                        icon = { Text("⚙️") },
                        label = { Text("Settings") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (state.screen) {
                    Screen.Dashboard -> DashboardScreen(state)
                    Screen.Settings -> SettingsScreen(state)
                    Screen.ManualEntry -> ManualEntryScreen(state)
                }
            }
        }
    }
}

private fun fmtUsd(value: Double): String {
    val rounded = (value * 100).toLong() / 100.0
    val s = rounded.toString()
    val dot = s.indexOf('.')
    return if (dot < 0) "$s.00" else s.padEnd(dot + 3, '0')
}

private fun fmtTokens(v: Long): String = when {
    v >= 1_000_000 -> "${fmtUsd(v / 1_000_000.0)}M"
    v >= 1_000 -> "${fmtUsd(v / 1_000.0)}k"
    else -> v.toString()
}

@Composable
private fun DashboardScreen(state: AppState) {
    val summary = state.summary
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AI Spend", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            RangeOption.entries.forEach { opt ->
                FilterChip(
                    selected = state.rangeOption == opt,
                    onClick = { state.setRange(opt) },
                    label = { Text(opt.label) },
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            TextButton(onClick = { state.refresh() }, enabled = !state.loading) {
                Text("Refresh")
            }
        }

        if (state.loading && summary == null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }

        if (summary != null) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total spend", style = MaterialTheme.typography.labelLarge)
                    Text("$${fmtUsd(summary.totalCostUsd)}", style = MaterialTheme.typography.displaySmall)
                }
            }

            DailyChart(summary)

            Text("Providers", style = MaterialTheme.typography.titleMedium)
            state.connectors.forEach { connector ->
                ProviderCard(connector.id, connector.displayName, summary)
            }

            if (summary.byModel.isNotEmpty()) {
                Text("By model", style = MaterialTheme.typography.titleMedium)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row {
                            Text("Model", Modifier.weight(2f), style = MaterialTheme.typography.labelLarge)
                            Text("Tokens", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text("Cost", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                        }
                        HorizontalDivider()
                        summary.byModel.forEach { m ->
                            Row {
                                Text(m.model, Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                                Text(fmtTokens(m.tokens), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text("$${fmtUsd(m.costUsd)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(id: ProviderId, name: String, summary: com.aispend.data.SpendSummary) {
    val spend = summary.byProvider[id]
    val status = when {
        summary.errors.containsKey(id) -> ProviderStatus.ERROR
        summary.unsupported.contains(id) -> ProviderStatus.UNSUPPORTED
        summary.notConfigured.contains(id) -> ProviderStatus.NOT_CONFIGURED
        else -> ProviderStatus.OK
    }
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall)
                Text(
                    when (status) {
                        ProviderStatus.OK -> "ok"
                        ProviderStatus.ERROR -> "error: ${summary.errors[id]}"
                        ProviderStatus.NOT_CONFIGURED -> "not configured"
                        ProviderStatus.UNSUPPORTED -> "unsupported (manual entry)"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when (status) {
                        ProviderStatus.OK -> MaterialTheme.colorScheme.primary
                        ProviderStatus.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$${fmtUsd(spend?.costUsd ?: 0.0)}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "in ${fmtTokens(spend?.inputTokens ?: 0)} / out ${fmtTokens(spend?.outputTokens ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun DailyChart(summary: com.aispend.data.SpendSummary) {
    if (summary.byDay.isEmpty()) return
    val barColor = MaterialTheme.colorScheme.primary
    Text("Daily spend", style = MaterialTheme.typography.titleMedium)
    Card(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(120.dp).padding(16.dp)) {
            val days = summary.byDay
            val max = days.maxOf { it.costUsd }.coerceAtLeast(0.01)
            val barWidth = size.width / days.size * 0.7f
            val gap = size.width / days.size * 0.3f
            days.forEachIndexed { i, day ->
                val h = (day.costUsd / max * size.height).toFloat()
                drawRect(
                    color = barColor,
                    topLeft = Offset(i * (barWidth + gap) + gap / 2, size.height - h),
                    size = Size(barWidth, h),
                )
            }
        }
    }
}
