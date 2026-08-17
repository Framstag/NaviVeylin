# Tasks: GitHub Actions Debug Build

## 1. Create the workflow

- [x] Create `.github/workflows/build.yml`:
  - Triggers: `push` (main), `pull_request` (main), `workflow_dispatch`
  - `concurrency` with `cancel-in-progress: true`
  - Env: `VCPKG_COMMIT` (pinned), `VCPKG_ROOT`, `VCPKG_BINARY_SOURCES`, `ANDROID_HOME`, `ANDROID_SDK_ROOT`
  - Steps:
    - checkout with `submodules: recursive`
    - `actions/setup-java` Temurin 21
    - write `local.properties` from env
    - Android SDK: `actions/cache` + `sdkmanager` install (platform 36, build-tools 36.0.0, NDK 27.0.12077973, CMake 3.22.1)
    - vcpkg cache key (hash of `setup-vcpkg.sh` + `vcpkg-overlays/**`)
    - vcpkg binary cache (`actions/cache`)
    - clone vcpkg at pinned commit (shallow) + bootstrap
    - `./setup-vcpkg.sh` (3 Android triplets)
    - verify vcpkg packages (`.pc` gate)
    - Gradle cache (`~/.gradle/caches`, wrapper dists)
    - `./gradlew -Dorg.gradle.java.home="$JAVA_HOME" :app:assembleDebug`
    - upload debug APK artifact

## 2. Validate

- [x] Workflow YAML parses (`yq`)
- [x] No secrets referenced anywhere in the workflow

## 3. Verify after merge (manual)

- [ ] First run: cold vcpkg build succeeds for all 3 triplets, APK artifact uploaded
- [ ] Second run: vcpkg binary cache hit, build completes faster
- [ ] Push to `main` and a PR both trigger the workflow
