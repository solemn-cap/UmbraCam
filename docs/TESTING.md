# Testing

## Milestone 5 Reaction Studio capability boundary

`ReactionRecordingTest` distinguishes hardware flags from usable capture paths. The Android adapter validates its typed request before chooser launch, after fresh consent, and before service acquisition. Native integration verifies both screen-camera layouts and actual Camera2 source-loss finalization on API29, API30 and API36 emulators. The new optional microphone/internal-playback track path and source-readiness/journal tests are authored but intentionally unrun until Milestone 9 is implemented; they do not certify physical-device compatibility, sustained throughput, thermal behavior, audibility or synchronization.

## Milestone 6 SOS Quick Record

Tests now cover the SOS session kind through core transitions, version-3 journal read/reconcile and completed-recording catalog labeling. Instrumentation covers the explicit SOS screen action, rear-camera start, microphone-off default, stop/finalization and retained SOS label. These tests are authored only; run them with the accumulated Milestone 5–9 verification batch. Also cover permission denial, notification denial, no camera, thermal/storage interruption, background continuation and notification Stop. The existing physical-install data-loss restriction still applies; use a disposable target for connected instrumentation.

2026-11-02 host verification: 178 JVM tests pass (124 core/54 app); debug and AndroidTest APK assembly pass; lint has 0 errors/27 warnings. API36 full native suite: 54 runner cases, 30 passed, 24 guarded skips, zero failures; the dedicated both-layout real-consent capture test passed. The API29 AVD passed combined PiP/split capture and actual Camera2-loss partial publication. API36 also passed actual Camera2-loss partial publication after the fixture was corrected to wait for a new attempt ID. A fresh API30 AVD then passed combined capture and source-loss finalization. Its lifecycle sweep passed cancellation, rotation, projection revocation and camera screen-off continuation; screen-only Home/playback hit repeated System UI ANRs, so that case remains unverified on API30. A fresh API36 boot in this follow-up did not expose ADB. No new physical result is claimed. Exact commands and caveats are in [MILESTONE_5_PROGRESS.md](MILESTONE_5_PROGRESS.md).

## Active Phase5.1 policy validation

[PHASE_5_POLICY_REPORT.md](PHASE_5_POLICY_REPORT.md) records pure consent/session/output-size tests and unchanged single/dual regression suites. These use opaque simulated correlations, never Android projection grants. No system chooser, screen output, emulator or physical behavior is validated by a JVM pass. Real consent, native output/provenance, rotation/secure-content/revocation and modern service behavior remain the next integration/acceptance work described in [PHASE_5_DESIGN.md](PHASE_5_DESIGN.md).

Executed final checkpoint:143 JVM tests passed (105core/38app, no failures/errors/skips), including29 new screen policy/sizing methods; app debug build and lint0errors/11existing warnings passed. No device was used or APK installed. The final APK contains the new unused-by-UI core policy; earlier native results apply to their recorded earlier artifact, not this build.

## Active Phase4.5 acceptance

[PHASE_4_ACCEPTANCE.md](PHASE_4_ACCEPTANCE.md) separates the physical acceptance matrix from supporting emulator evidence. `DualRecordingAcceptanceTest` is opt-in/emulator-only with a fresh run UUID and bounded30–300second hold per layout; it observes foreground→Home continuation, notification Stop, final media and old-file preservation. The read-only snapshot script records device/app/battery/thermal context without changing settings. Physical compatibility, real audio/content/motion, thermal/storage/OEM and accessibility acceptance remain unexecuted until appropriate hardware is available.

Executed: both methods passed with300second holds, then both passed with30seconds on the final fixture. Lint0errors/11existing warnings; collector parsing/argument/collision/current-location/hash checks passed. All46 earlier completed files retain their SHA256 hashes. Encoded video cadence was near11 samples/sec on the software-rendered API30 emulator, so performance qualification remains unmet. These four executions are two distinct native methods; the previous114 JVM baseline was not redundantly rerun for test-only changes. The report records exact commands, raw artifacts and limitations.

