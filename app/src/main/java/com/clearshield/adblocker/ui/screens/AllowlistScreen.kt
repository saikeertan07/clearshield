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
import com.clearshield.adblocker.data.db.entities.AllowlistEntity
import com.clearshield.adblocker.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllowlistScreen() {
    val app = ClearShieldApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val allowlist by app.filterRepository.getAllAllowlist().collectAsState(initial = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredList = allowlist.filter {
        it.domain.contains(searchQuery.trim(), ignoreCase = true)
    }

    Scaffold(
        containerColor = CyberNavy,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MintGreen,
                contentColor = CyberNavy
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Allowed Domain")
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
                text = "Domain Allowlist",
                style = MaterialTheme.typography.headlineMedium.copy(color = Color.White)
            )
            Text(
                text = "Allowed domains bypass all ad and tracker rules with highest priority",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search allowlist...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MintGreen) },
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
                    focusedBorderColor = MintGreen,
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
                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null, tint = SlateNavy, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (allowlist.isEmpty()) "No allowlist domains added yet" else "No matching domains",
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
                        AllowlistCard(
                            item = item,
                            onToggle = { scope.launch { app.filterRepository.toggleAllowlist(item) } },
                            onDelete = { scope.launch { app.filterRepository.removeAllowlist(item) } }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddDomainDialog(
            title = "Add to Allowlist",
            description = "Enter domain to always allow (e.g. trusted.example.com):",
            confirmText = "Allow Domain",
            confirmColor = MintGreen,
            onDismiss = { showAddDialog = false },
            onAdd = { domain ->
                scope.launch {
                    app.filterRepository.addAllowlist(domain)
                    Toast.makeText(context, "Added $domain to allowlist", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun AllowlistCard(
    item: AllowlistEntity,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, if (item.isEnabled) MintGreen.copy(alpha = 0.5f) else SlateNavy.copy(alpha = 0.4f))
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
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = if (item.isEnabled) MintGreen else TextSecondary)
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
                        checkedTrackColor = MintGreen
                    )
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun AddDomainDialog(
    title: String,
    description: String,
    confirmText: String,
    confirmColor: Color,
    onDismiss: () -> Unit,
    onAdd: (domain: String) -> Unit
) {
    var domainText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(description, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                OutlinedTextField(
                    value = domainText,
                    onValueChange = { domainText = it },
                    label = { Text("Domain Name") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (domainText.isNotBlank()) onAdd(domainText.trim()) },
                enabled = domainText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = confirmColor, contentColor = CyberNavy)
            ) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
        containerColor = DarkSurface
    )
}
