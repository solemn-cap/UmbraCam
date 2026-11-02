# Beta acceptance follow-up

Scope: interruption acceptance for UmbraCam 0.6.0-beta1. The user's phone remains unavailable unless explicitly offered. Only the named isolated project emulator is authorized for the automated work in this pass. This does not add SOS, encryption or overlays.

## Implementation and files

No production implementation changed. Extended app/src/androidTest/java/com/umbracam/app/ScreenRecordingIntegrationTest.kt with opt-in emulator-only rotation, real-projection revocation and external process-termination fixtures. The revocation fixture obtains the actual owned projection handle through reflection and calls its public stop() API; it does not manufacture authorization, callbacks or encoded data. It verifies backend handling of real revocation, not the phone's sharing-chip UI.

Created this acceptance record. TESTING.md/DEVELOPMENT_PLAN.md and Memo Bank record the final results. No dependency added. Production APK remains the previously delivered debug beta; its hash was verified unchanged at completion.

## Automated interruption checks

Build:
```
:app:assembleDebugAndroidTest --offline --no-daemon --max-workers=2
-Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process --console=plain
```
JDK 17 / SDK36 / Gradle8.13, serial build. Log: .tools/beta-interruptions-build.log.

AVD: UmbraCam_Phase3_API36, explicit adb server 5038/emulator-5558. Test runner com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner, -e screenInterruptions true. Methods:
- rotationStopsCapturePreservesFilesAndRequiresFreshConsent
- projectionRevocationSavesPartialAndNeverReusesConsent
- externalProcessTerminationFixture (host-controlled termination oracle; never count its killed runner as a passed JUnit method)

Results: AndroidTest assembly passed in 1m19s. The two selected rotation/revocation methods passed in 17.009s with no skips or failures (.tools/beta-interruptions-native.log). They saved decodable interrupted clips, retained earlier completed hashes, reported nonrecoverable warnings and showed fresh system consent on the next Start. Two new completed clips were retained.

The host-controlled termination oracle also passed. It killed the active recorder only after its unique readiness marker, then verified journal PREPARING -> FAILED/PREVIOUS_SESSION_INTERRUPTED, no active MediaProjection, no active screen service, an honest interrupted-session warning and fresh system consent on the next Start. All 8 completed files remained byte-identical. The 323903-byte unfinished MP4 was retained intact under quarantine, with no pending file or falsely published recording. The initial 6 completed files from the start of this pass also retained their hashes. The deliberately killed runner reported Process crashed and is NOT counted as a passed JUnit method.

An emulator System UI ANR blocked the first visual relaunch check; the actual Wait control cleared it. The emulator was also woken before final inspection. App storage/journal assertions and the subsequent native UI confirmed reconciliation. This is an emulator-environment limitation, not a claimed clean physical run.

Evidence: .tools/beta-termination-{active-journal,reconciled-journal,pending-before,pending-after,projection-after,service-after}.txt; .tools/beta-termination-{before,final}.sha256; .tools/beta-termination-warning.png. Initial and delivered production APK hashes both remain de22873f13ffe22365044d65d83a37132550ddeaa883d66c6e1851600c3a1f0a. No replacement APK is needed. Earlier 159 unit tests/lint results are historical; those suites were not rerun because production code did not change.

## Physical checklist — pending

Record the actual model, Android version and date before claiming any physical result. Use disposable clips. Retain existing videos and export copies before any uninstall; no reinstall or data wipe is required.

| Check | Procedure | Acceptance | Status |
| --- | --- | --- | --- |
| Camera | Record a short front clip and rear clip; opt into microphone for one. | Both play; intended source is visible; microphone clip is audible. | Pending phone |
| Continuation | Start camera capture, press Home, return through its notification, then Stop. | Same attempt continues; one playable file saves; no unexplained restart. | Pending phone |
| Screen consent | Cancel Start once, then accept a new Start. | Cancellation creates no file; each Start displays a fresh Android chooser. | Pending phone |
| Screen content | Show changing disposable content during capture, then Stop. | Saved video follows changes and is silent. | Pending phone |
| Display change | Rotate while screen recording. | Capture stops with an honest warning; a valid completed portion is playable; restarting asks again. | Pending phone |
| Sharing revocation | Use Android's sharing control to stop the screen projection. | App exits RECORDING, preserves valid output and requires fresh consent. | Pending phone |
| Library | Play/pause/seek/close a clip, export via Documents, cancel one deletion, then confirm deletion of a disposable original. | Playback controls work; exported copy remains usable after deleting its original; earlier videos remain. | Pending phone |
| Process termination | Force-stop during a disposable screen capture, then relaunch. | No automatic recording; interrupted-session warning; completed earlier files retained; unfinished output not advertised as saved. | Pending phone |
| Dual | Check advertised front/rear support and test only if eligible. | Unsupported pairs stay unavailable. Supported capture shows both sources in the selected portrait HD layout. | Pending compatible phone |

Long recordings, real calls, OEM power management, battery/thermal behavior, broad device compatibility, Android10 qualification, measured accessibility and store-release preparation remain separate acceptance work. No physical result is implied by emulator success.

## Recommended next task

Complete the handset checks when the user explicitly makes a phone available; fix observed problems before adding features. If the phone remains busy, keep physical acceptance pending and avoid accessing it.


Cleanup: the explicit API36 emulator was synced and shut down; the final process check found no project emulator/qemu processes. This pass observed POST_NOTIFICATIONS granted, CAMERA/MIC denied, accelerometer rotation1 and user rotation0; those initial settings were left unchanged. The phone was not accessed.