## Latest Phase4.4 verification

[PHASE_4_CONTROLS_REPORT.md](PHASE_4_CONTROLS_REPORT.md) records114 passing JVM tests,14 distinct passing native emulator methods, lint0errors/11existing warnings, the failed continuation/capture-harness attempts and their passing follow-ups. Real UI actions select PiP/split/microphone, freeze busy settings, preserve selection through recreation and save files with expected tracks; legacy single front/rear/audio regression passes. Six native methods run in four phone/tablet-size light/dark/large-text configurations. Forty-nine actual display captures support the independent native finish review. Synthetic unavailable/multiple/stale metadata fixtures are explicitly distinguished from actual hardware observations. All33 earlier completed-video SHA256 hashes are unchanged. No physical device or actualAPI29 result is claimed for this step; Phase4.5 acceptance remains open.

## Current Phase 2 verification
See PHASE_2_REPORT.md for exact commands, failures, subsequent fixes and remaining matrix. Final evidence includes 45 JVM tests and 13 distinct physical instrumentation methods on Samsung SM-S9080/API 36, plus manual force-stop/warning checks. Historical Phase 1 stop-on-leave expectations below are superseded by Phase 2 continuation behavior.

45 JVM tests passed in the Phase 2 integration build: 30 core and 15 app. Recovery tests cover attempt rotation, stale callbacks, retained segments, terminal restoration and Finalize/watchdog races. Journal tests cover atomic metadata round-trip, bounded/corrupt data, interrupted recovery, invalid completed references and pending quarantine.

CameraRecordingTest now covers front/rear output, microphone tracks, immediate Stop, recreation retaining the same attempt, Home continuation with notification Stop, navigation continuation, notification opening Camera once, and emulator AppOps camera-loss/manual recovery. Permission fixtures are test-only. The AppOps fixture runs by default only on an isolated emulator; `-e cameraLossTest true` explicitly opts physical hardware in after confirming availability. It temporarily sets only UmbraCam UID CAMERA app-op to ignore, verifies it took effect and restores its exact previous UID mode in finally. Only initial allow/foreground/default modes are accepted. Package-level AppOps do not reliably block runtime-permission sources. ScreenOffRecordingTest is separate opt-in (`-e lockScreenTest true`) because it locks the selected device and its owner must unlock afterward. Decode/track assertions do not certify continuous playback, audibility or long-session A/V sync.

Run builds serially, inspect actual runner output, and preserve existing private media. `adb am instrument` shell exit code alone does not prove test success. Read `OK (n tests)` and any skipped/failure output.

Phase 2 acceptance: explicit visible Start and notification permissions; Home/navigation/recreation retain an accepted attempt; notification opens correct UI and Stop finalizes; source loss exits RECORDING; manual recovery rotates token and retains earlier segments; process termination/relaunch reconciles without automatic capture; earlier completed bytes remain unchanged. Also exercise permission revocation, incoming calls, blocked notifications, full storage and screen off. Report unexecuted matrix entries honestly. No long background/power guarantee is inferred from short tests.

## Historical Phase 0/1 test specification

## Phase 0 automation
The original `core:recording` JUnit tests reject active snapshots missing a required source, saved sessions without finalized segments, failures without errors, empty requests and negative duration. They also verify that interruptions preserve earlier segments. Phase 1's temporal coordinator is covered separately below.
App JUnit tests check the Home destination set and route uniqueness. Compose instrumentation opens Camera Studio and the three feature placeholders, returns Home, opens Settings, invokes system Back and recreates the activity on Reaction Studio to validate the saved back stack.

Run from repository root with JDK 17 and SDK configured:
```powershell
.\gradlew.bat :app:dependencies --configuration debugRuntimeClasspath
.\gradlew.bat :app:assembleDebug :core:recording:test :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:connectedDebugAndroidTest
```
Instrumentation needs an online API 29+ emulator/device (`adb devices`). APK: app/build/outputs/apk/debug/app-debug.apk. Unit XML: module/build/test-results. Lint HTML: app/build/reports/lint-results-debug.html. Instrumentation HTML: app/build/reports/androidTests/connected/debug/index.html.

