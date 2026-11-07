package com.chitranjan.watchlist.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.util.Dir
import com.chitranjan.watchlist.util.arrow
import com.chitranjan.watchlist.util.dirOf
import com.chitranjan.watchlist.util.num
import com.chitranjan.watchlist.util.signPct

val Shape12 = RoundedCornerShape(12.dp)
val Shape16 = RoundedCornerShape(16.dp)

@Composable fun dirColor(c: Double?): Color { val x = LocalExtra.current; return when (dirOf(c)) { Dir.UP -> x.up; Dir.DOWN -> x.down; Dir.FLAT -> x.muted } }
@Composable fun dirSoft(c: Double?): Color { val x = LocalExtra.current; return when (dirOf(c)) { Dir.UP -> x.upSoft; Dir.DOWN -> x.downSoft; Dir.FLAT -> x.surface2 } }

@Composable
fun IconBtn(icon: ImageVector, desc: String, busy: Boolean = false, onClick: () -> Unit) {
    val x = LocalExtra.current
    Surface(
        onClick = onClick, shape = Shape12, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, x.border), modifier = Modifier.size(42.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
            else Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(21.dp))
        }
    }
}

@Composable
fun CardBox(modifier: Modifier = Modifier, padding: Dp = 14.dp, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(), shape = Shape16, color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, LocalExtra.current.border),
    ) { Box(Modifier.padding(padding)) { content() } }
}

@Composable
fun SectionTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalExtra.current.muted, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChipBtn(label: String, active: Boolean = false, icon: ImageVector? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val x = LocalExtra.current
    val shape = CircleShape
    Row(
        Modifier.height(36.dp).clip(shape)
            .background(if (active) cs.primary else cs.surface)
            .border(1.dp, if (active) cs.primary else x.border, shape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(16.dp), tint = if (active) cs.onPrimary else x.textSoft); Spacer(Modifier.width(4.dp)) }
        Text(label, fontSize = 13.5.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, color = if (active) cs.onPrimary else x.textSoft, maxLines = 1)
    }
}

@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val x = LocalExtra.current
    Row(Modifier.fillMaxWidth().clip(Shape12).background(x.surface2).padding(3.dp)) {
        options.forEach { (v, label) ->
            val on = v == selected
            Box(
                Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .then(if (on) Modifier.border(1.dp, x.border, RoundedCornerShape(9.dp)) else Modifier)
                    .clickable { onSelect(v) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.5.sp, maxLines = 1, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) MaterialTheme.colorScheme.onSurface else x.muted)
            }
        }
    }
}

