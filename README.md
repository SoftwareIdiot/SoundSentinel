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

## Use and edit it from a phone

SoundSentinel is a Progressive Web App (PWA), not a native APK or App Store app. Once it is served over HTTPS, open it on your phone and use the browser's **Add to Home Screen** or **Install App** option.

For phone-based editing and testing, publish the project to a GitHub repository (if you make the source repository public, verify that `.env` is not included), then create a Codespace from that repository. The included `.devcontainer/devcontainer.json` installs dependencies and forwards port 3000 over HTTPS with private visibility. In the Codespace terminal, run `npm run dev`, open the forwarded port URL on your phone, and use that same URL to install the PWA. Keep the forwarded app port private even when the source repository is public.

Add `GEMINI_API_KEY` as a GitHub Codespaces secret for the repository, then restart the Codespace. Do not commit `.env` or put a real key in `.env.example`.

Microphone access and PWA installation require a secure context such as HTTPS. Monitoring in the background or with the phone locked is not guaranteed; keep the app open while testing. The Gemini key is used by the server. The current API endpoint has no app-level authentication, so do not expose the server publicly outside a private Codespace port until access controls are added.
