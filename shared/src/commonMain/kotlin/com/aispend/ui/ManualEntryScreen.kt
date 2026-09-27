package com.aispend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aispend.model.ProviderId
import com.aispend.model.UsageRecord
import kotlinx.datetime.LocalDate

@Composable
fun ManualEntryScreen(state: AppState) {
    val unsupported = state.connectors.filter { !it.supportsUsageApi }
    var provider by remember { mutableStateOf(unsupported.firstOrNull()?.id ?: ProviderId.OPENAI) }
    var model by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(state.today().toString()) }
    var inputTokens by remember { mutableStateOf("") }
    var outputTokens by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Manual entry", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Record usage for providers that do not expose a usage API. " +
                "Cost is estimated from the built-in pricing table.",
            style = MaterialTheme.typography.bodySmall,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var expanded by remember { mutableStateOf(false) }
                Row {
                    Text("Provider: ${provider.name}", modifier = Modifier.weight(1f))
                    TextButton(onClick = { expanded = true }) { Text("Change") }
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    unsupported.forEach { c ->
                        DropdownMenuItem(
                            text = { Text(c.displayName) },
                            onClick = { provider = c.id; expanded = false },
                        )
                    }
                }
                OutlinedTextField(model, { model = it }, label = { Text("Model (e.g. gemini-2.5-pro)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(inputTokens, { inputTokens = it.filter(Char::isDigit) }, label = { Text("Input tokens") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(outputTokens, { outputTokens = it.filter(Char::isDigit) }, label = { Text("Output tokens") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                    when {
                        model.isBlank() -> message = "Model is required"
                        parsedDate == null -> message = "Invalid date"
                        else -> {
                            state.addManualRecord(
                                UsageRecord(
                                    provider = provider,
                                    model = model.trim(),
                                    date = parsedDate,
                                    inputTokens = inputTokens.toLongOrNull() ?: 0,
                                    outputTokens = outputTokens.toLongOrNull() ?: 0,
                                    costUsd = null,
                                )
                            )
                            message = "Saved"
                            model = ""
                            inputTokens = ""
                            outputTokens = ""
                        }
                    }
                }) { Text("Add record") }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
