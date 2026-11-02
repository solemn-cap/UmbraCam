# Development plan

Implement one milestone per approved task. Each milestone must build, pass relevant unit/lint checks, and document device evidence and unresolved gaps. Phase 0 does not authorize Phase 1.

## Active Milestone 5 acceptance gate

The Reaction Studio logic slice is implemented and its accumulated verification batch passed: 178 JVM tests, debug and AndroidTest APK assembly, lint with 0 errors/27 warnings, and native combined PiP/split plus camera-loss checks on API29/API30/API36 emulators. The two targeted API30 methods passed on the fresh `UmbraCam_API30_FreshCam` AVD. See [MILESTONE_5_PROGRESS.md](MILESTONE_5_PROGRESS.md). Physical screen-only/combined acceptance remains pending until the handset is cool and explicitly available. Microphone and internal playback audio remain unavailable. Do not advance to another milestone before reviewing this gate.

User explicitly approved Phase 1 on 2026-10-30 after the approval requirement was explained. Phase 1 implementation is present. Eleven distinct runtime checks now pass on physical SM-S9080 / API 36 and an API 29 emulator, including navigation/recreation, single-camera/media checks and foreground stop behavior. Broader physical failure/performance/accessibility acceptance is still pending; see DEVICE_VALIDATION.md. That historical evidence does not validate Phase 2 service continuation. Phase 2 is now approved and under verification; dual cameras and screen capture remain deferred.

| Milestone | Scope | Acceptance criteria |
|---|---|---|
| 0 Foundation | App shell, navigation, contracts, docs | Debug APK compiles; Home and all five destinations work; recreation restores route; no permissions; unit tests and lint pass; instrumentation executed when device available, otherwise explicitly pending |
| 1 Single camera | CameraX front OR rear, microphone opt-in, local output | Capability and permission denial handled; user can start/stop on supported physical hardware; playable finalized file; no active claim before source ready; unit and device tests |
| 2 Lifecycle | Service, interruption, segment finalization | Supported continuation with correct notification/service type; phone call, camera loss, revoke, stop and process-death tests; earlier segments retained; honest status |
| 3 Dual detection | Concurrent capability inventory | Enumerates supported combinations on compatible and incompatible devices; clear fallback; never enables unsupported pairing |
| 4 Dual recording | Two cameras and layout composition | Real devices produce playable PiP/split output for supported combinations; thermal/storage failure tested; graceful fallback |
| 5 Screen | MediaProjection recording | Fresh consent per session; cancellation/revocation/rotation/secure-content tests; correct service and final output |
| 6 Screen + camera | Authorized camera overlay/composition | Supported device matrix, correct sizes/orientation, source-loss handling and playable export; no unsupported overlay claims |
| 7 Audio sync | Commentary and permitted playback audio | Timestamp tests; objective A/V skew and drift thresholds defined before implementation and measured over long sessions; prohibited audio excluded |
| 8 SOS Mode | Quick visible user activation, recovery | End-to-end interruption/termination matrix; preserves finalized segments; truthful notification/UI; no silent reacquisition or emergency guarantees |
| 9 Vault | Encrypted private files, biometrics, metadata/playback/export | Threat model; tamper/key-loss/authentication tests; user-directed export; plaintext/thumbnail/backup leakage checks |
| 10 Hardening | Performance and security | Long recordings, low storage, battery/thermal matrix; security review; no release-blocking failures |
| 11 Beta | Release preparation | Signed bundle via external secret handling; ownership verified; current Play target/privacy/Data safety requirements checked; accessibility and device regression complete |

## Current Phase 2 status

The user approved the lifecycle milestone on 2026-10-30. Service ownership, notification controls, explicit recovery, attempt guards and journal reconciliation are implemented. The pending Samsung checks are complete: 13 distinct physical methods passed across final runs, plus force-stop/durable-warning checks. The emulator-only follow-up passed16 distinct API29 instrumentation methods plus force-stop, actual runtime camera revocation and simulated-call/69.5-second recording scenarios; see EMULATOR_VALIDATION.md. Real call/audio, OEM/power and active-disk-full acceptance remain open; PHASE_2_REPORT.md records the earlier physical results. Milestone 3 was approved on 2026-10-31. Metadata detection, explicit fallback and capability UI are implemented; validation is recorded in PHASE_3_REPORT.md. The user requested Phase 4 on 2026-10-31; design is prepared, with implementation and acceptance pending.

## Historical Phase 1 status and remaining acceptance
Implemented CameraX Preview + Recorder for one front OR rear lens, supported quality enumeration/selection, user-driven CAMERA permission, optional RECORD_AUDIO, explicit start/stop, pure session coordinator, and validated/atomic private-file publication. Recreation, leaving and backgrounding stop the attempt without automatic restart in tested scenarios. Unit tests exercise source gating, late callbacks, stop/finalize races and storage publication/failure paths. Named physical basic capture and lifecycle checks now pass; emulator dialog denial/grant and 2× font navigation also pass. Continuous playback/audibility, physical failures, true orientation transitions, all qualities and broader compatibility remain required. Background service behavior remains milestone 2.




