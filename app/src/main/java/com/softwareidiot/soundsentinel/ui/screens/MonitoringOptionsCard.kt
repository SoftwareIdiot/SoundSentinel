package com.softwareidiot.soundsentinel.ui.screens

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwareidiot.soundsentinel.data.model.*
import com.softwareidiot.soundsentinel.logic.MonitoringPolicy
import com.softwareidiot.soundsentinel.ui.theme.*

@Composable
fun MonitoringOptionsCard(settings: CCTVSettings, onUpdate: (CCTVSettings) -> Unit) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(24.dp), color = DarkSurface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Monitoring & alerts", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
            Text("Choose which saved events can alert you. Clips are still kept for every detected event.", color = TextSecondary, fontSize = 12.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AlertProfile.entries.forEach { profile ->
                    FilterChip(selected = settings.alertProfile == profile,
                        onClick = { onUpdate(settings.copy(alertProfile = profile)) },
                        label = { Text(when(profile) { AlertProfile.SILENT -> "Silent"; AlertProfile.IMPORTANT -> "Important"; AlertProfile.ALL_EVENTS -> "Every event" }) })
                }
            }
            Text("Minimum time between alerts", color = TextSecondary, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 60, 180).forEach { seconds ->
                    FilterChip(selected = settings.alertCooldownSeconds == seconds,
                        onClick = { onUpdate(settings.copy(alertCooldownSeconds = seconds)) }, label = { Text("${seconds}s") })
                }
            }
            HorizontalDivider(color = BorderColor)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Daily recording window", color = TextPrimary)
                    Text("Controls capture while already armed", color = TextMuted, fontSize = 11.sp)
                }
                Switch(settings.monitoringWindowEnabled, { onUpdate(settings.copy(monitoringWindowEnabled = it)) })
            }
            if (settings.monitoringWindowEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> onUpdate(settings.copy(monitoringStartMinute = h * 60 + m)) },
                            settings.monitoringStartMinute / 60, settings.monitoringStartMinute % 60, true).show()
                    }) { Text("From " + MonitoringPolicy.timeLabel(settings.monitoringStartMinute)) }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> onUpdate(settings.copy(monitoringEndMinute = h * 60 + m)) },
                            settings.monitoringEndMinute / 60, settings.monitoringEndMinute % 60, true).show()
                    }) { Text("Until " + MonitoringPolicy.timeLabel(settings.monitoringEndMinute)) }
                }
                Text("Uses your phone's local time, including overnight windows. Matching times mean all day. Arm manually first; this cannot restart the microphone after Force Stop or reboot.", color = TextSecondary, fontSize = 12.sp)
            }
            Text("Alerts happen after a clip is saved. A two-second feedback guard prevents the phone's vibration or notification tone from triggering another clip.", color = TextMuted, fontSize = 12.sp)
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
            }) { Text("Phone battery & permission settings", color = CyanAccent) }
        }
    }
}
