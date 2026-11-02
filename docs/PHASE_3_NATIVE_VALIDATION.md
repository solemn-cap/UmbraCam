# Phase 3 native validation

Date: 2026-10-31. Native functional verification and final visual review are complete at the emulator scope described below.

## 1. Implementation
Production app remains 0.4.0/code 4; no production code changed in this follow-up. Instrumentation waits for actual activity focus before injecting system Back. Camera support fixtures now capture the native Compose view buffer after focus, avoiding blank window-launch frames; added version fallback and loading/empty capture coverage.

## 2. Created files
This report. Ignored local artifacts: .tools/phase3-native-results.json, .tools/phase3-*.txt test logs, and native PNGs under .tools/phase3-captures/.

## 3. Modified files
app/src/androidTest/java/com/umbracam/app/{NavigationTest,CameraSupportTest}.kt. Phase 3 report, testing plan and design evidence are updated after final review.

## 4. Dependencies
No Gradle dependencies added or changed. Local validation uses the official AOSP Android 11/API 30 x86_64 default image, SDK repository revision11, archive x86_64-30_r11.zip. SHA1 verified: 5e4de3946d46f88856c35efcc4d797b381456347. Its embedded source.properties reports revision10; the repository metadata and archive checksum are retained in .tools. Dedicated AVD UmbraCam_Phase3_API30_AOSP; emulator37.2.13/WHPX,3072MB RAM,4vCPUs,SwiftShader,Vulkan disabled, synthetic front/rear cameras. SDK build: Android/sdk_phone_x86_64/generic_x86_64:11/RSR1.210722.013.A2/10067904:userdebug/test-keys.

## 5. Exact tests executed
Test APK rebuilt after instrumentation-only changes:

```powershell
.\gradlew.bat --offline --no-watch-fs --console=plain --max-workers=2 '-Dorg.gradle.jvmargs=-Xmx1024m -Dfile.encoding=UTF-8' '-Pkotlin.compiler.execution.strategy=in-process' :app:assembleDebugAndroidTest
```

Functional suite command (all commands target emulator-5558):

```powershell
adb -s emulator-5558 shell am instrument -w -r -e class 'com.umbracam.app.CameraSupportTest,com.umbracam.app.NavigationTest' com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

| Configuration | Result | Runner seconds | Log in .tools |
|---|---|---|---|
| Phone720x1280,320dpi,light,default1.0 text |13/13|21.163|phase3-aosp30-native-phone-light-final.txt|
| Phone720x1280,320dpi,dark,1.3 text |13/13|19.995|phase3-aosp30-native-phone-dark-1.3.txt|
| Tablet-sized1440x2560,320dpi,light,2.0 text |13/13|27.893|phase3-aosp30-native-tablet-light-2.0.txt|
| Phone720x1280,320dpi,light,2.0 text |13/13|18.476|phase3-aosp30-native-phone-light-2.0.txt|

The requested tablet override1600x2560 was capped by the runtime to1440x2560. Android configuration confirmed sw720dp,w720dp,h1256dp; this is a resized emulator, not physical tablet evidence.

After a review found blank first-launch fixture captures, CameraSupportTest's7 methods were rerun for recapture in the same configurations using:

```powershell
adb -s emulator-5558 shell am instrument -w -r -e class com.umbracam.app.CameraSupportTest com.umbracam.app.test/androidx.test.runner.AndroidJUnitRunner
```

All four recapture runs passed7/7:8.477s,8.019s,10.950s and8.267s respectively; logs are .tools/phase3-recapture-{phone-light,phone-dark-1.3,tablet-light-2.0,phone-light-2.0}.txt. This adds28 successful capture executions after the52 functional executions.40 fixture PNGs were validated, then inspected during the full native review. The earlier 61 JVM tests apply to the unchanged production code. The follow-up rebuilt the instrumentation APK, then reran lintDebug after the final helper change: BUILD SUCCESSFUL in 54 seconds, 0 errors and 22 warnings.

## 6. Results and evidence
52 successful functional executions across13 distinct methods. The real platform metadata query passed under application UID10130 while CAMERA and RECORD_AUDIO were denied. Android advertised an emulated front/rear concurrent group. No camera or microphone was opened or permission granted. Positive three-camera, no-group, empty, loading, access-error and Android10-version UI states are explicit synthetic fixtures; they do not establish hardware compatibility or an actual API 29 runtime result.

Manual native checks verified Settings→Camera support→Check, Open Camera Studio, and Camera Studio→Camera support. Two launcher captures show the supplied icon under the default and squircle masks. The mask overlay was restored; default phone size, light theme and effective1.0 font scale are restored after capture work.

Evidence: .tools/phase3-captures/aosp-phone-light/{home-steady,support-steady-top,support-steady-bottom,camera-studio-fallback,support-from-camera}.png; launcher-default.png and launcher-squircle.png at the capture root. Other configuration directories include settled Home and SOS views plus files/ native fixture captures. Old files/phase3-home.png images were sampled during launch fade; use home-steady.png for static Home review. Updated fixture captures are native Compose view images; settled production screens and launcher images are full-display Android screencaps.

Initial API 29/GoogleAPIs and API 36/GoogleAPIs boots failed or restarted system_server before app tests. API 36 reached boot-complete once but died before installation, so it is not acceptance evidence. AOSP API 30 stayed up. Early AOSP runs were obstructed by a System UI ANR dialog left from boot; after selecting Wait and confirming app focus, all functional runs passed. The initial12-test/13-test failures remain in diagnostic logs and are superseded, not hidden. Emulator root was used only for boot diagnostics/artifact export and restored before native test runs; the tested app retained its normal UID and denied source permissions. The user's Samsung phone was never used.

Final visual disposition: **ship**, scoped to the supplied native API 30 emulator production views and explicitly synthetic fixture states across all four configurations. No material production UI fixes were required. The documenter updated DESIGN.md without changing tokens or historical validation claims.

## 7. Known limitations
No new physical-device, actual dual-stream, thermal, quality, synchronization, real-call, OEM/power or true active-disk-full acceptance. API 29's version gate is covered by JVM logic and a synthetic native UI state; a new API 29 runtime check remains outstanding. TalkBack speech and physical gestures/performance remain untested. SOS is still a preview, and no monochrome themed-icon variant is included.

## 8. Recommended next task
Next work is Phase 4 dual-recording design, only after separate authorization. Compatible physical metadata/stream validation will still require explicitly available hardware; do not use the phone until availability is renewed.
