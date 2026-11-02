# UmbraCam delivery roadmap

Updated 2026-11-02. This roadmap follows Development Instruction 002 and the current codebase. Work in substantial implementation batches; add tests with the logic, but defer executing the accumulated verification suite until the premium feature set below is implemented. Then run the full host/emulator/device verification pass before opening the closed beta. Core recording remains free and unlimited throughout; hardware, operating-system, storage, thermal, and battery limits remain valid constraints.

## What “premium features built” means for this plan

For this roadmap, the premium build is Milestones 7–9: an encrypted Private Vault, real Pro creator foundations (filters/effects, teleprompter and capability/entitlement UX), and advanced creator workflows (tracks, layouts, overlays, transitions, audio tools and export profiles). Milestone 5's missing Reaction audio and Milestone 6 SOS are prerequisite product work and are built before those premium milestones. This is implementation completion, not a claim of quality or device acceptance. Tests will be authored as logic is built and run together after Milestone 9. The user has asked that testing wait until then.

## Current baseline — implementation complete, acceptance open

The neon beta2 foundation and the Milestone 5 Reaction Studio logic slice are implemented. Reaction Studio supports silent screen capture and screen-plus-front-camera PiP/split composition, with fresh consent, typed capability gates, both-source readiness and camera-loss finalization. The latest accumulated batch passed 178 JVM tests, build/AndroidTest APK assembly and lint (0 errors/27 warnings); native combined-layout capture and camera-loss finalization passed on API29, API30 and API36 emulators. The API30 tests ran on a fresh AVD with a working camera service. Physical Reaction Studio acceptance remains open; the handset must be cool and explicitly available before capture. Full evidence is in [MILESTONE_5_PROGRESS.md](MILESTONE_5_PROGRESS.md).

### Current state and sequencing

- Milestones 3–5 have substantial implementation and emulator evidence, but physical acceptance remains open. Reaction Studio microphone commentary/internal playback audio, OS sharing-control behavior, and sustained thermal/performance remain incomplete.
- Do not spend the next iteration repeating device acceptance. Continue the planned feature implementation first, preserving the physical/device matrix for the final accumulated test pass.
- The daily-use phone's prior app-private recordings may already have been removed by the connected test-install incident. The user has confirmed those recordings are not useful and authorized discarding them for now; this does not certify physical capture or thermal behavior.

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

Implementation follow-up before premium: finish the supported audio paths (opt-in microphone commentary and policy-permitted playback audio) with timestamped mixing/synchronization, truthful track metadata, and visible capability/permission states. Maintain a silent capture fallback when a source is unavailable. Do not claim universal internal-audio support.

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

After Milestone 9, run one accumulated verification and acceptance pass, then start the closed tester round. AI/cloud Milestone 10 is excluded unless separately authorized. Following tester feedback, complete release hardening and final delivery:

- Run the full JVM, instrumentation, lint, accessibility, storage, upgrade, and regression suites.
- Complete named-device matrix testing across supported Android versions and camera combinations.
- Verify privacy disclosures, permission timing, foreground-service behavior, data-safety declarations, and store assets.
- Run long-duration, thermal, low-storage, interruption, and process-termination acceptance with preserved-media hashes.
- Produce signed release artifacts through external secret handling; never commit keys or credentials.
- Publish the final implementation status, known limitations, support notes, changelog, and rollback plan.

## Build-first work batches, then verify once

1. **Complete capture product logic:** finish Reaction Studio audio and synchronization, then implement SOS as a fast user-started capture flow over the existing recorder/service/recovery path. Preserve safe silent behavior and truthful unsupported states.
2. **Build the Private Vault:** define the threat model and encrypted file format; implement Keystore-backed keys, migration/recovery behavior for existing recordings, biometric lock/fallback, media details and management. Keep export explicitly outside encrypted private storage.
3. **Build Pro creator foundations:** use the existing entitlement/catalog contracts for honest Pro discovery without fake checkout; implement deterministic filters and non-destructive post-processing, teleprompter, reusable layouts and settings.
4. **Build advanced creator tools:** independent tracks where supported, custom camera windows, overlays/transitions, audio mixing/noise reduction and export profiles, each behind actual capability/entitlement gates. Avoid empty modules and unsupported feature claims.
5. **Run the final verification batch:** execute JVM/unit, lint, debug/release assembly and instrumentation suites together; test migrations, interruption, permissions, storage limits, cancellation, media decoding, synchronization, encryption/key loss, and entitlement behavior. Then run named-device acceptance including physical thermal/performance and existing-file preservation. Keep the phone disconnected from test-install operations until this batch.
6. **Start the closed beta:** review the APK signing/key plan and tester guide, invite a small opt-in group, collect consented/sanitized feedback, and triage fixes. No external invitations are sent until the user supplies/approves the tester group and channel.

The initial closed beta may omit Milestone 10 AI/cloud. That milestone requires separate technical, privacy, security, and cost authorization; its optional features do not block a local-only premium beta.

## Approval gates

The user has authorized a feature-first push through the local premium creator scope: implement Milestone 5 audio follow-up, then Milestones 6–9 without running intermediate test suites; execute the accumulated suite after Milestone 9. Physical results and acceptance claims remain pending until that final batch. The roadmap does not authorize live billing, cloud services, emergency messaging, AI processing, or production signing credentials by itself. Milestone 10 requires a separate technical/privacy/security/cost review and explicit approval.
