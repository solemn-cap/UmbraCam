# UmbraCam agent instructions

## Development rules
- Read README.md and docs/{PRODUCT_SPEC,ARCHITECTURE,DEVELOPMENT_PLAN,TESTING,DECISIONS}.md before implementing features.
- Follow existing architecture. Work on one milestone at a time; Phase 1 requires explicit approval.
- Avoid unnecessary refactoring and unrelated module changes. Preserve backward compatibility where practical.
- Justify dependencies and pin stable versions in gradle/libs.versions.toml; add only what the milestone needs.
- Add meaningful tests for new logic. Never fabricate test results or physical-device validation.
- Do not hardcode credentials or secrets; do not commit local.properties, signing keys or local tools.
- Handle Android permissions at the point of use. Respect camera/microphone privacy indicators and foreground-service restrictions. Never bypass MediaProjection consent.
- Never report RECORDING if any required capture source is unavailable. Preserve completed segments and report finalization failures honestly.
- Do not implement speculative empty modules or the full capture pipeline during Phase 0.

## Reporting rules
Report: (1) implementation, (2) created files, (3) modified files, (4) dependencies added, (5) exact tests executed, (6) results, (7) known limitations, (8) recommended next task.
If a test cannot run, state the missing requirement. Only claim physical-device behavior after testing on named physical devices.
