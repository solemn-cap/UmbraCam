# Phase 4 acceptance tools and historical experiments

Current acceptance protocol: [Phase4.5 matrix and results](../../docs/PHASE_4_ACCEPTANCE.md). Product controls are implemented; experiments below are historical and their explicitly superseded limitations do not reopen closed gates.

## Read-only device snapshot

`collect-device-evidence.ps1` requires PowerShell7+, an explicit adb path, serial and fresh output directory. Relative output paths resolve from the current PowerShell location. It collects identity/build, only UmbraCam's package state, battery/thermal snapshots and file hashes; it never opens cameras, installs, grants permissions or changes device settings. Example for the isolated emulator:

```powershell
./scripts/phase4/collect-device-evidence.ps1 -AdbPath .tools/android-sdk/platform-tools/adb.exe -Serial emulator-5558 -ServerPort 5038 -OutputDirectory .tools/phase4-acceptance-evidence/identity-before
```

## Sustained emulator acceptance observations

Build the test APK serially, then run without another host build. `DualRecordingAcceptanceTest` requires `dualAcceptance=true`, a fresh `acceptanceRun` UUID and30–300seconds (default300). It refuses physical hardware. Both layouts use the real app/service, microphone and notification Stop after Home halfway through; the fixture checks continuous recording state, saved output/probes/tracks and unchanged prior files, then writes per-layout JSON to app-private `files/phase4-acceptance/`.

```powershell
$run=[guid]::NewGuid().ToString()
adb -P 5038 -s emulator-5558 shell am instrument -w -r -e dualAcceptance true -e acceptanceRun $run -e acceptanceSeconds 300 -e class com.umbracam.app.DualRecordingAcceptanceTest com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

Read runner results and both JSON artifacts. Cadence/PTS metrics describe encoded output; they do not prove physical compatibility, independent content, audibility, sync or thermal behavior. Never clear app data or delete prior recordings. Restore fixture grants and shut down only the task-owned emulator afterward.

## Historical Phase4.1 camera experiment

This test-only experiment does not enable product dual recording. Use an isolated API 30+ emulator with an advertised front/rear pair. It refuses physical hardware and requires `dualFeasibility=true`. Keep the user's phone untouched. Never clear app data or use untargeted connected tests.

Generate deterministic moving video inputs with the existing Node runtime; no npm packages are needed:

```powershell
node scripts/phase4/create-camera-markers.mjs .tools/phase4-markers
```

The rear source has a red background and right-side green identity block; front has blue and a left-side block. White bars move at different speeds/directions. Files are 160×90, BGR24 AVI, 30fps, 120 frames. These are source markers, not a claim about negotiated recording performance.

**Observed limitation:** On UmbraCam_Phase3_API30_AOSP, replacing both fake cameras with these file inputs changes the HAL metadata to no advertised concurrent group. Marker mode therefore fails at pair eligibility before capture. Do not bypass that gate. Distinct-input provenance remains unverified; the passing ordinary-input run establishes composition/callback feasibility only. The marker color/motion assertions have not yet been reached on a supported pair.

Cold-start the task-owned AVD with its normal arguments plus these overrides (absolute paths required):

```text
-camera-back videofile:C:/Projects/umbracam/.tools/phase4-markers/rear-red.avi
-camera-front videofile:C:/Projects/umbracam/.tools/phase4-markers/front-blue.avi
```

Build `:app:assembleDebugAndroidTest :app:lintDebug`. Confirm `sys.boot_completed=1`, package/window services and an unlocked foreground window before installing the test APK. Use explicit `adb -P 5038 -s emulator-5558` for this isolated server/AVD; if using a different serial, verify `ro.kernel.qemu=1` first.

```powershell
adb -P 5038 -s emulator-5558 install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -P 5038 -s emulator-5558 shell am instrument -w -r -e class com.umbracam.app.DualCameraFeasibilityTest -e dualFeasibility true -e provenanceMarkers true com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

Inspect runner text, not just shell exit status. With ordinary emulated camera inputs, omit `provenanceMarkers`; that run does not prove distinct visual provenance. Marker mode requires dominant expected colors in both decoded regions and temporal change in each. Both modes require advancing/fresh camera-ID callbacks after preview detachment. The final case denies CAMERA for this app UID, checks staleness and restores/verifies the previous permitted UID mode. This is a backend observation, not a test of a product dual-session coordinator or foreground service.

Evidence is retained under app-private `files/phase4-feasibility/<timestamp>/`: MP4s, decoded PNGs and JSON. Export with `adb exec-out run-as com.umbracam.app tar -c -f - files/phase4-feasibility`, preserving binary stdout. Test files never enter `no_backup/camera/recordings` and existing media are not deleted. After testing, restore emulator permission fixtures and shut down the task-owned emulator. This experiment does not validate physical devices, audio, all rotations or sustained thermal performance.

## Validated alternative: causal per-input exposure markers

The tested API30 image supports manual sensor controls while retaining its advertised concurrent pair. The opt-in test below modulates each input separately, verifies per-device AE/exposure results and decoded-region darkening/recovery, and repeats both layouts and primary orders. A preliminary Preview without a surface resolves the secondary public CameraX control; it is fully unbound before composition. This is a pinned-backend test fixture, not a production control design. No extra live stream or metadata bypass is used.

```powershell
adb -P 5038 -s emulator-5558 shell am instrument -w -r -e class com.umbracam.app.DualCameraProvenanceTest -e dualProvenance true com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

Actual success and limits are in [the provenance report](../../docs/PHASE_4_PROVENANCE_REPORT.md). This supersedes the current provenance blocker from the file-input attempt; the file-input marker assertions themselves remain unexecuted. Evidence is retained separately under app-private files/phase4-provenance and exported .tools/phase4-provenance-evidence.
