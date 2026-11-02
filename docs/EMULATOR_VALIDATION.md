# Phase 2 emulator-only validation

Runs: 2026-10-30, continued 2026-10-31. The user reserved their phone for personal use. All device actions in this pass explicitly targeted `emulator-5556`; no phone tests or all-device Gradle tasks were run.

## 1. Implementation
Test-only work extends CameraRecordingTest with two emulator fixtures: Android's actual global notification switch must prevent Start, and an unwritable pending directory must fail honestly while preserving completed files. Both restore their changed setting/permissions in finally. The notification UI fixture is limited to API29; the filesystem fixture is limited to emulators. No production application behavior changed.

## 2. Created files
- docs/EMULATOR_VALIDATION.md
- Ignored local `.tools/phase2-emulator-helpers.ps1` and test result/log files. The helper hardcodes the emulator serial and verifies `ro.kernel.qemu=1` before any action. It never falls back to a physical device.

## 3. Modified files
app/src/androidTest/java/com/umbracam/app/CameraRecordingTest.kt, README.md, docs/TESTING.md, docs/BUILD_VERIFICATION.md, docs/PHASE_2_REPORT.md, docs/DEVICE_VALIDATION.md and docs/DEVELOPMENT_PLAN.md. Local AVD configuration changed to 720x1280/density320, two cores, 2 GiB RAM and two synthetic cameras; its original config was backed up under .tools. Existing app data and recordings were retained.

## 4. Dependencies added
None. Existing Android test APIs and SDK tools suffice. Production APK remains version 0.3.0 (code 3), SHA256 `FFD5AB9E9564621285D5A57BA696EBCA468D0D3C56EDE1678C59825D3CEFFB7A`.

## 5. Exact checks and results
Target: Android 10 / API 29, Google APIs x86_64, fingerprint `google/sdk_gphone_x86_64/generic_x86_64:10/QSR1.211112.011/13135432:userdebug/dev-keys`, security patch 2019-09-05. Emulator 37.2.13, WHPX, SwiftShader, headless, synthetic front/rear cameras, host audio disabled. App and tests ran under the normal application UID.

Final fixture build:
```powershell
.\gradlew.bat --offline --no-watch-fs --console=plain --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' :app:assembleDebugAndroidTest :app:lintDebug
```
Passed in 50 s; 59 tasks (7 executed/52 up-to-date); `.tools/phase2-notification-settings-test-build.log`. Production/core logic was unchanged, so the prior 45 passing JVM tests were not represented as freshly executed in this pass.

All instrumentation used `com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner`:

| Selection passed with `adb -s emulator-5556 shell am instrument -w -r -e class ...` | Result | Evidence |
|---|---|---|
| CameraPermissionTest,NavigationTest | OK (5 tests), 37.68 s; no skips | .tools/phase2-api29-baseline-final.log |
| CameraRecordingTest | 9 passes/1 notification-fixture failure, 82.382 s | .tools/phase2-api29-camera-healthy.log |
| CameraRecordingTest#blockedNotificationsPreventRecording, after correcting the fixture | OK (1 test), 8.641 s | .tools/phase2-api29-notification-settings.log |
| ScreenOffRecordingTest, `-e lockScreenTest true` | OK (1 test), 33.085 s | .tools/phase2-api29-screen-off.log |

The nine camera-class passes cover microphone/video tracks, immediate Stop, actual UID camera loss/manual recovery retaining segments, navigation continuation, one-shot notification opening, front/rear decoded video, recreation continuation, Home continuation/notification Stop, and an OS-level unwritable staging directory. The failed notification fixture changed AppOps, which did not update API29's notification preference. Its replacement toggles Settings' Show notifications switch, confirms Start is refused without beginning a session/service, and restores the switch. There is no claim that the original ten-method run was an aggregate pass.

Three additional external emulator scenarios passed:

