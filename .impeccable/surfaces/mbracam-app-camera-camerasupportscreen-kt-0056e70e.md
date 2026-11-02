---
version: 1
slug: "mbracam-app-camera-camerasupportscreen-kt-0056e70e"
primary_target: "app/src/main/java/com/umbracam/app/camera/CameraSupportScreen.kt"
related_targets: ["app/src/main/java/com/umbracam/app/UmbraCamApp.kt"]
---

# Camera support extension

Mode: Operate. Keep the established Material 3 theme, typography and scrolling layout.

## Direction contract
THESIS: Give a clear device capability answer before a user expects two cameras; report advertised groups without offering dual recording.
OWN-WORLD: Existing system light/dark Material colors, type roles, 24dp screen padding, standard text buttons and dividers. The supplied eye/lens PNG is the launcher identity.
STORY: From Camera Studio or Settings, open Camera support, request a metadata check, read the result and use the single-camera fallback. No camera or microphone is opened by the checker.
FIRST VIEWPORT: Native top app bar and Back, one brief explanation, Check support action, then the result heading and available groups. Errors and Android-version fallback offer another check or Camera Studio. Results remain readable at large font sizes.
FORM: Precisely scoped code-led extension, not a new visual world or an open concept round. Preserve the incumbent component vocabulary.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
