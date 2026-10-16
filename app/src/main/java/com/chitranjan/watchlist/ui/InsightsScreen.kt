package com.chitranjan.watchlist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.data.Agg
import com.chitranjan.watchlist.data.Analytics
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Companies
import com.chitranjan.watchlist.data.HistoryReport
import com.chitranjan.watchlist.data.Line
import com.chitranjan.watchlist.data.ListReport
import com.chitranjan.watchlist.data.Member
import com.chitranjan.watchlist.data.Money
import com.chitranjan.watchlist.data.Period
import com.chitranjan.watchlist.data.Quote
import com.chitranjan.watchlist.data.SectorRow
import com.chitranjan.watchlist.data.Sectors
import com.chitranjan.watchlist.util.arrow
import com.chitranjan.watchlist.util.dirOf
import com.chitranjan.watchlist.util.Dir
import com.chitranjan.watchlist.util.inr
import com.chitranjan.watchlist.util.istNow
import com.chitranjan.watchlist.util.keyLabel
import com.chitranjan.watchlist.util.num
import com.chitranjan.watchlist.util.signPct
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

private enum class SectorSort(val label: String) { BEST("Best first"), WORST("Worst first"), SIZE("Most stocks") }

@Composable
fun InsightsScreen(state: AppState, openStock: (Quote) -> Unit, openSearch: () -> Unit) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val prefs = state.prefs
    val data = state.data
    val list = state.activeList
    val hide = prefs.hideAmounts

    // Everything below is recomputed only when prices, the list or the holdings change.
    val members = remember(data, list, prefs.holdings) {
        data?.rows.orEmpty().filter { it.list == list }.map { q -> Member(q, Sectors.of(q.ticker, q.name), prefs.holdings[q.key]) }
    }
    val report = remember(members) { Analytics.report(members) }
    val tickers = remember(members) { members.map { it.ticker } }
    androidx.compose.runtime.LaunchedEffect(tickers) { if (tickers.isNotEmpty()) state.loadHistory(tickers) }

    val version = state.history.version
    val period = prefs.period
    val hist by produceState<HistoryReport?>(null, members, period, version) {
        val today = istNow().toLocalDate()
        value = withContext(Dispatchers.Default) {
            val lines = HashMap<String, Line>()
            for (m in members) {
                val h = state.history.peek(m.ticker) ?: continue
                Analytics.line(h.closes, m.quote.price, Analytics.dateKey(today))?.let { lines[m.ticker] = it }
            }
            Analytics.historyReport(members, lines, Analytics.startKey(period.days, today), report.weighted)
        }
    }

    Column(Modifier.fillMaxSize().background(cs.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Insights", style = MaterialTheme.typography.headlineMedium, color = cs.onBackground)
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(state)
                    Spacer(Modifier.width(10.dp))
                    Text(data?.let { "Updated ${com.chitranjan.watchlist.util.hm(com.chitranjan.watchlist.util.ist(it.at))}" } ?: "Loading prices…",
                        style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1)
                }
            }
            IconBtn(Icons.Filled.Refresh, "Refresh prices", busy = state.loading) { state.refresh(); state.loadHistory(tickers) }
        }
        PullToRefreshBox(isRefreshing = false, onRefresh = { state.refresh() }, modifier = Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item("chips") { ListChips(state, Modifier.padding(bottom = 4.dp)) }

                if (members.isEmpty()) item("empty") {
                    CardBox(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp), padding = 24.dp) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Search, null, tint = x.muted, modifier = Modifier.size(28.dp))
                            Text(if (data == null) "Loading your watchlist" else "“$list” is empty", style = MaterialTheme.typography.titleMedium, color = cs.onSurface, modifier = Modifier.padding(top = 10.dp))
                            Text("Add some NSE or BSE stocks to see how the list is moving, by exchange and by sector.", style = MaterialTheme.typography.bodyMedium, color = x.muted,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                            PrimaryButton("Search stocks", icon = Icons.Filled.Search) { openSearch() }
                        }
                    }
                } else {
                    item("hero") { Column(Modifier.padding(horizontal = 16.dp).padding(top = 12.dp)) { Hero(report, list, hide) } }
                    item("exchanges") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            SectionTitle("NSE vs BSE")
                            ExchangeSplit(report, hist)
                        }
                    }
                    item("sectors") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            SectorSection(report, hist, period, openStock)
                        }
                    }
                    item("history") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            SectionTitle("Performance over time")
                            HistorySection(state, report, hist, period, tickers)
                        }
                    }
                    item("leaders") {
                        Column(Modifier.padding(horizontal = 16.dp)) { Leaders(hist, period, openStock) }
                    }
                    item("movers") {
                        Column(Modifier.padding(horizontal = 16.dp)) { Movers(report, openStock) }
                    }
                    item("foot") {
                        Text("Equal-weighted unless every stock has a holding · prices from Google Finance, may lag by a few minutes",
                            style = MaterialTheme.typography.bodySmall, color = x.muted, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 18.dp, bottom = 30.dp))
                    }
                }
            }
        }
    }
}

