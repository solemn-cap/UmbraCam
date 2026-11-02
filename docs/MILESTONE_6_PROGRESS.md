# UmbraCam Milestone 6 — SOS Mode progress

## Implementation follow-up — verification deferred

SOS Mode now offers an explicit **Quick Record** action. It opens Camera Studio's existing user-owned capture screen, selects a single rear camera, disables microphone capture, and starts through the same CameraX, foreground-service, notification, thermal, permission, storage and finalization checks as ordinary camera recording. It waits for camera readiness and any required permission/notification grant before issuing Start. It does not auto-start from Home, send an alert, contact emergency services, or claim guaranteed capture.

Recording sessions carry a `RecordingKind` value. SOS uses the version-3 camera journal format; readers remain compatible with version 1/2, and reconciliation retains the kind without restoring active capture. The Recordings catalog reads completed SOS segment IDs from the private journal and displays an SOS label. SOS remains a free core path and does not consult premium entitlements.

Meaningful tests were added for SOS state retention through finalization, version-3 journal reconciliation and library labeling, navigation/copy, and an instrumented Quick Record capture flow. **No build, test, lint, emulator or phone command has been run** per the user's request to accumulate validation until Milestone 9. The SOS feature is therefore implemented in source but not compiled or accepted.

## Remaining acceptance

- Run the accumulated host and emulator suite after Milestones 7–9, including SOS permission denial, notification denial, unavailable camera, thermal/storage failures, quick start/stop, journal migration/reconciliation, library labeling, Home continuation and notification Stop.
- Physical-device behavior and thermal impact remain unverified. Only claim them after a named device completes the final acceptance flow.
- The beta2 APK predates this source change and does not include SOS Quick Record.
