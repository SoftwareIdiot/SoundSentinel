package com.softwareidiot.soundsentinel.logic

/** Candidate shortcuts only. Availability and access are checked by launching on the device. */
object BackgroundSettingsTargets {
    fun forManufacturer(manufacturer: String): List<Pair<String, String>> = when (manufacturer.trim().lowercase(java.util.Locale.ROOT)) {
        "realme", "oppo", "oneplus" -> listOf(
            "com.oplus.safecenter" to "com.oplus.safecenter.startupapp.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity")
        "xiaomi", "redmi", "poco" -> listOf("com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity")
        "huawei", "honor" -> listOf("com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
        "vivo", "iqoo" -> listOf("com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")
        "asus" -> listOf("com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity")
        else -> emptyList()
    }
}
