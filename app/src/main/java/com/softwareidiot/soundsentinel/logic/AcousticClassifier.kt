package com.softwareidiot.soundsentinel.logic

import com.softwareidiot.soundsentinel.data.model.AudioCategory
import com.softwareidiot.soundsentinel.data.model.AudioClassification
import com.softwareidiot.soundsentinel.data.model.ThreatLevel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 100% Offline On-Device Acoustic DSP Signal Analyzer.
 * Analyzes PCM audio waveforms locally using time-domain & spectral-derivative metrics
 * (Crest factor, Zero-Crossing Rate, High-Frequency Energy Ratio, and Burst Cadence)
 * without any network, cloud, or external server dependencies.
 */
object AcousticClassifier {

    data class AudioFeatures(
        val sampleRate: Int,
        val durationSeconds: Double,
        val peakDb: Double,
        val avgDb: Double,
        val crestFactorDb: Double,
        val highFrequencyRatio: Double,
        val zeroCrossingRate: Double,
        val periodicBurstCount: Int,
        val medianBurstDuration: Double,
        val meanBurstInterval: Double,
        val shortLoudTransient: Boolean,
        val sustainedHighTone: Boolean
    )

    /**
     * Reads a 16-bit PCM WAV file and extracts acoustic DSP features.
     */
    fun extractFeaturesFromWav(wavFile: File): AudioFeatures? {
        if (!wavFile.exists() || wavFile.length() < 44) return null
        return try {
            val bytes = wavFile.readBytes()
            if (bytes.size < 44) return null

            val riff = String(bytes, 0, 4, Charsets.US_ASCII)
            val wave = String(bytes, 8, 4, Charsets.US_ASCII)
            if (riff != "RIFF" || wave != "WAVE") return null

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            var offset = 12
            var sampleRate = 44100
            var channels = 1
            var bitsPerSample = 16
            var dataOffset = -1
            var dataLength = 0

            while (offset + 8 <= bytes.size) {
                val chunkId = String(bytes, offset, 4, Charsets.US_ASCII)
                val chunkLen = buffer.getInt(offset + 4)
                val chunkStart = offset + 8

                if (chunkId == "fmt " && chunkLen >= 16) {
                    buffer.position(chunkStart)
                    buffer.short // format
                    channels = buffer.short.toInt()
                    sampleRate = buffer.int
                    buffer.int // byteRate
                    buffer.short // blockAlign
                    bitsPerSample = buffer.short.toInt()
                } else if (chunkId == "data") {
                    dataOffset = chunkStart
                    dataLength = chunkLen
                    break
                }
                offset = chunkStart + chunkLen + (chunkLen % 2)
            }

            if (dataOffset < 0 || channels <= 0 || bitsPerSample != 16) return null
            val actualDataLength = min(dataLength, bytes.size - dataOffset)
            val totalSamples = actualDataLength / (channels * 2)
            if (totalSamples <= 0) return null

            // Cap analysis to first 30 seconds of samples for instant UI responsiveness
            val maxSamplesToAnalyze = min(totalSamples, sampleRate * 30)
            val samples = ShortArray(maxSamplesToAnalyze)
            val pcmBuffer = ByteBuffer.wrap(bytes, dataOffset, maxSamplesToAnalyze * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until maxSamplesToAnalyze) {
                samples[i] = pcmBuffer.short
            }

            analyzeSamples(samples, sampleRate, totalSamples.toDouble() / sampleRate.toDouble())
        } catch (e: Exception) {
            null
        }
    }