If no device exists: install Android Studio, open Tools > Device Manager > Create Virtual Device, select a phone profile, download a stable API 36 Google APIs x86_64 image, finish and start it (hardware virtualization must be enabled). Confirm `adb devices` shows it as `device`, then rerun `connectedDebugAndroidTest`. Repeat with API 29 for minimum-version coverage. Alternatively connect a physical API 29+ Android phone, enable Developer options > USB debugging, accept its authorization prompt and verify its state with `adb devices`. These are setup instructions, not executed test evidence.

## UI and configuration checks
On API 29 and a current target-API image: cold launch, every destination, toolbar Back and system Back, activity recreation, background/return. Home must show no permission prompt or media privacy indicator. Camera Studio requests permission only on Enable camera; an already-authorized preview legitimately activates its camera indicator. Recording is never restored automatically. Review screenshots in light/dark mode, portrait/landscape, 1.3x and 2x font scale, narrow phone and tablet/multi-window. Test TalkBack order, labels, focus and 48dp targets; status/error changes should be announced politely without timer spam. All content must remain scrollable. Process restoration is separate from rotation. Static inspection does not replace runtime evidence.

## Phase 1 automated coverage
SingleCameraSessionCoordinatorTest adds 12 unit tests: preparing is not recording, missing audio/source gating, Stop winning over late Start, source loss, overlapping attempts rejected, publication before SAVED, interruption warnings, finalization failure, stale callback IDs, duplicate terminal callbacks, and unsupported dual/screen input rejection. RecordingFileStoreTest adds 7 filesystem tests: inspection before atomic publication, low-space rejection, inspection failure, empty output, quarantine preserving prior completed files, path containment, and zero-duration rejection. These inject fake metadata and test orchestration/filesystem rules, not real MP4 decoding. Together with Phase 0's 8 tests, there are 27 unit tests.

Navigation instrumentation expects Camera Studio and honest previews for the other three sections. CameraPermissionTest checks explanation and disabled Start when camera access is absent; it explicitly skips if the device already has a camera grant. CameraRecordingTest adds six real integration methods: available front/rear silent media, microphone tracks, immediate Stop, recreation, global Home/return, and in-app navigation. It decodes a video frame and inspects MIME tracks; it does not certify continuous playback or audible A/V quality.

There are now **11 instrumentation methods**. All distinct checks passed across the final runs on Samsung SM-S9080 / Android 16 API 36 and a simulated API 29 target; exact runs, harness failures and media profiles are in DEVICE_VALIDATION.md. Navigation also passed at 2× font on API 29. This evidence supersedes earlier reports saying no device was attached; their build-only results remain historical.

Keep the selected physical target unlocked. The test fixture's foreground window uses KEEP_SCREEN_ON but does not unlock the phone or change its global timeout. Test-only runtime grants are used for media capture; actual camera/audio dialog Deny/Allow was checked separately on the emulator. Back waits for its destination before an OS key event; Home uses UiAutomation's global action and explicit launcher relaunch, not ActivityScenario.moveToState as a task foregrounding mechanism. Home asserts prompt exit from RECORDING, a bounded recorded tail, finalized output after return and no automatic restart. No background scheduling guarantee is inferred.

