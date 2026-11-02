# Phase 4.1 — Composition and source-health experiment

Date: 2026-10-31. Status: composition/callback feasibility demonstrated on one emulator; full 4.1 acceptance remains open for distinct-input provenance. Dual recording is not exposed in the product. The user's phone was not used.

## 1. Implementation

Added an opt-in instrumentation-only CameraX 1.5.3 experiment. It selects an exact independently advertised front/rear pair, binds shared Preview/VideoCapture and records PiP, portrait split and a PiP camera-denial case. Each case gets a fresh lifecycle identity and its own test file. No app controller, coordinator, foreground service, journal, permissions UI or release controls changed.

Read-only interop observes advancing frame numbers by `session.device.id`, local monotonic receipt times and device close/error callbacks. Tests require both inputs to advance and remain younger than 1.5 seconds after three seconds with the preview surface detached. Denial applies only to this app's UID CAMERA app-op on the isolated emulator; after three seconds both inputs must be stale. Previous permitted UID policy is restored and read back in `finally`.

The fixture first requires both fresh inputs and positive encoded bytes within a ten-second acquisition deadline after Recorder Start, then observes three seconds with preview and three seconds detached. Each successful file is decoded at 0.5, 2 and 5 seconds, with one AVC video track and no audio track. These are short backend samples, not product recordings or sustained-performance certification. Private test files are separate from completed recordings and are retained.

## 2. Created files

- `app/src/androidTest/java/com/umbracam/app/DualCameraFeasibilityTest.kt`
- `scripts/phase4/create-camera-markers.mjs`
- `scripts/phase4/README.md`
- `docs/PHASE_4_FEASIBILITY.md` (this report)

The Node script generates deterministic red/rear and blue/front AVI inputs with different moving bars, using built-in modules only. Optional marker-mode assertions check expected region colors and temporal change. On the tested API30 image, file-backed cameras remove the advertised concurrent pair, so this mode is rejected before capture; its decoded marker assertions remain unexecuted.

## 3. Modified files

README.md; docs/ARCHITECTURE.md; docs/DEVELOPMENT_PLAN.md; docs/TESTING.md; docs/DECISIONS.md; docs/PHASE_4_DESIGN.md; docs/PHASE_4_REPORT.md. These record the experiment, API constraints and remaining gate. Production application source, build configuration, dependency catalog and version remain unchanged.

## 4. Dependencies added

None. Existing CameraX 1.5.3, Android instrumentation/Compose test libraries, local JDK/Gradle/emulator and Node built-ins were used. No npm packages, encoder library, SDK upgrade or app permission was added.

## 5. Exact validation executed

Build environment: local JDK17, project SDK and cached Gradle8.13. Initial wrapper invocation with a fresh tooling cache stalled at distribution download and was canceled; subsequent builds used the already-installed matching distribution and existing dependency cache:

```powershell
$env:JAVA_HOME=(Resolve-Path '.tools/java/jdk-17.0.20.1+1').Path
$env:ANDROID_HOME=(Resolve-Path .tools/android-sdk).Path
.\.tools/gradle/gradle-8.13/bin/gradle.bat :app:assembleDebugAndroidTest :app:lintDebug --offline --max-workers=2 --console=plain
node scripts/phase4/create-camera-markers.mjs .tools/phase4-markers
node --check scripts/phase4/create-camera-markers.mjs
```

Instrumentation commands, using only the selected emulator (an earlier equivalent TCP serial was `127.0.0.1:5559`, on the same isolated server):

