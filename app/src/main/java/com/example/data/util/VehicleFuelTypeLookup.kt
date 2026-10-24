package com.example.data.util

import java.util.Locale

object VehicleFuelTypeLookup {

    val FUEL_TYPES = listOf(
        "Gas",
        "Hybrid",
        "Plug-in Hybrid",
        "Electric"
    )

    fun normalizeFuelType(raw: String): String {
        val lower = raw.trim().lowercase(Locale.US)
        return when {
            lower.contains("plug-in") || lower.contains("plugin") || lower.contains("phev") || lower.contains("prime") || lower.contains("4xe") -> "Plug-in Hybrid"
            lower.contains("electric") || lower == "ev" || lower.endsWith(" ev") -> "Electric"
            lower.contains("hybrid") || lower.contains("hev") -> "Hybrid"
            else -> "Gas"
        }
    }

    // Specific Full Model to Fuel Type table (case-insensitive keys)
    private val MODEL_LOOKUP: Map<String, String> = mapOf(
        // Tesla (All Electric)
        "model 3" to "Electric",
        "model y" to "Electric",
        "model s" to "Electric",
        "model x" to "Electric",
        "cybertruck" to "Electric",
        "roadster" to "Electric",

        // Chevrolet
        "bolt ev" to "Electric",
        "bolt euv" to "Electric",
        "equinox ev" to "Electric",
        "blazer ev" to "Electric",
        "silverado ev" to "Electric",
        "equinox" to "Gas",
        "blazer" to "Gas",
        "silverado" to "Gas",
        "silverado 1500" to "Gas",
        "malibu" to "Gas",
        "trax" to "Gas",
        "trailblazer" to "Gas",
        "traverse" to "Gas",
        "tahoe" to "Gas",
        "suburban" to "Gas",
        "colorado" to "Gas",
        "corvette" to "Gas",
        "camaro" to "Gas",
        "volt" to "Plug-in Hybrid",

        // Ford
        "mustang mach-e" to "Electric",
        "f-150 lightning" to "Electric",
        "e-transit" to "Electric",
        "maverick" to "Hybrid",
        "maverick hybrid" to "Hybrid",
        "escape hybrid" to "Hybrid",
        "escape plug-in hybrid" to "Plug-in Hybrid",
        "f-150 hybrid" to "Hybrid",
        "f-150 powerboost" to "Hybrid",
        "f-150" to "Gas",
        "escape" to "Gas",
        "explorer" to "Gas",
        "explorer hybrid" to "Hybrid",
        "edge" to "Gas",
        "bronco" to "Gas",
        "bronco sport" to "Gas",
        "ranger" to "Gas",
        "mustang" to "Gas",
        "transit connect" to "Gas",

        // Hyundai
        "ioniq 5" to "Electric",
        "ioniq 6" to "Electric",
        "ioniq 9" to "Electric",
        "kona electric" to "Electric",
        "kona ev" to "Electric",
        "elantra hybrid" to "Hybrid",
        "sonata hybrid" to "Hybrid",
        "tucson hybrid" to "Hybrid",
        "tucson plug-in hybrid" to "Plug-in Hybrid",
        "santa fe hybrid" to "Hybrid",
        "santa fe plug-in hybrid" to "Plug-in Hybrid",
        "elantra" to "Gas",
        "sonata" to "Gas",
        "tucson" to "Gas",
        "santa fe" to "Gas",
        "palisade" to "Gas",
        "venue" to "Gas",
        "kona" to "Gas",

        // Kia
        "ev6" to "Electric",
        "ev9" to "Electric",
        "niro ev" to "Electric",
        "niro" to "Hybrid",
        "niro hybrid" to "Hybrid",
        "niro plug-in hybrid" to "Plug-in Hybrid",
        "sportage hybrid" to "Hybrid",
        "sportage plug-in hybrid" to "Plug-in Hybrid",
        "sorento hybrid" to "Hybrid",
        "sorento plug-in hybrid" to "Plug-in Hybrid",
        "carnival hybrid" to "Hybrid",
        "forte" to "Gas",
        "k5" to "Gas",
        "soul" to "Gas",
        "sportage" to "Gas",
        "sorento" to "Gas",
        "telluride" to "Gas",
        "carnival" to "Gas",

        // Nissan
        "leaf" to "Electric",
        "ariya" to "Electric",
        "altima" to "Gas",
        "sentra" to "Gas",
        "versa" to "Gas",
        "rogue" to "Gas",
        "murano" to "Gas",
        "pathfinder" to "Gas",
        "armada" to "Gas",
        "frontier" to "Gas",
        "kicks" to "Gas",

        // Volkswagen
        "id.4" to "Electric",
        "id.buzz" to "Electric",
        "id.7" to "Electric",
        "e-golf" to "Electric",
        "golf" to "Gas",
        "jetta" to "Gas",
        "tiguan" to "Gas",
        "taos" to "Gas",
        "atlas" to "Gas",
        "atlas cross sport" to "Gas",

        // Rivian
        "r1t" to "Electric",
        "r1s" to "Electric",
        "edv" to "Electric",

        // Lucid
        "air" to "Electric",
        "gravity" to "Electric",

        // Polestar
        "polestar 2" to "Electric",
        "polestar 3" to "Electric",
        "polestar 4" to "Electric",
        "2" to "Electric",
        "3" to "Electric",

        // BMW
        "i4" to "Electric",
        "ix" to "Electric",
        "i7" to "Electric",
        "i5" to "Electric",
        "i3" to "Electric",
        "x5 xdrive45e" to "Plug-in Hybrid",
        "x5 xdrive50e" to "Plug-in Hybrid",
        "330e" to "Plug-in Hybrid",
        "330i" to "Gas",
        "x3" to "Gas",
        "x5" to "Gas",

        // Mercedes-Benz
        "eqs" to "Electric",
        "eqe" to "Electric",
        "eqb" to "Electric",
        "eqa" to "Electric",
        "eqc" to "Electric",
        "eqs suv" to "Electric",
        "eqe suv" to "Electric",

        // Honda
        "prologue" to "Electric",
        "prologue ev" to "Electric",
        "cr-v hybrid" to "Hybrid",
        "accord hybrid" to "Hybrid",
        "civic hybrid" to "Hybrid",
        "clarity plug-in hybrid" to "Plug-in Hybrid",
        "civic" to "Gas",
        "accord" to "Gas",
        "cr-v" to "Gas",
        "hr-v" to "Gas",
        "pilot" to "Gas",
        "passport" to "Gas",
        "odyssey" to "Gas",
        "ridgeline" to "Gas",

        // Toyota
        "bz4x" to "Electric",
        "prius prime" to "Plug-in Hybrid",
        "rav4 prime" to "Plug-in Hybrid",
        "prius" to "Hybrid",
        "camry hybrid" to "Hybrid",
        "corolla hybrid" to "Hybrid",
        "rav4 hybrid" to "Hybrid",
        "highlander hybrid" to "Hybrid",
        "grand highlander hybrid" to "Hybrid",
        "sienna" to "Hybrid",
        "venza" to "Hybrid",
        "crown" to "Hybrid",
        "crown signia" to "Hybrid",
        "camry" to "Gas",
        "corolla" to "Gas",
        "rav4" to "Gas",
        "highlander" to "Gas",
        "4runner" to "Gas",
        "tacoma" to "Gas",
        "tundra" to "Gas",
        "sequoia" to "Hybrid",

        // Subaru
        "solterra" to "Electric",
        "solterra ev" to "Electric",
        "crosstrek hybrid" to "Plug-in Hybrid",
        "outback" to "Gas",
        "forester" to "Gas",
        "crosstrek" to "Gas",
        "ascent" to "Gas",
        "impreza" to "Gas",

        // Mazda
        "mx-30" to "Electric",
        "cx-90 phev" to "Plug-in Hybrid",
        "cx-70 phev" to "Plug-in Hybrid",
        "cx-5" to "Gas",
        "cx-30" to "Gas",
        "cx-50" to "Gas",
        "mazda3" to "Gas",

        // Jeep / Chrysler / Dodge
        "wrangler 4xe" to "Plug-in Hybrid",
        "grand cherokee 4xe" to "Plug-in Hybrid",
        "pacifica hybrid" to "Plug-in Hybrid",
        "hornet r/t" to "Plug-in Hybrid",
        "wrangler" to "Gas",
        "grand cherokee" to "Gas",
        "cherokee" to "Gas",
        "compass" to "Gas",
        "renegade" to "Gas",
        "pacifica" to "Gas",
        "gladiator" to "Gas",

        // Mitsubishi
        "outlander phev" to "Plug-in Hybrid",
        "outlander" to "Gas",

        // Volvo
        "xc40 recharge" to "Electric",
        "c40 recharge" to "Electric",
        "ex30" to "Electric",
        "ex90" to "Electric",
        "xc60 recharge" to "Plug-in Hybrid",
        "xc90 recharge" to "Plug-in Hybrid",

        // Audi / Porsche
        "e-tron" to "Electric",
        "q4 e-tron" to "Electric",
        "q8 e-tron" to "Electric",
        "taycan" to "Electric"
    )

