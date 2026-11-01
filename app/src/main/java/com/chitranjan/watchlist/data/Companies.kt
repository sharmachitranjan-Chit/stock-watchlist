package com.chitranjan.watchlist.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
    @Volatile private var byNse: Map<String, Company> = emptyMap()
    @Volatile private var byBse: Map<String, Company> = emptyMap()

    /** Goes up whenever the list changes after start-up (new listings found), so Search can re-run. */
    var version by mutableIntStateOf(0); private set
    /** How many companies came from the live NSE check rather than the bundled list. */
    @Volatile var liveCount: Int = 0; private set

    private var base: List<Company> = emptyList()

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
        base = rows
        install(applyLive(rows, readLive(context)))
    }

    private fun install(rows: List<Company>) {
        byNse = rows.filter { it.nse.isNotEmpty() }.associateBy { it.nse }
        byBse = rows.filter { it.bse.isNotEmpty() }.associateBy { it.bse }
        all = rows
    }

    /* ---------- live check for new listings ---------- */

    /** A listing found on NSE: [name, symbol, isin]. */
    internal class Live(val name: String, val sym: String, val isin: String)

    private fun liveFile(context: Context) = File(context.filesDir, "nse-live.csv")

    private fun readLive(context: Context): List<Live> = runCatching {
        val f = liveFile(context)
        if (f.exists()) parseNse(f.readText()) else emptyList()
    }.getOrDefault(emptyList())

    /** Adds NSE listings the bundled list lacks, and gives BSE-only companies their new NSE symbol. */
    internal fun applyLive(rows: List<Company>, live: List<Live>): List<Company> {
        if (live.isEmpty()) { liveCount = 0; return rows }
        val byIsin = HashMap<String, Int>()
        rows.forEachIndexed { i, c -> if (c.isin.isNotEmpty()) byIsin[c.isin] = i }
        val out = rows.toMutableList()
        val known = rows.mapTo(HashSet()) { it.nse }
        var added = 0
        for (l in live) {
            val i = byIsin[l.isin]
            if (i != null) {
                val c = out[i]
                if (c.nse.isEmpty() && l.sym !in known) { out[i] = Company(c.name, c.short, l.sym, c.bse, c.isin); added++ }
            } else if (l.sym !in known) {
                val name = l.name
                out.add(Company(name, shortName(name), l.sym, "", l.isin)); known.add(l.sym); added++
            }
        }
        liveCount = added
        return out
    }

    private val SERIES = setOf("EQ", "BE", "BZ", "SM", "ST")

    internal fun parseNse(csv: String): List<Live> {
        val lines = csv.replace("\r", "").split('\n').filter { it.isNotBlank() }
        if (lines.size < 100) return emptyList()
        val head = lines[0].split(',').map { it.trim().uppercase() }
        val iSym = head.indexOf("SYMBOL"); val iName = head.indexOf("NAME OF COMPANY")
        val iSeries = head.indexOf("SERIES"); val iIsin = head.indexOf("ISIN NUMBER")
        if (iSym < 0 || iName < 0 || iIsin < 0) return emptyList()
        return lines.drop(1).mapNotNull { l ->
            val c = l.split(',').map { it.trim() }
            val sym = c.getOrNull(iSym).orEmpty(); val isin = c.getOrNull(iIsin).orEmpty()
            val series = if (iSeries >= 0) c.getOrNull(iSeries).orEmpty() else "EQ"
            if (sym.isEmpty() || isin.isEmpty() || series !in SERIES) null
            else Live(c.getOrNull(iName).orEmpty().replace(Regex("\\s+"), " "), sym, isin)
        }
    }

    /**
     * Downloads NSE's list of listed companies (public file, a few hundred KB) and adds anything new, such as a
     * company listed this week. Done at most every 6 hours unless [force]. Returns how many new companies were
     * found, or null if the check could not be made; failures keep the list as it was.
     */
    fun refreshLive(context: Context, force: Boolean = false): Int? {
        val f = liveFile(context)
        if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600_000L) return liveCount
        return try {
            val conn = (URL("https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv").openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000; readTimeout = 25_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36")
                setRequestProperty("Referer", "https://www.nseindia.com/")
            }
            val text = try { if (conn.responseCode != 200) return null; conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
            val live = parseNse(text)
            if (live.size < 1000) return null
            f.writeText(text)
            val before = all.size
            install(applyLive(base, live))
            version++
            if (force) all.size - before else liveCount
        } catch (e: Exception) { null }
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
