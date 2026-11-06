package com.softwareidiot.soundsentinel.logic

import com.softwareidiot.soundsentinel.data.model.*
import java.util.Calendar

object MonitoringPolicy {
    fun isInWindow(minute: Int, start: Int, end: Int): Boolean {
        val m = minute.coerceIn(0, 1439)
        val s = start.coerceIn(0, 1439)
        val e = end.coerceIn(0, 1439)
        return if (s == e) true else if (s < e) m >= s && m < e else m >= s || m < e
    }

    fun canDetect(settings: CCTVSettings, now: Long = System.currentTimeMillis()): Boolean {
        val time = Calendar.getInstance().apply { timeInMillis = now }
        return settings.automaticDetectionEnabled && (!settings.monitoringWindowEnabled ||
            isInWindow(time.get(Calendar.HOUR_OF_DAY) * 60 + time.get(Calendar.MINUTE),
                settings.monitoringStartMinute, settings.monitoringEndMinute))
    }

    fun timeLabel(minute: Int): String = java.lang.String.format(java.util.Locale.US,
        "%02d:%02d", minute.coerceIn(0, 1439) / 60, minute.coerceIn(0, 1439) % 60)
}

/** Monotonic time avoids clock changes creating alert storms. Only accepted alerts consume cooldown. */
class AlertLimiter {
    private var lastAlertMs: Long? = null

    @Synchronized
    fun accept(profile: AlertProfile, threat: ThreatLevel?, peak: Int, nowMs: Long, cooldownSeconds: Int): Boolean {
        val eligible = when (profile) {
            AlertProfile.SILENT -> false
            AlertProfile.IMPORTANT -> peak >= 58 && (threat == ThreatLevel.HIGH || threat == ThreatLevel.CRITICAL)
            AlertProfile.ALL_EVENTS -> true
        }
        if (!eligible) return false
        val last = lastAlertMs
        if (last != null && nowMs - last < cooldownSeconds.coerceIn(10, 600) * 1000L) return false
        lastAlertMs = nowMs
        return true
    }
}