    /**
     * Determines the suggested fuel type given make and model.
     * Checks exact full model first (e.g. "Equinox EV" -> Electric, "Equinox" -> Gas).
     * If not in exact lookup table, falls back to free-text keyword matching.
     */
    fun suggestFuelType(make: String, model: String): String {
        val cleanMake = make.trim().lowercase(Locale.US)
        val cleanModel = model.trim().lowercase(Locale.US)

        if (cleanMake.isBlank() && cleanModel.isBlank()) {
            return "Gas"
        }

        // 1. All-electric makes
        if (cleanMake == "tesla" || cleanMake == "rivian" || cleanMake == "lucid" || cleanMake == "polestar") {
            return "Electric"
        }

        // 2. Exact match on full model name in lookup table (e.g. "Equinox EV" -> Electric, "Equinox" -> Gas)
        MODEL_LOOKUP[cleanModel]?.let { return it }

        // Combined make + model lookup
        val combined = "$cleanMake $cleanModel".trim()
        MODEL_LOOKUP[combined]?.let { return it }

        // Strip make prefix if user typed Make inside Model field (e.g., "Chevrolet Equinox EV")
        if (cleanMake.isNotBlank() && cleanModel.startsWith("$cleanMake ")) {
            val strippedModel = cleanModel.removePrefix("$cleanMake ").trim()
            MODEL_LOOKUP[strippedModel]?.let { return it }
        }

        // Mercedes EQ line: "EQE", "EQS", "EQB", "EQE 350", "EQS 450", etc.
        if ((cleanMake.contains("mercedes") || cleanModel.startsWith("eq")) &&
            (cleanModel.startsWith("eqa") || cleanModel.startsWith("eqb") ||
                    cleanModel.startsWith("eqc") || cleanModel.startsWith("eqe") ||
                    cleanModel.startsWith("eqs") || cleanModel == "eq")
        ) {
            return "Electric"
        }

        // 3. Keyword matching for free-text make / model
        return detectFuelTypeFromText(combined)
    }

