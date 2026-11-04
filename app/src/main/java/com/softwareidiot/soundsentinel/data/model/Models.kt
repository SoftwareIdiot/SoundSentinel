package com.softwareidiot.soundsentinel.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThreatLevel {
    INFO,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

@Serializable
enum class AudioCategory {
    THREAT,
    SPEECH,
    INTRUSION,
    DOMESTIC,
    AMBIENT
}

@Serializable
enum class RetentionMode {
    H1,
    H6,
    H24,
    D3,
    D7,
    D30,
    CUSTOM,
    NEVER
}

@Serializable
enum class CCTVEventType {
    DETECTION,
    EXTENSION,
    SAVED,
    WARNING,
    PRUNED,
    SYSTEM,
    CLASSIFIED
}

@Serializable
enum class DetectionMode {
    AUTO,
    MANUAL
}

@Serializable
enum class AudioQualityPreset(
    val sampleRate: Int,
    val label: String,
    val subtitle: String
) {
    VOICE_16K(16000, "16 kHz Voice", "Compact storage (~1.9 MB/min)"),
    HIGH_22K(22050, "22.05 kHz Clear", "Balanced acoustic fidelity (~2.6 MB/min)"),
    STUDIO_44K(44100, "44.1 kHz Studio PCM", "Maximum hardware quality (~5.3 MB/min)")
}

@Serializable
data class AudioClassification(
    val tag: String,
    val category: AudioCategory,
    val threatLevel: ThreatLevel,
    val confidence: Float,
    val explanation: String,
    val detectedAt: Long,
    val engine: String = "on-device-dsp"
)

@Serializable
data class AudioRecording(
    val id: String,
    val timestamp: Long,
    val dateString: String,
    val duration: Double,
    val filePath: String,
    val sizeBytes: Long,
    val sampleRate: Int = 44100,
    val peakDecibels: Int,
    val avgDecibels: Int,
    val triggersCount: Int,
    val isProtected: Boolean = false,
    val customTitle: String? = null,
    val notes: String? = null,
    val waveformData: List<Int> = emptyList(),
    val classification: AudioClassification? = null
)

@Serializable
data class CCTVSettings(
    val isArmed: Boolean = false,
    val detectionMode: DetectionMode = DetectionMode.AUTO,
    val sensitivityThreshold: Int = 42, // Manual 0 - 100 scale
    val autoSensitivityMargin: Int = 12, // Margin above ambient noise floor in AUTO mode (6 - 30)
    val postRollSeconds: Int = 10, // Default 10s post-roll extension
    val audioQuality: AudioQualityPreset = AudioQualityPreset.STUDIO_44K,
    val storageQuotaMB: Int = 250, // Default 250 MB local quota
    val warningThresholdPercent: Int = 85,
    val retentionMode: RetentionMode = RetentionMode.H24,
    val customRetentionHours: Int = 24,
    val autoPruneOnFull: Boolean = true,
    val enableNotifications: Boolean = true,
    val enableVibration: Boolean = true,
    val enableSignalAnalysis: Boolean = true
)

@Serializable
data class StorageStats(
    val usedBytes: Long = 0L,
    val quotaBytes: Long = 250L * 1024L * 1024L,
    val deviceFreeBytes: Long = 0L,
    val deviceTotalBytes: Long = 0L,
    val usedPercentage: Float = 0f,
    val isNearFull: Boolean = false,
    val isFull: Boolean = false,
    val recordingsCount: Int = 0,
    val protectedCount: Int = 0
)

@Serializable
data class CCTVEvent(
    val id: String,
    val timestamp: Long,
    val type: CCTVEventType,
    val message: String,
    val decibels: Int? = null,
    val tag: String? = null,
    val threatLevel: ThreatLevel? = null,
    val confidence: Float? = null
)
