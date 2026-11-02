# Phase 4.2 — Dual recording contracts and source-health policy

Date: 2026-10-31. Status: isolated domain implementation complete. No dual CameraX adapter, service integration, journal v2 or user controls are delivered here. Phase4.1 distinct-input provenance remains open.

## 1. Implementation

Added immutable dual settings: exact logical front/rear descriptors, PiP/split layout, explicit portrait/landscape output shape, target rotation, optional microphone and the initial HD SDR30 candidate. Output shape is separate from rotation because sensor orientation affects platform mapping. Final normalized geometry specifies rear/full plus front inset, or equal split regions. Validation rejects invalid/out-of-frame rectangles, invalid IDs, same/unknown-facing pairs and metadata mismatches. Eligibility requires the exact pair in independent Camera2/CameraX advertisements; larger groups never imply support for a subset. These contracts do not grant permissions or establish a supported recording profile.

Extracted the existing transition implementation into internal RecordingSessionTransitions. SingleCameraSessionCoordinator remains the public facade with the same methods/default arguments and its exact one-camera/optional-microphone validator. Existing single-camera state/recovery/finalization behavior is preserved and tested. No new module was created.

DualCameraSessionCoordinator applies the dual policy separately. Every acquisition receives an opaque token with a generation; callbacks for older tokens cannot start, stop, fail or publish newer work, even when an attempt string is reused in a new logical session. Each attempt clears its camera/frame/Recorder/byte/microphone evidence. Explicit recovery accepts only the same immutable settings, rotates the token and retains earlier completed segments.

Recording requires both exact cameras open, two advancing recent frame observations from each, confirmed Recorder Start, positive encoded bytes and requested microphone readiness. Receipts use an injected monotonic local clock; camera sensor timestamps are not compared. Duplicate/regressing frame numbers do not refresh progress. Acquisition expires at10000ms; active progress expires at1500ms. Expiry is checked before accepting a new event so a late frame cannot revive a stalled source. Known camera/microphone loss interrupts immediately on its callback. Encoded composite bytes cannot keep a missing secondary input healthy.

The Android owner must serialize calls and schedule tick; the core creates no timers or background work. A stalled scheduler can delay detection, so this is not a hard real-time guarantee. Interrupted state remains busy until Stop/finalization completes and never auto-resumes. Finalize claims publication once; duplicate Finalize, watchdogs and late backend failures cannot release that ownership. Only validated publication can report SAVED; its explicit storage-failure path reports FAILED while retaining earlier segments. Prior source-loss warnings survive successful file finalization. RecordingSession currently carries one primary error, so an existing interruption takes precedence over an additional save warning; broader multi-diagnostic persistence belongs to integration design.

## 2. Created files

- `core/recording/src/main/kotlin/com/umbracam/core/recording/RecordingSessionTransitions.kt`
- `core/recording/src/main/kotlin/com/umbracam/core/recording/DualRecordingSpec.kt`
- `core/recording/src/main/kotlin/com/umbracam/core/recording/DualCameraSessionCoordinator.kt`
- `core/recording/src/test/kotlin/com/umbracam/core/recording/DualRecordingSpecTest.kt`
- `core/recording/src/test/kotlin/com/umbracam/core/recording/DualCameraSessionCoordinatorTest.kt`
- `docs/PHASE_4_DOMAIN_REPORT.md` (this report)

## 3. Modified files

`core/recording/src/main/kotlin/com/umbracam/core/recording/SingleCameraSessionCoordinator.kt` delegates the existing public API to shared transitions and retains its validator. README.md and docs/ARCHITECTURE.md, DEVELOPMENT_PLAN.md, TESTING.md, DECISIONS.md and PHASE_4_DESIGN.md record implementation status and the remaining gates. No app controller, UI, permissions, manifest, journal, storage adapter or dependency configuration changed.

## 4. Dependencies added

None. Uses the existing Kotlin/JVM, StateFlow and JUnit dependencies. No version or Gradle catalog changes.

## 5. Exact tests and checks executed

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/java/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path .tools/android-sdk).Path
.\.tools/gradle/gradle-8.13/bin/gradle.bat :core:recording:test :app:testDebugUnitTest :app:lintDebug :app:compileDebugKotlin --offline --max-workers=2 --console=plain
```

The first successful build exposed a Kotlin generated-copy visibility warning for the attempt token. It was changed to an opaque identity class, then checks were rerun after adding duplicate-stat expiry and publication ownership coverage. Final log: `.tools/phase4-domain-publication.log`, BUILD SUCCESSFUL in17s. Tests and lint XML were inspected directly. No emulator, phone or capture test was run this step; domain logic and facade extraction were verified with JVM tests and compilation. No APK was assembled or installed.

## 6. Results

**97 JVM tests passed:76 core and21 app; zero failures, errors or skips.** New tests:8 settings/geometry/eligibility and28 coordinator tests. Existing40 core tests, including12 single-camera and11 recovery tests, passed unchanged. The21 app tests cover existing metadata/navigation contracts, file publication and journal behavior. App Kotlin compilation passed. Current lint report:0 errors,11 warnings. No new compiler warning remained in the final run.

New coverage includes exact independent pair selection; stale/ambiguous/unknown-facing inventory; no inferred pair from larger groups; rectangle boundaries/NaN/infinity; all output shape/rotation settings in final-frame space; both-camera readiness; optional audio; frame-before-open and unknown IDs; zero encoded data; duplicate/regressing frames; freshness and acquisition deadline boundaries; a stalled secondary despite increasing output; late-frame resurrection; immediate loss; Stop winning; explicit same-spec recovery; stale callbacks/generations; retained segments; duplicate Finalize/publication; watchdog and late-backend failure fencing; explicit storage failure and a non-monotonic test clock.

## 7. Known limitations

No production dual capture is enabled. Distinct independently moving input provenance remains unverified as recorded in [PHASE_4_FEASIBILITY.md](PHASE_4_FEASIBILITY.md). Final-frame geometry is tested, but its CameraX inverse rotation/mirroring/cropping mapping is deliberately unimplemented pending saved-output validation. Profile negotiation, permissions, service ownership/timer wiring, microphone health delivery, journal v2 compatibility and physical device acceptance remain later integration work. The existing installed/build APK remains the prior0.4.0 artifact; this step compiles source without packaging a replacement. JVM tests do not establish hardware, thermal, playback or runtime continuation behavior.

## 8. Recommended next task

Resolve the distinct-input provenance gate on a supported isolated fixture before product integration/controls. Then implement Phase4.3: exact dual CameraX adapter, binding/token/timer wiring, foreground ownership, versioned dual journal descriptor and validated publication using these contracts. Keep single-camera behavior and completed media compatible, and keep the user's phone untouched while unavailable. Do not bypass advertised-pair eligibility or claim this domain implementation completes Phase4 recording.

## Subsequent provenance verification

[PHASE_4_PROVENANCE_REPORT.md](PHASE_4_PROVENANCE_REPORT.md) closes the bounded emulator provenance blocker recorded above. Phase4.3 Android adapter/service/journal integration is now the next task; domain logic alone still does not enable product dual recording.
