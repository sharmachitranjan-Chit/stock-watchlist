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

    /* ---------- live check for new listings (NSE and BSE) ---------- */

    /** A listing seen live on an exchange. [nse] or [bse] is empty when that exchange did not report it. */
    internal class Live(val name: String, val nse: String, val bse: String, val isin: String)

    private fun nseFile(context: Context) = File(context.filesDir, "nse-live.csv")
    private fun bseFile(context: Context) = File(context.filesDir, "bse-live.csv")

    private fun readLive(context: Context): List<Live> = runCatching {
        val n = nseFile(context).takeIf { it.exists() }?.let { parseNse(it.readText()) }.orEmpty()
        val b = bseFile(context).takeIf { it.exists() }?.let { parseBse(it.readText()) }.orEmpty()
        n + b
    }.getOrDefault(emptyList())

    /**
     * Merges live listings into the bundled list by ISIN: a company the bundled list lacks is added, and a company
     * known on one exchange gets its code on the other (so a new IPO shows both its NSE and BSE buttons).
     */
    internal fun applyLive(rows: List<Company>, live: List<Live>): List<Company> {
        if (live.isEmpty()) { liveCount = 0; return rows }
        val out = rows.toMutableList()
        val byIsin = HashMap<String, Int>()
        out.forEachIndexed { i, c -> if (c.isin.isNotEmpty()) byIsin[c.isin] = i }
        val nseTaken = out.mapTo(HashSet()) { it.nse }.apply { remove("") }
        val bseTaken = out.mapTo(HashSet()) { it.bse }.apply { remove("") }
        var changed = 0
        for (l in live) {
            val nse = l.nse.takeIf { it.isNotEmpty() && it !in nseTaken }.orEmpty()
            val bse = l.bse.takeIf { it.isNotEmpty() && it !in bseTaken }.orEmpty()
            if (nse.isEmpty() && bse.isEmpty()) continue
            val i = byIsin[l.isin]
            if (i != null) {
                val c = out[i]
                val newNse = c.nse.ifEmpty { nse }; val newBse = c.bse.ifEmpty { bse }
                if (newNse == c.nse && newBse == c.bse) continue
                out[i] = Company(c.name, c.short, newNse, newBse, c.isin)
            } else {
                out.add(Company(l.name, shortName(l.name), nse, bse, l.isin))
                byIsin[l.isin] = out.size - 1
            }
            if (nse.isNotEmpty()) nseTaken.add(nse)
            if (bse.isNotEmpty()) bseTaken.add(bse)
            changed++
        }
        liveCount = changed
        return out
    }

    private val SERIES = setOf("EQ", "BE", "BZ", "SM", "ST")

    /** NSE's EQUITY_L.csv: every listed company with its symbol and ISIN. */
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
            else Live(c.getOrNull(iName).orEmpty().replace(Regex("\\s+"), " "), sym, "", isin)
        }
    }

    /** BSE's daily equity bhavcopy: every share that traded that day, with its scrip code and ISIN. */
    internal fun parseBse(csv: String): List<Live> {
        val lines = csv.replace("\r", "").split('\n').filter { it.isNotBlank() }
        if (lines.size < 100) return emptyList()
        val head = lines[0].split(',').map { it.trim().uppercase() }
        val iCode = head.indexOf("FININSTRMID"); val iIsin = head.indexOf("ISIN")
        val iName = head.indexOf("FININSTRMNM"); val iType = head.indexOf("FININSTRMTP")
        if (iCode < 0 || iIsin < 0 || iName < 0) return emptyList()
        return lines.drop(1).mapNotNull { l ->
            val c = l.split(',').map { it.trim() }
            val code = c.getOrNull(iCode).orEmpty(); val isin = c.getOrNull(iIsin).orEmpty()
            val type = if (iType >= 0) c.getOrNull(iType).orEmpty() else "STK"
            if (code.length != 6 || !code.all { it.isDigit() } || !isin.startsWith("IN") || type != "STK") null
            else Live(titleCase(c.getOrNull(iName).orEmpty()), "", code, isin)
        }
    }

    /** "JIO PLATFORMS LIMITED" → "Jio Platforms Limited". */
    private fun titleCase(s: String) = s.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36"

    /** GET [url]; returns the body on HTTP 200, otherwise null. */
    private fun download(url: String, referer: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Referer", referer)
        }
        return try { if (conn.responseCode == 200) conn.inputStream.bufferedReader().use { it.readText() } else null }
        finally { conn.disconnect() }
    }

    private fun fresh(f: File) = f.exists() && System.currentTimeMillis() - f.lastModified() < 6 * 3600_000L

    /**
     * Checks both exchanges for companies the bundled list lacks: NSE's list of listed companies, and BSE's latest
     * daily bhavcopy (published each trading evening; the newest of the last week is used). Each is downloaded at
     * most every 6 hours unless [force]. Returns how many companies were added or completed, or null when neither
     * exchange could be reached; a failure keeps the list as it was.
     */
    fun refreshLive(context: Context, force: Boolean = false): Int? {
        val nf = nseFile(context); val bf = bseFile(context)
        if (!force && fresh(nf) && fresh(bf)) return liveCount
        var reached = false
        if (force || !fresh(nf)) runCatching {
            download("https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv", "https://www.nseindia.com/")
                ?.takeIf { parseNse(it).size >= 1000 }?.let { nf.writeText(it); reached = true }
        }
        if (force || !fresh(bf)) runCatching {
            var day = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"))
            for (k in 0 until 8) {
                if (day.dayOfWeek.value <= 5) {
                    val d = day.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                    val text = runCatching { download("https://www.bseindia.com/download/BhavCopy/Equity/BhavCopy_BSE_CM_0_0_0_${d}_F_0000.CSV", "https://www.bseindia.com/") }.getOrNull()
                    if (text != null && parseBse(text).size >= 1000) { bf.writeText(text); reached = true; break }
                }
                day = day.minusDays(1)
            }
        }
        if (!reached && !(fresh(nf) || fresh(bf))) return null
        install(applyLive(base, readLive(context)))
        version++
        return liveCount
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
