# Phase 4 provenance follow-up

Date: 2026-10-31. Outcome: the outstanding emulator input-provenance gate is closed for the tested portrait PiP/split configuration and both primary camera orders. This establishes a basis for Phase4.3 integration, not completed product dual recording or physical-device acceptance.

## 1. Implementation

Added an opt-in, emulator-only causal provenance test. It retains the exact independently advertised pair, shared Preview/VideoCapture and CameraX1.5.3 composition backend. Instead of replacing camera feeds (which removed concurrent support on this image), it changes each input's exposure in separate phases through Camera2CameraControl, outside capture callbacks. Read-only callbacks identify each actual device and confirm its exposure/AE results.

Composition exposes one primary camera wrapper. The test obtains the other camera's public CameraX control using a Preview without a surface, then completely unbinds that preliminary configuration before composition. No extra live stream is added to the composed recording. Options are applied only while the selected camera is active in the advertised pair; per-device result metadata and decoded output must confirm targeting. This control-handle experiment is scoped to the pinned backend and is not a production adapter design or general OEM control guarantee.

Each recording includes six decoded probes: two normal, primary-only dark, primary restored, secondary-only dark, both restored. Tests require the controlled region to drop below40% of its normal luminance and recover by more than25 luma units; the uncontrolled region must remain above55% of its prior visible level. Both cameras must independently respond within the same saved MP4. This establishes independently changing input contributions rather than mistaking two copies of one stream for dual capture. Brightness is a test marker, not a product source-availability heuristic.

Runs cover split and PiP, each with camera0 and camera1 as primary, for four saved files and24 decoded samples. Camera2/CameraX advertisement, manual-sensor capability, actual result metadata, usable encoded output and successful finalization are required. No camera requests are submitted through callback sessions, no raw camera ownership or platform metadata is overridden, and the existing permissions/privacy behavior is preserved.

## 2. Created files

- `app/src/androidTest/java/com/umbracam/app/DualCameraProvenanceTest.kt`
- `docs/PHASE_4_PROVENANCE_REPORT.md` (this report)

## 3. Modified files

README.md; docs/ARCHITECTURE.md, DEVELOPMENT_PLAN.md, TESTING.md, DECISIONS.md, PHASE_4_DESIGN.md, PHASE_4_FEASIBILITY.md and PHASE_4_DOMAIN_REPORT.md; scripts/phase4/README.md. Changes record the result, replace the current provenance blocker with integration work and preserve historical failed attempts. No production app/core source, dependency, manifest, permissions or version setting changed in this follow-up.

## 4. Dependencies added

None. Existing CameraX1.5.3, Android/Compose instrumentation libraries and local SDK/JDK/Gradle were used. No npm package, custom encoder, SDK image or new AVD was required. Official references: [Camera2CameraControl](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2CameraControl) and [Android11 fake-camera implementation](https://android.googlesource.com/device/generic/goldfish/+/refs/heads/android11-release/camera/EmulatedFakeCamera3.cpp). The local build and actual result metadata establish behavior for the pinned library and tested image.

## 5. Exact tests and checks executed

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/java/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path .tools/android-sdk).Path
.\.tools/gradle/gradle-8.13/bin/gradle.bat :app:assembleDebug :app:assembleDebugAndroidTest --offline --max-workers=2 --console=plain
.\.tools/gradle/gradle-8.13/bin/gradle.bat :app:assembleDebugAndroidTest :app:lintDebug --offline --max-workers=2 --console=plain
adb -P 5038 -s emulator-5558 install -r app/build/outputs/apk/debug/app-debug.apk
adb -P 5038 -s emulator-5558 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -P 5038 -s emulator-5558 shell am instrument -w -r -e class com.umbracam.app.DualCameraProvenanceTest -e dualProvenance true com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
adb -P 5038 -s emulator-5558 shell am instrument -w -r -e class 'com.umbracam.app.DualCameraFeasibilityTest,com.umbracam.app.CameraRecordingTest#availableFrontAndRearRecordSilentDecodableVideos' -e dualFeasibility true com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

Target: isolated `UmbraCam_Phase3_API30_AOSP`, API30/Android11, x86_64, SwiftShader,720×1280/320dpi,4cores/3072MiB, headless/no audio/no snapshots. Fingerprint: `Android/sdk_phone_x86_64/generic_x86_64:11/RSR1.210722.013.A2/10067904:userdebug/test-keys`. Camera0 rear and camera1 front advertise the exact pair through Camera2 and CameraX. Commands target only emulator5558 on the isolated5038 server; no phone actions occurred.

