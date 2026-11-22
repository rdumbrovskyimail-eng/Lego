@file:OptIn(ExperimentalMaterial3Api::class)

package com.legode.friedrich.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legode.friedrich.brain.RadarScores
import com.legode.friedrich.brain.Srs
import com.legode.friedrich.core.Timex
import com.legode.friedrich.ui.theme.Fx
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Кружок с эмодзи — «аватар» беседы или режима. */
@Composable
fun EmojiAvatar(emoji: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Fx.CardHigh),
        contentAlignment = Alignment.Center
    ) {
        Text(emoji, fontSize = (size.value * 0.46f).sp)
    }
}

/** Верхняя панель экрана: «назад», заголовок, действия. */
@Composable
fun TopBar(
    title: String,
    subtitle: String = "",
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Fx.Panel)
            .statusBarsPadding()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = Fx.Text)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Fx.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Fx.TextDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

/** Глобальный мини-бар, позволяющий остановить TTS с любого экрана приложения. */
@Composable
fun SpeakingBar(speaking: Boolean, onStop: () -> Unit) {
    AnimatedVisibility(visible = speaking) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Fx.GoldDim)
                .clickable(onClick = onStop)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = Fx.Gold, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Атлас читает вслух…", color = Fx.Gold, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.Stop, contentDescription = "Остановить", tint = Fx.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("Стоп", color = Fx.Gold, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    var m = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(Fx.Card)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(14.dp), content = content)
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(start = 4.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = Fx.TextDim,
        letterSpacing = 1.sp
    )
}

@Composable
fun Pill(text: String, color: Color = Fx.TextDim, bg: Color = Fx.CardHigh, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1
    )
}

@Composable
fun Meter(percent: Int, modifier: Modifier = Modifier, color: Color = Fx.Gold, height: Dp = 6.dp) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Fx.Line)
    ) {
        Box(
            Modifier
                .fillMaxWidth(percent.coerceIn(0, 100) / 100f)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color)
        )
    }
}

@Composable
fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Fx.Ink)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) Fx.Gold else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (on) Fx.OnGold else Fx.TextDim,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun Chip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    val bg = when {
        selected -> Fx.Gold
        else -> Fx.CardHigh
    }
    val fg = if (selected) Fx.OnGold else if (accent) Fx.Gold else Fx.Text
    var m = modifier
        .clip(shape)
        .background(bg)
    if (accent && !selected) m = m.border(1.dp, Fx.Gold.copy(alpha = 0.6f), shape)
    Text(
        text,
        modifier = m
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        color = fg,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    danger: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirm, color = if (danger) Fx.Red else Fx.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fx.TextDim) } },
        containerColor = Fx.Card
    )
}

@Composable
fun InputDialog(
    title: String,
    initial: String = "",
    label: String = "",
    confirm: String = "Сохранить",
    multiline: Boolean = false,
    hint: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (hint.isNotEmpty()) {
                    Text(hint, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = if (label.isNotEmpty()) ({ Text(label) }) else null,
                    singleLine = !multiline,
                    minLines = if (multiline) 5 else 1,
                    maxLines = if (multiline) 12 else 1,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(value.trim())
                    onDismiss()
                },
                enabled = value.isNotBlank()
            ) { Text(confirm, color = Fx.Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fx.TextDim) } },
        containerColor = Fx.Card
    )
}

@Composable
fun SettingRow(
    title: String,
    subtitle: String = "",
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    var m: Modifier = Modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Fx.Text, style = MaterialTheme.typography.bodyLarge)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, color = Fx.TextDim, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

// ───────────────────────── Аналитические графики (Canvas) ─────────────────────────

/** 6-осевой Радар интеллекта и устойчивости без сторонних библиотек. */
@Composable
fun RadarChart(scores: RadarScores, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axes = remember(scores) {
        listOf(
            "Опора" to scores.resilience,
            "Критика" to scores.critical,
            "Системы" to scores.systems,
            "Решения" to scores.calibration,
            "Память" to scores.learning,
            "Воля" to scores.execution
        )
    }
    val labelStyle = TextStyle(color = Fx.TextDim, fontSize = 11.sp, fontWeight = FontWeight.Medium)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(235.dp)
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = minOf(size.width, size.height) * 0.34f
        val step = (2.0 * Math.PI / 6.0)

        fun point(idx: Int, ratio: Float): Offset {
            val angle = -Math.PI / 2.0 + idx * step
            return Offset(
                x = cx + (radius * ratio * cos(angle)).toFloat(),
                y = cy + (radius * ratio * sin(angle)).toFloat()
            )
        }

        // Концентрические шестиугольники
        for (ring in listOf(0.25f, 0.50f, 0.75f, 1.0f)) {
            val p = Path()
            for (i in 0 until 6) {
                val pt = point(i, ring)
                if (i == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
            }
            p.close()
            drawPath(p, color = Fx.Line, style = Stroke(width = if (ring == 1f) 1.5.dp.toPx() else 1.dp.toPx()))
        }

        // Лучи осей
        for (i in 0 until 6) {
            drawLine(color = Fx.Line, start = Offset(cx, cy), end = point(i, 1f), strokeWidth = 1.dp.toPx())
        }

        // Полигон текущих показателей пользователя
        val dataPath = Path()
        val pts = ArrayList<Offset>(6)
        axes.forEachIndexed { i, (_, value) ->
            val ratio = value.coerceIn(5, 100) / 100f
            val pt = point(i, ratio)
            pts.add(pt)
            if (i == 0) dataPath.moveTo(pt.x, pt.y) else dataPath.lineTo(pt.x, pt.y)
        }
        dataPath.close()
        drawPath(dataPath, color = Fx.Gold.copy(alpha = 0.24f))
        drawPath(dataPath, color = Fx.Gold, style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round))

        for (pt in pts) {
            drawCircle(color = Fx.Gold, radius = 4.dp.toPx(), center = pt)
        }

        // Подписи осей по кругу
        axes.forEachIndexed { i, (title, value) ->
            val angle = -Math.PI / 2.0 + i * step
            val labelR = radius + 24.dp.toPx()
            val text = "$title $value%"
            val res = measurer.measure(text, style = labelStyle)
            val rawX = cx + (labelR * cos(angle)).toFloat() - res.size.width / 2f
            val rawY = cy + (labelR * sin(angle)).toFloat() - res.size.height / 2f
            val safeX = rawX.coerceIn(2f, (size.width - res.size.width - 2f).coerceAtLeast(2f))
            val safeY = rawY.coerceIn(2f, (size.height - res.size.height - 2f).coerceAtLeast(2f))
            drawText(res, topLeft = Offset(safeX, safeY))
        }
    }
}

