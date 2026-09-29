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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clearshield.adblocker.ClearShieldApp
import com.clearshield.adblocker.data.db.entities.FilterListEntity
import com.clearshield.adblocker.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterListsScreen() {
    val app = ClearShieldApp.instance
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val filterLists by app.filterRepository.getAllFilterLists().collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var updatingListId by remember { mutableStateOf<Long?>(null) }
    var isUpdatingAll by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        containerColor = CyberNavy,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = CyanAccent,
                contentColor = CyberNavy
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Subscription")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Filter Lists",
                        style = MaterialTheme.typography.headlineMedium.copy(color = Color.White)
                    )
                    Text(
                        text = "Community ad & tracker subscriptions",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }

                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            isUpdatingAll = true
                            var totalUpdated = 0
                            for (list in filterLists) {
                                if (list.isEnabled) {
                                    val res = app.filterRepository.updateFilterListFromNetwork(list)
                                    if (res.isSuccess) totalUpdated += res.getOrDefault(0)
                                }
                            }
                            isUpdatingAll = false
                            Toast.makeText(context, "Updated filter lists ($totalUpdated rules)", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SlateNavy,
                        contentColor = CyanAccent
                    ),
                    enabled = !isUpdatingAll
                ) {
                    if (isUpdatingAll) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = CyanAccent, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Update All")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filterLists, key = { it.id }) { list ->
                    FilterListCard(
                        list = list,
                        isUpdating = updatingListId == list.id,
                        lastUpdatedText = dateFormat.format(Date(list.lastUpdated)),
                        onToggle = {
                            scope.launch { app.filterRepository.toggleFilterList(list) }
                        },
                        onUpdate = {
                            scope.launch {
                                updatingListId = list.id
                                val res = app.filterRepository.updateFilterListFromNetwork(list)
                                updatingListId = null
                                if (res.isSuccess) {
                                    Toast.makeText(context, "Loaded ${res.getOrNull()} rules", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onDelete = if (!list.url.startsWith("bundled://")) {
                            { scope.launch { app.filterRepository.removeFilterList(list) } }
                        } else null
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddFilterListDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, url ->
                scope.launch {
                    app.filterRepository.addFilterList(name, url)
                    Toast.makeText(context, "Added and downloaded filter list", Toast.LENGTH_SHORT).show()
                }
                showAddDialog = false
            }
        )
    }
}

@Composable
fun FilterListCard(
    list: FilterListEntity,
    isUpdating: Boolean,
    lastUpdatedText: String,
    onToggle: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, if (list.isEnabled) SlateNavy else SlateNavy.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = list.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = if (list.isEnabled) Color.White else TextSecondary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    if (list.description.isNotEmpty()) {
                        Text(
                            text = list.description,
                            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                            maxLines = 2
                        )
                    }
                }
                Switch(
                    checked = list.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CyberNavy,
                        checkedTrackColor = CyanAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = SlateNavy.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (list.ruleCount > 0) "${list.ruleCount} rules active" else "Cached / Ready",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = CyanAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Updated $lastUpdatedText",
                        style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onDelete != null) {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CrimsonRed)
                        }
                    }
                    IconButton(onClick = onUpdate, enabled = !isUpdating) {
                        if (isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = CyanAccent, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Update Now", tint = CyanAccent)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddFilterListDialog(
    onDismiss: () -> Unit,
    onAdd: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Filter Subscription", color = Color.White) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Enter the subscription URL (Adblock Plus / EasyList format or Hosts format supported).",
                    style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Subscription URL") },
                    placeholder = { Text("https://example.com/rules.txt") },
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
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onAdd(name.trim(), url.trim()) },
                enabled = name.isNotBlank() && url.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = CyberNavy)
            ) {
                Text("Add & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = DarkSurface
    )
}
