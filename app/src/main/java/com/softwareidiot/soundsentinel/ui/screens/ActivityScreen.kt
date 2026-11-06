package com.softwareidiot.soundsentinel.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwareidiot.soundsentinel.data.model.*
import com.softwareidiot.soundsentinel.logic.CctvLogic
import com.softwareidiot.soundsentinel.ui.theme.*
import java.util.Calendar
import java.util.Date
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ActivityScreen(recordings: List<AudioRecording>, events: List<CCTVEvent>) {
    var dayOffset by remember { mutableStateOf(0) }
    val start = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, dayOffset)
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }
    val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val clips = recordings.filter { it.timestamp >= start.timeInMillis && it.timestamp < end.timeInMillis }
    val history = events.filter { it.timestamp >= start.timeInMillis && it.timestamp < end.timeInMillis }.sortedByDescending { it.timestamp }.take(100)
    val hourly = IntArray(24)
    clips.forEach { hourly[Calendar.getInstance().apply { timeInMillis = it.timestamp }.get(Calendar.HOUR_OF_DAY)]++ }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 18.dp)) {
        item {
            Text("Your sound journal", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
            Text("Saved clips and local event history", color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Today", -1 to "Yesterday", -2 to "2 days ago").forEach { (offset, label) ->
                    FilterChip(selected = dayOffset == offset, onClick = { dayOffset = offset }, label = { Text(label) })
                }
            }
        }
        item {
            Surface(shape = RoundedCornerShape(24.dp), color = DarkSurface) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(start.time), color = TextPrimary)
                    Text("${clips.size} clips  •  ${CctvLogic.formatDuration(clips.sumOf { it.duration })} captured", color = CyanAccent, fontSize = 20.sp)
                    Text("${clips.count { it.isProtected }} protected  •  ${CctvLogic.formatBytes(clips.sumOf { it.sizeBytes })}", color = TextSecondary)
                    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
                        val step = size.width / 24
                        val maxCount = (hourly.maxOrNull() ?: 1).coerceAtLeast(1)
                        hourly.forEachIndexed { index, count ->
                            val height = if(count == 0) 3f else (count.toFloat() / maxCount) * size.height
                            drawRoundRect(CyanAccent.copy(alpha = if(count == 0) .15f else .85f),
                                Offset(index * step + 2f, size.height - height), Size((step - 4).coerceAtLeast(1f), height))
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("00:00", color = TextMuted, fontSize = 11.sp); Text("12:00", color = TextMuted, fontSize = 11.sp); Text("23:00", color = TextMuted, fontSize = 11.sp)
                    }
                    Text("Counts reflect clips still in your vault. Retention cleanup can reduce past totals.", color = TextMuted, fontSize = 11.sp)
                }
            }
        }
        item { Text("Event timeline", color = TextPrimary, style = MaterialTheme.typography.titleMedium) }
        if (history.isEmpty()) item { Text("No events for this day. Arm monitoring to begin your local journal.", color = TextSecondary) }
        items(history, key = { it.id }) { event ->
            Surface(shape = RoundedCornerShape(18.dp), color = DarkSurface) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(event.timestamp)) + "  •  " + event.type.name.lowercase().replaceFirstChar { it.uppercase() }, color = CyanAccent, fontSize = 12.sp)
                    Text(event.message, color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}
