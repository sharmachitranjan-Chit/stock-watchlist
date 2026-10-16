package com.chitranjan.watchlist.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.BuildConfigInfo
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Companies
import com.chitranjan.watchlist.data.Density
import com.chitranjan.watchlist.data.GainLoss
import com.chitranjan.watchlist.data.Period
import com.chitranjan.watchlist.data.StartTab
import com.chitranjan.watchlist.data.TextSize
import com.chitranjan.watchlist.data.Palette
import com.chitranjan.watchlist.data.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: AppState, openConnection: () -> Unit) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val prefs = state.prefs
    val scope = rememberCoroutineScope()
    var newList by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var clearing by remember { mutableStateOf(false) }
    val counts = state.data?.rows.orEmpty().groupingBy { it.list }.eachCount()

    Column(Modifier.fillMaxSize().background(cs.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp)) {

            SectionTitle("Theme")
            CardBox {
                Column {
                    Segmented(listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), prefs.theme) { v -> state.updatePrefs { it.copy(theme = v) } }
                    Hint("System follows your phone's day and night setting.")
                    Spacer(Modifier.height(6.dp))
                    SwitchRow("Pure black dark theme", "Deeper blacks for OLED screens. Applies when the dark theme is on.", prefs.pureBlack) { v -> state.updatePrefs { it.copy(pureBlack = v) } }
                }
            }

            SectionTitle("Colour palette")
            CardBox {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        Palette.entries.forEach { p ->
                            if (p == Palette.YOU && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@forEach
                            val on = prefs.palette == p
                            Column(Modifier.weight(1f).clickable { state.updatePrefs { it.copy(palette = p) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier.size(46.dp).clip(CircleShape).border(if (on) 2.5.dp else 1.dp, if (on) cs.primary else x.border, CircleShape).padding(4.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (p == Palette.YOU) Brush.sweepGradient(listOf(Color(0xFF6750A4), Color(0xFF0B7A6E), Color(0xFFB84A0C), Color(0xFF1D5BD8), Color(0xFF6750A4)))
                                            else Brush.linearGradient(listOf(accentPreview(p, x.dark), accentPreview(p, x.dark)))
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) { if (on) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(20.dp)) }
                                Text(if (p == Palette.YOU) "Wallpaper" else p.label, fontSize = 11.sp, maxLines = 1, softWrap = false, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium,
                                    color = if (on) cs.onSurface else x.muted, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Hint("Wallpaper picks the app's accent colour from your phone's wallpaper (Material You).")
                }
            }

            SectionTitle("Gain and loss colours")
            CardBox {
                Column {
                    Segmented(listOf(GainLoss.CLASSIC to "Green / Red", GainLoss.COLORBLIND to "Blue / Orange"), prefs.gainLoss) { v -> state.updatePrefs { it.copy(gainLoss = v) } }
                    Row(Modifier.padding(top = 12.dp)) {
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(x.upSoft).padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text("▲ +1.25%", color = x.up, fontWeight = FontWeight.ExtraBold) }
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(x.downSoft).padding(vertical = 8.dp), contentAlignment = Alignment.Center) { Text("▼ −0.60%", color = x.down, fontWeight = FontWeight.ExtraBold) }
                    }
                    Hint("Blue and orange are easier to tell apart for colour-blind eyes.")
                }
            }

            SectionTitle("Text size")
            CardBox {
                Column {
                    Segmented(TextSize.entries.map { it to it.label }, prefs.textSize) { v -> state.updatePrefs { it.copy(textSize = v) } }
                    Hint("Makes all text in the app smaller or larger, on top of your phone's own font size.")
                }
            }

            SectionTitle("List density")
            CardBox {
                Column {
                    Segmented(Density.entries.map { it to it.label }, prefs.density) { v -> state.updatePrefs { it.copy(density = v) } }
                    Hint("Compact fits more stocks on the screen.")
                }
            }

            SectionTitle("Watchlists") { Text("+ New list", color = cs.primary, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, modifier = Modifier.clickable { newList = true }) }
            CardBox(padding = 0.dp) {
                Column {
                    state.lists.forEachIndexed { i, l ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.List, null, tint = x.textSoft, modifier = Modifier.size(20.dp))
                            Text(l, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            val n = counts[l] ?: 0
                            Text("$n ${if (n == 1) "stock" else "stocks"}", style = MaterialTheme.typography.bodyMedium, color = x.muted)
                            Spacer(Modifier.width(8.dp))
                            IconAction(Icons.Filled.Create, "Rename $l", x.textSoft) { renaming = l }
                            IconAction(Icons.Filled.Delete, "Delete $l", x.down) {
                                if (state.lists.size <= 1) state.showToast("You need at least one watchlist") else deleting = l
                            }
                        }
                        if (i < state.lists.size - 1) Divider()
                    }
                }
            }

            SectionTitle("Auto refresh during market hours")
            CardBox {
                Column {
                    Segmented(listOf(0 to "Off", 30 to "30s", 60 to "1 min", 120 to "2 min", 300 to "5 min"), prefs.intervalSec) { v -> state.updatePrefs { it.copy(intervalSec = v) } }
                    Hint("Only while the app is open. Google updates its prices every few minutes, so faster checks won't always show new numbers. Pull down on any list to refresh by hand.")
                }
            }

            SectionTitle("Opening screen")
            CardBox {
                Column {
                    Segmented(StartTab.entries.map { it to it.label }, prefs.startTab) { v -> state.updatePrefs { it.copy(startTab = v) } }
                    Hint("The tab the app opens on.")
                }
            }

            SectionTitle("Insights")
            CardBox {
                Column {
                    Segmented(Period.entries.map { it to it.label }, prefs.period) { v -> state.updatePrefs { it.copy(period = v) } }
                    Hint("The period shown first in the performance charts.")
                }
            }

            SectionTitle("Privacy")
            CardBox {
                SwitchRow("Hide amounts", "Shows ₹ •••• instead of your holding values and profit and loss. Percentages stay visible.", prefs.hideAmounts) { v -> state.updatePrefs { it.copy(hideAmounts = v) } }
            }

            SectionTitle("Data")
            CardBox(padding = 0.dp) {
                Column {
                    NavRow("Price engine", if (state.error != null) "Problem" else if (state.usingCustomEngine) "Custom link" else "Connected",
                        if (state.error != null) x.down else x.up, onClick = openConnection)
                    Divider()
                    NavRow("Company list", "${"%,d".format(Companies.all.size)} on NSE + BSE", null, onClick = null)
                    Divider()
                    NavRow("Saved price history", "Clear", null, onClick = { clearing = true })
                }
            }

            SectionTitle("About")
            CardBox {
                Column {
                    Text("Watchlist ${BuildConfigInfo.version}", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text("A tool made by Chitranjan Sharma. Native Android app (Kotlin, Jetpack Compose, Material 3). Prices from Google Finance through your own Google Sheet; sectors from NSE's industry classification. For tracking only, not for trading decisions.",
                        style = MaterialTheme.typography.bodyMedium, color = x.textSoft, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    if (newList) {
        var err by remember { mutableStateOf<String?>(null) }
        NameDialog("New list", "e.g. Banks, Long term", confirm = "Create", error = err, onDismiss = { newList = false }) { name ->
            err = state.createList(name)
            if (err == null) { newList = false; state.showToast("“${state.activeList}” created") }
        }
    }
    renaming?.let { old -> RenameListDialog(state, old) { renaming = null } }
    if (clearing) AlertDialog(
        onDismissRequest = { clearing = false },
        title = { Text("Clear saved history") },
        text = { Text("Removes the price history kept on this phone for the Insights charts. It downloads again the next time you open Insights.") },
        confirmButton = { TextButton(onClick = { clearing = false; state.clearHistory(); state.showToast("History cleared") }) { Text("Clear", color = x.down) } },
        dismissButton = { TextButton(onClick = { clearing = false }) { Text("Cancel") } },
    )
    deleting?.let { l ->
        val n = counts[l] ?: 0
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete list") },
            text = { Text("Delete “$l” and the $n ${if (n == 1) "stock" else "stocks"} in it?") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = null
                    scope.launch { runCatching { state.deleteList(l) }.onSuccess { state.showToast("“$l” deleted") }.onFailure { state.showToast(it.message ?: "Could not delete") } }
                }) { Text("Delete", color = x.down) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IconAction(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val x = LocalExtra.current
    Row(Modifier.fillMaxWidth().clip(Shape12).clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 2.dp))
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary, checkedThumbColor = MaterialTheme.colorScheme.onPrimary))
    }
}

@Composable
private fun Hint(text: String) = Text(text, style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.muted, modifier = Modifier.padding(top = 10.dp))

@Composable
private fun NavRow(title: String, value: String, valueColor: Color?, onClick: (() -> Unit)?) {
    val x = LocalExtra.current
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).heightIn(min = 54.dp).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor ?: x.muted, fontWeight = if (valueColor != null) FontWeight.Bold else FontWeight.Normal)
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = x.muted, modifier = Modifier.padding(start = 4.dp).size(20.dp))
    }
}
