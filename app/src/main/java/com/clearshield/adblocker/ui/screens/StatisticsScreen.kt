package com.clearshield.adblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.data.db.entities.DailyStatsEntity
import com.clearshield.adblocker.service.VpnStateHolder
import com.clearshield.adblocker.ui.theme.*

@Composable
fun StatisticsScreen() {
    val app = ClearShieldApp.instance
    val recentStats by app.statsRepository.getRecentStats().collectAsState(initial = emptyList())

    val totalQueries by VpnStateHolder.totalQueries.collectAsState()
    val blockedQueries by VpnStateHolder.blockedQueries.collectAsState()
    val adsBlocked by VpnStateHolder.adsBlocked.collectAsState()
    val trackersBlocked by VpnStateHolder.trackersBlocked.collectAsState()
    val recentQueries by VpnStateHolder.recentQueries.collectAsState()

    val topBlocked = remember(recentQueries) {
        recentQueries.filter { it.isBlocked }
            .groupBy { it.domain }
            .mapValues { it.value.size }
            .toList()
            .sortedByDescending { it.second }
            .take(5)
    }

    val cleanQueries = (totalQueries - blockedQueries).coerceAtLeast(0)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavy)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Analytics & Statistics",
                style = MaterialTheme.typography.headlineMedium.copy(color = Color.White)
            )
            Text(
                text = "Persistent on-device filtering metrics",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
        }

        // Summary Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Session Traffic Distribution",
                        style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Multi-color Progress Bar
                    if (totalQueries > 0) {
                        val adWeight = (adsBlocked.toFloat() / totalQueries).coerceAtLeast(0.01f)
                        val trackerWeight = (trackersBlocked.toFloat() / totalQueries).coerceAtLeast(0.01f)
                        val cleanWeight = (cleanQueries.toFloat() / totalQueries).coerceAtLeast(0.01f)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(adWeight)
                                    .fillMaxHeight()
                                    .background(CrimsonRed)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(trackerWeight)
                                    .fillMaxHeight()
                                    .background(AmberWarning)
                            )
                            Box(
                                modifier = Modifier
                                    .weight(cleanWeight)
                                    .fillMaxHeight()
                                    .background(MintGreen)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SlateNavy.copy(alpha = 0.4f))
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendItem(color = CrimsonRed, label = "Ads ($adsBlocked)")
                        LegendItem(color = AmberWarning, label = "Trackers ($trackersBlocked)")
                        LegendItem(color = MintGreen, label = "Clean ($cleanQueries)")
                    }
                }
            }
        }

        // Top Blocked Domains Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Leaderboard, contentDescription = null, tint = CyanAccent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Top Blocked Domains",
                            style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    if (topBlocked.isEmpty()) {
                        Text(
                            text = "No blocked domain queries yet in this session.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        topBlocked.forEachIndexed { index, (domain, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = CyanAccent,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Text(
                                        text = domain,
                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                                        maxLines = 1
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = CrimsonRed.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        text = "$count hits",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = CrimsonRed,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            if (index < topBlocked.size - 1) {
                                HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }

        // Daily History Section
        item {
            Text(
                text = "Daily History (Past 7 Days)",
                style = MaterialTheme.typography.titleMedium.copy(color = Color.White)
            )
        }

        if (recentStats.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkCard)
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Daily history records will populate automatically as DNS queries are processed.",
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                        )
                    }
                }
            }
        } else {
            items(recentStats.size) { index ->
                val day = recentStats[index]
                DailyStatRow(day)
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
    }
}

@Composable
fun DailyStatRow(day: DailyStatsEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = day.dateString,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${day.totalQueries} Total Queries",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${day.blockedQueries} Blocked",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = CrimsonRed,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "${day.adsBlocked} ads • ${day.trackersBlocked} trackers",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                )
            }
        }
    }
}
