# Tasks — fix-download-worker-error-reporting

- [x] 1. Add the red case `MapDownloadManagerWorkerFailureTest.aWorkerThatDiesOfAnErrorIsReportedThroughTheListener`
      (`osmscout-client-java/src/test/java/com/framstag/libosmscout/client/MapDownloadManagerWorkerFailureTest.java`):
      a real `MapDownloadManager` downloads an entry whose first native call cannot link, and the case asserts the
      listener's `onError` arrives. **Verify**: `./gradlew :osmscout-client-java:test -PforceTests --tests
      "com.framstag.libosmscout.client.MapDownloadManagerWorkerFailureTest"` fails on HEAD, XML
      `tests=2 failures=1`, `system-err` carrying `map-download-Berlin` —
      `evidence/red-test-head.xml`.
- [x] 2. Add the guard case `aWorkerThatDiesOfAnExceptionKeepsItsMessage`: a directory entry makes the worker throw
      an `IllegalArgumentException` with a message. **Verify**: it passes on HEAD (the `Exception` path already
      reports), so the fix cannot silently change an existing message; XML copied to `evidence/`.
- [x] 3. Fix `downloadMap`'s worker in
      `osmscout-client-java/src/main/java/com/framstag/libosmscout/client/MapDownloadManager.java`: keep the
      `InterruptedException` clause, replace `catch (Exception e)` with `catch (Throwable e)` reporting
      `failureMessage(e)` (`getMessage()` when non-null, else `toString()`); add the private static helper. **Verify**:
      the same `--tests` command as task 1 passes, both cases green, XML copied to `evidence/`.
- [x] 4. Run the gate `./gradlew test -PforceTests --rerun-tasks`; confirm 0 failures and 0 errors, and that no
      `:app`/`:core`/`:auto`/`:osmscout-client-java` result XML carries an uncaught `map-download-` line. **Verify**:
      per-module `tests=`/`failures=`/`errors=` tallies and the cited XMLs copied to `evidence/`.
- [x] 5. Documentation: no guideline change. `guidelines/Design.md` §4 already owns the rule this defect violates
      ("confined for **any throwable, not only exceptions**"), and this change applies it rather than introducing a
      new one. **Verify** (structural, no build): `sed -n '/^## 4\. Threading/,/^## 5\./p' guidelines/Design.md |
      grep -n "any throwable"` still shows the rule, and `git status` names no `guidelines/` file.
