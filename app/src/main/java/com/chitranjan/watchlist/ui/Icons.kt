package com.chitranjan.watchlist.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Two small line icons that the core icon set doesn't have. 24×24, drawn as strokes. */
private fun strokeIcon(name: String, vararg paths: String): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/** Rising line: the Markets tab. */
val MarketsIcon: ImageVector = strokeIcon("markets", "M3,17 L9,11 L13,15 L21,7", "M15,7 L21,7 L21,13")

/** Two opposite arrows: NSE ⇄ BSE. */
val SwapIcon: ImageVector = strokeIcon("swap", "M4,8 L18,8", "M14,4 L18,8 L14,12", "M20,16 L6,16", "M10,12 L6,16 L10,20")

/** Three bars of different heights: the Insights tab. */
val InsightsIcon: ImageVector = strokeIcon("insights", "M5,20 L5,13", "M12,20 L12,5", "M19,20 L19,10")
