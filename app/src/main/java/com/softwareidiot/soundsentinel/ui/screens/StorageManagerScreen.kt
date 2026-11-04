package com.softwareidiot.soundsentinel.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.softwareidiot.soundsentinel.data.model.AudioQualityPreset
import com.softwareidiot.soundsentinel.data.model.CCTVSettings
import com.softwareidiot.soundsentinel.data.model.RetentionMode
import com.softwareidiot.soundsentinel.data.model.StorageStats
import com.softwareidiot.soundsentinel.logic.CctvLogic
import com.softwareidiot.soundsentinel.ui.theme.AlertAmber
import com.softwareidiot.soundsentinel.ui.theme.AlertAmberBg
import com.softwareidiot.soundsentinel.ui.theme.BorderColor
import com.softwareidiot.soundsentinel.ui.theme.CoralPrimary
import com.softwareidiot.soundsentinel.ui.theme.CyanAccent
import com.softwareidiot.soundsentinel.ui.theme.DarkSurface
import com.softwareidiot.soundsentinel.ui.theme.DarkSurfaceVariant
import com.softwareidiot.soundsentinel.ui.theme.GoldAccent
import com.softwareidiot.soundsentinel.ui.theme.TextMuted
import com.softwareidiot.soundsentinel.ui.theme.TextPrimary
import com.softwareidiot.soundsentinel.ui.theme.TextSecondary
import com.softwareidiot.soundsentinel.ui.theme.ThreatRed

@Composable
fun StorageManagerScreen(
    settings: CCTVSettings,
    storageStats: StorageStats,
    onUpdateSettings: (CCTVSettings) -> Unit,
    onClearUnprotected: () -> Unit,
    onRefreshStorage: () -> Unit,
    onSendTestAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    var customQuotaText by remember(settings.storageQuotaMB) { mutableStateOf(settings.storageQuotaMB.toString()) }
    var testAlertSent by remember { mutableStateOf(false) }

    val quotaPresets = listOf(25, 50, 100, 250, 500, 1000)

    val retentionOptions = listOf(
        RetentionMode.H1 to "1 Hour" to "Overwrites after 60 mins",
        RetentionMode.H6 to "6 Hours" to "Overwrites after 6h",
        RetentionMode.H24 to "24 Hours" to "Daily rolling cycle",
        RetentionMode.D3 to "3 Days" to "72-hour retention",
        RetentionMode.D7 to "7 Days" to "1-week archive",
        RetentionMode.D30 to "30 Days" to "Monthly rolling archive",
        RetentionMode.NEVER to "Only on Full" to "Keep until quota limit"
    )

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("storage_manager_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Storage & Circular Quota Gauge Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Storage",
                        tint = CyanAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "STORAGE & QUOTA MANAGER",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${storageStats.usedPercentage}% USED",
                        color = if (storageStats.isNearFull) ThreatRed else GoldAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onRefreshStorage,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Verify Disk Files",
                            tint = CyanAccent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (storageStats.usedPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    storageStats.isFull -> ThreatRed
                    storageStats.isNearFull -> AlertAmber
                    else -> CoralPrimary
                },
                trackColor = DarkSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Vault: ${CctvLogic.formatBytes(storageStats.usedBytes)} / ${settings.storageQuotaMB} MB",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${storageStats.recordingsCount} clips (${storageStats.protectedCount} locked)",
                    color = TextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (storageStats.deviceFreeBytes > 0L) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Device free space: ${CctvLogic.formatBytes(storageStats.deviceFreeBytes)} available",
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (storageStats.isNearFull) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(AlertAmberBg)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Warning",
                        tint = AlertAmber,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quota threshold exceeded: Oldest unlocked clips will be overwritten automatically when new sounds occur.",
                        color = AlertAmber,
                        fontSize = 10.sp
                    )
                }
            }
        }

        // 2. Quota Presets & Custom Capacity
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "MAXIMUM VAULT QUOTA LIMIT",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))

            val rows = quotaPresets.chunked(3)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowItems.forEach { preset ->
                        val isSelected = settings.storageQuotaMB == preset
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) CoralPrimary.copy(alpha = 0.25f) else DarkSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) CoralPrimary else BorderColor,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    onUpdateSettings(settings.copy(storageQuotaMB = preset))
                                    customQuotaText = preset.toString()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (preset >= 1000) "1 GB" else "$preset MB",
                                color = if (isSelected) GoldAccent else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = customQuotaText,
                    onValueChange = { customQuotaText = it },
                    label = { Text("Custom Quota (MB)", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = DarkSurfaceVariant,
                        unfocusedContainerColor = DarkSurfaceVariant,
                        focusedBorderColor = CoralPrimary,
                        unfocusedBorderColor = BorderColor,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        val parsed = customQuotaText.toIntOrNull()
                        if (parsed != null && parsed >= 5) {
                            onUpdateSettings(settings.copy(storageQuotaMB = parsed))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Text("Apply", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // 3. Retention Policy Options
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Text(
                text = "RETENTION EXPIRY WINDOW",
                color = TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(10.dp))

            retentionOptions.forEach { pair ->
                val (mode, label) = pair.first
                val desc = pair.second
                val isSelected = settings.retentionMode == mode

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CoralPrimary.copy(alpha = 0.2f) else DarkSurfaceVariant.copy(alpha = 0.5f))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) CoralPrimary else BorderColor,
                            shape = RoundedCornerShape(10.dp)
                        )
                        .clickable { onUpdateSettings(settings.copy(retentionMode = mode)) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = label,
                            color = if (isSelected) GoldAccent else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = desc,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }

                    if (isSelected) {
                        Text(
                            text = "ACTIVE",
                            color = GoldAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        // 4. Circular Overwrite Toggle
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Circular Auto-Prune on Full", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Automatically overwrites oldest unlocked recordings when quota is reached", color = TextMuted, fontSize = 10.sp)
                }
                Switch(
                    checked = settings.autoPruneOnFull,
                    onCheckedChange = { onUpdateSettings(settings.copy(autoPruneOnFull = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CoralPrimary)
                )
            }
        }

        // Purge Unlocked Recordings Button
        Button(
            onClick = onClearUnprotected,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = ThreatRed.copy(alpha = 0.85f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.DeleteSweep, contentDescription = "Purge", tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Purge All Unlocked Recordings",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
