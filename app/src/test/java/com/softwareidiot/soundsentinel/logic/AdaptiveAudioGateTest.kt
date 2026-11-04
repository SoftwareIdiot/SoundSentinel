package com.softwareidiot.soundsentinel.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveAudioGateTest {
    @Test
    fun stableRoomNoiseAndSmallMicrophonePeaksDoNotTrigger() {
        val gate = AdaptiveAudioGate()
        repeat(60) { read(gate, rmsDb = -41.0, peakDb = -34.0, crestDb = 7.0) }

        var anyTrigger = false
        repeat(900) {
            val reading = read(
                gate,
                rmsDb = -40.7,
                smoothedDb = -40.7,
                peakDb = -30.5,
                crestDb = 10.2
            )
            anyTrigger = anyTrigger || reading.detected
        }

        assertFalse("steady room noise with small peaks must remain idle", anyTrigger)
        assertEquals(-40.7, gate.noiseFloorDb, 1.0)
    }

    @Test
    fun oneMinorPeakDoesNotOpenAClip() {
        val gate = AdaptiveAudioGate()
        warmUp(gate, -55.0)

        val reading = read(
            gate,
            rmsDb = -50.0,
            smoothedDb = -52.0,
            peakDb = -34.0,
            crestDb = 16.0
        )

        assertFalse(reading.detected)
    }

    @Test
    fun strongTransientCanStartAnEventImmediately() {
        val gate = AdaptiveAudioGate()
        warmUp(gate, -60.0)

        val reading = read(
            gate,
            rmsDb = -28.0,
            smoothedDb = -35.0,
            peakDb = -8.0,
            crestDb = 20.0
        )

        assertTrue(reading.detected)
    }

    @Test
    fun detectionSwitchOffPreventsClipsButKeepsTheGateLive() {
        val gate = AdaptiveAudioGate()
        warmUp(gate, -60.0)

        val reading = read(
            gate,
            rmsDb = -20.0,
            smoothedDb = -25.0,
            peakDb = -5.0,
            crestDb = 15.0,
            detectionEnabled = false
        )

        assertFalse(reading.detected)
        assertEquals(reading.thresholdDb, CctvLogic.computeAutoThresholdDb(reading.noiseFloorDb), 0.001)
    }

    @Test
    fun sustainedSimilarSoundIsLearnedAndAllowsPostRollToExpire() {
        val gate = AdaptiveAudioGate()
        warmUp(gate, -60.0)

        var clipStarted = false
        var quietMs = 0L
        var clipStoppedAfterPostRoll = false

        for (frame in 0 until 1_000) {
            val reading = read(
                gate,
                rmsDb = -42.0,
                smoothedDb = -42.0,
                peakDb = -34.0,
                crestDb = 8.0,
                clipActive = clipStarted
            )

            if (reading.detected) {
                clipStarted = true
                quietMs = 0L
            } else if (clipStarted) {
                quietMs += FRAME_MS
                if (quietMs >= POST_ROLL_MS) {
                    clipStoppedAfterPostRoll = true
                    break
                }
            }
        }

        assertTrue("an event above the initial floor should start a clip", clipStarted)
        assertTrue("the learned steady sound should fall below the gate long enough to stop", clipStoppedAfterPostRoll)
        assertTrue("the ambient estimate should follow the sustained sound", gate.noiseFloorDb > -46.0)
    }

    @Test
    fun automaticGateUsesOneFixedMargin() {
        assertEquals(-33.0, CctvLogic.computeAutoThresholdDb(-45.0), 0.001)
    }

    private fun warmUp(gate: AdaptiveAudioGate, roomDb: Double) {
        repeat(70) {
            read(gate, rmsDb = roomDb, peakDb = roomDb + 6.0, crestDb = 6.0)
        }
        assertFalse("gate should finish calibration", gate.isCalibrating)
    }

    private fun read(
        gate: AdaptiveAudioGate,
        rmsDb: Double,
        smoothedDb: Double = rmsDb,
        peakDb: Double,
        crestDb: Double,
        detectionEnabled: Boolean = true,
        clipActive: Boolean = false
    ) = gate.process(
        rmsDb = rmsDb,
        smoothedDb = smoothedDb,
        peakDb = peakDb,
        crestDb = crestDb,
        frameDurationMs = FRAME_MS,
        detectionEnabled = detectionEnabled,
        clipActive = clipActive
    )

    private companion object {
        const val FRAME_MS = 20L
        const val POST_ROLL_MS = 5_000L
    }
}
