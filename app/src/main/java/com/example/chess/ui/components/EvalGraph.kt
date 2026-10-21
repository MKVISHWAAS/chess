package com.example.chess.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.chess.ui.theme.EvalGreen
import com.example.chess.ui.theme.EvalRed
import kotlin.math.abs

@Composable
fun EvalGraph(values: List<Int>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    Canvas(modifier.fillMaxWidth().height(120.dp)) {
        if (values.isEmpty()) return@Canvas
        val maxAbs = values.maxOf { abs(it).coerceAtMost(800) }.coerceAtLeast(100).toFloat()
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val mid = size.height / 2
        drawLine(color, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
        val path = Path()
        values.forEachIndexed { i, raw ->
            val v = raw.coerceIn(-800, 800).toFloat()
            val x = i * step
            val y = mid - (v / maxAbs) * (size.height * 0.45f)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, if ((values.lastOrNull() ?: 0) >= 0) EvalGreen else EvalRed, style = Stroke(width = 5f))
    }
}