/* ---------- today ---------- */

@Composable
private fun pctColor(p: Double?): Color = dirColor(p)

@Composable
private fun BigPct(p: Double?, size: Int = 30) {
    val x = LocalExtra.current
    Text(
        if (p == null) "—" else "${arrow(p)} ${num(abs(p))}%",
        fontSize = size.sp, fontWeight = FontWeight.ExtraBold, color = if (p == null) x.muted else pctColor(p), maxLines = 1,
    )
}

@Composable
private fun Hero(r: ListReport, list: String, hide: Boolean) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    CardBox(padding = 16.dp) {
        Column {
            Text("TODAY · ${list.uppercase()}", style = MaterialTheme.typography.labelSmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BigPct(r.all.pct, 34)
            Text(
                if (r.weighted) "Weighted by your holdings · ${r.all.count} stocks"
                else "Average move of ${r.all.count} ${if (r.all.count == 1) "stock" else "stocks"}",
                style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(14.dp))
            BreadthBar(r.all.up, r.all.flat, r.all.down)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${r.all.up} up", color = x.up, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                if (r.all.flat > 0) Text("${r.all.flat} unchanged", color = x.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text("${r.all.down} down", color = x.down, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            val m = r.money
            if (m != null) {
                Spacer(Modifier.height(14.dp))
                HorizontalLine()
                Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                    MoneyStat("Value", inr(m.value, hide), null, Modifier.weight(1f))
                    MoneyStat("Today", inr(m.dayPnl, hide, signed = true), m.dayPct, Modifier.weight(1f), colorBy = m.dayPnl)
                    MoneyStat("Overall", inr(m.totalPnl, hide, signed = true), m.totalPct, Modifier.weight(1f), colorBy = m.totalPnl)
                }
                if (m.covered < m.total) Text("Holdings entered for ${m.covered} of ${m.total} stocks", style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 10.dp))
            } else {
                Row(
                    Modifier.padding(top = 14.dp).fillMaxWidth().clip(Shape12).background(cs.primaryContainer).padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Open a stock and enter how many shares you hold and your buy price to see your profit and loss here.",
                        style = MaterialTheme.typography.bodySmall, color = cs.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun HorizontalLine() = Box(Modifier.fillMaxWidth().height(1.dp).background(LocalExtra.current.border))

@Composable
private fun MoneyStat(label: String, value: String, pct: Double?, modifier: Modifier, colorBy: Double? = null) {
    val x = LocalExtra.current
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = x.muted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = if (colorBy == null) MaterialTheme.colorScheme.onSurface else dirColor(colorBy), maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        if (pct != null) Text(signPct(pct), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dirColor(pct))
    }
}

/* ---------- NSE vs BSE ---------- */

@Composable
private fun ExchangeSplit(r: ListReport, hist: HistoryReport?) {
    val x = LocalExtra.current
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ExchangeCard("NSE", x.nse, r.nse, hist?.nse?.last, Modifier.weight(1f))
            ExchangeCard("BSE", x.bse, r.bse, hist?.bse?.last, Modifier.weight(1f))
        }
        val a = r.nse.pct; val b = r.bse.pct
        if (a != null && b != null) {
            val gap = b - a
            CardBox(Modifier.padding(top = 10.dp), padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(SwapIcon, null, tint = x.muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            abs(gap) < 0.005 -> "NSE and BSE stocks are moving in step today."
                            gap > 0 -> "BSE stocks are ahead of NSE by ${num(gap)} percentage points today."
                            else -> "NSE stocks are ahead of BSE by ${num(-gap)} percentage points today."
                        },
                        style = MaterialTheme.typography.bodyMedium, color = x.textSoft,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExchangeCard(name: String, color: Color, a: Agg, periodPct: Double?, modifier: Modifier) {
    val x = LocalExtra.current
    CardBox(modifier, padding = 14.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(7.dp))
                Text(name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.weight(1f))
                Text("${a.count} ${if (a.count == 1) "stock" else "stocks"}", style = MaterialTheme.typography.bodySmall, color = x.muted)
            }
            Spacer(Modifier.height(8.dp))
            if (a.count == 0) {
                Text("None in this list", style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.padding(vertical = 6.dp))
            } else {
                BigPct(a.pct, 24)
                Text("today", style = MaterialTheme.typography.bodySmall, color = x.muted)
                Spacer(Modifier.height(10.dp))
                BreadthBar(a.up, a.flat, a.down)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${a.up}▲", color = x.up, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("${a.down}▼", color = x.down, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (periodPct != null) Text("Over the period ${signPct(periodPct)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dirColor(periodPct), modifier = Modifier.padding(top = 8.dp))
                a.best?.let { b -> Text("Best ${Companies.label(b.ticker, b.quote.name)} ${signPct(b.quote.changepct)}", fontSize = 11.5.sp, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
                a.worst?.takeIf { it !== a.best }?.let { w -> Text("Worst ${Companies.label(w.ticker, w.quote.name)} ${signPct(w.quote.changepct)}", fontSize = 11.5.sp, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/* ---------- sectors ---------- */

@Composable
private fun SectorSection(r: ListReport, hist: HistoryReport?, period: Period, openStock: (Quote) -> Unit) {
    val x = LocalExtra.current
    var sort by rememberSaveable { mutableStateOf(SectorSort.BEST) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    val rows = remember(r, sort) {
        when (sort) {
            SectorSort.BEST -> r.sectors.sortedByDescending { it.all.pct ?: -1e9 }
            SectorSort.WORST -> r.sectors.sortedBy { it.all.pct ?: 1e9 }
            SectorSort.SIZE -> r.sectors.sortedWith(compareByDescending<SectorRow> { it.members.size }.thenBy { it.name })
        }
    }
    val maxAbs = max(rows.maxOfOrNull { abs(it.all.pct ?: 0.0) } ?: 0.0, 0.5)

    SectionTitle("Sectors")
    Segmented(SectorSort.entries.map { it to it.label }, sort) { sort = it }
    Spacer(Modifier.height(10.dp))
    CardBox(padding = 0.dp) {
        Column {
            rows.forEachIndexed { i, s ->
                val isOpen = open == s.name
                Column(Modifier.animateContentSize()) {
                    Column(Modifier.fillMaxWidth().clickable { open = if (isOpen) null else s.name }.padding(horizontal = 14.dp, vertical = max(x.rowV.value - 2f, 8f).dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(Sectors.short(s.name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${s.members.size} ${if (s.members.size == 1) "stock" else "stocks"}", style = MaterialTheme.typography.bodySmall, color = x.muted)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                ChangePill(s.all.pct, s.all.pct, compact = true)
                                hist?.sectorReturns?.get(s.name)?.let { Text("${period.label} ${signPct(it)}", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = dirColor(it), modifier = Modifier.padding(top = 3.dp)) }
                            }
                            Icon(if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, null, tint = x.muted, modifier = Modifier.padding(start = 6.dp).size(20.dp))
                        }
                        MoveBar(s.all.pct, maxAbs, Modifier.padding(top = 8.dp))
                    }
                    AnimatedVisibility(isOpen) {
                        Column(Modifier.fillMaxWidth().background(x.surface2.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (s.nse.count > 0 && s.bse.count > 0) Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text("NSE ${signPct(s.nse.pct)}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = dirColor(s.nse.pct))
                                Text("BSE ${signPct(s.bse.pct)}", fontSize = 12.5.sp, fontWeight = FontWeight.ExtraBold, color = dirColor(s.bse.pct))
                            }
                            s.members.forEach { m ->
                                Row(Modifier.fillMaxWidth().clickable { openStock(m.quote) }.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(Companies.label(m.ticker, m.quote.name), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(6.dp)); ExBadge(m.quote.exchange)
                                    Spacer(Modifier.weight(1f))
                                    Text(signPct(m.quote.changepct), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = dirColor(m.quote.changepct))
                                }
                            }
                        }
                    }
                    if (i < rows.size - 1) Divider()
                }
            }
        }
    }
}

/* ---------- history ---------- */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistorySection(state: AppState, r: ListReport, hist: HistoryReport?, period: Period, tickers: List<String>) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val load = state.historyLoad
    var hidden by rememberSaveable { mutableStateOf(setOf<String>()) }

    CardBox(padding = 14.dp) {
        Column {
            Segmented(Period.entries.map { it to it.label }, period) { p -> state.updatePrefs { it.copy(period = p) } }
            Spacer(Modifier.height(14.dp))

            if (load.running) {
                Text("Loading price history · ${load.done} of ${load.total} stocks", style = MaterialTheme.typography.bodySmall, color = x.muted)
                LinearProgressIndicator(progress = { if (load.total == 0) 0f else load.done.toFloat() / load.total }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), color = cs.primary, trackColor = x.surface2)
            } else if (load.failed.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().clip(Shape12).background(x.downSoft).clickable { state.loadHistory(tickers, force = false) }.padding(12.dp)) {
                    Text("${load.failed.size} ${if (load.failed.size == 1) "stock" else "stocks"} could not be loaded. Tap to retry.", color = x.down, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }

            val combined = hist?.combined
            if (combined == null) {
                Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (load.running || hist == null) "Gathering history… the first time can take a minute or two" else "No price history for this list yet",
                        color = x.muted, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                // Legend doubles as the on/off switches for each line.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LegendChip("Combined", cs.primary, combined.last, "Combined" !in hidden) { hidden = hidden.toggle("Combined") }
                    hist.nse?.let { LegendChip("NSE", x.nse, it.last, "NSE" !in hidden) { hidden = hidden.toggle("NSE") } }
                    hist.bse?.let { LegendChip("BSE", x.bse, it.last, "BSE" !in hidden) { hidden = hidden.toggle("BSE") } }
                }
                Spacer(Modifier.height(10.dp))
                val shown = buildList {
                    if ("Combined" !in hidden) add(ChartLine("Combined", cs.primary, combined))
                    hist.nse?.let { if ("NSE" !in hidden) add(ChartLine("NSE", x.nse, it)) }
                    hist.bse?.let { if ("BSE" !in hidden) add(ChartLine("BSE", x.bse, it, dashed = true)) }
                }
                if (shown.isEmpty()) Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) { Text("Turn a line on above", color = x.muted) }
                else MultiLineChart(shown)

                hist.stats?.let { st ->
                    Spacer(Modifier.height(12.dp))
                    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2) {
                        StatCell("Best day", "${signPct(st.bestDay)}", keyLabel(st.bestKey), dirColor(st.bestDay), Modifier.weight(1f))
                        StatCell("Worst day", "${signPct(st.worstDay)}", keyLabel(st.worstKey), dirColor(st.worstDay), Modifier.weight(1f))
                        StatCell("Biggest fall from a peak", signPct(st.maxDrawdown), "within ${period.label}", dirColor(st.maxDrawdown.takeIf { it < 0 }), Modifier.weight(1f))
                        StatCell("Stocks covered", "${hist.covered} of ${hist.total}", if (hist.covered < hist.total) "others have no history" else "all have history", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
                    }
                }
                Text(
                    if (r.weighted) "Weighted by your holdings (quantity × price)." else "Each stock starts with the same amount, so every stock counts equally at the start.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 4.dp),
                )
                val viaNse = tickers.count { state.history.peek(it)?.viaNse == true }
                if (viaNse > 0) Text(
                    "Google has no BSE history for $viaNse ${if (viaNse == 1) "stock" else "stocks"}, so their NSE prices are used for the charts.",
                    style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun Set<String>.toggle(k: String): Set<String> = if (k in this) this - k else this + k

@Composable
private fun LegendChip(label: String, color: Color, pct: Double, on: Boolean, onClick: () -> Unit) {
    val x = LocalExtra.current
    Surface(
        onClick = onClick, shape = Shape12, color = if (on) color.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (on) color else x.border),
    ) {
        Column(Modifier.padding(horizontal = 11.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) color else x.muted))
                Spacer(Modifier.width(6.dp))
                Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (on) MaterialTheme.colorScheme.onSurface else x.muted)
            }
            Text(signPct(pct), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (on) dirColor(pct) else x.muted, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, sub: String, color: Color, modifier: Modifier) {
    val x = LocalExtra.current
    Column(modifier.padding(bottom = 12.dp, end = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, fontSize = 15.5.sp, fontWeight = FontWeight.ExtraBold, color = color, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
        Text(sub, fontSize = 11.5.sp, color = x.muted, maxLines = 1)
    }
}

/* ---------- leaders and movers ---------- */

@Composable
private fun Leaders(hist: HistoryReport?, period: Period, openStock: (Quote) -> Unit) {
    val s = hist?.stockReturns.orEmpty()
    if (s.size < 2) return
    val best = s.take(3).filter { it.second > 0 }
    val worst = s.takeLast(3).reversed().filter { it.second < 0 }.filter { w -> best.none { it.first === w.first } }
    if (best.isEmpty() && worst.isEmpty()) return
    SectionTitle("Best and worst over ${period.label}")
    CardBox(padding = 0.dp) {
        Column {
            val all = best + worst
            all.forEachIndexed { i, (m, p) ->
                Row(Modifier.fillMaxWidth().clickable { openStock(m.quote) }.rowPad(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Companies.label(m.ticker, m.quote.name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Spacer(Modifier.width(6.dp)); ExBadge(m.quote.exchange)
                        }
                        Text(Sectors.short(m.sector), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    ChangePill(p, p, compact = true)
                }
                if (i < all.size - 1) Divider()
            }
        }
    }
}

@Composable
private fun Movers(r: ListReport, openStock: (Quote) -> Unit) {
    if (r.gainers.isEmpty() && r.losers.isEmpty()) return
    SectionTitle("Biggest moves today")
    CardBox(padding = 0.dp) {
        Column {
            val all = r.gainers + r.losers
            all.forEachIndexed { i, m ->
                Row(Modifier.fillMaxWidth().clickable { openStock(m.quote) }.rowPad(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Companies.label(m.ticker, m.quote.name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                            Spacer(Modifier.width(6.dp)); ExBadge(m.quote.exchange)
                        }
                        Text(Sectors.short(m.sector), style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 2.dp))
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(num(m.quote.price), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        ChangePill(m.quote.change, m.quote.changepct, compact = true)
                    }
                }
                if (i < all.size - 1) Divider()
            }
        }
    }
}
