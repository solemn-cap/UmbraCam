# UmbraCam closed beta — tester guide

**Status: staging draft; current source host-verified, device acceptance pending.** Do not distribute until the connected/device acceptance gates are marked ready in `DEVELOPMENT_PLAN.md` and the APK artifact/hash has been reviewed. This guide is scoped to a small, opt-in Android test group; it is not a public-release checklist.

## Candidate scope

The current source version is UmbraCam `0.6.1-beta2` (version code 7), package `com.umbracam.app`, minimum Android 10/API29. Camera Studio supports user-started front or rear recording, with optional microphone and advertised compatible dual-camera layouts. Reaction Studio supports screen-only or screen-plus-front-camera capture after Android's consent dialog, with optional microphone and policy-permitted playback audio. The Private Vault encrypts completed media. Pro creator editing exports a new encrypted copy with supported trim, framing, branding and audio controls; the source remains intact. Confirm each behavior on the reviewed APK and target device before advertising it to testers.

The checked-in beta2 APK predates the current source work: Reaction Studio audio, SOS Quick Record, the Private Vault and creator export changes are not present in it. Do not infer that new source features are in a distributed artifact; any updated APK requires connected/device acceptance and a fresh artifact review.

The current APK is a debug-signed engineering build. Give testers the same reviewed APK; later updates must use the same signing key. Do not ask a tester to uninstall an earlier build or clear app storage to resolve an install/update problem: uninstalling removes app-private recordings. The application ID is provisional. Do not distribute through Play or describe this as a release-signed build.

## Before the first recording

- Use only content that is safe to lose and share with the development team if the tester explicitly chooses to share it.
- Recordings are stored locally in app-private storage, **without encryption**. There is no cloud backup. Uninstalling the app removes its private recordings. Export anything the tester wants to keep before uninstalling.
- Reaction Studio currently records silent video. Microphone commentary and internal playback audio are unavailable there. Camera Studio's optional microphone path has not had a complete physical audibility qualification across devices.
- SOS is a preview and does not contact anyone or guarantee recording. Vault is not an encrypted vault.
- Use short initial captures, stop if Android displays a thermal warning or the device becomes uncomfortably hot, and avoid prolonged capture until thermal qualification is complete.

## Suggested first-round checks

1. Open Camera Studio, review permissions, record one short front clip and one short rear clip, then play each.
2. If the user wants audio, explicitly enable the Camera Studio microphone and record a short disposable sample. Confirm audibility locally; do not upload the clip without consent.
3. In Reaction Studio, start a short screen-only capture, accept Android's fresh consent dialog, show changing disposable content, stop, and play the result.
4. If Screen + camera is enabled for that device, try PiP and split separately. Stop if a required source fails or the device heats up.
5. Export one disposable clip using Android's destination picker. Delete only that disposable original after checking the export. Do not use personal recordings for deletion tests.

## Feedback template

Send one report per issue. Include device model, Android version, UmbraCam version, mode/layout, steps, expected result, actual result, and whether the issue repeats. Include the approximate recording duration and any Android thermal warning. Attach logs or media only after checking that they contain no private information and obtaining the recording owner's consent. Never include credentials, notification contents, or unrelated personal data.

```text
Device / Android:
UmbraCam version:
Mode / layout:
Steps to reproduce:
Expected:
Actual:
Frequency:
Approximate duration / thermal warning:
Optional sanitized log or disposable sample (owner consent confirmed):
```

## Current go/no-go items

- Host verification for the current source revision passed: 195 JVM tests, debug and AndroidTest APK assembly, lint with zero errors. Current-revision emulator instrumentation did not run because the disposable API36 AVD never registered with ADB; physical short-capture, audio, media-export and source-preservation checks remain pending.
- See [BETA_CANDIDATE.md](BETA_CANDIDATE.md) for the debug APK hash, provenance and release gates. Do not distribute it until device acceptance and artifact review are complete. Do not run instrumentation against a tester's daily-use installation.
- The closed test group, contact method, and consent to receive the APK have not been supplied. No invite or external distribution has been sent.
