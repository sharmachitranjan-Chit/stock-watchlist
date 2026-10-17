package com.chitranjan.watchlist.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitranjan.watchlist.data.IndexLine
import com.chitranjan.watchlist.util.epochDayLabel
import com.chitranjan.watchlist.util.signPct
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ChartLine(val label: String, val color: Color, val line: IndexLine, val dashed: Boolean = false)

/** Index of the last point at or before [day], or -1. */
private fun floorDay(days: IntArray, day: Int): Int {
    var lo = 0; var hi = days.size - 1; var ans = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (days[mid] <= day) { ans = mid; lo = mid + 1 } else hi = mid - 1
    }
    return ans
}

/**
 * Several percent-gain lines on one time axis. Touch or drag to read the value of every line on a day.
 * The first line gets a soft fill under it.
 */
@Composable
fun MultiLineChart(lines: List<ChartLine>, modifier: Modifier = Modifier, height: Dp = 210.dp) {
    val x = LocalExtra.current
    val measurer = rememberTextMeasurer()
    var touch by remember { mutableStateOf<Float?>(null) }
    if (lines.isEmpty()) return

    val minDay = lines.minOf { it.line.days.first() }
    val maxDay = lines.maxOf { it.line.days.last() }
    var lo = min(0.0, lines.minOf { l -> l.line.pct.min() })
    var hi = max(0.0, lines.maxOf { l -> l.line.pct.max() })
    val pad = max((hi - lo) * 0.12, 0.4)
    lo -= pad; hi += pad
    val span = max(maxDay - minDay, 1)

    Column(modifier) {
        // What the finger is on: the date and each line's value that day (latest values when not touching).
        val selDay = touch?.let { minDay + (it.coerceIn(0f, 1f) * span).toInt() }
        Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (selDay == null) "Touch the chart to read a day" else epochDayLabel(selDay),
                fontSize = 12.sp, color = x.muted, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            if (selDay != null) lines.forEach { l ->
                val i = floorDay(l.line.days, selDay)
                if (i >= 0) {
                    Box(Modifier.padding(start = 10.dp).size(7.dp).clip(CircleShape).background(l.color))
                    Text(signPct(l.line.pct[i]), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
        val labelStyle = TextStyle(fontSize = 10.5.sp, color = x.muted)
        Canvas(
            Modifier.fillMaxWidth().height(height)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { touch = (it.x / size.width) },
                        onDragEnd = { touch = null }, onDragCancel = { touch = null },
                        onHorizontalDrag = { c, _ -> touch = (c.position.x / size.width) },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(onPress = { p -> touch = p.x / size.width; tryAwaitRelease(); touch = null })
                },
        ) {
            val w = size.width; val h = size.height
            fun px(day: Int) = (day - minDay).toFloat() / span * w
            fun py(v: Double) = ((hi - v) / (hi - lo) * h).toFloat()

            // Grid: top, middle, bottom values, and a dashed zero line.
            for (v in listOf(hi - pad * 0.4, (hi + lo) / 2, lo + pad * 0.4)) {
                drawLine(x.border.copy(alpha = 0.55f), Offset(0f, py(v)), Offset(w, py(v)), strokeWidth = 1.dp.toPx())
                val t = measurer.measure(signPct(v), labelStyle)
                drawText(t, topLeft = Offset(w - t.size.width, py(v) - t.size.height - 1.dp.toPx()))
            }
            val zero = py(0.0)
            drawLine(x.muted.copy(alpha = 0.7f), Offset(0f, zero), Offset(w, zero), strokeWidth = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))

            lines.forEachIndexed { idx, l ->
                val d = l.line.days; val v = l.line.pct
                val path = Path()
                for (i in d.indices) if (i == 0) path.moveTo(px(d[i]), py(v[i])) else path.lineTo(px(d[i]), py(v[i]))
                if (idx == 0) {
                    val fill = Path().apply {
                        addPath(path)
                        lineTo(px(d.last()), h); lineTo(px(d.first()), h); close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(l.color.copy(alpha = 0.20f), l.color.copy(alpha = 0f))))
                }
                drawPath(
                    path, l.color,
                    style = Stroke(width = if (idx == 0) 2.6.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round,
                        pathEffect = if (l.dashed) PathEffect.dashPathEffect(floatArrayOf(14f, 9f)) else null),
                )
            }

            touch?.let { t ->
                val day = minDay + (t.coerceIn(0f, 1f) * span).toInt()
                val sx = px(day)
                drawLine(x.muted, Offset(sx, 0f), Offset(sx, h), strokeWidth = 1.dp.toPx())
                lines.forEach { l ->
                    val i = floorDay(l.line.days, day)
                    if (i >= 0) {
                        drawCircle(Color.White, 5.5.dp.toPx(), Offset(sx, py(l.line.pct[i])))
                        drawCircle(l.color, 4.dp.toPx(), Offset(sx, py(l.line.pct[i])))
                    }
                }
            }
        }
        if (selDay == null) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(epochDayLabel(minDay), fontSize = 11.sp, color = x.muted)
            Text(epochDayLabel(maxDay), fontSize = 11.sp, color = x.muted)
        } else Spacer(Modifier.height(19.dp))
    }
}

/** A bar from the middle: right in the gain colour, left in the loss colour, scaled so [maxAbs] fills a side. */
@Composable
fun MoveBar(pct: Double?, maxAbs: Double, modifier: Modifier = Modifier) {
    val x = LocalExtra.current
    Canvas(modifier.fillMaxWidth().height(8.dp)) {
        val r = size.height / 2
        drawRoundRect(x.surface2, cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        val mid = size.width / 2
        drawLine(x.border, Offset(mid, 0f), Offset(mid, size.height), strokeWidth = 1.5.dp.toPx())
        if (pct != null && pct != 0.0 && maxAbs > 0) {
            val len = (abs(pct) / maxAbs).coerceAtMost(1.0).toFloat() * mid
            val color = if (pct > 0) x.up else x.down
            val left = if (pct > 0) mid else mid - len
            drawRoundRect(color, topLeft = Offset(left, 0f), size = androidx.compose.ui.geometry.Size(max(len, r), size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        }
    }
}

/** Share of stocks that rose, stayed flat and fell. */
@Composable
fun BreadthBar(up: Int, flat: Int, down: Int, modifier: Modifier = Modifier) {
    val x = LocalExtra.current
    if (up + flat + down == 0) return
    Row(modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(x.surface2)) {
        if (up > 0) Box(Modifier.weight(up.toFloat()).height(8.dp).background(x.up))
        if (flat > 0) Box(Modifier.weight(flat.toFloat()).height(8.dp).background(x.border))
        if (down > 0) Box(Modifier.weight(down.toFloat()).height(8.dp).background(x.down))
    }
}
