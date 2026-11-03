# Closed beta candidate — staging record

Updated 2026-11-03. This is an internal candidate record, not authorization to distribute.

## Candidate artifact

- File: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `7D4E21C0B822D95E879EE13B82FE573FF82D744BC68E49CC8E3A0175DFC2A2AF` (local rebuilt APK; re-hash after any subsequent build)
- Package: `com.umbracam.app`
- Version: `0.6.1-beta2` (version code 7), minimum Android API 29, target API 36
- Signing: debug signing; suitable only for controlled engineering evaluation and not a release-signed/public build
- Source implementation: Milestone 9 commit `ed38a0d` (`Complete premium creator export milestone`) plus Pro screen nested-scroll fix commit `5fdfc79`
- Host verification: 132 core JVM tests, 63 app JVM tests, `assembleDebug`, `assembleDebugAndroidTest`, and lint (zero errors) passed on 2026-11-02. The Pro scroll fix was separately compiled by a focused connected NavigationTest run on 2026-11-03; `proDiscoveryShowsHonestAccessAndNoCheckout` passed.

The APK is the output of the focused run's rebuild after the Pro scroll fix. SHA-256 was recalculated on 2026-11-03. A subsequent broad host-check invocation stalled before reporting test/build outcomes and was canceled; it does not replace the successful 2026-11-02 host evidence or the focused NavigationTest result.

## Distribution gates

- Connected acceptance remains open. Disposable API36 AVD (`emulator-5554`) connected and exposed emulated front/rear cameras. A full `connectedDebugAndroidTest` run executed 58 methods (13 failed, 24 skipped); multiple camera finalization tests failed and system-service/I/O instability was observed. Focused NavigationTest executed 7 methods (6 passed, 1 failed on a back-navigation timeout); the Pro discovery test passed after the nested-scroll fix. CameraSupportTest could not complete because screenshot helpers timed out waiting for window focus in headless mode; a visible rerun crashed the instrumentation process in RenderThread. A manual CameraSupportTest invocation also crashed after starting its first test. No physical device was connected for this attempt.
- Follow-up disposable API30 FreshCam AVD booted into ADB after about a minute, but framework initialization remained incomplete for roughly two minutes (`init.svc.bootanim=running`; Package Manager reported `Can't find service: package`). No APK install or instrumentation was attempted. The disposable AVD was stopped.
- Named physical-device checks remain open for camera/audio behavior, SOS, media export/decoding, interruption, storage, thermal/performance and preservation of existing recordings.
- Review the actual APK and verify its hash on the recipient copy. Preserve app data when upgrading; uninstalling removes private recordings.
- Tester group, channel and opt-in consent are not established. No invitations or external distribution have been made.
- Keep Milestone 10 AI/cloud, live billing and production signing outside this candidate unless separately reviewed and authorized.
