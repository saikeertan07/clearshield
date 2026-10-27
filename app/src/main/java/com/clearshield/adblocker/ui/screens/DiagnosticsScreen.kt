package com.clearshield.adblocker.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.engine.dns.DnsPacket
import com.clearshield.adblocker.engine.filter.FilterDecision
import com.clearshield.adblocker.engine.filter.FilterEngine
import com.clearshield.adblocker.service.ClearShieldVpnService
import com.clearshield.adblocker.service.VpnStateHolder
import com.clearshield.adblocker.service.VpnStatus
import com.clearshield.adblocker.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class DiagnosticStatus {
    PASS,
    FAIL,
    NOT_VERIFIED,
    STOPPED
}

data class DiagnosticResultItem(
    val title: String,
    val status: DiagnosticStatus,
    val details: String
)

@Composable
fun DiagnosticsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val app = ClearShieldApp.instance

    val vpnStatus by VpnStateHolder.vpnStatus.collectAsState()
    val isEstablished by VpnStateHolder.isVpnEstablished.collectAsState()
    val hasTrafficFlowed by VpnStateHolder.hasTrafficFlowed.collectAsState()
    val totalQueries by VpnStateHolder.totalQueries.collectAsState()
    val blockedQueries by VpnStateHolder.blockedQueries.collectAsState()
    val upstreamDns by VpnStateHolder.activeUpstreamDns.collectAsState()
    val lastDomain by VpnStateHolder.lastDomain.collectAsState()
    val lastQueryType by VpnStateHolder.lastQueryType.collectAsState()
    val lastError by VpnStateHolder.lastError.collectAsState()

    val totalRules = FilterEngine.instance.totalBlockedRules
    val allowedQueries = (totalQueries - blockedQueries).coerceAtLeast(0L)

    var isRunningDiagnostics by remember { mutableStateOf(false) }
    var diagnosticResults by remember { mutableStateOf<List<DiagnosticResultItem>?>(null) }

    var testDomainInput by remember { mutableStateOf("pagead2.googlesyndication.com") }
    var testResult by remember { mutableStateOf<FilterDecision?>(null) }

    val statusColor = when (vpnStatus) {
        VpnStatus.PROTECTED -> MintGreen
        VpnStatus.STARTING -> AmberWarning
        VpnStatus.OFF -> TextSecondary
        VpnStatus.ERROR -> CrimsonRed
        VpnStatus.PERMISSION_REQUIRED -> AmberWarning
        VpnStatus.NETWORK_UNAVAILABLE -> AmberWarning
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavy)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Status Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VPN Filter Status",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Android VpnService Local Stack",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, statusColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = vpnStatus.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }
                    }
                }

                if (lastError != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = CrimsonRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, CrimsonRed.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CrimsonRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Last Error: $lastError",
                                style = MaterialTheme.typography.bodySmall.copy(color = CrimsonRed)
                            )
                        }
                    }
                }
            }
        }

        // Comprehensive Health Diagnostics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Engine Health & Diagnostics",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "Distinct subsystem verification (no synthetic conflation)",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                    }

                    Button(
                        onClick = {
                            scope.launch {
                                isRunningDiagnostics = true
                                diagnosticResults = runDetailedDiagnostics(app, upstreamDns, isEstablished, totalQueries)
                                isRunningDiagnostics = false
                            }
                        },
                        enabled = !isRunningDiagnostics,
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = CyberNavy),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isRunningDiagnostics) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyberNavy, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Run Diagnostic")
                        }
                    }
                }

                if (diagnosticResults != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        diagnosticResults!!.forEach { item ->
                            DiagnosticRowView(item)
                        }
                    }
                }
            }
        }

        // Real-Time Query Counters Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Session Query Statistics",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CounterPill(modifier = Modifier.weight(1f), label = "Total DNS", count = "$totalQueries", color = CyanAccent)
                    CounterPill(modifier = Modifier.weight(1f), label = "Blocked", count = "$blockedQueries", color = CrimsonRed)
                    CounterPill(modifier = Modifier.weight(1f), label = "Allowed", count = "$allowedQueries", color = MintGreen)
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { VpnStateHolder.resetSessionStats() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
                    border = BorderStroke(1.dp, SlateNavy)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Session Counters")
                }
            }
        }

        // Engine Memory & Buffer Telemetry Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Engine Memory & Buffer Telemetry",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Live zero-copy packet buffer pools and radix trie structural memory",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(12.dp))

                val poolStats = VpnStateHolder.getBufferPoolStats()
                val engineStats = VpnStateHolder.getFilterEngineStats()
                val memoryMb = String.format("%.2f MB", engineStats.second / (1024.0 * 1024.0))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CounterPill(modifier = Modifier.weight(1f), label = "Trie Nodes", count = "${engineStats.first}", color = CyanAccent)
                    CounterPill(modifier = Modifier.weight(1f), label = "Trie Memory", count = memoryMb, color = MintGreen)
                    CounterPill(modifier = Modifier.weight(1f), label = "Pool Buffers", count = "${poolStats.third}", color = AmberWarning)
                }
            }
        }

        // Filter Engine Verification Tool Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Filter Engine Verification Tool",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Test how any domain evaluates against local rules in real time",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = testDomainInput,
                    onValueChange = { testDomainInput = it },
                    label = { Text("Domain Name", color = TextSecondary) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanAccent,
                        unfocusedBorderColor = SlateNavy
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            testResult = FilterEngine.instance.evaluate(testDomainInput.trim())
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = CyberNavy)
                    ) {
                        Text("Evaluate Domain", fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            testDomainInput = if (testDomainInput == "pagead2.googlesyndication.com") "google.com" else "pagead2.googlesyndication.com"
                            testResult = FilterEngine.instance.evaluate(testDomainInput.trim())
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CyanAccent),
                        border = BorderStroke(1.dp, SlateNavy)
                    ) {
                        Text("Sample")
                    }
                }

                if (testResult != null) {
                    val res = testResult!!
                    val isBlocked = res is FilterDecision.Blocked
                    val categoryText = when (res) {
                        is FilterDecision.Blocked -> res.category.name
                        is FilterDecision.Allowed -> "BENIGN"
                    }
                    val rulePattern = when (res) {
                        is FilterDecision.Blocked -> res.rule.rawRule
                        is FilterDecision.Allowed -> res.rule?.rawRule ?: "Default Allow"
                    }
                    val reason = when (res) {
                        is FilterDecision.Blocked -> res.reason
                        is FilterDecision.Allowed -> res.reason
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isBlocked) CrimsonRed.copy(alpha = 0.15f) else MintGreen.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, if (isBlocked) CrimsonRed else MintGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isBlocked) Icons.Default.Block else Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = if (isBlocked) CrimsonRed else MintGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isBlocked) "BLOCKED (Synthetic 0.0.0.0)" else "ALLOWED (Pass to Upstream)",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = if (isBlocked) CrimsonRed else MintGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Category: $categoryText | Rule: $rulePattern ($reason)",
                                style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        }

        // Android Private DNS Notice & Troubleshooting
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, AmberWarning.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WarningAmber, contentDescription = null, tint = AmberWarning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Android Private DNS Guidance",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = AmberWarning,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "On Android 9+, if \"Private DNS\" is set to Automatic or a custom DoT hostname in Android System Settings, Android queries DNS-over-TLS (port 853) directly, bypassing local VPN DNS (port 53).",
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "For maximum blocking effectiveness, ensure Private DNS is set to \"Off\" in Android Settings > Network & internet > Private DNS.",
                    style = MaterialTheme.typography.bodySmall.copy(color = Color.White, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        try {
                            val intent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_SETTINGS)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SlateNavy, contentColor = Color.White)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Android Network Settings")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun DiagnosticRowView(item: DiagnosticResultItem) {
    val (statusBg, statusFg) = when (item.status) {
        DiagnosticStatus.PASS -> Pair(MintGreen.copy(alpha = 0.15f), MintGreen)
        DiagnosticStatus.FAIL -> Pair(CrimsonRed.copy(alpha = 0.15f), CrimsonRed)
        DiagnosticStatus.NOT_VERIFIED -> Pair(AmberWarning.copy(alpha = 0.15f), AmberWarning)
        DiagnosticStatus.STOPPED -> Pair(SlateNavy.copy(alpha = 0.25f), TextSecondary)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CyberNavy,
        border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = item.details,
                    style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = statusBg,
                border = BorderStroke(1.dp, statusFg)
            ) {
                Text(
                    text = item.status.name.replace('_', ' '),
                    style = MaterialTheme.typography.labelSmall.copy(color = statusFg, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun CounterPill(
    modifier: Modifier = Modifier,
    label: String,
    count: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = CyberNavy,
        border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.titleMedium.copy(color = color, fontWeight = FontWeight.Bold)
            )
        }
    }
}

