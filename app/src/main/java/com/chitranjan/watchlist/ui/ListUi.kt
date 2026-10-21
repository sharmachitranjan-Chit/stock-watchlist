package com.chitranjan.watchlist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.chitranjan.watchlist.data.AppState
import kotlinx.coroutines.launch

/**
 * The row of list chips with everything you can do to a list: tap to switch, tap the selected one (or long-press
 * any) for Rename and Delete, and "New list" at the end. Used on Watchlist and Insights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListChips(state: AppState, modifier: Modifier = Modifier, onSwitch: () -> Unit = {}) {
    val x = LocalExtra.current
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val active = state.activeList
    var menu by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    val counts = state.data?.rows.orEmpty().groupingBy { it.list }.eachCount()

    Row(modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        state.lists.forEach { l ->
            val on = l == active
            ChipBtn(l, active = on, icon = if (on) Icons.Filled.Create else null, onLongClick = { menu = l }) {
                if (on) menu = l else { state.setActiveList(l); onSwitch() }
            }
        }
        ChipBtn("New list", icon = Icons.Filled.Add) { creating = true }
    }

    menu?.let { l ->
        val n = counts[l] ?: 0
        ModalBottomSheet(onDismissRequest = { menu = null }, sheetState = rememberModalBottomSheetState(), containerColor = cs.surface) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(l, style = MaterialTheme.typography.titleLarge, color = cs.onSurface, modifier = Modifier.padding(horizontal = 24.dp))
                Text("$n ${if (n == 1) "stock" else "stocks"}", style = MaterialTheme.typography.bodyMedium, color = x.muted,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(top = 2.dp, bottom = 10.dp))
                SheetAction(Icons.Filled.Create, "Rename list", cs.onSurface) { menu = null; renaming = l }
                SheetAction(Icons.Filled.Delete, "Delete list", x.down) {
                    menu = null
                    if (state.lists.size <= 1) state.showToast("You need at least one watchlist") else deleting = l
                }
            }
        }
    }

    if (creating) {
        var err by remember { mutableStateOf<String?>(null) }
        NameDialog("New list", "e.g. Banks, Long term", confirm = "Create", error = err, onDismiss = { creating = false }) { name ->
            err = state.createList(name)
            if (err == null) { creating = false; onSwitch(); state.showToast("“${state.activeList}” created") }
        }
    }

    renaming?.let { old -> RenameListDialog(state, old) { renaming = null } }

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
private fun SheetAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Asks for a new name for [old], then renames it in the sheet and on the phone. */
@Composable
fun RenameListDialog(state: AppState, old: String, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    NameDialog("Rename list", "New name", confirm = "Rename", initial = old, error = err, busy = busy, onDismiss = { if (!busy) onDone() }) { name ->
        scope.launch {
            busy = true; err = null
            try { state.renameList(old, name); state.showToast("Renamed to “${cleanName(name)}”"); onDone() }
            catch (e: Exception) { err = e.message ?: "Could not rename" }
            finally { busy = false }
        }
    }
}

private fun cleanName(s: String) = com.chitranjan.watchlist.data.cleanListName(s)

/** A small dialog with one text field, used to name or rename a list. */
@Composable
fun NameDialog(
    title: String, placeholder: String, confirm: String, initial: String = "", error: String? = null, busy: Boolean = false,
    onDismiss: () -> Unit, onSubmit: (String) -> Unit,
) {
    var v by remember { mutableStateOf(initial) }
    val x = LocalExtra.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    v, { if (it.length <= 30) v = it }, placeholder = { Text(placeholder) }, singleLine = true, isError = error != null,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (v.isNotBlank() && !busy) onSubmit(v) }),
                )
                if (error != null) Text(error, color = x.down, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                else Text("${v.length}/30", color = x.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { if (v.isNotBlank() && !busy) onSubmit(v) }, enabled = v.isNotBlank() && !busy) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}
