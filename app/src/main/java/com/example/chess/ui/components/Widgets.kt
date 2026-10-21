package com.example.chess.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.example.chess.domain.engine.evalBar
import com.example.chess.ui.theme.EvalGreen
import com.example.chess.ui.theme.EvalRed

@Composable
fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
fun EvalBar(cp: Int, modifier: Modifier = Modifier) {
    val whiteShare = evalBar(cp).coerceIn(0.02f, 0.98f)
    Column(
        modifier
            .width(18.dp)
            .fillMaxHeight()
            .background(EvalRed, RoundedCornerShape(8.dp)),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f - whiteShare).background(EvalRed))
        Box(Modifier.fillMaxWidth().weight(whiteShare).background(EvalGreen, RoundedCornerShape(8.dp)))
    }
}

@Composable
fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.fillMaxWidth().height(88.dp)) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max().coerceAtLeast(min + 1f)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = size.width * i / (values.lastIndex)
            val y = size.height - ((v - min) / (max - min) * size.height)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f))
        val last = values.last()
        val x = size.width
        val y = size.height - ((last - min) / (max - min) * size.height)
        drawCircle(color, 8f, Offset(x, y))
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
}