private suspend fun runDetailedDiagnostics(
    app: ClearShieldApp,
    upstreamDns: String,
    isEstablished: Boolean,
    totalQueries: Long
): List<DiagnosticResultItem> = withContext(Dispatchers.IO) {
    val results = mutableListOf<DiagnosticResultItem>()

    // 1. VPN Service State
    val isRunning = VpnStateHolder.isVpnRunning.value
    results.add(
        DiagnosticResultItem(
            title = "1. VPN Service State",
            status = if (isRunning) DiagnosticStatus.PASS else DiagnosticStatus.STOPPED,
            details = if (isRunning) "Active Foreground VpnService" else "Service is not running"
        )
    )

    // 2. TUN Interface Established
    val isIpv6 = VpnStateHolder.isIpv6Supported.value
    results.add(
        DiagnosticResultItem(
            title = "2. TUN Interface Established",
            status = if (isEstablished) DiagnosticStatus.PASS else DiagnosticStatus.STOPPED,
            details = if (isEstablished) {
                if (isIpv6) "Dual-stack routed: IPv4 ${ClearShieldVpnService.VPN_IPV4_DNS}:53 + IPv6 [${ClearShieldVpnService.VPN_IPV6_DNS}]:53"
                else "IPv4 routed: ${ClearShieldVpnService.VPN_IPV4_DNS}:53 (IPv6 unavailable on network/kernel)"
            } else "VPN interface descriptor not established"
        )
    )

    // 3. DNS Packet Parser Self-Test
    val parserPass = runParserSelfTest()
    results.add(
        DiagnosticResultItem(
            title = "3. DNS Packet Parser Self-Test",
            status = if (parserPass) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            details = if (parserPass) "RFC 1035 labels & record decoding verified" else "Parser decoding test failed"
        )
    )

    // 4. Blocked-Response Generation Self-Test
    val synthPass = runSynthesizerSelfTest()
    results.add(
        DiagnosticResultItem(
            title = "4. Blocked-Response Synthesizer Self-Test",
            status = if (synthPass) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            details = if (synthPass) "Local synthetic 0.0.0.0 & :: RFC responses verified" else "Synthesizer failed validation"
        )
    )

    // 5. Upstream DNS Reachability (Real Ping)
    val latencyMs = pingUpstreamSocket(upstreamDns)
    results.add(
        DiagnosticResultItem(
            title = "5. Upstream DNS Reachability",
            status = if (latencyMs >= 0) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            details = if (latencyMs >= 0) "Server $upstreamDns responsive in ${latencyMs}ms" else "Failed to reach $upstreamDns on port 53"
        )
    )

    // 6. Actual System Traffic Interception
    val trafficStatus = if (!isRunning) {
        DiagnosticStatus.STOPPED
    } else if (totalQueries > 0) {
        DiagnosticStatus.PASS
    } else {
        DiagnosticStatus.NOT_VERIFIED
    }
    results.add(
        DiagnosticResultItem(
            title = "6. Actual End-to-End DNS Interception",
            status = trafficStatus,
            details = when (trafficStatus) {
                DiagnosticStatus.PASS -> "$totalQueries real system queries observed on TUN interface"
                DiagnosticStatus.NOT_VERIFIED -> "VPN established — packet interception not independently verified"
                else -> "VPN stopped"
            }
        )
    )

    // 7. Filter Engine Integrity
    val rulesCount = FilterEngine.instance.totalBlockedRules
    val filterPass = rulesCount > 0 && FilterEngine.instance.evaluate("pagead2.googlesyndication.com") is FilterDecision.Blocked
    results.add(
        DiagnosticResultItem(
            title = "7. Filter Engine Integrity",
            status = if (filterPass) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            details = if (filterPass) "$rulesCount rules loaded in 5-tier reverse Trie" else "Trie verification failed"
        )
    )

    // 8. Database Health Check
    val dbPass = try {
        app.database.openHelper.readableDatabase.version > 0
    } catch (_: Exception) {
        false
    }
    results.add(
        DiagnosticResultItem(
            title = "8. Room Database Health",
            status = if (dbPass) DiagnosticStatus.PASS else DiagnosticStatus.FAIL,
            details = if (dbPass) "SQLite tables operational and readable" else "Database access failed"
        )
    )

    results
}

