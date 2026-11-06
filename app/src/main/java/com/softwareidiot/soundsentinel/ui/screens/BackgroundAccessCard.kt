package com.softwareidiot.soundsentinel.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.softwareidiot.soundsentinel.ui.theme.*

private fun launchSetting(context: Context, vararg intents: Intent): Boolean {
    for (intent in intents) try { context.startActivity(intent); return true } catch (_: RuntimeException) { }
    Toast.makeText(context, "This settings screen is not exposed by your phone software.", Toast.LENGTH_LONG).show()
    return false
}

@Composable
fun BackgroundAccessCard() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var exempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if(event == Lifecycle.Event.ON_RESUME) exempt = power.isIgnoringBatteryOptimizations(context.packageName)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Background access", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Text(if(exempt) "Battery exemption: allowed" else "Battery exemption: not allowed", color = if(exempt) CyanAccent else CoralPrimary)
        OutlinedButton(onClick = {
            if(exempt) launchSetting(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            else launchSetting(context,
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, modifier = Modifier.fillMaxWidth()) { Text(if(exempt) "Review battery exemption" else "Allow unrestricted battery use") }
        OutlinedButton(onClick = {
            launchSetting(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.fillMaxWidth()) { Text("Open SoundSentinel notifications") }
        val maker = Build.MANUFACTURER.lowercase(java.util.Locale.ROOT)
        if (maker in listOf("realme", "oppo", "oneplus")) {
            OutlinedButton(onClick = {
                val targets = listOf(
                    "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
                    "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                    "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity")
                val choices = targets.map { (pkg, activity) -> Intent().setComponent(ComponentName(pkg, activity))
                    .putExtra("packageName", context.packageName).putExtra("package_name", context.packageName) }
                if (!launchSetting(context, *choices.toTypedArray())) {
                    Toast.makeText(context, "Realme startup shortcut unavailable. Search Settings for Auto launch and allow SoundSentinel.", Toast.LENGTH_LONG).show()
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Open Realme / ColorOS auto launch") }
            Text("In the startup list, allow SoundSentinel. Realme versions may also require Allow background activity and locking the app in Recents. Android does not expose a universal shortcut for those controls.", color = TextSecondary)
        }
        Text("Granting access reduces battery restrictions; it cannot override Force Stop. If monitoring is interrupted, reopen the app to resume your armed session.", color = TextMuted)
    }
}
