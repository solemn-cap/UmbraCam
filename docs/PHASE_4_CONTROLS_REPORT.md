# Phase 4.4 Camera Studio controls

Status: Phase4.4 complete for the reviewed native controls. Independent native review: **ship**. This is not Phase 4.5 physical acceptance.

## Implementation

Camera Studio exposes Single/Dual mode, exact advertised front/rear pair selection, rear-main/front-inset PiP or rear-above-front split, and optional microphone. Single remains the default. Mode, pair, layout and microphone actions freeze during capture and binding. Selected mode taps preserve settings. Positive inventory can be refreshed; loading, platform, query failure, missing pair and stale selection have explicit explanations. Dual Start requires the selected pair still to be listed. The capture adapter independently validates eligibility.

The screen keeps the approved stock Material 3 foundation, system light/dark theme, existing shared scroll, status and explicit Start/Stop. Dual portrait HD limits are visible before Start. Home and metadata checks do not start capture. Launcher assets and SOS naming are preserved. Version is 0.5.0/code 5.

## Created files

- `app/src/main/java/com/umbracam/app/camera/DualOptionsState.kt`
- `app/src/main/java/com/umbracam/app/camera/CameraModeControls.kt`
- `app/src/test/java/com/umbracam/app/camera/DualOptionsStateTest.kt`
- `app/src/androidTest/java/com/umbracam/app/DualCameraControlsTest.kt`
- `app/src/androidTest/java/com/umbracam/app/DualCameraControlStatesTest.kt`
- `.impeccable/surfaces/umbracam-app-camera-camerastudioscreen-kt-7da59865.md`
- This report; `.impeccable/review/{PHASE4_PACKET,PHASE4_FINISH_REVIEW,PHASE4_DOCUMENTATION}.md`;49 native PNGs and local execution/evidence artifacts listed below.

## Modified files

`app/build.gradle.kts`, `app/src/main/java/com/umbracam/app/camera/{CameraStudioViewModel,CameraStudioScreen,CameraRecordingController}.kt`, `app/src/main/res/values/strings.xml`, `README.md`, and `docs/{PRODUCT_SPEC,ARCHITECTURE,DEVELOPMENT_PLAN,TESTING,DECISIONS}.md`. The controller microphone setter now updates the immutable dual selection; loss of a requested grant does not silently downgrade the dual audio request. No completed media is migrated or deleted. The Impeccable documenter wrote only its comparison artifact; DESIGN.md and PRODUCT.md were preserved exactly.

## Dependencies

None added or changed. Existing pinned CameraX/Compose/Android toolchain retained.

## Verification

All commands ran from `C:\Projects\umbracam`. Java17 and the project SDK were selected explicitly. Builds ran serially using local Gradle8.13, offline cached dependencies and quoted Kotlin flags:

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/java/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path '.tools/android-sdk').Path
./.tools/gradle/gradle-8.13/bin/gradle.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :core:recording:test :app:lintDebug --offline --no-daemon --max-workers=2 '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

The first build passed in8m14s; the final product build passed in3m26s (`.tools/phase4-controls-final-build.log`). **114 JVM tests passed:38 app/76 core, zero failures/errors/skips.** Five new pure tests validate exact pairs, stable deduplication/order, no inferred subsets, changed/missing/duplicate inventory, unknown/same-facing groups and unavailable states. Lint: **0 errors/11 existing warnings**. Subsequent screenshot-helper-only Android test assemblies also passed; final display-buffer assembly took44s. Product APK remained unchanged throughout capture corrections.

Installed version0.5.0/code5 debug APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `2795ed31e14bb6482feddee3c99f35ce7b517966f01e195626a01aa93788dbdd`.

### Native execution

Only isolated `UmbraCam_Phase3_API30_AOSP`, x86_64 Android11/API30, explicit serial `emulator-5558`, adb server5038. No physical device was used. Invocation base:

