package com.chitranjan.watchlist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chitranjan.watchlist.data.Holding
import com.chitranjan.watchlist.util.num

private fun plain(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/** Enter or clear how many shares you hold and what you paid on average. */
@Composable
fun HoldingDialog(title: String, current: Holding?, lastPrice: Double?, onDismiss: () -> Unit, onSave: (Holding?) -> Unit) {
    val x = LocalExtra.current
    var qty by remember { mutableStateOf(current?.let { plain(it.qty) } ?: "") }
    var avg by remember { mutableStateOf(current?.let { plain(it.avg) } ?: "") }
    val q = qty.replace(',', '.').toDoubleOrNull()
    val a = avg.replace(',', '.').toDoubleOrNull()
    val valid = q != null && q > 0 && a != null && a > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2) },
        text = {
            Column {
                Row {
                    OutlinedTextField(qty, { if (it.length <= 10) qty = it }, label = { Text("Shares") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    OutlinedTextField(avg, { if (it.length <= 12) avg = it }, label = { Text("Avg buy price ₹") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                }
                if (q != null && a != null && valid) Text("Invested ₹${num(q * a)}" + (lastPrice?.let { " · now ₹${num(q * it)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = x.muted, modifier = Modifier.padding(top = 10.dp))
                else Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { if (q != null && a != null && valid) onSave(Holding(q, a)) }, enabled = valid) { Text("Save") } },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null) }) { Text("Remove", color = x.down) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
