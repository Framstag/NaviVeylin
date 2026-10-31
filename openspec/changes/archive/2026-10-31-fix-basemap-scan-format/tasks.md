# Tasks

## 1. Native client: the scan leaves out a directory this build does not read (submodule)

- [x] 1.1 Red case first in `app/src/main/cpp/libosmscout/Tests/src/MapManagerTest.cpp`: a helper
  `CreateDatabaseWithFormatVersion(dir, version, byteCount)` that writes every mandatory file and the
  little-endian version into `types.dat`, and the case "A scan skips a database whose data format this build
  does not read" — one unreadable-version directory beside one at `FILE_FORMAT_VERSION-1` and one at `+1`; the
  scan must publish exactly the unreadable one. Evidence: `evidence/native-red.txt` (exit 1,
  `3 == 1` where 1 was required, `9 test cases | 1 failed`).
- [x] 1.2 The second case, "A database whose data format version cannot be read stays published": a matching
  version beside a `types.dat` truncated to two bytes; both published. It passed on HEAD too — it pins D2
  against a fix that would over-refuse.
- [x] 1.3 The scan in `libosmscout-client/src/osmscoutclient/MapManager.cpp`: a file-local helper
  `ReadDatabaseFileFormatVersion()` returning `std::optional<uint32_t>` (catches the reader's exception, logs
  at debug level), and the version check before the `MapDirectory` construction — mismatch → `invalidCount++`,
  one `Warn` naming the directory and both versions, `continue`. Evidence:
  `evidence/native-green.txt` (`All tests passed (43 assertions in 9 test cases)`; both skip lines quoted).
- [x] 1.4 Revert-check in both directions: with the fix stashed the new case fails (`3 == 1`) and every other
  case stays green; with the fix restored all 9 cases pass. The md5 of `MapManager.cpp` reads back
  `0138c6880e7b0120c74eebb9937b5cbc` before and after the stash cycle.
- [x] 1.5 Commit the patch on `naviveylin-local` (`e4ec10400 client: the map scan leaves out a database whose data
  format this build does not read`), then bump the gitlink in the main repo and confirm the pairing: `git ls-tree
  HEAD app/src/main/cpp/libosmscout` reads `e4ec10400156552b8be54d6c1d665827d60a6b65`, identical to the submodule's
  `git log -1`; the submodule tree is clean. `git ls-remote origin naviveylin-local` read `b5d045eb4` immediately
  before the push and `e4ec10400` after it.
- [x] 1.6 Recorded the patch as an upstream PR candidate: `naviveylin-local`'s PR into `master` (#1773) carries the
  commit, and the bump recipe now names the file and its two cases —
  `.pi/skills/update-to-current-libosmscout-master/SKILL.md`, "Expect these shapes", a `MapManager.cpp` entry
  saying to keep the version read and the skip when upstream reworks the same scan, and to re-run
  `meson test -C hostbuild "Check map manager"` after such a merge.

## 2. Gate for the submodule bump (app side)

- [x] 2.1 Per-module suites rather than the aggregate `./gradlew test`
  (`./gradlew :app:testAutomotiveDebugUnitTest :auto:testDebugUnitTest :core:testDebugUnitTest`):
  `auto` 910 tests, `core` 653 tests, 0 failures/0 errors; `:app` 2219 tests, **2 failed**, and both failures are
  the uncommitted guideline split's, not this change's — `KoverDeprecationBaselineTest` (asserts
  `guidelines/Build.md` §7's engine table, which the split moved to `guidelines/BuildEvidence.md:238`) and
  `ReroutePolicyGuidelineParityTest` (asserts the bullet `- Phone/AA reroute parity` in `guidelines/UI.md`, which
  the split left nowhere). Attribution: with only those two files restored from `HEAD` (stash cycle, md5
  `9ff9447513aa9b6bd29df755024c3b8b` / `4bab9ee83e7623fcb6225bfdd004daf3` identical afterwards) both cases pass and
  the task turns `BUILD SUCCESSFUL`. This change touches `app/src/main/cpp/libosmscout` only — `git show --stat
  HEAD` names that one gitlink line.
- [x] 2.2 Flavor assemble for the device check: `:app:assembleMobileDebug -Pandroid.injected.build.abi=x86_64` —
  `BUILD SUCCESSFUL in 13s`, 23 tasks executed, `configureCMakeDebug[x86_64]` and `buildCMakeDebug[x86_64]` ran.
  The packaged library is proven, not assumed: the APK's `lib/x86_64/libosmscout_clientd.so` and the freshly
  stripped native library both read `ac77bd416a6883d25e7c964ad0c8917f` (`guidelines/BuildEvidence.md` §4f).

## 3. On-device verification

- [x] 3.1 With a basemap whose `types.dat` names another version in place under `files/maps/basemap/basemap/`
  (all 19 mandatory database files, version 26), one `adb logcat -s NaviVeylin` start on `emulator-5554`:
  evidence `evidence/device-red-then-green.txt`. Pre-fix APK: the scan publishes `2 candidates, 2 valid` and the
  reader prints `does not have the expected format version! Actual 26, expected: 27` plus `Cannot open db '…'!`.
  Fixed APK: one `skipping map directory whose data format version is 26, this build reads 27: …` line,
  `2 candidates, 1 valid, 1 invalid`, `Total installed maps found: 1`, and over the whole 46-line session log
  `0` format-version errors and `0` `Cannot open db` lines while the regional map stays published.

## 4. Documentation

- [x] 4.1 Stated the scan's rule where the project keeps its native facts: one bullet in `guidelines/DesignNative.md`
  §5b beside "the two database-open entries differ on purpose" — the version read before the `MapDirectory`
  construction, the skip with both versions in one warning, the unreadable version left to the reader, and the
  reason a basemap path exemption cannot replace it (design D1/D2). The section's measurement moved with it
  (`tools/check-doc-routes.sh`: §5b now 6.5 kB, `AGENTS.md`'s two rows updated; the check reports all 325 section
  references resolving).
- [x] 4.2 Reported #716 with the evidence lines and the submodule commit (`tools/issues/close.sh 716
  --comment-only`), leaving the closure to a person; the archived change answers D6 of
  `2026-10-30-fix-basemap-data-format-admission`, whose note this archive carries.
