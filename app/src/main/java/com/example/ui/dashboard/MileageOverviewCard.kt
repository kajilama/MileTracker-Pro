package com.example.ui.dashboard

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Trip
import com.example.data.model.TripPurpose
import com.example.ui.theme.ColorBusiness
import com.example.ui.theme.ColorCharity
import com.example.ui.theme.ColorMedical
import com.example.ui.theme.ColorPersonal

enum class OverviewPeriod(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year"),
    ALL("All")
}

@Composable
fun MileageOverviewCard(
    trips: List<Trip>,
    selectedPeriod: OverviewPeriod,
    onPeriodSelected: (OverviewPeriod) -> Unit,
    distanceUnit: String = "Miles",
    currencySymbol: String = "$",
    modifier: Modifier = Modifier
) {
    val businessMiles = trips.filter { it.purpose == TripPurpose.BUSINESS }.sumOf { it.distanceMiles }
    val personalMiles = trips.filter { it.purpose == TripPurpose.PERSONAL }.sumOf { it.distanceMiles }
    val medicalMiles = trips.filter { it.purpose == TripPurpose.MEDICAL }.sumOf { it.distanceMiles }
    val charityMiles = trips.filter { it.purpose == TripPurpose.CHARITY }.sumOf { it.distanceMiles }
    val otherMiles = trips.filter { it.purpose == TripPurpose.OTHER }.sumOf { it.distanceMiles }

    val totalMiles = trips.sumOf { it.distanceMiles }
    val totalDeduction = trips.sumOf { it.totalDeduction }

    val slices = listOf(
        DonutSlice(businessMiles, ColorBusiness, "Business"),
        DonutSlice(personalMiles, ColorPersonal, "Personal"),
        DonutSlice(medicalMiles, ColorMedical, "Medical"),
        DonutSlice(charityMiles, ColorCharity, "Charity"),
        DonutSlice(otherMiles, Color(0xFF64748B), "Other")
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mileage_overview_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title & Period Filter Chips
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Mileage Overview",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Period Filter Dropdown Menu (Immune to font size scaling, keeps layout clean)
                var dropdownExpanded by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { dropdownExpanded = true }
                            .testTag("period_dropdown_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = selectedPeriod.label,
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select Period",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        OverviewPeriod.values().forEach { period ->
                            val isSelected = period == selectedPeriod
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = period.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                } else null,
                                onClick = {
                                    onPeriodSelected(period)
                                    dropdownExpanded = false
                                },
                                modifier = Modifier.testTag("filter_period_${period.name.lowercase()}")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // CRITICAL REQUIREMENT: Mileage breakdowns and Donut Chart in the SAME ROW in 2 DIFFERENT CELLS/COLUMNS, with Donut Chart CENTERED!
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mileage_overview_columns_row"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1 (Left Cell): Mileage Breakdowns
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .testTag("mileage_breakdowns_column"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    BreakdownItem(
                        label = "Business",
                        miles = businessMiles,
                        color = ColorBusiness,
                        subtitle = "IRS standard $0.67/mi",
                        unit = distanceUnit
                    )
                    BreakdownItem(
                        label = "Personal",
                        miles = personalMiles,
                        color = ColorPersonal,
                        unit = distanceUnit
                    )
                    BreakdownItem(
                        label = "Medical / Charity",
                        miles = medicalMiles + charityMiles,
                        color = ColorMedical,
                        unit = distanceUnit
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Total Miles Line
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Total Miles",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "%.1f %s".format(totalMiles, if (distanceUnit == "Miles") "mi" else "km"),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Column 2 (Right Cell): Centered Donut Chart + Concise IRS Rate Badge directly below
                Column(
                    modifier = Modifier
                        .weight(0.9f)
                        .testTag("donut_chart_centered_cell"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    DonutChart(
                        slices = slices,
                        totalMiles = totalMiles,
                        unit = if (distanceUnit == "Miles") "MILES" else "KM",
                        size = 118.dp,
                        strokeWidth = 13.dp
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Concise IRS Rate Badge positioned directly below the circular donut chart inside the same cell!
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                    ) {
                        Text(
                            text = "IRS ${currencySymbol}0.67/mi",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 11.sp
                            ),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Estimated IRS Tax Deduction Highlight
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Est. Tax Deduction:",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Text(
                        text = "%s%.2f".format(currencySymbol, totalDeduction),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun BreakdownItem(
    label: String,
    miles: Double,
    color: Color,
    subtitle: String? = null,
    unit: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(color, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Text(
            text = "%.1f %s".format(miles, if (unit == "Miles") "mi" else "km"),
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