## Physical-device acceptance matrix (partially covered)
Basic FHD front/rear/audio, fast Stop, activity recreation and Home/navigation output preservation are covered on the named Samsung; basic HD simulated checks, dialogs and 2× font navigation are covered on API 29. Other rows/profiles/vendors and destructive/failure/stress scenarios remain pending. Do not fill or clear the user's device as a casual test fixture.
Record model, OS build/API, app version, camera/quality, microphone mode, duration and result for every case:
1. Fresh install/Home: no prompts; Camera Studio/Enable -> allow, deny and permanent denial; settings return correctly refreshes grants. Microphone denial leaves silent recording possible. A device without a microphone/camera must fail gracefully.
2. Front and rear separately: enumerate only supported qualities, preview, start/stop, valid nonempty output with expected orientation/audio. Verify no dual/screen recording path exists. Try an advertised profile that cannot bind and select another quality/retry.
3. Confirm RECORDING appears only after a Start callback and required sources are available. Tap Stop immediately during preparation and just after Start; no delayed callback may restart the stopped attempt. Unusable short output must not be reported saved.
4. Stop, rotate, press Home/Back, navigate away, and lock during preparation and recording. UI immediately leaves RECORDING; finalization can finish; return/rotation requires another Start. Verify controls cannot switch lens/quality/audio or overlap a new attempt during finalization.
5. Toggle OS camera/mic privacy controls, revoke grants and open a competing camera app. Inspect source-loss status and preserved valid output/warnings. There is no background continuation or recovery claim.
6. Fill storage, deny an output operation and terminate the process during capture/finalization. Verify prior `recordings/` files remain intact; pending/quarantine files are never exposed as saved; no active state is restored after process death.
7. Retrieve a finalized debug file with adb/run-as (below), play it in a real decoder and inspect audio/video tracks, timestamps and duration. Metadata validation in the app is not a substitute for this playback check.

For a debuggable install, list outputs with:
```powershell
adb shell run-as com.umbracam.app ls no_backup/camera/recordings
```
To extract on Windows without PowerShell text-redirection corrupting binary output, use a native shell command with the exact UUID filename reported by the app:
```text
cmd /c "adb exec-out run-as com.umbracam.app cat no_backup/camera/recordings/EXACT_UUID.mp4 > recording.mp4"
```
This is an optional developer verification step, not a product export feature. Do not clear app data/uninstall while preserving files. Store test footage privately and delete it only with authorization.

## Future test matrix
- Unit: coordinator transition guards, cancellation races, fake source availability, timestamps and output publication; deterministic coroutine scheduling.
- Instrumentation: permission allow/deny/permanent denial/revocation and service notification stop action. Never automate away consent as a product behavior.
- Physical hardware: representative vendors and API 29/current OS, front/rear lenses, supported quality profiles, missing microphone/camera and concurrent combinations; document model, OS build, codecs, settings, duration and exact results.
- Background: visible user start, Home, screen off, lock/unlock, OEM battery policy, notification denial, force-stop and reboot. No universal background success claim.
- Screen: consent cancellation, app/window selection, projection stop callback, rotation/resizing, protected content and token reuse rejection.
- Interruptions: incoming call, competing camera app, permission revoke, source disconnect, thermal pressure, process termination and stop during recovery. UI must leave RECORDING immediately when a required source is lost.
- A/V: clapper/timestamp fixtures at beginning/end of long recordings; measure skew/drift against a specified threshold, including variable frame rate and input-route changes.
- Storage: nearly full/full disk, failed open/write/finalize/rename, corrupted container, interrupted muxer and crash restart. Verify completed segments independently and never expose partial output as saved.
- Security: key invalidation, ciphertext/metadata tampering, biometric cancel/lockout, temporary plaintext cleanup, export grants, logs, backup and thumbnail leakage.

## Result discipline

**Do not run `connectedDebugAndroidTest` against a daily-use phone installation with recordings that must be retained.** The Android test install path can clear or replace target app data. Use a disposable emulator/device, or first arrange and verify a separate preservation process for every file. Keep physical app data and instrumentation APK installation separate unless the target is explicitly disposable.
Record exact commands, exit codes and reports in BUILD_VERIFICATION.md. Missing tools or devices mean blocked/unexecuted, never pass. Emulator evidence is not physical-device validation. CI should run units/lint/build and wrapper validation; add managed-device checks when an SDK-equipped runner exists.






