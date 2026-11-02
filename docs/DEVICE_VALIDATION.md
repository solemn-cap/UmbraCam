# Current Phase 2 evidence

See [Phase 2 report](PHASE_2_REPORT.md): eight camera methods, four navigation methods and one short screen-off method passed on Samsung SM-S9080/API36 across final runs, plus manual force-stop/durable-warning checks. Latest API29 service checks and the broader release matrix remain open. Historical Phase 1 passes below used stop-on-leave behavior and do not prove Phase 2 acceptance.

# Device validation — 2026-10-30

## Outcome
**Eleven distinct instrumentation checks passed on each of two targets**, across the runs described below. Basic single-camera capture and lifecycle behavior now have named physical-device evidence. This does not certify every device, quality profile, interruption or full audio/video playback case.

| Target | Configuration | Evidence scope |
|---|---|---|
| Physical Samsung SM-S9080 | Android 16 / API 36; build BP2A.250605.031.A3.S9080ZHSBGZH3; security patch 2026-08-05; system night configuration | Real front/rear camera, microphone/audio tracks, decoded video frame, source stopping/finalization, navigation and recreation |
| Android Emulator | Stable emulator 37.2.13, Google APIs x86_64 API 29 image revision 13, Pixel 2 profile, 1080×1920/420dpi, WHPX, SwiftShader | Minimum-API runtime coverage, simulated camera/audio, permission dialogs, default/2× font navigation and light screenshots |

App under test: **0.2.0/code 2**, `com.umbracam.app`, min 29 / target 36. Production APK SHA-256 remains `159d050b6db62fdbeef328cc2e6ff0bba914c679fbfe68609f135850cdf38020`. No production source or app dependencies changed in this follow-up.

## Checks and results
| Check | Samsung API 36 | Emulator API 29 |
|---|---|---|
| Missing camera grant: explanation and disabled Start | Passed before fixture grants | Passed before fixture grants |
| All four Home sections and toolbar Back | Passed | Passed |
| Destination retained across activity recreation | Passed | Passed |
| Settings copy | Passed | Passed |
| System Back returns Settings -> Home | Passed after correcting test synchronization/OS input | Passed |
| Available front/rear silent recordings, nonempty saved file, decoded frame, no audio track | Passed for REAR and FRONT | Passed for REAR and FRONT |
| Microphone recording: decoded video frame and audio/video tracks | Passed | Passed with simulated input |
| Immediate Stop during preparation never becomes active/saved | Passed | Passed |
| System Home stops active state; explicit return does not restart recording; output preserved | Passed after fixture correction/unlock | Passed with Android global Home action |
| In-app navigation Home stops and preserves output | Passed | Passed |
| Recreation during recording stops and preserves output without restart | Passed | Passed |

There are four NavigationTest methods, one CameraPermissionTest method and six CameraRecordingTest methods. The front/rear method covers both reported lenses. Pass totals above count distinct checks, not duplicate executions during diagnosis. There were initial failing harness runs; they are retained in the logs and are not represented as passes.

## Recorded media evidence
Test-only `UmbraCamValidation` logs identify lens/profile, duration, frame size, MIME tracks and warning code without logging private paths/content.

| Target/case | Profile | Duration (ms) | Decoded frame | Tracks / warning |
|---|---|---:|---|---|
| Samsung rear silent | FHD | 1919 | 1080×1920 | video/avc; no warning |
| Samsung front silent | FHD | 1966 | 1080×1920 | video/avc; no warning |
| Samsung microphone | FHD | 2089 | 1080×1920 | video/avc + audio/mp4a-latm; no warning |
| Samsung in-app Home | FHD | 1658 | 1080×1920 | video/avc; SOURCE_UNAVAILABLE stop warning |
| Samsung recreation | FHD | 1625 | 1080×1920 | video/avc; SOURCE_UNAVAILABLE stop warning |
| Samsung system Home, final retry | FHD | 1671 | 1080×1920 | video/avc; SOURCE_UNAVAILABLE stop warning |
| Emulator rear silent | HD | 2229 | 720×1280 | video/avc; no warning |
| Emulator front silent | HD | 2019 | 720×1280 | video/avc; no warning |
| Emulator microphone | HD | 2255 | 720×1280 | video/avc + audio/3gpp; simulated input |
| Emulator in-app Home | HD | 1860 | 720×1280 | video/avc; SOURCE_UNAVAILABLE stop warning |
| Emulator recreation | HD | 1718 | 720×1280 | video/avc; SOURCE_UNAVAILABLE stop warning |
| Emulator system Home, final retry | HD | 1910 | 720×1280 | video/avc; SOURCE_UNAVAILABLE stop warning |

