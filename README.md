# UmbraCam

Capture Every Perspective. Protect Every Moment.

Current source includes [Milestone 9 advanced creator exports](docs/MILESTONE_9_PROGRESS.md), [Milestone 8 Pro creator foundations](docs/MILESTONE_8_PROGRESS.md), the [Private Vault](docs/MILESTONE_7_PROGRESS.md), [Milestone 5 Reaction Studio](docs/MILESTONE_5_PROGRESS.md), and [Milestone 6 SOS](docs/MILESTONE_6_PROGRESS.md). Camera Studio records one camera, or an advertised compatible front/rear pair with PiP/split controls. Reaction Studio supports screen-only or front-camera composition after fresh Android consent, with optional microphone and Android 10+ policy-permitted playback audio as separately synchronized AAC tracks. Device/content policy can prevent playback audio. Creator exports provide non-destructive editing of supported source assets. Host verification has passed for the current Milestones 5–9 source; connected instrumentation and physical-device acceptance remain open. Completed recordings are AES-GCM encrypted; active staging and temporary playback/share copies remain transient plaintext.

Closed-round tester scope and handling notes: [CLOSED_BETA_GUIDE.md](docs/CLOSED_BETA_GUIDE.md). Physical acceptance remains open; see the current incident and retry report before running instrumentation on a device with existing recordings.

Native Android app in Kotlin, Jetpack Compose and Material 3. Minimum Android 10 (API29), target API36. Dual recording requires an advertised compatible Android11+ pair and is initially restricted to portrait HD. Physical dual/performance qualification remains pending. SOS Quick Record uses the existing rear-camera path and does not contact anyone. Pro discovery, non-destructive creator exports, a preview-only teleprompter and saved Reaction layout preferences are implemented. The supplied icon and approved system light/dark foundation are preserved. Application ID is provisional.

Camera permission is requested on Enable camera, microphone permission on its opt-in switch, and notification permission on Start when required. Home never starts capture. Notification access is required by the product before recording. Process death does not restart capture; explicit launch reconciles private journals and abandoned pending output. Recoverable interruptions require visible user Resume and fresh source checks.

## Open and build
Requirements: JDK 17, Android SDK Platform 36, Build Tools 35.0.0, Platform Tools, network access for pinned Maven/Gradle dependencies. Use Android Studio with support for AGP 8.13.2, or the command line. Minimum device API is 29; it has not yet been physically validated.

1. Install JDK 17 (for example [Temurin](https://adoptium.net/temurin/releases/?version=17)) and set `JAVA_HOME` to its directory.
2. Install Android Studio and use SDK Manager to install Android SDK Platform 36, Build Tools 35.0.0, Command-line Tools (latest) and Platform Tools.
3. Set `ANDROID_HOME` to the SDK directory, or create untracked `local.properties` containing `sdk.dir=C:/Users/YOUR_USER/AppData/Local/Android/Sdk` (forward slashes).
4. Open this directory in Android Studio and sync, or run:
```powershell
$env:JAVA_HOME = 'C:\Path\To\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
.\gradlew.bat :app:assembleDebug :core:recording:test :app:testDebugUnitTest :app:lintDebug
```
The checked-in official Gradle wrapper downloads Gradle 8.13 and verifies its distribution SHA-256. Do not install a global Gradle version. Linux/macOS: `sh ./gradlew` with equivalent SDK/JDK environment.

## Install and test UI
Connect an authorized API 29+ device or start an emulator:
```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
.\gradlew.bat :app:installDebug
```
Run connected instrumentation tests only on a disposable emulator or a device whose UmbraCam app data has been safely backed up and may be replaced. Test APK installation can clear or replace the target app's private data; never point this task at a daily-use installation with recordings you need to preserve.
```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```
Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Debug signing is for development only; release signing and Play publication are outside this milestone. Latest lifecycle work and checks are in [Phase 2 report](docs/PHASE_2_REPORT.md); [Phase 1 report](docs/PHASE_1_REPORT.md) is historical. [Device validation](docs/DEVICE_VALIDATION.md) records 11 distinct passing instrumentation checks per target on a physical Samsung SM-S9080 / Android 16 and an Android 10 emulator, including front/rear capture and decoded frames. The broader failure/playback/device matrix remains pending before release.

## Project map
- `app`: launcher/theme/navigation, Camera Studio ViewModel/CameraX adapter, private staging/output store and Android metadata inspector, plus tests.
- `core/recording`: pure Kotlin source/state/session/error contracts and the main-thread-confined single-camera session coordinator; no full interchangeable recording pipeline.
- `gradle`: centralized pinned dependencies and wrapper.
- `docs`: [product](docs/PRODUCT_SPEC.md), [architecture](docs/ARCHITECTURE.md), [milestones](docs/DEVELOPMENT_PLAN.md), [tests](docs/TESTING.md), [decisions](docs/DECISIONS.md), [verification](docs/BUILD_VERIFICATION.md).
- `AGENTS.md`: collaboration and reporting requirements.

Only extract additional modules when they have implemented responsibilities. Room, DataStore, and Hilt remain deferred. Read the docs before changing feature code. Phase 1 and Phase 2 were approved on 2026-10-30. The latest Milestone 5 implementation status and batched verification results are recorded in [the progress report](docs/MILESTONE_5_PROGRESS.md). Physical thermal, long-duration and broad compatibility acceptance remains distinct from emulator checks.

## Recording storage
Finalized camera/screen videos use a versioned AES-GCM file envelope under internal no-backup storage. Active files are plaintext in `pending/`; failed staging is encrypted when quarantined. Legacy recordings migrate only after an encrypted round-trip inspection. Playback/share use temporary cache copies and explicit export leaves the vault. Do not clear app data when preserving recordings; private journals now reconcile abandoned attempts on explicit launch. No catalog or repair UI exists.

Phase4.5 acceptance is active: see the [physical matrix and sustained emulator evidence](docs/PHASE_4_ACCEPTANCE.md). This adds validation tooling, not new product features. Physical dual-camera qualification remains pending; the user's phone is not used while unavailable.




Android 10 emulator-only compatibility and fault checks are recorded separately in [Emulator validation](docs/EMULATOR_VALIDATION.md). They do not replace physical call/audio or power testing.


Phase 4.1 emulator work demonstrated composed PiP/split output and per-camera progress callbacks. Its provenance gate was subsequently verified; broader physical acceptance remains open. See [feasibility report](docs/PHASE_4_FEASIBILITY.md).

Phase 4.2 adds tested dual-session settings, final-frame geometry and source-health policy in the core, preserving the single-camera facade. No production dual controls are enabled. See [domain report](docs/PHASE_4_DOMAIN_REPORT.md).

The Phase4 emulator provenance gate is now verified with independent per-camera exposure markers in both layouts/orders. App integration is now verified; mode/layout controls remain pending. See [provenance report](docs/PHASE_4_PROVENANCE_REPORT.md).

Phase4.3 connects the dual backend to the existing service, source-health timer, atomic storage and compatible version2 journal.109 JVM tests and15 distinct emulator native methods pass, plus a separate forced-termination/relaunch check. No phone was used. Next is the Camera Studio mode/layout UI. See [integration report](docs/PHASE_4_INTEGRATION_REPORT.md).