## Emulator-only follow-up
See [Emulator validation](EMULATOR_VALIDATION.md) for API 29 results, test-only fixture changes, environment failures and limitations. When the user reserves their phone, use explicit `adb -s emulator-5556` commands and verify `ro.kernel.qemu=1`; do not run connectedDebugAndroidTest across all attached targets. The notification-denial fixture uses the real API 29 Show notifications setting and restores it; AppOps-only changes were insufficient. The write-failure fixture restores the pending directory's exact previous mode. No physical device behavior may be inferred from these cases.

## Phase 3 capability checks
JVM tests cover advertised whole groups (including groups larger than two), unknown facing/IDs, duplicate inventory, immutable snapshots, API 29 short-circuit and metadata access/service errors. CameraSupportTest uses explicitly synthetic positive/error/no-group fixtures and one real emulator metadata read. Positive fixtures do not validate actual concurrent capture. NavigationTest checks Settings → Camera support, explicit Check, recreation retention and Back. Run instrumentation only against a named emulator serial while the user’s phone is in use. No dual-camera capture test is part of Phase 3.

2026-10-31 initial Phase 3 verification (superseded by native follow-up below): both debug APKs built; core40 + app21 JVM tests passed (61 total, zero failures/errors/skips), including16 new capability tests. lintDebug passed. No new instrumentation ran because API 29 repeatedly hit its OS boot watchdog and API 36 remained offline across startup attempts. See PHASE_3_REPORT.md for exact command, artifact fingerprint, source-only recapture verdict and pending native checks.

Phase 3 native follow-up: AOSP API 30 emulator passed13 support/navigation methods across phone light1.0,dark1.3,phone light2.0 and sw720dp tablet light2.0 (52 successful functional executions), plus28 native fixture recaptures. Native review:ship at supplied emulator scope. Source permissions stayed denied; synthetic version/no-group/error fixtures do not validate actualAPI29 or physical hardware. See PHASE_3_NATIVE_VALIDATION.md.

## Phase 4 planned validation

[PHASE_4_DESIGN.md](PHASE_4_DESIGN.md) defines feasibility, pure logic, emulator provenance/lifecycle and physical acceptance checks. No Phase 4 capture test ran in this design step. Phase 3 metadata results do not validate dual capture. Both source progress and decoded moving content in both regions must be established. Physical compatibility, sustained capture, thermal and storage behavior remain pending. The user’s phone must remain untouched while in use.

### Phase 4.1 executed experiment

The opt-in emulator-only DualCameraFeasibilityTest records PiP/split and a UID-camera-denial case, verifies both device IDs advance/remain fresh after preview detach, decodes samples and verifies policy restoration. See [PHASE_4_FEASIBILITY.md](PHASE_4_FEASIBILITY.md) for exact commands, failures, corrected passes and limitations. Ordinary-input success does not meet distinct-input provenance; video-file inputs advertised no concurrent pair. Optional marker assertions remain unexecuted beyond pair selection. No physical dual result or production service behavior is claimed. Fixture generation/run instructions are in [scripts/phase4](../scripts/phase4/README.md).

### Phase 4.2 executed domain validation

[PHASE_4_DOMAIN_REPORT.md](PHASE_4_DOMAIN_REPORT.md) records97 passing JVM tests (76 core/21 app), including36 new dual policy/settings tests. Existing single-camera and recovery suites pass unchanged after sharing internal transitions. App compilation and lint pass (0 errors/11 warnings). Clock-driven tests cover missing/stalled inputs despite encoded bytes, deadline boundaries, no revival by late frames, stale tokens, Stop, same-spec recovery, warning preservation and single publication ownership. No runtime capture, emulator or physical validation was performed in this step. Final-frame geometry tests do not establish CameraX rotation/mirror mapping.

### Executed dual input-provenance follow-up

