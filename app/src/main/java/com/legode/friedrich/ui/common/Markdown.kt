package com.legode.friedrich.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Лёгкий Markdown для СМС Фридриха и шпаргалок справочника:
 * **жирный**, *курсив*, `код`, ~~зачёркнуто~~, списки, заголовки, таблицы.
 * Подчёркивания не трогаем: «___» — это пропуск в упражнении.
 */

sealed class MdBlock {
    data class Para(val text: AnnotatedString) : MdBlock()
    data class Heading(val level: Int, val text: AnnotatedString) : MdBlock()
    data class Bullets(val items: List<Pair<String, AnnotatedString>>) : MdBlock()
    data class Table(val header: List<AnnotatedString>?, val rows: List<List<AnnotatedString>>, val widths: List<Int>) : MdBlock()
    object Rule : MdBlock()
}

data class MdColors(val bold: Color, val code: Color, val codeBg: Color)

object Md {
    private val BULLET = Regex("^\\s*[-•*]\\s+(.*)$")
    private val NUMBERED = Regex("^\\s*(\\d{1,2})[.)]\\s+(.*)$")
    private val HEADING = Regex("^(#{1,6})\\s+(.*)$")
    private val SEPARATOR_CELL = Regex("^:?-{2,}:?$")

    fun inline(src: String, c: MdColors): AnnotatedString = buildAnnotatedString {
        var i = 0
        val n = src.length
        while (i < n) {
            val ch = src[i]
            when {
                src.startsWith("**", i) -> {
                    val end = src.indexOf("**", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = c.bold)) {
                            append(inline(src.substring(i + 2, end), c))
                        }
                        i = end + 2
                    } else {
                        append("**")
                        i += 2
                    }
                }

                src.startsWith("~~", i) -> {
                    val end = src.indexOf("~~", i + 2)
                    if (end > i + 2) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(inline(src.substring(i + 2, end), c))
                        }
                        i = end + 2
                    } else {
                        append("~~")
                        i += 2
                    }
                }

                ch == '`' -> {
                    val end = src.indexOf('`', i + 1)
                    if (end > i + 1) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = c.code, background = c.codeBg)) {
                            append(src.substring(i + 1, end))
                        }
                        i = end + 1
                    } else {
                        append(ch)
                        i++
                    }
                }

                ch == '*' -> {
                    val end = src.indexOf('*', i + 1)
                    if (end > i + 1 && !src[i + 1].isWhitespace() && !src[end - 1].isWhitespace()) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(inline(src.substring(i + 1, end), c))
                        }
                        i = end + 1
                    } else {
                        append(ch)
                        i++
                    }
                }

                else -> {
                    append(ch)
                    i++
                }
            }
        }
    }

    private fun cells(line: String): List<String> {
        var s = line.trim()
        if (s.startsWith("|")) s = s.substring(1)
        if (s.endsWith("|")) s = s.substring(0, s.length - 1)
        return s.split('|').map { it.trim() }
    }

    fun parse(src: String, c: MdColors): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        val lines = src.replace("\r", "").split('\n')
        val para = StringBuilder()
        var bullets = ArrayList<Pair<String, AnnotatedString>>()

        fun flushPara() {
            if (para.isNotEmpty()) {
                out.add(MdBlock.Para(inline(para.toString().trimEnd(), c)))
                para.setLength(0)
            }
        }

        fun flushBullets() {
            if (bullets.isNotEmpty()) {
                out.add(MdBlock.Bullets(bullets))
                bullets = ArrayList()
            }
        }

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trimEnd()
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                flushPara()
                flushBullets()
                i++
                continue
            }

            // Таблица: подряд идущие строки, начинающиеся с «|».
            if (trimmed.startsWith("|") && trimmed.indexOf('|', 1) > 0) {
                flushPara()
                flushBullets()
                val rows = ArrayList<List<String>>()
                var hasHeader = false
                while (i < lines.size && lines[i].trim().startsWith("|")) {
                    val row = cells(lines[i])
                    if (row.isNotEmpty() && row.all { SEPARATOR_CELL.matches(it) || it.isEmpty() } && row.any { it.isNotEmpty() }) {
                        if (rows.size == 1) hasHeader = true
                    } else {
                        rows.add(row)
                    }
                    i++
                }
                if (rows.isNotEmpty()) {
                    val cols = rows.maxOf { it.size }
                    val widths = IntArray(cols)
                    for (r in rows) {
                        for ((k, cell) in r.withIndex()) {
                            val len = cell.replace("**", "").replace("*", "").length
                            if (len > widths[k]) widths[k] = len
                        }
                    }
                    val styled = rows.map { r -> (0 until cols).map { k -> inline(r.getOrElse(k) { "" }, c) } }
                    out.add(
                        if (hasHeader) MdBlock.Table(styled.first(), styled.drop(1), widths.toList())
                        else MdBlock.Table(null, styled, widths.toList())
                    )
                }
                continue
            }

            val heading = HEADING.find(trimmed)
            if (heading != null) {
                flushPara()
                flushBullets()
                out.add(MdBlock.Heading(heading.groupValues[1].length, inline(heading.groupValues[2], c)))
                i++
                continue
            }

            if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
                flushPara()
                flushBullets()
                out.add(MdBlock.Rule)
                i++
                continue
            }

            val numbered = NUMBERED.find(line)
            if (numbered != null) {
                flushPara()
                bullets.add((numbered.groupValues[1] + ".") to inline(numbered.groupValues[2], c))
                i++
                continue
            }

            val bullet = BULLET.find(line)
            if (bullet != null && !trimmed.startsWith("**")) {
                flushPara()
                bullets.add("•" to inline(bullet.groupValues[1], c))
                i++
                continue
            }

            flushBullets()
            if (para.isNotEmpty()) para.append('\n')
            para.append(trimmed)
            i++
        }
        flushPara()
        flushBullets()
        return out
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color,
    style: TextStyle,
    boldColor: Color = Color.Unspecified,
    lineColor: Color = Color(0x33FFFFFF),
    headerBg: Color = Color(0x14FFFFFF)
) {
    val colors = remember(boldColor, color) {
        MdColors(bold = boldColor, code = color, codeBg = Color(0x22FFFFFF))
    }
    val blocks = remember(text, colors) { Md.parse(text, colors) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (b in blocks) {
            when (b) {
                is MdBlock.Para -> Text(text = b.text, color = color, style = style)

                is MdBlock.Heading -> Text(
                    text = b.text,
                    color = color,
                    style = style.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (b.level <= 2) style.fontSize * 1.18f else style.fontSize * 1.06f
                    )
                )

                is MdBlock.Bullets -> Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for ((mark, item) in b.items) {
                        Row {
                            Text(text = mark, color = color.copy(alpha = 0.7f), style = style, modifier = Modifier.width(22.dp))
                            Text(text = item, color = color, style = style)
                        }
                    }
                }

                is MdBlock.Table -> MdTable(b, color, style, lineColor, headerBg)

                MdBlock.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(lineColor)
                )
            }
        }
    }
}

