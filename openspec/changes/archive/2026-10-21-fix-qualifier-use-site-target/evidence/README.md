# Evidence — fix-qualifier-use-site-target

Every file here was produced in the change root (`.pi/worktrees/loop`) on base commit
`3211d4d8b395c61041dfccff9330e754dfb3499b`, on 2026-10-20/21 (local date 2026-10-21; the XML timestamps are
UTC). Files produced before the change's first review are marked; the second round re-took them because the
recorded XMLs came from a guard whose scan surface excluded the package it lives in
(`sources=716`), and because its two `javap` dumps were one classfile taken twice.

| file | what it is |
|---|---|
| `head-compile-warnings.log` | round 1: `:app:compileMobileDebugKotlin :app:compileAutomotiveDebugKotlin --rerun-tasks --no-build-cache -Pkotlin.incremental=false` on HEAD (before the fix): the `value parameter only … field` family appears twice, both at `NavigationNotificationController.kt:36:5` — one per flavor, because both flavors compile the same `app/src/main/java` |
| `post-fix-compile-warnings.log` | the same command on the fixed tree: `BUILD SUCCESSFUL in 18s`, both compile tasks executed, **0** lines of the family, and the tree's other warnings still arrive (2 lines, the `LocationMarkerOverlay.kt:165` deprecation, one per flavor) |
| `red-run-round2.log`, `red-run-round2.started` | the red run of the final guard: revert-check mutation = HEAD's bare line; `QualifierUseSiteTargetTest > no qualifier … FAILED`, `2 tests completed, 1 failed`, start/end stamps `23:01:52`–`23:02:07` |
| `red-run-round2.xml` | that run's XML: `tests="2" failures="1"`, `sources=719 findings=1`, `2026-10-20T23:02:07.005Z`; `system-out` names `:36: @ApplicationContext private val context: Context, in app/src/main/java/com/naviveylin/navigation/NavigationNotificationController.kt` |
| `green-run-round2.log`, `green-run-round2.started` | the same filtered invocation after restoring `@param:`: `BUILD SUCCESSFUL`, `23:03:42`–`23:03:45` |
| `green-run-round2.xml` | that run's XML: `tests="2" failures="0"`, `sources=719 findings=0`, `2026-10-20T23:03:45.120Z` |
| `classfile-state-bare.txt`, `classfile-state-param.txt` | the state record for the two-state classfile measurement: capture time, the `NavigationNotificationController.kt:36` line as it read at that moment (`@ApplicationContext` / `@param:ApplicationContext`), and the classfile's `Last modified` (`22:56:32` / `22:56:50`), size and SHA-256 |
| `classfile-bare.class`, `classfile-param.class` | the two captured classfiles: 6225 bytes each, SHA-256 `cd6edefe2556e83d67f8ba34d6304456cfc1760399ae391a4eb614fb01db90b5` each — two different compilations, one byte-identical class |
| `javap-constructor-bare-round2.txt`, `javap-constructor-param-round2.txt` | `javap -v -p` of those two classfiles, both carrying `RuntimeVisibleParameterAnnotations: parameter 0: dagger.hilt.android.qualifiers.ApplicationContext` |
| `javap-diff-round2.txt` | `diff` of the two dumps: **empty** (0 bytes) — the annotation's placement is unchanged in both states, header included, because the classfiles are identical |
| `probe-guard-surface.py`, `probe-guard-surface.txt` | the guard's scan replicated outside Gradle: `sources=719 findings=0`, and the fixtures still match (`implicit fixtures hits: [1, 1, 1]`, `explicit fixtures hits: [0, 0, 0, 0, 0]`) — the numbers the two JUnit XMLs are checked against |
| `probe-property-qualifier-sites.py`, `probe-property-qualifier-sites.txt` | the wider probe behind the design's residual-risk statement: a qualifier on any `val`/`var` with any modifier list, comments reported apart → `scanned=719 code_findings=0 comment_lines=1` (the one comment is this guard's KDoc naming the refused form) |
| `gate/gate-console.log` | the gate run on the committed tree: `BUILD SUCCESSFUL in 3m 36s`, 189 tasks (26 executed), 0 `FROM-CACHE` |
| `gate/gate-tally.txt`, `gate/gate-tally.py` | the gate's per-module tally and the script that sums it from the task XMLs: `tests=5704 failures=0 errors=0` over the five suites (`app` ×2, `auto`, `core`, `osmscout-client-java`). The per-module `TEST-*.xml` files are build output and are not archived — the tally is the record, and the script re-derives it |
| `gate/gate-app-mobile-QualifierUseSiteTargetTest.xml`, `gate/gate-app-automotive-QualifierUseSiteTargetTest.xml` | the guard case inside the two `:app` flavor suites of the gate run: `tests="2" failures="0"`, `sources=719 findings=0` in each, `2026-10-20T23:04:03.678Z` and `2026-10-20T23:05:51.651Z` |
| `gate/started.txt` | the gate's wall clock: `2026-10-20T23:03:48.588Z` … `2026-10-20T23:07:25.544Z` |

The red and green runs of the guard are the filtered invocation
`./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.build.QualifierUseSiteTargetTest"
-PforceTests --no-build-cache`, which is a pre-gate run, not gate evidence (spec `build-test-gate`, "A
pre-gate run is not gate evidence"); the gate is `./gradlew test -PforceTests --no-build-cache`, run once on
the committed tree (`gate/`).

The numbers in this change's prose (`sources=719`, `findings=1`/`0`, `tests=5704`, the timestamps) are read
out of the XMLs above, not from a console that hides test stdout.

Round-1 artefacts that a later measurement replaced are not kept here — their claims and their defects are
recorded in `design.md` ("Round 2 — what the first review changed, and what it did not") and in the item's
`reviewer-report-1.md`.
