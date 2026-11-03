# UmbraCam implementation status

Updated 2026-11-03 after the accumulated Milestone 5–9 host verification and two unsuccessful API36 emulator startup attempts.

## Works

- Neon dark design system with Poppins typography, responsive phone/tablet navigation, Home dashboard, Settings, onboarding and permissions guidance.
- Camera Studio supports front/rear selection, supported dual-camera layouts, preview, microphone option, recording, finalization and library publication.
- Screen recorder uses fresh Android MediaProjection consent, foreground notification, interruption handling and playback. Optional microphone and policy-permitted internal playback are implemented as separate synchronized AAC tracks.
- Reaction Studio supports screen-only and screen-plus-front-camera capture, with PiP and split GLES composition. Typed requests are validated before consent, after consent, and before service acquisition; combined recording starts only after both sources are ready and stops/finalizes when the camera source is lost.
- Recordings lists completed camera/screen media with search, source filters, playback, export and confirmed deletion.
- SOS Mode has a Quick Record action routed through the existing rear-camera/service path, microphone off by default, and durable SOS labeling in the camera journal and Recordings library.
- Private Vault encrypts finalized camera/screen media and its title/category index with Android Keystore AES-GCM, migrates old completed clips after verification, and provides optional biometric app lock, metadata management, playback, sharing and explicit export.
- Shared thermal monitoring blocks warm starts and stops active capture conservatively at severe/unknown thermal state.
- Centralized entitlement checks keep core recording/SOS free, unlock local Pro features only in debug builds, and leave billing disabled.
- Milestone 8 adds deterministic Media3 video filter exports that preserve originals, Pro discovery with configurable offer/pricing display, a preview-only teleprompter, and saved Reaction PiP/split preferences.
- Milestone 9 adds non-destructive trim, framing, positioned branded text, per-track audio gain/mixing, target bitrate profiles, progress, cancellation, thermal checks and encrypted publication; the library also displays inspected audio-track counts. See [MILESTONE_9_PROGRESS.md](MILESTONE_9_PROGRESS.md).
- Single-camera Milestone 2 path is functional: capability-driven front/rear preview, CameraX recording, microphone opt-in, notification/foreground continuation, timer/state reporting, dynamic supported quality choices, storage checks, metadata validation, finalization, recovery journal, playback and export/delete through the library.

## Partially implemented

- Dual-camera recording is limited to advertised compatible camera groups and tested portrait HD configurations.
- Playback capture is subject to API and source-app policy. Screen-camera composition has earlier emulator evidence, but the current revision has no connected-device verification; physical compatibility remains unverified.
- Recording recovery preserves completed/partial outputs and requires fresh consent where Android requires it; process-death and long-duration physical qualification remain open.
- Settings screens expose current supported options and truthful planned-feature descriptions; other preference persistence remains limited.
- Advanced creator scope is partial: source video is a baked composite, so independent camera windows and camera-angle transitions cannot be reconstructed. Noise reduction and codec/frame-rate/HDR choices are not implemented. Bitrate profiles are device-dependent encoder targets. Media transformation behavior still needs connected-device validation.

## Not implemented

Live billing, cloud backup, advanced reaction layouts/overlays/mixing, AI tools, emergency messaging, and premium purchase flows.

## Verification and limits

On 2026-11-02 the accumulated current-source host pass succeeded: 132 core JVM tests, 63 app JVM tests, debug and AndroidTest APK assembly, and lint with zero errors. On 2026-11-02 and 2026-11-03, API36 emulator attempts remained offline/unregistered in ADB; no connected instrumentation methods ran. No physical device was attached. Reaction audio, SOS, creator export/media decoding, Android sharing-chip behavior, sustained throughput, thermal performance and existing-file preservation remain unaccepted on a device. Details: [TESTING.md](TESTING.md), [MILESTONE_5_PROGRESS.md](MILESTONE_5_PROGRESS.md), [MILESTONE_6_PROGRESS.md](MILESTONE_6_PROGRESS.md), [MILESTONE_7_PROGRESS.md](MILESTONE_7_PROGRESS.md), [MILESTONE_8_PROGRESS.md](MILESTONE_8_PROGRESS.md), and [MILESTONE_9_PROGRESS.md](MILESTONE_9_PROGRESS.md).

## Recommended next step

Run the connected instrumentation and named-device acceptance pass when a responsive target is attached. Closed-beta candidate details and the current go/no-go status are in [BETA_CANDIDATE.md](BETA_CANDIDATE.md); distribution remains gated on device acceptance and artifact review.

See the complete staged delivery plan in [ROADMAP.md](ROADMAP.md).
