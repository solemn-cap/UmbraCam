# First useful beta — 0.6.0-beta1

A usable development beta is implemented and packaged. Final native results and remaining limits are recorded below. The packaged APK is ready to try. This is a debug-signed APK, not a store release or a physical-device qualification.

## 1. Implementation

- Existing Camera Studio supports one front/rear camera with optional microphone, and advertised compatible dual front/rear pairs in the established portrait HD PiP/split profile.
- Screen recorder now performs real, silent whole-display MediaProjection recording. Every Start requests fresh Android consent. A typed foreground service owns capture; RECORDING requires an actual encoded sample written to the muxer. Home/recreation continue an accepted attempt; explicit controls and notification Stop finalize it. Rotation, display resizing and revoked consent stop capture and require a new Start.
- Encoder candidates retain aspect ratio, use even dimensions and fit within a 1280-pixel long edge. The controller tries 1280/960/720/480 ceilings and selects a codec-supported candidate before consent. MediaCodec/MediaMuxer run on one worker; completed output is validated and atomically published. Pending/invalid output is quarantined. A separate bounded screen journal stores metadata, never consent or projection authorization.
- Recordings lists completed camera/screen videos, latest first, with source, date, duration and size. A confined TextureView/MediaPlayer uses native playback controls and releases resources on close/background. Android Create Document exports a copy to the user's selected destination. Confirmed Delete removes only the selected original and updates terminal session references. Camera/screen acquisition and export/delete are mutually exclusive.
- Storage is private but unencrypted; uninstall removes originals. The app says so. Home retains the approved Material3 identity, supplied icon, system appearance and SOS Mode name. SOS remains an explicit preview.

## 2. Created files

Product files under app/src/main/java/com/umbracam/app/:
- screen/ScreenEncoder.kt
- screen/ScreenRecordingController.kt
- screen/ScreenRecordingService.kt
- screen/ScreenSessionJournal.kt
- screen/ScreenStudioViewModel.kt
- screen/ScreenStudioScreen.kt
- library/RecordingCatalog.kt
- library/RecordingsViewModel.kt
- library/RecordingsScreen.kt
- library/RecordingPlayerView.kt

Tests:
- app/src/test/java/com/umbracam/app/library/RecordingCatalogTest.kt
- app/src/test/java/com/umbracam/app/screen/ScreenSessionJournalTest.kt
- app/src/androidTest/java/com/umbracam/app/ScreenRecordingIntegrationTest.kt

Delivery/design evidence: this report; the beta surface brief in .impeccable/surfaces/; 22 native captures plus FINISH_REVIEW.md and SYSTEM_COMPARISON.md under .impeccable/review/beta/. Ignored .tools scripts/logs support reproducibility and the packaged APK.

## 3. Modified files

- app/build.gradle.kts; app/src/main/AndroidManifest.xml; app/src/main/res/values/strings.xml
- app/src/main/java/com/umbracam/app/{UmbraCamApplication,MainActivity,UmbraCamApp}.kt
- app/src/main/java/com/umbracam/app/camera/{CameraRecordingController,CameraSessionCoordinator}.kt
- core/recording/src/main/kotlin/com/umbracam/core/recording/{RecordingSessionTransitions,SingleCameraSessionCoordinator,DualCameraSessionCoordinator,ScreenSessionCoordinator,ScreenOutputSize}.kt
- NavigationTest.kt, ScreenSessionCoordinatorTest.kt and ScreenOutputSizeTest.kt
- README.md and docs/{ARCHITECTURE,DEVELOPMENT_PLAN,DECISIONS,TESTING}.md

## 4. Dependencies

None added. Existing pinned versions are unchanged. App version 0.6.0-beta1/code 6 uses the development debug key. No credentials, signing keys or local tools were added as product dependencies.

## 5. Exact checks executed

Host: JDK 17, SDK 36, Gradle 8.13, offline, serial builds. Final production log .tools/beta-player-build.log:
```
:app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :core:recording:test :app:lintDebug
--offline --no-daemon --max-workers=2 -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process --console=plain
```
Later AndroidTest-only builds used the same flags with :app:assembleDebugAndroidTest to correct the fixture; they did not change the production APK.

Native commands used only adb server 5038 and explicit emulator-5558, with each AVD booted separately. Instrumentation runner: com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner.

| AVD / suite | Exact methods | Result / log |
| --- | --- | --- |
| UmbraCam_Phase3_API30_AOSP / CameraRecordingTest | availableFrontAndRearRecordSilentDecodableVideos; microphoneRecordingContainsAudioAndVideoTracks | Passed, included in4-method, 36.792s run, beta-api30-camera-regression.log |
| Same / DualRecordingIntegrationTest, -e dualIntegration true | bothLayoutsUseTheServiceAndPublishVersionTwoWithFrozenSettings; immediateStopAndUnsupportedOrientationNeverClaimRecording | Passed in same run. This camera regression preceded the screen-size/player-only corrections; camera implementation did not change afterward. |
| Same / ScreenRecordingIntegrationTest | cancellationNeverCreatesOutputAndNextStartRequestsFreshConsent; realConsentProducesSilentDecodableVideoAndLibraryEntry | Final production passed 2 methods, 21.859s, beta-api30-screen-playback-verified.log |
| UmbraCam_Phase3_API36 / ScreenRecordingIntegrationTest + NavigationTest | The same 2 screen methods plus destinationSurvivesActivityRecreation; sosPreviewExplainsItsLimits; settingsExplainsSystemAppearance; supportCheckSurvivesRecreationAndBackReturnsToSettings; systemBackReturnsFromSettingsToHome; homeSectionsNavigateToCameraOrHonestPlaceholdersAndBack | Passed 8 methods, 64.107s, beta-api36-delivery-validation.log |

