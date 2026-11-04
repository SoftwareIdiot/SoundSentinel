# SoundSentinel — Native Android Acoustic CCTV Surveillance

A native Android application written in Kotlin and Jetpack Compose for acoustic surveillance. SoundSentinel turns your device into an acoustic CCTV sensor that continuously monitors audio, captures sounds when they exceed an adaptive threshold with a 2-second pre-roll and 10-second post-roll extension, and manages circular storage.

## Key Features

- **Acoustic Surveillance Engine:** Real-time 16 kHz 16-bit PCM microphone monitoring via `AudioRecord`.
- **Adaptive Detection & Pre-roll:** Tracks ambient room noise floors and transient explosive peaks (e.g., glass breaking, door knocks, impacts) with a circular 2-second pre-roll buffer.
- **10-Second Post-Roll Extension:** Recordings continue for 10 seconds after sound falls quiet. New sound triggers dynamically reset the 10-second countdown (up to 120s max clip).
- **On-Device AI Classification:** Evaluates audio features (crest factor, burst intervals, spectral transients) on-device to classify security events:
  - Glass Breaking (high threat)
  - Footsteps (intrusion alert)
  - Door Knock / Slam (intrusion)
  - Human Voice / Speech (speech)
  - Dog Barking (domestic)
  - Alarm / Siren (critical threat)
  - Ambient Noise (info)
- **Recordings Vault:** Built-in audio player with waveform previews, playback speed controls (0.5x, 1x, 1.5x, 2x), scrubber, search, filter chips, clip locking/protection, and re-classification.
- **Circular Storage Manager:** Rolling storage quota limit (e.g. 25 MB to 1 GB) with oldest-first circular overwrite, retention policies (1h, 6h, 24h, 3d, 7d, 30d, custom), and lock protection for important clips.
- **Event Audit Log:** Complete real-time audit trail of triggers, extensions, saves, warnings, classifications, and system states.
- **Background Foreground Service:** Dedicated microphone foreground service (`AcousticMonitorService`) with ongoing notification and wake-lock to maintain active surveillance when the screen turns off.

## Tech Stack

- **Platform:** Android (Min SDK 26, Target SDK 35)
- **Language:** Kotlin 2.1.0
- **UI Toolkit:** Jetpack Compose (Material 3 Dark Surveillance Theme)
- **Build System:** Gradle (Kotlin DSL), Android Gradle Plugin 8.8.2
- **Audio Capture & Playback:** Native `AudioRecord` (16 kHz 16-bit PCM WAV) & `MediaPlayer`
- **Data & Serialization:** `kotlinx.serialization` (JSON persistence) + StateFlow reactive architecture
