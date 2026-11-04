# SoundSentinel 🎙️

### My offline acoustic-monitoring project

Hi! I’m a **19-year-old first-year cybersecurity student**, and SoundSentinel is one of my hands-on projects. I’m exploring Android development, audio processing, and privacy-conscious security tools by building an acoustic version of CCTV.

The idea is simple: arm the phone, let it listen, and save a clip when it detects a sound event. It should listen continuously while armed, but recording starts only when the sound gate is crossed.

## What it can do

- **Arm and listen:** Monitoring runs in an Android foreground service with a persistent notification.
- **Adapt to room noise:** AUTO mode adjusts its threshold as the background changes. MANUAL mode uses the sensitivity set in Preferences.
- **Capture sound events:** Clips include up to two seconds of pre-roll and continue for the quiet period selected in Preferences. A clip is capped at three minutes.
- **Analyze recordings on the phone:** Optional signal processing looks for acoustic patterns and adds a best-effort label. This is local DSP, not a trained AI model or a cloud service, so labels can be wrong.
- **Review and manage clips:** The vault supports playback, waveforms, searching, filtering, and protecting clips. Storage preferences include a quota and retention choices.
- **Keep the app offline:** SoundSentinel does not request internet access. Clips and settings are stored in the app’s private storage on the phone; there is no cloud upload built in. No API key is needed.

## Getting it on an Android phone

The easiest option is the [SoundSentinel 1.1 APK](https://github.com/SoftwareIdiot/SoundSentinel/releases/download/v1.1-offline-recording-fix-20261104/SoundSentinel-Offline-v1.1-CCTV-Fix.apk) from GitHub Releases. Download it on the phone, open it, and follow Android’s prompts to install it. If asked, allow your browser or Files app to install apps from that source. SoundSentinel will ask for microphone and notification permissions when needed.

The project supports **Android 8.0 (API 26) and newer**. For monitoring to continue with the screen off, keep the foreground notification enabled. Some phone makers also apply battery rules that can interrupt background apps.

## Run from Android Studio

1. Install Android Studio on your computer.
2. Clone this repository:

   ```bash
   git clone https://github.com/SoftwareIdiot/SoundSentinel.git
   ```

3. Open the cloned `SoundSentinel` folder in Android Studio and let Gradle finish syncing.
4. Connect an Android phone with USB debugging enabled, or start an emulator.
5. Select the device in Android Studio and press **Run**.

Gradle may need internet access to download build dependencies the first time. The installed app itself is designed to work offline.

## A few places to explore in the code

- `AdaptiveAudioGate.kt` — estimates background noise and decides when a sound event starts.
- `AcousticMonitorService.kt` — keeps the microphone monitoring while the app is armed and saves event clips.
- `AcousticClassifier.kt` — applies local signal-processing rules to label saved clips.
- `SurveillanceScreen.kt` — contains the Home screen and arm/listen status.

## Built with

- Kotlin and Jetpack Compose
- Android `AudioRecord` for microphone capture
- A foreground microphone service for monitoring
- Local files and settings for saved recordings and preferences

## Where I’m taking it

I’m building SoundSentinel as a learning project while studying cybersecurity. I’m especially interested in how mobile permissions, background services, local data storage, and audio signal processing fit together. I’m continuing to test it on real phones; microphones and room acoustics vary, so detection behavior may need tuning for a particular device.

Suggestions and constructive feedback are welcome.
