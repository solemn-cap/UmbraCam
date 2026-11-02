# UmbraCam Milestone 4 — dual-camera production readiness

Readiness review started 2026-11-02 while the physical phone cools.

The current implementation already contains capability-gated concurrent-camera discovery, explicit advertised front/rear groups, frozen dual settings, PiP and split layouts, service-owned sessions, output geometry validation, dual journaling, and unsupported-combination rejection. Existing emulator evidence includes dual integration and thermal coverage; the named Samsung has short PiP/split evidence from beta1.

This pass tightened the backend contract with a single `DualRecordingSpec.isSupportedByInitialBackend` gate. The binding now uses that shared rule, so API level, portrait geometry, rotation, and the shipped HD SDR 30 profile cannot drift between UI eligibility and capture binding.

The next implementation gate is a focused dual-camera hardening pass:

- Re-run capability inventory and dual control tests on a clean API36 emulator when an AVD is available.
- Verify every advertised pair is mapped to a supported output profile before the Start action becomes enabled.
- Exercise PiP and split publication, source-loss finalization, microphone denial, rotation rejection, and thermal stop.
- Confirm the library exposes dual recordings with stable metadata and no duplicate publication.
- Repeat short PiP/split checks on the Samsung after it cools; do not start a sustained run until the thermal state is normal and a separate safety decision is made.

Current limitation: no emulator AVD is presently registered in the local SDK, so no new Milestone 4 runner was started in this pass. Existing verified emulator and Samsung evidence remains the baseline; no new physical or emulator pass is claimed here.

The next major roadmap slice has begun with Reaction Studio domain contracts in `core:recording`: explicit screen-only versus screen-plus-front-camera modes, PiP/split layouts, microphone/internal-audio capability gates, and source derivation. These contracts do not claim a combined capture implementation until the platform adapter is complete.
