<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/93e9318a-e9e1-471c-9c47-ecc73b0de79b

## Run Locally in VS Code

**Prerequisites:** Node.js 22.12 or newer and npm.

1. Open this folder in VS Code and open **Terminal → New Terminal**.
2. Install dependencies with `npm install`.
3. Copy `.env.example` to `.env` and put your Gemini key in `.env`. Keep `.env` private; Git ignores it.
4. Run `npm run dev` and open `http://localhost:3000`.

Run the existing checks with `npm run lint`, `npm run build`, and `npm test`.

## Android APK and background audio

The Android project uses Capacitor and includes a native microphone foreground service. On Android, arm monitoring while the app is open and allow microphone access. Android then keeps the native audio monitor running behind the app with an ongoing notification and a **Stop** action. Sound clips are saved in the app's private storage and imported into the app's recording vault when it is open again. Audio classification happens when clips are imported or manually classified; if the laptop server is unreachable, the app falls back to its local heuristic classifier.

Android may stop any app after a force-stop, reboot, permission change, hardware privacy-mic switch, or vendor battery policy. The app does not start monitoring automatically after reboot. Long-running capture uses battery and storage; it is intended for supervised testing, not certified security use.

### Build and install a test APK on Windows

**Prerequisites:** Node.js 22 or newer, Android Studio with its Android SDK, and an Android phone. Android Studio supplies the JDK needed by Gradle.

1. Open this folder in VS Code and open **Terminal → New Terminal**.
2. Run `npm install`.
3. Run `npm run android:sync` to build the web assets and copy them into the Android project.
4. Run `npm run android:open` to open the `android` project in Android Studio.
5. On the phone, enable **Developer options → USB debugging**, connect it to the PC, unlock the phone, and accept the debugging prompt.
6. In Android Studio, select the phone and press **Run** to install and start a debug build. To create a file, select **Build → Build Bundle(s)/APK(s) → Build APK(s)**. Android Studio reports where it saved the debug APK.
7. Open the app on the phone, go to the monitoring screen, press **Arm**, and grant microphone permission while the app is visible. Keep the ongoing SoundSentinel notification present while monitoring.

For future app updates, keep the same app ID and signing key. Do not put a release keystore or its passwords in GitHub.

### Use the laptop as the temporary AI server

Keep the phone and laptop on the same trusted Wi-Fi network. In a laptop terminal, run `npm run dev`. The server listens on port 3000 on the laptop's network interfaces.

The Android app server URL is prefilled from VITE_LAPTOP_SERVER_URL in the laptop's ignored .env file. If the laptop address changes, update that value and rebuild the APK, or change the URL in Storage > Laptop AI Server. Find the current Wi-Fi address with ipconfig.

Copy the access token from the laptop terminal with `(Get-Content .env | Where-Object { $_ -match '^APP_API_TOKEN=' }) -replace '^APP_API_TOKEN=', ''`, then paste it in Storage > Laptop AI Server. Keep both devices on the same trusted network. Windows Firewall may block port 3000; allow only inbound TCP 3000 from LocalSubnet while testing. Do not set up router port forwarding or expose this server to the internet. If Wi-Fi blocks device-to-device traffic, use your phone's hotspot and connect the laptop to it.

The Gemini key stays in the laptop's ignored `.env` file. LAN classification is disabled until you set a random `APP_API_TOKEN` in the laptop `.env` and enter the same token in the app's server settings. This prevents other devices on the Wi-Fi from using the Gemini endpoint without the token. The app manifest permits plain HTTP for trusted-LAN development only; use HTTPS plus authentication before hosting beyond the LAN. Never place the Gemini API key in the APK or the Android project.

### Edit on a phone

GitHub's mobile app can browse the source but is not an Android IDE. To edit from a phone, open the repository in Chrome, create a GitHub Codespace, and use the Codespaces editor in the browser. Codespaces runs the web server in GitHub's cloud, not on the phone or laptop. That setup is useful for editing and PWA testing; generating an APK still requires the Android SDK/build environment. Keep Codespaces port 3000 private. For AI calls from a Codespace, add both `GEMINI_API_KEY` and `APP_API_TOKEN` as Codespaces secrets and enter the token in the app's Laptop AI Server settings.

The standalone APK's interface is packaged locally on the phone. The temporary laptop server is only needed for Gemini classification. If the laptop or Wi-Fi is offline, native audio monitoring and local recording continue; when the app next imports a clip, it tries Gemini if the laptop server is reachable and otherwise uses the local fallback.
