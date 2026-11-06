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
        Text(if(exempt) "Battery access: allowed" else "Battery access: restricted", color = if(exempt) CyanAccent else CoralPrimary)
        OutlinedButton(onClick = {
            if(exempt) launchSetting(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            else launchSetting(context,
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + context.packageName)),
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }, modifier = Modifier.fillMaxWidth()) { Text(if(exempt) "Review battery access" else "Allow unrestricted battery use") }
        OutlinedButton(onClick = {
            launchSetting(context, Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
        }, modifier = Modifier.fillMaxWidth()) { Text("Notifications") }

        OutlinedButton(onClick = {
            val choices = com.softwareidiot.soundsentinel.logic.BackgroundSettingsTargets
                .forManufacturer(Build.MANUFACTURER).map { (pkg, activity) ->
                    Intent().setComponent(ComponentName(pkg, activity))
                        .putExtra("packageName", context.packageName).putExtra("package_name", context.packageName)
                } + Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            launchSetting(context, *choices.toTypedArray())
        }, modifier = Modifier.fillMaxWidth()) { Text("Background settings") }
        Text("Phone controls vary. Force Stop can end monitoring.", color = TextMuted)
    }
}
