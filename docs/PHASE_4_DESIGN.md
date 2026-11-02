# Phase 4 — Dual-camera recording design

Date: 2026-10-31. Status: design prepared after the user requested moving to Phase 4, following the proposal for Phase 4 design. No dual-recording implementation or new APK is delivered by this document. The current app remains 0.4.0. The user's phone remains unavailable; development verification must use explicitly selected emulators.

## Outcome and scope
Record one front and one rear logical camera into a single composited MP4, with optional microphone audio. Offer picture-in-picture and a two-region split layout. Preserve existing single-camera behavior, private publication, foreground-service controls and explicit recovery. No separate camera files, screen capture, playback audio, Vault, contact alerts or automatic background reacquisition belong to this milestone.

A camera pair being advertised is only the first gate. Successful binding, live-source evidence and actual decoded output are separate gates. Phase 4 acceptance still requires compatible physical hardware; emulator results alone cannot establish device support.

## CameraX decision
Keep the existing pinned CameraX 1.5.3 modules. Its concurrent composition path uses the same Preview and VideoCapture instances in the two SingleCameraConfig entries, with one UseCaseGroup and CompositionSettings. This provides one composed preview and recording. Avoid a new MediaCodec/OpenGL pipeline unless the feasibility spike demonstrates that the existing backend cannot satisfy the source-health contract. Do not upgrade CameraX or add dependencies as an assumed fix.

