package com.chitranjan.watchlist.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Quote
import com.chitranjan.watchlist.util.INDEX_NAMES
import com.chitranjan.watchlist.util.MarketKind
import com.chitranjan.watchlist.util.hm
import com.chitranjan.watchlist.util.ist
import com.chitranjan.watchlist.util.market
import com.chitranjan.watchlist.util.num
import com.chitranjan.watchlist.util.sessionNote

@Composable
fun MarketsScreen(state: AppState, openStock: (Quote) -> Unit) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val data = state.data
    val m = market(data)

    val unique = data?.rows.orEmpty().filter { it.changepct != null }.distinctBy { it.ticker }
    val up = unique.filter { it.changepct!! > 0 }.sortedByDescending { it.changepct }
    val down = unique.filter { it.changepct!! < 0 }.sortedBy { it.changepct }
    val flat = unique.size - up.size - down.size

    Column(Modifier.fillMaxSize().background(cs.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Markets", style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, modifier = Modifier.weight(1f))
            IconBtn(Icons.Filled.Refresh, "Refresh prices", busy = state.loading) { state.refresh() }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp)) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).clip(Shape12).background(if (m.kind == MarketKind.LIVE) x.upSoft else cs.primaryContainer), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (m.kind == MarketKind.LIVE) x.up else cs.primary))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        StatusPill(state)
                        Text(sessionNote(data), style = MaterialTheme.typography.bodyMedium, color = x.textSoft, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }

            SectionTitle("Indices")
            data?.indices.orEmpty().forEach { q ->
                Surface(onClick = { openStock(q) }, shape = Shape16, color = cs.surface, border = BorderStroke(1.dp, x.border), modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(INDEX_NAMES[q.ticker] ?: q.name.orEmpty(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = x.muted)
                                Text(num(q.price), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = cs.onSurface, modifier = Modifier.padding(top = 2.dp))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                ChangeText(q.change, q.changepct)
                                Text("Prev close ${num(q.closeyest)}", style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                        RangeBar("Today's range", q.low, q.high, q.price)
                    }
                }
            }

            if (unique.isNotEmpty()) {
                SectionTitle("Your stocks today")
                CardBox {
                    Column {
                        Row(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(x.surface2)) {
                            if (up.isNotEmpty()) Box(Modifier.weight(up.size.toFloat()).height(10.dp).background(x.up))
                            if (flat > 0) Box(Modifier.weight(flat.toFloat()).height(10.dp).background(x.border))
                            if (down.isNotEmpty()) Box(Modifier.weight(down.size.toFloat()).height(10.dp).background(x.down))
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${up.size} up", color = x.up, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (flat > 0) Text("$flat unchanged", color = x.muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("${down.size} down", color = x.down, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (up.isNotEmpty()) Movers(state, "Top gainers", up.take(5), openStock)
            if (down.isNotEmpty()) Movers(state, "Top losers", down.take(5), openStock)

            Text((data?.let { "Updated ${hm(ist(it.at))} IST · " } ?: "") + "Prices from Google Finance",
                style = MaterialTheme.typography.bodySmall, color = x.muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp))
        }
    }
}

@Composable
private fun Movers(state: AppState, title: String, rows: List<Quote>, openStock: (Quote) -> Unit) {
    SectionTitle(title)
    CardBox(padding = 0.dp) {
        Column { rows.forEachIndexed { i, q -> StockRow(state, q, showList = true) { openStock(q) }; if (i < rows.size - 1) Divider() } }
    }
}

