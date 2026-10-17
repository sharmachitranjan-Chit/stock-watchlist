package com.chitranjan.watchlist.data

import android.content.Context
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Which sector a company belongs to. The official NSE industry comes from assets/sectors.json (keyed by ISIN,
 * covers about 750 companies). Anything not in that file is guessed from words in its name.
 */
object Sectors {
    private var names: List<String> = emptyList()
    private var byIsin: Map<String, Int> = emptyMap()
    private val cache = HashMap<String, String>()

    fun load(context: Context) {
        if (byIsin.isNotEmpty()) return
        val text = context.assets.open("sectors.json").bufferedReader().use { it.readText() }
        val root = Engine.json.parseToJsonElement(text).jsonObject
        names = root.getValue("s").jsonArray.map { it.jsonPrimitive.content }
        byIsin = root.getValue("m").jsonObject.mapValues { it.value.jsonPrimitive.int }
    }

    @Synchronized
    fun of(ticker: String, fallbackName: String?): String = cache.getOrPut(ticker) {
        val c = Companies.forTicker(ticker)
        val raw = c?.isin?.let { byIsin[it] }?.let { names.getOrNull(it) }
        refine(raw, c?.name ?: fallbackName.orEmpty())
    }

    /** Shorter names for the screen. */
    fun short(sector: String): String = SHORT[sector] ?: sector

    private val SHORT = mapOf(
        "Automobile and Auto Components" to "Auto",
        "Fast Moving Consumer Goods" to "FMCG",
        "Oil Gas & Consumable Fuels" to "Oil & Gas",
        "Media Entertainment & Publication" to "Media",
        "Information Technology" to "IT",
        "Telecommunication" to "Telecom",
        "Metals & Mining" to "Metals & Mining",
    )

    private val KEYWORDS: List<Pair<Regex, String>> = listOf(
        "\\b(ETF|BEES|MSCI|FUND|INDEX|GILT)\\b" to "ETFs & Funds",
        "BANK" to "Banks",
        "INSURANCE|ASSURANCE" to "Insurance",
        "PETROL|REFINER|LUBRIC|\\bOIL\\b|\\bGAS\\b|PETRO" to "Oil Gas & Consumable Fuels",
        "PHARMA|LABORATOR|HOSPITAL|HEALTH|DRUG|DIAGNOS|LIFESCIENCE|BIOTECH|MEDICA" to "Healthcare",
        "CEMENT" to "Construction Materials",
        "STEEL|ALUMIN|COPPER|ZINC|MINING|\\bIRON\\b|FERRO|METAL|ALLOY" to "Metals & Mining",
        "MOTOR|AUTOMOT|TYRE|VEHICLE|\\bAUTO\\b" to "Automobile and Auto Components",
        "SOFTWARE|INFOTECH|TECHNOLOG|COMPUTER|DIGITAL|INFOSYS" to "Information Technology",
        "POWER|ELECTRIC|ENERGY|SOLAR|RENEWABLE" to "Power",
        "REALTY|ESTATE|PROPERT|HOUSING|DEVELOPERS" to "Realty",
        "TEXTILE|SPINNING|COTTON|FABRIC|YARN|GARMENT|APPAREL" to "Textiles",
        "CHEMICAL|FERTILI|PESTICIDE|AGRO|PAINT|POLYMER|PLASTIC" to "Chemicals",
        "FOOD|SUGAR|\\bTEA\\b|DAIRY|BEVERAGE|BREW|DISTILL|TOBACCO|SPICES|RICE" to "Fast Moving Consumer Goods",
        "TELECOM|COMMUNICATION|BROADBAND" to "Telecommunication",
        "MEDIA|ENTERTAIN|FILM|BROADCAST|PUBLISH" to "Media Entertainment & Publication",
        "INFRA|CONSTRUCT|BUILDER|PROJECTS" to "Construction",
        "FINANC|CAPITAL|CREDIT|INVEST|SECURITIES|LEASING|FINCORP|MICROFIN|HOLDING" to "Financial Services",
        "ENGINEER|INDUSTR|FORGE|CASTING|PUMP|BEARING|ELECTRICALS|TUBES|CABLE" to "Capital Goods",
        "RETAIL|TRADING|HOTEL|RESORT|TRAVEL|EDUCATION|LOGISTIC|SHIPPING|AIRLINE" to "Services",
    ).map { (re, sector) -> Regex(re) to sector }

    /** Splits the broad "Financial Services" bucket into Banks and Insurance, and guesses when NSE has no entry. */
    fun refine(raw: String?, companyName: String): String {
        val up = companyName.uppercase()
        if (raw == "Financial Services") {
            return when {
                "BANK" in up -> "Banks"
                "INSURANCE" in up || "ASSURANCE" in up || "LIFE" in up && "INS" in up -> "Insurance"
                else -> raw
            }
        }
        if (raw != null) return raw
        return KEYWORDS.firstOrNull { it.first.containsMatchIn(up) }?.second ?: "Other"
    }
}
