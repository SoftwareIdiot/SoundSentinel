package com.softwareidiot.soundsentinel.data.repository

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.softwareidiot.soundsentinel.data.model.AudioClassification
import com.softwareidiot.soundsentinel.data.model.AudioRecording
import com.softwareidiot.soundsentinel.data.model.CCTVEvent
import com.softwareidiot.soundsentinel.data.model.CCTVEventType
import com.softwareidiot.soundsentinel.data.model.CCTVSettings
import com.softwareidiot.soundsentinel.data.model.StorageStats
import com.softwareidiot.soundsentinel.data.model.ThreatLevel
import com.softwareidiot.soundsentinel.logic.AcousticClassifier
import com.softwareidiot.soundsentinel.logic.CctvLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CctvRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private val _settings = MutableStateFlow(CCTVSettings())
    val settings: StateFlow<CCTVSettings> = _settings.asStateFlow()

    private val _recordings = MutableStateFlow<List<AudioRecording>>(emptyList())
    val recordings: StateFlow<List<AudioRecording>> = _recordings.asStateFlow()

    private val _events = MutableStateFlow<List<CCTVEvent>>(emptyList())
    val events: StateFlow<List<CCTVEvent>> = _events.asStateFlow()

    private val _storageStats = MutableStateFlow(StorageStats())
    val storageStats: StateFlow<StorageStats> = _storageStats.asStateFlow()

    val recordingsDir: File = File(context.filesDir, "cctv_recordings").apply {
        if (!exists()) mkdirs()
    }

    private val settingsFile = File(context.filesDir, "cctv_settings.json")
    private val recordingsMetaFile = File(context.filesDir, "recordings_meta.json")
    private val eventsMetaFile = File(context.filesDir, "cctv_events.json")

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannels()
        loadPersistedData()
    }

    private fun loadPersistedData() {
        scope.launch {
            if (settingsFile.exists()) {
                try {
                    val content = settingsFile.readText()
                    _settings.value = json.decodeFromString<CCTVSettings>(content)
                } catch (e: Exception) {
                    // keep defaults
                }
            }

            val loadedRecordings = if (recordingsMetaFile.exists()) {
                try {
                    val content = recordingsMetaFile.readText()
                    json.decodeFromString<List<AudioRecording>>(content)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            if (eventsMetaFile.exists()) {
                try {
                    val content = eventsMetaFile.readText()
                    _events.value = json.decodeFromString<List<CCTVEvent>>(content)
                } catch (e: Exception) {
                    _events.value = emptyList()
                }
            }

            reconcileDiskAndMetadata(loadedRecordings)
            checkRetentionPruning()
        }
    }

    /**
     * Reconciles metadata with actual .wav files on disk so storage stats are 100% real and accurate.
     */
    fun reconcileDiskAndMetadata(currentList: List<AudioRecording> = _recordings.value) {
        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        val diskWavFiles = recordingsDir.listFiles { _, name -> name.endsWith(".wav") }?.toList() ?: emptyList()
        val diskMap = diskWavFiles.associateBy { it.absolutePath }

        val validRecordings = mutableListOf<AudioRecording>()
        val knownPaths = mutableSetOf<String>()

        for (rec in currentList) {
            val file = File(rec.filePath)
            if (file.exists() && file.length() >= 44) {
                knownPaths.add(file.absolutePath)
                val updatedWave = if (rec.waveformData.size < 20) {
                    extractHighResWaveformFromWav(file, 64)
                } else {
                    rec.waveformData
                }
                validRecordings.add(rec.copy(sizeBytes = file.length(), waveformData = updatedWave))
            }
        }

        // Recover any orphan .wav files on disk not in metadata
        for ((path, file) in diskMap) {
            if (path !in knownPaths && file.length() >= 44) {
                val ts = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis()
                val features = AcousticClassifier.extractFeaturesFromWav(file)
                val sr = features?.sampleRate ?: 44100
                val dur = features?.durationSeconds ?: ((file.length() - 44).toDouble() / (sr * 2.0))
                val peakScore = if (features != null) CctvLogic.mapDecibelsToScale(features.peakDb) else 55
                val avgScore = if (features != null) CctvLogic.mapDecibelsToScale(features.avgDb) else 35
                val wave = extractHighResWaveformFromWav(file, 64)
                val classification = AcousticClassifier.classify(file, peakScore, avgScore, dur, 1)

                validRecordings.add(
                    AudioRecording(
                        id = file.nameWithoutExtension,
                        timestamp = ts,
                        dateString = CctvLogic.formatDateTime(ts),
                        duration = dur,
                        filePath = file.absolutePath,
                        sizeBytes = file.length(),
                        sampleRate = sr,
                        peakDecibels = peakScore,
                        avgDecibels = avgScore,
                        triggersCount = 1,
                        isProtected = false,
                        waveformData = wave,
                        classification = classification
                    )
                )
            }
        }

        val sorted = validRecordings.sortedByDescending { it.timestamp }
        _recordings.value = sorted
        persistRecordingsMeta(sorted)
        recalculateStorage()
    }

    fun updateSettings(newSettings: CCTVSettings) {
        val oldQuota = _settings.value.storageQuotaMB
        _settings.value = newSettings
        scope.launch {
            try {
                settingsFile.writeText(json.encodeToString(newSettings))
            } catch (e: Exception) {
                // ignore
            }
            if (newSettings.autoPruneOnFull && newSettings.storageQuotaMB < oldQuota) {
                val quotaBytes = newSettings.storageQuotaMB * 1024L * 1024L
                val currentUsed = _recordings.value.sumOf { File(it.filePath).let { f -> if (f.exists()) f.length() else it.sizeBytes } }
                if (currentUsed > quotaBytes) {
                    pruneOldestRecordings(currentUsed - quotaBytes)
                }
            }
            recalculateStorage()
            checkRetentionPruning()
        }
    }

    fun logEvent(event: CCTVEvent) {
        val updated = listOf(event) + _events.value.take(250)
        _events.value = updated
        scope.launch {
            try {
                eventsMetaFile.writeText(json.encodeToString(updated))
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun clearEvents() {
        _events.value = emptyList()
        scope.launch {
            try {
                if (eventsMetaFile.exists()) eventsMetaFile.delete()
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    suspend fun savePcmClip(
        pcmBytes: ByteArray,
        sampleRate: Int,
        startTime: Long,
        peakScore: Int,
        avgScore: Int,
        triggersCount: Int,
        waveform: List<Int>,
        userHint: String? = null
    ): AudioRecording = withContext(Dispatchers.IO) {
        val currentQuota = _settings.value.storageQuotaMB * 1024L * 1024L
        val incomingSize = pcmBytes.size + 44L

        if (_settings.value.autoPruneOnFull) {
            val projected = _storageStats.value.usedBytes + incomingSize
            if (projected > currentQuota) {
                val needed = projected - currentQuota
                pruneOldestRecordings(needed)
            }
        }

        if (!recordingsDir.exists()) recordingsDir.mkdirs()

        val id = "cctv-${startTime}"
        val wavFile = File(recordingsDir, "$id.wav")
        FileOutputStream(wavFile).use { out ->
            writeWavHeader(out, pcmBytes.size, sampleRate)
            out.write(pcmBytes)
            out.fd.sync()
        }

        val duration = pcmBytes.size.toDouble() / (sampleRate * 2.0)
        val highResWaveform = extractHighResWaveformFromPcmBytes(pcmBytes, 64).ifEmpty { waveform }

        val classification = if (_settings.value.enableSignalAnalysis) {
            AcousticClassifier.classify(
                recordingFile = wavFile,
                peakScore = peakScore,
                avgScore = avgScore,
                durationSeconds = duration,
                triggersCount = triggersCount,
                userHint = userHint
            )
        } else null

        val recording = AudioRecording(
            id = id,
            timestamp = startTime,
            dateString = CctvLogic.formatDateTime(startTime),
            duration = duration,
            filePath = wavFile.absolutePath,
            sizeBytes = wavFile.length(),
            sampleRate = sampleRate,
            peakDecibels = peakScore,
            avgDecibels = avgScore,
            triggersCount = maxOf(1, triggersCount),
            isProtected = false,
            notes = userHint,
            waveformData = highResWaveform,
            classification = classification
        )

        val updatedRecordings = listOf(recording) + _recordings.value
        _recordings.value = updatedRecordings
        persistRecordingsMeta(updatedRecordings)
        recalculateStorage()

        logEvent(
            CCTVEvent(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = CCTVEventType.SAVED,
                message = "Saved ${sampleRate / 1000.0}kHz PCM clip: ${CctvLogic.formatDuration(duration)} • ${CctvLogic.formatBytes(wavFile.length())}",
                decibels = peakScore,
                tag = classification?.tag,
                threatLevel = classification?.threatLevel,
                confidence = classification?.confidence
            )
        )

        if (classification != null) {
            logEvent(
                CCTVEvent(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    type = CCTVEventType.CLASSIFIED,
                    message = "Local DSP: ${classification.tag} (${(classification.confidence * 100).toInt()}%) • ${classification.explanation}",
                    decibels = peakScore,
                    tag = classification.tag,
                    threatLevel = classification.threatLevel,
                    confidence = classification.confidence
                )
            )

            if (_settings.value.enableNotifications &&
                (classification.threatLevel == ThreatLevel.HIGH || classification.threatLevel == ThreatLevel.CRITICAL)
            ) {
                sendThreatNotification(classification, peakScore)
            }
        }

        recording
    }

    /**
     * Generates a 64-bar high-resolution normalized waveform from raw 16-bit little-endian PCM bytes.
     */
    fun extractHighResWaveformFromPcmBytes(pcmBytes: ByteArray, barsCount: Int = 64): List<Int> {
        val totalSamples = pcmBytes.size / 2
        if (totalSamples <= 0) return emptyList()

        val samplesPerBar = max(1, totalSamples / barsCount)
        val rawPeaks = IntArray(barsCount)
        var globalMax = 1

        val buffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (b in 0 until barsCount) {
            val startSample = b * samplesPerBar
            val endSample = min(totalSamples, startSample + samplesPerBar)
            if (startSample >= endSample) break

            var localPeak = 0
            // Step through samples in bucket
            val step = max(1, (endSample - startSample) / 40)
            var idx = startSample
            while (idx < endSample) {
                val byteIndex = idx * 2
                if (byteIndex + 1 < pcmBytes.size) {
                    val sampleVal = abs(buffer.getShort(byteIndex).toInt())
                    if (sampleVal > localPeak) localPeak = sampleVal
                }
                idx += step
            }
            rawPeaks[b] = localPeak
            if (localPeak > globalMax) globalMax = localPeak
        }

        // Normalize to 12..100 so even faint sounds show a clear, readable waveform contour
        return rawPeaks.map { peak ->
            val ratio = peak.toDouble() / globalMax.toDouble()
            (12 + (ratio * 88.0).toInt()).coerceIn(12, 100)
        }
    }

    private fun extractHighResWaveformFromWav(wavFile: File, barsCount: Int = 64): List<Int> {
        if (!wavFile.exists() || wavFile.length() <= 44) return emptyList()
        return try {
            val bytes = wavFile.readBytes()
            if (bytes.size <= 44) return emptyList()
            val pcmBytes = bytes.copyOfRange(44, bytes.size)
            extractHighResWaveformFromPcmBytes(pcmBytes, barsCount)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun renameRecording(id: String, newTitle: String) {
        val trimmed = newTitle.trim().takeIf { it.isNotEmpty() }
        val updated = _recordings.value.map {
            if (it.id == id) it.copy(customTitle = trimmed) else it
        }
        _recordings.value = updated
        scope.launch {
            persistRecordingsMeta(updated)
        }
    }

    /**
     * Exports a recording WAV file directly to the phone's public Music/SoundSentinel folder
     * using MediaStore (zero storage permissions required on Android 10+).
     */
    suspend fun exportRecordingToDevice(recording: AudioRecording): String? = withContext(Dispatchers.IO) {
        val sourceFile = File(recording.filePath)
        if (!sourceFile.exists()) return@withContext null

        val cleanTitle = (recording.customTitle ?: recording.classification?.tag ?: "Recording")
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val fileName = "SoundSentinel_${cleanTitle}_${recording.timestamp}.wav"

        return@withContext try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/SoundSentinel")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri: Uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: return@withContext null

                resolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceFile).use { input ->
                        input.copyTo(out)
                    }
                }

                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                "Music/SoundSentinel/$fileName"
            } else {
                @Suppress("DEPRECATION")
                val destDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "SoundSentinel")
                if (!destDir.exists()) destDir.mkdirs()
                val destFile = File(destDir, fileName)
                sourceFile.copyTo(destFile, overwrite = true)
                destFile.absolutePath
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Shares a recording WAV file using Android's native Share sheet via FileProvider.
     */
    fun shareRecording(recording: AudioRecording) {
        val file = File(recording.filePath)
        if (!file.exists()) return
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/wav"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, recording.customTitle ?: "SoundSentinel Audio Recording")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(shareIntent, "Share Audio Recording").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun toggleProtect(id: String) {
        val updated = _recordings.value.map {
            if (it.id == id) it.copy(isProtected = !it.isProtected) else it
        }
        _recordings.value = updated
        scope.launch {
            persistRecordingsMeta(updated)
            recalculateStorage()
        }
    }

    fun deleteRecording(id: String) {
        val target = _recordings.value.find { it.id == id }
        if (target != null) {
            try {
                File(target.filePath).delete()
            } catch (e: Exception) {
                // ignore
            }
        }
        val updated = _recordings.value.filter { it.id != id }
        _recordings.value = updated
        scope.launch {
            persistRecordingsMeta(updated)
            recalculateStorage()
        }
    }

    fun clearAllUnprotected() {
        val toDelete = _recordings.value.filter { !it.isProtected }
        for (r in toDelete) {
            try {
                File(r.filePath).delete()
            } catch (e: Exception) {
                // ignore
            }
        }
        val updated = _recordings.value.filter { it.isProtected }
        _recordings.value = updated
        scope.launch {
            persistRecordingsMeta(updated)
            recalculateStorage()
        }
        logEvent(
            CCTVEvent(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = CCTVEventType.PRUNED,
                message = "Cleared ${toDelete.size} unprotected recording(s) from device storage"
            )
        )
    }

    fun reclassifyRecording(id: String): AudioRecording? {
        val recording = _recordings.value.find { it.id == id } ?: return null
        val wavFile = File(recording.filePath)
        val classification = AcousticClassifier.classify(
            recordingFile = if (wavFile.exists()) wavFile else null,
            peakScore = recording.peakDecibels,
            avgScore = recording.avgDecibels,
            durationSeconds = recording.duration,
            triggersCount = recording.triggersCount,
            userHint = recording.notes
        )
        val updatedWave = if (wavFile.exists()) extractHighResWaveformFromWav(wavFile, 64) else recording.waveformData
        val updatedRecording = recording.copy(classification = classification, waveformData = updatedWave)
        val updatedList = _recordings.value.map {
            if (it.id == id) updatedRecording else it
        }
        _recordings.value = updatedList
        scope.launch {
            persistRecordingsMeta(updatedList)
        }
        logEvent(
            CCTVEvent(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = CCTVEventType.CLASSIFIED,
                message = "Re-analyzed on-device DSP: ${classification.tag} (${(classification.confidence * 100).toInt()}%)",
                decibels = recording.peakDecibels,
                tag = classification.tag,
                threatLevel = classification.threatLevel,
                confidence = classification.confidence
            )
        )
        return updatedRecording
    }

    private fun pruneOldestRecordings(bytesNeeded: Long) {
        val idsToPrune = CctvLogic.identifyOldestToPrune(_recordings.value, bytesNeeded)
        if (idsToPrune.isEmpty()) return

        for (id in idsToPrune) {
            val rec = _recordings.value.find { it.id == id }
            if (rec != null) {
                try { File(rec.filePath).delete() } catch (e: Exception) {}
            }
        }
        val updated = _recordings.value.filter { it.id !in idsToPrune }
        _recordings.value = updated
        persistRecordingsMeta(updated)
        recalculateStorage()

        logEvent(
            CCTVEvent(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = CCTVEventType.PRUNED,
                message = "Circular storage overwrite pruned ${idsToPrune.size} oldest clip(s) to free space"
            )
        )
    }

    private fun checkRetentionPruning() {
        val retentionMs = CctvLogic.getRetentionDurationMs(
            _settings.value.retentionMode,
            _settings.value.customRetentionHours
        )
        val expiredIds = CctvLogic.identifyExpiredRecordings(_recordings.value, retentionMs)
        if (expiredIds.isNotEmpty()) {
            for (id in expiredIds) {
                val rec = _recordings.value.find { it.id == id }
                if (rec != null) {
                    try { File(rec.filePath).delete() } catch (e: Exception) {}
                }
            }
            val updated = _recordings.value.filter { it.id !in expiredIds }
            _recordings.value = updated
            persistRecordingsMeta(updated)
            recalculateStorage()
            logEvent(
                CCTVEvent(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    type = CCTVEventType.PRUNED,
                    message = "Retention policy expired and pruned ${expiredIds.size} recording(s)"
                )
            )
        }
    }

    fun recalculateStorage() {
        val quotaBytes = _settings.value.storageQuotaMB * 1024L * 1024L
        var freeDisk = 0L
        var totalDisk = 0L
        try {
            val stat = StatFs(context.filesDir.absolutePath)
            freeDisk = stat.availableBytes
            totalDisk = stat.totalBytes
        } catch (ignored: Exception) {}

        val stats = CctvLogic.calculateStorageStats(
            recordings = _recordings.value,
            quotaBytes = quotaBytes,
            deviceFreeBytes = freeDisk,
            deviceTotalBytes = totalDisk,
            warningThresholdPercent = _settings.value.warningThresholdPercent
        )
        _storageStats.value = stats

        if (stats.isNearFull && _settings.value.enableNotifications) {
            sendStorageWarningNotification(stats.usedPercentage, stats.usedBytes, quotaBytes)
        }
    }

    private fun persistRecordingsMeta(list: List<AudioRecording>) {
        try {
            recordingsMetaFile.writeText(json.encodeToString(list))
        } catch (e: Exception) {
            // ignore
        }
    }

    fun vibrateAlert() {
        if (!_settings.value.enableVibration) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(180, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(180)
                }
            }
        } catch (e: Exception) {
            // ignore
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val alertChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "SoundSentinel Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Critical sound and storage warning notifications"
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }

    fun sendThreatNotification(classification: AudioClassification, peakScore: Int) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 SoundSentinel: ${classification.tag}")
            .setContentText("Alert Level: ${classification.threatLevel.name} • Peak $peakScore/100")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("${classification.explanation}\nConfidence: ${(classification.confidence * 100).toInt()}% • Saved to Recordings Vault.")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        notificationManager.notify(7143, builder.build())
    }

    fun sendStorageWarningNotification(usedPercent: Float, usedBytes: Long, quotaBytes: Long) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("⚠️ SoundSentinel Storage Alert")
            .setContentText("Used: ${usedPercent.toInt()}% (${CctvLogic.formatBytes(usedBytes)} / ${CctvLogic.formatBytes(quotaBytes)}). Oldest clips will be auto-purged.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        notificationManager.notify(7144, builder.build())
    }

    private fun writeWavHeader(out: FileOutputStream, dataLength: Int, sampleRate: Int) {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put(byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte()))
        h.putInt(36 + dataLength)
        h.put(byteArrayOf('W'.code.toByte(), 'A'.code.toByte(), 'V'.code.toByte(), 'E'.code.toByte()))
        h.put(byteArrayOf('f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(), ' '.code.toByte()))
        h.putInt(16) // Subchunk1Size (16 for PCM)
        h.putShort(1.toShort()) // AudioFormat (1 for PCM)
        h.putShort(1.toShort()) // NumChannels (1 for Mono)
        h.putInt(sampleRate)
        h.putInt(sampleRate * 2) // ByteRate (SampleRate * NumChannels * BitsPerSample/8)
        h.putShort(2.toShort()) // BlockAlign (NumChannels * BitsPerSample/8)
        h.putShort(16.toShort()) // BitsPerSample
        h.put(byteArrayOf('d'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte()))
        h.putInt(dataLength)
        out.write(h.array())
    }

    companion object {
        const val CHANNEL_ALERTS = "sound_sentinel_alerts"

        @Volatile
        private var INSTANCE: CctvRepository? = null

        fun getInstance(context: Context): CctvRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CctvRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
