package com.softwareidiot.soundsentinel.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.softwareidiot.soundsentinel.MainActivity
import com.softwareidiot.soundsentinel.data.model.CCTVEvent
import com.softwareidiot.soundsentinel.data.model.CCTVEventType
import com.softwareidiot.soundsentinel.data.repository.CctvRepository
import com.softwareidiot.soundsentinel.logic.AdaptiveAudioGate
import com.softwareidiot.soundsentinel.logic.CctvLogic
import com.softwareidiot.soundsentinel.logic.MonitoringPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class MonitorLiveState(
    val isRunning: Boolean = false,
    val isCapturing: Boolean = false,
    val activeSampleRate: Int = 44100,
    val soundLevel: Int = 0,
    val currentDb: Double = -80.0,
    val noiseFloorDb: Double = -62.0,
    val effectiveThreshold: Int = 30,
    val liveWaveformHistory: List<Int> = List(48) { 4 },
    val isCalibrating: Boolean = false,
    val isRecording: Boolean = false,
    val recordingDuration: Double = 0.0,
    val silenceRemaining: Double = 10.0,
    val triggersInSession: Int = 0,
    val error: String? = null
)

class AcousticMonitorService : Service() {

    private val running = AtomicBoolean(false)
    private var audioThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var repository: CctvRepository

    override fun onCreate() {
        super.onCreate()
        repository = CctvRepository.getInstance(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (ACTION_STOP == action) {
            stopMonitoring()
            return START_NOT_STICKY
        }

        try {
            startAsForeground()
        } catch (e: RuntimeException) {
            _liveState.value = MonitorLiveState(error = "Open SoundSentinel and arm again: ${e.message}")
            stopSelf()
            return START_NOT_STICKY
        }

        if (running.compareAndSet(false, true)) {
            acquireWakeLock()
            _liveState.value = _liveState.value.copy(
                isRunning = true,
                isCalibrating = true,
                isRecording = false,
                recordingDuration = 0.0,
                error = null
            )
            audioThread = Thread(::captureLoop, "SoundSentinel-AudioLoop").apply {
                priority = Thread.MAX_PRIORITY
                start()
            }
            repository.logEvent(
                CCTVEvent(
                    id = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    type = CCTVEventType.SYSTEM,
                    message = "SoundSentinel armed (${repository.settings.value.audioQuality.label} • automatic detection ${if (repository.settings.value.automaticDetectionEnabled) "on" else "off"})"
                )
            )
        }

        return START_STICKY
    }

    // The existing foreground service survives task removal; a fresh background
    // microphone start can be rejected by Android permission rules.
    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }

    private fun startAsForeground() {
        val notification = buildNotification(isRecording = false, sampleRate = repository.settings.value.audioQuality.sampleRate)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun openBestAudioRecord(preferredRate: Int): Triple<AudioRecord, Int, Int> {
        val candidateRates = listOf(preferredRate, 44100, 22050, 16000).distinct()
        val candidateSources = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            listOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION, MediaRecorder.AudioSource.UNPROCESSED)
        } else {
            listOf(MediaRecorder.AudioSource.MIC, MediaRecorder.AudioSource.VOICE_RECOGNITION)
        }

