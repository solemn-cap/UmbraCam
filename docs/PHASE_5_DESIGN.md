# Phase 5 — Screen recording

Date2026-11-01. The user requested the next phase after the Phase4.5 emulator subset. Phase4 physical acceptance and its unmet emulator performance targets remain explicitly deferred, not passed. Phase5 begins with the implemented/tested5.1 consent/session foundation below; Android capture and user controls are subsequent checkpoints. Reaction Studio remains a preview until the Android integration and native validation are complete.

## Outcome and first capture scope

Record the whole display into one silent, app-private AVC MP4 after a visible user action and a new system consent result. Initial output candidate follows display aspect ratio with even-dimension rounding, never upscales, and caps the longer edge at1280pixels. Reject extreme aspect ratios whose scaled short edge would be below2pixels rather than distorting them. Codec capabilities must independently accept the dimensions/profile; this pure candidate is not a negotiated hardware profile. No camera overlay, microphone, playback audio, encryption, playback/export UI, background consent request or automatic restart belongs to the initial screen slice. Audio/overlay work stays in its owning milestones.

The initial Android adapter should request full-display capture explicitly on API34+ using the platform's default-display configuration, and say “whole screen” in the UI. Single-app sharing and dynamic encoder resizing are separate follow-up scope; never silently substitute one sharing scope for another. Protected/secure content remains controlled by Android and can be blank. No accessibility, root or capture-consent bypass is acceptable.

## Platform constraints checked against primary sources

