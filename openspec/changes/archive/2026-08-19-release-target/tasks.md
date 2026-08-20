# Tasks — Release Target

Specs: `specs/release-target/spec.md`, `specs/about-dialog/spec.md` (delta). Design: `design.md`.

## 1. Version state and generation (spec: release-target R1, R2, R5; design D1, D2, D4)

- [x] 1.1 Add `app/release-version.properties` (Properties: `lastDate`, `runningNumber`, `versionCode`) and add it to `.gitignore` (design D1)
- [x] 1.2 Implement pure version logic in `app/build.gradle.kts`: format `versionName` as `yyyy-MM-dd-N` (4-digit year, zero-padded month/day, no leading zero on `N`); same `lastDate` → `runningNumber + 1`, new date → reset to 1; `versionCode + 1` (design D1, D4)
- [x] 1.3 Register `release` task via `tasks.register`; bump state and feed `defaultConfig` only when `gradle.startParameter.taskNames` contains `release`; non-release builds keep fixed `versionName = "1.0.0"` / `versionCode = 19` and never touch state (spec R5; design D2)
- [x] 1.4 Wire `release` task to depend on `:app:bundleRelease`; keep existing release signing config (keystore present → signed AAB, absent → warning, still produces AAB) (spec R3)

## 2. Version availability and about dialog (spec: release-target R4; about-dialog delta; design D3)

- [x] 2.1 Enable `buildFeatures.buildConfig = true` in `app/build.gradle.kts` (design D3)
- [x] 2.2 Update `AboutDialog.kt` to read the version from `BuildConfig.VERSION_NAME` instead of `PackageManager.getPackageInfo(...).versionName`, keeping `"?"` fallback (about-dialog delta; design D3)

## 3. Verification

- [x] 3.1 Add Robolectric Compose unit test (mirror `RoutePanelComposeTest.kt` setup) asserting the about dialog renders the version string from `BuildConfig.VERSION_NAME` (about-dialog delta; note: date-format logic lives inline in the Gradle DSL where JVM unit testing is not feasible — covered behaviorally by 3.2)
- [x] 3.2 Behavioral verification: run `./gradlew release` twice on the same day → second `versionName` running number increments (e.g., `-1` then `-2`) and `versionCode` increments by one; AAB exists in `app/build/outputs/bundle/release/`; run `./gradlew assembleDebug` twice → `release-version.properties` unchanged (spec R1, R2, R5)
- [x] 3.3 Verify fresh-start migration: delete `release-version.properties`, run `./gradlew release` → starts at `versionCode = 20`, running number `1` (spec R1, R2; design D1)
- [x] 3.4 Full debug build compiles without errors: `./gradlew :app:assembleDebug` (app spec)
- [x] 3.5 Existing tests still pass: `./gradlew test` (app spec)
- [x] 3.6 `openspec validate release-target --type change` passes
