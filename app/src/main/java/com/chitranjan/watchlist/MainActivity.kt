package com.chitranjan.watchlist

import android.graphics.Color as AColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Quote
import com.chitranjan.watchlist.data.ThemeMode
import com.chitranjan.watchlist.ui.ConnectionScreen
import com.chitranjan.watchlist.ui.LocalExtra
import com.chitranjan.watchlist.ui.InsightsIcon
import com.chitranjan.watchlist.ui.InsightsScreen
import com.chitranjan.watchlist.ui.MarketsIcon
import com.chitranjan.watchlist.ui.MarketsScreen
import com.chitranjan.watchlist.ui.SearchScreen
import com.chitranjan.watchlist.ui.SettingsScreen
import com.chitranjan.watchlist.ui.Shape12
import com.chitranjan.watchlist.ui.StockScreen
import com.chitranjan.watchlist.ui.WatchlistScreen
import com.chitranjan.watchlist.ui.WatchlistTheme

/** Version shown in Settings, read from the installed package. */
object BuildConfigInfo { var version: String = "" }

private enum class Tab(val label: String) { WATCHLIST("Watchlist"), INSIGHTS("Insights"), MARKETS("Markets"), SEARCH("Search"), SETTINGS("Settings") }

/** Screens that open on top of the tabs. */
private sealed interface Overlay {
    data class Stock(val ticker: String, val isIndex: Boolean) : Overlay
    data object Connection : Overlay
}

class MainActivity : ComponentActivity() {
    private val state: AppState by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BuildConfigInfo.version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull().orEmpty()
        enableEdgeToEdge()
        setContent {
            val p = state.prefs
            val dark = when (p.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(AColor.TRANSPARENT) else SystemBarStyle.light(AColor.TRANSPARENT, AColor.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            WatchlistTheme(p) { App(state) }
        }
    }

    // Prices refresh only while the app is on screen.
    override fun onStart() { super.onStart(); state.startPolling() }
    override fun onStop() { super.onStop(); state.stopPolling() }
}

private fun startTab(s: com.chitranjan.watchlist.data.StartTab) = when (s) {
    com.chitranjan.watchlist.data.StartTab.WATCHLIST -> Tab.WATCHLIST
    com.chitranjan.watchlist.data.StartTab.INSIGHTS -> Tab.INSIGHTS
    com.chitranjan.watchlist.data.StartTab.MARKETS -> Tab.MARKETS
}

@Composable
private fun App(state: AppState) {
    var tab by rememberSaveable { mutableStateOf(if (state.ready) startTab(state.prefs.startTab) else Tab.WATCHLIST) }
    // The saved start screen is known once settings have loaded; apply it a single time.
    var started by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(state.ready) {
        if (state.ready && !started) { started = true; tab = startTab(state.prefs.startTab) }
    }
    var stack by androidx.compose.runtime.remember { mutableStateOf(listOf<Overlay>()) }
    val cs = MaterialTheme.colorScheme
    val x = LocalExtra.current

    fun open(o: Overlay) { stack = stack + o }
    fun back() { stack = stack.dropLast(1) }
    val openStock: (Quote) -> Unit = { q -> open(Overlay.Stock(q.ticker, q.list == "Indices")) }

    BackHandler(enabled = stack.isNotEmpty()) { back() }
    BackHandler(enabled = stack.isEmpty() && tab != Tab.WATCHLIST) { tab = Tab.WATCHLIST }

    Box(Modifier.fillMaxSize().background(cs.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                AnimatedContent(tab, label = "tabs", transitionSpec = { fadeIn() togetherWith fadeOut() }) { t ->
                    when (t) {
                        Tab.WATCHLIST -> WatchlistScreen(state, openStock, openSearch = { tab = Tab.SEARCH }, openInsights = { tab = Tab.INSIGHTS })
                        Tab.INSIGHTS -> InsightsScreen(state, openStock) { tab = Tab.SEARCH }
                        Tab.MARKETS -> MarketsScreen(state, openStock)
                        Tab.SEARCH -> SearchScreen(state)
                        Tab.SETTINGS -> SettingsScreen(state) { open(Overlay.Connection) }
                    }
                }
            }
            NavigationBar(containerColor = cs.surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { t ->
                    val icon: ImageVector = when (t) {
                        Tab.WATCHLIST -> Icons.AutoMirrored.Filled.List
                        Tab.INSIGHTS -> InsightsIcon
                        Tab.MARKETS -> MarketsIcon
                        Tab.SEARCH -> Icons.Filled.Search
                        Tab.SETTINGS -> Icons.Filled.Settings
                    }
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(icon, t.label) },
                        label = { Text(t.label, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = cs.primary, selectedTextColor = cs.primary, indicatorColor = cs.primaryContainer,
                            unselectedIconColor = x.muted, unselectedTextColor = x.muted,
                        ),
                    )
                }
            }
        }

        // Detail screens slide in over the tabs.
        AnimatedContent(
            stack.lastOrNull(), label = "overlay",
            transitionSpec = { slideInHorizontally { it / 3 } + fadeIn() togetherWith slideOutHorizontally { it / 3 } + fadeOut() },
        ) { o ->
            when (o) {
                is Overlay.Stock -> StockScreen(state, o.ticker, o.isIndex, onBack = { back() }, openStock = { q -> stack = stack.dropLast(1) + Overlay.Stock(q.ticker, false) })
                Overlay.Connection -> ConnectionScreen(state) { back() }
                null -> Box(Modifier)
            }
        }

        // Short messages such as "TCS added to My list".
        AnimatedVisibility(state.toast != null, enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 92.dp, start = 24.dp, end = 24.dp)) {
            Surface(color = cs.inverseSurface, shape = Shape12) {
                Text(state.toast.orEmpty(), color = cs.inverseOnSurface, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
            }
        }
    }
}
