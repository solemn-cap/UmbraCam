# UmbraCam

<!-- impeccable:product-schema 1 -->

## Platform
android

## Stack
User-specified Kotlin, Jetpack Compose, Material 3, Gradle Kotlin DSL; API 29 minimum pending device validation.

## Product Purpose
Capture Every Perspective. Protect Every Moment. A planned recording app combining Camera Studio, Reaction Studio, SOS Mode and Private Vault.

## Users
The brief describes content creation and personal safety use cases. Primary audience and priority between these use cases remain open.

## Capabilities and Constraints
Phase 1 was explicitly approved on 2026-10-30. Camera Studio implements a single front OR rear camera, supported quality choices, optional microphone and finalized app-private files. Basic runtime checks passed on physical Samsung SM-S9080 / Android 16 and an API 29 emulator; broader failure/performance/full-playback acceptance is pending (docs/DEVICE_VALIDATION.md). Phase 2 now uses a user-started camera/microphone foreground service for accepted capture across Home, navigation and recreation; ongoing notification controls remain visible. Process death never restarts capture; recovery requires an explicit user action. Phase 3 adds a read-only concurrent-camera metadata check with Android-version and single-camera fallbacks. Dual recording is deferred. Other studios and Private Vault remain previews. No encryption, playback or export UI yet. Home opens no capture or permission prompts; requests occur only at camera/microphone user actions. Entering/restoring Camera Studio can open an already-authorized preview, but recording always requires Start. Respect Android consent/privacy requirements. Work on one milestone at a time.

## Brand Commitments
UmbraCam wordmark; simple, modern native interface. The user explicitly confirmed proceeding with the simple Material 3 foundation, four section links, Settings and system light/dark mode. The user limits visual polish in this milestone. On 2026-10-31 the user supplied UmbraCam Icon.png (blue/silver eye and lens on a dark tile) for the launcher and requested the name SOS Mode. Preserve this artwork’s proportions within adaptive launcher masks. SOS is a preview without recording, alerts or emergency-response behavior.
