package com.example.data.repository

data class KnowledgeArticle(
    val id: String,
    val section: String,
    val title: String,
    val summary: String,
    val content: String,
    val tags: List<String> = emptyList(),
    val officialUrl: String? = null
)

object KnowledgeBaseRepository {

    val SECTIONS = listOf(
        "All Sections",
        "Getting Started",
        "Permissions & Battery Settings",
        "How Trips Are Detected",
        "Trip Types & Rules",
        "Round Trips & Stops",
        "Reports & Taxes",
        "Troubleshooting Using Diagnostics",
        "FAQ"
    )

    val ARTICLES = listOf(
        // 1. Getting Started
        KnowledgeArticle(
            id = "kb_get_started",
            section = "Getting Started",
            title = "First-Time Driver Quickstart Guide",
            summary = "How to set up your primary vehicle, grant background GPS permissions, and log your first automated trip.",
            content = """
                Welcome to MileTracker Pro! Here is the recommended setup sequence:
                
                1. Vehicle Setup: Go to Settings > Vehicle and Maintenance. Add your vehicle (use the NHTSA VIN Decoder or Year + Make + Model selector to autofill your fuel type). Set your starting odometer reading.
                2. Set Default Trip Type: In Settings > Trip Types, select your primary trip type (e.g. Walmart Spark, Instacart, DoorDash) and set it as Default for new drives.
                3. Save Frequent Places: In Settings > Saved Places, save your Home Base and common fulfillment stores (e.g. 8400 Rosedale Hwy Walmart, local grocery stores).
                4. Background Tracking: Ensure Location permission is set to "Allow all the time" so trips start automatically when you drive above your trigger speed threshold.
                5. Review & Export: Review your logged trips on the Dashboard anytime, and view or export IRS-compliant CSV or PDF reports at tax time.
            """.trimIndent(),
            tags = listOf("quickstart", "setup", "vehicle", "first trip")
        ),

        // 2. Permissions and Battery Settings
        KnowledgeArticle(
            id = "kb_permissions_battery",
            section = "Permissions & Battery Settings",
            title = "Android Permissions & OEM Battery Optimization",
            summary = "Crucial system settings to ensure background GPS telemetry never gets killed by Samsung, Xiaomi, Google, or OnePlus battery managers.",
            content = """
                Android device manufacturers (OEMs) aggressively suspend background services to conserve battery. To guarantee 100% reliable trip detection:
                
                • Location Permission: Must be set to "Allow all the time", NOT "Only while using the app". Without background location, the OS terminates tracking the instant your screen turns off.
                • Precise Location: Ensure "Use precise location" is toggled ON. Fused location requires high accuracy GPS fixes to differentiate highway driving from walking.
                • Battery Optimization (Doze Mode): Go to Android Settings > Apps > MileTracker Pro > Battery > Select "Unrestricted" (or "Don't optimize").
                • Notifications: Post notifications must be enabled so Android's persistent Foreground Service notification remains active. This notification informs the Linux kernel that the service is high priority and prevents Out-Of-Memory (OOM) termination.
                • Auto-Start / Background Activity: On devices running MIUI, OneUI, or ColorOS, enable "Auto-start" and "Allow background activity" in your phone's security manager.
            """.trimIndent(),
            tags = listOf("permissions", "battery", "samsung", "doze", "background", "gps")
        ),

        // 3. How Trips Are Detected
        KnowledgeArticle(
            id = "kb_trip_detection",
            section = "How Trips Are Detected",
            title = "Motion Detection State Machine & Zero External Fees",
            summary = "Technical breakdown of speed thresholds, consecutive fast fixes, stationary stop windows, and native hardware telemetry.",
            content = """
                MileTracker Pro uses a multi-stage finite state machine running directly on your phone's hardware, with zero cloud API tracking fees:
                
                1. Idle State: Fused location monitors periodic hardware telemetry.
                2. Candidate Trigger: When speed exceeds your configured Speed Trigger Threshold (default 10 mph, adjustable down to 5 mph), the state machine enters POSSIBLE_MOVEMENT.
                3. Speed Filtering: To eliminate false triggers from running, elevators, or biking, consecutive high-speed fixes (accuracy < 60 meters) are required before declaring TRIP_ACTIVE.
                4. In-Drive Telemetry: While TRIP_ACTIVE, GPS updates measure distance in real-time. Speed drops below 3 mph (stoplights, traffic) transition to POSSIBLE_STOP.
                5. Traffic Light Immunity: If driving resumes above 3 mph before the Inactivity Timeout expires, the possible stop window cancels seamlessly.
                6. Inactivity Timeout: If the vehicle remains stationary longer than your configured timeout (default 3 minutes), the trip is automatically finalized and saved to your local database.
            """.trimIndent(),
            tags = listOf("speed", "threshold", "detection", "motion", "inactivity timeout", "gps")
        ),

        // 4. Trip Types and Rules
        KnowledgeArticle(
            id = "kb_trip_types_rules",
            section = "Trip Types & Rules",
            title = "Custom Trip Types, Default Presets & Rule Precedence",
            summary = "How auto-classification rules categorize trips and how the Default Trip Type handles all unmatched drives.",
            content = """
                Drivers frequently balance multiple platforms (Walmart Spark, Instacart, DoorDash, UberEats, Lyft) alongside Personal, Medical, and Charity drives.
                
                Rule Engine Precedence:
                1. Specific Auto-Classification Rules: Evaluated first. For example, if a drive originates at "Walmart" and ends at an "Unsaved Address", the rule matches and classifies the trip as "Walmart Spark".
                2. Multi-Store Rules: Rules support pipe dividers, e.g. "Albertsons|Vons|Kroger|Safeway" ⇄ "Unsaved Address" = "Instacart".
                3. Personal Trip Rules: "Home" ⇄ "Relatives|Friends|Medical" = "Personal" (non-deductible).
                4. Default Trip Type: If no rule matches, the system applies your selected Default Trip Type (e.g. Walmart Spark).
                5. Unclassified Option: If a trip's type was deleted or left unset, it is shown as "Unclassified" so you can easily review and categorize it.
            """.trimIndent(),
            tags = listOf("spark", "walmart", "instacart", "doordash", "rules", "classification", "default", "trip type")
        ),

        // 5. Round Trips and Stops
        KnowledgeArticle(
            id = "kb_roundtrips_stops",
            section = "Round Trips & Stops",
            title = "Intermediate Customer Stops & Configurable Dropoff Duration",
            summary = "Handling multi-customer dropoff batches, return loops, and configurable minutes per dropoff stop.",
            content = """
                Delivery runs rarely consist of a simple point A to B drive. A typical Walmart Spark or Instacart delivery includes 2 to 12 intermediate stops.
                
                • Adding Stops: In the trip add/edit dialog, tap "+ Add Dropoff Stop" to add intermediate addresses.
                • Configurable Dropoff Duration: Each stop adds your configured Minutes per Dropoff Stop (0–15 minutes, default 4 minutes in Settings > Tracking & Drive Detection, with optional per-trip override). Total Route Duration = driving time + (stops × minutes per stop), and Start Time = End Time − Total Route Duration.
                • Round Trips: If you return to your starting hub or home base at the end of the batch, toggle "Round Trip Return". The mileage calculator doubles the corridor or traces the full loop.
                • Audit-Ready Records: Recording intermediate dropoffs provides contemporaneous verification under IRC § 274(d) showing continuous commercial trade activity.
            """.trimIndent(),
            tags = listOf("stops", "dropoff", "spark batch", "multi-stop", "roundtrip")
        ),

        // 6. Reports and Taxes
        KnowledgeArticle(
            id = "kb_reports_taxes",
            section = "Reports & Taxes",
            title = "IRS Publication 463 & Standard Mileage Deduction",
            summary = "Official guidelines for self-employed 1099 contractors deducting business vehicle mileage vs actual vehicle expenses.",
            content = """
                General tax education based strictly on IRS official guidance:
                
                • IRS Standard Mileage Rate: Self-employed individuals and independent contractors can deduct business miles using the statutory rate ($0.70/mi in 2025/2026, $0.67/mi in 2024). Medical mileage is deductible at $0.22/mi and charitable volunteer miles at $0.14/mi.
                • Standard Rate vs Actual Expenses: The standard mileage rate covers gas, depreciation, lease payments, maintenance, repairs, tires, and insurance. If you take the standard rate in the first year a vehicle is placed in service, you can choose between standard or actual expenses in later years. Parking and tolls are deductible separately in addition to standard mileage!
                • Contemporaneous Log Requirement (IRC § 274(d)): The IRS strictly requires timely documentation recorded at or near the time of the expense. The log must state: (1) Date, (2) Mileage, (3) Destination / Location, and (4) Business Purpose.
                • Beginning and Ending Odometer: IRS Form 2106 and Schedule C require your vehicle's total mileage driven during the tax year (from Jan 1 and Dec 31 odometer readings), broken down into business miles, commuting miles, and other personal miles.
                
                Disclaimer: This app helps keep records. Consult a qualified tax professional or CPA. See official IRS Publication 463 (Travel, Gift, and Car Expenses) at irs.gov.
            """.trimIndent(),
            tags = listOf("irs", "taxes", "deduction", "schedule c", "publication 463", "odometer"),
            officialUrl = "https://www.irs.gov/forms-pubs/about-publication-463"
        ),

        // 7. Troubleshooting Using the Diagnostics Screen
        KnowledgeArticle(
            id = "kb_diagnostics",
            section = "Troubleshooting Using Diagnostics",
            title = "Troubleshooting GPS & Background Tracking",
            summary = "How to inspect real-time satellite telemetry, review state machine logs, and resolve tracking interruptions.",
            content = """
                If drives are not automatically logging or if distance seems inaccurate:
                
                1. Open Settings > Permissions & Tracking Health:
                   - Confirm all permission checks show green checkmarks.
                   - Verify Google Play Location Provider is connected.
                2. Check Current Speed & Accuracy:
                   - GPS fixes with accuracy worse than 60 meters are discarded to prevent wild jumps while sitting at home.
                   - If you are indoors, GPS satellites may be obscured. Drive outdoors to acquire clear sky view.
                3. Inspect Diagnostics Logger:
                   - Under Settings > About App > Diagnostics, tap "View Live Diagnostic Logs".
                   - The log records every StateMachine transition, GPS fix, and background trigger.
                4. Manual Drive Start:
                   - You can always tap the prominent "Start Drive" button on the Dashboard at any time to guarantee an instant recording.
            """.trimIndent(),
            tags = listOf("diagnostics", "troubleshooting", "gps", "logs", "accuracy")
        ),

        // 8. FAQ
        KnowledgeArticle(
            id = "kb_faq",
            section = "FAQ",
            title = "Frequently Asked Questions",
            summary = "Answers to common questions regarding electric vehicles, Google Drive sync, and data privacy.",
            content = """
                Q: Does the app work completely offline?
                A: Yes! All trips, places, vehicles, maintenance reminders, and receipts are stored in your device's local Room SQLite database. You can track drives without an active internet connection.
                
                Q: How do EV maintenance reminders differ from gas cars?
                A: Electric vehicles eliminate motor oil, spark plugs, and timing belts. However, EVs have heavier curb weight and instant torque, making tire rotations every 7,500 miles critical. EVs also require high-voltage battery coolant flushes, cabin filters, and brake fluid moisture testing.
                
                Q: Is my location data tracked or sold?
                A: Never. All GPS coordinates remain strictly on your device or in your personal Google Drive account if you enable backup. There are no third-party tracking SDKs or ad servers.
                
                Q: Can I edit a trip if I forgot to stop it?
                A: Yes. Tap any trip card on the Dashboard, tap "Edit", and adjust start/end addresses, intermediate stops, distance, or purpose.
            """.trimIndent(),
            tags = listOf("faq", "ev", "offline", "privacy", "edit trip")
        )
    )

    fun searchArticles(query: String, selectedSection: String): List<KnowledgeArticle> {
        val filteredBySection = if (selectedSection == "All Sections") {
            ARTICLES
        } else {
            ARTICLES.filter { it.section.equals(selectedSection, ignoreCase = true) }
        }

        if (query.isBlank()) return filteredBySection

        val cleanQuery = query.trim().lowercase()
        return filteredBySection.filter { article ->
            article.title.lowercase().contains(cleanQuery) ||
                    article.summary.lowercase().contains(cleanQuery) ||
                    article.content.lowercase().contains(cleanQuery) ||
                    article.tags.any { it.lowercase().contains(cleanQuery) }
        }
    }
}
