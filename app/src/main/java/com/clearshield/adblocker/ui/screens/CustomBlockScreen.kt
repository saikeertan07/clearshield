package com.clearshield.adblocker.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.data.db.entities.CustomBlockEntity
import com.clearshield.adblocker.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomBlockScreen() {
    val app = ClearShieldApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val blocklist by app.filterRepository.getAllCustomBlocklist().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredList = blocklist.filter {
        it.domain.contains(searchQuery.trim(), ignoreCase = true)
    }

    Scaffold(
        containerColor = CyberNavy,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CrimsonRed,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Custom Block Domain")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text(
                text = "Custom Blocklist",
                style = MaterialTheme.typography.headlineMedium.copy(color = Color.White)
            )
            Text(
                text = "User-defined domains to always block locally",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search custom blocklist...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = CrimsonRed) },
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
                    focusedBorderColor = CrimsonRed,
                    unfocusedBorderColor = SlateNavy,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (filteredList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Block, contentDescription = null, tint = SlateNavy, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (blocklist.isEmpty()) "No custom blocked domains added yet" else "No matching domains",
                            style = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredList, key = { it.id }) { item ->
                        CustomBlockCard(
                            item = item,
                            onToggle = { scope.launch { app.filterRepository.toggleCustomBlock(item) } },
                            onDelete = { scope.launch { app.filterRepository.removeCustomBlock(item) } }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddDomainDialog(
            title = "Add to Blocklist",
            description = "Enter domain to block (e.g. ads.example.com):",
            confirmText = "Block Domain",
            confirmColor = CrimsonRed,
            onDismiss = { showAddDialog = false },
            onAdd = { domain ->
                scope.launch {
                    app.filterRepository.addCustomBlock(domain)
                    Toast.makeText(context, "Added $domain to blocklist", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun CustomBlockCard(
    item: CustomBlockEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, if (item.isEnabled) CrimsonRed.copy(alpha = 0.5f) else SlateNavy.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Block, contentDescription = null, tint = if (item.isEnabled) CrimsonRed else TextSecondary)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = item.domain,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = if (item.isEnabled) Color.White else TextSecondary,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberNavy,
                        checkedTrackColor = CrimsonRed
                    )
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
                }
            }
        }
    }
}
