package com.softwareidiot.soundsentinel.logic

import com.softwareidiot.soundsentinel.data.model.AudioRecording
import com.softwareidiot.soundsentinel.data.model.RetentionMode
import com.softwareidiot.soundsentinel.data.model.StorageStats
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

object CctvLogic {

    /**
     * Calculates decibels (dBFS) from root mean square amplitude.
     * Returns a value between -80 dB and 0 dB.
     */
    fun calculateDecibelsFromRms(rms: Double): Double {
        if (rms <= 0.00005) return -80.0
        val db = 20.0 * log10(rms)
        return max(-80.0, min(0.0, (db * 10).roundToInt() / 10.0))
    }

    /**
     * Maps raw dBFS (-72 dB to -4 dB) to a human-friendly 0 - 100 sound level score.
     * Calibrated so even faint sounds (-62 dBFS) map to ~15/100 and clear speech maps to ~45-70/100.
     */
    fun mapDecibelsToScale(db: Double): Int {
        if (db <= -72.0) return 0
        if (db >= -4.0) return 100
        val score = ((db + 72.0) / 68.0) * 100.0
        return max(0, min(100, score.roundToInt()))
    }

    /** Fixed margin for automatic detection; the gate itself follows the live room-noise floor. */
    const val AUTO_GATE_MARGIN_DB = 12.0

    /** Current automatic gate in dBFS, clamped away from the microphone's noise and clipping limits. */
    fun computeAutoThresholdDb(noiseFloorDb: Double): Double {
        return (noiseFloorDb + AUTO_GATE_MARGIN_DB).coerceIn(-68.0, -8.0)
    }

    /**
     * Computes the live Auto gate on the UI's 0..100 scale. The audio detector uses the same
     * threshold in dBFS, so the number shown in the waveform panel matches the actual gate.
     */
    fun computeAutoThreshold(noiseFloorDb: Double): Int {
        return mapDecibelsToScale(computeAutoThresholdDb(noiseFloorDb)).coerceIn(5, 95)
    }

    /**
     * Checks if sound level exceeds the sensitivity threshold.
     */
    fun isSoundDetected(soundLevel: Int, threshold: Int): Boolean {
        return soundLevel >= threshold
    }

    /**
     * Computes storage usage from actual on-disk files, quota percentage, device disk space, and warning flags.
     */
    fun calculateStorageStats(
        recordings: List<AudioRecording>,
        quotaBytes: Long,
        deviceFreeBytes: Long = 0L,
        deviceTotalBytes: Long = 0L,
        warningThresholdPercent: Int = 85
    ): StorageStats {
        val safeQuota = max(5L * 1024L * 1024L, quotaBytes) // At least 5 MB
        var usedBytes = 0L
        var protectedCount = 0

        for (r in recordings) {
            val f = File(r.filePath)
            val realSize = if (f.exists()) f.length() else r.sizeBytes
            usedBytes += realSize
            if (r.isProtected) protectedCount++
        }

        val rawPercent = (usedBytes.toDouble() / safeQuota.toDouble()) * 100.0
        val usedPercentage = min(100.0, (rawPercent * 10).roundToInt() / 10.0).toFloat()
        val isNearFull = rawPercent >= warningThresholdPercent
        val isFull = rawPercent >= 98.0

        return StorageStats(
            usedBytes = usedBytes,
            quotaBytes = safeQuota,
            deviceFreeBytes = deviceFreeBytes,
            deviceTotalBytes = deviceTotalBytes,
            usedPercentage = usedPercentage,
            isNearFull = isNearFull,
            isFull = isFull,
            recordingsCount = recordings.size,
            protectedCount = protectedCount
        )
    }

    /**
     * Identifies oldest, unprotected recordings that must be deleted to free up target bytes.
     * Sorts from oldest timestamp to newest. Protected recordings are never pruned.
     */
    fun identifyOldestToPrune(
        recordings: List<AudioRecording>,
        bytesNeeded: Long
    ): List<String> {
        if (bytesNeeded <= 0L) return emptyList()

        val candidateRecordings = recordings
            .filter { !it.isProtected }
            .sortedBy { it.timestamp }

        val idsToPrune = mutableListOf<String>()
        var freedBytes = 0L

        for (rec in candidateRecordings) {
            idsToPrune.add(rec.id)
            val f = File(rec.filePath)
            freedBytes += if (f.exists()) f.length() else rec.sizeBytes
            if (freedBytes >= bytesNeeded) {
                break
            }
        }

        return idsToPrune
    }

    /**
     * Returns retention time in milliseconds based on user preference.
     * Returns Long.MAX_VALUE if mode is NEVER.
     */
    fun getRetentionDurationMs(mode: RetentionMode, customHours: Int = 24): Long {
        val hourMs = 60L * 60L * 1000L
        return when (mode) {
            RetentionMode.H1 -> 1L * hourMs
            RetentionMode.H6 -> 6L * hourMs
            RetentionMode.H24 -> 24L * hourMs
            RetentionMode.D3 -> 3L * 24L * hourMs
            RetentionMode.D7 -> 7L * 24L * hourMs
            RetentionMode.D30 -> 30L * 24L * hourMs
            RetentionMode.CUSTOM -> max(1, customHours).toLong() * hourMs
            RetentionMode.NEVER -> Long.MAX_VALUE
        }
    }

    /**
     * Identifies recordings older than retention duration.
     * Protected recordings are preserved.
     */
    fun identifyExpiredRecordings(
        recordings: List<AudioRecording>,
        retentionDurationMs: Long,
        now: Long = System.currentTimeMillis()
    ): List<String> {
        if (retentionDurationMs == Long.MAX_VALUE) return emptyList()

        return recordings
            .filter { !it.isProtected && (now - it.timestamp) > retentionDurationMs }
            .map { it.id }
    }

    /**
     * Formats byte counts into human-readable strings (B, KB, MB, GB).
     */
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (log10(bytes.toDouble()) / log10(1024.0)).toInt()
        val index = min(units.size - 1, digitGroups)
        val value = bytes.toDouble() / 1024.0.pow(index.toDouble())
        return if (index == 0) {
            String.format(Locale.US, "%.0f %s", value, units[index])
        } else {
            String.format(Locale.US, "%.1f %s", value, units[index])
        }
    }

    /**
     * Formats duration in seconds into MM:SS or HH:MM:SS.
     */
    fun formatDuration(seconds: Double): String {
        val sec = max(0, seconds.roundToInt())
        val m = sec / 60
        val s = sec % 60
        return if (m >= 60) {
            val h = m / 60
            val remM = m % 60
            String.format(Locale.US, "%d:%02d:%02d", h, remM, s)
        } else {
            String.format(Locale.US, "%02d:%02d", m, s)
        }
    }

    /**
     * Formats timestamps into live CCTV timecode format (YYYY-MM-DD HH:MM:SS).
     */
    fun formatTimeCode(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        return sdf.format(Date(timestamp))
    }

    /**
     * Formats timestamps into friendly date & time.
     */
    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("MMM d, yyyy • h:mm:ss a", Locale.US)
        return sdf.format(Date(timestamp))
    }
}
