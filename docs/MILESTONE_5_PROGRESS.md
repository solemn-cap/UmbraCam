# UmbraCam Milestone 5 — Reaction Studio progress

This major slice began 2026-11-02.

The core contracts now distinguish hardware presence from an implemented capture path. A front camera alone cannot enable combined mode; the adapter must explicitly report composition support for the selected layout. A microphone or internal-audio track likewise requires both source availability and an implemented capture path. Requests still derive their required source set.

Reaction Studio now exposes Screen only and Screen + camera choices, layout choices, microphone status and the internal-audio limitation. Unsupported options remain visible with a concrete reason, and Start is gated by request validation. Camera permission is requested only on Start for a supported combined request; microphone permission is requested only when enabling a supported mic control. Capability discovery itself opens no device and requests no permission. The existing screen-only adapter receives the typed request, validates it before showing consent, validates again when consent returns, and checks it again before service acquisition. Request policy is held only in memory and is not journaled or placed in an Intent.

The Android adapter now contains a real Camera2 + MediaProjection GLES composition path for silent PiP and split output. The capture service declares camera ownership only for combined requests; consent, camera permission and both-source encoded readiness gate start. A stale or failed camera feed interrupts capture, and the screen journal records required sources while preserving screen-1 compatibility. Microphone commentary and internal audio remain unavailable. On the API36 AVD, fresh-consent capture, decode and publication passed for both layouts. Physical device acceptance remains pending.

Tests cover hardware-versus-adapter capability gating, request sources, real-consent combined layouts and source loss. They were accumulated through implementation and run as a final batch; detailed results follow. The wider Milestone 5 acceptance matrix remains pending.

## Verification for this implementation slice — 2026-11-02

Host command (JDK17, SDK36, Gradle8.13, offline, max-workers2, in-process Kotlin):

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :core:recording:test :app:testDebugUnitTest :app:lintDebug --offline --no-daemon --max-workers=2 '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

The host command passed: **178 JVM tests passed: 124 core and 54 app, zero failures/errors/skips.** Debug and AndroidTest APKs assembled. Lint passed with **0 errors and 27 warnings**. No dependency was added.

The API36 `UmbraCam_Phase3_API36` emulator ran `:app:connectedDebugAndroidTest`. The runner XML contained 54 cases: 30 passed, 24 skipped by existing device/explicit-opt-in guards, zero failures/errors. `ScreenRecordingIntegrationTest#frontCameraCompositionRecordsBothLayoutsWithFreshConsent` passed: real screen-consent dialogs were accepted for both PiP and split, both required sources gated RECORDING, and each saved output decoded. The API29 `UmbraCam_Phase2_API29` emulator passed that same combined-layout case and `frontCameraLossStopsCombinedCaptureAndPreservesPartialVideo`; the latter closed the actual opened Camera2 device and verified the interrupted partial was saved and decodable. API36 also passed the source-loss test after the test was hardened to wait for a new attempt ID, avoiding stale terminal state from a prior invocation. A final API29 rerun of that test-only attempt-ID guard could not start because the cold-booted API29 image remained incomplete without a camera service. An API30 emulator cold boot also remained offline with no camera service after one lower-memory retry, so API30 app behavior is unverified. No physical device was accessed.

API30 follow-up completed on a fresh `UmbraCam_API30_FreshCam` AVD using the API30 x86_64 default image with both virtual cameras enabled. Android reported `sys.boot_completed=1`, a live `media.camera` service, and two camera devices. `frontCameraCompositionRecordsBothLayoutsWithFreshConsent` and `frontCameraLossStopsCombinedCaptureAndPreservesPartialVideo` both passed (2 tests, 0 skipped, 0 failed; `:app:connectedDebugAndroidTest` BUILD SUCCESSFUL in 1m58s). The attempt-ID guard now also passes on API30, alongside API29 and API36 source-loss evidence.

The previously captured Reaction Studio screenshots (`captures/device-validation/milestone5-reaction-*.png`) show the earlier screen-only adapter checkpoint and are layout evidence only. The combined-mode integration test now verifies the current screen + camera controls and capture path; those old screenshots should not be used as current capability evidence.

This is not the Milestone 5 exit. Combined capture is verified on API29, API30 and API36 AVDs; camera-loss finalization is verified on API29, API30 and API36. Physical device compatibility, sustained recording, thermals and OS sharing-chip loss remain open. Microphone and internal audio remain unavailable.
