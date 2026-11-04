package com.softwareidiot.soundsentinel.logic

import com.softwareidiot.soundsentinel.data.model.ThreatLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AcousticClassifierTest {
    @Test
    fun moderateHighFrequencyNoiseDoesNotBecomeAHighThreatAlert() {
        val result = AcousticClassifier.classifyFeatures(
            features = features(
                peakDb = -30.0,
                avgDb = -42.0,
                crestFactorDb = 12.0,
                highFrequencyRatio = 0.82,
                sustainedHighTone = true
            ),
            peakScore = 50,
            avgScore = 40,
            durationSeconds = 2.0,
            triggersCount = 1
        )

        assertNotEquals(ThreatLevel.HIGH, result.threatLevel)
        assertNotEquals(ThreatLevel.CRITICAL, result.threatLevel)
    }

    @Test
    fun strongSharpTransientCanStillBeMarkedHigh() {
        val result = AcousticClassifier.classifyFeatures(
            features = features(
                peakDb = -14.0,
                avgDb = -45.0,
                crestFactorDb = 31.0,
                highFrequencyRatio = 0.85
            ),
            peakScore = 70,
            avgScore = 30,
            durationSeconds = 1.0,
            triggersCount = 1
        )

        assertEquals(ThreatLevel.HIGH, result.threatLevel)
    }

    @Test
    fun quietContinuousToneDoesNotBecomeCritical() {
        val result = AcousticClassifier.classifyFeatures(
            features = features(
                peakDb = -30.0,
                avgDb = -40.0,
                crestFactorDb = 10.0,
                highFrequencyRatio = 0.60,
                sustainedHighTone = true
            ),
            peakScore = 50,
            avgScore = 40,
            durationSeconds = 2.0,
            triggersCount = 1
        )

        assertNotEquals(ThreatLevel.CRITICAL, result.threatLevel)
    }

    private fun features(
        peakDb: Double,
        avgDb: Double,
        crestFactorDb: Double,
        highFrequencyRatio: Double,
        sustainedHighTone: Boolean = false
    ) = AcousticClassifier.AudioFeatures(
        sampleRate = 44_100,
        durationSeconds = 2.0,
        peakDb = peakDb,
        avgDb = avgDb,
        crestFactorDb = crestFactorDb,
        highFrequencyRatio = highFrequencyRatio,
        zeroCrossingRate = 0.10,
        periodicBurstCount = 0,
        medianBurstDuration = 0.0,
        meanBurstInterval = 0.0,
        shortLoudTransient = false,
        sustainedHighTone = sustainedHighTone
    )
}