```powershell
adb -P 5038 -s emulator-5558 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -P 5038 -s emulator-5558 shell am instrument -w -r -e class com.umbracam.app.DualCameraFeasibilityTest -e dualFeasibility true com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
adb -P 5038 -s emulator-5558 shell am instrument -w -r -e class 'com.umbracam.app.CameraRecordingTest#availableFrontAndRearRecordSilentDecodableVideos' com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

Also attempted the same feasibility command with `-e provenanceMarkers true` after cold booting with the two generated `-camera-*:videofile:` inputs. It failed pair eligibility (`Camera2={}`, `CameraX=[]`); no concurrency gate was bypassed. One earlier install/runner attempt occurred before system package/activity services were ready and could not execute; readiness was then checked explicitly.

No JVM tests or physical tests were rerun for this test-only backend step. Existing JVM counts are historical. No untargeted connected test task was used.

## 6. Results and findings

Target: `UmbraCam_Phase3_API30_AOSP`, Android11/API30 x86_64, model `Android SDK built for x86_64`, fingerprint `Android/sdk_phone_x86_64/generic_x86_64:11/RSR1.210722.013.A2/10067904:userdebug/test-keys`. Four cores, 3072MiB, 720×1280/320dpi, SwiftShader, Vulkan off, headless/no audio/no snapshots. Ordinary emulated cameras advertise `{0,1}` in both Camera2 and CameraX: rear0, front1. This is simulated input, not physical hardware.

Build and lint passed; lint reports zero errors and 22 existing warnings. The corrected ordinary-input experiment and existing single-camera front/rear regression each produced `OK (1 test)`. Final-source recheck logs are retained with the evidence below.

Final verification: `.tools/phase4-acquisition-build.log` passed in15s; `.tools/phase4-acquisition-final.txt` reports `OK (1 test)` in31.197s with the readiness check; `.tools/phase4-single-normal-final.txt` reports `OK (1 test)` in10.254s. Final experiment evidence is under timestamp `1793468306219`. The nine updated/new Markdown documents passed a relative-link check:26 links, zero missing. CAMERA fixture permission was restored to denied, RECORD_AUDIO remained denied, and the task-owned emulator was shut down with userdata/evidence retained. No phone actions were taken.

Cleanup note: the emulator accepted its console shutdown command but then reported hung QEMU CPU threads. After exporting evidence and restoring permissions, the exact task-owned AVD/port/process identity was verified and its remaining VM/launcher processes were terminated. No userdata wipe or media deletion was performed; next use should cold boot and reconcile normally.

One corrected run (`1793467770865`) produced:

| Case | Duration | Bytes | Both advance/fresh after preview detach | Output |
|---|---:|---:|---|---|
| PiP | 5951ms | 490919 | Yes | Decoded 720×1280, rotation0, AVC only |
| Portrait split | 5892ms | 506829 | Yes | Decoded 720×1280, rotation0, AVC only |
| PiP + UID camera denial | 6179ms | 396040 | Yes before denial; both stale and closed afterward | Decodable prefix; Finalize error0 |

Manual inspection of decoded PNGs confirmed a PiP inset and, after the lifecycle fix, separate top/bottom split regions. Similar animated patterns in the two ordinary fake feeds do not meet the stronger distinct-moving-marker provenance gate. The file-input attempt retains two cameras but advertises no concurrent group; its HAL uses Qemu cameras rather than the two fake cameras. Do not extrapolate that negative fixture result to physical support or infer support from having two IDs.

Important pinned-API findings:

- Exact-ID filters must also carry the appropriate `requireLensFacing`. ID-only selectors leave both facings null, entering CameraX's same-facing path and yielding a decodable single-camera video. Initial source-health assertions correctly failed that run.
- Composition returns one public camera wrapper around both devices. A second early assertion expecting two wrappers was incorrect and was removed after inspecting the pinned implementation. Exact selector resolution, concurrent mode and both device callback IDs are checked instead.
- `unbindAll()` with the same lifecycle owner/pair retained earlier composition settings in this experiment. A fresh lifecycle identity for each immutable layout produced the expected split. Production integration must scope binding ownership accordingly.
- Interop callbacks propagate to both composed inputs, including with the preview detached, on this target. This demonstrates a viable source-health mechanism; it does not guarantee delivery on every OEM.
- Camera denial can close both inputs while Recorder finalizes with error0. Product source-loss warnings must be carried by the coordinator/journal, independently of container finalization.
- The API30 shell omitted an explicit UID line for default CAMERA allow. Sending the CLI mode `default` after denial left `ignore` set. This contaminated one subsequent capture (`ERROR_NO_VALID_DATA`) and one single-camera regression (preview timeout). The fixture now restores explicit prior permitted policy and asserts the read-back. Both tests passed after restoration. These failed runs remain in evidence and are not counted as passes.
- After a cold boot, Recorder Start preceded both input callbacks long enough that the original fixed three-second lead-in took an empty baseline. That run correctly failed the advance-after-detach assertion. The fixture now requires both fresh source signals and positive encoded bytes within a ten-second acquisition window before observing continuity. Recorder Start alone is not a readiness signal.

These findings were checked against exact pinned [camera-lifecycle sources](https://dl.google.com/dl/android/maven2/androidx/camera/camera-lifecycle/1.5.3/camera-lifecycle-1.5.3-sources.jar) and [camera-core sources](https://dl.google.com/dl/android/maven2/androidx/camera/camera-core/1.5.3/camera-core-1.5.3-sources.jar), including LifecycleCameraProviderImpl and StreamSharing, rather than newer API signatures.

Evidence is local and ignored: `.tools/phase4-*-build.log`, `.tools/phase4-feasibility-*.txt`, `.tools/phase4-single-regression*.txt`, `.tools/phase4-markers-*.txt`, `.tools/phase4-evidence/files/phase4-feasibility/`. JSON/MP4/decoded PNGs are also retained app-private. No existing completed library existed on this Phase3 AVD before the experiment; the later single-camera regression creates its own normal outputs. No prior recordings were deleted. The production APK SHA256 remains `693834bace44bf796d73f07284d598c13b2b762a567e8336eced546b026621a2`, version0.4.0.

## 7. Known limitations

Full task4.1 exit criteria are not yet met: distinct independently moving input provenance remains open. Optional marker color/motion assertions have not executed beyond eligibility. Swapped lens order, all rotations/mirroring/cropping, audio, single-input-only loss, production service/notification/recreation, process death, journals and sustained thermal/storage behavior are untested for dual mode. Requested HD SDR30 is not a measured throughput/profile guarantee. Callback freshness is a proposed timeout, not hard real-time protection. Real-device acceptance still requires explicitly available compatible hardware.

## 8. Recommended next task

Implement the isolated Phase4.2 dual specification and source-health policy with pure tests: two required inputs, acquisition/freshness boundaries, stale generations, Stop winning, loss warnings and explicit same-spec recovery, while preserving the single-camera facade. Keep production dual Start unavailable. Close distinct-input provenance on a supported fixture before claiming the complete4.1 gate or integrating release controls; do not add a custom encoder or bypass advertised-pair selection to force this emulator fixture through.

## Subsequent provenance verification

The open provenance item in this historical experiment is now closed for the bounded emulator configuration by [PHASE_4_PROVENANCE_REPORT.md](PHASE_4_PROVENANCE_REPORT.md). Separate primary/secondary exposure markers are visible in decoded output, without replacing inputs or bypassing advertised support. Full product integration and physical acceptance remain open.
