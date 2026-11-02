# UmbraCam — Phase 1 delivery (2026-10-30)

Historical implementation handoff before a device was connected. Follow-up [device validation](DEVICE_VALIDATION.md) now supplies named physical/emulator evidence and supersedes the runtime-unverified status below for the specific checks it records.

## 1. Implementation
User explicitly approved Phase 1 after the single-camera approval requirement was explained. Camera Studio now implements CameraX preview and recording with one front OR rear camera, supported quality enumeration/selection, optional microphone, explicit Start/Stop and finalized app-private MP4 output. Version is 0.2.0/code 2; min API 29, target/compile API 36, provisional package com.umbracam.app.

Camera permission is requested only on Enable camera; microphone permission only on its opt-in switch. An already-authorized preview can open when entering/restoring Camera Studio. Recording always requires Start. Unsupported hardware/profiles and permission denial are handled without pretending that capture is available. Busy controls cannot switch sources or overlap attempts.

SingleCameraSessionCoordinator is pure Kotlin and main-thread confined by the Android adapter. A Start callback plus source availability is required for RECORDING; source loss leaves it immediately on detection; Stop wins over delayed Start and stale callbacks cannot mutate newer sessions. The activity-scoped ViewModel retains options/finalization state across rotation, but recording stops on pause/navigation/rotation and never automatically restarts. Weak preview references and cleared surface providers avoid retaining destroyed Activity views. Meaningful status/error changes use polite live regions; the timer does not.

Recorder output is staged under internal `no_backup/camera/pending`. A 32 MiB usable-space check is conservative preflight, not a recording-duration guarantee. After Finalize, Android metadata validation checks video presence, positive dimensions/duration and audio-track metadata; an atomic same-filesystem move publishes into `recordings`. No copy fallback exposes partial completed output. Known source-inactive/storage-full results may preserve structurally usable video with a warning. Other failed output goes to `quarantine`, or remains unpublished in pending if moving fails. Previously finalized files remain intact. These files are private but **not encrypted**; uninstall removes them.

No background recording service, automatic recovery, dual camera, screen capture, playback, export, Vault security or Phase 2 work was introduced. No device behavior or release readiness is claimed.

## 2. Created files
```text
core/recording/src/main/kotlin/com/umbracam/core/recording/SingleCameraSessionCoordinator.kt
core/recording/src/test/kotlin/com/umbracam/core/recording/SingleCameraSessionCoordinatorTest.kt
app/src/main/java/com/umbracam/app/camera/CameraStudioViewModel.kt
app/src/main/java/com/umbracam/app/camera/CameraStudioScreen.kt
app/src/main/java/com/umbracam/app/camera/RecordingFileStore.kt
app/src/main/java/com/umbracam/app/camera/AndroidVideoInspector.kt
app/src/test/java/com/umbracam/app/camera/RecordingFileStoreTest.kt
app/src/androidTest/java/com/umbracam/app/CameraPermissionTest.kt
docs/PHASE_1_REPORT.md
```

## 3. Modified files
```text
app/build.gradle.kts
gradle/libs.versions.toml
app/src/main/AndroidManifest.xml
app/src/main/java/com/umbracam/app/MainActivity.kt
app/src/main/java/com/umbracam/app/UmbraCamApp.kt
app/src/main/res/values/strings.xml
app/src/androidTest/java/com/umbracam/app/NavigationTest.kt
README.md
PRODUCT.md
DESIGN.md
docs/PRODUCT_SPEC.md
docs/ARCHITECTURE.md
docs/DEVELOPMENT_PLAN.md
docs/TESTING.md
docs/DECISIONS.md
docs/BUILD_VERIFICATION.md
```
Historical Phase 0 report/results remain historical snapshots; no unrelated feature code was refactored. No new empty modules were created.

## 4. Dependencies added
- CameraX core, camera-camera2, camera-lifecycle, camera-video and camera-view **1.5.3**: official camera backend, capability/preview/lifecycle and muxed recording APIs. Stable conservative pin; not claimed latest.
- Lifecycle runtime-compose and viewmodel-compose **2.9.4**: lifecycle-aware StateFlow collection and retained recording/options ownership. Existing Kotlin/AGP/Compose pins were preserved.
- No new third-party libraries, storage permissions or persistence/DI frameworks. All versions live in the version catalog. Camera/autofocus/microphone hardware declarations are optional; actual capabilities are queried.

