package com.clearshield.adblocker.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.service.VpnStateHolder
import com.clearshield.adblocker.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val app = ClearShieldApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val autoStart by app.settingsRepository.autoStartBoot.collectAsState()
    val blockingMode by app.settingsRepository.blockingMode.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavy)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "App Settings",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = "System behavior & persistence preferences",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
        }

        // Blocking Response Mode Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Blocked DNS Response Behavior",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Controls the exact DNS packet synthesized for blocked domains",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Option 1: 0.0.0.0 (Fastest / RFC Standard)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (blockingMode != "NXDOMAIN"),
                            onClick = { app.settingsRepository.setBlockingMode("0.0.0.0") }
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (blockingMode != "NXDOMAIN"),
                        onClick = { app.settingsRepository.setBlockingMode("0.0.0.0") },
                        colors = RadioButtonDefaults.colors(selectedColor = CyanAccent, unselectedColor = TextSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "0.0.0.0 / :: (Standard Fast Response)",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Returns 0.0.0.0 (IPv4) or :: (IPv6) with NOERROR. Immediate connection close without DNS timeout.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }

                HorizontalDivider(color = SlateNavy.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))

                // Option 2: NXDOMAIN (Non-Existent Domain)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = (blockingMode == "NXDOMAIN"),
                            onClick = { app.settingsRepository.setBlockingMode("NXDOMAIN") }
                        )
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (blockingMode == "NXDOMAIN"),
                        onClick = { app.settingsRepository.setBlockingMode("NXDOMAIN") },
                        colors = RadioButtonDefaults.colors(selectedColor = CyanAccent, unselectedColor = TextSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "NXDOMAIN (Non-Existent Domain)",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Returns standard DNS error code RCODE=3 informing client that the domain does not exist.",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }
                }
            }
        }

        // System & Lifecycle Behavior Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Start on Boot",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Automatically start ClearShield protection when device boots up",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                    Switch(
                        checked = autoStart,
                        onCheckedChange = { app.settingsRepository.setAutoStartBoot(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = CyberNavy,
                            checkedTrackColor = CyanAccent
                        )
                    )
                }

                HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Recompile Filter Rules",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                        Text(
                            text = "Re-build the internal reverse Trie matcher from all active subscriptions and database tables",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                app.filterRepository.reloadAndCompileAllRules()
                                Toast.makeText(context, "Filter rules recompiled successfully", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent)
                    ) {
                        Text("Recompile")
                    }
                }

                HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reset All Statistics",
                            style = MaterialTheme.typography.titleMedium.copy(color = CrimsonRed)
                        )
                        Text(
                            text = "Clears session metrics and stored Room daily history records",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                    Button(
                        onClick = { showResetDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = Color.White)
                    ) {
                        Text("Reset")
                    }
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Statistics?", color = Color.White) },
            text = { Text("This will clear all daily history records and live query counters. This action cannot be undone.", color = TextSecondary) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            app.statsRepository.clearAllStats()
                            VpnStateHolder.resetSessionStats()
                            showResetDialog = false
                            Toast.makeText(context, "Statistics reset", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = Color.White)
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = DarkSurface
        )
    }
}
