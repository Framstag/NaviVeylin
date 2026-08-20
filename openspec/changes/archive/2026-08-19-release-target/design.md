# Design — Release Target

## Context

See `proposal.md` — Why. Current state: `app/build.gradle.kts` hardcodes `versionCode = 19`, `versionName = "1.0.0"` in `defaultConfig`; no release pipeline. `buildFeatures.buildConfig` is **not** enabled, so `BuildConfig.VERSION_NAME` does not exist today. `AboutDialog.kt` reads the version via `PackageManager.getPackageInfo(...).versionName` (which reflects whatever AGP wrote into the manifest — the generated value flows there automatically once set in `defaultConfig`).

Constraint driving the design: AGP reads `versionCode`/`versionName` from `defaultConfig` at **configuration time** — values cannot be computed inside a task that runs later. Version generation must therefore happen at configuration time, gated on the release invocation.

## Goals / Non-Goals

**Goals:**
- One command (`./gradlew release`) produces a signed, Play-ready AAB with a fresh date-based `versionName` and monotonically increasing `versionCode`.
- Version state survives `clean` builds.
- Generated `versionName` readable by app code (`BuildConfig.VERSION_NAME`) and shown in the about dialog.
- Non-release builds (debug, etc.) are deterministic and never touch version state.

**Non-Goals:**
- Automated upload to Google Play (only artifact production).
- Version bumping on direct `bundleRelease` invocation (only the `release` task bumps).
- Multi-developer/CI release coordination — single release machine assumed.

## Decisions

### D1: Version state persisted in gitignored properties file next to build script
State lives in `app/release-version.properties` (Java `Properties`: `lastDate`, `runningNumber`, `versionCode`), added to `.gitignore`.

- **Alternative A — state in `build/` dir**: wiped by `clean` → `versionCode` would restart at 20 on every clean build → Google Play rejects "lower versionCode" → rejected.
- **Alternative B — derive from git history/tags**: no state file, but "running number per day" requires counting commits per day and breaks on amends/rebase; over-engineered for a single-dev project.
- **Alternative C — state in source-controlled file**: survives everywhere but creates merge conflicts and requires manual bumps on fresh clones.
- **Chosen**: A properties file outside `build/`, gitignored. First release build migrates from the current hardcoded 19 → `versionCode = 20`, `runningNumber = 1`. Fresh clones start clean at 20/1.

### D2: Bump happens at configuration time, gated on the `release` task being requested
A `release` task is registered with configuration-avoidance (`tasks.register`). Its configuration block runs the bump: reads state, computes `yyyy-MM-dd-N` (same day → `runningNumber + 1`, else reset to 1; `versionCode + 1`), persists, and returns the values for `defaultConfig`. Because the `release` task is only realized when requested, running `./gradlew assembleDebug` never realizes it and never bumps state.

- **Alternative A — bump inside task `doFirst`**: impossible — `defaultConfig` is already consumed by AGP at configuration time.
- **Alternative B — always bump at configuration**: `./gradlew tasks` or `assembleDebug` would burn version numbers → rejected.
- **Chosen**: gate on `gradle.startParameter.taskNames` containing `release`. Direct `bundleRelease` (without `release`) reuses the last persisted values — documented, avoids surprise double-bumps. Non-release builds fall back to fixed `versionName = "1.0.0"`, `versionCode = 19` (unchanged behavior).

### D3: `versionName` exposed via `BuildConfig.VERSION_NAME`
Enable `buildFeatures.buildConfig = true` (AGP 8 defaults it off) so `BuildConfig.VERSION_NAME` carries the generated value; `AboutDialog.kt` switches from the `PackageManager` lookup to `BuildConfig.VERSION_NAME`.

- **Alternative A — keep `PackageManager` lookup only**: works (AGP mirrors `versionName` into the manifest) but leaves no compile-time constant for other code and contradicts the existing `about-dialog` spec wording; also the spec explicitly names `BuildConfig.VERSION_NAME`.
- **Chosen**: BuildConfig as the primary source; manifest still carries the same value, so `PackageManager`-based readers stay consistent. No functional regression in the dialog's fallback (`"?"` retained for robustness).

### D4: Running number formatting
`yyyy` = 4 digits, `MM`/`dd` zero-padded 2 digits, `N` raw integer (no padding) — per spec. `LocalDate.now()` (device-local timezone) determines "today".

## Risks / Trade-offs

- [State file diverges across machines] → Play rejects lower `versionCode`, making the error loud; documented single-release-machine assumption. Fresh clone starts at 20/1, still above 19.
- [Failed release build still consumes a version number (bump happens pre-build)] → next attempt produces a new number; harmless for Play (unused numbers don't collide), just skips an integer.
- [User runs `bundleRelease` directly and ships an unchanged version twice] → documented: only `release` bumps; direct `bundleRelease` is for re-producing the same artifact.
- [Clock/timezone changes mid-day] → running number based on local date; a timezone shift back may reissue a number — accepted, same as any date-stamped build.
- [`BuildConfig` fields stripped by R8?] → `BuildConfig.VERSION_NAME` is a constant inlined at compile time; not affected by minification.

## Migration Plan

1. Add `release-version.properties` logic + `.gitignore` entry; remove hardcoded `versionCode`/`versionName` (fallback constants live in the version helper).
2. Enable `buildConfig`; switch `AboutDialog.kt` to `BuildConfig.VERSION_NAME`.
3. Verify: `./gradlew release` produces signed AAB; run twice same day → `-1`, `-2`; next day → resets; `assembleDebug` unchanged + state untouched.
4. Rollback: revert to hardcoded values; `release-version.properties` is inert if the release task is removed.

## Open Questions

None — remaining unknowns (e.g., Play console upload details, CI wiring) do not change specs, approach, or task breakdown.
