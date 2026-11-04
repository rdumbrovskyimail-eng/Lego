package com.legode.app.ui.synergy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.legode.app.data.model.CbtEntry
import com.legode.app.data.model.WorkoutEntry

@Composable
fun SynergyScreen(
    cbtEntries: List<CbtEntry>,
    workouts: List<WorkoutEntry>
) {
    val totalCbt = cbtEntries.size
    val totalWorkouts = workouts.size
    val totalTonnage = workouts.sumOf { it.totalTonnage.toDouble() }

    val avgRelief = if (cbtEntries.isNotEmpty()) {
        cbtEntries.map { it.intensityBefore - it.intensityAfter }.average().toInt()
    } else 0

    val cbtDates = cbtEntries.map { it.date }.toSet()
    val sportDates = workouts.map { it.date }.toSet()
    val synergyDays = cbtDates.intersect(sportDates).size

    // Частота искажений
    val distortionFrequency = mutableMapOf<String, Int>()
    cbtEntries.forEach { entry ->
        entry.distortions.forEach { d ->
            distortionFrequency[d] = (distortionFrequency[d] ?: 0) + 1
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Синергия «Разум и Тело»",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = "Доказательная взаимосвязь физической нагрузки и когнитивной гибкости",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    title = "КПТ-СЕЙВЫ",
                    value = "$totalCbt",
                    sub = "Спад тревоги: -$avgRelief%",
                    accent = Color(0xFFA855F7),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "ТРЕНИРОВКИ",
                    value = "$totalWorkouts",
                    sub = "Объем: ${totalTonnage.toInt()} кг",
                    accent = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
                    .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Дни активной синергии (КПТ + Спорт)",
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "$synergyDays дней",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    )
                    Text(
                        text = "Силовые нагрузки активируют BDNF и снижают кортизол, повышая восприимчивость префронтальной коры к рациональным аргументам на 40%.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF111827))
                    .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Частота когнитивных ошибок",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                if (distortionFrequency.isEmpty()) {
                    Text(
                        text = "Заполните протоколы КПТ, чтобы увидеть спектр ошибок мышления.",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp
                    )
                } else {
                    distortionFrequency.entries.sortedByDescending { it.value }.forEach { (distortion, count) ->
                        val percent = (count.toFloat() / totalCbt * 100).toInt()
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = distortion, color = Color(0xFFCBD5E1), fontSize = 12.sp)
                                Text(text = "$count ($percent%)", color = Color(0xFFA855F7), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF1F2937))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(count.toFloat() / totalCbt.coerceAtLeast(1))
                                        .height(4.dp)
                                        .background(Color(0xFFA855F7))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, sub: String, accent: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF1F2937), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = title, color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text(text = value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(text = sub, color = Color(0xFF94A3B8), fontSize = 11.sp)
    }
}