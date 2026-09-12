## Why

The GitHub Actions build fails its unit-test step: `:app:testMobileDebugUnitTest` and `:auto:testDebugUnitTest` fail with `java.lang.UnsatisfiedLinkError` at every test that instantiates a fake libosmscout client. The host-compiled JNI stub `.so` files in `app/src/test/jniLibs/` and `auto/src/test/jniLibs/` are matched by the `*.so` gitignore rule and were never committed — they exist only on the developer machine. Fresh clones (CI) get no stub, so `OSMScoutClient`'s static `System.loadLibrary` cannot resolve the library and every test touching it dies. This was masked by CI history: the run before last failed at Kotlin compile before tests executed, and the test-execution step itself is recent.

## What Changes

- Commit the test-only JNI stubs so CI has them:
  - `app/src/test/jniLibs/libosmscout_client_java.so` (existing)
  - `app/src/test/jniLibs/libosmscout_client_javad.so` (existing fallback variant)
  - `auto/src/test/jniLibs/libosmscout_client_java.so` (existing)
  - `auto/src/test/jniLibs/libosmscout_client_javad.so` (new — copied; the loader tries `osmscout_client_java` then `osmscout_client_javad`, and AGENTS.md notes the missing fallback makes full-suite runs flaky)
- Extend `.gitignore` with re-include (`!`) rules for those two directories' `.so` files so the stubs stay versioned while other `*.so` artifacts remain ignored.
- Update AGENTS.md's JNI stub section to state the stubs are committed (test source set only, never shipped to the app).
- No change to `OSMScoutClient.java`, the app, or any production build.

## Capabilities

### New Capabilities

- `ci-unit-test-jni`: JVM unit tests in the CI build can load the libosmscout JNI stub — every module whose tests trigger `OSMScoutClient`'s static `System.loadLibrary` has the stub on its test `java.library.path`, in the repository, in both loader-name variants.

### Modified Capabilities

- None.

## Impact

- `.gitignore` — two re-include rules.
- `app/src/test/jniLibs/*.so`, `auto/src/test/jniLibs/*.so` — newly versioned binary files (~15 KB each, x86-64 host ELF, no symbols).
- `AGENTS.md` — one paragraph in the JNI stub section.
- New OpenSpec change artifacts.
- CI: unblocks unit tests, which then run against the vcpkg NuGet feed restored deps (previous change `fix-vcpkg-ci-cache`).
- Rollback: revert the commit; tests fail in CI again as before.
