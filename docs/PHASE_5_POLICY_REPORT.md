# Phase5.1 consent/session foundation

Status: Phase5.1 complete. Phase5 is started, but runtime screen recording is not yet available. Phase4 physical acceptance remains pending.

## Implementation

Implemented concrete screen consent/session policy and bounded output-size calculation in the existing pure core. Every attempt needs a fresh correlation from the current owner; cancellation/replay/foreign/late results cannot authorize it. Projection readiness plus a real encoded sample is required before RECORDING. Stop, revocation, acquisition expiry, stale callbacks and publication races are guarded. No same-token recovery exists. Static content is not classified as source loss. Design and integration sequence: [PHASE_5_DESIGN.md](PHASE_5_DESIGN.md).

## Created files

- `core/recording/src/main/kotlin/com/umbracam/core/recording/ScreenSessionCoordinator.kt`
- `core/recording/src/main/kotlin/com/umbracam/core/recording/ScreenOutputSize.kt`
- `core/recording/src/test/kotlin/com/umbracam/core/recording/ScreenSessionCoordinatorTest.kt`
- `core/recording/src/test/kotlin/com/umbracam/core/recording/ScreenOutputSizeTest.kt`
- `docs/PHASE_5_DESIGN.md` and this report.

## Modified files

`core/recording/src/main/kotlin/com/umbracam/core/recording/RecordingSessionTransitions.kt` adds an overload allowing screen interruptions to forbid same-grant recovery, retaining the existing two-argument camera behavior. `README.md` and `docs/{PRODUCT_SPEC,ARCHITECTURE,DEVELOPMENT_PLAN,TESTING,DECISIONS}.md` record Phase5's active checkpoint and deferred Phase4 acceptance. No app UI, manifest or capture adapter changed.

## Dependencies

None. Existing pinned toolchain and modules retained. The planned Android feasibility uses platform APIs; no speculative dependency/module is added.

## Exact tests and results

Initial `:core:recording:test` passed in1m10s before the final publication/geometry additions. Final serial verification:

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/java/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path '.tools/android-sdk').Path
./.tools/gradle/gradle-8.13/bin/gradle.bat :core:recording:test :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --offline --no-daemon --max-workers=2 '-Pkotlin.incremental=false' '-Pkotlin.compiler.execution.strategy=in-process' --console=plain
```

**143 JVM tests passed:105 core/38 app, zero failures/errors/skips.** This includes29 new tests:23 in `ScreenSessionCoordinatorTest` and6 in `ScreenOutputSizeTest`. All previous single/dual/domain/storage/journal/navigation-model tests remained passing. `:app:assembleDebug` and `:app:lintDebug` passed; lint reports **0 errors/11 existing warnings**.

The expanded policy build passed in2m6s (`.tools/phase5-policy-build.log`). Review then identified a minimum-size clamp that could distort an extreme aspect ratio; it was replaced by explicit rejection plus a regression test. The final complete command above passed in1m21s (`.tools/phase5-final-build.log`). No failed or skipped tests are presented as passes. New tests exercise cancelled/late/foreign/replayed consent; invalid identifiers burning a grant correlation; repeated request/busy rejection; independent projection/sample gates; missing/empty/late output; valid static content; Stop versus late readiness; revocation/warning preservation without recovery; stale callbacks even with reused string IDs; terminal restoration and retained files; once-only finalization; backend error versus publication ownership; storage-owner failure; missing callback timeout; and portrait/landscape/tablet/odd/tiny/oversize/extreme-aspect candidate dimensions. Existing single/dual suites verify the shared transition change retains their behavior.

New debug APK SHA256: `d2513d74b1cd4faec7d2c70b82b8b4e70d27221656a1987677f09bc55a0fe0cf`; app version remains0.5.0/code5 because user-facing capabilities are unchanged. Core classes changed the binary, so the earlier Phase4 native evidence remains tied to its earlier APK, not this new artifact. No APK was installed and no emulator or phone was accessed during this checkpoint. No native screen-capture/consent/foreground-service behavior is claimed. The design's platform facts were checked against the linked official Android references on2026-11-01.

## Limitations and recommended next task

The core never calls Android or grants permission. There is no actual MediaProjection prompt, screen encoder/service/journal/UI, emulator capture or physical validation in5.1. Existing Reaction Studio remains a preview, and no screen-recording capability is claimed. The user phone was not touched. Next is5.2: implement and verify the real system-consent/typed-service/MediaCodec/MP4 path described in the design, including process-level camera/screen exclusion and private crash reconciliation. Then ship/review the user controls and acceptance matrix.
