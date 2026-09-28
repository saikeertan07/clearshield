package com.clearshield.adblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clearshield.adblocker.data.db.entities.QueryLogItem
import com.clearshield.adblocker.service.VpnStateHolder
import com.clearshield.adblocker.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionScreen(
    onAddAllowlist: (String) -> Unit,
    onAddBlocklist: (String) -> Unit
) {
    val queries by VpnStateHolder.recentQueries.collectAsState()
    val isRunning by VpnStateHolder.isVpnRunning.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var filterMode by remember { mutableStateOf(0) } // 0 = All, 1 = Blocked, 2 = Allowed

    val filteredQueries = queries.filter { item ->
        val matchesSearch = item.domain.contains(searchQuery.trim(), ignoreCase = true)
        val matchesFilter = when (filterMode) {
            1 -> item.isBlocked
            2 -> !item.isBlocked
            else -> true
        }
        matchesSearch && matchesFilter
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavy)
            .padding(16.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Live Traffic Inspector",
                    style = MaterialTheme.typography.headlineMedium.copy(color = Color.White)
                )
                Text(
                    text = if (isRunning) "Streaming live DNS requests locally" else "VPN paused - no traffic",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
            }
            IconButton(onClick = { VpnStateHolder.resetSessionStats() }) {
                Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear Log", tint = TextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Filter domain (e.g. google, doubleclick)", color = TextSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CyanAccent) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                    }
                }
            },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkCard,
                unfocusedContainerColor = DarkCard,
                focusedBorderColor = CyanAccent,
                unfocusedBorderColor = SlateNavy,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == 0,
                onClick = { filterMode = 0 },
                label = { Text("All (${queries.size})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanAccent,
                    selectedLabelColor = CyberNavy
                )
            )
            FilterChip(
                selected = filterMode == 1,
                onClick = { filterMode = 1 },
                label = { Text("Blocked (${queries.count { it.isBlocked }})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CrimsonRed,
                    selectedLabelColor = Color.White
                )
            )
            FilterChip(
                selected = filterMode == 2,
                onClick = { filterMode = 2 },
                label = { Text("Allowed (${queries.count { !it.isBlocked }})") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MintGreen,
                    selectedLabelColor = CyberNavy
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredQueries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = SlateNavy,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (queries.isEmpty()) "Waiting for DNS traffic..." else "No matching requests found",
                        style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredQueries, key = { it.id }) { item ->
                    QueryLogCard(
                        item = item,
                        timeStr = timeFormat.format(Date(item.timestamp)),
                        onAllow = { onAddAllowlist(item.domain) },
                        onBlock = { onAddBlocklist(item.domain) }
                    )
                }
            }
        }
    }
}

@Composable
fun QueryLogCard(
    item: QueryLogItem,
    timeStr: String,
    onAllow: () -> Unit,
    onBlock: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, if (item.isBlocked) CrimsonRed.copy(alpha = 0.4f) else SlateNavy.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status pill
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.isBlocked) CrimsonRed.copy(alpha = 0.2f) else MintGreen.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = if (item.isBlocked) "BLOCKED ${item.category}" else "ALLOWED",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (item.isBlocked) CrimsonRed else MintGreen,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = SlateNavy.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = item.queryType,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.domain,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                ),
                maxLines = 1
            )

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Reason: ${item.reason}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (item.isBlocked) {
                        OutlinedButton(
                            onClick = onAllow,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MintGreen)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Allow Domain")
                        }
                    } else {
                        OutlinedButton(
                            onClick = onBlock,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonRed)
                        ) {
                            Icon(Icons.Default.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Block Domain")
                        }
                    }
                }
            }
        }
    }
}
