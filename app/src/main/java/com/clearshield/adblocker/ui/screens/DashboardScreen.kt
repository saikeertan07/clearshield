package com.clearshield.adblocker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearshield.adblocker.engine.filter.FilterEngine
import com.clearshield.adblocker.service.VpnStateHolder
import com.clearshield.adblocker.ui.theme.*

import com.clearshield.adblocker.service.VpnStatus

@Composable
fun DashboardScreen(
    onToggleVpn: () -> Unit,
    onNavigateTo: (String) -> Unit
) {
    val vpnStatus by VpnStateHolder.vpnStatus.collectAsState()
    val isRunning by VpnStateHolder.isVpnRunning.collectAsState()
    val totalQueries by VpnStateHolder.totalQueries.collectAsState()
    val blockedQueries by VpnStateHolder.blockedQueries.collectAsState()
    val adsBlocked by VpnStateHolder.adsBlocked.collectAsState()
    val trackersBlocked by VpnStateHolder.trackersBlocked.collectAsState()
    val upstreamDns by VpnStateHolder.activeUpstreamDns.collectAsState()
    val isDnsDegraded by VpnStateHolder.isDnsDegraded.collectAsState()
    val dnsDegradedReason by VpnStateHolder.dnsDegradedReason.collectAsState()

    val blockPercentage = if (totalQueries > 0) {
        ((blockedQueries.toDouble() / totalQueries) * 100).toInt()
    } else 0

    val totalRules = FilterEngine.instance.totalBlockedRules

    val statusColor = when (vpnStatus) {
        VpnStatus.PROTECTED -> MintGreen
        VpnStatus.STARTING -> AmberWarning
        VpnStatus.OFF -> TextSecondary
        VpnStatus.ERROR -> CrimsonRed
        VpnStatus.PERMISSION_REQUIRED -> AmberWarning
        VpnStatus.NETWORK_UNAVAILABLE -> AmberWarning
    }

    val app = com.clearshield.adblocker.ClearShieldApp.instance
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPrivateDnsActive by remember {
        mutableStateOf(com.clearshield.adblocker.engine.dns.PrivateDnsDetector.isPrivateDnsActive(context))
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isPrivateDnsActive = com.clearshield.adblocker.engine.dns.PrivateDnsDetector.isPrivateDnsActive(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavy)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ClearShield",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Text(
                    text = "On-Device Local DNS Ad Blocker",
                    style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent)
                )
            }

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, statusColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
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

        // Persistent Private DNS Warning Banner
        if (isPrivateDnsActive) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = AmberWarning.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, AmberWarning)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning",
                            tint = AmberWarning,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Private DNS is Bypassing ClearShield",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Android's built-in Private DNS routes queries directly over port 853 (DNS-over-TLS), bypassing ClearShield's local ad-blocking engine. To protect apps and block ads, set Private DNS to \"Off\" in Android Settings. ClearShield will secure your queries with DNS-over-HTTPS (DoH).",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = com.clearshield.adblocker.engine.dns.PrivateDnsDetector.getPrivateDnsInstructions(),
                        style = MaterialTheme.typography.bodySmall.copy(color = MintGreen, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                try {
                                    context.startActivity(com.clearshield.adblocker.engine.dns.PrivateDnsDetector.createSettingsIntent())
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(android.content.Intent(android.provider.Settings.ACTION_SETTINGS).apply {
                                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                                        })
                                    } catch (_: Exception) {}
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning, contentColor = CyberNavy),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Settings", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                isPrivateDnsActive = com.clearshield.adblocker.engine.dns.PrivateDnsDetector.isPrivateDnsActive(context)
                            },
                            border = BorderStroke(1.dp, AmberWarning),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AmberWarning),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retest", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Degraded DNS Warning Banner
        if (isDnsDegraded) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CrimsonRed.copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, CrimsonRed)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Degraded DNS",
                            tint = CrimsonRed,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Upstream DNS Degraded",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = dnsDegradedReason ?: "Multiple upstream DNS query failures detected. Your upstream provider may be unreachable or throttling.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary, lineHeight = 18.sp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { onNavigateTo("dns_settings") },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = Color.White),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Configure Upstream DNS", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Shield Status Hero Button
        ShieldHeroButton(
            isRunning = isRunning,
            onClick = onToggleVpn
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isRunning) "Protection Enabled" else "Protection Disabled",
            style = MaterialTheme.typography.titleLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = if (isRunning) "Filtering ads & trackers system-wide" else "Tap the shield to start local VPN filter",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        // DNS and Active Rules pill row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Dns,
                title = "Upstream DNS",
                value = upstreamDns,
                onClick = { onNavigateTo("dns_settings") }
            )
            InfoPill(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Security,
                title = "Active Rules",
                value = if (totalRules > 0) "$totalRules rules" else "Active",
                onClick = { onNavigateTo("filter_lists") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Metrics 2x2 Grid
        Text(
            text = "Real-Time Protection Metrics",
            style = MaterialTheme.typography.titleMedium.copy(
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Ads Blocked",
                value = "$adsBlocked",
                icon = Icons.Default.Block,
                color = CrimsonRed
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Trackers Stopped",
                value = "$trackersBlocked",
                icon = Icons.Default.GpsFixed,
                color = AmberWarning
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "DNS Requests",
                value = "$totalQueries",
                icon = Icons.Default.SwapVert,
                color = CyanAccent
            )
            MetricCard(
                modifier = Modifier.weight(1f),
                title = "Block Rate",
                value = "$blockPercentage%",
                icon = Icons.Default.PieChart,
                color = MintGreen
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick Navigation Tiles
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                QuickNavRow(
                    icon = Icons.Default.Sensors,
                    title = "Live Query Inspector",
                    subtitle = "Inspect incoming and blocked domain queries live",
                    onClick = { onNavigateTo("protection") }
                )
                HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                QuickNavRow(
                    icon = Icons.Default.CheckCircle,
                    title = "Domain Allowlist",
                    subtitle = "Always pass trusted domains without blocking",
                    onClick = { onNavigateTo("allowlist") }
                )
                HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                QuickNavRow(
                    icon = Icons.Default.Apps,
                    title = "Per-App Protection",
                    subtitle = "Exclude or include individual Android apps",
                    onClick = { onNavigateTo("applications") }
                )
                HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 8.dp))
                QuickNavRow(
                    icon = Icons.Default.BugReport,
                    title = "Diagnostics & Engine Test",
                    subtitle = "Inspect TUN telemetry, verify rules, and Private DNS tips",
                    onClick = { onNavigateTo("diagnostics") }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ShieldHeroButton(
    isRunning: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shield_pulse"
    )

    val haloColor by animateColorAsState(
        targetValue = if (isRunning) CyanAccent.copy(alpha = 0.25f) else Color.Transparent,
        animationSpec = tween(500),
        label = "halo_color"
    )

    val shieldBgColor by animateColorAsState(
        targetValue = if (isRunning) DeepNavy else DarkSurface,
        animationSpec = tween(500),
        label = "shield_bg"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isRunning) CyanAccent else SlateNavy,
        animationSpec = tween(500),
        label = "shield_border"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(190.dp)
            .clickable(onClick = onClick)
    ) {
        // Outer pulsing aura
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(haloColor)
        )

        // Main circular button
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(shieldBgColor, CyberNavy)
                    )
                )
                .border(3.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isRunning) Icons.Default.Security else Icons.Default.Shield,
                contentDescription = "VPN Toggle",
                tint = if (isRunning) CyanAccent else TextSecondary,
                modifier = Modifier.size(68.dp)
            )
        }
    }
}

@Composable
fun InfoPill(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = DarkCard,
        border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = CyanAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
fun QuickNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(DeepNavy),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = title, tint = CyanAccent, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}
