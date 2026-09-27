package com.aispend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.aispend.model.ProviderCredentials

@Composable
fun SettingsScreen(state: AppState) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Demo data", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Load sample records to preview the dashboard without API keys.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = state.demoMode, onCheckedChange = { state.enableDemoMode(it) })
            }
        }

        state.connectors.forEach { connector ->
            ProviderCredentialRow(state, connector)
        }
    }
}

@Composable
private fun ProviderCredentialRow(state: AppState, connector: com.aispend.model.ProviderConnector) {
    var apiKey by remember(connector.id) {
        mutableStateOf(state.credentialsStore.get(connector.id)?.apiKey ?: "")
    }
    var orgId by remember(connector.id) {
        mutableStateOf(state.credentialsStore.get(connector.id)?.organizationId ?: "")
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(connector.displayName, style = MaterialTheme.typography.titleSmall)
            Text(connector.credentialHint, style = MaterialTheme.typography.bodySmall)
            if (connector.supportsUsageApi) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (connector.id == com.aispend.model.ProviderId.OPENAI) {
                    OutlinedTextField(
                        value = orgId,
                        onValueChange = { orgId = it },
                        label = { Text("Organization ID (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        state.saveCredentials(
                            connector.id,
                            ProviderCredentials(apiKey.trim(), orgId.trim().ifBlank { null }),
                        )
                    }) { Text("Save") }
                    TextButton(onClick = {
                        apiKey = ""
                        orgId = ""
                        state.clearCredentials(connector.id)
                    }) { Text("Clear") }
                }
            } else {
                Text(
                    "No usage API available; add usage on the Manual tab.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        HorizontalDivider()
    }
}
