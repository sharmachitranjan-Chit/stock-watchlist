package com.chitranjan.watchlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.chitranjan.watchlist.data.AppState
import com.chitranjan.watchlist.util.hm
import com.chitranjan.watchlist.util.ist
import kotlinx.coroutines.launch

@Composable
fun ConnectionScreen(state: AppState, onBack: () -> Unit) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(state.engine.url) }
    var key by remember { mutableStateOf(state.engine.key) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Column(Modifier.fillMaxSize().background(cs.background).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Row(Modifier.padding(horizontal = 16.dp).padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBtn(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
            Text("Price engine", style = MaterialTheme.typography.headlineMedium, color = cs.onBackground, modifier = Modifier.padding(start = 10.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 40.dp)) {
            CardBox {
                Column {
                    Text(if (state.error != null) "Not reachable right now" else "Connected", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(
                        state.error ?: state.data?.let { "Last answer at ${hm(ist(it.at))} IST · ${it.rows.size} stocks in your sheet" } ?: "Waiting for the first answer",
                        style = MaterialTheme.typography.bodyMedium, color = x.muted, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            SectionTitle("Web app link")
            OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                placeholder = { Text("https://script.google.com/macros/s/…/exec") })
            SectionTitle("App key")
            OutlinedTextField(key, { key = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                placeholder = { Text("Settings tab, cell B2") })
            msg?.let { (ok, t) -> Text(t, color = if (ok) x.up else x.down, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(Modifier.height(18.dp))
            PrimaryButton("Test and save", icon = Icons.Filled.Check, busy = busy) {
                if (!Regex("^https://script\\.google\\.com/.+/exec$").matches(url.trim())) { msg = false to "The link should start with https://script.google.com/ and end with /exec"; return@PrimaryButton }
                if (key.isBlank()) { msg = false to "Paste the app key from the Settings tab of your sheet."; return@PrimaryButton }
                scope.launch {
                    busy = true; msg = null
                    try { val s = state.saveEngine(url, key); msg = true to "Connected · ${s?.rows?.size ?: 0} stocks found" }
                    catch (e: Exception) { msg = false to (e.message ?: "Could not connect") }
                    finally { busy = false }
                }
            }
            Spacer(Modifier.height(10.dp))
            PrimaryButton("Use built-in engine", plain = true) {
                scope.launch { state.saveEngine("", ""); url = state.engine.url; key = state.engine.key; msg = true to "Using the built-in engine" }
            }
            Text("You only need this if you redeploy the Apps Script or change the key in the sheet.",
                style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 16.dp))
        }
    }
}
