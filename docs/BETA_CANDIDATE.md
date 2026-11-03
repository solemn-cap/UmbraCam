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

The APK is the output of the focused run's rebuild after the Pro scroll fix. SHA-256 was recalculated on 2026-11-03. On 2026-11-03, using the wrapper-cached Gradle 8.13 distribution with `-Dorg.gradle.native=false`, `:app:testDebugUnitTest` passed (63 tests, 0 failures), and `:app:assembleDebug` plus `:app:assembleDebugAndroidTest` succeeded with their outputs up to date. The 132 core tests remain successful from 2026-11-02 and were up to date in this rerun. `:app:lintDebug` reached `lintAnalyzeDebug` but stalled while opening classpath files; it was stopped without a lint result. Previous lint passed with zero errors on 2026-11-02.

## Distribution gates

- Connected acceptance remains open. Disposable API36 AVD (`emulator-5554`) connected and exposed emulated front/rear cameras. A full `connectedDebugAndroidTest` run executed 58 methods (13 failed, 24 skipped); multiple camera finalization tests failed and system-service/I/O instability was observed. Focused NavigationTest executed 7 methods (6 passed, 1 failed on a back-navigation timeout); the Pro discovery test passed after the nested-scroll fix. CameraSupportTest could not complete because screenshot helpers timed out waiting for window focus in headless mode; a visible rerun crashed the instrumentation process in RenderThread. A manual CameraSupportTest invocation also crashed after starting its first test. No physical device was connected for this attempt.
- Follow-up disposable API30 FreshCam AVD booted into ADB after about a minute, but framework initialization remained incomplete for roughly two minutes (`init.svc.bootanim=running`; Package Manager reported `Can't find service: package`). No APK install or instrumentation was attempted. The disposable AVD was stopped.
- Additional retry on 2026-11-03: the API29 Phase2 AVD and API30 AOSP AVD both registered in ADB but failed to start Package Manager. Guest logs repeatedly waited for `package_native`; the API29 guest reported `zygote` in uninterruptible I/O sleep. Neither target was used to install or run tests. The repo-local Gradle distribution stalled on launch; the wrapper-cached Gradle distribution started normally. Gradle project configuration was slow on a cold cache (the `help` task succeeded in 3m32s). Host unit tests and APK assemblies then completed as summarized above. Lint did not finish. These failures point to this environment's emulator and lint-classpath responsiveness, not a new verified app regression.
- Named physical-device checks remain open for camera/audio behavior, SOS, media export/decoding, interruption, storage, thermal/performance and preservation of existing recordings.
- Review the actual APK and verify its hash on the recipient copy. Preserve app data when upgrading; uninstalling removes private recordings.
- Tester group, channel and opt-in consent are not established. No invitations or external distribution have been made.
- Keep Milestone 10 AI/cloud, live billing and production signing outside this candidate unless separately reviewed and authorized.
