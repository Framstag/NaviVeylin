# Release Target

## Why

Releases are currently built ad-hoc with a hardcoded `versionCode = 19` / `versionName = "1.0.0"` in `app/build.gradle.kts`; every release requires manual editing and there is no repeatable, Play-ready artifact pipeline. A dedicated release target that derives the version from the build date and increments the version code automatically removes manual bookkeeping and produces an AAB suitable for upload to Google Play.

## What Changes

- Add a `release` Gradle target (`:app:release` or `release` task) that:
  - Generates `versionName` in the format `<yyyy>-<MM>-<dd>-<N>`, where `yyyy` is 4 digits, `MM` and `dd` are zero-padded to 2 digits, and `N` is the running number for that day with no leading zeros (increments per release build on the same day, resets to 1 on a new day).
  - Increments `versionCode` by one on each release build, persisted so it is strictly monotonic across builds.
  - Runs `:app:bundleRelease`, producing an `*.aab` signed (when `app/release.keystore` present) and suitable for upload to Google Play.
- Make the generated `versionName` available to the application (e.g., `BuildConfig.VERSION_NAME` or package metadata) so UI code can display it.
- Show the version number in the about dialog — the existing dialog already renders `Version <versionName>`; ensure it reflects the generated release version.
- Replace the hardcoded `versionCode`/`versionName` in `defaultConfig` with the generated values (fallback for non-release builds stays deterministic).

## Capabilities

### New Capabilities
- `release-target`: Gradle release build target that generates the date-based version name, increments the persisted version code, and produces a signed Play-ready AAB via `:app:bundleRelease`.

### Modified Capabilities
- `about-dialog`: The version display requirement changes — the about dialog SHALL show the generated release `versionName` (date-based format) sourced from the built app's version metadata; the stale "e.g., 1.0.0" example is replaced by the new format.

## Impact

- `app/build.gradle.kts` — replace hardcoded `versionCode`/`versionName`; add release task logic, `buildConfig` generation (or equivalent), and version-state persistence.
- New Gradle state file (e.g., `app/release-version.properties` or `build/`-scoped, gitignored) storing last build date, running number, and `versionCode`.
- `app/src/main/java/com/naviveylin/ui/about/AboutDialog.kt` — no functional change expected (reads `PackageManager` versionName), may switch to `BuildConfig.VERSION_NAME` per `about-dialog` spec.
- `openspec/specs/about-dialog/spec.md` — requirement text update.
- `.gitignore` — ensure version-state file is ignored.
- Build system: requires Gradle/AGP task wiring; no native (C++) or dependency changes.
