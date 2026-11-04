package com.softwareidiot.soundsentinel.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.softwareidiot.soundsentinel.data.model.CCTVSettings
import com.softwareidiot.soundsentinel.data.model.StorageStats
import com.softwareidiot.soundsentinel.logic.CctvLogic
import com.softwareidiot.soundsentinel.ui.components.LiveScrollingWaveformView
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
import com.softwareidiot.soundsentinel.ui.theme.ThreatRed
import com.softwareidiot.soundsentinel.ui.theme.VioletAccent
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun SurveillanceScreen(
    isArmed: Boolean,
    onToggleArmed: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenPreferences: () -> Unit,
    isMicCapturing: Boolean,
    isCalibrating: Boolean,
    activeSampleRate: Int,
    soundLevel: Int,
    currentDb: Double,
    noiseFloorDb: Double,
    effectiveThreshold: Int,
    liveWaveformHistory: List<Int>,
    isRecording: Boolean,
    recordingDuration: Double,
    silenceCountdown: Double,
    triggersInSession: Int,
    micError: String?,
    settings: CCTVSettings,
    storageStats: StorageStats,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasMicPermission by remember {
        mutableStateOf(isMicGranted(context))
    }
    var hasNotifPermission by remember {
        mutableStateOf(isNotifGranted(context))
    }

    fun refreshPermissions() {
        val micNow = isMicGranted(context)
        val notifNow = isNotifGranted(context)
        hasMicPermission = micNow
        hasNotifPermission = notifNow
        // If microphone permission was revoked by the device while armed, disarm cleanly
        if (!micNow && isArmed) {
            onToggleArmed()
        }
    }

    // Observe lifecycle resume/start to immediately detect granted or revoked permissions
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Also poll permission state periodically so any runtime revocation/grant by the device updates UI immediately
    LaunchedEffect(isArmed) {
        while (true) {
            refreshPermissions()
            delay(1200L)
        }
    }

    var shouldArmAfterPermission by remember { mutableStateOf(false) }
    var lastRequestedPerm by remember { mutableStateOf<String?>(null) }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micNow = perms[Manifest.permission.RECORD_AUDIO] == true || isMicGranted(context)
        val notifNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms[Manifest.permission.POST_NOTIFICATIONS] == true || isNotifGranted(context)
        } else true

        hasMicPermission = micNow
        hasNotifPermission = notifNow

        if (micNow && shouldArmAfterPermission && !isArmed) {
            shouldArmAfterPermission = false
            onToggleArmed()
        } else if (!micNow || !notifNow) {
            // If the device blocked the dialog ("Don't ask again"), open App Settings so the user can still enable it
            val activity = context.findActivity()
            val blockedByDevice = lastRequestedPerm?.let { perm ->
                ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED &&
                    activity != null &&
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, perm)
            } ?: false

            if (blockedByDevice) {
                openAppPermissionSettings(context)
            }
        }
    }

    val allPermissionsGranted = hasMicPermission && hasNotifPermission
    val enabledCount = (if (hasMicPermission) 1 else 0) + (if (hasNotifPermission) 1 else 0)

    val infiniteTransition = rememberInfiniteTransition(label = "arm_pulse")
    val ringScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isRecording) 1.14f else if (isArmed) 1.08f else 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isRecording) 700 else 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_scale"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // TOP SECTION: Temporary Permission Checklist (disappears completely when all required permissions are ticked off,
        // and automatically returns if any required permission is denied by the device)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnimatedVisibility(
                visible = !allPermissionsGranted,
                enter = fadeIn(tween(220)) + expandVertically(tween(250)),
                exit = fadeOut(tween(180)) + shrinkVertically(tween(220))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(DarkSurface)
                        .border(
                            width = 1.dp,
                            color = GoldAccent.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("permission_checklist_section"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "Required Permissions",
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "REQUIRED PERMISSIONS ($enabledCount/2)",
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Button(
                            onClick = {
                                val missing = mutableListOf<String>()
                                if (!hasMicPermission) missing.add(Manifest.permission.RECORD_AUDIO)
                                if (!hasNotifPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    missing.add(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                if (missing.isNotEmpty()) {
                                    lastRequestedPerm = missing.first()
                                    permissionsLauncher.launch(missing.toTypedArray())
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .testTag("enable_all_permissions_btn"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "Enable All",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 1. Microphone Permission Item
                    PermissionChecklistItem(
                        icon = Icons.Default.Mic,
                        title = "Microphone Access",
                        subtitle = if (hasMicPermission) "Ticked off • Ready to detect & record sound" else "Required to detect & record sounds",
                        isGranted = hasMicPermission,
                        onEnable = {
                            lastRequestedPerm = Manifest.permission.RECORD_AUDIO
                            permissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                    )

                    // 2. Notifications & Background Status Permission Item
                    PermissionChecklistItem(
                        icon = Icons.Default.Notifications,
                        title = "Background & Alert Notifications",
                        subtitle = if (hasNotifPermission) "Ticked off • Background status bar enabled" else "Required for background monitoring & alerts",
                        isGranted = hasNotifPermission,
                        onEnable = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                lastRequestedPerm = Manifest.permission.POST_NOTIFICATIONS
                                permissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                            }
                        }
                    )
                }
            }

            if (micError != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ThreatRed.copy(alpha = 0.15f))
                        .border(width = 1.dp, color = ThreatRed.copy(alpha = 0.5f), shape = RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = ThreatRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = micError, color = TextPrimary, fontSize = 11.sp)
                }
            } else if (allPermissionsGranted) {
                // Quick Summary Pills Row (Vault Clips + Audio Quality) shown cleanly once permissions are complete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurface)
                            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(14.dp))
                            .clickable { onOpenVault() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Audiotrack,
                                contentDescription = "Recordings Vault",
                                tint = GoldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Saved Clips",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${storageStats.recordingsCount}",
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(DarkSurface)
                            .border(width = 1.dp, color = BorderColor, shape = RoundedCornerShape(14.dp))
                            .clickable { onOpenPreferences() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Audio Fidelity",
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Quality",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "${activeSampleRate / 1000.0}kHz",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // CENTER HERO: Unified Recording & Background Arm Orb
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(164.dp)
            ) {
                // Outer animated multicolor halo ring
                Box(
                    modifier = Modifier
                        .size(156.dp)
                        .scale(if (isArmed) ringScale else 1f)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.sweepGradient(
                                colors = when {
                                    isRecording -> listOf(
                                        ThreatRed.copy(alpha = 0.45f),
                                        CoralPrimary.copy(alpha = 0.45f),
                                        GoldAccent.copy(alpha = 0.45f),
                                        ThreatRed.copy(alpha = 0.45f)
                                    )
                                    isArmed -> listOf(
                                        CoralPrimary.copy(alpha = 0.32f),
                                        GoldAccent.copy(alpha = 0.32f),
                                        CyanAccent.copy(alpha = 0.32f),
                                        VioletAccent.copy(alpha = 0.32f),
                                        CoralPrimary.copy(alpha = 0.32f)
                                    )
                                    else -> listOf(
                                        DarkSurfaceVariant.copy(alpha = 0.4f),
                                        BorderColor.copy(alpha = 0.4f)
                                    )
                                }
                            )
                        )
                )

                // Main tappable Orb Button
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .clip(CircleShape)
                        .background(
                            brush = when {
                                isRecording -> Brush.linearGradient(
                                    colors = listOf(ThreatRed, CoralPrimary)
                                )
                                isArmed -> Brush.linearGradient(
                                    colors = listOf(CoralPrimary, VioletAccent)
                                )
                                else -> Brush.linearGradient(
                                    colors = listOf(DarkSurfaceVariant, DarkSurface)
                                )
                            }
                        )
                        .border(
                            width = 2.5.dp,
                            brush = Brush.linearGradient(
                                colors = when {
                                    isRecording -> listOf(GoldAccent, Color.White)
                                    isArmed -> listOf(GoldAccent, CyanAccent)
                                    else -> listOf(BorderColor, TextMuted.copy(alpha = 0.4f))
                                }
                            ),
                            shape = CircleShape
                        )
                        .clickable {
                            if (!isArmed) {
                                if (!hasMicPermission || !hasNotifPermission) {
                                    shouldArmAfterPermission = true
                                    val req = mutableListOf(Manifest.permission.RECORD_AUDIO)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        req.add(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                    lastRequestedPerm = req.first()
                                    permissionsLauncher.launch(req.toTypedArray())
                                } else {
                                    onToggleArmed()
                                }
                            } else {
                                onToggleArmed()
                            }
                        }
                        .testTag("arm_cctv_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.GraphicEq else Icons.Default.PowerSettingsNew,
                            contentDescription = if (isArmed) "Disarm SoundSentinel" else "Arm SoundSentinel",
                            tint = if (isArmed) Color.White else CoralPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Text(
                            text = when {
                                isRecording -> "RECORDING"
                                isArmed -> "ARMED"
                                else -> "ARM SOUND\nSENTINEL"
                            },
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = when {
                                isRecording -> String.format(Locale.US, "%.1fs • Tail %.0fs", recordingDuration, silenceCountdown)
                                isArmed -> if (isCalibrating) "Calibrating room" else "Listening for events"
                                else -> "Ready to arm"
                            },
                            color = if (isArmed) Color.White.copy(alpha = 0.85f) else TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = when {
                    isRecording -> "Sound detected. Saving after ${settings.postRollSeconds}s of quiet."
                    isArmed && isCalibrating -> "Calibrating room noise before automatic detection starts..."
                    isArmed && isMicCapturing -> "Armed and listening. A clip starts only when sound crosses the gate."
                    isArmed -> "Initializing phone microphone..."
                    else -> "Arm SoundSentinel to listen; clips start automatically when sound is detected."
                },
                color = if (isRecording) GoldAccent else TextSecondary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }

        // UNIFIED STUDIO WAVEFORM MONITOR CARD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(22.dp))
                .background(DarkSurface)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = if (isRecording) {
                            listOf(CoralPrimary.copy(alpha = 0.8f), GoldAccent.copy(alpha = 0.8f))
                        } else if (isArmed) {
                            listOf(CyanAccent.copy(alpha = 0.45f), VioletAccent.copy(alpha = 0.45f))
                        } else {
                            listOf(BorderColor, BorderColor)
                        }
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) ThreatRed else if (isArmed) CyanAccent else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE ACOUSTIC WAVE",
                        color = TextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Level $soundLevel / 100",
                        color = if (soundLevel >= effectiveThreshold) GoldAccent else CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                }
            }

            // Single unified multicolor scrolling sound wave
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(DarkSurfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                LiveScrollingWaveformView(
                    waveformHistory = liveWaveformHistory,
                    effectiveThreshold = effectiveThreshold,
                    isRecording = isRecording,
                    isArmed = isArmed,
                    modifier = Modifier.fillMaxSize()
                )
            }

            val floorScale = CctvLogic.mapDecibelsToScale(noiseFloorDb)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = String.format(Locale.US, "Input: %.1f dB (Floor %d/100)", currentDb, floorScale),
                    color = TextMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "Trigger Gate: $effectiveThreshold / 100",
                    color = GoldAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }


    }
}

@Composable
private fun PermissionChecklistItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    onEnable: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DarkSurfaceVariant.copy(alpha = 0.65f))
            .clickable(enabled = !isGranted) { onEnable() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = if (isGranted) "Ticked Off" else "Not Enabled",
                tint = if (isGranted) MintActive else GoldAccent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) MintActive else TextSecondary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = if (isGranted) MintActive.copy(alpha = 0.85f) else TextMuted,
                    fontSize = 9.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        if (isGranted) {
            Text(
                text = "✓ Done",
                color = MintActive,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CoralPrimary.copy(alpha = 0.22f))
                    .border(width = 1.dp, color = CoralPrimary, shape = RoundedCornerShape(6.dp))
                    .clickable { onEnable() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Enable",
                    color = CoralPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

private fun isMicGranted(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}

private fun isNotifGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun openAppPermissionSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (ignored: Exception) {}
}
