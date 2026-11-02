# UmbraCam Milestone 3 — reliability and recovery

Milestone 3 baseline and implementation review completed 2026-11-02.

## Implemented

- Explicit recording state machines are shared by camera and screen flows and are independent of Compose screen visibility.
- Foreground services own active capture and expose user-visible notification stop actions.
- Home/background transitions retain an authorized camera session until explicit stop; task removal stops safely rather than silently reacquiring capture.
- Rotation and projection revocation stop screen capture, preserve valid partial output, and require fresh consent for a new projection.
- Camera and screen journals reconcile incomplete sessions on process recreation without restarting recording or reusing old authorization.
- Reconciliation preserves completed segments and exposes a recoverable interrupted state with explicit user Resume.
- Camera, screen, and dual flows validate source availability, notification permission, storage, metadata, and thermal status before acquisition and finalization.
- Unknown or severe thermal state stops active capture; cooling never automatically restarts it.
- Recording notifications update state and provide explicit stop controls.

## Verification

Core recovery and transition tests pass. The API36 emulator suite previously passed 14 tests, including screen rotation/revocation, camera thermal stop/cooldown, camera lifecycle/Home/notification finalization, and playback. The named Samsung beta1 session passed nine short native interruption and dual methods. The sustained physical run remains safety-aborted and beta2 is not installed on that phone.

## Remaining physical gate

Beta2 is now installed in place without wiping data. The first bounded runner was inconclusive: Android screen-share controls were unavailable to the harness and Camera Studio entry timed out in two tests. A manual supervised retry reached Camera Studio, started a real camera session, displayed the foreground notification, and stopped/finalized automatically at thermal status3. The UI reported the video saved locally, blocked retry while warm, and did not auto-resume. Screen timeout was restored to30000ms. This validates the thermal interruption/finalization path on the named phone; it does not qualify sustained performance or audibility. Remaining short checks are screen-lock, screen-off, process recreation, notification stop, and saved-media inspection. A sustained run requires a separate safety decision and must stop on thermal escalation.

## Exit status

The software reliability architecture and emulator coverage for Milestone 3 are complete. Physical qualification is partial and remains open. Milestone 4 should begin only after this physical gate is reviewed and explicitly approved.