Android requires consent for each projection session and restricts reuse of grants/virtual displays on modern releases. Keep returned data only in memory, bind it to one request and attempt, and discard cancelled, orphaned or already-consumed results. Register projection callbacks before creating the virtual display. Revocation invalidates the projection and requires resource release. These requirements are described in the [MediaProjection guide](https://developer.android.com/media/grow/media-projection).

After consent, promote a private non-sticky `mediaProjection` foreground service before obtaining the projection. Declare the corresponding foreground-service permission/type when the Android adapter is added; retain the existing notification-access policy. This sequence follows the [foreground-service type requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#media-projection). Never start capture from boot or reuse a service restart as authorization.

## Concrete backend choice for feasibility

Use platform MediaProjection→VirtualDisplay→surface-input AVC MediaCodec→MediaMuxer. The [MediaCodec reference](https://developer.android.com/reference/android/media/MediaCodec) defines surface input, output callbacks and end-of-input signaling; the [MediaMuxer reference](https://developer.android.com/reference/android/media/MediaMuxer) defines track/format and sample writing. No dependency is needed. Keep codec/muxer operations serialized on a dedicated worker; route identity-bound state transitions to Main. Select and validate a supported surface-input codec/profile before consuming consent. A successful setup/start return or growing header is insufficient for RECORDING; require a real non-config encoded sample written to the started muxer while projection ownership remains valid.

Stop signals encoder EOS, drains pending output and releases the projection/virtual display/surface/codec/muxer exactly once. A bounded finalization watchdog can fail a missing callback but cannot steal a file already owned by publication. Reservation, inspection, journal work and atomic publication run off Main. A muxer failure or empty/unverified output remains unpublished or quarantined with an honest failure. Preserve prior completed media.

## Ownership and integration boundary

Keep `app` plus `core:recording`. Reuse the shared session transitions and concrete file store/inspector rather than moving existing camera classes or replacing the working CameraX pipeline. The camera coordinator still accepts only its existing source sets. Screen policy accepts exactly SCREEN and exposes no same-grant recovery operation.

Add one process-level mutual-exclusion gate when integrating Android owners. Camera Start/Resume and screen consent/acquisition must not overlap; waiting for consent reserves the screen request until its result/cancellation. Do not instantiate a second camera provider or permit independent recorders to compete. The screen service is separate and correctly typed so existing camera notification/session behavior stays backward compatible. Its Stop intent is immutable and attempt-specific; notification opening uses a consumed one-shot route request.

Register the consent ActivityResult handler at stable activity ownership, not only in a transient navigation destination. Retain only the in-flight request correlation across activity recreation. After process death the new coordinator has no request, so any orphaned result is discarded without calling getMediaProjection. No Intent, binder, projection, consent request or runtime token belongs in a journal, saved state or preferences.

Keep the correlation belonging to the launched chooser separate from any later UI request: an old chooser result must never be paired with the newest pending request. Permit only one actual chooser in flight until its result is delivered, even if the app cancels its logical request first. Start foreground acquisition only while the initiating activity is resumed; if visible ownership is lost before acceptance, discard the unused result rather than starting from the background. Stop during pending service promotion still needs Android's promotion deadline fulfilled, but must not create an encoder or virtual display after cancellation; preserve the existing camera race fix when designing this screen path.

Use a separate `no_backup/screen/{pending,recordings,quarantine,sessions}` namespace with the existing atomic file-store behavior. A concrete bounded screen journal should persist metadata/verified references only, leaving camera v1/v2 formats untouched. Reconciliation quarantines abandoned pending files and restores only terminal interruption context. Screen recovery always means a new visible Start and system consent; completed segments are never authorization.

## Rotation, visibility and readiness

Lock the candidate output geometry for each first-slice attempt. A display rotation/size change stops and finalizes with an explanatory warning and requires a new consented session. Do not create a second virtual display using the old grant. Modern captured-content resize callbacks and legacy display notifications need coverage; an initial callback matching the chosen geometry is not a rotation. Check reported dimensions/rotation before publication.

Projection revocation, explicit codec failure or unusable surface immediately removes RECORDING and requests finalization. Ten seconds bounds acquisition; late readiness events check that deadline before becoming active. A static screen is valid content and can produce no new encoded frame: unlike two independent camera inputs, lack of image changes alone cannot prove screen-source loss. The platform projection callback and encoder failure are the source-authority signals. This does not promise a real-time OS callback deadline or prove continuous playback.

## Implemented checkpoint5.1

`ScreenSessionCoordinator` implements opaque per-owner consent/attempt correlations, cancelled/foreign/replayed result rejection, screen-only source gating, projection-plus-real-sample readiness, deadline checks, Stop/late-event ordering, explicit non-recoverable revocation, stale-attempt rejection, once-only finalization/publication ownership, preserved warnings and terminal restoration. These objects correlate callbacks; they are **not** Android permissions. The future adapter must enforce real system consent and actual encoded-sample provenance.

`ScreenOutputSize` implements bounded even output candidates without upscaling. Shared transitions gain a non-recoverable interruption overload while preserving the original two-argument behavior for camera callers. No Android service, manifest permission, encoder, UI or output file is introduced in5.1. This is implemented domain behavior, not runtime screen capture.

## Next checkpoints and exit criteria

| Checkpoint | Required concrete result |
| --- | --- |
| 5.1 policy/design | Consent identity, source/sample readiness, Stop/races, publication ownership and geometry tests; existing camera tests unchanged |
| 5.2 Android feasibility/integration | Real system chooser, typed foreground owner, one virtual display, playable private output, revocation/EOS/failure/journal/mutual-exclusion checks |
| 5.3 user controls | Extend existing restrained Material3 Reaction Studio with truthful whole-screen/silent/output limits, explicit consent/start/stop/status/retry; native light/dark/large-text and independent review |
| 5.4 acceptance | API29/30 and API34+ real consent cancellation/grant/reuse rejection, OS stop, resize/rotation, secure-content exclusion, Home/recreation/process death, preserved earlier media; named physical acceptance kept separate |

Use the isolated emulator only while the phone is unavailable. Instrumentation may activate the real chooser on the project emulator but must not grant projection AppOps or fabricate a result token to skip it. Compare decoded output with controlled visible content, including independently changing frames; track metadata alone is not screen-provenance proof. Preserve camera and dual regression evidence. Do not mark Phase5 complete at this domain checkpoint.