[PHASE_4_PROVENANCE_REPORT.md](PHASE_4_PROVENANCE_REPORT.md) records3 passing native methods on the isolated API30 emulator. The new opt-in DualCameraProvenanceTest checks four layout/order cases,24 decoded probes and separate primary/secondary manual exposure phases verified by actual device metadata. Controlled regions darken/recover while the other remains visible. A failed ambient-motion assumption was replaced by explicit causal input modulation, not a relaxed threshold. Existing source-health and single front/rear methods also passed. Literal file-input markers remain unsupported, but the bounded provenance gap is closed by this alternative. No physical result or production dual service validation is claimed.

## Phase4.3 integration verification

[PHASE_4_INTEGRATION_REPORT.md](PHASE_4_INTEGRATION_REPORT.md) records109 passing JVM tests (76 core/33 app),15 distinct passing native methods (7 dual/8 single) on isolated AOSP API30, and the separate forced-termination/relaunch oracle. DualRecordingIntegrationTest covers real app service/storage publication for both layouts, immutable busy settings, microphone, Home/recreation, notification Stop, source-loss/manual recovery with retained bytes, immediate Stop, unsupported orientation/pair, denied requested audio and reversible publication failure. Denial is run after host-side RECORD_AUDIO revocation before starting the fixture; do not revoke the instrumented process’s runtime permission mid-test.

Each fixture explicitly returns to single mode before selection so an intentional unsupported-pair case does not contaminate later tests. externalTerminationFixture requires a unique terminationRun argument and is intentionally killed by the host; its runner is not counted as a passed JUnit method. Six older completed files retain identical hashes. Use quoted Kotlin property flags and serial builds as recorded in the report after an overlapping invocation damaged the incremental cache. No physical dual result, true disk-fill or all-rotation claim is made.

## First useful beta — final verification

See [FIRST_BETA_REPORT.md](FIRST_BETA_REPORT.md) for exact methods, commands, corrected failures, file inventory and APK SHA256. Version 0.6.0-beta1/code6 passes 159 JVM tests (110 core / 49 app), production assembly and lint (0 errors / 14 warnings). Final API36 ScreenRecordingIntegrationTest+NavigationTest passes 8 methods / 64.107s; final API30 ScreenRecordingIntegrationTest passes 2 methods / 21.859s, including native playback/pause/seek/close. Camera single/microphone/dual-layout/immediate-stop regression passes 4 methods / 36.792s. All 50 earlier camera files retain identical hashes after upgrade; 55 camera files including new fixtures remain. Native export/cancel/delete and byte-identical copy were also observed. Independent native UI review returned ship at the supplied capture scope. No new physical validation; user phone was not accessed. No dependency was added.


## Beta interruption acceptance follow-up

[BETA_ACCEPTANCE.md](BETA_ACCEPTANCE.md) records the next acceptance pass. Production code/APK is unchanged. AndroidTest assembly passed; two opt-in API36 methods passed in17.009s: rotationStopsCapturePreservesFilesAndRequiresFreshConsent and projectionRevocationSavesPartialAndNeverReusesConsent. The latter stops the actual system projection via its public API; it does not certify the handset sharing-chip UI. A separately host-killed externalProcessTerminationFixture was verified by durable journal/storage/projection/service assertions, not counted as JUnit success. Eight completed files retained identical hashes;323903 unfinished bytes were quarantined intact. Fresh consent was observed after each interruption. A System UI emulator ANR required Wait before final visual inspection. Physical acceptance remains pending; no phone was accessed. Prior unit/lint results were not rerun for test/documentation-only changes.

## Deferred Milestone 8 verification
No Milestone 8 checks have been executed. The accumulated suite must cover deterministic tone mappings and intensity limits, Free/Pro gates, Media3 transformation completion/error/cancel, preservation of original encrypted media, encrypted publication and metadata labels, insufficient-space/thermal rejection, cancellation cleanup, and output decoding on supported Android versions. UI checks must confirm release has no purchase action, debug-only Pro messaging is clear, teleprompter text is excluded from recorded output, layout preference restoration, accessibility, and cancellation cleanup. Add device thermal and long-video performance measurements to the Milestone 9/final acceptance batch.