/** Кривая калибровки прогнозов (идеальная диагональ vs реальная точность пользователя). */
@Composable
fun CalibrationChart(stats: Srs.CalibrationStats, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val axisStyle = TextStyle(color = Fx.TextFaint, fontSize = 10.sp)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(175.dp)
    ) {
        val left = 32.dp.toPx()
        val right = size.width - 14.dp.toPx()
        val top = 14.dp.toPx()
        val bottom = size.height - 24.dp.toPx()
        val w = (right - left).coerceAtLeast(1f)
        val h = (bottom - top).coerceAtLeast(1f)

        fun xy(px: Double, py: Double): Offset = Offset(
            x = left + (px.coerceIn(0.0, 1.0) * w).toFloat(),
            y = bottom - (py.coerceIn(0.0, 1.0) * h).toFloat()
        )

        // Сетка
        for (pct in listOf(0.0, 0.25, 0.5, 0.75, 1.0)) {
            val y = bottom - (pct * h).toFloat()
            drawLine(Fx.Line, Offset(left, y), Offset(right, y), strokeWidth = 1.dp.toPx())
            val lbl = measurer.measure("${(pct * 100).toInt()}%", style = axisStyle)
            drawText(lbl, topLeft = Offset(2f, y - lbl.size.height / 2f))
        }

        // Идеальная диагональ калибровки
        drawLine(
            color = Fx.TextFaint,
            start = xy(0.0, 0.0),
            end = xy(1.0, 1.0),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )

        val activeBins = stats.bins.filter { it.count > 0 }
        if (activeBins.size >= 2) {
            val path = Path()
            activeBins.forEachIndexed { idx, b ->
                val pt = xy(b.expectedProb, b.observedFreq)
                if (idx == 0) path.moveTo(pt.x, pt.y) else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, color = Fx.Gold, style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round))
        }

        for (b in activeBins) {
            val pt = xy(b.expectedProb, b.observedFreq)
            val err = abs(b.expectedProb - b.observedFreq)
            val dotColor = when {
                err <= 0.10 -> Fx.Green
                err <= 0.20 -> Fx.Gold
                else -> Fx.Red
            }
            drawCircle(color = dotColor, radius = 5.dp.toPx(), center = pt)
            val tag = measurer.measure("n=${b.count}", style = axisStyle.copy(color = Fx.Text))
            drawText(tag, topLeft = Offset((pt.x - tag.size.width / 2f).coerceIn(left, right - tag.size.width), (pt.y - 16.dp.toPx()).coerceAtLeast(2f)))
        }

        for (pct in listOf(0.0, 0.5, 1.0)) {
            val x = left + (pct * w).toFloat()
            val lbl = measurer.measure("${(pct * 100).toInt()}%", style = axisStyle)
            drawText(lbl, topLeft = Offset((x - lbl.size.width / 2f).coerceIn(left, right - lbl.size.width), bottom + 4.dp.toPx()))
        }
    }
}

/** Тепловая карта 7 дней × 4 фазы суток (Утро / День / Вечер / Ночь). */
@Composable
fun ActivityHeatmap(grid: Array<IntArray>, modifier: Modifier = Modifier) {
    val periods = listOf("Утро", "День", "Вечер", "Ночь")
    val peak = remember(grid) {
        var m = 1
        for (row in grid) for (v in row) if (v > m) m = v
        m
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Spacer(Modifier.width(44.dp))
            for (d in Timex.weekdaysShortRu) {
                Text(d, color = Fx.TextFaint, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        periods.forEachIndexed { periodIdx, label ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Fx.TextDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(44.dp))
                for (dayIdx in 0 until 7) {
                    val count = grid.getOrNull(dayIdx)?.getOrNull(periodIdx) ?: 0
                    val alpha = if (count == 0) 0f else (0.22f + 0.78f * (count.toFloat() / peak)).coerceIn(0.2f, 1f)
                    val bg = if (count == 0) Fx.Ink else Fx.Gold.copy(alpha = alpha)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(22.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(bg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (count > 0) {
                            Text(
                                count.toString(),
                                color = if (alpha > 0.6f) Fx.OnGold else Fx.Text,
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}