| Scenario | Observed outcome | Evidence |
|---|---|---|
| Force-stop during confirmed Recording, then explicit launch | Terminal PREVIOUS_SESSION_INTERRUPTED; no restarted service; pending empty; all 16 earlier completed-file SHA256s unchanged | .tools/phase2-api29-force-stop-result.json |
| Revoke the actual CAMERA runtime grant during Recording | Android terminated the process; relaunch explained the missing permission and restored a terminal interruption; no capture restart; all 16 earlier hashes unchanged; original grant restored | .tools/phase2-api29-revoke-result.json |
| Synthetic incoming/active call during microphone recording | Same attempt continued, finalized Saved with 69,540 ms duration and no error; all 16 earlier hashes unchanged; synthetic call canceled | .tools/phase2-api29-call-result.json |

The call scenario used `adb -s emulator-5556 emu gsm call 5550100`, `gsm accept`, `gsm list` and `gsm cancel`. No real call was placed. The emulator supports modem state simulation but not call audio, so this is not a physical call/audio-policy certification. See [official emulator console documentation](https://developer.android.com/studio/run/emulator-console).

## 6. Aggregate results and environment recovery

All 16 distinct instrumentation methods passed across the documented runs: permission explanation 1, navigation 4, original camera/lifecycle 8, notification denial 1, filesystem write denial 1, and screen off 1. The three external scenarios passed separately; they are not counted as instrumentation methods. Tests stopped capture, restored their app settings/permissions, and retained 18 completed synthetic recordings on the AVD. Final UmbraCam service list was empty and the pending directory mode was 700. The emulator was shut down after the final check, preserving its data.

The first camera attempt passed six methods before NetworkWatchlistService threw an OutOfMemoryError and killed the Android system process. That run was aborted, not passed. A later boot exposed zero camera devices, so readiness timeouts from that environment are not counted as app capture failures or passes.

To isolate unrelated background services, emulator Wi-Fi/mobile data were disabled and `network_watchlist_enabled=0` was applied. UmbraCam declares no network permission. The zero-camera boot was consistent with a provider/property initialization race: `qemu.sf.fake_camera=both` was present after boot, and restarting the emulator camera provider restored two devices. The bounded property wait in [Goldfish camera initialization](https://android.googlesource.com/device/generic/goldfish/+/310f9b1/camera/EmulatedCameraFactory.cpp) supports that inference; the timeout itself was not captured. ADB was briefly root only to restart that emulator HAL, then immediately returned to non-root before tests. No app privilege, permission manifest, privacy indicator or consent behavior was bypassed.

Host builds and emulator runs were separated to avoid memory pressure. No AVD wipe, app-data clear or completed-media deletion was used. Local emulator-only network settings are test-environment changes, not product requirements or production workarounds.

The resumed cold boot on 2026-10-31 was unusually slow. ADB connected before Android framework services were ready; a premature emulator data-service command failed in the SDK utility, not UmbraCam. The final test waited for boot completion, settings and two camera devices, then passed. Unrelated Google service ANR history was still present in the old image; it is not reported as an UmbraCam failure or platform-health certification.

## 7. Known limitations
- Synthetic camera/audio and modem-state results do not certify real incoming-call audio behavior, OEM camera contention, battery/thermal behavior or long Doze sessions.
- A 69.5-second capture is a limited duration check, not an hours-long endurance or synchronization test. The app structurally validates output; camera instrumentation decodes a video frame and checks track metadata, not continuous audibility/playback.
- Storage testing here injects a genuine filesystem write denial. It does not fill the host/emulator disk or certify muxer behavior when storage becomes full during a long recording. Existing JVM low-space checks remain separate evidence.
- This is an old API29 system image with documented environment stabilization. Other API levels/vendors, orientations and accessibility configurations remain separate matrix entries.

## 8. Recommended next task
Keep the remaining physical call/audio, OEM/power and active-recording disk-full matrix for a later authorized hardware session. Do not treat emulator evidence as fresh phone validation or begin another feature milestone as part of this testing pass.


