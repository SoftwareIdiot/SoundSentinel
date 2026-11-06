package com.softwareidiot.soundsentinel.ui.screens

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import com.softwareidiot.soundsentinel.logic.MonitoringPolicy
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
    DisposableEffect(lifecycleOwner, isArmed) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME || event == Lifecycle.Event.ON_START) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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


    val status = when {
        !isArmed -> "Ready when you are"
        isCalibrating -> "Learning your room"
        isRecording -> "Capturing an event"
        !MonitoringPolicy.canDetect(settings) -> "Armed - capture paused"
        isMicCapturing -> "Listening for a change"
        else -> "Opening microphone"
    }
    val accent = if (isRecording) CoralPrimary else if (isArmed) CyanAccent else GoldAccent
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("YOUR SOUND WATCH", color = TextMuted, fontSize = 11.sp, letterSpacing = 2.sp)
            Text("OFFLINE", color = CyanAccent, fontSize = 10.sp)
        }
        Surface(shape = RoundedCornerShape(28.dp), color = DarkSurface) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(if (isRecording) "EVENT IN PROGRESS" else if (isArmed) "MONITORING" else "STANDBY",
                    color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(status, color = TextPrimary, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text(when {
                    isRecording -> String.format(Locale.US, "%.1fs captured | %.1fs until save if quiet", recordingDuration, silenceCountdown)
                    isArmed -> "Room noise is learned locally. Distinct events become saved clips."
                    else -> "Arm once to listen with the screen off. Clips start automatically when an event is detected."
                }, color = TextSecondary, fontSize = 14.sp)
                Button(onClick = {
                    if (isArmed || hasMicPermission) onToggleArmed()
                    else {
                        shouldArmAfterPermission = true
                        lastRequestedPerm = Manifest.permission.RECORD_AUDIO
                        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        permissionsLauncher.launch(permissions.toTypedArray())
                    }
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("arm_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Default.PowerSettingsNew, null)
                    Spacer(Modifier.width(10.dp))
                    Text(if (isArmed) "Disarm SoundSentinel" else "Arm SoundSentinel", fontSize = 16.sp)
                }
            }
        }
        micError?.let {
            Surface(shape = RoundedCornerShape(18.dp), color = ThreatRed.copy(alpha = .12f)) {
                Column(Modifier.padding(16.dp)) {
                    Text(it, color = ThreatRed)
                    TextButton(onClick = onOpenPreferences) { Text("Check permissions and background settings") }
                }
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = DarkSurface) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Acoustic activity", color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text("$soundLevel / 100", color = accent)
                }
                LiveScrollingWaveformView(liveWaveformHistory, effectiveThreshold, isRecording, isArmed,
                    Modifier.fillMaxWidth().height(100.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(String.format(Locale.US, "Input %.1f dBFS", currentDb), color = TextSecondary, fontSize = 12.sp)
                    Text("Gate $effectiveThreshold / 100", color = CyanAccent, fontSize = 12.sp)
                }
                Text("dBFS measures microphone signal, not calibrated room loudness.", color = TextMuted, fontSize = 11.sp)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = DarkSurface, onClick = onOpenVault) {
                Column(Modifier.padding(18.dp)) {
                    Text(storageStats.recordingsCount.toString(), color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Saved clips", color = TextSecondary, fontSize = 12.sp)
                }
            }
            Surface(Modifier.weight(1f), shape = RoundedCornerShape(20.dp), color = DarkSurface) {
                Column(Modifier.padding(18.dp)) {
                    Text(storageStats.usedPercentage.toInt().toString() + "%", color = TextPrimary, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                    Text("Vault capacity used", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = DarkSurface) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Background readiness", color = TextPrimary, fontWeight = FontWeight.Bold)
                val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                Text(if (pm.isIgnoringBatteryOptimizations(context.packageName)) "Battery: unrestricted" else
                    "Battery: check restrictions for long sessions", color = TextSecondary, fontSize = 13.sp)
                Text(if (hasNotifPermission) "Monitoring notification enabled" else "Enable notifications to keep status visible", color = TextSecondary, fontSize = 13.sp)
                Text("Swiping Recents keeps an active service. Force Stop, reboot, or manufacturer battery controls may stop it; open the app to arm again.",
                    color = TextMuted, fontSize = 12.sp)
                TextButton(onClick = onOpenPreferences) { Text("Monitoring options & permissions", color = CyanAccent) }
            }
        }
    }
}

private fun isMicGranted(context: Context) = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
private fun isNotifGranted(context: Context) = Build.VERSION.SDK_INT < 33 ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
private fun openAppPermissionSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
