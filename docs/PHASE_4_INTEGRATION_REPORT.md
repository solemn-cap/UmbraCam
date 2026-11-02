# Phase 4.3 — App integration

Date: 2026-11-01. Status: initial portrait dual backend integrated and verified on the selected emulator. User-facing dual mode/layout controls remain Phase 4.4; physical acceptance remains Phase 4.5.

## 1. Implementation

The existing application-scoped CameraRecordingController now routes single and dual sessions through one observable session and the existing typed, non-sticky RecordingService. No second service/provider owner was introduced. SingleCameraSessionCoordinator retains its one-camera boundary. Dual sessions use the previously tested core policy, exact front/rear selectors and independently advertised Camera2/CameraX pair.

DualCameraBinding binds one shared Preview/VideoCapture group and Recorder, with a fresh lifecycle identity for each acquisition/layout. Callbacks capture the immutable core attempt token and binding identity. They observe each device's usable capture progress without extra streams or raw requests. Events from closed bindings are dropped; capture callbacks queued for1,500 ms or more interrupt rather than being credited as fresh. A 250 ms main-thread tick checks the core freshness/acquisition policy. Recording requires both inputs, Recorder output and selected microphone readiness. Missing requested microphone permission fails before foreground-service startup, never silently downgrading to video-only capture.

Accepted service ownership keeps capture across preview detachment, navigation, Home and activity recreation. Notification Stop remains attempt-specific. Stop/recovery/publication use the existing private staging, validated atomic move, quarantine and finalization watchdog. Source loss remains distinct from container finalization success. Primary error plus additional diagnostic codes are carried into dual metadata/UI state. Finalize claims dual publication once; only its storage owner can complete or fail publication.

Initial binding supports **portrait, target rotation0, HD SDR 30 candidate, rear main/front inset or equal top/bottom split**. Unsupported shape/rotation settings are rejected. Pair/profile binding failures do not fall back silently to one camera. Finalized dual dimensions/rotation must match the descriptor before publication. Broader rotation/mirror/crop mapping remains outside this verified initial configuration.

RecordingSessionJournal preserves its original one-argument write method and version 1 single-session format. Version 2 adds the immutable dual descriptor, exact IDs, profile, output geometry, composition mirror policy and bounded diagnostics. Readers accept both formats, reject mismatched/unknown/corrupt descriptors and preserve media. Reconciliation retains version 2 context, validates earlier completed references, quarantines abandoned pending output and returns only a terminal warning without an acquisition token or selected capture mode.

## 2. Created files

- `app/src/main/java/com/umbracam/app/camera/CameraSessionCoordinator.kt`
- `app/src/main/java/com/umbracam/app/camera/DualCameraBinding.kt`
- `app/src/test/java/com/umbracam/app/camera/CameraSessionCoordinatorTest.kt`
- `app/src/test/java/com/umbracam/app/camera/DualSessionJournalTest.kt`
- `app/src/androidTest/java/com/umbracam/app/DualRecordingIntegrationTest.kt`
- `docs/PHASE_4_INTEGRATION_REPORT.md` (this report)

## 3. Modified files

Production source: CameraRecordingController.kt, CameraStudioViewModel.kt, RecordingSessionJournal.kt and core/recording/DualCameraSessionCoordinator.kt (explicit external interruption entry point). The ViewModel exposes backend selection for fixtures and the following controls task; no new user-facing mode selector is delivered here.

Documentation: README.md and docs/ARCHITECTURE.md, DEVELOPMENT_PLAN.md, TESTING.md, DECISIONS.md, PHASE_4_DESIGN.md and PHASE_4_PROVENANCE_REPORT.md. Existing RecordingService, application/manifest, screen/reaction/SOS/Vault modules and launcher identity were not changed.

## 4. Dependencies added

None. No permissions, SDK/library versions or Gradle catalog changes. App version remains0.4.0/code4. Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, SHA256 `f35fa13e7bc714b6af056cfa246587366d42f8d93ff058af28adfb02fd0ddd4e`. It was installed only on the emulator.

## 5. Exact verification executed

The required project docs were read before implementation. Final complete build command:

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/java/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path .tools/android-sdk).Path
.\.tools/gradle/gradle-8.13/bin/gradle.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :core:recording:test :app:lintDebug --offline --no-daemon --max-workers=2 '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

An overlapping build invocation damaged the incremental compiler cache; those builds failed and were not installed. Builds were stopped and run serially, in-process without incremental compilation. PowerShell requires the property arguments quoted as above. Final full build passed in 2m 42s (`.tools/phase4-integration-serial-final.log`); the final fixture-isolation/test APK and lint build passed in 57s (`.tools/phase4-integration-isolation-build.log`). Early tests also caught a journal JVM-signature incompatibility; retaining the original overload fixed it, and the unchanged legacy tests passed.

All device commands explicitly targeted `adb -P 5038 -s emulator-5558`. Target: UmbraCam_Phase3_API30_AOSP, Android11/API30 x86_64, SwiftShader,4cores/3072MiB,720×1280/320dpi, headless/no audio/no snapshots; rear0/front1. This is the same isolated AVD used for the earlier provenance check. No phone was accessed.

