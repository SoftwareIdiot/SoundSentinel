package com.softwareidiot.soundsentinel.logic

import java.util.ArrayDeque

/**
 * Tracks the room's changing acoustic floor and emits event-start/hold decisions.
 *
 * AUTO detection has no user-tuned sensitivity value. It uses a fixed margin above a robust,
 * rolling estimate of recent room noise. Large transients must be both loud above that floor and
 * sufficiently sharp; peak-only microphone noise is not enough to open or hold a clip.
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
    private var aboveGateMs = 0L

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
        detectionEnabled: Boolean,
        clipActive: Boolean
    ): Reading {
        val frameMs = frameDurationMs.coerceAtLeast(1L)
        elapsedMs += frameMs
        ambientFrames.addLast(AmbientFrame(rmsDb.coerceIn(-80.0, 0.0), frameMs))
        ambientWindowMs += frameMs

        while (ambientWindowMs > AMBIENT_WINDOW_MS && ambientFrames.size > 1) {
            ambientWindowMs -= ambientFrames.removeFirst().durationMs
        }

        if (ambientFrames.size >= MIN_FRAMES_FOR_ESTIMATE) {
            if (isCalibrating) {
                noiseFloorDb = lowerQuartileDb().coerceIn(MIN_FLOOR_DB, MAX_FLOOR_DB)
                lastFloorUpdateMs = elapsedMs
            } else if (elapsedMs - lastFloorUpdateMs >= FLOOR_UPDATE_INTERVAL_MS) {
                val targetFloor = lowerQuartileDb()
                val alpha = when {
                    targetFloor > noiseFloorDb && clipActive -> FLOOR_RISE_ALPHA_DURING_CLIP
                    targetFloor > noiseFloorDb -> FLOOR_RISE_ALPHA
                    else -> FLOOR_FALL_ALPHA
                }
                noiseFloorDb = (noiseFloorDb + (targetFloor - noiseFloorDb) * alpha)
                    .coerceIn(MIN_FLOOR_DB, MAX_FLOOR_DB)
                lastFloorUpdateMs = elapsedMs
            }
        }

        if (isCalibrating && elapsedMs >= CALIBRATION_MS && ambientFrames.size >= MIN_FRAMES_FOR_ESTIMATE) {
            noiseFloorDb = lowerQuartileDb().coerceIn(MIN_FLOOR_DB, MAX_FLOOR_DB)
            isCalibrating = false
            lastFloorUpdateMs = elapsedMs
        }

        val thresholdDb = CctvLogic.computeAutoThresholdDb(noiseFloorDb)
        val thresholdScale = CctvLogic.mapDecibelsToScale(thresholdDb).coerceIn(5, 95)
        val activeGateDb = if (clipActive) thresholdDb - RELEASE_HYSTERESIS_DB else thresholdDb
        val aboveGate = smoothedDb >= activeGateDb

        if (detectionEnabled && !isCalibrating && aboveGate) {
            aboveGateMs = (aboveGateMs + frameMs).coerceAtMost(10_000L)
        } else {
            aboveGateMs = 0L
        }

        val requiredAttackMs = if (clipActive) REQUIRED_HOLD_MS else REQUIRED_ATTACK_MS
        val sustainedEvent = aboveGateMs >= requiredAttackMs
        val transientEvent = isStrongTransient(rmsDb, peakDb, crestDb)
        val detected = detectionEnabled && !isCalibrating && (sustainedEvent || transientEvent)

        return Reading(
            noiseFloorDb = noiseFloorDb,
            thresholdDb = thresholdDb,
            thresholdScale = thresholdScale,
            detected = detected,
            isCalibrating = isCalibrating
        )
    }

    private fun isStrongTransient(rmsDb: Double, peakDb: Double, crestDb: Double): Boolean {
        val peakAboveFloorDb = peakDb - noiseFloorDb
        val rmsAboveFloorDb = rmsDb - noiseFloorDb
        return crestDb >= MIN_TRANSIENT_CREST_DB &&
            peakAboveFloorDb >= MIN_TRANSIENT_FLOOR_MARGIN_DB &&
            rmsAboveFloorDb >= MIN_TRANSIENT_RMS_MARGIN_DB &&
            peakDb >= MIN_TRANSIENT_PEAK_DB
    }

    private fun lowerQuartileDb(): Double {
        val levels = ambientFrames.map { it.db }.sorted()
        val index = ((levels.size - 1) * AMBIENT_PERCENTILE).toInt().coerceIn(0, levels.lastIndex)
        return levels[index]
    }

    private companion object {
        const val CALIBRATION_MS = 1_200L
        const val AMBIENT_WINDOW_MS = 2_000L
        const val FLOOR_UPDATE_INTERVAL_MS = 200L
        const val MIN_FRAMES_FOR_ESTIMATE = 8
        const val AMBIENT_PERCENTILE = 0.25
        const val FLOOR_RISE_ALPHA = 0.35
        const val FLOOR_RISE_ALPHA_DURING_CLIP = 0.60
        const val FLOOR_FALL_ALPHA = 0.12
        const val MIN_FLOOR_DB = -72.0
        const val MAX_FLOOR_DB = -25.0
        const val MIN_TRANSIENT_CREST_DB = 14.0
        const val MIN_TRANSIENT_FLOOR_MARGIN_DB = 22.0
        const val MIN_TRANSIENT_RMS_MARGIN_DB = 6.0
        const val MIN_TRANSIENT_PEAK_DB = -32.0
        const val RELEASE_HYSTERESIS_DB = 3.0
        const val REQUIRED_ATTACK_MS = 80L
        const val REQUIRED_HOLD_MS = 40L
    }
}
