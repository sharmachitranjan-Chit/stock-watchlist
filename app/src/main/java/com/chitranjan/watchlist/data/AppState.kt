package com.chitranjan.watchlist.data

import android.app.Application
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

enum class Flash { UP, DOWN }

/** The single source of truth for the UI: prices, lists, settings and actions. */
class AppState(app: Application) : AndroidViewModel(app) {
    private val settings = Settings(app)

    var ready by mutableStateOf(false); private set
    var prefs by mutableStateOf(Prefs()); private set
    var data by mutableStateOf<Snapshot?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var flash by mutableStateOf<Map<String, Flash>>(emptyMap()); private set
    var toast by mutableStateOf<String?>(null); private set

    val engine: EngineConfig
        get() = if (prefs.customUrl.isNotBlank() && prefs.customKey.isNotBlank()) EngineConfig(prefs.customUrl, prefs.customKey) else DEFAULT_ENGINE
    val usingCustomEngine: Boolean get() = prefs.customUrl.isNotBlank() && prefs.customKey.isNotBlank()

    private var poller: Job? = null
    private var toastJob: Job? = null

    init {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Companies.load(app) }
            prefs = settings.load()
            data = settings.loadCache()
            ready = true
            refresh()
        }
    }

    /* ---------- lists ---------- */

    val lists: List<String>
        get() {
            val out = LinkedHashSet<String>()
            data?.rows?.forEach { out.add(it.list) }
            prefs.extraLists.forEach { out.add(it) }
            if (out.isEmpty()) out.add("My list")
            return out.toList()
        }

    val activeList: String get() = lists.let { if (prefs.activeList in it) prefs.activeList else it.first() }

    fun setActiveList(l: String) = updatePrefs { it.copy(activeList = l) }

    fun createList(name: String) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        updatePrefs { it.copy(extraLists = (it.extraLists + n).distinct(), activeList = n) }
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
        viewModelScope.launch { settings.saveCache(s) }
    }

    /* ---------- edits (all go through the Google Sheet) ---------- */

    suspend fun add(list: String, ticker: String) {
        loading = true
        try {
            apply(Engine.add(engine, list, ticker))
            if (list in prefs.extraLists) updatePrefs { it.copy(extraLists = it.extraLists - list) }
        } finally { loading = false }
    }

    suspend fun remove(list: String, ticker: String) = mutate { Engine.remove(engine, list, ticker) }
    suspend fun move(list: String, ticker: String, dir: Int) = mutate { Engine.move(engine, list, ticker, dir) }
    suspend fun deleteList(list: String) {
        updatePrefs { it.copy(extraLists = it.extraLists - list) }
        mutate { Engine.deleteList(engine, list) }
    }

    private suspend fun mutate(block: suspend () -> Snapshot) {
        loading = true
        try { apply(block()); error = null } finally { loading = false }
    }

    suspend fun history(ticker: String): List<Close> = Engine.history(engine, ticker)

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
