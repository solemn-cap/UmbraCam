# Phase 4.5 dual-camera acceptance

Status: acceptance tooling and emulator subset complete, including final fixture/lint checks. Full Phase4.5 physical acceptance is **pending**, and emulator performance is **not qualified** against the proposed30fps targets. The user's phone was not accessed. Phase4.4 controls and its native review are complete; see [controls report](PHASE_4_CONTROLS_REPORT.md). Do not interpret earlier historical design/provenance blockers as current blockers.

## Scope and evidence rules

Qualify the existing portrait/rotation0 HD SDR dual profile on named compatible hardware, plus an incompatible control and actual API29 Single fallback. This milestone adds acceptance tooling, not another capture pipeline or new UI. Screen capture, independent tracks, mirror switches, general orientation support and precision synchronization remain later work.

Record app APK SHA/version, model/build/security patch, API, exact independently advertised Camera2/CameraX IDs, requested versus negotiated format, layout, microphone, lighting, power/thermal/storage conditions, duration and outcome for every run. A supported model name or metadata pair alone does not pass capture acceptance. Synthetic unavailable-state UI tests are not incompatible-device evidence. Prior Samsung single-camera results do not qualify dual capture.

## Matrix

| Case | Procedure and required evidence | Current physical result |
| --- | --- | --- |
| Identity and eligibility | Read-only device snapshot; record complete advertised groups and exact shared front/rear pair, actual bind outcome and profile. On incompatible hardware explicitly use Single, with no silent substitution. | Pending compatible and incompatible devices |
| Basic PiP and Split | Each layout:30-second silent and microphone clips, actual preview/output agreement, portrait720×1280/rotation0, one video track and selected audio only, decode beginning/middle/ending and full playback. | Pending |
| Independent sources | Use separate visible changing markers in front/rear fields; label/timestamp them. Decode both regions across independent changes. Two similar visible regions or callbacks alone are insufficient. | Pending; causal emulator provenance previously verified |
| Sustained | At least5 minutes per layout/profile, microphone selected; include foreground and Home continuation. Record state, source errors, cadence/gaps, thermal/battery/storage before/after, final output and notification Stop. | Pending; new emulator test is supporting evidence only |
| Lifecycle | Recreation/Home/navigation, notification reopen/Stop, screen off and return; immutable attempt/settings and no implicit restart. Capture exact timings and segment hashes. | Pending for dual physical hardware |
| Required-source failure | Real competing camera, camera/mic privacy controls, grant revocation and source stall. Leave RECORDING when a source fails; preserve valid output with warning. Resume only by explicit same-spec action/new acquisition. | Pending |
| Unsupported orientation | Pre-start landscape/unsupported rotation must reject honestly; rotate during accepted capture without changing locked output geometry or silently rebinding. | Pending |
| Stop/races/process death | Stop during preparing/recording/saving; terminate and relaunch; no automatic capture, earlier bytes retained, unverified output not listed saved. | Pending |
| Storage/finalization | Verify preflight and reversible publication failure first. True ENOSPC only on a dedicated disposable target with a bounded fixture; never fill or clear a personal phone. Preserve old files and report failed finalization. | Pending real storage exhaustion |
| Thermal/OEM behavior | Observe ordinary sustained load, throttling and power policies. Record actual state/temperature/errors; an injected thermal status is not physical thermal evidence. Do not heat hardware deliberately. | Pending |
| Accessibility | Real TalkBack focus/order/status, reachable Stop, permission recovery, large text, system Back and interrupted/saved warnings. | Pending speech/gesture checks; native visual review already passed |

## Measurement policy declared before qualification

Safety/structural gates: no false RECORDING, no unrequested source/audio, no automatic reacquisition, one publication owner, all previous files unchanged, correct locked geometry/tracks and playable finalized output. Any failure blocks the affected configuration regardless of average frame rate.

