## Why

The GitHub Actions build rebuilds all vcpkg native dependencies from source on nearly every run (~60 min), because the binary cache is invalidated by runner image updates and never recovers. libosmscout hit and fixed the identical bug (see Framstag/libosmscout `vs-vcpkg-cache-fix`): `actions/cache` restores stale archives on an exact key hit, vcpkg rejects them all, and the post-step never re-saves — so every subsequent run rebuilds. NaviVeylin uses exactly that rejected design (`VCPKG_BINARY_SOURCES=files,<dir>` + `actions/cache`).

## What Changes

- Replace the files-based vcpkg binary cache with a **NuGet binary cache on GitHub Packages** (`nuget.pkg.github.com/Framstag`, same org feed libosmscout already uses): `VCPKG_BINARY_SOURCES=clear;nuget,https://nuget.pkg.github.com/Framstag/index.json,readwrite`.
- vcpkg stores each compiled port as a NuGet package **versioned by its ABI hash**; restore matches per package, so a runner image/toolchain update rebuilds exactly once and the cache self-heals.
- Grant the workflow job `permissions: packages: write` (plus `contents: read`, `actions: write` for checkout/cache).
- Install `mono-complete` on the runner — `nuget.exe` (fetched via `vcpkg fetch nuget`) needs mono, and ubuntu-24.04/latest images no longer preinstall it (runner-images#10636).
- Register the GitHub Packages NuGet source with the run's `GITHUB_TOKEN` (`sources add` + `setapikey`), config written to the user-level NuGet config, not the checkout.
- Remove the `Compute vcpkg cache key` and `Cache vcpkg binary cache` steps; the `VCPKG_COMMIT` pin and `.pc` verification gate stay.
- Add `NuGet.config` to `.gitignore` (defensive; token lives in the runner-local user config, not the repo).
- Update AGENTS.md and guidelines/Build.md where they document the old cache-key mechanics.

## Capabilities

### New Capabilities

- `ci-vcpkg-cache`: vcpkg dependency caching in the GitHub Actions build — restore between builds, self-heal after toolchain/runner-image changes with exactly one rebuild, and survive dependency-list changes without full rebuilds.

### Modified Capabilities

- None.

## Impact

- `.github/workflows/build.yml` — job permissions, mono install step, NuGet source registration step, `VCPKG_BINARY_SOURCES` env change, removal of the old bincache steps.
- `.gitignore` — one line.
- `AGENTS.md`, `guidelines/Build.md` — CI cache documentation.
- `setup-vcpkg.sh` — **unchanged** (env-driven; still installs the same DEPS for the 3 Android triplets).
- No source code, API, or dependency-lists change; no manifest conversion.
- CI runtime: ~60 min saved per run when the feed is warm; one full rebuild + feed push after each ABI-relevant runner image update.
- Storage: GitHub Packages NuGet, free for public repos, per-port ABI-versioned entries (~250 MB per generation).
- Fork PRs: restore only (read-only `GITHUB_TOKEN`); push happens on main-branch runs. No worse than today.
- Rollback: revert the workflow edit; feed packages can be deleted from GitHub Packages.
