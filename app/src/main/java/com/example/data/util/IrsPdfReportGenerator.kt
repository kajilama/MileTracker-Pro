package com.example.data.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.CustomTripType
import com.example.data.model.Trip
import com.example.data.model.TripPurpose
import com.example.data.model.UserProfile
import com.example.data.service.IrsRateService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object IrsPdfReportGenerator {

    const val UNCLASSIFIED_TRIP_TYPE_ID = "UNCLASSIFIED"

    enum class ReportPeriod(val title: String) {
        THIS_WEEK("Current Week (Last 7 Days)"),
        THIS_MONTH("Current Month (MTD)"),
        LAST_MONTH("Prior Month"),
        CURRENT_YEAR("Tax Year (YTD)"),
        ALL_TIME("All Recorded Drives"),
        CUSTOM_RANGE("Custom Date Range")
    }

    fun resolveTripTypeId(trip: Trip, tripTypesById: Map<String, CustomTripType>): String {
        val id = trip.tripTypeId
        return if (!id.isNullOrBlank() && tripTypesById.containsKey(id)) {
            id
        } else {
            UNCLASSIFIED_TRIP_TYPE_ID
        }
    }

    fun resolveTripTypeName(trip: Trip, tripTypesById: Map<String, CustomTripType>): String {
        val id = trip.tripTypeId
        return if (!id.isNullOrBlank() && tripTypesById.containsKey(id)) {
            tripTypesById[id]?.name ?: "Unclassified"
        } else {
            "Unclassified"
        }
    }

    fun computeEffectiveRateForTrip(trip: Trip, tripTypesById: Map<String, CustomTripType>): Double {
        val tripYear = Calendar.getInstance().apply { timeInMillis = trip.dateTimestamp }.get(Calendar.YEAR)
        val officialRates = IrsRateService.getOfficialRateForYear(tripYear)
        val matchedType = trip.tripTypeId?.let { tripTypesById[it] }

        return when {
            matchedType != null && !matchedType.isTaxDeductible -> 0.0
            matchedType != null && matchedType.name.contains("Medical", ignoreCase = true) -> officialRates.medicalRate
            matchedType != null && matchedType.name.contains("Charity", ignoreCase = true) -> officialRates.charityRate
            matchedType != null && matchedType.isTaxDeductible -> officialRates.businessRate
            trip.purpose == TripPurpose.MEDICAL -> officialRates.medicalRate
            trip.purpose == TripPurpose.CHARITY -> officialRates.charityRate
            trip.purpose == TripPurpose.BUSINESS || trip.purpose == TripPurpose.GIG_DELIVERY -> officialRates.businessRate
            else -> 0.0
        }
    }

    fun computeEffectiveDeductionForTrip(trip: Trip, tripTypesById: Map<String, CustomTripType>): Double {
        return trip.distanceMiles * computeEffectiveRateForTrip(trip, tripTypesById)
    }

    fun isBusinessTrip(trip: Trip, tripTypesById: Map<String, CustomTripType>): Boolean {
        val matchedType = trip.tripTypeId?.let { tripTypesById[it] }
        if (matchedType != null) {
            return matchedType.isTaxDeductible
        }
        return trip.purpose == TripPurpose.BUSINESS ||
                trip.purpose == TripPurpose.GIG_DELIVERY ||
                trip.purpose == TripPurpose.MEDICAL ||
                trip.purpose == TripPurpose.CHARITY
    }

    fun filterTrips(
        trips: List<Trip>,
        tripTypes: List<CustomTripType>,
        period: ReportPeriod,
        vehicleFilter: String? = null,
        selectedTripTypeIds: Set<String>? = null,
        customStartEpoch: Long? = null,
        customEndEpoch: Long? = null
    ): List<Trip> {
        val now = System.currentTimeMillis()
        val oneDay = 24 * 60 * 60 * 1000L
        val tripTypesById = tripTypes.associateBy { it.id }

        val timeFiltered = when (period) {
            ReportPeriod.THIS_WEEK -> trips.filter { it.dateTimestamp >= now - (7 * oneDay) }
            ReportPeriod.THIS_MONTH -> trips.filter { it.dateTimestamp >= now - (30 * oneDay) }
            ReportPeriod.LAST_MONTH -> trips.filter { it.dateTimestamp in (now - (60 * oneDay))..(now - (30 * oneDay)) }
            ReportPeriod.CURRENT_YEAR -> trips.filter { it.dateTimestamp >= now - (365 * oneDay) }
            ReportPeriod.ALL_TIME -> trips
            ReportPeriod.CUSTOM_RANGE -> trips.filter { t ->
                val okStart = customStartEpoch == null || t.dateTimestamp >= customStartEpoch
                val okEnd = customEndEpoch == null || t.dateTimestamp <= customEndEpoch
                okStart && okEnd
            }
        }

        return timeFiltered.filter { t ->
            val matchVeh = vehicleFilter.isNullOrBlank() ||
                    t.vehicleId == vehicleFilter ||
                    t.vehicleName == vehicleFilter
            val resolvedTypeId = resolveTripTypeId(t, tripTypesById)
            val matchType = selectedTripTypeIds.isNullOrEmpty() || selectedTripTypeIds.contains(resolvedTypeId)
            matchVeh && matchType
        }.sortedByDescending { it.dateTimestamp }
    }

    fun generateIrsPdf(
        context: Context,
        userProfile: UserProfile,
        trips: List<Trip>,
        period: ReportPeriod,
        vehicleFilter: String? = null,
        selectedTripTypeIds: Set<String>? = null,
        tripTypes: List<CustomTripType> = emptyList(),
        preFilteredTrips: List<Trip>? = null
    ): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "IRS_Mileage_Report_${period.name}_$timestamp.pdf"
        val outputFile = File(reportsDir, fileName)

        val document = PdfDocument()

        val pageWidth = 612 // US Letter (8.5 x 11 in) at 72 dpi
        val pageHeight = 792
        val margin = 36f // 0.5 inch margins

        val tripTypesById = tripTypes.associateBy { it.id }
        val filteredTrips = preFilteredTrips?.sortedByDescending { it.dateTimestamp }
            ?: filterTrips(trips, tripTypes, period, vehicleFilter, selectedTripTypeIds)

        val totalMiles = filteredTrips.sumOf { it.distanceMiles }
        val businessTrips = filteredTrips.filter { isBusinessTrip(it, tripTypesById) }
        val businessMiles = businessTrips.sumOf { it.distanceMiles }
        val personalMiles = (totalMiles - businessMiles).coerceAtLeast(0.0)
        val totalDeduction = filteredTrips.sumOf { computeEffectiveDeductionForTrip(it, tripTypesById) }
        val totalExpenses = filteredTrips.sumOf { it.expenseAmount }
        val totalStops = filteredTrips.sumOf { it.stopsCount }

        val titlePaint = Paint().apply {
            color = Color.rgb(20, 45, 95)
            textSize = 14f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val subtitlePaint = Paint().apply {
            color = Color.rgb(80, 90, 105)
            textSize = 8.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val headerLabelPaint = Paint().apply {
            color = Color.rgb(40, 50, 70)
            textSize = 8f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val bodyPaint = Paint().apply {
            color = Color.rgb(30, 30, 30)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }

        val boldBodyPaint = Paint().apply {
            color = Color.rgb(20, 20, 20)
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val tableHeaderPaint = Paint().apply {
            color = Color.WHITE
            textSize = 7.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        val linePaint = Paint().apply {
            color = Color.rgb(210, 215, 225)
            strokeWidth = 0.75f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        val fillBoxPaint = Paint().apply {
            color = Color.rgb(243, 246, 252)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val darkHeaderBoxPaint = Paint().apply {
            color = Color.rgb(26, 60, 115)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val altRowPaint = Paint().apply {
            color = Color.rgb(248, 250, 253)
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.US)
        val timeFormat = SimpleDateFormat("hh:mm a", Locale.US)
        val fullDateFormat = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)

        val rowsPerPage = 16
        val totalPages = ((filteredTrips.size + rowsPerPage - 1) / rowsPerPage).coerceAtLeast(1)

        var tripIndex = 0

        for (pageNumber in 1..totalPages) {
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = document.startPage(pageInfo)
            val canvas = page.canvas

            var y = margin

            canvas.drawText("IRS CONTEMPORANEOUS VEHICLE MILEAGE & EXPENSE LOG", margin, y + 12f, titlePaint)
            canvas.drawText(
                "Internal Revenue Code § 274(d) Standard Mileage Documentation • See IRS Publication 463",
                margin,
                y + 23f,
                subtitlePaint
            )

            val generatedText = "Generated: ${fullDateFormat.format(Date())} | Period: ${period.title}"
            val genWidth = subtitlePaint.measureText(generatedText)
            canvas.drawText(generatedText, pageWidth - margin - genWidth, y + 12f, subtitlePaint)

            y += 32f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 8f

            if (pageNumber == 1) {
                val boxWidth = (pageWidth - (margin * 2))
                val boxHeight = 74f
                canvas.drawRect(margin, y, margin + boxWidth, y + boxHeight, fillBoxPaint)
                canvas.drawRect(margin, y, margin + boxWidth, y + boxHeight, linePaint)

                val col1X = margin + 10f
                canvas.drawText("TAXPAYER & ENTITY INFORMATION", col1X, y + 12f, headerLabelPaint)
                canvas.drawText("Taxpayer: ${userProfile.displayName}", col1X, y + 25f, bodyPaint)
                canvas.drawText("Business: ${userProfile.businessName}", col1X, y + 37f, bodyPaint)
                canvas.drawText("Tax ID / EIN: ${userProfile.taxIdEin} (${userProfile.taxStatus})", col1X, y + 49f, bodyPaint)
                canvas.drawText("Principal Base: ${userProfile.homeBaseAddress.take(45)}", col1X, y + 61f, bodyPaint)

                val col2X = margin + (boxWidth * 0.52f)
                canvas.drawText("AUDIT & DEDUCTION SUMMARY", col2X, y + 12f, headerLabelPaint)
                canvas.drawText(
                    "Total Miles Logged: %.1f mi (Business: %.1f mi, Personal: %.1f mi)".format(
                        totalMiles,
                        businessMiles,
                        personalMiles
                    ),
                    col2X,
                    y + 25f,
                    bodyPaint
                )
                canvas.drawText(
                    "Standard IRS Rate: $%.2f/mi | Intermediate Dropoffs: %d stops".format(
                        userProfile.irsBusinessRate,
                        totalStops
                    ),
                    col2X,
                    y + 37f,
                    bodyPaint
                )
                canvas.drawText(
                    "Direct Vehicle Expenses / Receipts: $%.2f".format(totalExpenses),
                    col2X,
                    y + 49f,
                    bodyPaint
                )
                canvas.drawText(
                    "NET IRS TAX DEDUCTION VALUE: $%.2f".format(totalDeduction + totalExpenses),
                    col2X,
                    y + 63f,
                    Paint(titlePaint).apply { textSize = 9.5f; color = Color.rgb(15, 110, 60) }
                )

                y += boxHeight + 10f
            }

            val tableY = y
            val tableHeight = 18f
            canvas.drawRect(margin, tableY, pageWidth - margin, tableY + tableHeight, darkHeaderBoxPaint)

            val c0 = margin + 5f
            val c1 = margin + 55f
            val c2 = margin + 140f
            val c3 = margin + 335f
            val c4 = margin + 370f
            val c5 = margin + 410f
            val c6 = margin + 455f
            val c7 = margin + 495f

            canvas.drawText("Date", c0, tableY + 12f, tableHeaderPaint)
            canvas.drawText("Trip Type", c1, tableY + 12f, tableHeaderPaint)
            canvas.drawText("Route (Origin → Destination)", c2, tableY + 12f, tableHeaderPaint)
            canvas.drawText("Stops", c3, tableY + 12f, tableHeaderPaint)
            canvas.drawText("Miles", c4, tableY + 12f, tableHeaderPaint)
            canvas.drawText("Deduction", c5, tableY + 12f, tableHeaderPaint)
            canvas.drawText("Receipt", c6, tableY + 12f, tableHeaderPaint)
            canvas.drawText("Vehicle / Notes", c7, tableY + 12f, tableHeaderPaint)

            y += tableHeight

            val rowHeight = 26f
            var pageTripsCount = 0

            while (tripIndex < filteredTrips.size && pageTripsCount < rowsPerPage) {
                val trip = filteredTrips[tripIndex]
                val rowTop = y
                val isAlt = pageTripsCount % 2 == 1

                if (isAlt) {
                    canvas.drawRect(margin, rowTop, pageWidth - margin, rowTop + rowHeight, altRowPaint)
                }
                canvas.drawRect(margin, rowTop, pageWidth - margin, rowTop + rowHeight, linePaint)

                val tripDateStr = dateFormat.format(Date(trip.dateTimestamp))
                val tripTimeStr = if (trip.startTimeMillis > 0) timeFormat.format(Date(trip.startTimeMillis)) else ""
                canvas.drawText(tripDateStr, c0, rowTop + 11f, boldBodyPaint)
                canvas.drawText(tripTimeStr, c0, rowTop + 20f, subtitlePaint)

                val tripTypeLabel = resolveTripTypeName(trip, tripTypesById)
                canvas.drawText(tripTypeLabel.take(18), c1, rowTop + 11f, boldBodyPaint)
                val subTitle = trip.title.take(18)
                canvas.drawText(subTitle, c1, rowTop + 20f, subtitlePaint)

                val originShort = trip.startAddress.take(36)
                val destShort = if (trip.isRoundTrip) "Round Trip Return" else trip.endAddress.take(36)
                canvas.drawText("From: $originShort", c2, rowTop + 10f, bodyPaint)
                canvas.drawText("To:   $destShort", c2, rowTop + 20f, bodyPaint)

                val stopsDisplay = if (trip.stopsCount > 0) "${trip.stopsCount} dropoffs" else "-"
                canvas.drawText(stopsDisplay, c3, rowTop + 14f, bodyPaint)

                canvas.drawText("%.1f mi".format(trip.distanceMiles), c4, rowTop + 14f, boldBodyPaint)

                val effDeduction = computeEffectiveDeductionForTrip(trip, tripTypesById)
                val dedText = if (effDeduction > 0) "$%.2f".format(effDeduction) else "$0.00"
                canvas.drawText(
                    dedText,
                    c5,
                    rowTop + 14f,
                    Paint(boldBodyPaint).apply { color = Color.rgb(15, 110, 60) }
                )

                val expText = if (trip.expenseAmount > 0) "$%.2f".format(trip.expenseAmount) else "-"
                canvas.drawText(expText, c6, rowTop + 14f, bodyPaint)

                val vehicleText = trip.vehicleName ?: "Primary Vehicle"
                val notesText = if (trip.notes.isNotBlank()) trip.notes.take(20) else tripTypeLabel.take(20)
                canvas.drawText(vehicleText, c7, rowTop + 11f, bodyPaint)
                canvas.drawText(notesText, c7, rowTop + 20f, subtitlePaint)

                y += rowHeight
                tripIndex++
                pageTripsCount++
            }

            if (pageNumber == totalPages) {
                y += 12f
                val sigBoxHeight = 58f
                canvas.drawRect(margin, y, pageWidth - margin, y + sigBoxHeight, fillBoxPaint)
                canvas.drawRect(margin, y, pageWidth - margin, y + sigBoxHeight, linePaint)

                canvas.drawText(
                    "TAXPAYER CONTEMPORANEOUS COMPLIANCE CERTIFICATION (IRC § 274(d))",
                    margin + 10f,
                    y + 12f,
                    headerLabelPaint
                )
                canvas.drawText(
                    "Under penalties of perjury, I declare that I have examined this mileage and vehicle log, including accompanying expense records,",
                    margin + 10f,
                    y + 24f,
                    bodyPaint
                )
                canvas.drawText(
                    "and to the best of my knowledge and belief, they are true, correct, contemporaneous, and complete, incurred exclusively for trade or business purposes.",
                    margin + 10f,
                    y + 34f,
                    bodyPaint
                )

                canvas.drawText("Taxpayer Signature: ____________________________________", margin + 10f, y + 49f, boldBodyPaint)
                canvas.drawText("Date: ________________________", margin + 360f, y + 49f, boldBodyPaint)
            }

            val footerY = pageHeight - margin + 10f
            canvas.drawLine(margin, footerY - 14f, pageWidth - margin, footerY - 14f, linePaint)
            canvas.drawText(
                "Confidential Tax Record • MileTrackerPro • Notice 2024-08 Compliant Log",
                margin,
                footerY - 3f,
                subtitlePaint
            )
            val pageStr = "Page $pageNumber of $totalPages"
            val pageStrWidth = subtitlePaint.measureText(pageStr)
            canvas.drawText(pageStr, pageWidth - margin - pageStrWidth, footerY - 3f, subtitlePaint)

            document.finishPage(page)
        }

        try {
            val fos = FileOutputStream(outputFile)
            document.writeTo(fos)
            fos.flush()
            fos.close()
        } finally {
            document.close()
        }

        return outputFile
    }

    fun sharePdfReport(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "IRS Mileage & Expense Report - ${pdfFile.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share / Print Official IRS PDF Log")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun viewPdfReport(context: Context, pdfFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            sharePdfReport(context, pdfFile)
        }
    }
}
