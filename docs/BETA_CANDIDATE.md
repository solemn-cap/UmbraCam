# Closed beta candidate — staging record

Updated 2026-11-03. This is an internal candidate record, not authorization to distribute.

## Candidate artifact

- File: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `E2FC05B2E148EF8C98A8766B1F79A47E5B1FAA78738EE21474CFB2EC18C8AA0B`
- Package: `com.umbracam.app`
- Version: `0.6.1-beta2` (version code 7), minimum Android API 29, target API 36
- Signing: debug signing; suitable only for controlled engineering evaluation and not a release-signed/public build
- Source implementation: Milestone 9 commit `ed38a0d` (`Complete premium creator export milestone`); later commits contain acceptance documentation only
- Host verification: 132 core JVM tests, 63 app JVM tests, `assembleDebug`, `assembleDebugAndroidTest`, and lint (zero errors) passed on 2026-11-02

The APK was produced by that successful host-verification build. A fresh optional rebuild attempt on 2026-11-03 was canceled after Gradle stopped producing output; it is not a successful verification result. Rebuild and recalculate this hash before any artifact transfer.

## Distribution gates

- Connected instrumentation must pass on a disposable emulator. The API36 AVD remained offline/unregistered in ADB on two attempts; no connected test methods ran.
- Named physical-device checks remain open for camera/audio behavior, SOS, media export/decoding, interruption, storage, thermal/performance and preservation of existing recordings.
- Review the actual APK and verify its hash on the recipient copy. Preserve app data when upgrading; uninstalling removes private recordings.
- Tester group, channel and opt-in consent are not established. No invitations or external distribution have been made.
- Keep Milestone 10 AI/cloud, live billing and production signing outside this candidate unless separately reviewed and authorized.