The new emulator fixture holds RECORDING for300seconds, samples state every100ms, moves Home halfway, uses notification Stop, requires no session error, duration at least hold−2seconds, decodes three frames and checks tracks and prior-file hashes. It emits observed encoded-sample cadence, sorted presentation-time p95/max gaps, decode-order reversals and first/last timestamps. These measurements are **descriptive**: encoded timestamps are not proof of changing camera content, sensor synchronization, audible microphone capture or absence of duplicated frames. No physical frame-rate/performance claim follows from an emulator pass.

For a proposed30fps physical qualification, assess each complete30-second window: at least27 distinct presented frames/sec, p95 frame interval≤67ms, no unexplained video gap/freeze>250ms, and independent changing content in both regions. These are initial qualification targets, not a product guarantee; record failures without loosening thresholds after seeing results. A profile that misses them remains unqualified until an explicit profile/requirements decision. Export full sample timestamps and decoded-region motion measurements for this assessment; the emulator summary alone does not satisfy the window/content requirement.

Microphone acceptance requires full listening and visible/audible clap or equivalent external marker at start/middle/end. Record measured offset and drift plus uncertainty; container-track endpoints alone are not an A/V-sync measurement. Precision A/V/cross-camera synchronization thresholds and long-session guarantees remain Phase7. Phase4 cannot claim synchronized output from mere track presence.

## Reproducible tooling

`scripts/phase4/collect-device-evidence.ps1` uses the existing PowerShell7+ host, takes an explicit adb executable, serial and server port, creates a fresh output directory and records identity, app package state, battery/thermal snapshots and SHA256 manifest. Relative output paths use the caller's PowerShell location. It is read-only: no installation, permission/settings changes, camera opening or recording. It was not run against a physical phone.

```powershell
./scripts/phase4/collect-device-evidence.ps1 -AdbPath .tools/android-sdk/platform-tools/adb.exe -Serial emulator-5558 -ServerPort 5038 -OutputDirectory .tools/phase4-acceptance-evidence/identity-before
```

`DualRecordingAcceptanceTest` is opt-in and emulator-only. It uses the real production controller/service/storage and UI Start, preserves old media and writes per-layout JSON under app-private `files/phase4-acceptance/`. Test-only camera/microphone grants apply only to the selected emulator. A unique run UUID is mandatory; duplicate artifact names fail rather than overwrite evidence. Duration accepts30–300seconds, default300. A30-second smoke pass is not a sustained pass. Run without a concurrent host build.