Native runner invocations use `am instrument -w -r -e class <selected methods> com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner`, with `-e dualIntegration true` for the new suite. Microphone denial was run separately after revoking RECORD_AUDIO on the inactive emulator app. Selected dual methods:

- bothLayoutsUseTheServiceAndPublishVersionTwoWithFrozenSettings
- microphoneRecreationAndHomeKeepSameAttemptUntilNotificationStop
- sourceLossFinalizesWithWarningAndExplicitRecoveryRetainsEarlierBytes
- immediateStopAndUnsupportedOrientationNeverClaimRecording
- publicationFailurePreservesPriorFilesAndRetainsUnpublishedOutput
- deniedRequestedMicrophoneNeverBecomesSilentDualRecording
- unadvertisedPairNeverStarts

Selected unchanged CameraRecordingTest regression methods:

- availableFrontAndRearRecordSilentDecodableVideos
- microphoneRecordingContainsAudioAndVideoTracks
- recreationKeepsTheSameAuthorizedRecording
- immediateStopDuringPreparationNeverStartsRecording
- homeKeepsRecordingAndNotificationStopFinalizes
- navigatingHomeKeepsRecordingUntilExplicitStop
- notificationOpensCameraOnceWithoutRestartingCapture
- unwritableStagingDirectoryPreservesCompletedFiles

The first expanded batch exposed a fixture-isolation error: the deliberately invalid pair from one test remained selected in the process controller. Later setup waited for that invalid preview. The run was stopped, each setup was changed to explicitly select single mode, and the affected batch passed on rerun. This was not resolved with an automatic product fallback.

An additional opt-in externalTerminationFixture was held in RECORDING after saving an earlier file. The host read its unique marker, explicitly force-stopped the emulator app, launched MainActivity and inspected the journal/service/camera state. Its intentionally killed runner reports process termination, not a JUnit pass. The separate oracle is `.tools/phase4-termination-result.json`.

## 6. Results

**109 JVM tests passed: 76 core / 33 app, zero failures/errors/skips.** This includes12 new routing/version 2-journal tests. App/test APK builds passed. Lint:0 errors / 11 warnings.

**15 distinct native methods passed** on the named emulator: 7 dual and 8 single-camera. Final microphone denial: `OK (1 test)` in 4.469s (`.tools/phase4-integration-denial-final.txt`); remaining dual methods: `OK (6 tests)` in 55.448s (`.tools/phase4-integration-isolated-native.txt`); single regressions: `OK (8 tests)` in 50.492s (`.tools/phase4-integration-final-single.txt`). Earlier smoke/repeated methods are not additional distinct coverage.

Dual output was decoded at720×1280, with exactly one video track and an audio track only for requested microphone capture. Version 2 journals matched the selected layout. Both layouts used the actual app service/storage path. Recreation/Home retained the same acquisition attempt until notification Stop. UID-scoped camera denial exited RECORDING, preserved a validated warning segment, and explicit same-session recovery used a new attempt while retaining identical earlier bytes. Publication failure used reversible permissions on the emulator app's completed directory; it reported FAILED, retained unpublished output and preserved prior files. Directory modes were restored; no disk-fill test was performed.

The external termination oracle confirmed all of:

- The marker was RECORDING with service ownership before force-stop.
- Relaunch changed the active version 2 journal to FAILED with PREVIOUS_SESSION_INTERRUPTED.
- Version 2 descriptor was retained; no recording authorization was restored.
- An earlier completed file kept its SHA256.
- No recording service or camera client was active after relaunch; pending output was reconciled without automatic restart.

Six completed recordings present before this task kept identical SHA256 hashes; none was missing. After all tests, 33 completed files existed, including new test outputs. Evidence is exported under `.tools/phase4-integration-evidence/` and `.tools/phase4-integration-evidence.tar`; command logs, preservation JSON and termination marker/oracle are under `.tools/phase4-*`. App-private originals remain in the AVD. No app-data clear or media deletion occurred.

## 7. Known limitations

Dual selection/layout/audio controls are not yet present in the product UI; fixtures call the backend selection API. Only the initial portrait/rotation0 geometry is enabled. Landscape/other rotations, broader crop/mirror behavior, physical OEM compatibility, real calls/power/screen-off behavior, sustained thermal/throughput/A-V sync and true disk-full acceptance remain pending. Microphone track presence is not proof of audibility or long-session synchronization. Source-stall detection depends on scheduling and is not a hard real-time guarantee. No physical dual result is claimed; historical Samsung single-camera checks do not validate this backend.

## 8. Recommended next task

Phase 4.4: add explicit Single/Dual mode, supported pair/layout and microphone controls to the approved Material3 Camera Studio, with truthful portrait/profile limits and unavailable/retry states. Freeze settings while busy, preserve recovery semantics and keep Home/metadata checks capture-free. Perform native light/dark/large-text/navigation/accessibility review. Physical acceptance remains Phase 4.5; no phone testing while the user is using it.

Final cleanup: eight updated/new Markdown documents passed44 relative-link checks with zero missing targets. CAMERA and RECORD_AUDIO fixtures were restored to denied, evidence was exported, guest storage was synced and both task-owned emulator processes exited after console shutdown. Userdata was retained; the phone was not accessed.
