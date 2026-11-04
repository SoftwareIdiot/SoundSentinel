package com.softwareidiot.soundsentinel.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.softwareidiot.soundsentinel.data.model.AudioQualityPreset
import com.softwareidiot.soundsentinel.data.model.CCTVSettings
import com.softwareidiot.soundsentinel.data.model.DetectionMode
import com.softwareidiot.soundsentinel.ui.theme.BorderColor
import com.softwareidiot.soundsentinel.ui.theme.CoralPrimary
import com.softwareidiot.soundsentinel.ui.theme.CyanAccent
import com.softwareidiot.soundsentinel.ui.theme.DarkSurface
import com.softwareidiot.soundsentinel.ui.theme.DarkSurfaceVariant
import com.softwareidiot.soundsentinel.ui.theme.GoldAccent
import com.softwareidiot.soundsentinel.ui.theme.MintActive
import com.softwareidiot.soundsentinel.ui.theme.TextMuted
import com.softwareidiot.soundsentinel.ui.theme.TextPrimary
import com.softwareidiot.soundsentinel.ui.theme.TextSecondary
import com.softwareidiot.soundsentinel.ui.theme.VioletAccent

@Composable
fun PreferencesScreen(
    settings: CCTVSettings,
    effectiveThreshold: Int,
    onUpdateSettings: (CCTVSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasNotifPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasMicPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        hasMicPermission = perms[Manifest.permission.RECORD_AUDIO] == true ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        hasNotifPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms[Manifest.permission.POST_NOTIFICATIONS] == true ||
                    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("preferences_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Audio Detection Mode & Sensitivity Calibration Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Detection Mode",
                        tint = CoralPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AUDIO DETECTION SYSTEM",
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Segmented AUTO / MANUAL Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .padding(3.dp)
                ) {
                    val isAuto = settings.detectionMode == DetectionMode.AUTO
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                brush = if (isAuto) {
                                    Brush.horizontalGradient(listOf(CoralPrimary, GoldAccent))
                                } else {
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                }
                            )
                            .clickable { onUpdateSettings(settings.copy(detectionMode = DetectionMode.AUTO)) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "AUTO",
                            color = if (isAuto) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                brush = if (!isAuto) {
                                    Brush.horizontalGradient(listOf(CoralPrimary, VioletAccent))
                                } else {
                                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                                }
                            )
                            .clickable { onUpdateSettings(settings.copy(detectionMode = DetectionMode.MANUAL)) }
                            .padding(horizontal = 12.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "MANUAL",
                            color = if (!isAuto) Color.White else TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            if (settings.detectionMode == DetectionMode.AUTO) {
                Text(
                    text = "Dynamic Auto-Sensitivity continuously adapts to your environment: when the room is quiet, sensitivity increases to capture faint whispers or footsteps; when the room is noisy, sensitivity scales down so only distinct sound spikes trigger recording.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto Margin Above Room Floor", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("+${settings.autoSensitivityMargin} pts (Active Gate: $effectiveThreshold)", color = GoldAccent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = settings.autoSensitivityMargin.toFloat(),
                    onValueChange = { onUpdateSettings(settings.copy(autoSensitivityMargin = it.toInt())) },
                    valueRange = 6f..30f,
                    colors = SliderDefaults.colors(
                        thumbColor = GoldAccent,
                        activeTrackColor = CoralPrimary,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Ultra-sensitive (+6)", color = TextMuted, fontSize = 10.sp)
                    Text("Balanced (+12)", color = TextMuted, fontSize = 10.sp)
                    Text("Loud sounds (+30)", color = TextMuted, fontSize = 10.sp)
                }
            } else {
                Text(
                    text = "Manual Mode locks the trigger gate to a fixed level. Lower the number for higher sensitivity to faint sounds, or raise it so only loud sounds start recording.",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Fixed Trigger Gate", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("${settings.sensitivityThreshold} / 100", color = CyanAccent, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Slider(
                    value = settings.sensitivityThreshold.toFloat(),
                    onValueChange = { onUpdateSettings(settings.copy(sensitivityThreshold = it.toInt())) },
                    valueRange = 8f..90f,
                    colors = SliderDefaults.colors(
                        thumbColor = CyanAccent,
                        activeTrackColor = VioletAccent,
                        inactiveTrackColor = DarkSurfaceVariant
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("High sensitivity (8)", color = TextMuted, fontSize = 10.sp)
                    Text("Medium (45)", color = TextMuted, fontSize = 10.sp)
                    Text("Loud only (90)", color = TextMuted, fontSize = 10.sp)
                }
            }
        }

        // 2. Post-Roll Recording Hold Duration
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Post-Roll Hold",
                        tint = GoldAccent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "POST-ROLL EXTENSION TIME",
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "${settings.postRollSeconds} seconds",
                    color = GoldAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Text(
                text = "How long recording continues after the sound becomes quiet. Any new sound resets this countdown.",
                color = TextSecondary,
                fontSize = 11.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 10, 15, 30).forEach { sec ->
                    val isSelected = settings.postRollSeconds == sec
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
                            .clickable { onUpdateSettings(settings.copy(postRollSeconds = sec)) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${sec}s",
                            color = if (isSelected) GoldAccent else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 3. Hardware Audio Recording Quality
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.HighQuality,
                    contentDescription = "Audio Fidelity",
                    tint = CyanAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HARDWARE RECORDING QUALITY",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            AudioQualityPreset.entries.forEach { preset ->
                val isSelected = settings.audioQuality == preset
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) CyanAccent.copy(alpha = 0.14f) else DarkSurfaceVariant.copy(alpha = 0.55f))
                        .border(
                            width = 1.dp,
                            color = if (isSelected) CyanAccent else BorderColor,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable { onUpdateSettings(settings.copy(audioQuality = preset)) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = preset.label,
                            color = if (isSelected) CyanAccent else TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = preset.subtitle,
                            color = TextMuted,
                            fontSize = 10.sp
                        )
                    }
                    if (isSelected) {
                        Text(
                            text = "SELECTED",
                            color = CyanAccent,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 4. Smart Signal Tagging, Notifications & Haptics
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Signal Tagging & Alerts",
                    tint = VioletAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "SIGNAL TAGGING & ALERTS",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Acoustic Signal Tagging", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Automatically labels speech, footsteps, knocks, impacts & faint sounds", color = TextMuted, fontSize = 10.sp)
                }
                Switch(
                    checked = settings.enableSignalAnalysis,
                    onCheckedChange = { onUpdateSettings(settings.copy(enableSignalAnalysis = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CoralPrimary)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Alert Notifications", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Notify when high-intensity sound events or storage limits occur", color = TextMuted, fontSize = 10.sp)
                }
                Switch(
                    checked = settings.enableNotifications,
                    onCheckedChange = { onUpdateSettings(settings.copy(enableNotifications = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CoralPrimary)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Haptic Vibration", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Gentle vibration pulse when a sound triggers recording", color = TextMuted, fontSize = 10.sp)
                }
                Switch(
                    checked = settings.enableVibration,
                    onCheckedChange = { onUpdateSettings(settings.copy(enableVibration = it)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = CoralPrimary)
                )
            }
        }

        // 5. Phone Permissions & Background Health Status
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(DarkSurface)
                .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Permissions",
                    tint = MintActive,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PHONE PERMISSIONS & BACKGROUND STATUS",
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            PermissionStatusRow(
                title = "Microphone Capture",
                subtitle = if (hasMicPermission) "Granted and ready" else "Required to detect and record audio",
                isGranted = hasMicPermission,
                onAction = {
                    permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            )

            PermissionStatusRow(
                title = "Status Notifications",
                subtitle = if (hasNotifPermission) "Granted and ready" else "Required for background monitoring bar",
                isGranted = hasNotifPermission,
                onAction = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.55f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = TextMuted, fontSize = 10.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (isGranted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Active", tint = MintActive, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Active", color = MintActive, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        } else {
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Enable", color = GoldAccent, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}
