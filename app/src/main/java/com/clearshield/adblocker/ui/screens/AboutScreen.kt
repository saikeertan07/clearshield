package com.clearshield.adblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearshield.adblocker.ui.theme.*

@Composable
fun AboutScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavy)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(DeepNavy)
                .border(2.dp, CyanAccent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = CyanAccent,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "ClearShield",
            style = MaterialTheme.typography.headlineLarge.copy(
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = "Version 1.0.0 • Native Android",
            style = MaterialTheme.typography.bodyMedium.copy(color = CyanAccent)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Privacy Guarantee Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, MintGreen.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MintGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Transparent Privacy Guarantee",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• 100% On-Device Rule Matching: Filter list rules are evaluated entirely inside ClearShield's local engine. Blocked queries are intercepted and null-routed locally without touching the network.\n\n• Encrypted Upstream Forwarding: Unblocked queries are forwarded exclusively to your chosen upstream DNS provider (defaulting to encrypted DNS-over-HTTPS via Cloudflare).\n\n• Zero Telemetry or Third-Party Proxies: ClearShield contains zero telemetry SDKs, zero remote logging, and zero analytics beacons. We operate no remote proxy or intermediate VPN server.\n\n• Local-Only Data Storage: Real-time query stats and history are stored strictly on-device in a private SQLite database excluded from cloud backups.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Architecture Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Core Engine Architecture",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))

                ArchitectureRow(number = "1", title = "TUN Packet Interception", desc = "Intercepts system DNS UDP packets on port 53 without requiring root.")
                ArchitectureRow(number = "2", title = "Zero-Copy DNS Parser", desc = "Parses DNS questions (A, AAAA, HTTPS) and decodes compressed domain names.")
                ArchitectureRow(number = "3", title = "Reverse Suffix Trie", desc = "Sub-millisecond domain matching across tens of thousands of rules in O(depth).")
                ArchitectureRow(number = "4", title = "Local Response Synthesizer", desc = "Synthesizes immediate 0.0.0.0 responses for blocked ad/tracker requests.")
                ArchitectureRow(number = "5", title = "Socket Protection", desc = "Upstream queries are forwarded using protected sockets that bypass the VPN loop.")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tech Stack
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Technology Stack",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Kotlin 2.0 & Coroutines Flow\n• Jetpack Compose & Material 3\n• Room SQLite Database\n• Android VpnService & TUN Interface\n• RFC 1035 DNS & RFC 768 UDP Protocols",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 22.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Honest Filtering Scope Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "DNS Filtering Scope & Limits",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✓ What is blocked: Third-party ad networks, tracking analytics, telemetry beacons, and malware domains across web browsers and apps.\n\n✗ Limitations: In-stream video ads (such as YouTube video ads that share identical first-party streaming domains) and in-feed sponsored posts cannot be blocked at the DNS layer without breaking the media stream itself.",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary, lineHeight = 20.sp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ArchitectureRow(number: String, title: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(SlateNavy),
            contentAlignment = Alignment.Center
        ) {
            Text(text = number, style = MaterialTheme.typography.labelSmall.copy(color = CyanAccent, fontWeight = FontWeight.Bold))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.bodyMedium.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
            Text(text = desc, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
        }
    }
}