    fun analyzeSamples(pcm: ShortArray, sampleRate: Int, fullDurationSeconds: Double? = null): AudioFeatures {
        val totalCount = pcm.size
        var squareSum = 0.0
        var highPassSum = 0.0
        var zeroCrossings = 0
        var maxPeak = 0
        var previous = pcm[0].toInt()

        for (i in 0 until totalCount) {
            val sample = pcm[i].toInt()
            val normalized = sample / 32768.0
            squareSum += normalized * normalized
            maxPeak = max(maxPeak, abs(sample))

            if (i > 0) {
                val diff = (sample - previous) / 32768.0
                highPassSum += diff * diff
                if ((sample >= 0 && previous < 0) || (sample < 0 && previous >= 0)) {
                    zeroCrossings++
                }
            }
            previous = sample
        }

        val rms = sqrt(squareSum / max(1, totalCount))
        val avgDb = CctvLogic.calculateDecibelsFromRms(rms)
        val peakDb = if (maxPeak == 0) -80.0 else CctvLogic.calculateDecibelsFromRms(maxPeak / 32768.0)
        val crestFactorDb = max(0.0, peakDb - avgDb)
        val zcr = zeroCrossings.toDouble() / max(1, totalCount).toDouble()

        val highFrequencyRatio = if (rms <= 0.0001 || totalCount < 2) 0.0
        else sqrt(highPassSum / max(1, totalCount - 1)) / (rms * sqrt(2.0))

        // Frame-by-frame burst analysis (20ms frames)
        val frameSamples = max(1, (sampleRate * 0.02).toInt())
        val frameCount = totalCount / frameSamples
        val frameRmsList = mutableListOf<Double>()
        val framePeakList = mutableListOf<Double>()

        for (f in 0 until frameCount) {
            val start = f * frameSamples
            val end = min(totalCount, start + frameSamples)
            var fSquareSum = 0.0
            var fPeak = 0.0
            for (j in start until end) {
                val s = abs(pcm[j] / 32768.0)
                fSquareSum += s * s
                fPeak = max(fPeak, s)
            }
            frameRmsList.add(sqrt(fSquareSum / max(1, end - start)))
            framePeakList.add(fPeak)
        }

        val sortedRms = frameRmsList.sorted()
        val noiseFloor = if (sortedRms.isNotEmpty()) sortedRms[(sortedRms.size * 0.2).toInt()] else 0.001
        val gate = max(0.008, noiseFloor * 2.2)

        val bursts = mutableListOf<Pair<Int, Int>>()
        var inBurst = false
        var burstStart = 0

        for (i in frameRmsList.indices) {
            val active = frameRmsList[i] >= gate
            if (!inBurst && active) {
                inBurst = true
                burstStart = i
            } else if (inBurst && !active) {
                inBurst = false
                if (i - burstStart >= 2) {
                    bursts.add(Pair(burstStart, i))
                }
            }
        }
        if (inBurst && frameRmsList.size - burstStart >= 2) {
            bursts.add(Pair(burstStart, frameRmsList.size))
        }

        val periodicBursts = bursts.filter {
            val dur = (it.second - it.first) * 0.02
            dur in 0.04..1.5
        }

        val intervals = mutableListOf<Double>()
        for (i in 1 until periodicBursts.size) {
            intervals.add((periodicBursts[i].first - periodicBursts[i - 1].first) * 0.02)
        }

        val meanInterval = if (intervals.isNotEmpty()) intervals.average() else 0.0
        val burstDurations = periodicBursts.map { (it.second - it.first) * 0.02 }.sorted()
        val medianBurst = if (burstDurations.isNotEmpty()) burstDurations[burstDurations.size / 2] else 0.0

        val shortLoudTransient = bursts.any {
            val dur = (it.second - it.first) * 0.02
            val localPeak = framePeakList.subList(it.first, it.second).maxOrNull() ?: 0.0
            dur <= 0.20 && localPeak >= 0.30
        }

        val sustainedHighTone = bursts.any {
            val dur = (it.second - it.first) * 0.02
            dur >= 1.8 && (highFrequencyRatio >= 0.55 || zcr >= 0.15)
        }

        return AudioFeatures(
            sampleRate = sampleRate,
            durationSeconds = fullDurationSeconds ?: (totalCount.toDouble() / sampleRate.toDouble()),
            peakDb = peakDb,
            avgDb = avgDb,
            crestFactorDb = crestFactorDb,
            highFrequencyRatio = highFrequencyRatio,
            zeroCrossingRate = zcr,
            periodicBurstCount = periodicBursts.size,
            medianBurstDuration = medianBurst,
            meanBurstInterval = meanInterval,
            shortLoudTransient = shortLoudTransient,
            sustainedHighTone = sustainedHighTone
        )
    }

