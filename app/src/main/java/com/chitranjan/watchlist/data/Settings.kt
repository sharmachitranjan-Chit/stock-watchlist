package com.chitranjan.watchlist.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

private val Context.store by preferencesDataStore("watchlist")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Palette(val label: String) { YOU("Your wallpaper"), OCEAN("Ocean"), EMERALD("Emerald"), SAFFRON("Saffron"), VIOLET("Violet"), GRAPHITE("Graphite") }
enum class GainLoss { CLASSIC, COLORBLIND }
enum class SortMode(val label: String) { MY("My order"), GAIN("Top gainers"), LOSS("Top losers"), NAME("A–Z") }
enum class ViewMode(val label: String) { ALL("All"), NSE("NSE"), BSE("BSE"), COMPARE("Side by side") }
enum class TextSize(val label: String, val scale: Float) { SMALL("Small", 0.92f), NORMAL("Default", 1f), LARGE("Large", 1.12f), HUGE("Largest", 1.25f) }
enum class Density(val label: String) { COMFORTABLE("Comfortable"), COMPACT("Compact") }
enum class StartTab(val label: String) { WATCHLIST("Watchlist"), INSIGHTS("Insights"), MARKETS("Markets") }
enum class Period(val label: String, val days: Long) { W1("1W", 7), M1("1M", 30), M3("3M", 91), M6("6M", 182), Y1("1Y", 365) }

data class Prefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val palette: Palette = Palette.OCEAN,
    val gainLoss: GainLoss = GainLoss.CLASSIC,
    val intervalSec: Int = 60,
    val sort: SortMode = SortMode.MY,
    val view: ViewMode = ViewMode.ALL,
    val activeList: String = "My list",
    val extraLists: List<String> = emptyList(),
    val customUrl: String = "",
    val customKey: String = "",
    val pureBlack: Boolean = false,
    val textSize: TextSize = TextSize.NORMAL,
    val density: Density = Density.COMFORTABLE,
    val hideAmounts: Boolean = false,
    val startTab: StartTab = StartTab.WATCHLIST,
    val period: Period = Period.M1,
    /** Quantity and average price per stock, keyed by "list|ticker" (see [Quote.key]). */
    val holdings: Map<String, Holding> = emptyMap(),
)

private val HOLDINGS = MapSerializer(String.serializer(), Holding.serializer())

/** Everything the app remembers on the phone. Watchlists themselves live in the Google Sheet. */
class Settings(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val palette = stringPreferencesKey("palette")
        val gainLoss = stringPreferencesKey("gain_loss")
        val interval = intPreferencesKey("interval")
        val sort = stringPreferencesKey("sort")
        val view = stringPreferencesKey("view")
        val active = stringPreferencesKey("active_list")
        val extra = stringPreferencesKey("extra_lists")
        val url = stringPreferencesKey("engine_url")
        val key = stringPreferencesKey("engine_key")
        val cache = stringPreferencesKey("snapshot_cache")
        val pureBlack = stringPreferencesKey("pure_black")
        val textSize = stringPreferencesKey("text_size")
        val density = stringPreferencesKey("density")
        val hide = stringPreferencesKey("hide_amounts")
        val startTab = stringPreferencesKey("start_tab")
        val period = stringPreferencesKey("period")
        val holdings = stringPreferencesKey("holdings")
    }

    private inline fun <reified T : Enum<T>> Preferences.enum(k: Preferences.Key<String>, d: T): T =
        this[k]?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: d

    suspend fun load(): Prefs {
        val p = context.store.data.first()
        return Prefs(
            theme = p.enum(K.theme, ThemeMode.SYSTEM),
            palette = p.enum(K.palette, Palette.OCEAN),
            gainLoss = p.enum(K.gainLoss, GainLoss.CLASSIC),
            intervalSec = p[K.interval] ?: 60,
            sort = p.enum(K.sort, SortMode.MY),
            view = p.enum(K.view, ViewMode.ALL),
            activeList = p[K.active] ?: "My list",
            extraLists = p[K.extra]?.split('\n')?.filter { it.isNotBlank() } ?: emptyList(),
            customUrl = p[K.url] ?: "",
            customKey = p[K.key] ?: "",
            pureBlack = p[K.pureBlack] == "1",
            textSize = p.enum(K.textSize, TextSize.NORMAL),
            density = p.enum(K.density, Density.COMFORTABLE),
            hideAmounts = p[K.hide] == "1",
            startTab = p.enum(K.startTab, StartTab.WATCHLIST),
            period = p.enum(K.period, Period.M1),
            holdings = p[K.holdings]?.let { runCatching { Engine.json.decodeFromString(HOLDINGS, it) }.getOrNull() } ?: emptyMap(),
        )
    }

    suspend fun save(x: Prefs) {
        context.store.edit {
            it[K.theme] = x.theme.name
            it[K.palette] = x.palette.name
            it[K.gainLoss] = x.gainLoss.name
            it[K.interval] = x.intervalSec
            it[K.sort] = x.sort.name
            it[K.view] = x.view.name
            it[K.active] = x.activeList
            it[K.extra] = x.extraLists.joinToString("\n")
            it[K.url] = x.customUrl
            it[K.key] = x.customKey
            it[K.pureBlack] = if (x.pureBlack) "1" else "0"
            it[K.textSize] = x.textSize.name
            it[K.density] = x.density.name
            it[K.hide] = if (x.hideAmounts) "1" else "0"
            it[K.startTab] = x.startTab.name
            it[K.period] = x.period.name
            it[K.holdings] = Engine.json.encodeToString(HOLDINGS, x.holdings)
        }
    }

    suspend fun loadCache(): Snapshot? = try {
        context.store.data.first()[K.cache]?.let { Engine.json.decodeFromString(Snapshot.serializer(), it) }
    } catch (e: Exception) { null }

    suspend fun saveCache(s: Snapshot) {
        context.store.edit { it[K.cache] = Engine.json.encodeToString(Snapshot.serializer(), s) }
    }
}

/** Built-in engine: the "Stock Watchlist" sheet's Apps Script. Keep the repository private. */
val DEFAULT_ENGINE = EngineConfig(
    url = "https://script.google.com/macros/s/AKfycbwtSiaDgdsQyJHgC6ZAXv66XGfCFjEICVFBfsz12LIpc25ee_F4Na8EEC6QOLEHobKB/exec",
    key = "RS5NYfswFosn7rseUuG_x5WqIdbvPeQ0",
)
