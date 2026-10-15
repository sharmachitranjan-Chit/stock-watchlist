package com.chitranjan.watchlist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Companies
import com.chitranjan.watchlist.data.CrashLog
import com.chitranjan.watchlist.data.Quote
import com.chitranjan.watchlist.data.SortMode
import com.chitranjan.watchlist.data.ViewMode
import com.chitranjan.watchlist.util.INDEX_NAMES
import com.chitranjan.watchlist.util.INDEX_SHORT
import com.chitranjan.watchlist.util.MarketKind
import com.chitranjan.watchlist.util.arrow
import com.chitranjan.watchlist.util.day
import com.chitranjan.watchlist.util.hm
import com.chitranjan.watchlist.util.ist
import com.chitranjan.watchlist.util.latestTrade
import com.chitranjan.watchlist.util.market
import com.chitranjan.watchlist.util.num
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(state: AppState, openStock: (Quote) -> Unit, openSearch: () -> Unit) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val prefs = state.prefs
    val data = state.data
    val list = state.activeList
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(false) }
    var newList by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Quote?>(null) }
    val listState = rememberLazyListState()
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 8 } }
    val showTicker by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // Worked out only when prices, list, view or sort change, not on every scroll frame.
    val inList = remember(data, list) { data?.rows.orEmpty().filter { it.list == list } }
    val rows = remember(inList, prefs.view, prefs.sort) { when (prefs.view) {
        ViewMode.NSE -> inList.filter { it.isNse }
        ViewMode.BSE -> inList.filter { it.isBse }
        else -> inList
    }.let { r ->
        when (prefs.sort) {
            SortMode.MY -> r
            SortMode.GAIN -> r.sortedByDescending { it.changepct ?: -1e9 }
            SortMode.LOSS -> r.sortedBy { it.changepct ?: 1e9 }
            SortMode.NAME -> r.sortedBy { Companies.label(it.ticker) }
        }
    } }
    val pairs = remember(inList, prefs.sort) { inList.mapIndexed { i, q -> i to q }
        .groupBy { (_, q) -> Companies.forTicker(q.ticker)?.isin ?: q.ticker }
        .map { (k, v) -> Pair2(k, Companies.forTicker(v.first().second.ticker), v.firstOrNull { it.second.isNse }?.second, v.firstOrNull { it.second.isBse }?.second, v.first().first) }
        .let { p ->
            when (prefs.sort) {
                SortMode.MY -> p.sortedBy { it.order }
                SortMode.GAIN -> p.sortedByDescending { maxOf(it.nse?.changepct ?: -1e9, it.bse?.changepct ?: -1e9) }
                SortMode.LOSS -> p.sortedBy { minOf(it.nse?.changepct ?: 1e9, it.bse?.changepct ?: 1e9) }
                SortMode.NAME -> p.sortedBy { Companies.label((it.nse ?: it.bse)?.ticker ?: it.key) }
            }
        } }
    val compare = prefs.view == ViewMode.COMPARE
    val count = if (compare) pairs.size else rows.size
    val indices = data?.indices.orEmpty().filter { it.price != null }
    LaunchedEffect(list, prefs.view, prefs.sort, count) {
        CrashLog.step("Watchlist: “$list”, ${prefs.view.label}, ${prefs.sort.label}, $count rows")
    }

    val lt = latestTrade(data)
    val status = when {
        data == null -> "Loading prices…"
        market(data).kind == MarketKind.CLOSED && lt != null -> "Prices as of ${day(lt)}, ${hm(lt)}"
        else -> "Updated ${hm(ist(data.at))}"
    }

    Column(Modifier.fillMaxSize().background(cs.background)) {
        /* ---------- fixed top bar ---------- */
        Column(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(top = 6.dp)) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Watchlist", style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, modifier = Modifier.weight(1f))
                IconBtn(Icons.Filled.Refresh, "Refresh prices", busy = state.loading) { state.refresh() }
                Spacer(Modifier.width(8.dp))
                IconBtn(Icons.Filled.Add, "Add stock") { openSearch() }
            }
            AnimatedContent(
                targetState = showTicker, label = "statusTicker",
                transitionSpec = { (fadeIn() + slideInVertically { it / 3 }) togetherWith fadeOut() },
                modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 10.dp).height(24.dp),
            ) { ticker ->
                if (!ticker) Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(state)
                    Spacer(Modifier.width(10.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    indices.forEach { q ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(INDEX_SHORT[q.ticker] ?: q.name.orEmpty(), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = x.muted, letterSpacing = 0.2.sp, maxLines = 1)
                            Spacer(Modifier.width(4.dp))
                            Text("${arrow(q.change)}${q.changepct?.let { num(abs(it)) + "%" } ?: ""}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = dirColor(q.change), maxLines = 1)
                        }
                    }
                }
            }
            if (scrolled) Divider()
        }

        PullToRefreshBox(isRefreshing = false, onRefresh = { state.refresh() }, modifier = Modifier.weight(1f)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                /* ---------- scrolls away: banner, index cards, list chips ---------- */
                item("intro") {
                    Column(Modifier.padding(top = 6.dp)) {
                        state.error?.let { err ->
                            Row(
                                Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).fillMaxWidth().clip(Shape12).background(x.downSoft)
                                    .clickable { state.refresh() }.padding(12.dp),
                            ) { Text("$err Tap to retry.", color = x.down, style = MaterialTheme.typography.bodyMedium) }
                        }
                        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            indices.forEach { q ->
                                Surface(
                                    onClick = { openStock(q) }, shape = Shape16, color = cs.surface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, x.border), modifier = Modifier.weight(1f),
                                ) {
                                    Column(Modifier.padding(11.dp)) {
                                        Text(INDEX_NAMES[q.ticker] ?: q.name.orEmpty(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = x.muted, maxLines = 1)
                                        Text(num(q.price), fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurface, maxLines = 1, modifier = Modifier.padding(vertical = 4.dp))
                                        ChangePill(q.change, q.changepct, compact = true)
                                    }
                                }
                            }
                        }
                        Row(
                            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.lists.forEach { l -> ChipBtn(l, active = l == list) { state.setActiveList(l); edit = false } }
                            ChipBtn("New list", icon = Icons.Filled.Add) { newList = true }
                        }
                    }
                }

                /* ---------- pinned under the top bar ---------- */
                stickyHeader("controls") {
                    Column(Modifier.fillMaxWidth().background(cs.background).padding(top = 10.dp, bottom = 2.dp)) {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            Segmented(ViewMode.entries.map { it to it.label }, prefs.view) { v -> state.updatePrefs { it.copy(view = v) }; edit = false }
                        }
                        Row(Modifier.padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                SortMode.entries.forEach { m ->
                                    Text(m.label, fontSize = 13.5.sp, fontWeight = if (prefs.sort == m) FontWeight.ExtraBold else FontWeight.Medium,
                                        color = if (prefs.sort == m) cs.primary else x.muted,
                                        modifier = Modifier.clickable { state.updatePrefs { it.copy(sort = m) } })
                                }
                            }
                            if (!compare && rows.isNotEmpty()) {
                                Text(if (edit) "Done" else "Edit", color = cs.primary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 12.dp).clickable { edit = !edit })
                            }
                        }
                        if (edit && prefs.sort != SortMode.MY) {
                            Text("Switch to “My order” to move stocks up or down.", style = MaterialTheme.typography.bodySmall, color = x.muted,
                                modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))
                        }
                        if (compare && pairs.isNotEmpty()) CompareHeader()
                    }
                }

                if (count == 0) item("empty") {
                    CardBox(Modifier.padding(horizontal = 16.dp), padding = 24.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Search, null, tint = x.muted, modifier = Modifier.size(28.dp))
                            Text(
                                when { data == null -> "Loading your watchlist"; prefs.view == ViewMode.NSE -> "No NSE stocks in “$list”"; prefs.view == ViewMode.BSE -> "No BSE stocks in “$list”"; else -> "“$list” is empty" },
                                style = MaterialTheme.typography.titleMedium, color = cs.onSurface, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp),
                            )
                            Text("Search any NSE or BSE company by name and add it here.", style = MaterialTheme.typography.bodyMedium, color = x.muted,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                            PrimaryButton("Search stocks", icon = Icons.Filled.Search) { openSearch() }
                        }
                    }
                }

                if (compare) itemsIndexed(pairs, key = { _, p -> "p:" + p.key }) { i, p ->
                    Segment(i, count) { CompareRow(state, p, list, openStock) }
                } else itemsIndexed(rows, key = { _, q -> "r:" + q.key }) { i, q ->
                    Segment(i, count) {
                        StockRow(
                            state, q,
                            edit = if (edit) Edit(
                                first = i == 0 || prefs.sort != SortMode.MY, last = i == count - 1 || prefs.sort != SortMode.MY,
                                up = { scope.launch { runCatching { state.move(q.list, q.ticker, -1) }.onFailure { state.showToast(it.message ?: "Could not move") } } },
                                down = { scope.launch { runCatching { state.move(q.list, q.ticker, 1) }.onFailure { state.showToast(it.message ?: "Could not move") } } },
                                remove = { confirm = q },
                            ) else null,
                        ) { openStock(q) }
                    }
                }

                item("foot") {
                    Text("Prices from Google Finance · may lag by a few minutes", style = MaterialTheme.typography.bodySmall, color = x.muted,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 30.dp))
                }
            }
        }
    }

    if (newList) NameDialog("New list", "e.g. Banks, Long term", onDismiss = { newList = false }) { state.createList(it); newList = false }

    confirm?.let { q ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Remove stock") },
            text = { Text("Remove ${Companies.label(q.ticker, q.name)} (${q.exchange}) from “${q.list}”?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch { runCatching { state.remove(q.list, q.ticker) }.onFailure { state.showToast(it.message ?: "Could not remove") } }
                }) { Text("Remove", color = x.down) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

/** Rows join into one rounded card: top corners on the first, bottom on the last, dividers between. */
@Composable
fun Segment(i: Int, count: Int, content: @Composable () -> Unit) {
    val r = 16.dp
    val shape: Shape = RoundedCornerShape(topStart = if (i == 0) r else 0.dp, topEnd = if (i == 0) r else 0.dp,
        bottomStart = if (i == count - 1) r else 0.dp, bottomEnd = if (i == count - 1) r else 0.dp)
    Column(Modifier.padding(horizontal = 16.dp).clip(shape).background(MaterialTheme.colorScheme.surface)) {
        content()
        if (i < count - 1) Divider()
    }
}

@Composable
fun StatusPill(state: AppState) {
    val x = LocalExtra.current
    val m = market(state.data)
    val color = when (m.kind) { MarketKind.LIVE -> x.up; MarketKind.PRE -> MaterialTheme.colorScheme.primary; MarketKind.CLOSED -> x.muted }
    Row(
        Modifier.clip(CircleShape).background(if (m.kind == MarketKind.LIVE) x.upSoft else x.surface2).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(m.label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun NameDialog(title: String, placeholder: String, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var v by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(v, { if (it.length <= 30) v = it }, placeholder = { Text(placeholder) }, singleLine = true) },
        confirmButton = { TextButton(onClick = { if (v.isNotBlank()) onCreate(v.trim()) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