FHD/HD are the default supported choices actually tested; this does not certify every advertised quality. Portrait decoded dimensions include output orientation. Home tests also require leaving RECORDING within five seconds, verify no automatic restart on return, retain the same ViewModel and reject a duration exceeding the last foreground statistic by more than three seconds. These are test bounds, not a product latency guarantee.

Physical clips remained app-private on the phone; none were copied off it. Existing recordings were not deleted, and app data was not cleared. Test clips remain available in the app-private store. Emulator cameras were `emulated` and host audio was disabled with `-no-audio`; no host webcam/microphone was used.

## Permission and visual checks
On the emulator, after the fixture-granted suite, CAMERA was revoked while the app was stopped. The app was opened normally and UI actions were selected from actual UIAutomator bounds:
- Home showed no permission prompt. Enable camera displayed Android's actual camera dialog.
- Deny returned to the explanation and a disabled Start button. A retry displayed the system dialog again; Allow opened camera choices/preview.
- RECORD_AUDIO was then revoked with the app stopped. Camera access remained granted; Start stayed enabled for silent video.
- Enabling microphone displayed Android's actual audio dialog. Deny showed the silent-video explanation and left Start enabled. Retrying Allow checked the microphone switch; status remained Not recording.

NavigationTest passed again at **font scale 2.0** on API 29 (four methods). Home screenshots at default/2× font were visually inspected: text wrapped and content remained scrollable; no Home clipping defect was found at that viewport. This is not a complete large-font/TalkBack audit of every recording state.

Local screenshot artifacts (ignored by Git):
- `captures/device-validation/api29-home-light.png`
- `captures/device-validation/api29-home-font2.png`
- `captures/device-validation/api29-camera-permission.png`
- `captures/device-validation/api29-camera-denied.png`
- `captures/device-validation/api29-microphone-denied.png`

The API 29 image reports `mNightModeLocked=true`; a global night-mode request did not change its light configuration. The attempted screenshot was renamed `api29-night-switch-refused.png` and is **not** dark-mode evidence. Samsung's functional runs used its existing `night` configuration, but no physical dark screenshot or TalkBack speech check was captured. Emulator font scale was restored to effective default 1.0. User phone font/night/timeout settings were not changed.

## Harness failures and fixes
1. Initial physical baseline: 4/5 passed; the Back test directly called the dispatcher before the destination's composed handler was settled, closing the activity. It now asserts that Settings is visible and injects an Android Back key. All four NavigationTest methods subsequently passed on both targets; no app navigation code changed.
2. ActivityScenario.moveToState(RESUMED) did not foreground a Home-backgrounded task on the phone. Test relaunch now uses an explicit launcher intent through test UiAutomation and verifies retained ownership/no restart.
3. Injected Home key input was not reliable on the API 29 emulator. The final test uses Android's global accessibility Home action, checks prompt state exit and validates completion after explicitly returning. Background finalization scheduling is not guaranteed in this foreground-only milestone.
4. A phone retry had no Compose hierarchy because the physical phone had automatically locked (`Dozing`, keyguard showing). User unlocked it; the final Home test passed in 4.824s. The camera test fixture now keeps its foreground test window awake; this neither unlocks the phone nor changes production behavior.

## Exact build and execution commands
JDK/SDK environment is the same ignored local `.tools` setup documented in README/earlier reports. Test APK was rebuilt after each fixture edit using:
```powershell
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:assembleDebugAndroidTest :app:lintDebug
```
The last build/lint command passed in 7s. Lint remains 0 errors / 18 pre-existing advisories. Production Kotlin code did not change; the 27 already-passing JVM tests were not redundantly rerun in this validation-only follow-up.

