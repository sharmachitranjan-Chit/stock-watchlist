package com.chitranjan.watchlist.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Companies
import com.chitranjan.watchlist.data.Company
import com.chitranjan.watchlist.data.Quote
import com.chitranjan.watchlist.util.arrow
import com.chitranjan.watchlist.util.num
import com.chitranjan.watchlist.util.signPct
import kotlinx.coroutines.launch
import kotlin.math.abs

class Edit(val first: Boolean, val last: Boolean, val up: () -> Unit, val down: () -> Unit, val remove: () -> Unit)

@Composable
fun StockRow(state: AppState, q: Quote, showList: Boolean = false, edit: Edit? = null, onOpen: () -> Unit) {
    val x = LocalExtra.current
    val c = Companies.forTicker(q.ticker)
    Row(
        Modifier.fillMaxWidth().clickable(enabled = edit == null, onClick = onOpen).rowPad(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Companies.label(q.ticker, q.name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
                Spacer(Modifier.width(6.dp)); ExBadge(q.exchange)
            }
            Text(
                (if (showList) "${q.list} · " else "") + (c?.short ?: q.name ?: ""),
                style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        if (edit != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                EditBtn(Icons.Filled.KeyboardArrowUp, "Move up", !edit.first, onClick = edit.up)
                EditBtn(Icons.Filled.KeyboardArrowDown, "Move down", !edit.last, onClick = edit.down)
                EditBtn(Icons.Filled.Delete, "Remove", true, danger = true, onClick = edit.remove)
            }
        } else {
            Column(horizontalAlignment = Alignment.End) {
                FlashPrice(num(q.price), state.flash[q.key])
                Spacer(Modifier.height(4.dp))
                ChangePill(q.change, q.changepct)
            }
        }
    }
}

@Composable
private fun EditBtn(icon: ImageVector, desc: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val x = LocalExtra.current
    Surface(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, x.border), modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, desc, tint = (if (danger) x.down else MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(20.dp))
        }
    }
}

/* ---------- side by side ---------- */

/** One company with its NSE and BSE quotes (either may be missing). */
data class Pair2(val key: String, val company: Company?, val nse: Quote?, val bse: Quote?, val order: Int)

private val COL = 96.dp

@Composable
fun CompareHeader() {
    val x = LocalExtra.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp).padding(bottom = 6.dp)) {
        Text("COMPANY", style = MaterialTheme.typography.labelSmall, color = x.muted, modifier = Modifier.weight(1f))
        Text("NSE", style = MaterialTheme.typography.labelSmall, color = x.muted, textAlign = TextAlign.End, modifier = Modifier.width(COL))
        Text("BSE", style = MaterialTheme.typography.labelSmall, color = x.muted, textAlign = TextAlign.End, modifier = Modifier.width(COL))
    }
}

@Composable
fun CompareRow(state: AppState, pair: Pair2, list: String, onOpen: (Quote) -> Unit) {
    val x = LocalExtra.current
    val any = pair.nse ?: pair.bse ?: return
    val gap = if (pair.nse?.price != null && pair.bse?.price != null) pair.bse.price - pair.nse.price else null
    val lower = when { gap == null || gap == 0.0 -> null; gap > 0 -> "NSE"; else -> "BSE" }
    Column(Modifier.fillMaxWidth().clickable { onOpen(any) }.padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(Companies.label(any.ticker, any.name), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(pair.company?.short ?: any.name ?: "", style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
            Cell(state, pair.nse, lower == "NSE", pair.company?.nse?.takeIf { it.isNotEmpty() }?.let { "NSE:$it" }, list)
            Cell(state, pair.bse, lower == "BSE", pair.company?.bse?.takeIf { it.isNotEmpty() }?.let { "BOM:$it" }, list)
        }
        if (gap != null) {
            Row(
                Modifier.padding(top = 9.dp).clip(RoundedCornerShape(8.dp)).background(x.surface2).padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(SwapIcon, null, tint = x.muted, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(6.dp))
                val pct = abs(gap) / pair.nse!!.price!! * 100
                Text(
                    if (gap == 0.0) "Same price on both exchanges" else "Gap ₹${num(abs(gap))} (${num(pct)}%) · $lower is lower",
                    fontSize = 12.sp, color = x.textSoft,
                )
            }
        }
    }
}

@Composable
private fun Cell(state: AppState, q: Quote?, lowest: Boolean, addTicker: String?, list: String) {
    val x = LocalExtra.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    Box(Modifier.width(COL), contentAlignment = Alignment.CenterEnd) {
        when {
            q != null -> Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (lowest) { Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)); Spacer(Modifier.width(3.dp)) }
                    FlashPrice(num(q.price), state.flash[q.key], size = 15)
                }
                Text(if (q.change == null) "—" else "${arrow(q.change)} ${signPct(q.changepct)}", color = dirColor(q.change), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
            addTicker == null -> Text("Not listed", fontSize = 12.sp, color = x.muted)
            else -> Surface(
                onClick = {
                    if (!busy) scope.launch {
                        busy = true
                        try { state.add(list, addTicker); state.showToast("Added ${if (addTicker.startsWith("NSE")) "NSE" else "BSE"} price") }
                        catch (e: Exception) { state.showToast(e.message ?: "Could not add") }
                        finally { busy = false }
                    }
                },
                shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, x.border),
            ) {
                Row(Modifier.height(30.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (busy) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                    else {
                        Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(15.dp))
                        Text("Add", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