Screen tests use the actual system consent dialog, with no synthetic grant or consent bypass. They check two cancellations with unchanged output set, real recording readiness, recreation, elapsed timer, Home continuation, notification Stop, silent saved metadata, decoded frame, library discovery, native playback/pause/seek and close/release.

Native manual checks: actual notification-shade tap reopened a roughly 45-second screen capture, then Stop saved it. Actual Create Document export to Downloads matched the original SHA25692f27c8fe7b966cd74a4fa9eb97c365a20129a1b5e0bc0b505060ff7f9b6e00c. Keep recording canceled deletion; confirmed Delete removed only the current-pass test original, while the exported 482829-byte copy remained. The real empty-library state appeared. No historical camera recording was deleted.

Data preservation: all 50 pre-existing API30 completed camera files retained identical hashes after upgrade/regression; 55 camera files remained including 5 new test outputs. Local before/final manifests are beta-api30-before.sha256 and beta-api30-final.sha256.

Visual checks: actual native phone 720x1280/320dpi light 1.0, dark 1.3, light 2.0 and tablet 1440x2560/320dpi light 2.0, including scrolled controls, real consent, active recording, delete dialog and settled player. Independent review disposition **ship**, no material fixes, scoped to supplied captures. Empty/loading/export-error states have no separate visual acceptance. No HTML detector ran for native Android. The documenter verified incumbent-system preservation; historical PRODUCT.md/DESIGN.md milestone drift and the absent design.json sidecar were documented without unrequested repair.

## 6. Results and corrected failures

159 JVM tests passed:110 core / 49 app, zero failures/errors/skips. Production assembly and lint passed: 0 errors / 14 warnings. Warnings include pinned-version/resource/storage suggestions, a plural candidate, code-only player constructor/tooling and toUri suggestions. Final native totals: 14 passing test executions across the two emulator APIs (12 distinct methods). API36 final run passed all 8 methods in 64.107 seconds. No physical results are claimed.

Earlier failures were not counted as passes. API30 first rejected the initial portrait encoder size; codec-checked smaller candidates fixed that. Native API30 playback exposed SurfaceView overflow; the texture-backed player fixed it and its settled capture was reviewed. Strengthened fixture runs also exposed ActivityScenario onNewIntent identity tracking, a test launcher Intent carrying CLEAR_TASK and the resulting wrong Home/retained-Screen library selector. The final fixture preserves the activity and selects its retained Screen route; notification opening was separately tested through the actual Android shade. Logs retain the failed attempts. Initial splash/transition/scrolled-past-player captures were replaced before review.

## 7. Known limitations

- No new physical-device validation. The user's phone was not accessed. Android 10 / API29, OEM restrictions, calls/audio audibility, thermal/battery behavior and sustained performance are not newly qualified. Emulator camera cadence is not a physical 30fps guarantee.
- Screen capture is silent and whole-display only, within the supported bounded size. Protected content may be blank. Display changes stop capture; no automatic consent reuse or restart.
- SOS, encryption, camera overlays, internal audio and editing are deferred. Recordings is not an encrypted Vault. Dual availability still depends on advertised compatible pairs and the established portrait HD profile.
- Debug signing/distribution is for testing. Store signing, broad device acceptance and release preparation remain separate work. Measured contrast and TalkBack speech were not certified by the visual review.

## 8. Recommended next task

Use this beta for a short supported-phone acceptance session when a phone is available: camera and screen capture, Home/notification Stop, playback, export and deletion of a disposable clip. Fix observed issues before adding more features. No further feature phase is required just to try this first version.

## APK

C:/Projects/umbracam/.tools/deliverables/UmbraCam-0.6.0-beta1.apk

35,998,500 bytes; SHA256:
```
de22873f13ffe22365044d65d83a37132550ddeaa883d66c6e1851600c3a1f0a
```

Platform references: [MediaProjection consent/lifecycle](https://developer.android.com/media/grow/media-projection), [Create Document export](https://developer.android.com/training/data-storage/shared/documents-files#create-file), [surface encoder frame repeat](https://developer.android.com/reference/android/media/MediaFormat#KEY_REPEAT_PREVIOUS_FRAME_AFTER).


Cleanup: both isolated AVDs were synced and shut down; final process checks found no matching emulator/qemu processes. Initial camera/microphone/notification grants and font/theme/display settings were restored. Final delivery/next-task records were saved to Memo Bank. The user's phone was never selected or accessed.