The exact 1.5.3 source archives were inspected, rather than relying on newer reference signatures: [camera-lifecycle sources](https://dl.google.com/dl/android/maven2/androidx/camera/camera-lifecycle/1.5.3/camera-lifecycle-1.5.3-sources.jar), [camera-core sources](https://dl.google.com/dl/android/maven2/androidx/camera/camera-core/1.5.3/camera-core-1.5.3-sources.jar). Composition supports Preview and VideoCapture; the design adds no analysis/still stream. The list order controls layering. VideoCapture mirrorMode is ignored in this mode, so independent front-camera mirroring is not promised. Preview and saved composition must match. Borders, rounded-corner and z-order setters added in later releases must not enter the 1.5.3 implementation.

## Eligibility and negotiation
1. API 29 continues to support single recording; the standard dual eligibility path requires API 30 or later.
2. Requery the current CameraManager inventory and CameraX availableConcurrentCameraInfos after explicit dual selection. Accept an exact two-ID combination independently advertised by both, containing one known front and one known rear logical camera. Do not derive pairs from larger groups, count lenses, substitute the default lens or select same-facing/physical subcameras.
3. Resolve selectors to these exact IDs. A disconnected or ambiguous ID invalidates the choice. The Phase 3 checker remains metadata-only and never starts this negotiation or opens sources.
4. Initial profile candidate: SDR, HD/720p, requested 30fps, one normal CameraX Recorder. Per-camera video capabilities and frame-rate ranges are necessary filters, not proof of a supported concurrent session. Disable UHD/HDR, stabilization/extensions and live quality changes in the initial dual scope. Add a lower profile only as an explicit pre-start user choice backed by validation.
5. Bind the exact shared Preview/VideoCapture group with both configs as one operation. Binding may fail synchronously or asynchronously. Do not fabricate a Camera2 SessionConfiguration from quality labels: CameraX negotiates actual surfaces internally. A successful return alone does not make recording ready.
6. If the pair/profile cannot be acquired, release partial ownership, report the failure and offer an explicit return to single-camera mode. Never silently start or continue a single-camera session from a dual request.

## Composition contract
Define geometry in final-output normalized rectangles, not in screen pixels. PiP default: rear fills the output; front occupies [0.64,0.04,0.96,0.36], a 32% inset with 4% top/right margins. Split default: rear and front occupy equal left/right regions in landscape and top/bottom regions in portrait. CameraX owns input cropping/resolution negotiation; the whole composed frame is displayed with FIT_CENTER so the preview does not hide recorded edges.

A pure mapping function converts final-output rectangles into the pinned CompositionSettings coordinate system and inverse rotation/mirroring transform. Do not copy offsets from a documentation example without checking the saved result. Test portrait/landscape/180/270-degree cases, z-order and asymmetric markers. The front image may be mirrored differently from a single-camera selfie preview; the actual composed preview is the authority, and output must agree. No independent mirror switch is exposed until supported behavior is proven.

Lock pair, layout, primary lens, profile and output target rotation for each accepted attempt. Rotating the activity must not mutate the ongoing recorded composition or trigger rebind. Layout/camera swaps are pre-start operations; after finalization, changed settings require a new session rather than rewriting completed segments.

## Ownership and state
Keep app plus core:recording. The existing application controller remains the single owner of ProcessCameraProvider, foreground service, Recorder attempt and private output. Introduce a concrete dual binding adapter under app/camera only when implemented. Single and dual bindings are mutually exclusive; switching mode is rejected while any accepted attempt is busy. Reuse the process-owned capture lifecycle for both configs. Preview disposal/recreation changes only the view surface, not accepted recording ownership or pair identity.

The current SingleCameraSessionCoordinator deliberately rejects more than one camera. Do not remove this guard globally. During implementation, extract the already-tested transition machinery into a policy-driven internal coordinator and retain the SingleCameraSessionCoordinator facade with its one-camera policy. The dual policy requires FRONT_CAMERA and REAR_CAMERA, plus MICROPHONE only when selected; SCREEN and INTERNAL_AUDIO remain invalid. Keep the existing begin/stop/finalize/recovery invariants and tests unchanged at the single-camera boundary.

A proposed immutable dual specification carries exact camera IDs, layout, output geometry, quality, rotation and audio choice. Bind generation and acquisition attempt token are ephemeral and fence every asynchronous callback. Stop during preparation wins; stale callbacks cannot make a newer attempt active. Exactly one Recorder stop/finalize/publication owns the single composite file.

## Required-source health: feasibility gate
CameraState.OPEN and positive combined encoded bytes do not establish two healthy input streams. Before RECORDING, require both cameras open without errors, advancing per-device capture evidence after the current acquisition boundary, Recorder Start, positive encoded output and microphone readiness when audio was requested.

The first spike must verify whether a read-only Camera2 session capture callback attached through the pinned CameraX interop survives the shared-use-case/stream-sharing path and identifies both session.device.id values. Observe frame numbers/sensor timestamps and local monotonic receipt times; never submit/stop requests, close sessions or attach surfaces through those callbacks. Do not add a third ImageAnalysis stream to obtain telemetry. Callback propagation to both inputs is an open feasibility question, not an implemented guarantee. See [interop callback contract](https://developer.android.com/reference/androidx/camera/camera2/interop/Camera2Interop.Extender).

For the prototype, use a 10-second acquisition deadline and a proposed 1.5-second per-input progress deadline, measured with a monotonic clock and tested against observed cadence before acceptance. Sensor clocks may differ; do not subtract front/rear sensor timestamps to claim synchronization. A known camera error/close or required microphone loss immediately exits RECORDING and requests Stop; loss of progress becomes an interruption when its deadline expires. OS scheduling can delay the check, so this is not a hard real-time guarantee. Output bytes alone never keep a failed input healthy.

Capture completion telemetry is not proof of compositor contribution. Decoded-region tests with distinct moving input markers must separately prove both streams reach the MP4. If reliable two-input health/provenance cannot be established with this backend, the spike fails and dual Start stays unavailable. Document an alternative design before committing to a custom compositor; do not weaken the readiness rule.

## Storage, service and recovery
Reuse the typed non-sticky camera/microphone foreground service, mandatory notification controls, one pending file, structural MP4 inspection and atomic publication. A composited recording has one video track and zero or one microphone track. Preserve completed files on failure, quarantine unverified pending output and retain warnings after partial finalization. Structural validation is not proof of both video regions or continuous playback.

Add a bounded journal v2 descriptor for dual sessions only when implementing persistence: explicit mode, exact IDs, layout, quality, rotation/output geometry, mirror policy and audio choice. New readers accept legacy v1 single records and v2 dual records; v1 single writes and existing files remain compatible. Do not infer a dual mode from a malformed legacy source set. Validate enums, IDs, bounded dimensions and all referenced segment paths. Unknown/corrupt records warn and preserve media. Older app versions may not understand v2 metadata and must not be treated as a supported active-session downgrade path.

Explicit Resume must acquire the same immutable specification with a new attempt token and fresh source evidence; unavailable pairs require a new user-selected session. Process death, force-stop and reboot never resume capture. No new Android permissions or MediaProjection flow are needed. Microphone remains opt-in and permission failure cannot silently turn requested audio into a silent dual recording.

## Implementation sequence
| Step | Concrete work | Exit condition |
|---|---|---|
| 4.1 Feasibility | Debug/instrumentation-only shared-use-case binding and one composed output; prove per-device callbacks and independently changing regions | Exact pair/profile, both source signals, PiP/split output, view detach and source-loss behavior recorded; unsupported cases remain disabled |
| 4.2 Domain | Immutable dual specification, validated geometry, source-policy coordinator and stale/freshness guards | Pure tests pass; legacy single coordinator contract unchanged |
| 4.3 Integration | Dual adapter, foreground ownership, journal v2/read compatibility and publication | Lifecycle/storage/error integration checks pass with previous files unchanged |
| 4.4 Controls | Extend existing Material Camera Studio with explicit mode/layout/try/start/retry choices; disable conflicting actions while busy | Native light/dark, large text, navigation and accessibility review; no capture from Home or metadata checker |
| 4.5 Acceptance | Real-device profiles, decoded provenance, interruption/thermal/storage matrix | Named physical evidence meets the matrix below; unsupported devices use explicit single-camera fallback |

No speculative modules, empty interfaces or release-facing dual controls are added during the design step. UI work will preserve the approved Material foundation and SOS/launcher identity.

## Validation plan
Pure tests: exact pair intersection and stale IDs; groups larger than two; unknown/same-facing cameras; rectangle containment and transforms; source policy; Stop versus late Start; missing one source; stale generation/frame events; stalled secondary while composite bytes grow; required microphone loss; explicit same-spec recovery; v1/v2 round-trip, invalid metadata and preserved completed references.

Emulator instrumentation: isolated AOSP API30 AVD, explicit serial, synthetic front/rear sources and test-only permission grants at the capture fixture. Produce real MP4s for both layouts and lens order, decode several timestamps plus the ending, inspect expected dimensions/orientation and audio tracks, and verify independently changing regions. If feeds cannot provide distinct provenance markers, record the gap rather than claiming that two visible regions prove two cameras. Exercise immediate Stop, failed bind, active source denial, notification Stop, Home/recreation with no rebind, and process death with earlier segments retained. Keep the existing single-camera regression suite.

Physical acceptance requires an explicitly available compatible device and an incompatible control device. Record model/build, exact IDs, negotiated profile, layout, orientation, audio mode, duration, decoded-region evidence and source-loss timing. Validate 30-second samples and at least one 5-minute sustained recording per supported layout/profile, full playback, real camera competition, permission revocation, screen off, thermal pressure and controlled storage/finalization failure. Define and record objective frame cadence/drop and A/V measurements before claiming profile performance; precision cross-camera/audio synchronization remains Phase 7. Never fill or manipulate the user's personal device as a casual failure fixture. No new physical test is authorized while the phone is in use.

## Current evidence and open decisions
At design preparation, only source/API review and repository inspection had run. Subsequent Phase4.1 evidence is in [PHASE_4_FEASIBILITY.md](PHASE_4_FEASIBILITY.md): ordinary-input emulator composition, both device callbacks after preview detach and camera-denial staleness are demonstrated. Distinct-input provenance remains open; file-backed sources remove advertised concurrency on this image. No product dual controls or physical dual result exist. Phase4.2 may develop isolated contracts/policy, but full4.1 acceptance and product integration remain gated. Layout defaults above remain proposed product behavior.

### Constraints learned in 4.1
Exact ID selectors must include explicit known lens facing. CameraX composition returns one public wrapper for two internal cameras; use per-device callback evidence rather than wrapper count. Give changed immutable layouts a fresh binding lifecycle identity; unbindAll with the same owner retained prior settings in the spike. Carry source-loss diagnostics separately from Recorder finalization success. These pinned-backend constraints supersede assumptions where necessary; single-camera guards remain unchanged.

### Phase 4.2 implemented boundary

[PHASE_4_DOMAIN_REPORT.md](PHASE_4_DOMAIN_REPORT.md) records implemented immutable settings/final-frame geometry and dual source-health policy, plus shared transitions behind the preserved single-camera facade.97 JVM tests pass. CameraX geometry mapping, Android adapters, journal v2 and controls remain pending; the distinct-input provenance gate from4.1 remains open.

### Provenance follow-up supersedes the remaining emulator input gate

[PHASE_4_PROVENANCE_REPORT.md](PHASE_4_PROVENANCE_REPORT.md) demonstrates independently changing contributions through separate per-source exposure markers, in PiP/split and both primary orders. The earlier file-input limitation remains factual; the bounded gate is closed with a different validated method. Proceed to4.3 Android adapter/service/journal integration. User controls, all-rotation geometry mapping and physical acceptance remain pending.

### Phase4.3 implemented initial integration

[PHASE_4_INTEGRATION_REPORT.md](PHASE_4_INTEGRATION_REPORT.md) records implemented controller/service routing, exact shared dual binding, captured attempt health and scheduled ticks, atomic saving and compatible v2/v1 journals.109 JVM tests,15 native emulator methods and a separate forced-termination oracle pass. Binding is restricted to verified portrait/rotation0 geometry, with dimension/rotation validation before publication. General transforms, user controls and physical acceptance remain pending.
