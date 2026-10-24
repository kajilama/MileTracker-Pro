package com.example.ui.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AutoClassificationRule
import com.example.data.repository.MileageRepository
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AutoClassificationSubmenu(
    rules: List<AutoClassificationRule>,
    repository: MileageRepository,
    onShowMessage: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val tripTypes by repository.allTripTypes.collectAsState(initial = emptyList())
    var showDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<AutoClassificationRule?>(null) }
    var ruleToDelete by remember { mutableStateOf<AutoClassificationRule?>(null) }

    val categories = listOf("Home", "Store", "Office", "Medical", "Friends", "Relatives", "Unsaved Address")

    if (ruleToDelete != null) {
        AlertDialog(
            onDismissRequest = { ruleToDelete = null },
            title = { Text("Delete Classification Rule?") },
            text = { Text("Are you sure you want to delete '${ruleToDelete?.ruleName}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val r = ruleToDelete
                        ruleToDelete = null
                        if (r != null) {
                            coroutineScope.launch {
                                repository.deleteRule(r)
                                onShowMessage("Deleted rule: ${r.ruleName}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { ruleToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Clean Text-Line Rule Engine", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(
                    "Drives are automatically categorized upon start or finish matching origin and destination categories/patterns. Target Trip Types read directly from Settings > Trip Types.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        rules.forEach { rule ->
            val resolvedTripTypeName = tripTypes.firstOrNull { it.id == rule.tripTypeId }?.name
                ?: tripTypes.firstOrNull { it.name.equals(rule.tripTypeName, ignoreCase = true) }?.name
                ?: "Unclassified"

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.ruleName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${rule.originPattern} ${if (rule.isBidirectional) "⇄" else "→"} ${rule.destinationPattern} = [$resolvedTripTypeName]",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    repository.saveRule(rule.copy(isEnabled = it))
                                    onShowMessage("Rule ${if (it) "enabled" else "disabled"}")
                                }
                            }
                        )
                        IconButton(onClick = {
                            editingRule = rule
                            showDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            ruleToDelete = rule
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        Button(
            onClick = {
                editingRule = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Classification Rule")
        }
    }

    if (showDialog) {
        var name by remember { mutableStateOf(editingRule?.ruleName ?: "") }
        var origin by remember { mutableStateOf(editingRule?.originPattern ?: "") }
        var dest by remember { mutableStateOf(editingRule?.destinationPattern ?: "") }
        val initialTripType = remember(editingRule, tripTypes) {
            tripTypes.firstOrNull { it.id == editingRule?.tripTypeId }
                ?: tripTypes.firstOrNull { it.name.equals(editingRule?.tripTypeName, ignoreCase = true) }
                ?: tripTypes.firstOrNull()
        }
        var selectedTripTypeId by remember { mutableStateOf(initialTripType?.id ?: "type_1") }
        var selectedTripTypeName by remember { mutableStateOf(initialTripType?.name ?: "Walmart Spark") }
        var isBidirectional by remember { mutableStateOf(editingRule?.isBidirectional ?: true) }

        // Helper to toggle category in a pipe-separated string
        fun toggleCategory(current: String, cat: String): String {
            val parts = current.split("|").map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
            if (parts.any { it.equals(cat, ignoreCase = true) }) {
                parts.removeAll { it.equals(cat, ignoreCase = true) }
            } else {
                parts.add(cat)
            }
            return parts.joinToString("|")
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingRule == null) "Add Classification Rule" else "Edit Classification Rule") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Rule Description (e.g. Walmart Delivery Loop)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Origin Pattern with selectable toggle buttons
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = origin,
                            onValueChange = { origin = it },
                            label = { Text("Origin Pattern / Category") },
                            placeholder = { Text("Select buttons below or type text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Tap labels to add/combine with divider (e.g. Store|Office):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = origin.split("|").map { it.trim() }.any { it.equals(cat, ignoreCase = true) }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { origin = toggleCategory(origin, cat) },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // Destination Pattern with selectable toggle buttons
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = dest,
                            onValueChange = { dest = it },
                            label = { Text("Destination Pattern / Category") },
                            placeholder = { Text("Select buttons below or type text") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            "Tap labels to add/combine with divider (e.g. Relative|Friends):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSelected = dest.split("|").map { it.trim() }.any { it.equals(cat, ignoreCase = true) }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { dest = toggleCategory(dest, cat) },
                                    label = { Text(cat, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    // Target Trip Type selector reading from Room Trip Types table
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Target Trip Type:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            tripTypes.forEach { tt ->
                                val isSelected = selectedTripTypeId == tt.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedTripTypeId = tt.id
                                        selectedTripTypeName = tt.name
                                    },
                                    label = { Text(tt.name, fontSize = 11.sp) },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                    } else null
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isBidirectional, onCheckedChange = { isBidirectional = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Bidirectional (⇄ reverse trip also matches)", style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && origin.isNotBlank() && dest.isNotBlank()) {
                        val matchedType = tripTypes.firstOrNull { it.id == selectedTripTypeId }
                        val r = AutoClassificationRule(
                            id = editingRule?.id ?: UUID.randomUUID().toString(),
                            ruleName = name.trim(),
                            originPattern = origin.trim(),
                            destinationPattern = dest.trim(),
                            tripTypeId = matchedType?.id ?: selectedTripTypeId,
                            tripTypeName = matchedType?.name ?: selectedTripTypeName.trim(),
                            isEnabled = true,
                            isBidirectional = isBidirectional
                        )
                        coroutineScope.launch {
                            repository.saveRule(r)
                            onShowMessage("Saved rule: ${r.ruleName}")
                        }
                        showDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDialog = false }) { Text("Cancel") }
            }
        )
    }
}