@Composable
private fun MdTable(t: MdBlock.Table, color: Color, style: TextStyle, lineColor: Color, headerBg: Color) {
    val cellStyle = style.copy(fontSize = style.fontSize * 0.92f, lineHeight = (style.fontSize.value * 0.92f * 1.3f).sp)
    val perChar = cellStyle.fontSize.value * 0.56f
    val widths = t.widths.map { (it.coerceIn(3, 30) * perChar + 18f).coerceIn(48f, 250f).dp }
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .horizontalScroll(rememberScrollState())
            .clip(shape)
            .border(1.dp, lineColor, shape)
    ) {
        val all = ArrayList<Pair<List<AnnotatedString>, Boolean>>()
        t.header?.let { all.add(it to true) }
        for (r in t.rows) all.add(r to false)
        for ((idx, pair) in all.withIndex()) {
            val row = pair.first
            val isHeader = pair.second
            if (idx > 0) {
                Box(
                    Modifier
                        .width(widths.fold(0.dp) { a, w -> a + w } + (widths.size - 1).coerceAtLeast(0).dp)
                        .height(1.dp)
                        .background(lineColor)
                )
            }
            Row(
                Modifier
                    .height(IntrinsicSize.Min)
                    .background(if (isHeader) headerBg else Color.Transparent)
            ) {
                for ((k, cell) in row.withIndex()) {
                    if (k > 0) {
                        Box(
                            Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(lineColor)
                        )
                    }
                    Text(
                        text = cell,
                        color = color,
                        style = if (isHeader) cellStyle.copy(fontWeight = FontWeight.SemiBold) else cellStyle,
                        modifier = Modifier
                            .width(widths.getOrElse(k) { 80.dp })
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
