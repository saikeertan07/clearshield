package com.clearshield.adblocker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.data.repository.InstalledAppInfo
import com.clearshield.adblocker.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ApplicationsScreen() {
    val app = ClearShieldApp.instance
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var filterUserAppsOnly by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        apps = app.appFilterRepository.loadInstalledApplications()
        isLoading = false
    }

    val filteredApps = apps.filter {
        val matchesSearch = it.appName.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        val matchesSystem = if (filterUserAppsOnly) !it.isSystemApp else true
        matchesSearch && matchesSystem
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CyberNavy)
            .padding(16.dp)
    ) {
        Text(
            text = "Per-App Protection",
            style = MaterialTheme.typography.headlineMedium.copy(color = Color.White)
        )
        Text(
            text = "Select which installed applications route through ClearShield",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search installed apps...", color = TextSecondary) },
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredApps.size} apps found",
                style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "User apps only",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = filterUserAppsOnly,
                    onCheckedChange = { filterUserAppsOnly = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberNavy,
                        checkedTrackColor = CyanAccent
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = CyanAccent)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { appInfo ->
                    AppFilterRow(
                        appInfo = appInfo,
                        onToggle = {
                            scope.launch {
                                app.appFilterRepository.toggleAppProtection(
                                    appInfo.packageName,
                                    appInfo.appName,
                                    appInfo.isProtected
                                )
                                apps = app.appFilterRepository.loadInstalledApplications()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppFilterRow(
    appInfo: InstalledAppInfo,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, SlateNavy.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(SlateNavy.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Android,
                        contentDescription = null,
                        tint = if (appInfo.isProtected) CyanAccent else TextSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = appInfo.appName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1
                    )
                    Text(
                        text = appInfo.packageName,
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary),
                        maxLines = 1
                    )
                }
            }

            Switch(
                checked = appInfo.isProtected,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = CyberNavy,
                    checkedTrackColor = MintGreen,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SlateNavy
                )
            )
        }
    }
}
