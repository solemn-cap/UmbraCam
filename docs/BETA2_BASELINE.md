# UmbraCam beta2 baseline

Baseline captured 2026-11-02 from the current repository, verified emulator evidence, and named-device records. This is the starting point for Milestone 3.

## Build and artifacts

- Version: 0.6.1-beta2, code 7.
- APK: `.tools/deliverables/UmbraCam-0.6.1-beta2.apk`.
- APK SHA256: `4CC5FECB9AC291731A3F2337A259C04381EB5683AE892E97720A4E33F0E90528`.
- Latest serial Gradle gate: core recording tests, 53 app unit tests, and lint passed; lint reported 0 errors and 26 warnings.

## Emulator baseline

API36 emulator native validation passed 14 tests in 93.057 seconds: navigation, neon source/search flows, screen consent/capture/playback, camera front/rear and microphone-track checks, and thermal stop/cooldown behavior. The emulator was reset after testing; no physical-device result is inferred from it.

## Physical baseline

Samsung SM-S9080, Android 16/API36, beta1 installed at the last physical session. Nine short native methods passed, covering front/rear camera files, microphone-track presence, recreation, Home/notification finalization, projection cancellation/real consent/playback, rotation, projection revocation, and short PiP/split dual recording. Export/hash/delete checks passed and the original completed media hashes were preserved.

The five-minute sustained split attempt was deliberately stopped after thermal status reached CRITICAL (status4; skin47.1C; battery43.3C). It is not a pass, PiP sustained did not run, and audibility was not confirmed. Beta2 was subsequently installed in place as code7 without wiping app data.

## Known baseline limitations

The current baseline does not claim universal 4K or frame-rate support, OS sharing-chip stop, process-termination recovery after the thermal abort, screen-off continuation, internal audio, encryption, biometric vault access, or live premium billing.

The first post-install bounded physical runner was inconclusive: three screen tests could not find the Android screen-share controls, and two camera tests timed out entering Camera Studio. These are harness/device-state failures, not passes; the phone remains available for a manually supervised retry.

Manual supervised retry after unlocking reached Camera Studio and started a real camera session. The foreground notification appeared. When the phone reached thermal status3, beta2 stopped and finalized the session; the UI showed “Video saved locally,” blocked retry, and did not automatically resume. Screen timeout was restored to30000ms. This is a short thermal-safety observation, not sustained-performance qualification.

The finalized file appeared in the physical Recordings library as a Camera recording dated 2 Nov 2026 13:24 with a displayed duration of 90 seconds and size 189028 KB. The library also retained earlier camera and screen entries. Playback/listening was not attempted during this pass.
