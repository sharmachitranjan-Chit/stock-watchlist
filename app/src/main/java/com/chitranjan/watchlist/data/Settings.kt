package com.chitranjan.watchlist.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.store by preferencesDataStore("watchlist")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class Palette(val label: String) { YOU("Your wallpaper"), OCEAN("Ocean"), EMERALD("Emerald"), SAFFRON("Saffron"), VIOLET("Violet"), GRAPHITE("Graphite") }
enum class GainLoss { CLASSIC, COLORBLIND }
enum class SortMode(val label: String) { MY("My order"), GAIN("Top gainers"), LOSS("Top losers"), NAME("A–Z") }
enum class ViewMode(val label: String) { ALL("All"), NSE("NSE"), BSE("BSE"), COMPARE("Side by side") }

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
)

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
