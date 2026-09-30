package com.clearshield.adblocker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.data.repository.SettingsRepository
import com.clearshield.adblocker.service.VpnStateHolder
import com.clearshield.adblocker.ui.theme.*

@Composable
fun DnsSettingsScreen() {
    val app = ClearShieldApp.instance
    val context = LocalContext.current

    val currentUpstream by app.settingsRepository.upstreamDns.collectAsState()
    val useDoh by app.settingsRepository.useDoh.collectAsState()
    val allowInsecureFallback by app.settingsRepository.allowInsecureFallback.collectAsState()
    val blockingMode by app.settingsRepository.blockingMode.collectAsState()
    val customDnsIp by app.settingsRepository.customDnsIp.collectAsState()

    var customInput by remember { mutableStateOf(customDnsIp) }
    var showCustomDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavy)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "Upstream DNS Configuration",
            style = MaterialTheme.typography.headlineMedium.copy(color = Color.White)
        )
        Text(
            text = "Choose which DNS resolver handles allowed queries",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Encrypted Upstream DNS (DoH) Toggle Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, if (useDoh) CyanAccent else SlateNavy.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "DNS-over-HTTPS (DoH)",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (useDoh) MintGreen.copy(alpha = 0.2f) else SlateNavy.copy(alpha = 0.4f)
                        ) {
                            Text(
                                text = if (useDoh) "Encrypted" else "Plain UDP",
                                color = if (useDoh) MintGreen else TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (useDoh)
                            "All upstream DNS queries are encrypted with HTTPS (port 443) preventing ISP surveillance."
                        else
                            "Queries are sent unencrypted over standard UDP port 53.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = useDoh,
                    onCheckedChange = {
                        app.settingsRepository.setUseDoh(it)
                        Toast.makeText(
                            context,
                            if (it) "DNS-over-HTTPS (DoH) Enabled" else "Using Plain UDP Fallback",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyanAccent,
                        checkedTrackColor = DeepNavy,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = DarkSurface
                    )
                )
            }

            if (useDoh) {
                HorizontalDivider(color = SlateNavy.copy(alpha = 0.4f), modifier = Modifier.padding(horizontal = 16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Allow Plaintext Fallback",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (allowInsecureFallback) CrimsonRed else Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (allowInsecureFallback) CrimsonRed.copy(alpha = 0.2f) else MintGreen.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = if (allowInsecureFallback) "INSECURE" else "STRICT SECURE",
                                    color = if (allowInsecureFallback) CrimsonRed else MintGreen,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (allowInsecureFallback)
                                "Warning: If encrypted DoH fails, queries will silently downgrade to plain UDP port 53, exposing requests to your ISP."
                            else
                                "Strict Privacy: Queries will never silently downgrade to plaintext. If DoH fails, requests are safely dropped.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = allowInsecureFallback,
                        onCheckedChange = {
                            app.settingsRepository.setAllowInsecureFallback(it)
                            Toast.makeText(
                                context,
                                if (it) "Warning: Insecure plaintext fallback permitted" else "Strict privacy: Plaintext fallback disabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CrimsonRed,
                            checkedTrackColor = DeepNavy,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurface
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Upstream DNS Providers",
            style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent)
        )
        Spacer(modifier = Modifier.height(8.dp))

        SettingsRepository.DNS_PROVIDERS.forEach { provider ->
            val isSelected = (provider.name != "Custom" && currentUpstream == provider.primaryIp) ||
                    (provider.name == "Custom" && currentUpstream == customDnsIp && !SettingsRepository.DNS_PROVIDERS.dropLast(1).any { it.primaryIp == currentUpstream })

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        if (provider.name == "Custom") {
                            showCustomDialog = true
                        } else {
                            app.settingsRepository.setUpstreamDns(provider.primaryIp)
                            VpnStateHolder.setActiveUpstream(provider.primaryIp)
                            Toast.makeText(context, "Upstream set to ${provider.name} (${provider.primaryIp})", Toast.LENGTH_SHORT).show()
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, if (isSelected) CyanAccent else SlateNavy.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = provider.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            if (provider.primaryIp.isNotEmpty() && provider.name != "Custom") {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = provider.primaryIp,
                                    style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent)
                                )
                            }
                        }
                        Text(
                            text = if (provider.name == "Custom") "Custom IP: $customDnsIp" else provider.description,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }

                    RadioButton(
                        selected = isSelected,
                        onClick = {
                            if (provider.name == "Custom") {
                                showCustomDialog = true
                            } else {
                                app.settingsRepository.setUpstreamDns(provider.primaryIp)
                                VpnStateHolder.setActiveUpstream(provider.primaryIp)
                            }
                        },
                        colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "DNS Blocking Synthesis Mode",
            style = MaterialTheme.typography.titleMedium.copy(color = CyanAccent)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { app.settingsRepository.setBlockingMode("0.0.0.0") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "0.0.0.0 / :: (Recommended)",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Immediate resolution to dummy address. Fastest response time and lowest battery drain.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                    RadioButton(
                        selected = blockingMode == "0.0.0.0",
                        onClick = { app.settingsRepository.setBlockingMode("0.0.0.0") },
                        colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                    )
                }

                HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { app.settingsRepository.setBlockingMode("NXDOMAIN") },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NXDOMAIN (Non-Existent Domain)",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Returns non-existent domain code. Some apps may retry several times.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                    RadioButton(
                        selected = blockingMode == "NXDOMAIN",
                        onClick = { app.settingsRepository.setBlockingMode("NXDOMAIN") },
                        colors = RadioButtonDefaults.colors(selectedColor = CyanAccent)
                    )
                }
            }
        }
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Configure Custom DNS", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter custom upstream IPv4 address:", style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                    OutlinedTextField(
                        value = customInput,
                        onValueChange = { customInput = it },
                        singleLine = true,
                        placeholder = { Text("9.9.9.9") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = customInput.trim()
                        if (clean.matches(Regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}$"))) {
                            app.settingsRepository.setCustomDnsIp(clean)
                            app.settingsRepository.setUpstreamDns(clean)
                            VpnStateHolder.setActiveUpstream(clean)
                            showCustomDialog = false
                            Toast.makeText(context, "Upstream set to $clean", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Invalid IPv4 address", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = CyberNavy)
                ) {
                    Text("Save & Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}
