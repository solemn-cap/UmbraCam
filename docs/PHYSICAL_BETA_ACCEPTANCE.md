# Samsung beta acceptance — 2026-11-02

## Physical UI follow-up — 2026-11-05

Targeted only ADB serial `R5CT718YA8M` (Samsung SM-S9080, Android 16/API36). ADB initially required a server restart, then reported this device as `device`. Before installation, `com.umbracam.app` and both `/data/user/0/com.umbracam.app` and `/data/data/com.umbracam.app` were absent. Installed the debug APK and its AndroidTest APK with `adb install -r`; no prior UmbraCam sandbox was present to replace. No capture or audio test was run.

Exact instrumentation commands, both via `adb -s R5CT718YA8M shell am instrument -w -r` and runner `com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner`:

- `-e class com.umbracam.app.NavigationTest#proDiscoveryShowsHonestAccessAndNoCheckout`: **1 passed**, 2.474s.
- `-e class com.umbracam.app.NavigationTest`: **7 passed**, 0 failed, 13.836s. Covered Pro discovery, SOS explanation, settings, navigation/back, activity recreation and the support screen.

ThermalService was already status 2 before testing (AP 45.5°C, battery 39.9°C, skin 40.1°C, USB 39.0°C). After the first test it remained status 2 (AP 46.5°C, battery 40.4°C, skin 39.7°C). After the seven-test navigation run it reached status 3 (AP 61.1°C, battery 41.0°C, skin 42.7°C, USB 40.9°C). After a 30-second idle interval it was still status 3, with AP 48.1°C, battery 41.5°C, skin 41.3°C and USB 41.4°C. Testing stopped at that point. The final foreground was WhatsApp; no WhatsApp content was inspected or changed. `dumpsys activity services` showed no UmbraCam service. No settings were changed, no camera/microphone capture started, and no claim is made about completed-media preservation because no file hash inventory was taken. Camera, microphone, interruptions, export, storage, encryption and sustained/thermal acceptance remain pending.

## Follow-up physical attempt — 2026-11-02

The user made the phone available for further acceptance. Connected explicitly to `R5CT718YA8M` (Samsung SM-S9080, Android 16/API36); no other device was targeted. The app data was not cleared. At the start, Android ThermalService reported status 1, battery 36.6°C, skin 36.6°C, and AP 40.4°C. The app `no_backup` tree contained 116 files. A swipe dismissed the lock screen without a credential; no settings or timeout values were changed.

The first runner command used an unsupported `+`-joined method filter and Gradle reported `BUILD SUCCESSFUL` with **0 tests**. This is not test evidence. The corrected command selected `ScreenRecordingIntegrationTest#cancellationNeverCreatesOutputAndNextStartRequestsFreshConsent` and executed one test on `SM-S9080 - 16`; it failed in 3.544 seconds before consent or capture. `enter()` could not find a Compose hierarchy while the notification shade was focused. XML: `app/build/outputs/androidTest-results/connected/debug/TEST-SM-S9080 - 16-_app-.xml`. No recording was started or deleted. The test APK compiled; production sources/APK were unchanged.

After the failed attempt, ThermalService still reported status 1, with battery 37.1°C, skin 39.2°C, and AP 57.1°C. The device had returned to the notification shade/lock screen, so physical capture attempts were stopped. This does not establish a thermal-stop defect; the test never reached app UI or capture. The remainder of the requested physical batch (screen cancellation/rotation/projection revocation, combined layouts/camera loss, OS sharing control, and source-preservation checks) remains pending. Existing completed files were not intentionally modified; no before/after content hash comparison was completed during this attempt. Host and emulator evidence elsewhere in this report remains unchanged.

### Test-install data loss discovered during investigation

During follow-up diagnostics, Android event logs showed `installer_clear_app_data_caller` for `com.umbracam.app` at 20:03:56 UTC, during the `connectedDebugAndroidTest` install path. A subsequent package query found UmbraCam absent, and both `/data/user/0/com.umbracam.app` and `/data/data/com.umbracam.app` were absent. The prior app sandbox had contained 116 files, including private completed recordings. This strongly indicates the instrumentation install cleared/replaced the app installation and its private files; no backup of the `no_backup` directory was made, so those files may be lost. No more phone-side writes or installs were performed. This is an incident, not a passing result. The exact mechanism that removed the package after test execution is not fully established. Future physical instrumentation must use a disposable target; never run Gradle connected instrumentation against a daily-use installation with files to preserve.

After this incident, the user confirmed the recordings were not useful and authorized discarding them for now. This removes the preservation constraint for a future test install, but does not change the recorded incident or complete any physical acceptance checks. Per the user's latest sequencing direction, physical and other acceptance tests are deferred until the premium feature implementation batch is complete.

Exact commands: initial invocation selected methods with a `+` separator and executed zero tests; corrected invocation used `-Pandroid.testInstrumentationRunnerArguments.class=com.umbracam.app.ScreenRecordingIntegrationTest#cancellationNeverCreatesOutputAndNextStartRequestsFreshConsent`, plus `-Pandroid.testInstrumentationRunnerArguments.physicalAcceptance=true` and `-Pandroid.testInstrumentationRunnerArguments.screenInterruptions=true`, with `:app:connectedDebugAndroidTest --offline --no-daemon --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process --console=plain`. Corrected result: 1 failed, 0 passed, Gradle BUILD FAILED.

