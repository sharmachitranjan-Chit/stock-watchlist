package com.chitranjan.watchlist.data

import android.app.Application
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.chitranjan.watchlist.util.MarketKind
import com.chitranjan.watchlist.util.market
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

enum class Flash { UP, DOWN }

/** Progress of downloading price history for the stocks being analysed. */
data class HistoryLoad(val total: Int = 0, val done: Int = 0, val failed: Set<String> = emptySet(), val running: Boolean = false)

/** Names are trimmed, kept to 30 characters and stripped of line breaks. */
fun cleanListName(raw: String): String = raw.filter { it >= ' ' }.trim().take(30)

/** The single source of truth for the UI: prices, lists, settings and actions. */
class AppState(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)
    val history = HistoryStore(File(app.filesDir, "history"))

    var ready by mutableStateOf(false); private set
    var prefs by mutableStateOf(Prefs()); private set
    var data by mutableStateOf<Snapshot?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var flash by mutableStateOf<Map<String, Flash>>(emptyMap()); private set
    var toast by mutableStateOf<String?>(null); private set
    var historyLoad by mutableStateOf(HistoryLoad()); private set

    val engine: EngineConfig
        get() = if (prefs.customUrl.isNotBlank() && prefs.customKey.isNotBlank()) EngineConfig(prefs.customUrl, prefs.customKey) else DEFAULT_ENGINE
    val usingCustomEngine: Boolean get() = prefs.customUrl.isNotBlank() && prefs.customKey.isNotBlank()

    private var poller: Job? = null
    private var toastJob: Job? = null
    private var historyJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Companies.load(app); Sectors.load(app) }
            prefs = settings.load()
            data = settings.loadCache()
            ready = true
            refresh()
        }
    }

    /* ---------- lists ---------- */

    /** Every list name: those with stocks in the sheet, then empty ones created on this phone. Cached until either changes. */
    val lists: List<String> by derivedStateOf {
        val out = LinkedHashSet<String>()
        data?.rows?.forEach { out.add(it.list) }
        prefs.extraLists.forEach { out.add(it) }
        if (out.isEmpty()) out.add("My list")
        out.toList()
    }

    val activeList: String get() = lists.let { if (prefs.activeList in it) prefs.activeList else it.first() }

    fun setActiveList(l: String) = updatePrefs { it.copy(activeList = l) }

    /** Returns an error message, or null when the list was created. */
    fun createList(name: String): String? {
        val n = cleanListName(name)
        if (n.isEmpty()) return "Enter a name"
        if (lists.any { it.equals(n, ignoreCase = true) }) return "A list called “$n” already exists"
        updatePrefs { it.copy(extraLists = (it.extraLists + n).distinct(), activeList = n) }
        return null
    }

    /**
     * Renames a list in the sheet (and on this phone). The updated price engine does it in one step; with an older
     * engine the stocks are copied to the new name one by one and the old list is removed at the end.
     */
    suspend fun renameList(from: String, to: String) {
        val n = cleanListName(to)
        if (n.isEmpty()) throw IllegalArgumentException("Enter a name")
        if (n == from) return
        if (lists.any { it.equals(n, ignoreCase = true) && !it.equals(from, ignoreCase = true) }) {
            throw IllegalArgumentException("A list called “$n” already exists")
        }
        loading = true
        try {
            if (data?.rows.orEmpty().any { it.list == from }) {
                val snap = try { Engine.renameList(engine, from, n) }
                catch (e: EngineException) {
                    if (e.message.orEmpty().startsWith("Unknown action")) copyList(from, n) else throw e
                }
                apply(snap)
            }
            updatePrefs { p ->
                p.copy(
                    extraLists = p.extraLists.map { if (it == from) n else it }.distinct(),
                    activeList = if (p.activeList == from) n else p.activeList,
                    holdings = p.holdings.mapKeys { (k, _) -> if (k.startsWith("$from|")) "$n|" + k.removePrefix("$from|") else k },
                )
            }
            error = null
        } finally { loading = false }
    }

    private suspend fun copyList(from: String, to: String): Snapshot {
        val tickers = data?.rows.orEmpty().filter { it.list == from }.map { it.ticker }
        try {
            for (t in tickers) Engine.add(engine, to, t)
            return Engine.deleteList(engine, from)
        } catch (e: Exception) {
            runCatching { Engine.deleteList(engine, to) }   // leave the original list as it was
            throw e
        }
    }

    /** Sets or clears (null) how much of a stock you hold in a list. */
    fun setHolding(list: String, ticker: String, h: Holding?) = updatePrefs {
        val key = "$list|$ticker"
        it.copy(holdings = if (h == null) it.holdings - key else it.holdings + (key to h))
    }

    fun updatePrefs(f: (Prefs) -> Prefs) {
        prefs = f(prefs)
        viewModelScope.launch { settings.save(prefs) }
    }

    /* ---------- prices ---------- */

    fun refresh() {
        if (loading) return
        viewModelScope.launch {
            loading = true
            try { apply(Engine.data(engine)); error = null }
            catch (e: Exception) { error = e.message ?: "Could not load prices" }
            finally { loading = false }
        }
    }

    /** Refresh every few seconds while the app is on screen; slower when the market is closed. */
    fun startPolling() {
        poller?.cancel()
        poller = viewModelScope.launch {
            if (ready) refresh()
            while (isActive) {
                val open = market(data).kind != MarketKind.CLOSED
                if (open && prefs.intervalSec == 0) { delay(30_000L); continue }   // "manual only"
                delay(if (open) prefs.intervalSec * 1000L else 10 * 60_000L)
                refresh()
            }
        }
    }

    fun stopPolling() { poller?.cancel(); poller = null }

    private fun apply(s: Snapshot) {
        val old = data
        if (old != null) {
            val prev = (old.rows + old.indices).associate { it.key to it.price }
            val f = HashMap<String, Flash>()
            (s.rows + s.indices).forEach { q ->
                val p = prev[q.key]
                if (p != null && q.price != null && p != q.price) f[q.key] = if (q.price > p) Flash.UP else Flash.DOWN
            }
            if (f.isNotEmpty()) {
                flash = f
                viewModelScope.launch { delay(1400); flash = emptyMap() }
            }
        }
        data = s
        viewModelScope.launch(Dispatchers.Default) { settings.saveCache(s) }
    }

    /* ---------- edits (all go through the Google Sheet) ---------- */

    suspend fun add(list: String, ticker: String) {
        loading = true
        try {
            apply(Engine.add(engine, list, ticker))
            if (list in prefs.extraLists) updatePrefs { it.copy(extraLists = it.extraLists - list) }
        } finally { loading = false }
    }

    suspend fun remove(list: String, ticker: String) {
        mutate { Engine.remove(engine, list, ticker) }
        setHolding(list, ticker, null)
    }
    suspend fun move(list: String, ticker: String, dir: Int) = mutate { Engine.move(engine, list, ticker, dir) }
    suspend fun deleteList(list: String) {
        updatePrefs { it.copy(extraLists = it.extraLists - list, holdings = it.holdings.filterKeys { k -> !k.startsWith("$list|") }) }
        mutate { Engine.deleteList(engine, list) }
    }

    private suspend fun mutate(block: suspend () -> Snapshot) {
        loading = true
        try { apply(block()); error = null } finally { loading = false }
    }

    /** Daily closes for one stock, from the saved copy when it is from today. */
    suspend fun historyOf(ticker: String): TickerHistory = history.get(ticker, engine)

    /** Downloads (once a day, one stock at a time) the history of every ticker, so Insights can chart them together. */
    fun loadHistory(tickers: List<String>, force: Boolean = false) {
        historyJob?.cancel()
        val wanted = tickers.distinct()
        historyJob = viewModelScope.launch {
            history.preload(wanted)
            val todo = wanted.filter { force || !history.isFresh(it) }
            val failed = LinkedHashSet<String>()
            var done = 0
            historyLoad = HistoryLoad(todo.size, 0, emptySet(), todo.isNotEmpty())
            for (t in todo) {
                try { history.get(t, engine, force) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { failed.add(t) }
                done++
                historyLoad = HistoryLoad(todo.size, done, failed.toSet(), done < todo.size)
            }
        }
    }

    fun clearHistory() {
        historyJob?.cancel()
        viewModelScope.launch { history.clear(); historyLoad = HistoryLoad() }
    }

    /** Tests a link and key, then saves them. Passing blanks returns to the built-in engine. */
    suspend fun saveEngine(url: String, key: String): Snapshot? {
        if (url.isBlank() && key.isBlank()) {
            updatePrefs { it.copy(customUrl = "", customKey = "") }
            refresh()
            return null
        }
        val s = Engine.data(EngineConfig(url.trim(), key.trim()))
        updatePrefs { it.copy(customUrl = url.trim(), customKey = key.trim()) }
        apply(s)
        return s
    }

    fun showToast(m: String) {
        toast = m
        toastJob?.cancel()
        toastJob = viewModelScope.launch { delay(2600); toast = null }
    }
}
