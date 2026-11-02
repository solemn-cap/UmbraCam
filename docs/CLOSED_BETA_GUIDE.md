# UmbraCam closed beta — tester guide

**Status: staging draft.** Do not distribute until the current acceptance gates are marked ready in `DEVELOPMENT_PLAN.md` and the APK artifact/hash has been reviewed. This guide is scoped to a small, opt-in Android test group; it is not a public-release checklist.

## Candidate scope

The current engineering build is UmbraCam `0.6.1-beta2` (version code 7), package `com.umbracam.app`, minimum Android 10/API29. Camera Studio supports a user-started front or rear recording, with an optional microphone. Reaction Studio supports screen-only recording after Android's consent dialog and silent screen-plus-front-camera composition in PiP or split where the device reports support. Completed clips can be played in the app and explicitly exported or deleted.

The beta2 APK predates the current source work: its Reaction Studio audio and SOS Quick Record remain unavailable. Do not infer that new source features are in a distributed artifact; any updated APK requires the accumulated Milestone 5–9 verification and a fresh artifact review.

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

- Host and emulator verification for the current source revision must pass.
- Physical short-capture smoke checks and source-preservation checks remain pending. The recent physical test setup cleared the installed app's private data; see `PHYSICAL_BETA_ACCEPTANCE.md`. Do not run instrumentation against a tester's daily-use installation.
- The closed test group, contact method, and consent to receive the APK have not been supplied. No invite or external distribution has been sent.
