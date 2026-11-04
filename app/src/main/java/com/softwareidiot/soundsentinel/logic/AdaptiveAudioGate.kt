package com.softwareidiot.soundsentinel.logic

import com.softwareidiot.soundsentinel.data.model.DetectionMode
import java.util.ArrayDeque

/**
 * Converts microphone levels into event-start and event-hold decisions.
 *
 * The ambient estimate is based on the lower quartile of recent frames. This lets the gate learn
 * steady room noise without letting a short impact or burst immediately raise the threshold.
 */
class AdaptiveAudioGate {
    data class Reading(
        val noiseFloorDb: Double,
        val thresholdDb: Double,
        val thresholdScale: Int,
        val detected: Boolean,
        val isCalibrating: Boolean
    )

    private data class AmbientFrame(val db: Double, val durationMs: Long)

    private val ambientFrames = ArrayDeque<AmbientFrame>()
    private var ambientWindowMs = 0L
    private var elapsedMs = 0L
    private var lastFloorUpdateMs = 0L
    private var consecutiveLoudFrames = 0

    var noiseFloorDb: Double = -62.0
        private set

    var isCalibrating: Boolean = true
        private set

    fun process(
        rmsDb: Double,
        smoothedDb: Double,
        peakDb: Double,
        crestDb: Double,
        frameDurationMs: Long,
        detectionMode: DetectionMode,
        sensitivityThreshold: Int,
        autoSensitivityMargin: Int,
        clipActive: Boolean
    ): Reading {
        val frameMs = frameDurationMs.coerceAtLeast(1L)
        elapsedMs += frameMs
        ambientFrames.addLast(AmbientFrame(rmsDb.coerceIn(-80.0, 0.0), frameMs))
        ambientWindowMs += frameMs

        while (ambientWindowMs > AMBIENT_WINDOW_MS && ambientFrames.size > 1) {
            ambientWindowMs -= ambientFrames.removeFirst().durationMs
        }

        val shouldUpdateFloor = isCalibrating || elapsedMs - lastFloorUpdateMs >= FLOOR_UPDATE_INTERVAL_MS
        if (shouldUpdateFloor && ambientFrames.size >= MIN_FRAMES_FOR_ESTIMATE) {
            val targetFloor = lowerQuartileDb()
            if (isCalibrating) {
                noiseFloorDb = targetFloor.coerceIn(-72.0, -25.0)
            } else {
                val alpha = if (targetFloor > noiseFloorDb) FLOOR_RISE_ALPHA else FLOOR_FALL_ALPHA
                noiseFloorDb = (noiseFloorDb + (targetFloor - noiseFloorDb) * alpha)
                    .coerceIn(-72.0, -25.0)
            }
            lastFloorUpdateMs = elapsedMs
        }

        if (isCalibrating && elapsedMs >= CALIBRATION_MS && ambientFrames.size >= MIN_FRAMES_FOR_ESTIMATE) {
            noiseFloorDb = lowerQuartileDb().coerceIn(-72.0, -25.0)
            isCalibrating = false
            lastFloorUpdateMs = elapsedMs
        }

        val thresholdDb = when (detectionMode) {
            DetectionMode.AUTO -> CctvLogic.computeAutoThresholdDb(noiseFloorDb, autoSensitivityMargin)
            DetectionMode.MANUAL -> CctvLogic.manualThresholdDb(sensitivityThreshold)
        }
        val thresholdScale = CctvLogic.mapDecibelsToScale(thresholdDb).coerceIn(5, 95)

        // Peak-minus-9 dB catches short events while remaining comparable to the RMS gate.
        val eventDb = maxOf(smoothedDb, peakDb - PEAK_TO_RMS_OFFSET_DB)
        val aboveStartGate = eventDb >= thresholdDb
        if (!clipActive && !isCalibrating) {
            consecutiveLoudFrames = if (aboveStartGate) consecutiveLoudFrames + 1 else 0
        } else {
            consecutiveLoudFrames = 0
        }

        val transientGate = crestDb >= MIN_TRANSIENT_CREST_DB &&
            peakDb - noiseFloorDb >= maxOf(MIN_TRANSIENT_FLOOR_MARGIN_DB, thresholdDb - noiseFloorDb + 4.0)

        val detected = !isCalibrating && (
            if (clipActive) {
                transientGate || eventDb >= thresholdDb - RELEASE_HYSTERESIS_DB
            } else {
                transientGate || consecutiveLoudFrames >= REQUIRED_ATTACK_FRAMES
            }
        )

        return Reading(
            noiseFloorDb = noiseFloorDb,
            thresholdDb = thresholdDb,
            thresholdScale = thresholdScale,
            detected = detected,
            isCalibrating = isCalibrating
        )
    }

    private fun lowerQuartileDb(): Double {
        val levels = ambientFrames.map { it.db }.sorted()
        val index = ((levels.size - 1) * AMBIENT_PERCENTILE).toInt().coerceIn(0, levels.lastIndex)
        return levels[index]
    }

    private companion object {
        const val CALIBRATION_MS = 700L
        const val AMBIENT_WINDOW_MS = 2_800L
        const val FLOOR_UPDATE_INTERVAL_MS = 200L
        const val MIN_FRAMES_FOR_ESTIMATE = 5
        const val AMBIENT_PERCENTILE = 0.25
        const val FLOOR_RISE_ALPHA = 0.28
        const val FLOOR_FALL_ALPHA = 0.42
        const val PEAK_TO_RMS_OFFSET_DB = 9.0
        const val MIN_TRANSIENT_CREST_DB = 8.0
        const val MIN_TRANSIENT_FLOOR_MARGIN_DB = 11.0
        const val RELEASE_HYSTERESIS_DB = 3.0
        const val REQUIRED_ATTACK_FRAMES = 2
    }
}
