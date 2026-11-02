# Architecture

## Phase5.1 screen policy boundary

ScreenSessionCoordinator accepts SCREEN or SCREEN + FRONT_CAMERA, uses ephemeral identity-bound consent/attempt correlations and requires fresh platform consent. It gates RECORDING on projection ownership and a real muxed sample that includes every required source; interruptions are non-recoverable. ScreenOutputSize produces bounded even, non-upscaled candidates and rejects impossible aspect ratios; Android codec support is checked separately. The Android owner uses a typed foreground service, AVC encoder, atomic publication and bounded journal. Combined mode composes MediaProjection and front Camera2 external textures through GLES into the encoder surface in PiP or split layout. Camera access is requested at Start, camera foreground ownership is used only for combined requests, and camera loss stops capture. The journal writes screen-2 source requirements while reading screen-1 records. API36 AVD capture and decode passed for both layouts; physical acceptance is pending. Microphone and internal audio tracks remain unsupported. See [Phase5 design](PHASE_5_DESIGN.md) for platform constraints.

ReactionCapability separates advertised hardware from implemented paths: a front-camera feature does not imply a usable screen compositor, and a microphone does not imply an encoded audio track. Supported PiP/split layouts and audio sources must be explicit capabilities. Mode/layout/audio controls are disabled or gated by the same request validation used by the adapter. Permission discovery itself opens no camera and requests no permission.

## Phase 2 ownership
`:app` owns navigation and the CameraX adapter. UmbraCamApplication constructs a process-scoped CameraRecordingController; activity-scoped CameraStudioViewModel delegates to it. RecordingService is private, non-sticky and typed camera/microphone. Visible user Start, source permissions and notification access are required. A custom CameraX lifecycle stays resumed during accepted service capture. Preview disposal releases its surface without rebinding active Recorder.

Commands/callbacks serialize on Main; storage/journals serialize on one IO executor. Immutable attempt-specific notification Stop controls capture. Notification content uses a consumed one-shot activity intent. Task removal requests Stop. There is no boot receiver, sticky restart or background retry.

`:core:recording` remains pure Kotlin with StateFlow. Logical sessions have separate acquisition attempt IDs. Explicit same-lens/microphone recovery retains prior segments and rotates tokens; stale callbacks are rejected. RECOVERING is not RECORDING. Process-restored sessions are terminal and cannot reuse authorization.

## Lifecycle and finalization
Begin enters PREPARING; accepted service ownership acquires Recorder. Confirmed Start plus every required usable source enters RECORDING. Source loss immediately interrupts and requests Stop. Stop enters STOPPING. Finalize receipt fences publication; only validated, atomically published output becomes SAVED. A 20-second watchdog fails missing Finalize, but cannot release ownership during publication. Native IO stalls may leave Saving; timeout never fabricates success.

Service.onCreate promotes pending ownership even if immediate Stop canceled acquisition, satisfying the foreground-service deadline without acquiring Recorder for canceled requests. Attempt-specific teardown protects newer work.

Accepted capture can continue across Home, navigation and recreation. Unaccepted pending starts are canceled on visible-owner loss. Source errors, permission changes and disabled notification access stop capture. Recoverable terminal interruption permits explicit visible Resume. Process death never restarts capture. Latest device evidence is in PHASE_2_REPORT.md.

## Private storage and reconciliation
RecordingFileStore reserves pending MP4 after a 32 MiB free-space preflight. AndroidVideoInspector checks positive dimensions/duration and audio metadata. Same-filesystem atomic move publishes into no_backup/camera/recordings. Rejected output goes to quarantine; failed moves leave unpublished staging files. This is structural validation, not continuous playback certification.

RecordingSessionJournal stores bounded versioned properties under no_backup/camera/sessions, with UUID-only paths, file sync and atomic replacement. Critical preparing metadata precedes Recorder start. Explicit launch validates completed references and quarantines abandoned pending output, then records terminal interruption. Corrupt metadata warns without deleting media. Legacy recordings remain untouched. Published files survive journal-write failure with an honest warning. No catalog, repair, cleanup, Room or DataStore is added.

## Permissions and security
Manifest declares CAMERA, RECORD_AUDIO, FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, FOREGROUND_SERVICE_MICROPHONE and POST_NOTIFICATIONS. Camera and audio requests are point-of-use; audio is opt-in. Notification access is mandatory product policy. Microphone service type is used only when selected. Android privacy indicators remain intact. No network/storage/telephony/boot permission or MediaProjection implementation exists. AndroidX contributes an internal signature receiver permission.

Files are private and unencrypted; uninstall removes them. Backup/transfer exclusions remain. No FileProvider, export, playback, encrypted Vault, telemetry or backend exists. Package ID is provisional.

## Deferred boundaries
Keep app plus core:recording until implemented responsibilities justify extraction. CameraX Recorder serves single-camera and composed dual capture; no speculative MediaCodec abstraction. Dual detection is implemented in milestone 3. Phase 4 composition design is proposed; screen, sync and encryption remain later work.


Recovery readiness: coalesce pending binds, preserve existing bindings after terminal finalization, and avoid forced Retry on a ready camera. Non-error camera transitions during Preparing/Recovering remain bounded by the ten-second acquisition deadline; Recording requires positive encoded output and usable required sources. Explicit camera errors and source loss during active Recording still stop immediately.

