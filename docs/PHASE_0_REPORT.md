# UmbraCam — Phase 0 delivery

## A. Project summary
Created a Kotlin Android application foundation with Jetpack Compose and Material 3: Home, four clearly labeled previews, Settings, Back navigation, system dark mode and saved navigation state. No recording engine, media/runtime permission requests, foreground services, encryption or playback have been implemented. AndroidX contributes only an internal signature permission in the merged manifest. Package `com.umbracam.app` is provisional.

All project source files are new: the workspace was empty and contained no existing Git repository. No pre-existing application files were modified. Phase 1 has not started.

## B. Architecture summary
Two modules: `app` owns the Android/UI composition root; `core:recording` contains platform-independent capture source, request, error, segment and session types plus a StateFlow recording-engine interface. Session constructors enforce required-source availability for RECORDING, finalized segments for SAVED and an error for FAILED. A future transition table, adapter/pipeline design, storage/finalization strategy and permission/service rules are documented, not implemented. Empty feature/core modules, Hilt and persistence are deferred.

Direct dependencies added: Compose BOM/UI/Material 3/tooling; Activity Compose; Navigation Compose; Coroutines Core for the engine contract; JUnit; AndroidX Test runner/JUnit; Compose UI testing. All versions are centralized in `gradle/libs.versions.toml`. CameraX, Camera2 wrappers, Room, DataStore, biometrics and encryption dependencies are not yet needed.

## C. Actual source tree
Generated build output, local `.tools/`, Gradle caches and ignored design-tool session metadata are omitted.
```text
umbracam/
├── .gitignore
├── AGENTS.md
├── README.md
├── PRODUCT.md
├── DESIGN.md
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/umbracam/app/
│       │   │   ├── MainActivity.kt
│       │   │   ├── Destination.kt
│       │   │   ├── UmbraCamApp.kt
│       │   │   └── UmbraCamTheme.kt
│       │   └── res/
│       │       ├── drawable/ic_umbracam.xml
│       │       ├── drawable/ic_arrow_back.xml
│       │       ├── values/strings.xml
│       │       ├── values/styles.xml
│       │       ├── values-night/styles.xml
│       │       └── xml/data_extraction_rules.xml
│       ├── test/java/com/umbracam/app/DestinationTest.kt
│       └── androidTest/java/com/umbracam/app/NavigationTest.kt
├── core/recording/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/kotlin/com/umbracam/core/recording/RecordingContract.kt
│       └── test/kotlin/com/umbracam/core/recording/RecordingSessionTest.kt
└── docs/
    ├── PRODUCT_SPEC.md
    ├── ARCHITECTURE.md
    ├── DEVELOPMENT_PLAN.md
    ├── TESTING.md
    ├── DECISIONS.md
    ├── BUILD_VERIFICATION.md
    └── PHASE_0_REPORT.md
```

## D. Build results
Dependency resolution, Android compilation, debug APK packaging, unit tests, lint and instrumentation APK compilation succeeded. Final uncached lint: **0 errors, 9 dependency/tool-update warnings**, no suppressed checks. APK metadata confirms min API 29 / target and compile API 36 and the launcher activity; debug signature verification passed. See BUILD_VERIFICATION.md for exact commands, bootstrap attempts, checksums, warnings and final outcomes. Generated debug APK: `app/build/outputs/apk/debug/app-debug.apk`.

## E. Tests
Seven recording-contract JUnit tests and one app destination JUnit test passed (8 total; zero failures/errors). Four Compose instrumentation tests cover all Home section links and toolbar Back, Settings copy, system Back, and destination restoration after activity recreation. They compiled, but execution was blocked: connectedDebugAndroidTest exited 1 with `No connected devices!`. No temporal state machine exists, so no transitions are falsely claimed as tested. Full results are in BUILD_VERIFICATION.md.

## F. Limitations
Runtime navigation, recreation, rendering, TalkBack and installation/launch require an emulator/device; no connected target was found. Phase 0 implementation and build verification are delivered, but full runtime acceptance remains pending. Physical-device behavior has not been tested. API 29 minimum and future dual capture/background/synchronization/security behavior need validation. No recording, storage protection, emergency guarantee or release readiness is claimed. SDK/dependency pins are a conservative stable baseline, not a claim that every dependency is the newest release. Lint retains dependency-update notices for those deliberate pins.

## G. Next milestone — approval required
Phase 1: enumerate cameras and supported quality, request CAMERA only after user action, offer front OR rear CameraX preview/recording, make microphone optional with explicit permission, implement start/stop and finalized private output, surface errors honestly, then validate playable files and denial/interruption behavior on named physical devices. Add only the dependencies required for that work. Full plan and acceptance criteria are in DEVELOPMENT_PLAN.md. Do not begin camera implementation without approval.
