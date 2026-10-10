package com.chitranjan.watchlist.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Companies
import com.chitranjan.watchlist.data.Company
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(state: AppState) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    val results = remember(q) { if (q.isBlank()) Companies.popular else Companies.search(q) }
    val list = state.activeList
    val inList = state.data?.rows.orEmpty().filter { it.list == list }.map { it.ticker }.toSet()

    Column(Modifier.fillMaxSize().background(cs.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Text("Search", style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp))
        Row(
            Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(52.dp).background(cs.surface, Shape12)
                .border(1.5.dp, cs.primary, Shape12).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = x.muted, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Search by name, symbol or code", color = x.muted, fontSize = 16.sp, maxLines = 1)
                BasicTextField(
                    q, { q = it }, singleLine = true,
                    textStyle = TextStyle(color = cs.onSurface, fontSize = 16.sp), cursorBrush = SolidColor(cs.primary),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            if (q.isNotEmpty()) Icon(Icons.Filled.Close, "Clear", tint = x.muted, modifier = Modifier.size(20.dp).clickableNoRipple { q = "" })
        }

        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("ADD TO", style = MaterialTheme.typography.labelSmall, color = x.muted, modifier = Modifier.padding(start = 16.dp, end = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(end = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.lists.forEach { l -> ChipBtn(l, active = l == list) { state.setActiveList(l) } }
            }
        }

        Text(
            when { q.isBlank() -> "POPULAR"; results.isEmpty() -> "NO MATCHES"; results.size == 40 -> "40+ RESULTS"; else -> "${results.size} RESULTS" },
            style = MaterialTheme.typography.labelSmall, color = x.muted, modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 8.dp),
        )

        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(results, key = { _, c -> c.isin.ifEmpty { c.nse + c.bse } }) { i, c ->
                Segment(i, results.size) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(c.short, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(c.nse.takeIf { it.isNotEmpty() }?.let { "NSE $it" }, c.bse.takeIf { it.isNotEmpty() }?.let { "BSE $it" }).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = x.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                        }
                        ExchangeButton(state, c, "NSE", inList, list)
                        Spacer(Modifier.width(6.dp))
                        ExchangeButton(state, c, "BSE", inList, list)
                    }
                }
            }
            item {
                Text(
                    if (q.isNotBlank() && results.isEmpty()) "Nothing matches “${q.trim()}”. Try part of the company name, like “baroda” or “tata”."
                    else "${"%,d".format(Companies.all.size)} companies from NSE and BSE · updated weekly",
                    style = MaterialTheme.typography.bodySmall, color = x.muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            }
        }
    }
}

private val busyMap = mutableStateMapOf<String, Boolean>()

@Composable
private fun ExchangeButton(state: AppState, c: Company, ex: String, inList: Set<String>, list: String) {
    val code = if (ex == "NSE") c.nse else c.bse
    if (code.isEmpty()) { Spacer(Modifier.width(58.dp)); return }
    val ticker = (if (ex == "NSE") "NSE:" else "BOM:") + code
    val on = ticker in inList
    val busy = busyMap[ticker] == true
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    Surface(
        onClick = {
            if (busy) return@Surface
            scope.launch {
                busyMap[ticker] = true
                val label = "${c.nse.ifEmpty { c.short }} ($ex)"
                try {
                    if (on) { state.remove(list, ticker); state.showToast("$label removed from $list") }
                    else { state.add(list, ticker); state.showToast("$label added to $list") }
                } catch (e: Exception) { state.showToast(e.message ?: "Could not update") }
                finally { busyMap.remove(ticker) }
            }
        },
        shape = RoundedCornerShape(10.dp), color = if (on) cs.primary else cs.surface,
        border = BorderStroke(1.dp, if (on) cs.primary else LocalExtra.current.border),
        modifier = Modifier.width(58.dp).height(34.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (busy) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp, color = if (on) cs.onPrimary else cs.primary)
            else {
                Icon(if (on) Icons.Filled.Check else Icons.Filled.Add, null, tint = if (on) cs.onPrimary else cs.primary, modifier = Modifier.size(15.dp))
                Text(ex, color = if (on) cs.onPrimary else cs.primary, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp)
            }
        }
    }
}
