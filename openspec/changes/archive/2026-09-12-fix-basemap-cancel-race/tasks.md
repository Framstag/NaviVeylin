## 1. Implementation (spec: basemap-cancel)

- [x] 1.1 In `BasemapManager.java` worker catch: report `"Download cancelled"` when `ad.isCancelled()` instead of `e.getMessage()`; verify the edit compiles (`:osmscout-client-java:compileJava`)
- [x] 1.2 Add post-loop `ad.isCancelled()` check that throws `InterruptedException("Download cancelled")` before extraction; verify compile
- [x] 1.3 Run `./gradlew :osmscout-client-java:test` and verify all 22 tests pass, no regressions

## 2. Validation and CI verification

- [x] 2.1 Run `openspec validate` for the change and verify it passes
- [x] 2.2 Stress the cancel test 20 times (`:osmscout-client-java:test --tests "*testCancelDownloadCleansUp"` loop) and verify zero failures
- [x] 2.3 Run full `./gradlew test` locally and verify green
- [x] 2.4 Push; verify the CI unit-test step passes (22/22 osmscout-client-java) and the whole build is green