Let `$serial` be the selected ADB target (actual phone serial omitted here; emulator was `emulator-5556`):
```powershell
adb -s $serial install -r app/build/outputs/apk/debug/app-debug.apk
adb -s $serial install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s $serial shell am instrument -w -r -e class 'com.umbracam.app.CameraPermissionTest,com.umbracam.app.NavigationTest' com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s $serial shell am instrument -w -r -e class com.umbracam.app.CameraRecordingTest com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s $serial shell am instrument -w -r -e class 'com.umbracam.app.CameraRecordingTest#homeStopsRecordingAndResumeRequiresAnotherStart' com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```
Back correction was rechecked using `-e class com.umbracam.app.NavigationTest`; 2× font used that same class on the emulator. Home was rerun alone after its fixture fixes. ADB's process exit alone is not test success: results were read from `OK (...)` / failure entries in runner output.

Final evidence logs retained under ignored `.tools/`:
- Physical: `physical36-navigation.log` (initial 4/5), `physical36-navigation-retry.log` (4/4), `physical36-capture.log` (initial 4/4), `physical36-capture-final.log` (5/6 before Home fixture correction), `physical36-home-unlocked.log` (1/1 final Home), `physical36-capture-evidence.log`.
- Emulator: `emulator29-navigation.log` (5/5), `emulator29-capture.log` (5/6 before global Home correction), `emulator29-home-final.log` (1/1 final Home), `emulator29-large-font-navigation.log` (4/4), `emulator29-capture-evidence.log`.
- Build/lint: `device-validation-checked.log`; additional setup/diagnostic/retry logs remain alongside them. Permission dialog/UI XML evidence is in `emulator29-*-ui.xml` / `*-dialog.xml`.

## Changes delivered
Created source/docs: `app/src/androidTest/java/com/umbracam/app/CameraRecordingTest.kt` (six integration methods) and `docs/DEVICE_VALIDATION.md`.

Modified: `app/src/androidTest/java/com/umbracam/app/NavigationTest.kt`, `README.md`, `PRODUCT.md`, `DESIGN.md`, `docs/PHASE_1_REPORT.md`, `docs/BUILD_VERIFICATION.md`, `docs/PRODUCT_SPEC.md`, `docs/DEVELOPMENT_PLAN.md`, `docs/TESTING.md`.

No app dependencies were added. Local tooling: stable emulator and API 29 AVD; SDK archives were verified against Google's repository checksums after sdkmanager downloads failed. No Windows virtualization/security feature was changed. All reported emulator results use stable 37.2.13; an initial preview bootstrap was replaced before validation. An API 36 image download was stopped once the physical API 36 phone was available; no API 36 emulator result is claimed.

## Remaining limitations and next task
Still pending: real incoming-call/competing-camera/privacy-switch and permission-revocation matrix; physical portrait/landscape transitions; low/full storage and process-kill recovery; all quality profiles; long-duration/thermal/battery behavior; continuous playback, audible audio quality and A/V drift; full TalkBack/dark/large-font screen coverage and broader vendors. Decoding one frame plus track checks is useful evidence, not certification of complete playback or synchronization.

Recommended next milestone: Phase 2 recording lifecycle/interruption handling, using the outstanding source-loss, foreground-service and process-termination cases as explicit acceptance criteria. Do not infer background continuation or automatic recovery from these foreground stop tests. Broader performance, synchronization and security checks remain in their planned milestones. No Phase 2 implementation was started in this validation task.



## API29 emulator follow-up (2026-10-30/31)
Sixteen distinct instrumentation methods passed across documented runs, plus force-stop, runtime camera revocation and simulated call/69.5-second capture scenarios. Two new emulator-only fixtures cover real notification blocking and OS filesystem write denial. All16 prior completed clip hashes survived each external scenario;18 completed synthetic clips were retained at shutdown. This pass did not use a phone. See [Emulator validation](EMULATOR_VALIDATION.md) for individual failures/fixes and limits.

