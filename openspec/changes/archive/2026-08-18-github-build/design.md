# Design: GitHub Actions Debug Build

## Overview

Single workflow `.github/workflows/build.yml` on `ubuntu-latest` (x86_64). Builds `:app:assembleDebug` (all 3 ABIs) on push to `main`, pull requests, and manual dispatch. No secrets anywhere.

## Environment

| Component | Version | Source |
|---|---|---|
| Runner | ubuntu-latest | GitHub-hosted |
| JDK | Temurin 21 | `actions/setup-java` — matches `gradle-daemon-jvm.properties` (toolchainVersion=21) |
| Android SDK | platform 36, build-tools 36.0.0, NDK 27.0.12077973, CMake 3.22.1 | `sdkmanager`, cached |
| vcpkg | pinned commit `94a541197763a4f449a1b91478df48c0584a6256` | shallow fetch, binary-cached |
| Gradle | 9.6.1 (wrapper) | cached dist |

## Key decisions

1. **`org.gradle.java.home` override is mandatory.** `gradle.properties` commits a machine-local JDK path (`/usr/lib/jvm/java-17-openjdk`). Gradle still honors this property — verified locally that an invalid path fails the build. CI passes `-Dorg.gradle.java.home="$JAVA_HOME"` to override.
2. **`local.properties` written from env** (`sdk.dir`, `org.gradle.java.home`). Required by AGP and by `setup-vcpkg.sh` (which derives `ANDROID_SDK_ROOT`/`ANDROID_NDK_HOME` from it).
3. **vcpkg pinned to a commit.** The vcpkg binary-cache ABI hash includes the vcpkg tool version, so an unpinned daily clone would miss the cache every run. Pinning keeps the cache valid; bump the pin to refresh ports.
4. **vcpkg binary cache, not the vcpkg tree.** `VCPKG_BINARY_SOURCES=files,<dir>,readwrite` stores compiled packages (~0.5–1 GB) instead of the full vcpkg dir (4–7 GB with buildtrees/installed for 3 triplets). Stays well under the 10 GB repo cache limit alongside Gradle + SDK caches.
5. **SDK cached whole.** NDK 27 download is ~700 MB; cache the SDK dir keyed on component versions so only the first run downloads.
6. **Gradle caches + wrapper dists cached**, keyed on build scripts.
7. **No secrets.** Debug signing uses the auto-generated debug keystore; submodule and all dependencies are public.
8. **Submodules checked out recursively.** `libosmscout` is needed for stylesheets and native source. `initialize.sh` is NOT used — it rewrites `CMakeLists.txt` (dev-only bootstrap).
9. **Dependency gate after `setup-vcpkg.sh`.** The script tolerates per-package install failures (`|| echo`), so a failed install would otherwise pass silently. An explicit `.pc` verification step fails the job if any required package is missing for any triplet.

## Flow

```
checkout (submodules)
  → JDK 21
  → write local.properties
  → Android SDK: cache restore | sdkmanager install
  → vcpkg cache key (hash of setup-vcpkg.sh + vcpkg-overlays)
  → vcpkg binary cache restore
  → clone vcpkg @ pinned commit (shallow) + bootstrap
  → ./setup-vcpkg.sh (3 triplets, binary cache read/write)
  → verify vcpkg packages (.pc gate)
  → Gradle cache restore
  → ./gradlew -Dorg.gradle.java.home=$JAVA_HOME :app:assembleDebug
  → upload APK artifact
```

## Failure modes

- vcpkg port build failure → `.pc` gate or CMake configure fails → job fails
- Submodule not initialized → checkout step fails
- SDK component missing → sdkmanager install step fails
- Cache eviction (repo > 10 GB) → cold rebuild, still correct
