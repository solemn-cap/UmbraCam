# UmbraCam implementation status

Updated 2026-11-02 after the Milestone 7 Private Vault implementation. Current feature work remains unverified by user direction.

## Works

- Neon dark design system with Poppins typography, responsive phone/tablet navigation, Home dashboard, Settings, onboarding and permissions guidance.
- Camera Studio supports front/rear selection, supported dual-camera layouts, preview, microphone option, recording, finalization and library publication.
- Screen recorder uses fresh Android MediaProjection consent, foreground notification, interruption handling and playback. Optional microphone and policy-permitted internal playback are implemented as separate synchronized AAC tracks; latest changes await the accumulated test pass.
- Reaction Studio supports screen-only and screen-plus-front-camera capture, with PiP and split GLES composition. Typed requests are validated before consent, after consent, and before service acquisition; combined recording starts only after both sources are ready and stops/finalizes when the camera source is lost.
- Recordings lists completed camera/screen media with search, source filters, playback, export and confirmed deletion.
- SOS Mode has a Quick Record action routed through the existing rear-camera/service path, microphone off by default, and durable SOS labeling in the camera journal and Recordings library. This implementation is awaiting verification.
- Private Vault encrypts finalized camera/screen media and its title/category index with Android Keystore AES-GCM, migrates old completed clips after verification, and provides optional biometric app lock, metadata management, playback, sharing and explicit export. This implementation is awaiting verification.
- Shared thermal monitoring blocks warm starts and stops active capture conservatively at severe/unknown thermal state.
- Centralized entitlement checks keep core recording/SOS free, unlock local Pro features only in debug builds, and leave billing disabled.
- Milestone 8 adds deterministic Media3 video filter exports that preserve originals, Pro discovery with configurable offer/pricing display, a preview-only teleprompter, and saved Reaction PiP/split preferences. This source awaits verification.
- Single-camera Milestone 2 path is functional: capability-driven front/rear preview, CameraX recording, microphone opt-in, notification/foreground continuation, timer/state reporting, dynamic supported quality choices, storage checks, metadata validation, finalization, recovery journal, playback and export/delete through the library.

## Partially implemented

- Dual-camera recording is limited to advertised compatible camera groups and tested portrait HD configurations.
- Reaction Studio audio adapter is implemented but not yet compiled or tested; playback capture is subject to API and source-app policy. Screen-camera composition is verified on API29/API30/API36 emulators, but physical-device compatibility remains unverified.
- Recording recovery preserves completed/partial outputs and requires fresh consent where Android requires it; process-death and long-duration physical qualification remain open.
- Settings screens expose current supported options and truthful planned-feature descriptions; other preference persistence remains limited.
- Milestone 9 advanced creator tools remain; filter export thermal, cancellation, and media compatibility acceptance is unverified.

## Not implemented

Live billing, cloud backup, advanced reaction layouts/overlays/mixing, AI tools, emergency messaging, and premium purchase flows.

## Verification and limits

The 2026-11-02 baseline Milestone 5 batch passed 178 JVM tests (124 core, 54 app), debug and AndroidTest APK assembly, and lint (0 errors, 27 warnings), before the current audio and SOS implementation follow-ups. Native checks passed combined PiP/split capture and camera-source-loss finalization on API29, API30 and API36 emulators. The current audio and SOS changes have not been compiled/tested by user direction. Physical screen-only/combined/audio/SOS acceptance, OS sharing-chip behavior, sustained throughput and thermal performance remain open. See [MILESTONE_5_PROGRESS.md](MILESTONE_5_PROGRESS.md), [MILESTONE_6_PROGRESS.md](MILESTONE_6_PROGRESS.md), [MILESTONE_7_PROGRESS.md](MILESTONE_7_PROGRESS.md) and [MILESTONE_8_PROGRESS.md](MILESTONE_8_PROGRESS.md).

## Recommended next step

Continue with Milestone 9 advanced creator features before the accumulated test pass. Physical acceptance remains open and is deferred to that final pass.

See the complete staged delivery plan in [ROADMAP.md](ROADMAP.md).