The cold boot took several minutes and System UI presented an ANR. Its Wait action was selected from the actual emulator UI hierarchy, and the launcher regained focus before installation/testing. No runtime result is inferred from the boot itself.

## 6. Results

Build and lint passed; current lint reports0 errors/11 warnings. The final test build passed in9s (`.tools/phase4-provenance-modulation-build.log`). Provenance passed `OK (1 test)` in64.142s (`.tools/phase4-provenance-run2.txt`). Existing source-health/preview-detach/denial and single-camera front/rear regression methods passed `OK (2 tests)` in43.508s (`.tools/phase4-provenance-regression.txt`). These are3 distinct passing methods; the four provenance cases are not counted as four methods. No JVM suite was rerun this follow-up; the97 passing domain/app JVM tests are the prior Phase4.2 evidence.

The first provenance run correctly failed an ambient-motion threshold: two samples of the default camera0 scene were almost identical (mean RGB difference0.048), despite successful primary-only exposure targeting. The final method does not weaken the luminance thresholds or assume that the fake scene naturally animates. It actively modulates both source inputs and requires each recorded region to change and recover independently. Ambient differences remain in evidence as observations, not proof of motion. The unused red/blue file-input approach remains unsupported on this image and was not forced through eligibility.

Final evidence timestamp: `1793472967703`. Mean luma values on a0–255 scale:

| Layout / primary | Primary normal → dark → restored | Secondary normal → dark → restored | File bytes | Finalize error |
|---|---|---|---:|---:|
| Split /0 |202.25 →0 →197.36 |178.18 →0.07 →172.93 |471563 |0 |
| Split /1 |177.58 →0.01 →178.18 |199.75 →0 →199.62 |695076 |0 |
| PiP /0 |167.18 →0 →159.46 |176.06 →0.01 →170.53 |414247 |0 |
| PiP /1 |129.77 →0 →132.92 |198.12 →0 →198.12 |595220 |0 |

During camera0 modulation, capture results report AE_OFF/exposure1000ns only on device0; during camera1 modulation, they report AE_OFF/exposure13611ns only on device1. The other device remains AE_ON, and both return to automatic exposure afterward. Decoded PNGs were visually inspected: the controlled full-frame region or inset becomes black while the other retains its scene. This rules out duplicate-source and frozen-secondary explanations for the observed phases.

MP4/PNG/JSON evidence is retained app-private under `files/phase4-provenance/` and exported under `.tools/phase4-provenance-evidence/files/phase4-provenance/`. Four pre-existing completed recordings were SHA256-checked before/after update and testing: all4 unchanged, none missing. The single-camera regression added2 new completed files. No media was deleted or app data cleared.

Ten updated/new Markdown documents passed41 relative-link checks with zero missing targets. All recorded control states returned to AE_ON with no control cleanup errors. After evidence export, CAMERA was restored to denied, RECORD_AUDIO remained denied, guest storage was synced and the emulator was shut down. Both task-owned emulator processes exited normally. The user's phone was not accessed.

A new debug APK was assembled to include the previously source-only Phase4.2 core extraction and test against that actual package. App version remains0.4.0; its SHA256 is `73252c3745608e3d695a9ff97a66e8a6048505bdaf72000aedb7ebcd9fc0425d`. This replaces the older local build artifact, not the user's phone installation. Dual app controls remain unavailable.

## 7. Known limitations

This closes the bounded emulator provenance question using controlled temporal input markers, not natural object-motion certification or literal red/blue feed replacement. It does not validate every output frame, all orientations/mirroring/cropping, objective cadence/sync, audio, sustained thermal/storage behavior, OEM compatibility or physical dual capture. The preliminary secondary-control handle technique is test-only. Production dual adapter, scheduled source-health delivery, service controls, journal v2 and Camera Studio controls are still unimplemented. The user's phone remains unavailable and untouched.

## 8. Recommended next task

Proceed to Phase4.3 app integration: exact-pair dual CameraX adapter with immutable binding identity, captured core attempt tokens and scheduled health ticks; shared foreground-service ownership; dual journal descriptor with v1 compatibility; validated atomic output and honest source/storage diagnostics. Preserve completed media and single-camera behavior. Keep user-facing dual controls for Phase4.4 after backend integration checks; physical acceptance remains Phase4.5.

## Subsequent app integration

The next backend task described above is now implemented and verified in [PHASE_4_INTEGRATION_REPORT.md](PHASE_4_INTEGRATION_REPORT.md). User-facing mode/layout controls are Phase4.4; physical acceptance is still pending.
