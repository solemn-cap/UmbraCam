# UmbraCam implementation status

Updated 2026-11-02 after the Milestone 5 Reaction Studio implementation and accumulated verification batch.

## Works

- Neon dark design system with Poppins typography, responsive phone/tablet navigation, Home dashboard, Settings, onboarding and permissions guidance.
- Camera Studio supports front/rear selection, supported dual-camera layouts, preview, microphone option, recording, finalization and library publication.
- Screen recorder uses fresh Android MediaProjection consent, foreground notification, interruption handling and playback. Optional microphone and policy-permitted internal playback are implemented as separate synchronized AAC tracks; latest changes await the accumulated test pass.
- Reaction Studio supports screen-only and screen-plus-front-camera capture, with PiP and split GLES composition. Typed requests are validated before consent, after consent, and before service acquisition; combined recording starts only after both sources are ready and stops/finalizes when the camera source is lost.
- Recordings lists completed camera/screen media with search, source filters, playback, export and confirmed deletion.
- SOS Mode is consistently presented as an honest preview; the safety route remains as a backward-compatible internal route.
- Shared thermal monitoring blocks warm starts and stops active capture conservatively at severe/unknown thermal state.
- Centralized free/pro/cloud entitlement model and premium feature catalog are defined without billing or artificial recording limits.
- Filter/effect contracts and deterministic initial filter identifiers are defined without fake processing.
- Single-camera Milestone 2 path is functional: capability-driven front/rear preview, CameraX recording, microphone opt-in, notification/foreground continuation, timer/state reporting, dynamic supported quality choices, storage checks, metadata validation, finalization, recovery journal, playback and export/delete through the library.

## Partially implemented

- Dual-camera recording is limited to advertised compatible camera groups and tested portrait HD configurations.
- Reaction Studio audio adapter is implemented but not yet compiled or tested; playback capture is subject to API and source-app policy. Screen-camera composition is verified on API29/API30/API36 emulators, but physical-device compatibility remains unverified.
- Recording recovery preserves completed/partial outputs and requires fresh consent where Android requires it; process-death and long-duration physical qualification remain open.
- Settings screens expose current supported options and truthful planned-feature descriptions; persistence and secure vault controls are not complete.
- Private Vault is local app storage and export management. Files are not encrypted and biometric access is not implemented.

## Not implemented

Live billing, cloud backup, encryption, biometric vault lock, internal screen audio, advanced reaction layouts, filters/effects processing, teleprompter, AI tools, emergency messaging, and premium purchase flows.

## Verification and limits

The 2026-11-02 baseline Milestone 5 batch passed 178 JVM tests (124 core, 54 app), debug and AndroidTest APK assembly, and lint (0 errors, 27 warnings), before the current audio implementation follow-up. Native checks passed combined PiP/split capture and camera-source-loss finalization on API29, API30 and API36 emulators. The follow-up API30 lifecycle sweep passed cancellation, rotation, projection revocation, combined capture and opt-in camera screen-off continuation. Its screen-only Home/playback method timed out after System UI ANRs. Current audio changes have not been compiled/tested by user direction. Physical screen-only/combined/audio acceptance, OS sharing-chip behavior, sustained throughput and thermal performance remain open. See [MILESTONE_5_PROGRESS.md](MILESTONE_5_PROGRESS.md) for exact commands and caveats.

## Recommended next step

Complete physical screen-only and screen-plus-camera acceptance only when the handset is cool and explicitly available. Do not begin another milestone until the Milestone 5 acceptance gate is reviewed.

See the complete staged delivery plan in [ROADMAP.md](ROADMAP.md).
