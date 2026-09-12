## 1. Stub versioning (spec: ci-unit-test-jni)

- [x] 1.1 Extend `.gitignore` with `!app/src/test/jniLibs/*.so` and `!auto/src/test/jniLibs/*.so` re-include rules after the `*.so` exclusion; verify `git check-ignore` returns non-zero for the stub files and still ignores a random other `*.so`
- [x] 1.2 Copy `libosmscout_client_javad.so` from `app/src/test/jniLibs/` to `auto/src/test/jniLibs/` and verify both variants present in both modules and byte-identical to the app originals
- [x] 1.3 `git add -f` the four stub `.so` files and verify `git ls-files` lists them in both modules

## 2. Validation and CI verification

- [x] 2.1 Run `openspec validate` for the change and verify it passes
- [x] 2.2 Run local unit tests for the affected modules (`./gradlew :auto:testDebugUnitTest :app:testMobileDebugUnitTest`) and verify green — this proves the committed stubs behave exactly like today's local files
- [x] 2.3 Run the full `./gradlew test` suite locally and verify green
- [x] 2.4 Push; verify the CI run restores vcpkg deps from the NuGet feed and the unit-test step passes
- [x] 2.5 Verify the app APK contains no stub `.so` (unzip check on build output) and no `java.library.path`/AGP warnings in the build log
