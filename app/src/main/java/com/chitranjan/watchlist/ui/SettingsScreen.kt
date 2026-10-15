package com.chitranjan.watchlist.ui

import android.content.Intent
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.BuildConfigInfo
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.data.Companies
import com.chitranjan.watchlist.data.CrashLog
import com.chitranjan.watchlist.data.GainLoss
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
    var deleting by remember { mutableStateOf<String?>(null) }
    var crashes by remember { mutableStateOf(CrashLog.reports()) }
    var showCrashes by remember { mutableStateOf(false) }
    val counts = state.data?.rows.orEmpty().groupingBy { it.list }.eachCount()

    Column(Modifier.fillMaxSize().background(cs.background).windowInsetsPadding(WindowInsets.statusBars)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 30.dp)) {

            SectionTitle("Theme")
            CardBox {
                Column {
                    Segmented(listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"), prefs.theme) { v -> state.updatePrefs { it.copy(theme = v) } }
                    Hint("System follows your phone's day and night setting.")
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

            SectionTitle("Watchlists") { Text("+ New list", color = cs.primary, fontWeight = FontWeight.ExtraBold, fontSize = 13.5.sp, modifier = Modifier.clickable { newList = true }) }
            CardBox(padding = 0.dp) {
                Column {
                    state.lists.forEachIndexed { i, l ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.List, null, tint = x.textSoft, modifier = Modifier.size(20.dp))
                            Text(l, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, modifier = Modifier.weight(1f).padding(start = 12.dp))
                            val n = counts[l] ?: 0
                            Text("$n ${if (n == 1) "stock" else "stocks"}", style = MaterialTheme.typography.bodyMedium, color = x.muted)
                            Spacer(Modifier.width(12.dp))
                            Icon(Icons.Filled.Delete, "Delete $l", tint = x.down, modifier = Modifier.size(20.dp).clickable {
                                if (state.lists.size <= 1) state.showToast("You need at least one watchlist") else deleting = l
                            })
                        }
                        if (i < state.lists.size - 1) Divider()
                    }
                }
            }

            SectionTitle("Auto refresh during market hours")
            CardBox {
                Column {
                    Segmented(listOf(30 to "30 sec", 60 to "1 min", 120 to "2 min", 300 to "5 min"), prefs.intervalSec) { v -> state.updatePrefs { it.copy(intervalSec = v) } }
                    Hint("Only while the app is open. Google updates its prices every few minutes, so faster checks won't always show new numbers.")
                }
            }

            SectionTitle("Data")
            CardBox(padding = 0.dp) {
                Column {
                    NavRow("Price engine", if (state.error != null) "Problem" else if (state.usingCustomEngine) "Custom link" else "Connected",
                        if (state.error != null) x.down else x.up, onClick = openConnection)
                    Divider()
                    NavRow("Company list", "${"%,d".format(Companies.all.size)} on NSE + BSE", null, onClick = null)
                }
            }

            SectionTitle("Crash reports")
            CardBox(padding = 0.dp) {
                Column {
                    NavRow("Crash reports", if (crashes.isEmpty()) "None" else "${crashes.size} saved",
                        if (crashes.isEmpty()) null else x.down, onClick = { crashes = CrashLog.reports(); CrashLog.markSeen(); showCrashes = true })
                }
            }
            Hint("If the app closes on its own, the reason is saved here. Share it to find what went wrong.")

            SectionTitle("About")
            CardBox {
                Column {
                    Text("Watchlist ${BuildConfigInfo.version}", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text("A tool made by Chitranjan Sharma. Native Android app (Kotlin, Jetpack Compose, Material 3). Prices from Google Finance through your own Google Sheet. For tracking only, not for trading decisions.",
                        style = MaterialTheme.typography.bodyMedium, color = x.textSoft, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    if (newList) NameDialog("New list", "e.g. Banks, Long term", onDismiss = { newList = false }) { state.createList(it); newList = false; state.showToast("“$it” created") }
    if (showCrashes) CrashDialog(crashes, onClear = { CrashLog.clear(); crashes = emptyList(); showCrashes = false; state.showToast("Crash reports cleared") },
        onDismiss = { showCrashes = false })

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

@Composable
private fun CrashDialog(reports: List<String>, onClear: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var i by remember { mutableStateOf(0) }
    val x = LocalExtra.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (reports.isEmpty()) "No crashes" else "Crash ${i + 1} of ${reports.size}") },
        text = {
            if (reports.isEmpty()) Text("The app has not crashed since the reports were last cleared.")
            else Column {
                if (reports.size > 1) Row(Modifier.padding(bottom = 8.dp)) {
                    Text("‹ Newer", color = if (i > 0) MaterialTheme.colorScheme.primary else x.muted, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = i > 0) { i-- })
                    Spacer(Modifier.weight(1f))
                    Text("Older ›", color = if (i < reports.size - 1) MaterialTheme.colorScheme.primary else x.muted, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable(enabled = i < reports.size - 1) { i++ })
                }
                SelectionContainer {
                    Text(reports[i], fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                        modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()))
                }
            }
        },
        confirmButton = {
            if (reports.isEmpty()) TextButton(onClick = onDismiss) { Text("OK") }
            else TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_SUBJECT, "Watchlist crash report")
                    .putExtra(Intent.EXTRA_TEXT, reports[i])
                context.startActivity(Intent.createChooser(send, "Share crash report"))
            }) { Text("Share") }
        },
        dismissButton = {
            if (reports.isNotEmpty()) Row {
                TextButton(onClick = onClear) { Text("Clear all", color = x.down) }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
