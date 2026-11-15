# The gate's two red steps are attributed to work this change does not touch

Finalization of item #868, by a builder session on the item's branch (not by a run's gate child). The gate was
run **once**, and never re-run:

    bash tools/loop/gate.sh --base 5215e5452b97bb6452d198110bba3d7390fd347b     # base = main, 11 m 59 s

Its verdict line, verbatim from `evidence/gate-output-868.log:351` (tally run=860982):

    LOOP-GATE: tests=7 failures=0 errors=2 (./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache 713s, bash tools/gen-feature-list.sh --check-classification 6s, tools/check-doc-routes.sh 2s, tools/check-openspec-hygiene.sh 1s, tools/tests/check-openspec-hygiene-selftest.sh 1s) — failing: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache bash tools/gen-feature-list.sh --check-classification

The 4-tally directory is `evidence/gate-tallies-868/`; `tools/check-doc-routes.sh` printed "all 341 section
references resolve", `tools/check-openspec-hygiene.sh` "OpenSpec artifacts are consistent: 41 open change(s)",
and its self-test "all 7 cases passed" (`gate-output-868.log:341-349`).

## The tree this gate measured

The gate's own `LOOP-GATE-TREE:` line was not kept — its stdout was read through `tail -40`, and the line is printed
before the first step (`tools/loop/gate.sh:262`). Recomputed here, with the loop's own function and the same base, at
2026-11-15T07:05Z:

    LOOP-GATE-TREE: head=2b795305c7b9f2805cfe913fb9d300e82434efc3 diff=07272d69e375ee25

(`bash .pi/review-868/identity.sh 5215e5452b97bb6452d198110bba3d7390fd347b`, which sources `tools/loop/lib.sh` and
calls `loop_change_identity`, the function `gate.sh` and `run.sh` both use.) `head` names the commit those contents
now have — the gate ran them **uncommitted** on `f1084146` — and `diff` is a digest over file contents, so it is the
same value the gate printed: nothing of `architecture/**` was written after the gate, the two files it gated being
exactly what commit `77a51af1` carries, and every later write is an `evidence/` file the identity's
`LOOP_REVIEW_EXCLUDE_PATHSPEC` (`:(exclude,glob)**/evidence/**`) leaves out. Review round 1 was handed the round's diff
as a patch plus that patch's `sha256` (`.pi/review-868/diff.patch.identity`); the two `diff=` values are **different
derivations** and are not compared for equality — the comparison item 3 asks for is on `head=` plus the file-content
argument above, which `loop_change_identity` recomputed at the reviewed tree returns as `diff=07272d69e375ee25`.

## The two falsifications, and the mutations they performed

Tasks 3.1 and 3.5 each name a mutation of the **tree** (`add import … to a concept file`, `move one baselined file into
its target package`) and require the named **case** to fail. Neither tree edit can reach the case it names: both cases
assert over a source list and a baseline the test hands the check itself (`ConceptRuleTest.reportOf`,
`StructureBaselineTest.reportOf`), so a tree edit reds `greenRunPrintsFigures`, never the named case. Each falsification
therefore carries the case level, and 3.5 carries the prescribed tree level beside it.

