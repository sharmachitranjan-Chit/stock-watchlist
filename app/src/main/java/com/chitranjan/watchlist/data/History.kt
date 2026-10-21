package com.chitranjan.watchlist.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.chitranjan.watchlist.util.istNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** A stock's daily closes, the IST day they were downloaded, and whether they are NSE prices standing in for BSE. */
class TickerHistory(val closes: List<Close>, val day: String, val viaNse: Boolean)

@Serializable
private class Stored(val day: String, val d: List<String>, val p: List<Double>, val nse: Boolean = false)

/**
 * Price history for every stock, kept in memory and on disk. A stock is downloaded at most once a day, one at a time
 * (the engine is a Google Sheet and answers one history request at a time), so opening Insights again is instant.
 */
class HistoryStore(private val dir: File) {
    private val mem = HashMap<String, TickerHistory>()
    private val net = Mutex()

    /** Bumped whenever something new arrives, so screens know to recompute. */
    var version by mutableIntStateOf(0); private set

    @Synchronized fun peek(ticker: String): TickerHistory? = mem[ticker]

    private fun file(ticker: String) = File(dir, ticker.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".json")

    private fun today() = istNow().toLocalDate().toString()

    /** Reads any saved histories for [tickers] into memory. */
    suspend fun preload(tickers: List<String>) = withContext(Dispatchers.IO) {
        var added = false
        for (t in tickers) {
            if (peek(t) != null) continue
            val f = file(t)
            if (!f.exists()) continue
            val s = runCatching { Engine.json.decodeFromString(Stored.serializer(), f.readText()) }.getOrNull() ?: continue
            val closes = s.d.indices.map { Close(s.d[it], s.p[it]) }
            synchronized(this@HistoryStore) { mem[t] = TickerHistory(closes, s.day, s.nse) }
            added = true
        }
        if (added) version++
    }

    fun isFresh(ticker: String): Boolean = peek(ticker)?.day == today()

    /**
     * Returns the history of [ticker], downloading it when the saved copy is from an earlier day.
     * If the download fails but an older copy exists, that copy is returned.
     */
    suspend fun get(ticker: String, engine: EngineConfig, force: Boolean = false): TickerHistory {
        if (!force) peek(ticker)?.let { if (it.day == today()) return it }
        return net.withLock {
            if (!force) peek(ticker)?.let { if (it.day == today()) return@withLock it }
            try {
                var closes = Engine.history(engine, ticker)
                var viaNse = false
                // Google keeps only a point or two of history for many BSE stocks; NSE tracks it within paise.
                if (closes.size < 15 && ticker.startsWith("BOM:")) {
                    val nse = Companies.forTicker(ticker)?.nse.orEmpty()
                    if (nse.isNotEmpty()) {
                        val alt = runCatching { Engine.history(engine, "NSE:$nse") }.getOrDefault(emptyList())
                        if (alt.size > closes.size) { closes = alt; viaNse = true }
                    }
                }
                val h = TickerHistory(closes, today(), viaNse)
                synchronized(this) { mem[ticker] = h }
                withContext(Dispatchers.IO) {
                    runCatching {
                        dir.mkdirs()
                        file(ticker).writeText(Engine.json.encodeToString(Stored.serializer(), Stored(h.day, closes.map { it.date }, closes.map { it.price }, viaNse)))
                    }
                }
                version++
                h
            } catch (e: Exception) {
                peek(ticker) ?: throw e
            }
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        synchronized(this@HistoryStore) { mem.clear() }
        dir.listFiles()?.forEach { it.delete() }
        version++
    }
}
