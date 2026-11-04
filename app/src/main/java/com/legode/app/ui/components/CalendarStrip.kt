package com.legode.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@Composable
fun CalendarStrip(
    selectedDateStr: String,
    cbtDates: List<String>,
    sportDates: List<String>,
    onDateSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val selectedDate = runCatching { LocalDate.parse(selectedDateStr, formatter) }.getOrDefault(LocalDate.now())

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Показываем окно: 3 дня до, текущий, 3 дня после
        for (i in -3..3) {
            val date = selectedDate.plusDays(i.toLong())
            val dateStr = date.format(formatter)
            val isSelected = dateStr == selectedDateStr
            val hasCbt = cbtDates.contains(dateStr)
            val hasSport = sportDates.contains(dateStr)

            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale("ru"))
            val dayNumber = date.dayOfMonth.toString()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) Color(0xFF1E293B) else Color(0xFF0F172A))
                    .border(
                        width = 1.dp,
                        color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF1E293B),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .clickable { onDateSelected(date) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = dayOfWeek.uppercase(),
                    fontSize = 10.sp,
                    color = if (isSelected) Color(0xFF38BDF8) else Color(0xFF64748B),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = dayNumber,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.height(6.dp)
                ) {
                    if (hasCbt) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFA855F7))
                        )
                    }
                    if (hasSport) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                    }
                }
            }
        }
    }
}