# Phase 0 verification — 2026-10-30

Historical Phase 0 snapshot. For CameraX implementation/build evidence see [Phase 1 report](PHASE_1_REPORT.md); current named-device runtime outcomes are in [device validation](DEVICE_VALIDATION.md).

This file distinguishes implementation from executed verification. Phase 1 is not started.

## Environment
Workspace: `C:\Projects\umbracam`, PowerShell on Windows. Initial discovery found no Java, Gradle, adb or Android SDK in PATH/default installation directories. Local ignored `.tools/` contains a portable Temurin JDK 17 and Android command-line tools. SDK Platform 36, Build Tools 35.0.0 and Platform Tools were installed with sdkmanager. These local binaries are not project source or a portable SDK installation.

For verification commands in this file:
```powershell
$env:JAVA_HOME = (Get-ChildItem .tools/java -Directory | Select-Object -First 1).FullName
$env:ANDROID_HOME = Join-Path (Get-Location) '.tools/android-sdk'
```
For a normal developer setup, follow README.md instead of depending on this local tools directory.

## Executed checks
- All Android manifest/resource XML parsed with PowerShell's XML parser. Passed at that point; Android lint/build remain separate checks.
- Official wrapper files fetched from Gradle v8.13.0. `Get-FileHash gradle/wrapper/gradle-wrapper.jar -Algorithm SHA256` matched the published [wrapper checksum](https://services.gradle.org/distributions/gradle-8.13-wrapper.jar.sha256): `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.
- Distribution pin matches the [official Gradle 8.13 ZIP checksum](https://services.gradle.org/distributions/gradle-8.13-bin.zip.sha256): `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
- `curl.exe -I -L --fail ...` for AGP 8.13.2 and Compose BOM 2025.10.01 POMs: HTTP 200 from Google Maven. Artifact existence is not dependency resolution.
- `sdkmanager.bat --licenses`, then `sdkmanager.bat 'platforms;android-36' 'build-tools;35.0.0' 'platform-tools'`: completed with exit 0.
- `.tools/android-sdk/platform-tools/adb.exe devices`: exit 0, empty device list.
- Independent read-only native UI source review: no material source-level UI defects found. Static review only; no screenshot or runtime accessibility evidence.

## Build attempts
- `.\gradlew.bat --version`: initial attempt ran during JDK extraction and failed to locate `lib/jvm.cfg`. Retried after extraction; distribution download remained slow.
- `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath`: first queued attempt failed waiting 120000ms for the Gradle distribution lock held by the download. This was a tool bootstrap failure, not a source compile result.
- First build process waiting for that lock was stopped deliberately; exit -1. A mirrored Gradle 8.13 ZIP was fetched locally, matched against the official SHA-256, and placed into the wrapper cache. Project repository/distribution URLs remain official. The successful `--version` retry reported Gradle 8.13 and Temurin JDK 17.0.20.1.

## Completed build and test commands
| Exact command | Outcome |
|---|---|
| `.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath` (retry) | Exit 0; BUILD SUCCESSFUL, 7m 12s |
| `.\gradlew.bat :app:assembleDebug :core:recording:test :app:testDebugUnitTest :app:lintDebug` | Exit 0; BUILD SUCCESSFUL, 7m 36s; APK built, 8 unit tests passed, lint no errors |
| `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest` | Exit 0; BUILD SUCCESSFUL, 27s after explicit annotation targets; app unit test reran successfully; instrumentation APK compiled |
| `.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest` | Exit 0; 12s after modern backup-rule addition; a remaining legacy backup lint advisory was addressed afterward |
| `.\gradlew.bat :app:connectedDebugAndroidTest` | Exit 1, 56s; `DeviceException: No connected devices!`; no instrumentation test case executed |

After adding the legacy backup flag, `.\gradlew.bat :app:assembleDebug :app:lintDebug :app:assembleDebugAndroidTest` passed again: exit 0, BUILD SUCCESSFUL in 7s. Because lint reused a report with stale source line locations, `.\gradlew.bat :app:lintDebug --rerun-tasks` was then executed: exit 0, BUILD SUCCESSFUL in 17s, 30 tasks executed. The fresh report has **0 errors and 9 warnings**, all dependency/tool-version update advisories. The backup warning is resolved; no lint checks were suppressed. Versions remain deliberately pinned to the verified stable baseline.

## Unit and instrumentation results
- `RecordingSessionTest`: 7 tests, 0 failures, 0 errors, 0 skipped.
- `DestinationTest`: 1 test, 0 failures, 0 errors.
- `NavigationTest`: 4 test methods created and compiled; not executed because no connected target exists.
- Unit results are backed by XML at `core/recording/build/test-results/test/TEST-com.umbracam.core.recording.RecordingSessionTest.xml` and `app/build/test-results/testDebugUnitTest/TEST-com.umbracam.app.DestinationTest.xml`.
- Compiler migration advisories for Kotlin JVM target configuration and annotation targets were fixed. The initial debug build logged that `libandroidx.graphics.path.so` was packaged without symbol stripping; it did not fail packaging. No NDK is required for this Java/Kotlin foundation.

## Installation configuration checks
`aapt.exe dump badging app/build/outputs/apk/debug/app-debug.apk` returned package `com.umbracam.app`, version 0.1.0/code 1, minimum SDK 29, target/compile SDK 36 and launchable activity `com.umbracam.app.MainActivity`.
The merged manifest and APK contain only AndroidX Core's internal signature permission `com.umbracam.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no media/storage/network/runtime grants were added.
`apksigner.bat verify --verbose app/build/outputs/apk/debug/app-debug.apk` returned exit 0 and verified APK Signature Scheme v2, one signer. This confirms development packaging/signing, not installation or launch on a device.
Final delivered APK SHA-256: `e4978c7ecc126e557da13a310d12b1f2e11e7e9ea2cccb8a1065d40da1a23511`.

Local ignored logs are in `.tools/{dependencies-retry,build-retry,final-build,backup-verification,manifest-final,instrumentation}.log`. SDK/JDK setup for other developers is in README.md; no local tool paths are required by committed Gradle configuration.

## Runtime and physical-device gaps
No attached device/emulator; instrumentation, install/launch, screenshots, light/dark rendering, configuration restoration, TalkBack and physical-device behavior are unverified. The instrumentation suite exists; creation/compilation is not execution. Do not call Phase 0 fully verified until these gaps are closed.

## Phase 2 final checkpoint (2026-10-30)
Application/test build, JVM suite and lint passed. Final app hash FFD5AB9E9564621285D5A57BA696EBCA468D0D3C56EDE1678C59825D3CEFFB7A; v2 signature verified. 45 JVM reports have zero failures/errors. Thirteen distinct physical methods passed across documented final runs; API29 runtime and broader matrix remain unverified. See [Phase 2 report](PHASE_2_REPORT.md) for exact commands, individual failures/retests and limitations.


## Emulator-only follow-up (2026-10-30/31)
Test APK and lint passed in50s after adding API29 notification-setting and unwritable-directory fixtures; no production APK/dependency change. Application SHA256 remains FFD5AB9E9564621285D5A57BA696EBCA468D0D3C56EDE1678C59825D3CEFFB7A. Latest lint XML: zero errors/19 warnings. Prior45 JVM results are unchanged, not claimed as newly run. See [Emulator validation](EMULATOR_VALIDATION.md) for actual API29 outcomes, aborted runs, fixes and remaining limitations.


Final API29 follow-up result:16 distinct instrumentation methods and3 external scenarios passed across the documented runs; screen-off OK1 in33.085s. No production APK change. The emulator was shut down with no UmbraCam service active and pending-directory permissions restored to700;18 completed files retained. See EMULATOR_VALIDATION.md for evidence and limitations.