## Phase3 native follow-up
Emulator-only verification completed on AOSP API30:13 distinct methods passed in4 configurations, plus28 fixture recaptures; native review ship. See PHASE_3_NATIVE_VALIDATION.md. Physical compatibility/dual streams and actualAPI29 follow-up remain unverified. Phase 4 design is prepared; see PHASE_4_DESIGN.md.

## Phase 4 current task

Phase4.4 user-facing controls are complete:114 JVM tests,14 distinct native emulator methods, four light/dark/large-text configurations and independent native review `ship`. See [PHASE_4_CONTROLS_REPORT.md](PHASE_4_CONTROLS_REPORT.md). Phase4.5 tooling/emulator subset is complete: [acceptance matrix and sustained observations](PHASE_4_ACCEPTANCE.md), two five-minute continuity/integrity passes and two shorter final-fixture passes. Measured emulator cadence near11 video samples/sec does not meet the proposed30fps qualification targets. Full physical acceptance remains open; the user's phone was not accessed. Earlier checkpoints below are historical.

### Phase 4.1 experiment status

[PHASE_4_FEASIBILITY.md](PHASE_4_FEASIBILITY.md) records passing ordinary-input composition/callback and source-denial observations plus the single-camera regression. Full 4.1 acceptance remains open: the distinct video-file fixture removes concurrent eligibility on this API30 image. Keep dual Start unavailable. Next development work is isolated 4.2 dual specification/source-health policy and pure tests; close provenance before product integration/controls.

### Phase 4.2 domain status

Immutable dual settings, final-frame geometry, pair eligibility and source-health coordinator are implemented. Shared transition extraction preserves the single-camera facade. Final verification:97 JVM tests passed, app compilation passed, lint0 errors/11 warnings. See [PHASE_4_DOMAIN_REPORT.md](PHASE_4_DOMAIN_REPORT.md). Distinct-input provenance still gates product integration/controls. Close it on a supported isolated fixture, then proceed to4.3 adapter/service/journal/publication integration;4.4 controls and4.5 physical acceptance remain pending.

### Phase4 provenance gate closed on emulator

[PHASE_4_PROVENANCE_REPORT.md](PHASE_4_PROVENANCE_REPORT.md) supersedes the current distinct-input blocker above for the tested API30 portrait configuration. Independent exposure markers in each source reach the MP4 in both layouts/orders. Provenance1method and regression2methods passed; four older files retain identical hashes. Next is4.3 app adapter/service/journal integration, then4.4 controls and4.5 physical acceptance. No user-facing dual recording is enabled yet.

## Phase4.3 current result and next task

The initial portrait dual adapter, common process/service ownership, health timer, v2/v1 journal compatibility and atomic publication are implemented.109 JVM tests and15 distinct emulator native methods passed, plus the independent termination oracle. See [PHASE_4_INTEGRATION_REPORT.md](PHASE_4_INTEGRATION_REPORT.md) for exact evidence, failed build/fixture attempts and limits. Next is Phase4.4: explicit Single/Dual, layout and microphone controls on the approved Material3 foundation, truthful portrait/profile limits and native UX/accessibility review. Phase4.5 physical acceptance remains pending; the user’s phone is unavailable.

## Current priority: first useful beta

The user authorized a combined shortest-path milestone superseding the small Phase5 checkpoint cadence: real silent screen capture plus a camera/screen recordings library with playback, selected export and confirmed delete. Implement and validate these together before adding SOS, encryption or reaction overlays. See FIRST_BETA_REPORT.md for the live delivery record. Earlier phase descriptions above are historical; full physical dual/performance qualification remains outstanding and must not be claimed from emulator tests.

## Current next task after beta interruption checks

The useful beta is delivered. Emulator rotation/revocation and process-termination acceptance now pass; see BETA_ACCEPTANCE.md for exact scope and environment limitations. Production APK remains unchanged. Next is the recorded handset acceptance checklist when the user explicitly makes a phone available. Keep physical/OEM/audio/thermal/long-duration qualification pending and resolve observed issues before new features; a generic proceed/resume does not revoke the phone-unavailable constraint.

## Current active major slice — Milestone 5 Reaction Studio (2026-11-02)

This latest user direction supersedes the historical next-task paragraph above: work through a substantial Milestone 5 logic slice before running the accumulated verification batch. The core now distinguishes device hardware from adapter support, and the UI plus screen adapter validate typed mode requests before consent, after consent and before service acquisition. Unsupported camera composition/audio remains visibly unavailable; no capture permission is requested during capability discovery.

The substantial implementation batch is in place: silent GLES PiP/split composition, typed foreground camera ownership, camera permission checks at Start, both-source output readiness, camera-frame health stops, recovery-source journaling and live layout/status UI. Core/app JVM tests, builds and lint passed. API29 and API36 emulator tests saved and decoded both layouts and verified actual Camera2 source loss finalizes partial output. The API30 AVD did not boot to a usable camera service, so no API30 result is claimed. Microphone and internal audio are unavailable; physical compatibility and sustained acceptance remain pending. See [MILESTONE_5_PROGRESS.md](MILESTONE_5_PROGRESS.md).