- **3.1, case level — the production rule.** The `concept imports nothing of ours` arm of `LayerRules.conceptFindings`
deleted, so the rule declines to refuse a cross-package import. Red: `ConceptRuleTest#conceptImportsNothingOfOurs`,
message "a concept file importing another of our packages must fail: rows=1 files=1 exemptions=0 rules=4"
(`evidence/revert-check-concept-red.xml`, `tests="2" skipped="0" failures="1" errors="0" timestamp="2026-11-15T06:19:48.573Z"`);
the sibling `conceptImportsNoAndroidType` stays green, and the tree case reds beside it
(`evidence/revert-check-concept-red-tree.xml`, `failures="1" timestamp="2026-11-15T06:19:53.204Z"`) because the three
`concept imports nothing of ours` entries of `architecture/baseline.tsv` are then stale. Restored verbatim.
- **3.5, case level — the refusals.** `Baseline.refusals`'s stale-exemption test (`if (entry.key !in live)`) replaced by
`if (false)` — the whole of that refusal. Red: `StructureBaselineTest#staleExemptionFails`, message "an entry whose file
no longer violates the rule must fail: rows=1 files=1 exemptions=0 rules=4"
(`evidence/revert-check-stale-exemption-red.xml`, `tests="4" skipped="0" failures="1" errors="0" timestamp="2026-11-15T06:20:11.667Z"`).
`rules=4` is `LayerRules.ALL.size`, which the mutation does not touch, and the three sibling cases stay green. Restored
verbatim.
- **3.5, tree level — the prescribed mutation.** One stale entry appended to `architecture/baseline.tsv`
(`app/src/main/java/com/naviveylin/concept/Version.kt`, exempted from `concept imports no Android type` — a file that
imports nothing of Android): `StructureReportTest#greenRunPrintsFigures` reds naming the entry, verbatim
`stale-exemption: architecture/baseline.tsv:37 app/src/main/java/com/naviveylin/concept/Version.kt (concept imports no
Android type) exempts the file from 'concept imports no Android type', which it no longer violates`
(`evidence/revert-check-stale-entry-tree-red.xml`, `tests="2" skipped="0" failures="1" errors="0" timestamp="2026-11-15T06:48:15.327Z"`;
14 of the module's 15 cases green). The row was removed, `git checkout architecture/baseline.tsv` restoring it, and the
module re-ran green in 16 s (`evidence/green-architecture-module/`, `timestamp="2026-11-15T06:48:24.809Z"`).

No mutation marker is left in either production file: `grep -rn 'REVERT-CHECK' architecture/src` prints nothing, and
`git diff 77a51af1 -- architecture/src/main/kotlin/com/naviveylin/architecture/LayerRules.kt
architecture/src/main/kotlin/com/naviveylin/architecture/Baseline.kt` is empty.

## The baseline's day-one figures

The header of `architecture/baseline.tsv` quotes 59 files, 26 Android-import matches in 11 files and 9 cross-package
matches in 6 files, with the two `tools/look.sh` commands that produce them. Both commands' outputs, and the file
count, are recorded in `evidence/baseline-figures.log`, measured at `2b795305` on 2026-11-15.

## Red 1 — `:app:testMobileDebugUnitTest`, a TLS environment fault

`gate-output-868.log:239-240`, verbatim:

    MapManagerSourceSwitchPlanTest > aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered FAILED
        java.lang.NoClassDefFoundError: Could not initialize class sun.security.ssl.NamedGroup$SupportedGroups

The stack is a real `HttpsURLConnection` (`gate-output-868.log:298`: `sun.security.ssl.NamedGroup$SupportedGroups.<clinit>`,
reached through `sun.net.www.protocol.https.HttpsClient`), not an assertion about this change: the run reports one failing
test out of the 2 294 the suite completed (`gate-output-868.log:305`), and the class the failing case sits in carries
nine cases of which this is the one that failed — the class's own run, `evidence/flake-case-green-alone.xml`, carries
all nine green.

- **Not on this change's paths.** `git diff --name-only 5215e545...HEAD` is `architecture/**` and
  `openspec/changes/archive/2026-11-14-package-structure-as-five-kinds/evidence/**` and nothing else — no file of
  `:app` is in the diff, so the same bytes of `app/**` and of every Gradle file that builds it are the base's.
- **Red long before this work, same case, same error**: `openspec/changes/archive/2026-10-18-fix-parked-state-race/report.md:82`
  names `NoClassDefFoundError: sun.security.ssl.NamedGroup$SupportedGroups` on `MapManagerSourceSwitchPlanTest`;
  `2026-10-27…/evidence/gate-attribution.md:16-23` records the same case failing and "Rerun alone, the class passed";
  `2026-11-06-motorway-destination-hint/tasks.md:113` carries it as the change's unattributable red; item #759's
  `worker-tail-report.md` and `reviewer-report-1.md:77` the same.
- **Green alone at this HEAD**, measured after the gate: `./gradlew :app:testMobileDebugUnitTest -PforceTests
  --no-build-cache --tests '*MapManagerSourceSwitchPlanTest*'` — `BUILD SUCCESSFUL in 3m 32s`,
  `evidence/flake-case-green-alone.xml` (`tests="9" skipped="0" failures="0" errors="0" timestamp="2026-11-15T06:35:53.495Z"`).
- **Green in the item's own gate**: this branch's earlier tally, `evidence/gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`,
  reads `tests="0" failures="0" errors="0"` and `BUILD SUCCESSFUL in 6m 49s` — the same JVM suite, green, in the kept
  worktree at `062810f2`.

## Red 2 — the classification step, the inherited red this archive already attributed

The step printed the identical block that `evidence/gate-classification-attribution.md` attributes for #867:
`settings-surface` and `settings-taxonomy` unclassified, both introduced by the open change
`settings-taxonomy-and-surface` (gitignored, `.gitignore:76`), and 5 stale ids that only print
(`tools/gen-feature-list.sh:252-255`). Nothing this item writes touches `tools/feature-list/` or that delta —
`git diff --name-only 5215e545...HEAD -- tools/feature-list openspec/specs` is empty, and the step's `delta spec ids`
figure reads 56 against #867's 30 while this diff adds no `openspec/specs/**` file at all — the figure counts the
open changes' deltas present in this checkout (`tools/gen-feature-list.sh:276` over `find openspec/changes -path
'*/specs/*/spec.md'`, 77 of them across 41 open changes here). The attribution above stands unchanged and is not
re-derived here.

**What this item added to that attribution, measured:** the check's inputs are unchanged from the base, so the
step's verdict is a pure function of files outside this diff — the same red, before and after (`before`:
`evidence/classification-red-before-867.log`; `after`: `evidence/gate-output-868.log:330-340`).

## The clauses both closes are waived with

    LOOP-GATE-WAIVED: the gradle step's one failing case is MapManagerSourceSwitchPlanTest#aSwitchThatKeepsTheTargetsBasemapKeepsItRegistered, a NoClassDefFoundError from sun.security.ssl.NamedGroup$SupportedGroups raised through a real HttpsURLConnection (gate-output-868.log:239-240, 298), an environment fault this project recorded before this items base on the same case and error (2026-10-18-fix-parked-state-race/report.md:82, 2026-10-27 gate-attribution.md:16-23, 2026-11-06-motorway-destination-hint/tasks.md:113, item #759) and green alone at this HEAD (BUILD SUCCESSFUL 3m 32s, evidence/flake-case-green-alone.xml tests=9 failures=0); the branch changes no file of :app (git diff --name-only 5215e545...HEAD), and the items own gate at 062810f2 recorded BUILD SUCCESSFUL 6m 49s over the same suite; and the other step is the classification red attributed in evidence/gate-classification-attribution.md, two ids of another changes gitignored open delta with tools/feature-list byte-identical to the base
    LOOP-GATE-WAIVED-SUITE: ./gradlew test -PforceTests -PjvmTestsOnly --no-build-cache

    LOOP-GATE-WAIVED: the step's only failing condition is two unclassified ids, settings-surface and settings-taxonomy, introduced by the open change settings-taxonomy-and-surface — another change's open delta, gitignored by .gitignore:76, red before this item's base (evidence/classification-red-before-867.log) and after every write this item made (evidence/gate-output-868.log:330-340); tools/feature-list and openspec/specs are unchanged by this diff (git diff --name-only 5215e545...HEAD), and the step's stale half only prints (tools/gen-feature-list.sh:252-255)
    LOOP-GATE-WAIVED-SUITE: bash tools/gen-feature-list.sh --check-classification