## Phase 3 capability inventory
CameraSupportViewModel runs a read-only Camera2 metadata adapter on IO after explicit Check. Android 10 returns a version fallback without querying camera metadata. Android 11+ returns currently advertised concurrent groups and basic lens-facing labels. The pure core analyzer validates complete group membership and never derives pairs or support from lens count. No capture session, runtime permission request, foreground service or camera opening occurs in this path. Navigation retains the legacy safety route while displaying SOS Mode.

## Phase 4 proposed composition

See [PHASE_4_DESIGN.md](PHASE_4_DESIGN.md). Reuse pinned CameraX 1.5.3 concurrent composition with shared Preview/VideoCapture, one Recorder and existing service/storage ownership. Preserve the single-camera coordinator restriction. Prove independent progress observations for both inputs before implementation; composite bytes alone are insufficient. The dual contracts and initial portrait adapter/journal integration are implemented below; general transforms remain pending.

Phase 4.1 findings: exact-ID selectors also need explicit lens-facing constraints in pinned CameraX 1.5.3. Composition returns one public camera wrapper, so wrapper count is not source count. Use a fresh binding lifecycle identity when changing immutable layouts; unbindAll alone retained the earlier composition in the experiment. Both input callbacks advanced after preview detach; source denial closed both while Recorder finalized successfully, requiring separate coordinator loss warnings. These are emulator observations, not production integration. See [feasibility report](PHASE_4_FEASIBILITY.md).

## Phase 4.2 domain implementation

RecordingSessionTransitions now contains the existing shared state machinery behind the unchanged SingleCameraSessionCoordinator facade and its one-camera validator. DualCameraSessionCoordinator adds generation/identity tokens, exact-ID/open/advancing-frame readiness, monotonic acquisition/freshness checks, microphone gating and same-spec recovery. Its owner must serialize calls and schedule tick. Finalize claims publication once, rejects stale backend failures and exposes a separate storage-owner failure path. DualRecordingSpec holds immutable settings and final-frame normalized geometry. Phase4.3 implements the portrait binding/timers/service/journal path; general rotation/mirror transforms remain pending. See [domain report](PHASE_4_DOMAIN_REPORT.md).

## Phase4 provenance follow-up

The bounded emulator provenance gate is closed: per-device capture metadata and decoded regions independently follow primary/secondary exposure changes in PiP/split and both primary orders. This is a test-only CameraX control experiment, not a production adapter/control-handle design. Both source contributions are demonstrated without replacing feeds or bypassing eligibility. Phase4.3 adapter/service/journal integration is now implemented below; see [provenance report](PHASE_4_PROVENANCE_REPORT.md). Physical/rotation/performance acceptance remains open.

## Phase4.3 current ownership and persistence

CameraSessionCoordinator routes the preserved single facade and dual policy behind one stable process-owned session flow; existing RecordingService owns both. DualCameraBinding captures attempt/binding identity, uses exact independently advertised front/rear selectors with shared Preview/VideoCapture, and filters delayed callback receipts. A250ms owner tick enforces source freshness. Accepted capture retains the process lifecycle across Home/recreation/preview disposal. Initial dual shape is portrait/rotation0; unsupported settings reject rather than silently switching source or geometry. Final output dimensions/rotation are validated before publication.

RecordingSessionJournal retains its original one-argument/v1 single format and accepts a bounded v2 dual descriptor with multiple diagnostics. Reconciliation preserves descriptor/verified segments but restores no selected mode or token. Forced termination/relaunch confirmed a terminal warning, identical earlier bytes and no active service/camera. See [integration report](PHASE_4_INTEGRATION_REPORT.md). User controls remain Phase4.4; physical acceptance remains Phase4.5.

## Phase4.4 native controls

CameraStudioViewModel reads public camera metadata on Studio entry or explicit refresh; it never starts capture from Home. DualOptionsState derives only exact inventory-consistent two-camera front/rear groups. Production Material controls select immutable dual specifications through the existing controller, preserving Single lens/quality controls and the shared scrolling surface. Pair metadata is advisory; the adapter independently revalidates at bind. Busy/binding/service guards freeze selection, and stale inventory disables Dual Start. The microphone action requests permission at use and updates the selected specification; grant loss does not silently downgrade a requested dual audio source. See [controls report](PHASE_4_CONTROLS_REPORT.md). No new dependencies or capture owners were added.

## First beta: native screen capture and completed recordings

ScreenRecordingController is a process-level Main-thread owner backed by the pure ScreenSessionCoordinator. A separate non-sticky mediaProjection foreground service promotes only an accepted user request. Activity-owned correlation retains one chooser in memory across recreation; process death never restores consent. ScreenEncoder serializes AVC surface encoding and MP4 muxing on a HandlerThread, releases native resources before handing publication to serialized storage IO, and gates RECORDING on a real written sample. Display changes and revoked consent stop rather than silently resize or reuse authorization.

Screen files and bounded metadata journals use no_backup/screen, separate from existing camera v1/v2 journals. Both use the established reserve/inspect/atomic-publication/quarantine behavior. Camera Start/Resume and screen consent reserve mutually exclusive capture ownership. Library IO also blocks capture acquisition. A UI elapsed clock runs only after actual recording starts; it is not evidence of capture readiness.

RecordingCatalog enumerates only completed UUID MP4s under the two explicit recording directories, with canonical containment checks. RecordingsViewModel handles IO and per-operation exclusion. Inline native playback is lifecycle-bound. Android Create Document exports a copy without broad storage permission. Confirmed deletion updates terminal coordinator/journal references. There is no encryption claim or persisted export authorization. See FIRST_BETA_REPORT.md for current validation status.
