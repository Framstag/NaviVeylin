## Why

NaviVeylin has no CI. The build is complex — Gradle 9.6 + AGP 9.3, NDK 27, CMake 3.22, and a vcpkg cross-compiled dependency tree (cairo, pango, harfbuzz, protobuf, ...) for three Android ABIs — so regressions in the native toolchain, dependency bumps, or libosmscout submodule updates go unnoticed until someone builds locally. A GitHub Actions workflow that builds the debug APK on every change catches breakage immediately, with no secrets required.

## What Changes

- Add `.github/workflows/build.yml` — a GitHub Actions workflow that builds the debug APK (`:app:assembleDebug`, all 3 ABIs: arm64-v8a, armeabi-v7a, x86_64) on every push to `main` and on pull requests targeting it (plus manual `workflow_dispatch`).
- No secrets: debug signing uses the auto-generated debug keystore; every dependency (Android SDK, NDK, vcpkg, libosmscout submodule) is public.
- Caching:
  - vcpkg binary cache, with vcpkg pinned to a commit so the cache stays valid
  - Gradle caches + wrapper distributions
  - Android SDK components (platform 36, build-tools 36.0.0, NDK 27.0.12077973, CMake 3.22.1)
- Upload the built APK as a workflow artifact.
- No changes to app code, Gradle files, CMake, or native sources.

## Capabilities

### New Capabilities

None — this is a tooling change (CI configuration). No app behavior changes.

### Modified Capabilities

None.

## Impact

- New file: `.github/workflows/build.yml`
- No changes to `app/`, `core/`, `auto/`, `osmscout-client-java/`, Gradle, or CMake files
- CI-only environment: `ubuntu-latest`, JDK 21 (matches `gradle-daemon-jvm.properties` toolchain), Android SDK 36, NDK 27.0.12077973, CMake 3.22.1, vcpkg pinned to commit `94a541197763a4f449a1b91478df48c0584a6256`
- `skip_specs: true` — no spec-level behavior changes (pure tooling)