## 5. Exact checks executed
PowerShell environment for these local runs:
```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/java -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path (Get-Location) '.tools/android-sdk'
```

| Command | Outcome |
|---|---|
| `.tools/android-sdk/platform-tools/adb.exe devices` | Exit 0; no connected target |
| `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath` | Exit 0, 49s; new dependencies resolved |
| `.\gradlew.bat :app:assembleDebug` | Exit 0, 4m 9s; first CameraX debug build |
| `.\gradlew.bat :app:assembleDebug :core:recording:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` | Exit 1, 56s; 27 unit tests passed and APKs compiled, but lint flagged the helper-based audio permission guard |
| `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` | Exit 0, 15s after explicit call-site audio permission check; no lint suppression |
| Same command after weak-preview references, detach cleanup and timer wording | Exit 0, 15s; final app/test APKs and app unit tests passed |
| `.\gradlew.bat :app:connectedDebugAndroidTest` | Exit 1, 4s; `DeviceException: No connected devices!`; no test case executed |
| `aapt.exe dump badging app/build/outputs/apk/debug/app-debug.apk` | Exit 0; package/version/min29/target36/launcher verified |
| `apksigner.bat verify --verbose app/build/outputs/apk/debug/app-debug.apk` | Exit 0; v2 APK signature verified, one debug signer |

Logs are retained under ignored `.tools/phase1-*.log`. APK: `app/build/outputs/apk/debug/app-debug.apk`; instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.
Final app APK SHA-256: `159d050b6db62fdbeef328cc2e6ff0bba914c679fbfe68609f135850cdf38020`.

## 6. Results
**27 unit tests passed, 0 failures/errors/skips:**
- RecordingSessionTest: 7 existing contract tests.
- SingleCameraSessionCoordinatorTest: 12 source gating/stop/callback/finalization/scope tests.
- RecordingFileStoreTest: 7 publication/space/validation/quarantine/path-containment tests.
- DestinationTest: 1 navigation configuration test.

**Five instrumentation methods compile but did not run:** four navigation/configuration tests, plus a missing-camera-grant explanation/disabled-Start test (the latter explicitly skips if permission already exists). There is no fabricated camera/permission/device result.

**Lint: 0 errors, 18 warnings.** Sixteen are dependency/tool update advisories for stable pins. Two UsableSpace advisories recommend StorageManager allocatable-byte APIs; the intentionally conservative filesystem free-space preflight does not assume reclaimable cache space. No checks were suppressed. The debug packaging task noted unstripped AndroidX graphics/CameraX native libraries; packaging and signing succeeded.

APK permissions are CAMERA, RECORD_AUDIO and AndroidX's internal signature-only receiver permission. No storage/network/foreground-service permission. CameraX contributes a disabled, non-exported MetadataHolderService for configuration metadata; this is not a running recording/foreground service.

Independent static native source review found no blocking defect. Its two accessibility recommendations (switch space and polite announcements) were subsequently scored resolved at source scope. This is not runtime visual/accessibility verification.

## 7. Limitations and acceptance gaps
- No connected device/emulator. Installation/launch, preview, actual recording, permission UI, front/rear compatibility, physical playback/audio, lifecycle/callback races, TalkBack and large-font rendering remain unverified. **Phase 1 implementation/build checks are delivered; physical-device acceptance is pending.**
- Android ViewModel/CameraX orchestration is not covered by pure JVM tests. Structural metadata checks cannot certify decoding, synchronization or every corruption mode.
- Backgrounding, leaving and rotation stop capture. OS termination may prevent Finalize/validation; no background continuation or process-death reconciliation exists. A missing Finalize callback can leave a stopping attempt blocked; it must never be claimed saved without publication.
- Failed/abandoned output is retained privately and may accumulate. There is no cleanup/recovery browser, persistent recording catalog, playback/export or encrypted Vault yet. Last-session status is not a durable recording list.
- Debug signing and provisional package ownership are not production release preparation. Minimum API and quality/device compatibility need the documented hardware matrix.

## 8. Recommended next task
Complete the named-device acceptance matrix in TESTING.md: API 29/current OS, front/rear, silent/audio, denial/revocation, fast Stop, rotation/background/navigation, storage failure and actual extracted-file playback. Close remaining Phase 0 UI checks in the same device pass. Preserve existing files and report exact device/build/results. Only then scope milestone 2 (foreground service and robust interruption lifecycle); it has not been implemented here.
