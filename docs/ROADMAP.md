# UmbraCam delivery roadmap

Updated 2026-11-02. This roadmap follows Development Instruction 002 and the current codebase. Each milestone ends with a reviewable build, tests, documentation, and an explicit go/no-go gate. Core recording remains free and unlimited throughout; hardware, operating-system, storage, thermal, and battery limits remain valid constraints.

## Current baseline — implementation complete, acceptance open

The neon beta2 foundation and the Milestone 5 Reaction Studio logic slice are implemented. Reaction Studio supports silent screen capture and screen-plus-front-camera PiP/split composition, with fresh consent, typed capability gates, both-source readiness and camera-loss finalization. The latest accumulated batch passed 178 JVM tests, build/AndroidTest APK assembly and lint (0 errors/27 warnings); native combined-layout capture and camera-loss finalization passed on API29, API30 and API36 emulators. The API30 tests ran on a fresh AVD with a working camera service. Physical Reaction Studio acceptance remains open; the handset must be cool and explicitly available before capture. Full evidence is in [MILESTONE_5_PROGRESS.md](MILESTONE_5_PROGRESS.md).

### Next acceptance batch

- On an explicitly available, cool named handset, check screen-only and screen-plus-camera PiP/split, source loss, sharing-chip stop, and short continuation/finalization.
- Keep sustained thermal/performance and all microphone/internal-audio claims separate; those are not established by emulator results.
- Review the Milestone 5 exit gate before starting another milestone.

## Milestone 3 — recording reliability and recovery

Goal: make authorized recording sessions resilient to normal lifecycle and hardware interruptions.

Scope:

- Complete explicit recording state-machine coverage across camera, screen, SOS, and service ownership.
- Validate background continuation, Home, rotation, screen lock, notification stop, camera loss, microphone loss, storage pressure, encoding failure, and thermal interruption.
- Preserve completed segments and reconcile abandoned journals after process termination.
- Define when recovery can continue an existing authorization and when fresh permission or MediaProjection consent is mandatory.
- Add user-visible recovery details and honest finalization errors.

Exit criteria:

- Deterministic JVM transition and recovery tests cover every terminal path.
- Emulator instrumentation covers lifecycle, rotation, lock/interruption, process-recreation, thermal stop, and fresh-consent requirements.
- Named physical-device checks pass for short background, lock, interruption, and reconciliation flows.
- No UI reports RECORDING when a required source is unavailable.

## Milestone 4 — dual-camera production support

Goal: expand supported simultaneous front/rear recording without assuming universal hardware capability.

Scope:

- Capability inventory for concurrent camera groups, resolutions, aspect ratios, and encoder combinations.
- Production PiP and split layouts with frozen settings and clear unsupported-device explanations.
- Preview and output geometry validation, synchronized publication, and dual-session recovery.
- Device matrix for Android 11+ compatible combinations; Android 10 remains single-camera fallback.

Exit criteria:

- Every advertised configuration is backed by capability metadata and a passing output-validation test.
- Emulator and named physical devices pass short PiP and split recordings with playable output.
- Unsupported configurations fail before capture and preserve prior media.
- Sustained thermal/performance results are reported separately from short functional results.

## Milestone 5 — Reaction Studio

Goal: deliver screen recording with supported face-camera and layout options.

Scope:

- Screen plus front-camera capture where Android and device capabilities allow it.
- Basic PiP and split composition, timer, stop controls, local storage, and metadata.
- Microphone capture and an explicit AudioPlaybackCapture capability check.
- Honest explanation when protected applications or device policy prevent internal audio.
- MediaProjection consent, foreground service, revocation, screen lock, rotation, and interruption handling.

Exit criteria:

- Fresh consent is required for every new projection session.
- Camera, screen, microphone, and audio-track availability are reflected truthfully in the UI and metadata.
- Emulator tests cover cancellation, revocation, rotation, lock, and playback.
- Physical tests name the device and clearly separate screen-only, screen-plus-camera, and audio results.

## Milestone 6 — SOS Mode

Goal: make SOS a fast, reliable, free quick-recording workflow.

Scope:

- Prominent Quick Record action using the existing authorized camera/service path.
- Recording timer, storage monitoring, notification controls, interruption detection, and segment preservation.
- Background continuation where Android permits it.
- Recovery preferences and settings that never bypass permission or camera ownership rules.
- Clear unavailable states; no emergency-service contact or messaging claims until implemented.

