package com.example.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CustomTripType
import com.example.data.repository.MileageRepository
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun TripTypesSubmenu(
    tripTypes: List<CustomTripType>,
    repository: MileageRepository,
    onShowMessage: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val userProfile by repository.userProfile.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var editingType by remember { mutableStateOf<CustomTripType?>(null) }
    var typeToDelete by remember { mutableStateOf<CustomTripType?>(null) }

    if (typeToDelete != null) {
        AlertDialog(
            onDismissRequest = { typeToDelete = null },
            title = { Text("Delete Trip Type?") },
            text = { Text("Are you sure you want to delete '${typeToDelete?.name}'? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        val t = typeToDelete
                        typeToDelete = null
                        if (t != null) {
                            coroutineScope.launch {
                                repository.deleteTripType(t)
                                onShowMessage("Deleted trip type: ${t.name}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { typeToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // Explanatory card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Default Trip Type & Tax Rates",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Current default for new trips: '${userProfile.defaultTripTypeName}'. When auto-classification rules don't match, this type is applied automatically. You can also set it to Unclassified.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        tripTypes.forEach { type ->
            val color = try { Color(android.graphics.Color.parseColor(type.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
            val isCurrentDefault = type.isDefault ||
                userProfile.defaultTripTypeId == type.id ||
                userProfile.defaultTripTypeName.equals(type.name, ignoreCase = true)

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isCurrentDefault)
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    else
                        MaterialTheme.colorScheme.surface
                ),
                border = if (isCurrentDefault)
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                else null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(type.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                                Spacer(modifier = Modifier.width(6.dp))
                                if (isCurrentDefault) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                "Default",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                } else if (type.isTaxDeductible) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                                        Text(
                                            "Deductible",
                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "Rate: $%.2f/mi".format(type.ratePerMile),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isCurrentDefault) {
                            OutlinedButton(
                                onClick = {
                                    coroutineScope.launch {
                                        repository.setDefaultTripType(type.id, type.name)
                                        onShowMessage("Set '${type.name}' as default trip type for new drives")
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(34.dp)
                            ) {
                                Text("Set Default", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }

                        IconButton(onClick = {
                            editingType = type
                            showDialog = true
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = {
                            typeToDelete = type
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // Option to leave default as Unclassified
        if (userProfile.defaultTripTypeName != "Unclassified") {
            OutlinedButton(
                onClick = {
                    repository.preferencesManager.setDefaultTripType("Unclassified", "")
                    onShowMessage("New drives will default to 'Unclassified' unless auto-rules match")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Set Default to 'Unclassified' (Prompt Every Time)")
            }
        }

        Button(
            onClick = {
                editingType = null
                showDialog = true
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Add Custom Trip Type")
        }
    }

    if (showDialog) {
        var name by remember { mutableStateOf(editingType?.name ?: "") }
        var isDeductible by remember { mutableStateOf(editingType?.isTaxDeductible ?: true) }
        var rateStr by remember { mutableStateOf(editingType?.ratePerMile?.toString() ?: userProfile.irsBusinessRate.toString()) }
        var selectedHex by remember { mutableStateOf(editingType?.colorHex ?: "#0D9488") }
        var setAsDefault by remember { mutableStateOf(editingType?.isDefault ?: false) }

        val palette = listOf("#0D9488", "#EA580C", "#DC2626", "#16A34A", "#BE123C", "#DB2777", "#2563EB", "#7C3AED", "#64748B", "#B45309")

        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editingType == null) "New Trip Type" else "Edit Trip Type") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Trip Type Name (e.g. Walmart Spark, Instacart)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = rateStr,
                        onValueChange = { rateStr = it },
                        label = { Text("IRS Deduction Rate ($/mi)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isDeductible, onCheckedChange = { isDeductible = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("IRS Tax-Deductible Category", style = MaterialTheme.typography.bodyMedium)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = setAsDefault, onCheckedChange = { setAsDefault = it })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Set as Default for new drives", style = MaterialTheme.typography.bodyMedium)
                    }

                    Text("Badge Color:", style = MaterialTheme.typography.labelSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        palette.forEach { hex ->
                            val c = try { Color(android.graphics.Color.parseColor(hex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(if (selectedHex == hex) 2.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    .clickable { selectedHex = hex }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val tt = CustomTripType(
                            id = editingType?.id ?: UUID.randomUUID().toString(),
                            name = name.trim(),
                            colorHex = selectedHex,
                            isTaxDeductible = isDeductible,
                            ratePerMile = rateStr.toDoubleOrNull() ?: userProfile.irsBusinessRate,
                            isDefault = setAsDefault
                        )
                        coroutineScope.launch {
                            repository.saveTripType(tt)
                            if (setAsDefault) {
                                repository.setDefaultTripType(tt.id, tt.name)
                            }
                            onShowMessage("Saved trip type: ${tt.name}")
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
