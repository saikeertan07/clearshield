package com.clearshield.adblocker.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.clearshield.adblocker.service.ClearShieldVpnService
import com.clearshield.adblocker.service.VpnStateHolder
import com.clearshield.adblocker.ui.navigation.AppNavHost
import com.clearshield.adblocker.ui.theme.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            ClearShieldTheme {
                var startupError by remember { mutableStateOf<String?>(null) }

                if (startupError != null) {
                    StartupFallbackScreen(
                        errorMessage = startupError ?: "Unknown startup error",
                        onRetry = { startupError = null }
                    )
                } else {
                    MainScreenContent(
                        onError = { err -> startupError = err }
                    )
                }
            }
        }
    }

    @Composable
    private fun MainScreenContent(onError: (String) -> Unit) {
        var showVpnDisclosure by remember { mutableStateOf(false) }
        var pendingVpnIntent by remember { mutableStateOf<Intent?>(null) }

        // VPN Permission launcher
        val vpnPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                startVpnService()
            } else {
                Toast.makeText(this, "VPN permission is required to filter ads", Toast.LENGTH_LONG).show()
            }
        }

        // Notification permission launcher (Android 13+)
        val notificationPermissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        val toggleVpn = {
            try {
                if (VpnStateHolder.isVpnRunning.value) {
                    stopVpnService()
                } else {
                    // Request notification permission if needed (Android 13+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }

                    // Check VPN preparation
                    val intent = VpnService.prepare(this)
                    if (intent != null) {
                        // Google Play VpnService Requirement: Prominent Disclosure first!
                        pendingVpnIntent = intent
                        showVpnDisclosure = true
                    } else {
                        startVpnService()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Failed to toggle VPN", e)
                Toast.makeText(this, "Could not start VPN: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        AppNavHost(onToggleVpn = toggleVpn)

        if (showVpnDisclosure) {
            AlertDialog(
                onDismissRequest = {
                    showVpnDisclosure = false
                    pendingVpnIntent = null
                },
                icon = {
                    Icon(Icons.Default.Security, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(32.dp))
                },
                title = {
                    Text(
                        text = "VpnService & Privacy Disclosure",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "ClearShield uses Android's VpnService to create a local virtual DNS loopback directly on your phone.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary, lineHeight = 20.sp)
                        )
                        Text(
                            text = "• DNS Filtering Only: Only DNS queries (UDP port 53) are routed through ClearShield's on-device filter engine to block ad networks, tracking beacons, and telemetry.\n• Zero Interception of Web Content: ClearShield does NOT inspect or touch your normal HTTPS web browsing, app traffic, or personal data.\n• No Remote Data Collection: Your browsing history and DNS records are never sold, monetized, or sent to any remote server or proxy.\n• Direct Upstream: Allowed benign DNS queries are forwarded directly to your configured upstream DNS provider (Cloudflare, Google, Quad9, etc.).",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showVpnDisclosure = false
                            val intent = pendingVpnIntent
                            pendingVpnIntent = null
                            if (intent != null) {
                                vpnPermissionLauncher.launch(intent)
                            } else {
                                startVpnService()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = CyberNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Agree & Continue", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showVpnDisclosure = false
                            pendingVpnIntent = null
                        }
                    ) {
                        Text("Cancel", color = TextSecondary)
                    }
                },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }

    @Composable
    private fun StartupFallbackScreen(
        errorMessage: String,
        onRetry: () -> Unit
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(CyberNavy)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateNavy),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "Warning",
                        tint = AmberWarning,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "ClearShield Recovery Mode",
                        style = MaterialTheme.typography.titleLarge.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "A non-critical startup error was encountered. You can retry initialization or proceed into the dashboard.",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Surface(
                        color = DarkSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall.copy(color = CrimsonRed),
                            modifier = Modifier.padding(12.dp),
                            maxLines = 4
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = CyberNavy),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Retry", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onRetry,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Dashboard")
                        }
                    }
                }
            }
        }
    }

    private fun startVpnService() {
        try {
            val intent = Intent(this, ClearShieldVpnService::class.java).apply {
                action = ClearShieldVpnService.ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Failed to start VPN service", t)
            Toast.makeText(this, "Could not start VPN service: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopVpnService() {
        try {
            val intent = Intent(this, ClearShieldVpnService::class.java).apply {
                action = ClearShieldVpnService.ACTION_STOP
            }
            startService(intent)
        } catch (t: Throwable) {
            android.util.Log.e("MainActivity", "Failed to stop VPN service", t)
        }
    }
}
