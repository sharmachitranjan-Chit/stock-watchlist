package com.chitranjan.watchlist.data

import android.content.Context
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** A listed company, matched across NSE and BSE by ISIN. */
data class Company(val name: String, val short: String, val nse: String, val bse: String, val isin: String) {
    internal val upper = name.uppercase()
    internal val words = upper.split(' ', '.', '&', '(', ')', '-').filter { it.isNotEmpty() }
}

/** Every NSE and BSE equity, from assets/symbols.json (refreshed weekly by a GitHub workflow). */
object Companies {
    @Volatile var all: List<Company> = emptyList(); private set
    private var byNse: Map<String, Company> = emptyMap()
    private var byBse: Map<String, Company> = emptyMap()

    private val POPULAR_NSE = listOf(
        "RELIANCE", "HDFCBANK", "TCS", "INFY", "ICICIBANK", "SBIN", "BANKBARODA", "BHARTIARTL", "ITC", "LT",
        "HINDUNILVR", "KOTAKBANK", "AXISBANK", "BAJFINANCE", "MARUTI", "SUNPHARMA", "TITAN", "TATASTEEL",
        "NTPC", "ONGC", "WIPRO", "HCLTECH", "COALINDIA", "PNB",
    )

    fun load(context: Context) {
        if (all.isNotEmpty()) return
        val text = context.assets.open("symbols.json").bufferedReader().use { it.readText() }
        val rows = Engine.json.parseToJsonElement(text).jsonArray.map { r ->
            val a = r.jsonArray
            val name = a[0].jsonPrimitive.content
            Company(name, shortName(name), a[1].jsonPrimitive.content, a[2].jsonPrimitive.content, a[3].jsonPrimitive.content)
        }
        byNse = rows.filter { it.nse.isNotEmpty() }.associateBy { it.nse }
        byBse = rows.filter { it.bse.isNotEmpty() }.associateBy { it.bse }
        all = rows
    }

    fun shortName(name: String) = name
        .replace(Regex("\\s+(Limited|Ltd\\.?)$", RegexOption.IGNORE_CASE), "")
        .replace(Regex("\\s+"), " ").trim()

    fun forTicker(ticker: String): Company? {
        val code = ticker.substringAfter(':')
        return when (ticker.substringBefore(':')) {
            "NSE" -> byNse[code]
            "BOM" -> byBse[code]
            else -> null
        }
    }

    /** Main label for a row: the NSE symbol when there is one, otherwise the short company name. */
    fun label(ticker: String, fallbackName: String? = null): String {
        val c = forTicker(ticker)
        val code = ticker.substringAfter(':')
        return when {
            c?.nse?.isNotEmpty() == true -> c.nse
            c != null -> c.short.uppercase().take(18)
            ticker.startsWith("NSE:") -> code
            fallbackName != null -> shortName(fallbackName).uppercase().take(18)
            else -> code
        }
    }

    val popular: List<Company> get() = POPULAR_NSE.mapNotNull { byNse[it] }

    fun search(query: String, limit: Int = 40): List<Company> {
        val q = query.trim().uppercase()
        if (q.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Company, Int>>()
        for (c in all) {
            var s = when {
                c.nse == q -> 100
                c.bse == q -> 95
                c.nse.startsWith(q) -> 80 - minOf(c.nse.length - q.length, 10)
                q.length >= 3 && c.bse.startsWith(q) -> 70
                c.upper.startsWith(q) -> 65
                c.words.any { it.startsWith(q) } -> 55
                q.length >= 3 && c.upper.contains(q) -> 35
                else -> 0
            }
            if (s > 0) {
                // Well-known companies trade on both exchanges; lift them above small BSE-only names.
                if (c.nse.isNotEmpty() && c.bse.isNotEmpty()) s += 12 else if (c.nse.isNotEmpty()) s += 5
                scored.add(c to s)
            }
        }
        return scored.sortedWith(compareByDescending<Pair<Company, Int>> { it.second }.thenBy { it.first.name.length })
            .take(limit).map { it.first }
    }
}
