package com.softwareidiot.soundsentinel.logic

import com.softwareidiot.soundsentinel.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MonitoringPolicyTest {
    @Test fun overnightWindowIncludesBothSidesOfMidnightAndExcludesEnd() {
        assertTrue(MonitoringPolicy.isInWindow(23 * 60, 22 * 60, 7 * 60))
        assertTrue(MonitoringPolicy.isInWindow(2 * 60, 22 * 60, 7 * 60))
        assertFalse(MonitoringPolicy.isInWindow(7 * 60, 22 * 60, 7 * 60))
        assertFalse(MonitoringPolicy.isInWindow(12 * 60, 22 * 60, 7 * 60))
    }
    @Test fun normalWindowHasInclusiveStartExclusiveEnd() {
        assertTrue(MonitoringPolicy.isInWindow(540, 540, 1020))
        assertFalse(MonitoringPolicy.isInWindow(1020, 540, 1020))
        assertFalse(MonitoringPolicy.isInWindow(539, 540, 1020))
    }
    @Test fun equalTimesAreAllDay() { assertTrue(MonitoringPolicy.isInWindow(100, 500, 500)) }
    @Test fun disabledDetectionCannotBeOverriddenBySchedule() {
        assertFalse(MonitoringPolicy.canDetect(CCTVSettings(automaticDetectionEnabled = false)))
    }
    @Test fun quietProfileSuppressesAllAlerts() {
        assertFalse(AlertLimiter().accept(AlertProfile.SILENT, ThreatLevel.CRITICAL, 100, 1000, 60))
    }
    @Test fun unimportantEventsDoNotConsumeCooldown() {
        val limiter = AlertLimiter()
        assertFalse(limiter.accept(AlertProfile.IMPORTANT, ThreatLevel.LOW, 80, 1000, 60))
        assertTrue(limiter.accept(AlertProfile.IMPORTANT, ThreatLevel.HIGH, 80, 1001, 60))
        assertFalse(limiter.accept(AlertProfile.IMPORTANT, ThreatLevel.CRITICAL, 90, 2000, 60))
        assertTrue(limiter.accept(AlertProfile.IMPORTANT, ThreatLevel.HIGH, 80, 61001, 60))
    }
    @Test fun everyEventProfileStillRespectsCooldown() {
        val limiter = AlertLimiter()
        assertTrue(limiter.accept(AlertProfile.ALL_EVENTS, null, 30, 0, 30))
        assertFalse(limiter.accept(AlertProfile.ALL_EVENTS, null, 30, 29000, 30))
        assertTrue(limiter.accept(AlertProfile.ALL_EVENTS, null, 30, 30000, 30))
    }
    @Test fun attackTimingIsConsistentForDifferentAudioBufferDurations() {
        for (frameMs in listOf(10L, 20L, 40L)) {
            val gate = AdaptiveAudioGate()
            repeat((1600 / frameMs).toInt()) { gate.process(-60.0, -60.0, -54.0, 6.0, frameMs, true, false) }
            var elapsed = 0L
            var detected = false
            while (elapsed < 80) {
                detected = gate.process(-42.0, -42.0, -34.0, 8.0, frameMs, true, false).detected
                elapsed += frameMs
                if(elapsed < 80) assertFalse(detected)
            }
            assertTrue(detected)
        }
    }
}
