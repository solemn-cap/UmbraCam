# Product specification

## Vision and users
UmbraCam — Capture Every Perspective. Protect Every Moment.
Native Android tools for creators recording camera footage and reactions, and users preserving important moments. Audience prioritization remains open; no emergency response or guaranteed evidence preservation is promised.

## Milestone scope
Phase 0 delivers an installable application shell, four clearly labeled feature previews, Settings, system light/dark appearance, saved navigation state, contracts, tests and documentation. It performs no recording, playback, encryption, biometric checks, export or permission requests.
Phase 1 was explicitly approved on 2026-10-30. Camera Studio implements a user-started single front OR rear camera session, optional microphone, supported quality selection, explicit Stop, structurally validated app-private output, callback-driven status and actionable errors. Basic capture/decoded-frame and lifecycle checks have passed on Samsung SM-S9080 / API 36 and a simulated API 29 target; see DEVICE_VALIDATION.md for exact scope. Broader device/failure/full-playback acceptance remains pending. Phase 2 supersedes the Phase 1 stop-on-leave policy: accepted recording uses a typed foreground service across recreation, navigation and Home. Latest verification is in PHASE_2_REPORT.md. Process death never restarts capture; recoverable interruptions need explicit visible Resume.

## Capability classification
| Classification | Planned capability | Qualification |
|---|---|---|
| Implemented; limited device validation | Single camera recording via CameraX, microphone capture | Basic FHD front/rear/audio tests passed on SM-S9080 / API 36; HD simulated checks passed on API 29; other profiles/devices remain unverified |
| Confirmed platform API | MediaProjection screen capture | System consent per session, service requirements, protected content exclusions |
| Confirmed platform API | Scoped local storage, Keystore, biometric APIs | API availability is not implementation or a security guarantee |
| Implemented; emulator validation | Concurrent front/rear cameras and portrait PiP/split | Exact advertised pair, Android11+, one HD video, optional microphone; physical compatibility/thermal acceptance pending |
| Device/content dependent | Playback audio capture | API 29+, allowed usage, same user profile and source-app policy; not all audio can be captured |
| Implemented; API29/API36 emulator validation | Silent screen + front camera, composited PiP/split | Both layouts and camera-loss finalization passed emulator tests; physical compatibility, sustained throughput and thermals remain pending |
| Implemented; verification deferred | Separate synchronized microphone commentary and policy-permitted internal playback audio in Reaction Studio | Each requested source must yield a muxed sample; playback remains limited by Android version, source-app policy and content protection. Mixing is deferred to Milestone 9 |
| Implemented in source; verification deferred | Free SOS Quick Record | Explicit action, rear camera, microphone off, standard permission/notification/thermal/storage checks, journaled kind and library label; no emergency contact or alerting |
| Implemented; Samsung validation | User-started service continuation and explicit recovery | Short physical continuation/recovery/screen-off checks passed; broader matrix open; no automatic restart |
| Future | Encrypted vault, biometric gate, metadata, playback, export | Not available until storage/security milestones |
| Future | Cloud backup, contact alerts, independent tracks, creator tools | Out of scope; no backend dependencies |

## Recording modes
Camera Studio: front OR rear camera and optional microphone, plus explicit Dual mode on Android 11+ when an exact front/rear pair is advertised. Dual currently records one portrait HD (720p) video, with rear main/front inset or rear above front. The selected pair is validated again at bind; unsupported devices retain Single camera. Settings freeze during capture. Emulator evidence does not certify physical dual-camera compatibility.
Reaction Studio: screen recording supports whole-display capture after fresh Android consent, with service continuation, playback, export and deletion through Recordings. Screen + front camera supports PiP and split composition through the GLES adapter, requires camera permission and readiness for both encoded sources, and stops if camera output is lost. Optional microphone and Android 10+ policy-permitted playback audio are encoded as separate synchronized AAC tracks; selecting both does not mix them. Physical audio acceptance and the deferred verification batch remain pending; microphone hardware does not guarantee capture, and protected or disallowed playback may be absent.
SOS Mode: Quick Record is an explicit user action routed through Camera Studio's existing camera/service path. It selects the rear camera, defaults microphone off, respects runtime camera/notification permission, thermal and storage gates, and records SOS kind in the private session journal and library label. It has standard Stop and notification controls, and no emergency-service contact or alerting. It cannot prevent OS termination or guarantee recording. Current implementation is awaiting the accumulated verification batch.
Private Vault: eventual encrypted local files, authenticated access, organization and explicit export. The Phase 0 preview stores nothing and is not a vault.

## Android restrictions
Minimum API 29 is provisional and aligns with future audio playback capture; physical validation is pending. Compile/target API 36 is a pinned stable baseline that satisfies the currently checked Play submission requirement. No local SDK was present initially; this is not a claim that API 36 is the newest Android platform release. Re-evaluate SDK/tool support before release.
Phase 1 declares CAMERA and RECORD_AUDIO, requested only at the corresponding user action in Camera Studio. Camera/autofocus/microphone hardware are optional manifest features; runtime checks determine supported operations. Phase 2 adds FOREGROUND_SERVICE, FOREGROUND_SERVICE_CAMERA, FOREGROUND_SERVICE_MICROPHONE and point-of-use POST_NOTIFICATIONS; recording requires notification access. MediaProjection remains deferred. There is no generic screen-recording runtime permission: use MediaProjection's consent flow. Avoid broad storage permissions; use app-private storage, MediaStore and user-selected destinations as appropriate.
Camera and microphone services must obey while-in-use and background-start rules. Screen capture consent cannot be reused to silently restart a revoked session. Protected content may be unavailable. Force-stop ends application work; reboot is not authorization to record.

## Official references (checked 2026-10-30)
- [Play target API requirements](https://developer.android.com/google/play/requirements/target-sdk)
- [Foreground service types and prerequisites](https://developer.android.com/develop/background-work/services/fgs/service-types)
- [MediaProjection consent and lifecycle](https://developer.android.com/media/grow/media-projection)
- [Audio playback capture](https://developer.android.com/media/platform/av-capture)
- [CameraX architecture](https://developer.android.com/media/camera/camerax/architecture)