```powershell
./.tools/android-sdk/platform-tools/adb.exe -P 5038 -s emulator-5558 shell am instrument -w -r -e class '<selected class/methods>' com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

**14 distinct native methods passed**:

- `DualCameraControlsTest`: `actualModeLayoutMicrophoneAndFrozenSettingsProduceFiles`, `captureNativeStudioStatesWithoutStartingCapture` (requires `-e dualControls true`). Actual Single→Dual, default PiP, silent MP4, Split, selected microphone MP4, repeated mode tap preserving settings, recreation, disabled busy controls, explicit Stop, saved video/audio tracks and explicit return to Single.
- `DualCameraControlStatesTest`: `checkingDisablesDual`, `oldPlatformRetainsSingleWithoutClaimingHardwareFailure`, `unavailableQueryOffersRetry`, `noAdvertisedPairDisablesDualAndExplainsFallback`, `multiplePairsCanBeChosenAndStalePairIsExplained`. These are explicitly synthetic metadata fixtures using the production native controls, not actual API29 or incompatible-hardware evidence.
- `DualRecordingIntegrationTest`: `bothLayoutsUseTheServiceAndPublishVersionTwoWithFrozenSettings`, `microphoneRecreationAndHomeKeepSameAttemptUntilNotificationStop`, `sourceLossFinalizesWithWarningAndExplicitRecoveryRetainsEarlierBytes`, `unadvertisedPairNeverStarts`, `deniedRequestedMicrophoneNeverBecomesSilentDualRecording` (requires `-e dualIntegration true`). Denial was run separately after host-side permission revocation on the inactive app.
- `CameraRecordingTest`: `availableFrontAndRearRecordSilentDecodableVideos`, `microphoneRecordingContainsAudioAndVideoTracks`. Single capture regression on the final APK.

Exact selected-method invocations are in `.tools/phase4-controls-evidence/COMMANDS.md`; runner output is retained in the same directory: `phone-light-controls.txt` (7 passed), `backend-regression.txt` (3 passed/1 timeout), `home-regression-rerun.txt` (1 passed), `denied-audio-regression.txt` (1 passed), `final-recording-regression.txt` (3 passed), and four `*-display-final.txt` files (6 passed in each configuration). `.tools/phase4-controls-capture-matrix.ps1` records the exact final matrix command and settings. All14 distinct methods have final passing evidence; no skipped method is counted.

The backend Home-continuation check initially timed out while a host build was still active, before notification Stop. It passed after the build finished. Host contention is a possible contributor, not an established root cause; source-health thresholds were not relaxed. Early capture attempts exposed first-frame timing, incomplete Compose-layer readback and a composed-camera `PreviewView.StreamState` that did not reliably reach STREAMING. The final helper waits for actual pixels from the known bright emulator feed, settles, and uses the real display buffer. These capture-harness failures are retained; their images were superseded, not accepted as review evidence. adb initially could not pull scoped external screenshots as shell, so emulator-only adbd root was used to extract artifacts; application capture still ran under its ordinary UID and permission gates.

### Native visual evidence and preservation

The final matrix runs production capture/navigation plus five metadata fixtures in phone720×1280/320dpi light1.0, phone dark1.3, phone light2.0 and tablet-sized1440×2560/320dpi light2.0. It exercises actual scrolling/reachable controls at large text. **49 final native display captures** are listed in `.impeccable/review/PHASE4_PACKET.md`, including real busy recording. Every listed file was opened and checked before independent review. Black side bars in the FIT_CENTER portrait preview are intentional letterboxing. No web detector was run for native Compose; no new shipping raster was produced.

Before/after SHA256 manifests verified **all33 prior completed videos unchanged**, with46 completed files after this task. See `media-preservation.json`, `prior-media.txt`, `final-media.txt` in the evidence directory. No active RecordingService remained after the final runner.

### Independent finish and cleanup

Fresh Impeccable finish reviewer inspected all49 required native captures and relevant source, returned the complete five-section contract with **ship**, and found no material visual fixes. Its scope is the supplied native visual evidence, not an independent controller/test audit or physical certification. See `.impeccable/review/PHASE4_FINISH_REVIEW.md`.

Fresh documenter compared palette, native type roles, local/shared spacing, Material components and launcher/SOS identity against the incumbent. It confirmed preservation and wrote only `.impeccable/review/PHASE4_DOCUMENTATION.md`; no design-system replacement occurred. Pre-existing stale milestone text in Impeccable PRODUCT.md/DESIGN.md and the absent `.impeccable/design.json` were disclosed without unrelated repair. Repository product/architecture/development/test documentation reflects the new feature separately.

Cleanup restored font1.0, night mode off,720×1280 at320dpi, CAMERA/RECORD_AUDIO denied and adbd shellUID2000. `restored-state.json` records readback and no active recording service. Guest storage was synced, console shutdown completed, and both task-owned emulator processes were checked for exit. Userdata and completed media were retained. No phone was accessed.

## Known limitations

Physical dual-camera acceptance remains pending; no phone is available or touched for this milestone. The project AOSP API30 emulator uses synthetic sources and proves only its tested configuration. Current dual binding is portrait/rotation 0/HD SDR; no live source/layout swap, independent output tracks or mirror controls. Native semantic/touch/large-text checks do not prove TalkBack speech or physical performance. Screen capture, Reaction recording, SOS recording and Vault remain future work.

## Recommended next task

Phase 4.5 physical acceptance: named compatible and incompatible devices, independently decoded source provenance, actual microphone audibility/sync, interruption, rotation rejection, thermal/long-duration/low-storage and OEM lifecycle matrix. Prepare the matrix without using the user's phone; do not claim the whole dual-recording phase accepted until physical evidence exists.