    /**
     * Detects fuel type from arbitrary text or VIN description keywords.
     */
    fun detectFuelTypeFromText(text: String): String {
        val t = text.trim().lowercase(Locale.US)
        if (t.isBlank()) return "Gas"

        // Check exact model lookup first if the entire text matches a known model
        MODEL_LOOKUP[t]?.let { return it }

        val tokens = t.split(Regex("[\\s/,-]+"))

        return when {
            // Plug-in Hybrid checks ("Prime", "PHEV", "4xe", "Plug-in", etc.)
            t.contains("prime") || t.contains("phev") || t.contains("4xe") ||
                    t.contains("plug-in") || t.contains("plugin") ||
                    t.contains("xdrive45e") || t.contains("xdrive50e") || t.contains("330e") -> "Plug-in Hybrid"

            // Electric checks ("EV", "Electric", "e-tron", etc.)
            t.contains("equinox ev") || t.contains("blazer ev") || t.contains("silverado ev") ||
                    t.contains("bolt ev") || t.contains("bolt euv") || t.contains("mach-e") ||
                    t.contains("lightning") || t.contains("e-transit") || t.contains("electric") ||
                    t.contains("bev") || t.contains("e-tron") || t.contains("etron") ||
                    t.contains("taycan") || t.contains("cybertruck") || t.contains("bz4x") ||
                    t.contains("solterra") || t.contains("prologue") || t.contains("ioniq") ||
                    t.contains("ev6") || t.contains("ev9") || t.contains("niro ev") ||
                    t.contains("id.4") || t.contains("id.buzz") || t.contains("ariya") ||
                    t.contains("leaf") || t.contains("mx-30") || t.contains("r1t") ||
                    t.contains("r1s") || t.contains("lucid air") || t.contains("polestar") ||
                    tokens.contains("ev") || tokens.contains("i4") || tokens.contains("ix") ||
                    tokens.contains("eqb") || tokens.contains("eqe") || tokens.contains("eqs") -> "Electric"

            // Hybrid checks ("Hybrid", "Prius", "PowerBoost", etc.)
            t.contains("hybrid") || t.contains("prius") || t.contains("powerboost") ||
                    t.contains("e-power") -> "Hybrid"

            else -> "Gas"
        }
    }
}