Exit criteria:

- Short SOS recordings finalize and appear in the library with SOS metadata.
- Active recording never shows a purchase prompt.
- Interruption and recovery tests preserve completed segments and require reauthorization when necessary.
- Physical-device checks confirm quick start, Home continuation, notification stop, and safe finalization.

## Milestone 7 — Private Vault

Goal: provide a complete, understandable local recording manager.

Scope:

- Recording details screen with filename, date, duration, resolution, frame rate, size, mode, and audio information.
- Rename, sort, category filters, share, export, playback, and confirmed deletion.
- Android Keystore-backed encryption design and implementation only when verified end to end.
- Optional biometric lock where supported, with clear fallback behavior.
- Explicit distinction between private app storage and exported shared storage.
- Protection against accidental data loss and migration of existing recordings.

Exit criteria:

- Metadata and file operations have JVM and instrumentation coverage.
- Encryption is never labeled enabled until encrypted files and recovery behavior are verified.
- Existing media survives upgrade and migration tests.
- Exported copies are clearly identified as outside the app vault.

## Milestone 8 — Premium creator foundation

Goal: make Pro discoverable without implementing deceptive or incomplete commerce.

Scope:

- Polished Pro feature-discovery screen using the neon design system.
- Configurable plan/pricing source with no live purchase action until billing is authorized.
- Initial deterministic filters: Natural, Warm, Cool, Monochrome, Cinematic, Vintage.
- Post-recording processing that writes a new output and preserves the original.
- First creator foundations: teleprompter architecture, layout presets, and feature capability checks.
- Entitlement gates centralized through the existing provider.

Exit criteria:

- Free users retain unlimited core recording and SOS access.
- No fake purchase success, countdown, or forced upgrade interruption exists.
- Filter outputs are deterministic, tested, and reversible by retaining originals.
- Catalog, entitlement, and UI copy agree about what is available.

## Milestone 9 — advanced creator features

Goal: add professional production tools after the core pipeline is proven.

Scope:

- Separate camera and audio tracks where supported.
- Advanced layouts, custom camera windows, overlays, transitions, framing presets, and export configurations.
- Independent dual-camera effects and creator branding.
- Audio mixing, noise reduction, and professional metadata/export profiles.
- Performance budgets, cancellation, progress reporting, and disk-space safeguards.

Exit criteria:

- Multi-track files are validated with media inspection and playback tests.
- Long operations survive cancellation and preserve originals.
- Feature availability is capability- and entitlement-driven.
- Performance and thermal measurements exist on representative named devices.

## Milestone 10 — AI and cloud services

Goal: assess and then selectively ship services requiring separate cost, privacy, and security decisions.

Scope only after a separate authorization:

- Captions, transcription, subtitle styling/export, silence detection/removal, highlights, and vertical reframing.
- Optional encrypted cloud backup, trusted-contact sharing, remote access, and recording-integrity reports.
- Data retention, consent, deletion, regional availability, service failure, offline behavior, and cost controls.

Exit criteria:

- A separate technical and privacy assessment is approved before implementation.
- Cloud and AI features are opt-in, observable, cancellable, and honest about processing location and limits.
- No emergency messaging or cloud protection claim ships without verified backend security and recovery tests.

## Release hardening and final delivery

After Milestone 10 or an explicitly scoped earlier release:

- Run the full JVM, instrumentation, lint, accessibility, storage, upgrade, and regression suites.
- Complete named-device matrix testing across supported Android versions and camera combinations.
- Verify privacy disclosures, permission timing, foreground-service behavior, data-safety declarations, and store assets.
- Run long-duration, thermal, low-storage, interruption, and process-termination acceptance with preserved-media hashes.
- Produce signed release artifacts through external secret handling; never commit keys or credentials.
- Publish the final implementation status, known limitations, support notes, changelog, and rollback plan.

## Approval gates

Milestone 3 begins only after the current Milestone 2 physical gate is reviewed. Each later milestone requires explicit approval after its predecessor's exit criteria are met. The roadmap does not authorize live billing, cloud services, emergency messaging, AI processing, or production signing credentials by itself.