Device: physical Samsung SM-S9080, Android16/API36, current user0. The user explicitly made this phone available for all acceptance steps and confirmed it was unlocked. Tests use its explicit ADB selector on server5038. No other device is controlled.

## Implementation

Upgraded installed UmbraCam0.3.0/code3 in place to delivered0.6.0-beta1/code6. No app data wipe and no production implementation change. Recorded SHA256 for46existing camera videos before upgrade. The delivered APK remains de22873f13ffe22365044d65d83a37132550ddeaa883d66c6e1851600c3a1f0a.

## Files / dependencies

Created this physical acceptance report. Modified test-only physical opt-in guards in ScreenRecordingIntegrationTest.kt, DualRecordingIntegrationTest.kt and DualRecordingAcceptanceTest.kt; sustained artifacts now label physical runs PHYSICAL instead of EMULATOR. No dependency added. TESTING.md, DEVELOPMENT_PLAN.md and BETA_ACCEPTANCE.md will receive final status. Native logs/JSON manifests are local ignored .tools evidence.

## Exact tests and results

AndroidTest-only build using JDK17/SDK36/Gradle8.13, --offline --no-daemon --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process --console=plain. Build logs physical-beta-fixture-build.log (46s) and physical-beta-sustained-fixture-build.log (58s). No host build overlaps sustained capture.

Physical core runner, physical-beta-core-tests.log:6methods passed38.142s:
- CameraRecordingTest#availableFrontAndRearRecordSilentDecodableVideos
- CameraRecordingTest#microphoneRecordingContainsAudioAndVideoTracks
- CameraRecordingTest#recreationKeepsTheSameAuthorizedRecording
- CameraRecordingTest#homeKeepsRecordingAndNotificationStopFinalizes
- ScreenRecordingIntegrationTest#cancellationNeverCreatesOutputAndNextStartRequestsFreshConsent
- ScreenRecordingIntegrationTest#realConsentProducesSilentDecodableVideoAndLibraryEntry

Physical opt-in runner (-e physicalAcceptance true -e screenInterruptions true -e dualIntegration true), physical-beta-interruption-dual-tests.log:3methods passed27.695s:
- ScreenRecordingIntegrationTest#rotationStopsCapturePreservesFilesAndRequiresFreshConsent
- ScreenRecordingIntegrationTest#projectionRevocationSavesPartialAndNeverReusesConsent
- DualRecordingIntegrationTest#bothLayoutsUseTheServiceAndPublishVersionTwoWithFrozenSettings

The projection fixture stops the real MediaProjection via public stop(); it does not certify tapping the OS sharing-chip control. Actual consent dialogs are used; none is fabricated or bypassed. Screen tests validate silent decodable saved video, recreation/timer/Home/notificationStop, library play/pause/seek/close. Camera tests validate real front/rear files, microphone audio-track presence and accepted continuation. Audio-track presence alone does not prove audibility.

Camera-support UI advertises rear1/front2 and rear1/front4 groups. The tested first compatible pair recorded both PiP and split at portrait HD with the established frozen-settings/service/publication path. This is a physical short-recording result; sustained frame-rate/thermal qualification awaits the runs below.

## Native export and delete

Real Create Document picker saved a newly created test camera file fa116384-c17d-4183-b873-4eed9744037c.mp4 to Downloads. It was explicitly checked absent from the46original-file manifest before deletion. Source and exported copy both hashed42805d9dd189736d0dab3439f1f1621e6e0a0f96d951c428c76f17900fd8fae3. Keep recording canceled the first delete. A subsequent confirmed Delete removed only that new test original; the Downloads copy retained the same hash. Earlier-user-video preservation will be verified again at completion.

## Sustained runs — aborted for heat

DualRecordingAcceptanceTest, -e dualAcceptance true -e physicalAcceptance true -e acceptanceSeconds300, fresh acceptanceRun UUID80f679cb-f61d-4e9a-9d2a-5be71d915499. Each of sustainedSplit and sustainedPictureInPicture records five minutes with microphone, presses Home halfway and stops through the notification. The fixture measures video/audio sample timing, decodes beginning/middle/end frames and verifies earlier bytes. Results pending. Charging over USB; initial battery75%, reported battery temperature39.0C. These are observations, not thermal qualification. User confirmed speaking the requested audible test phrase during the first (split) run; playback audibility has not yet been confirmed.

## Remaining acceptance / limits

Pending: beta2 install and reconciliation of the aborted segment, sustained results, actual OS sharing-control stop, process-termination recovery on this phone, listening confirmation and short screen-off camera continuation. Broad OEM/device/call/thermal/battery and all-quality/all-orientation qualification remain outside one handset session. SOS/encryption/overlays/internal audio remain deferred.

## Cleanup and next task

Initial permissions CAMERA/MIC/POST were granted for user0; auto-rotation0/user-rotation0; screen_off_timeout30000. Screen timeout was temporarily extended to600000 to keep the acceptance session available and MUST be restored to30000. Restore any other changed settings, stop all capture, verify the46original hashes, leave the beta installed and save results to Memo Bank before finishing. A floating chat bubble briefly covered controls; unrelated phone content is outside this acceptance scope.
