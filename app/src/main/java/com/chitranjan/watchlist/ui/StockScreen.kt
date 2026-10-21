package com.chitranjan.watchlist.ui

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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Close
import com.chitranjan.watchlist.data.Companies
import com.chitranjan.watchlist.data.Sectors
import com.chitranjan.watchlist.util.inr
import com.chitranjan.watchlist.data.Quote
import com.chitranjan.watchlist.util.INDEX_NAMES
import com.chitranjan.watchlist.util.arrow
import com.chitranjan.watchlist.util.lastTradeLabel
import com.chitranjan.watchlist.util.num
import com.chitranjan.watchlist.util.signPct
import com.chitranjan.watchlist.util.volume
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

private val PERIODS = listOf("1W" to 7L, "1M" to 30L, "3M" to 91L, "6M" to 182L, "1Y" to 365L)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StockScreen(state: AppState, ticker: String, isIndex: Boolean, onBack: () -> Unit, openStock: (Quote) -> Unit) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val rows = state.data?.rows.orEmpty()
    val q = (if (isIndex) state.data?.indices.orEmpty() else rows).firstOrNull { it.ticker == ticker }
    val c = Companies.forTicker(ticker)

    var hist by remember(ticker) { mutableStateOf<List<Close>?>(null) }
    var fromNse by remember(ticker) { mutableStateOf(false) }
    var failed by remember(ticker) { mutableStateOf(false) }
    var period by remember { mutableStateOf("1M") }
    var busyList by remember { mutableStateOf<String?>(null) }
    var editHolding by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ticker) {
        try {
            val th = state.historyOf(ticker)
            fromNse = th.viaNse
            hist = th.closes
        } catch (e: Exception) { failed = true; hist = emptyList() }
    }

    val slice: List<Double> = remember(hist, period) {
        val h = hist.orEmpty()
        if (h.isEmpty()) emptyList() else {
            val days = PERIODS.first { it.first == period }.second
            val cutDate = LocalDate.now().minusDays(days)
            val cut = cutDate.toString()
            val inside = h.filter { it.date >= cut }
            val before = h.lastOrNull { it.date < cut }
            val near = before != null && LocalDate.parse(before.date).isAfter(cutDate.minusDays(10))
            if (inside.isEmpty()) emptyList() else (if (near) listOf(before!!) + inside else inside).map { it.price }
        }
    }
    val price = q?.price
    val values = if (price != null && slice.isNotEmpty()) slice + price else slice
    val base = slice.firstOrNull()
    val pChange = if (base != null && price != null) price - base else null

    val title = if (isIndex) INDEX_NAMES[ticker] ?: q?.name ?: ticker else Companies.label(ticker, q?.name)
    val memberOf = rows.filter { it.ticker == ticker }.map { it.list }.toSet()
    val nseQ = c?.nse?.takeIf { it.isNotEmpty() }?.let { s -> rows.firstOrNull { it.ticker == "NSE:$s" } }
    val bseQ = c?.bse?.takeIf { it.isNotEmpty() }?.let { s -> rows.firstOrNull { it.ticker == "BOM:$s" } }
    val twin = !isIndex && nseQ != null && bseQ != null

    Column(Modifier.fillMaxSize().background(cs.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
            Text(title, style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            IconBtn(Icons.Filled.Refresh, "Refresh", busy = state.loading) { state.refresh() }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = 30.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isIndex) {
                    ExBadge(q?.exchange ?: if (ticker.startsWith("BOM:")) "BSE" else "NSE"); Spacer(Modifier.width(6.dp))
                    ExBadge(Sectors.short(Sectors.of(ticker, q?.name))); Spacer(Modifier.width(8.dp))
                }
                Text(if (isIndex) (if (ticker.startsWith("INDEXBOM")) "BSE index" else "NSE index") else c?.name ?: q?.name.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (q == null) {
                CardBox(Modifier.padding(top = 16.dp)) { Text("This stock isn't in your watchlists any more.", color = x.muted) }
                return@Column
            }
            Text("₹" + num(price), fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, color = cs.onBackground, modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChangeText(q.change, q.changepct, 15)
                Spacer(Modifier.width(8.dp))
                Text("today · ${lastTradeLabel(q)}", style = MaterialTheme.typography.bodyMedium, color = x.muted)
            }

            CardBox(Modifier.padding(top = 18.dp)) {
                Column {
                    if (pChange != null && base != null) Row(Modifier.padding(bottom = 12.dp)) {
                        Text("${arrow(pChange)} ${num(abs(pChange))} (${signPct(pChange / base * 100)})", color = dirColor(pChange), fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp)
                        Text("  in $period", color = x.muted, fontSize = 13.5.sp)
                    } else Text("Price history", color = x.muted, fontSize = 13.5.sp, modifier = Modifier.padding(bottom = 12.dp))
                    when {
                        hist == null -> Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        failed -> Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) { Text("Couldn't load history", color = x.muted) }
                        else -> PriceChart(values)
                    }
                    if (fromNse && !failed && hist != null) Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Info, null, tint = x.muted, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Google has no BSE price history for this stock, so the chart shows its NSE prices.", fontSize = 12.sp, color = x.muted)
                    }
                    Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(Shape12).background(x.surface2).padding(3.dp)) {
                        PERIODS.forEach { (k, _) ->
                            val on = k == period
                            Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) cs.primary else cs.surface.copy(alpha = 0f))
                                .clickable { period = k }.padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(k, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) cs.onPrimary else x.textSoft)
                            }
                        }
                    }
                }
            }

            if (twin) {
                SectionTitle("NSE vs BSE")
                CardBox {
                    Column {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf(nseQ!!, bseQ!!).forEach { t ->
                                val mine = t.ticker == ticker
                                Surface(
                                    onClick = { if (!mine) openStock(t) }, shape = Shape12,
                                    color = if (mine) cs.primaryContainer else x.surface2,
                                    border = BorderStroke(1.5.dp, if (mine) cs.primary else x.surface2), modifier = Modifier.weight(1f),
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(t.exchange, style = MaterialTheme.typography.labelSmall, color = x.muted)
                                        Text(num(t.price), fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurface, modifier = Modifier.padding(top = 4.dp))
                                        ChangeText(t.change, t.changepct, 12)
                                    }
                                }
                            }
                        }
                        val g = if (nseQ.price != null && bseQ.price != null) bseQ.price - nseQ.price else null
                        if (g != null) Text(
                            if (g == 0.0) "Same price on both exchanges right now."
                            else "BSE is ₹${num(abs(g))} ${if (g > 0) "higher" else "lower"} than NSE (${num(abs(g) / nseQ.price!! * 100)}%).",
                            style = MaterialTheme.typography.bodyMedium, color = x.textSoft, modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }

            SectionTitle("Today")
            CardBox {
                Column {
                    FlowRow(Modifier.fillMaxWidth(), maxItemsInEachRow = 2) {
                        listOf(
                            "Day low – high" to "${num(q.low)} – ${num(q.high)}",
                            "Prev close" to num(q.closeyest),
                            "Volume" to if (isIndex) "—" else volume(q.volume),
                            "Last trade" to lastTradeLabel(q),
                        ).forEach { (k, v) ->
                            Column(Modifier.weight(1f).padding(bottom = 12.dp)) {
                                Text(k, style = MaterialTheme.typography.bodySmall, color = x.muted)
                                Text(v, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onSurface, maxLines = 1, modifier = Modifier.padding(top = 3.dp))
                            }
                        }
                    }
                    RangeBar("Day range", q.low, q.high, price)
                    RangeBar("52-week range", q.low52, q.high52, price)
                }
            }

            if (!isIndex && memberOf.isNotEmpty()) {
                SectionTitle("Your holding")
                CardBox(padding = 0.dp) {
                    Column {
                        val mine = state.lists.filter { it in memberOf }
                        mine.forEachIndexed { i, l ->
                            val h = state.prefs.holdings["$l|$ticker"]
                            Row(Modifier.fillMaxWidth().clickable { editHolding = l }.rowPad(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(l, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (h == null) "Tap to add shares and buy price" else "${num(h.qty, if (h.qty % 1.0 == 0.0) 0 else 2)} shares · avg ₹${num(h.avg)}",
                                        style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                if (h != null && price != null) {
                                    val pnl = h.qty * (price - h.avg)
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(inr(h.qty * price, state.prefs.hideAmounts), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = cs.onSurface)
                                        Text("${inr(pnl, state.prefs.hideAmounts, signed = true)}  ${signPct(if (h.avg > 0) (price / h.avg - 1) * 100 else null)}",
                                            fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = dirColor(pnl))
                                    }
                                } else Icon(Icons.Filled.Add, "Add holding", tint = cs.primary, modifier = Modifier.size(20.dp))
                            }
                            if (i < mine.size - 1) Divider()
                        }
                    }
                }
                Text("Stored only on this phone. Used for value and profit and loss in Insights.", style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }

            if (!isIndex) {
                SectionTitle("In your lists")
                CardBox {
                    Column {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.lists.forEach { l ->
                                val on = l in memberOf
                                ChipBtn(if (busyList == l) "Saving…" else l, active = on, icon = if (on) Icons.Filled.Check else Icons.Filled.Add) {
                                    if (busyList != null) return@ChipBtn
                                    scope.launch {
                                        busyList = l
                                        try {
                                            if (on) { state.remove(l, ticker); state.showToast("Removed from $l") }
                                            else { state.add(l, ticker); state.showToast("Added to $l") }
                                        } catch (e: Exception) { state.showToast(e.message ?: "Could not update") }
                                        finally { busyList = null }
                                    }
                                }
                            }
                        }
                        Text("Tap a list to add or remove this stock.", style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
    }

    editHolding?.let { l ->
        HoldingDialog(
            title = "${Companies.label(ticker, q?.name)} in $l", current = state.prefs.holdings["$l|$ticker"], lastPrice = price,
            onDismiss = { editHolding = null },
            onSave = { h -> state.setHolding(l, ticker, h); editHolding = null; state.showToast(if (h == null) "Holding removed" else "Holding saved") },
        )
    }
}
