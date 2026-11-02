# UmbraCam Milestone 2 — single-camera recording

Milestone 2 uses the existing production Camera Studio path rather than creating a parallel demo flow.

Implemented and verified in the current codebase:

- Front and rear CameraX preview with capability-driven lens availability.
- Real start/stop recording through the existing foreground service and notification flow.
- Microphone permission requested at the audio switch; silent recording remains available when denied.
- Recording timer and explicit state reporting for preparing, recording, stopping, saved, interrupted, recovering and failed states.
- Dynamic CameraX quality selection from the qualities advertised by the bound camera; unsupported quality choices are not shown.
- Storage checks, staging/finalization, metadata validation, journal reconciliation and preservation of completed segments.
- Recovery controls require fresh source and permission checks; no automatic camera reacquisition occurs.
- Existing Recordings playback, metadata display, export and confirmed deletion.
- Thermal policy blocks unsafe starts and finalizes active capture when the OS reports severe or unknown thermal state.

Validation: the existing API36 emulator suite includes front/rear recording, microphone-track validation, lifecycle/Home finalization, recovery and thermal tests. The latest unit/lint gate passed 53 app tests plus the core recording suite, with lint reporting zero errors and 26 warnings. Physical Samsung evidence covers short front/rear and microphone tests on beta1; sustained physical qualification remains open after the documented thermal safety abort.

Not claimed: universal 4K/frame-rate support, pause/resume on every device, torch support on every lens, or beta2 physical installation while the phone is unavailable.

Next milestone after approval: reliability expansion around interruptions, screen lock, process termination and recovery qualification.