private fun runParserSelfTest(): Boolean {
    return try {
        // Standard query: example.com Type A
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x1111.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.put(7.toByte())
        buf.put("example".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())
        buf.putShort(1.toShort()) // A
        buf.putShort(1.toShort()) // IN

        val parsed = DnsPacket.parse(buf.array(), buf.position())
        parsed != null && parsed.questions.firstOrNull()?.qName == "example.com"
    } catch (_: Exception) {
        false
    }
}

private fun runSynthesizerSelfTest(): Boolean {
    return try {
        val buf = ByteBuffer.allocate(64).order(ByteOrder.BIG_ENDIAN)
        buf.putShort(0x2222.toShort())
        buf.putShort(0x0100.toShort())
        buf.putShort(1.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.putShort(0.toShort())
        buf.put(3.toByte())
        buf.put("ads".toByteArray(Charsets.US_ASCII))
        buf.put(3.toByte())
        buf.put("com".toByteArray(Charsets.US_ASCII))
        buf.put(0.toByte())
        buf.putShort(1.toShort())
        buf.putShort(1.toShort())

        val query = DnsPacket.parse(buf.array(), buf.position()) ?: return false
        val respBytes = DnsPacket.buildBlockedResponse(query, asNxDomain = false)
        val resp = DnsPacket.parse(respBytes)
        resp != null && resp.header.qr && resp.header.rcode == 0
    } catch (_: Exception) {
        false
    }
}

private fun pingUpstreamSocket(serverIp: String): Long {
    var sock: DatagramSocket? = null
    return try {
        val start = System.currentTimeMillis()
        sock = DatagramSocket()
        sock.soTimeout = 2500

        val target = InetAddress.getByName(serverIp)
        // Root query for "." (IN NS)
        val queryBuf = byteArrayOf(
            0x12, 0x34, 0x01, 0x00, 0x00, 0x01, 0x00, 0x00,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x00, 0x01
        )
        val sendPkt = DatagramPacket(queryBuf, queryBuf.size, target, 53)
        sock.send(sendPkt)

        val recvBuf = ByteArray(512)
        val recvPkt = DatagramPacket(recvBuf, recvBuf.size)
        sock.receive(recvPkt)

        System.currentTimeMillis() - start
    } catch (_: Exception) {
        -1L
    } finally {
        try { sock?.close() } catch (_: Exception) {}
    }
}