        for (rate in candidateRates) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) continue
            val bufferBytes = max(minBuf * 2, (rate * 2) / 10)

            for (source in candidateSources) {
                try {
                    val rec = AudioRecord(
                        source,
                        rate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferBytes
                    )
                    if (rec.state == AudioRecord.STATE_INITIALIZED) {
                        rec.startRecording()
                        if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                            return Triple(rec, rate, bufferBytes)
                        }
                        rec.release()
                    } else {
                        rec.release()
                    }
                } catch (ignored: Exception) {}
            }
        }
        throw IllegalStateException("Microphone could not be opened. Check microphone permission.")
    }

    private fun captureLoop() {
        var audioRecord: AudioRecord? = null
        var clipStream: ByteArrayOutputStream? = null
        val preRoll = ArrayDeque<ByteArray>()
        var preRollBytes = 0L
        val waveform = mutableListOf<Int>()
        val rollingWaveHistory = ArrayDeque<Int>(48).apply {
            repeat(48) { addLast(4) }
        }

        var activeSampleRate = repository.settings.value.audioQuality.sampleRate
        var clipStart = 0L
        var totalLevelSum = 0L
        var sampleCount = 0L
        var quietDurationMs = 0L
        var peakScore = 0
        var triggers = 0
        val audioGate = AdaptiveAudioGate()
        var noiseFloorDb = audioGate.noiseFloorDb
        var smoothedDb = -75.0
        var lastStatusUpdate = 0L
        var lastWaveSampleAt = 0L

        try {
            val (openedRecord, negotiatedRate, bufferBytes) = openBestAudioRecord(activeSampleRate)
            audioRecord = openedRecord
            activeSampleRate = negotiatedRate
            val maxPreRollBytes = activeSampleRate * 2L * 2L

            _liveState.value = _liveState.value.copy(
                isCapturing = true,
                isCalibrating = true,
                isRecording = false,
                recordingDuration = 0.0,
                activeSampleRate = activeSampleRate,
                error = null
            )

            val readSamplesCount = max(256, min(bufferBytes / 2, activeSampleRate / 45))
            val pcm = ShortArray(readSamplesCount)

            while (running.get()) {
                val count = audioRecord.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (count < 0) {
                    throw IllegalStateException("Microphone stream interrupted (code $count)")
                }
                if (count == 0) continue

                var sum = 0.0
                var peakSample = 0

                for (i in 0 until count) {
                    val raw = pcm[i].toInt()
                    val norm = raw / 32768.0
                    sum += norm * norm
                    val absRaw = abs(raw)
                    if (absRaw > peakSample) peakSample = absRaw
                }

                val rms = sqrt(sum / count)
                val db = CctvLogic.calculateDecibelsFromRms(rms)
                val peakDb = if (peakSample == 0) -80.0 else CctvLogic.calculateDecibelsFromRms(peakSample / 32768.0)
                val crestDb = peakDb - db

                val now = System.currentTimeMillis()
                smoothedDb += (db - smoothedDb) * (if (db > smoothedDb) 0.65 else 0.18)

                val currentSettings = repository.settings.value
                val postRollSec = currentSettings.postRollSeconds

                val rmsScale = CctvLogic.mapDecibelsToScale(smoothedDb)
                val peakScale = CctvLogic.mapDecibelsToScale(peakDb)
                val eventLevel = max(rmsScale, (peakScale * 0.82).toInt()).coerceIn(0, 100)
                val frameDurationMs = max(1L, (count * 1000L / activeSampleRate))
                val gate = audioGate.process(
                    rmsDb = db,
                    smoothedDb = smoothedDb,
                    peakDb = peakDb,
                    crestDb = crestDb,
                    frameDurationMs = frameDurationMs,
                    detectionEnabled = MonitoringPolicy.canDetect(currentSettings, now) && !repository.isAlertFeedbackActive(),
                    clipActive = clipStream != null
                )
                noiseFloorDb = gate.noiseFloorDb
                val effectiveThreshold = gate.thresholdScale
                val detected = gate.detected

                val pcmBytes = encodePcm(pcm, count)
                preRoll.addLast(pcmBytes)
                preRollBytes += pcmBytes.size
                while (preRollBytes > maxPreRollBytes && preRoll.size > 1) {
                    preRollBytes -= preRoll.removeFirst().size
                }

                var newClipStarted = false

                if (detected) {
                    if (clipStream == null) {
                        clipStream = ByteArrayOutputStream()
                        val preRollDurMs = (preRollBytes / 2L * 1000L / activeSampleRate)
                        clipStart = now - preRollDurMs
                        for (prevFrame in preRoll) {
                            clipStream.write(prevFrame, 0, prevFrame.size)
                        }
                        peakScore = eventLevel
                        totalLevelSum = 0L
                        sampleCount = 0L
                        triggers = 0
                        waveform.clear()
                        quietDurationMs = 0L
                        newClipStarted = true

                        // Defer feedback until save to avoid vibrating into the microphone.
                        repository.logEvent(
                            CCTVEvent(
                                id = UUID.randomUUID().toString(),
                                timestamp = now,
                                type = CCTVEventType.DETECTION,
                                message = "Sound event detected ($eventLevel/100 reached automatic gate $effectiveThreshold/100)",
                                decibels = eventLevel
                            )
                        )
                    } else if (quietDurationMs >= 450) {
                        triggers++
                        repository.logEvent(
                            CCTVEvent(
                                id = UUID.randomUUID().toString(),
                                timestamp = now,
                                type = CCTVEventType.EXTENSION,
                                message = "Sound re-trigger extended recording (+${postRollSec}s)",
                                decibels = eventLevel
                            )
                        )
                    }

                    quietDurationMs = 0L
                    peakScore = max(peakScore, eventLevel)
                    if (newClipStarted) {
                        updateNotification(isRecording = true, sampleRate = activeSampleRate)
                    }
                }

                if (clipStream != null) {
                    if (!newClipStarted) {
                        clipStream.write(pcmBytes, 0, pcmBytes.size)
                    }
                    totalLevelSum += eventLevel
                    sampleCount++
                    peakScore = max(peakScore, eventLevel)
                    if (waveform.size < 64 && now - lastWaveSampleAt >= 160) {
                        waveform.add(max(8, eventLevel))
                        lastWaveSampleAt = now
                    }

                    if (!detected) {
                        quietDurationMs += frameDurationMs
                    }

                    val clipDurationMs = clipStream.size() * 1000L / (activeSampleRate * 2L)
                    val postRollMs = postRollSec * 1000L

                    if (clipDurationMs >= MAX_CLIP_SECONDS * 1000L || (!detected && quietDurationMs >= postRollMs)) {
                        val clipData = clipStream.toByteArray()
                        val avgScore = if (sampleCount == 0L) peakScore else (totalLevelSum / sampleCount).toInt()
                        val clipWave = waveform.toList()
                        val cTriggers = triggers + 1
                        val cStart = clipStart
                        val cRate = activeSampleRate
                        val cPeakScore = peakScore

                        saveClipAsync(
                            pcmBytes = clipData,
                            sampleRate = cRate,
                            startTime = cStart,
                            peakScore = cPeakScore,
                            avgScore = avgScore,
                            triggersCount = cTriggers,
                            waveform = clipWave
                        )

                        clipStream = null
                        quietDurationMs = 0L
                        updateNotification(isRecording = false, sampleRate = activeSampleRate)
                    }
                }

                if (now - lastStatusUpdate >= 32) {
                    if (rollingWaveHistory.size >= 48) rollingWaveHistory.removeFirst()
                    rollingWaveHistory.addLast(max(4, eventLevel))

                    val currentClipSize = clipStream?.size() ?: 0
                    val durSeconds = currentClipSize.toDouble() / (activeSampleRate * 2.0)
                    val postRollMs = postRollSec * 1000L
                    val silenceLeft = if (clipStream == null) postRollSec.toDouble()
                    else max(0.0, (postRollMs - quietDurationMs) / 1000.0)

                    _liveState.value = _liveState.value.copy(
                        activeSampleRate = activeSampleRate,
                        soundLevel = eventLevel,
                        currentDb = smoothedDb,
                        noiseFloorDb = noiseFloorDb,
                        effectiveThreshold = effectiveThreshold,
                        liveWaveformHistory = rollingWaveHistory.toList(),
                        isCalibrating = gate.isCalibrating,
                        isRecording = clipStream != null,
                        recordingDuration = durSeconds,
                        silenceRemaining = silenceLeft,
                        triggersInSession = triggers + if (clipStream != null) 1 else 0
                    )
                    lastStatusUpdate = now
                }
            }
        } catch (t: Throwable) {
            val msg = t.message ?: t.javaClass.simpleName
            _liveState.value = _liveState.value.copy(
                isCapturing = false,
                isCalibrating = false,
                error = "Microphone error: $msg"
            )
        } finally {
            if (clipStream != null && clipStream.size() > 0) {
                val clipData = clipStream.toByteArray()
                val avgScore = if (sampleCount == 0L) peakScore else (totalLevelSum / sampleCount).toInt()
                val clipWave = waveform.toList()
                val cTriggers = triggers + 1
                val cStart = clipStart
                val cRate = activeSampleRate
                val cPeakScore = peakScore

                saveClipAsync(
                    pcmBytes = clipData,
                    sampleRate = cRate,
                    startTime = cStart,
                    peakScore = cPeakScore,
                    avgScore = avgScore,
                    triggersCount = cTriggers,
                    waveform = clipWave
                )
            }

            try {
                audioRecord?.stop()
            } catch (ignored: Exception) {}
            audioRecord?.release()

            val shouldStop = running.getAndSet(false)
            run {
                releaseWakeLock()
                _liveState.value = _liveState.value.copy(
                    isRunning = false,
                    isCapturing = false,
                    isCalibrating = false,
                    isRecording = false,
                    soundLevel = 0,
                    currentDb = -80.0
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (shouldStop) stopSelf()
            }
        }
    }

    private fun saveClipAsync(
        pcmBytes: ByteArray,
        sampleRate: Int,
        startTime: Long,
        peakScore: Int,
        avgScore: Int,
        triggersCount: Int,
        waveform: List<Int>
    ) {
        scope.launch {
            try {
                repository.savePcmClip(
                    pcmBytes = pcmBytes,
                    sampleRate = sampleRate,
                    startTime = startTime,
                    peakScore = peakScore,
                    avgScore = avgScore,
                    triggersCount = triggersCount,
                    waveform = waveform
                )
            } catch (e: Exception) {
                val details = e.message ?: e.javaClass.simpleName
                repository.logEvent(
                    CCTVEvent(
                        id = UUID.randomUUID().toString(),
                        timestamp = System.currentTimeMillis(),
                        type = CCTVEventType.WARNING,
                        message = "Clip save failed: $details",
                        decibels = peakScore
                    )
                )
                _liveState.value = _liveState.value.copy(error = "Clip save failed: $details")
            }
        }
    }

    private fun stopMonitoring() {
        running.set(false)
        if (Thread.currentThread() != audioThread) audioThread?.join(800)
        releaseWakeLock()
        _liveState.value = _liveState.value.copy(
            isRunning = false,
            isCapturing = false,
            isCalibrating = false,
            isRecording = false
        )
        repository.logEvent(
            CCTVEvent(
                id = UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                type = CCTVEventType.SYSTEM,
                message = "SoundSentinel disarmed"
            )
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SoundSentinel::MonitorWakeLock").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (ignored: Exception) {}
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        } catch (ignored: Exception) {}
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SoundSentinel Background Monitoring",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification keeping microphone monitoring active in the background"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isRecording: Boolean, sampleRate: Int): Notification {
        val stopIntent = Intent(this, AcousticMonitorService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            this,
            2,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val rateKhz = String.format("%.1f kHz", sampleRate / 1000.0)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(if (isRecording) "🔴 SoundSentinel • Recording Event ($rateKhz)" else "🎙️ SoundSentinel • Armed in Background ($rateKhz)")
            .setContentText(
                if (isRecording) "Capturing audio with post-roll extension. Tap to open."
                else if (repository.settings.value.automaticDetectionEnabled) {
                    "Adaptive event detection active. Tap Disarm to stop."
                } else {
                    "Microphone monitoring active; event recording is off. Tap Disarm to stop."
                }
            )
            .setOngoing(true)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_media_pause, "Disarm", stopPending)
            .build()
    }

    private fun updateNotification(isRecording: Boolean, sampleRate: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(isRecording, sampleRate))
    }

    private fun encodePcm(pcm: ShortArray, count: Int): ByteArray {
        val bytes = ByteArray(count * 2)
        for (i in 0 until count) {
            val s = pcm[i].toInt()
            bytes[i * 2] = (s and 0xFF).toByte()
            bytes[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    override fun onDestroy() {
        running.set(false)
        if (Thread.currentThread() != audioThread) audioThread?.join(800)
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.softwareidiot.soundsentinel.START"
        const val ACTION_STOP = "com.softwareidiot.soundsentinel.STOP"
        private const val CHANNEL_ID = "sound_sentinel_monitor"
        private const val NOTIFICATION_ID = 7142
        private const val MAX_CLIP_SECONDS = 180

        private val _liveState = MutableStateFlow(MonitorLiveState())
        val liveState: StateFlow<MonitorLiveState> = _liveState.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AcousticMonitorService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AcousticMonitorService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

    }
}
