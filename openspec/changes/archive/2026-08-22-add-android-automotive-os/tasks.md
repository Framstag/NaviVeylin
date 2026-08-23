## 1. Manifest declarations (spec: android-automotive-os, design D1/D2)

- [x] 1.1 Verify `app/src/main/AndroidManifest.xml` contains the `com.android.automotive` metadata referencing `@xml/automotive_app_desc`; run `./gradlew :app:processDebugMainManifest` and grep the merged manifest
- [x] 1.2 Verify the `android.hardware.type.automotive` uses-feature is present with `required="false"` in the source and merged manifests
- [x] 1.3 Verify the packaged manifest (task output of the manifest-for-package step) contains both AAOS declarations; full `:app:assembleDebug` optional (native NDK build) unless a release APK is needed
- [x] 1.4 Update the AGENTS.md manifest-conventions documentation to mention the AAOS metadata + automotive uses-feature alongside the Android Auto declarations

## 2. No regression of projection declarations (spec: android-automotive-os "Android Auto projection declarations remain intact")

- [x] 2.1 Verify the merged manifest still contains `com.google.android.gms.car.application`, `NaviVeylinCarAppService` with `androidx.car.app.CarAppService` action and `androidx.car.app.category.NAVIGATION` category, and the template/access-surface permissions
- [x] 2.2 Verify `automotive_app_desc.xml` still declares `<uses name="template" />` and is referenced by both metadata entries
- [x] 2.3 Add `android:appCategory="maps"` (map/navigation category) on `<application>` for AAOS categorization; verify merged manifest contains it
- [x] 2.4 Add `androidx.car.app:app-automotive` dependency and bump app minSdk 28 → 29 (library requires 29; Android 9 dropped); verify `:app:compileDebugKotlin`
- [x] 2.5 Declare `androidx.car.app.activity.CarAppActivity` (exported, singleTask, `distractionOptimized`, no launcher filter) + `android.software.car.templates_host` uses-feature (required=false); verify merged manifest
- [x] 2.6 Add the automotive trampoline to `MainActivity` (detect `android.hardware.type.automotive` → launch `CarAppActivity` + finish; phone path unchanged); verify `AutomotiveDeviceTest` (phone false, automotive true via Robolectric shadow)

## 3. Validation

- [x] 3.1 Run `openspec validate add-android-automotive-os` and verify the change is valid
- [x] 3.2 Run `./gradlew test` and verify the unit test suite still passes (no code touched, no regression expected)
