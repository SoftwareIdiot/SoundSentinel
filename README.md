# SoundSentinel

I started SoundSentinel because I wanted to explore an acoustic version of CCTV on a phone: keep it armed and listening, but save a clip only when a sound event crosses the detection gate. I’m building it around two ideas: make event capture useful in practice, and keep audio processing and recordings on the device.

SoundSentinel is a native Android app written in Kotlin and Jetpack Compose. It is an evolving project, and sound detection can vary with the phone’s microphone and the room.

## How monitoring works

1. **Arm the app.** SoundSentinel starts a foreground microphone service and shows an ongoing notification.
2. **Listen for a change.** Automatic event detection has one on/off switch in Preferences. When enabled, the gate adapts to recent room noise; there is no sensitivity slider.
3. **Save an event clip.** When sound crosses the gate, the app captures up to two seconds of pre-roll and keeps recording until the quiet period selected in Preferences has passed. A clip is capped at three minutes.
4. **Review it on the phone.** Recordings appear in the local vault, where they can be played, searched, filtered, and protected from cleanup.

Optional signal analysis applies local DSP rules to a saved clip and suggests an acoustic label. These are best-effort heuristics, not a trained AI model or a verified security judgment; a label can be wrong.

## What I’m focusing on

- Reliable event-triggered recording instead of recording continuously while armed.
- An adaptive noise floor that follows changes in the room, with a manual sensitivity option.
- A configurable quiet post-roll, pre-roll audio, and clear recording status.
- Local clip review, a storage quota, retention options, and protection for clips I want to keep.
- Keeping the app useful without an account, API key, or network connection.

## Privacy and practical limits

SoundSentinel does not request internet access, and it has no cloud upload feature. Recordings and settings are written to the app’s private storage. Android device backup behavior is controlled by the phone’s system settings.

The app needs microphone permission to monitor and notification permission to show its foreground service on supported Android versions. Monitoring is opt-in: tap **ARM SOUND SENTINEL** to start listening. Use it only where recording is lawful and appropriate, and with the consent required in your location.

Microphones and background-app rules vary between Android phones. Keep the foreground notification enabled, and check the phone’s battery settings if it stops monitoring with the screen off. Detection is a learning project, not a certified alarm system; test it in the environment where you plan to use it.

## Install the APK

Download [SoundSentinel v1.5.apk](https://github.com/SoftwareIdiot/SoundSentinel/releases/download/v1.5-compact-android-20261106/SoundSentinel.v1.5.apk) from the final GitHub release. Open the downloaded file on Android and follow the install prompts. Android may ask you to allow your browser or Files app to install apps from that source. (GitHub replaces spaces in uploaded asset names with periods, so its filename is `SoundSentinel.v1.5.apk`.)

The app supports Android 8.0 (API 26) and newer. The app is designed to run offline after installation. Building from source may need internet access once to download Gradle dependencies.

## Run from Android Studio

1. Install Android Studio on your computer.
2. Clone the repository:

   ```bash
   git clone https://github.com/SoftwareIdiot/SoundSentinel.git
   ```

3. Open the cloned `SoundSentinel` folder in Android Studio and wait for Gradle sync to finish.
4. Connect an Android phone with USB debugging enabled, or start an emulator.
5. Select the device and press **Run**.

## Project map

- `app/src/main/java/com/softwareidiot/soundsentinel/logic/AdaptiveAudioGate.kt` — estimates room noise and decides when an event starts.
- `app/src/main/java/com/softwareidiot/soundsentinel/service/AcousticMonitorService.kt` — runs monitoring and saves detected clips.
- `app/src/main/java/com/softwareidiot/soundsentinel/logic/AcousticClassifier.kt` — labels saved clips using local acoustic signal rules.
- `app/src/main/java/com/softwareidiot/soundsentinel/data/repository/CctvRepository.kt` — stores recordings, settings, and event history.
- `app/src/main/java/com/softwareidiot/soundsentinel/ui/screens/SurveillanceScreen.kt` — Home screen and arm/listen status.

## How I want to grow it

I want to make event detection more dependable across different phones, understand false triggers and missed sounds better, and keep the recording and storage behavior easy to inspect. Feedback from testing in different rooms and on different devices is especially useful.

## Offline tools in v1.3

The monitor now uses a compact scrolling layout and bottom navigation. Activity shows daily saved-clip totals, an hourly chart, and a local event timeline. Clip details support private notes, and search includes those notes; use the Today filter to find recent recordings.

Monitoring options include Silent, Important, and Every event alert profiles with a cooldown. A daily recording window can span midnight and uses the phone's local time. Arm the app first: the window gates event capture inside an active monitoring session, rather than starting microphone access from a background alarm.

The existing microphone foreground service is configured to keep running when the activity is removed from Recents. Android Force Stop, reboot, revoked permissions, and manufacturer battery controls can still stop monitoring. Open the app to arm again in those cases. No service can promise uninterrupted microphone access on every Android device. The app supports Android 8.0 and newer, not iOS.

Alerts are sent after a clip is saved, with a two-second guard against the phone's own notification or vibration triggering another recording. Short events in that guard can be missed. Audio trigger timing now uses elapsed audio duration instead of a fixed number of frames, so it stays consistent across microphone buffer sizes.

## Background access in v1.4

Settings now offers a direct battery-exemption request and a dedicated notification-settings shortcut. Realme, Oppo, and OnePlus phones also get startup-manager shortcuts when their firmware exposes them. Startup screens cannot reliably select or toggle an individual app on every firmware; unsupported shortcuts report that limitation instead of silently opening App Info.

The armed request is saved separately from the service process. Reopening the visible app resumes an interrupted requested session when microphone permission is available. Explicit Disarm clears the request. This improves recovery after process removal; it does not bypass vendor process killing or grant permissions without consent.

## v1.5

Shorter screens, manufacturer-aware background settings with Android battery-settings fallback, and fewer schedule calculations in the audio loop. Manufacturer shortcuts are candidates rather than guaranteed firmware support. Android 8.0+ remains the supported platform.