/** ▲ 2.92  +1.25% on a tinted background. */
@Composable
fun ChangePill(change: Double?, pct: Double?, compact: Boolean = false) {
    val text = when { change == null -> "—"; compact -> "${arrow(change)} ${signPct(pct)}"; else -> "${arrow(change)} ${num(kotlin.math.abs(change))}  ${signPct(pct)}" }
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(dirSoft(change)).padding(horizontal = 7.dp, vertical = 3.dp)) {
        Text(text, color = dirColor(change), fontSize = 12.5.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun ChangeText(change: Double?, pct: Double?, size: Int = 14) {
    if (change == null) { Text("—", color = LocalExtra.current.muted); return }
    Text("${arrow(change)} ${num(kotlin.math.abs(change))} (${signPct(pct)})", color = dirColor(change), fontSize = size.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ExBadge(ex: String) {
    val x = LocalExtra.current
    Box(Modifier.clip(RoundedCornerShape(5.dp)).background(x.surface2).padding(horizontal = 5.dp, vertical = 1.dp)) {
        Text(ex, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = x.muted, letterSpacing = 0.4.sp)
    }
}

@Composable
fun PrimaryButton(label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, busy: Boolean = false, plain: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = { if (!busy) onClick() }, shape = Shape12, modifier = modifier.fillMaxWidth().height(50.dp),
        color = if (plain) cs.surface else cs.primary,
        border = if (plain) BorderStroke(1.dp, LocalExtra.current.border) else null,
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            val fg = if (plain) cs.onSurface else cs.onPrimary
            if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = fg)
            else {
                if (icon != null) { Icon(icon, null, tint = fg, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
                Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

/** Where the current price sits between a low and a high. */
@Composable
fun RangeBar(label: String, lo: Double?, hi: Double?, v: Double?) {
    if (lo == null || hi == null || v == null || hi <= lo) return
    val x = LocalExtra.current
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val pc = ((v - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
    Column(Modifier.padding(top = 14.dp)) {
        Row { Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = x.muted, modifier = Modifier.weight(1f)); Text("${(pc * 100).toInt()}% of range", style = MaterialTheme.typography.bodySmall, color = x.muted) }
        Canvas(Modifier.fillMaxWidth().height(16.dp).padding(vertical = 5.dp)) {
            val y = size.height / 2
            drawLine(x.surface2, Offset(0f, y), Offset(size.width, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawLine(primary.copy(alpha = 0.25f), Offset(0f, y), Offset(size.width * pc, y), strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(surface, 7.dp.toPx(), Offset(size.width * pc, y))
            drawCircle(primary, 5.dp.toPx(), Offset(size.width * pc, y))
        }
        Row { Text(num(lo), style = MaterialTheme.typography.bodySmall, color = x.textSoft, modifier = Modifier.weight(1f)); Text(num(hi), style = MaterialTheme.typography.bodySmall, color = x.textSoft) }
    }
}

/** Daily closes as a line with a soft fill, the period's high and low marked. */
@Composable
fun PriceChart(values: List<Double>, height: Dp = 170.dp) {
    val x = LocalExtra.current
    if (values.size < 2) {
        Box(Modifier.fillMaxWidth().height(height), contentAlignment = Alignment.Center) {
            Text("Not enough history for this period", color = x.muted, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val mn = values.min(); val mx = values.max()
    val col = if (values.last() >= values.first()) x.up else x.down
    Box(Modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxWidth().height(height).padding(vertical = 18.dp)) {
            val w = size.width; val h = size.height
            fun px(i: Int) = i.toFloat() / (values.size - 1) * w
            fun py(v: Double) = if (mx == mn) h / 2 else ((1 - (v - mn) / (mx - mn)) * h).toFloat()
            val line = Path().apply { values.forEachIndexed { i, v -> if (i == 0) moveTo(px(i), py(v)) else lineTo(px(i), py(v)) } }
            val fill = Path().apply { addPath(line); lineTo(w, h + 18.dp.toPx()); lineTo(0f, h + 18.dp.toPx()); close() }
            val base = py(values.first())
            drawLine(x.border, Offset(0f, base), Offset(w, base), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
            drawPath(fill, Brush.verticalGradient(listOf(col.copy(alpha = 0.22f), col.copy(alpha = 0f))))
            drawPath(line, col, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text("H ${num(mx)}", fontSize = 11.sp, color = x.muted, modifier = Modifier.align(Alignment.TopEnd))
        Text("L ${num(mn)}", fontSize = 11.sp, color = x.muted, modifier = Modifier.align(Alignment.BottomEnd))
    }
}

@Composable
fun Divider() = HorizontalDivider(thickness = 1.dp, color = LocalExtra.current.border)

/** Price text that briefly glows green or red when it changes. */
@Composable
fun FlashPrice(text: String, flash: com.chitranjan.watchlist.data.Flash?, size: Int = 16) {
    val x = LocalExtra.current
    val bg by animateColorAsState(
        when (flash) { com.chitranjan.watchlist.data.Flash.UP -> x.upSoft; com.chitranjan.watchlist.data.Flash.DOWN -> x.downSoft; null -> Color.Transparent },
        tween(600), label = "flash",
    )
    Text(
        text, fontSize = size.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
        overflow = TextOverflow.Clip, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 4.dp),
    )
}

@Composable
fun Modifier.rowPad(): Modifier = this.padding(horizontal = 14.dp, vertical = LocalExtra.current.rowV)


/** Tap target without the ripple, for small inline icons. */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.then(
    Modifier.composed {
        clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null, onClick = onClick,
        )
    }
)