```powershell
./.tools/android-sdk/platform-tools/adb.exe -P 5038 -s emulator-5558 shell am instrument -w -r -e dualAcceptance true -e acceptanceRun '<fresh UUID>' -e acceptanceSeconds 300 -e class com.umbracam.app.DualRecordingAcceptanceTest com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

Read `OK (2 tests)` and the per-layout JSON, not just adb's exit code. On any failure retain runner output, partial JSON/session error and prior hashes. Restore fixture grants/settings after testing. The fixture never runs on a physical target; physical execution currently follows the manual matrix above.

## Created/modified files and dependencies

Created `app/src/androidTest/java/com/umbracam/app/DualRecordingAcceptanceTest.kt`, `scripts/phase4/collect-device-evidence.ps1` and this report/matrix, plus local run evidence. Modified `scripts/phase4/README.md`, `docs/DEVELOPMENT_PLAN.md`, `docs/TESTING.md` and `README.md`. No production code, app version, dependency, permission, visual-system or launcher change.

## Executed results

The new test APK compiled successfully with `:app:assembleDebugAndroidTest` in51seconds; no production APK was rebuilt or modified. The collector ran on the selected emulator and passed parser, invalid-serial rejection, existing-directory rejection and four-file manifest-hash verification. Its final current-location path handling and after-run manifest were also verified. Five updated/new documentation files passed37 relative-link checks with no missing targets.

Both methods passed in623.711seconds: `sustainedSplit` and `sustainedPictureInPicture`, each configured with300seconds. Environment: `UmbraCam_Phase3_API30_AOSP`, x86_64 Android11/API30, build `RSR1.210722.013.A2/10067904`, SwiftShader, emulated front/rear inputs, `-no-audio`, explicit adb5038/serialemulator-5558. Run UUID `349d88b2-efc2-41e2-8f02-9ba6adfa1f87`. No host build ran during recording. The headless emulator does not capture the user's webcam or ambient microphone audio.

| Observation | Split | PiP |
| --- | ---: | ---: |
| Wall-clock hold in RECORDING | 300.037s | 300.016s |
| Home action after hold began | 150.084s | 150.031s |
| MP4 duration | 302.587s | 301.265s |
| File bytes | 17,108,880 | 16,746,229 |
| Encoded AVC video samples | 3,327 | 3,294 |
| Observed video samples/sec | 10.996 | 10.933 |
| p95 sorted video presentation gap | 179.044ms | 176.567ms |
| Maximum video presentation gap | 1,702.344ms | 923.311ms |
| Audio/3gpp access units | 4,724 | 4,706 |
| Decoded frame probes | 3 | 3 |
| Older completed files preserved by fixture | 46 | 47 |

Both used exact frontID1/rearID0, microphone selection, notification Stop, locked portrait720×1280/rotation0, a single video track plus audio, no session error and identical earlier hashes. **Neither run meets the proposed30fps performance targets.** A passing structural/continuity test is not a passing performance qualification. The software-rendered emulator cannot establish physical performance; thresholds were not relaxed. These are encoded-sample observations, not independent content, full playback, audibility or sync certification.

JSON and runner evidence are under `.tools/phase4-acceptance-evidence/`: `split.json`, `pip.json`, `sustained-run.txt`, `run-id.txt`, `identity-before/`, `identity-after/`, `collector-checks.json` and `final-collector-checks.json`. Test artifacts remain in app-private `files/phase4-acceptance/`; completed videos remain in private recording storage. Final fixture revisions add serialized decoded dimensions/reported format frame rate and the standard API33+ notification test grant; API33+ remains unexecuted.

Final `:app:assembleDebugAndroidTest :app:lintDebug` passed in1m18s, with **0 lint errors/11 existing warnings**. Both methods then passed again with30second holds in78.104seconds on the final fixture. These are **two distinct native methods/four successful executions**, not four distinct tests; the shorter checks do not count as five-minute evidence. Their JSON confirms720×1280 decoded and track dimensions; reported video frame rate was11 for the short Split run and9 for short PiP. All four JSON outcomes, media SHA256 values and final schema fields were checked against actual completed-file hashes. Source changes after the long run were test-only telemetry/notification-fixture changes; no capture implementation changed.

Exact build/test commands are in `.tools/phase4-acceptance-evidence/COMMANDS.md`, with the final smoke UUID in `smoke-run-id.txt` and output in `final-smoke.txt`. Build logs are `.tools/phase4-acceptance-build.log` and `.tools/phase4-acceptance-final-build.log`. Native tests ran with no concurrent Gradle invocation. No executed test failed or skipped in this step. API33+, physical hardware, real thermal/storage faults, full playback and acoustic sync were not executed and are not passes.

Host before/after manifests verified **all46 existing completed videos unchanged**,50 files after the four new captures. `artifact-checks.json` and `media-preservation.json` retain verification. CAMERA/RECORD_AUDIO fixtures were restored to denied, no RecordingService remained, guest storage was synced and the two task-owned emulator processes exited normally. No userdata/media was cleared. Local evidence includes the restored permission readback and shutdown check.

Latest product baseline remains0.5.0/code5, APK SHA256 `2795ed31e14bb6482feddee3c99f35ce7b517966f01e195626a01aa93788dbdd`. The114 JVM and14 distinct native tests recorded in Phase4.4 are historical baseline evidence, not newly rerun tests in this step.

## Completion gate and next task

Full Phase4.5 remains open until named physical compatible/incompatible targets meet the matrix. Missing requirement: available authorized physical hardware. While the user's phone is in use, complete the emulator/tooling subset and preserve the pending physical rows. Do not label all Phase4 accepted or skip into Phase5 implementation based solely on emulator success.
