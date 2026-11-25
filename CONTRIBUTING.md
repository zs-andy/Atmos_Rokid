# Contributing to Atmos_Rokid

Thank you for helping make real-time ambient perception better for visually impaired users!

## Areas Where Help Is Especially Welcome

- **Networking & Config**: Replace hardcoded LAN server URLs in `FastVLMUploader.kt` and `YoloUploader.kt` with a settings UI or mDNS discovery.
- **Latency & Streaming**: Optimize JPEG compression, frame scheduling, or migrate to WebSocket streaming.
- **Accessibility UX**: Improve voice phrasing, haptic/audio cues, and Rokid Glasses interaction ergonomics.
- **Clean Repo Hygiene**: Exclude generated Gradle caches and keep model weights out of git commits.

## Local Workflow

1. Fork the repository and create a branch (`feat/...` or `fix/...`).
2. Never commit downloaded FastVLM/YOLO model weights or `.gradle` cache files.
3. Verify the macOS `Server` target builds in Xcode and/or the Android `Atmos` module builds via `./gradlew assembleDebug`.
4. Open a pull request describing what changed and how you tested it.
