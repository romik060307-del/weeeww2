# OHLI Social v3.6.27 — Test plan

## Checks run in this build workspace
- JavaScript syntax validation (`node --check`).
- Source verification script (`node tools/source-verify.mjs`).
- ZIP integrity test (`unzip -t`).
- Version alignment: Gradle, `/health`, workflow archive/artifact, and asset cache-busters all use v3.6.27 / versionCode 56.

## Required on-device tests before public release
1. Install APK on the intended Android host phone; start and stop server repeatedly.
2. Turn Wi-Fi off after it was connected: verify a notification appears; turn Wi-Fi on and verify recovery notification.
3. Keep mobile data enabled while Wi-Fi is off; confirm tunnel reachability separately from LAN access.
4. Trigger a controlled listener failure in a test build only; verify the monitor alerts and attempts restart without duplicate listeners.
5. Start Cloudflare Quick Tunnel from the Tunnel panel; do not share the URL until state is `online` after public `/health` verification.
6. From two different external devices/networks, test login, profile, feed, image/video, chat, call setup, and call teardown.
7. Check Android battery optimization / background restrictions. Do not claim the service is immortal: force-stop and vendor power management can stop it.
8. Visual review: compare mobile and desktop layouts, keyboard/IME, safe areas, dark mode, long names, empty/error/loading states, and screen widths 320–1440 px.

## Known infrastructure boundary
The built-in adaptive engine is a local performance controller based on observed server metrics. It is not a GPT/Gemini-class conversational model. Current storage remains JSON on one Android device; production-scale concurrency would require database, media object storage, push infrastructure, and WebRTC TURN/SFU services.