    /**
     * Classifies the audio recording purely on-device using local acoustic DSP signatures.
     */
    fun classify(
        recordingFile: File?,
        peakScore: Int,
        avgScore: Int,
        durationSeconds: Double,
        triggersCount: Int,
        userHint: String? = null
    ): AudioClassification {
        val now = System.currentTimeMillis()
        val features = if (recordingFile != null && recordingFile.exists()) {
            extractFeaturesFromWav(recordingFile)
        } else null

        if (features != null) {
            // High-frequency sharp transient: Glass Break / Sharp Snap
            if (features.highFrequencyRatio >= 0.68 && features.crestFactorDb >= 9.5 && features.peakDb >= -32.0) {
                return AudioClassification(
                    tag = "Sharp High-Freq Transient",
                    category = AudioCategory.THREAT,
                    threatLevel = ThreatLevel.HIGH,
                    confidence = 0.89f,
                    explanation = "On-device DSP detected high-frequency impulse (crest +${String.format("%.1f", features.crestFactorDb)} dB) consistent with glass or sharp snap.",
                    detectedAt = now,
                    engine = "on-device-dsp"
                )
            }

            // Sustained high tone: Alarm / Siren / Beep
            if (features.sustainedHighTone) {
                return AudioClassification(
                    tag = "Sustained Tone / Alarm",
                    category = AudioCategory.THREAT,
                    threatLevel = ThreatLevel.CRITICAL,
                    confidence = 0.92f,
                    explanation = "On-device DSP detected continuous high-frequency tonal signal across consecutive frames.",
                    detectedAt = now,
                    engine = "on-device-dsp"
                )
            }

            // Periodic rhythmic bursts: Footsteps vs Door Knock
            if (features.periodicBurstCount >= 3 && features.meanBurstInterval in 0.25..1.9) {
                val isLowFreqCadence = features.medianBurstDuration <= 0.24 && features.highFrequencyRatio <= 0.62
                return if (isLowFreqCadence) {
                    AudioClassification(
                        tag = "Rhythmic Cadence / Steps",
                        category = AudioCategory.INTRUSION,
                        threatLevel = ThreatLevel.MEDIUM,
                        confidence = 0.85f,
                        explanation = "Local DSP identified ${features.periodicBurstCount} spaced low-mid frequency bursts matching footsteps or movement.",
                        detectedAt = now,
                        engine = "on-device-dsp"
                    )
                } else {
                    AudioClassification(
                        tag = "Percussive Knock / Tap",
                        category = AudioCategory.INTRUSION,
                        threatLevel = ThreatLevel.MEDIUM,
                        confidence = 0.84f,
                        explanation = "Local DSP identified ${features.periodicBurstCount} clustered percussive impulses matching knocking or tapping.",
                        detectedAt = now,
                        engine = "on-device-dsp"
                    )
                }
            }

            // Loud short transient: Impact / Slam / Clap
            if (features.shortLoudTransient && features.peakDb >= -22.0) {
                return AudioClassification(
                    tag = "Loud Impact / Slam",
                    category = AudioCategory.THREAT,
                    threatLevel = ThreatLevel.HIGH,
                    confidence = 0.87f,
                    explanation = "Local DSP measured a brief, high-amplitude acoustic spike with rapid decay.",
                    detectedAt = now,
                    engine = "on-device-dsp"
                )
            }

            // Speech / Vocal activity
            if (features.durationSeconds >= 1.0 && features.avgDb >= -52.0 && features.zeroCrossingRate in 0.03..0.22) {
                return AudioClassification(
                    tag = "Voice / Vocal Activity",
                    category = AudioCategory.SPEECH,
                    threatLevel = ThreatLevel.LOW,
                    confidence = 0.82f,
                    explanation = "Harmonic energy modulation and zero-crossing profile match human speech or vocalization.",
                    detectedAt = now,
                    engine = "on-device-dsp"
                )
            }

            // Faint / Whisper / Subtle rustle
            if (peakScore < 38) {
                return AudioClassification(
                    tag = "Faint Sound / Whisper",
                    category = AudioCategory.AMBIENT,
                    threatLevel = ThreatLevel.INFO,
                    confidence = 0.78f,
                    explanation = "Low-amplitude acoustic event captured above the quiet room noise floor (Peak $peakScore/100).",
                    detectedAt = now,
                    engine = "on-device-dsp"
                )
            }
        }

        if (peakScore >= 80) {
            return AudioClassification(
                tag = "Loud Acoustic Event",
                category = AudioCategory.THREAT,
                threatLevel = ThreatLevel.HIGH,
                confidence = 0.80f,
                explanation = "High-intensity sound spike reached $peakScore/100 on local sensor.",
                detectedAt = now,
                engine = "on-device-dsp"
            )
        } else if (peakScore >= 50) {
            return AudioClassification(
                tag = "Moderate Sound Activity",
                category = AudioCategory.INTRUSION,
                threatLevel = ThreatLevel.MEDIUM,
                confidence = 0.75f,
                explanation = "Sustained acoustic activity with $triggersCount trigger burst(s) (Peak $peakScore/100).",
                detectedAt = now,
                engine = "on-device-dsp"
            )
        }

        return AudioClassification(
            tag = "Quiet / Ambient Sound",
            category = AudioCategory.AMBIENT,
            threatLevel = ThreatLevel.INFO,
            confidence = 0.72f,
            explanation = "Subtle room sound detected and recorded locally (Peak $peakScore/100).",
            detectedAt = now,
            engine = "on-device-dsp"
        )
    }
}
