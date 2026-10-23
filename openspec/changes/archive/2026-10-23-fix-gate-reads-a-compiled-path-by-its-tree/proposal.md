# fix-gate-reads-a-compiled-path-by-its-tree

## Why

The loop's gate asks one question of a delta — does a path the build compiles appear in it? — and answers it from
a **file name**, because the profile's pattern list is matched against every path in the tree:
`tools/loop/loop.conf:140` declares `LOOP_GATE_COMPILED_PATTERNS="*/src/* */lint.xml *.gradle.kts *.gradle *.toml
CMakeLists.txt"` today and declared `*.kt *.java *.kts *.gradle *.toml *.xml *.c *.cpp *.cc *.h *.hpp
CMakeLists.txt app/src/main/cpp/* app/src/main/res/*` at HEAD, and `tools/loop/gate.sh:83` decides a path is
compiled when any of those patterns matches it anywhere. So an archived change's own evidence tally is a compiled
file.

**Root cause, one sentence:** the pattern list identifies a compiled path by its extension alone and not by the
tree it sits in, so `*.xml` matches `openspec/changes/archive/<x>/evidence/gate-tallies/a.xml` and the gate owes
the full Kotlin suite for a delta that touches no compiled file (measured on HEAD, `evidence/probes.log` probe 1:
this tree's diff against its base carries **0** compiled-extension paths and **4** `.xml`, and
`./tools/loop/gate.sh --plan` still prints `./gradlew test -PforceTests --no-build-cache` first).

**The scale of it, re-measured in the tree.** `git ls-files '*.xml' | grep -c '^openspec/changes/archive/'` → 735
archived artefacts when this change was planned, **741** on reread — the count grows with every archived evidence XML
(`evidence/counts.log`) — and two more trees carry the same false positive today: three fixture `.xml` under
`tools/fixtures/gate-timings/root/app/build/test-results/` (a fixture that *mimics* a test report) and ten
`tools/issues/core/*.java` the shell suites compile with `javac`, not the Gradle gate. The measured cost is
#262's: its own gate line printed 202 cases over `tools/loop-selftest.sh`,
`tools/loop-mutations-selftest.sh` and `tools/check-doc-routes.sh` with no failure — the item reports 13 min 15 s,
while the cited log carries the tally and no duration — before its archive commit, and
`./gradlew test -PforceTests --no-build-cache` first on the same tree after it.

**A second mechanism, in the same function, and why it is part of this delta.** An anchored pattern cannot work
today even if the profile declared one: `matches()` reads its list with an unquoted `for glob in $1`, so the shell
**expands the pattern list against the directory the gate happens to run in** before matching
(`tools/loop/gate.sh:68-76`). Measured 2026-10-23 (`evidence/probes.log` probes 1–2, the run's `date=` line in
probe 1): with the profile's own
`app/src/main/res/*` and a new file under that directory, the plan says `LOOP-GATE: nothing owed`, and with
`set -f` in the matcher it owes `fake-build` — so the two patterns the profile already anchors
(`app/src/main/cpp/*`, `app/src/main/res/*`) are dead weight in any project root. Bounding the list to the trees
the build reads therefore cannot be done without this: the fix is one profile line and one matcher guard.

**Red test on HEAD, before the fix.** Two cases added to `tools/loop-cases.sh`, run against HEAD
(`b1b8de08c6e1673`, 2026-10-23T07:00:27Z, `evidence/red-HEAD.log`), both asked with the profile the engine itself
carries — a profile of the case's own could not catch a wrong pattern:

- `gate#anArchivedArtefactOwesNoBuild` — **FAILED**, for the reason the defect names: a tree whose only change is
  `openspec/changes/archive/x/evidence/gate-tallies/a.xml` owes `./gradlew test -PforceTests --no-build-cache`
  (`should not appear in: ./gradlew test -PforceTests --no-build-cache …`).
- `gate#aModuleResourceOwesTheBuild` — **PASSED on HEAD**, which is the point: it is the guard against a fix that
  simply drops `*.xml`, and it passes today only because `*.xml` matches a module's resource by accident.

The tally is `evidence/red-HEAD.xml` (`tests="2" failures="1"`: one invocation per case, so the two cases and
the one failure).

**Owning capability and guideline section.** The capability is `agent-issue-loop`, which owns the run's gate and
its profile ("The run's mechanics are the project's configuration, not its code", and the engine-path record-check
scenario this requirement sits beside). The rule the patterns disagree with is `guidelines/Build.md` §2a's scope
block, line 114: "**No compiled file changed** (`*.md`, `openspec/**`, `.pi/**`, `guidelines/**`, `tools/**`): run
**no build and no suite**" — the guideline already says a `tools/**` or `openspec/**` delta compiles nothing,
while the patterns call an `.xml` under either of them compiled. The guideline is right and needs no change; the
mapping is what moved.

## What Changes

- `tools/loop/loop.conf` — `LOOP_GATE_COMPILED_PATTERNS` becomes `*/src/* */lint.xml *.gradle.kts *.gradle *.toml
  CMakeLists.txt`: a module's source tree whatever the module is called (so a new module is gated), its
  `lint.xml`, and the build inputs whose names are the convention — a Gradle script, a toolchain `.toml`,
  `CMakeLists.txt`. That last half is a residual the requirement states rather than hides: it also classifies a
  Gradle init script beside the build (`tools/gate-timings.init.gradle.kts`) and the root `hmm_mise.toml` as
  compiled. The measurement behind the line and the reason the two dead anchored patterns disappear are in the
  comment above it.
- `tools/loop/gate.sh` — `matches()` reads its pattern list with pathname expansion off, saved and restored, so a
  pattern is compared as a pattern instead of being replaced by whatever it matched in the caller's directory.
- `tools/loop-cases.sh` — the two cases above and one helper, `profile_value`, that reads a value from the profile
  the engine under test carries.
- `tools/loop-mutations.sh` — records `44.1` (the profile line, restoring `*.xml`) and `44.2` (the matcher's
  `set -f`), each naming one of the new cases.

**Surface, counted before the plan was fixed.**
`grep -rn 'LOOP_GATE_COMPILED_PATTERNS\|LOOP_GATE_COMPILED_CMD\|matches()' --exclude-dir=archive --exclude-dir=.pi
tools/` names 3 cases that build a profile of their own (`gate#theDeltaSelectsTheSuitesItTouches`,
`measure#namesTheOwedChecksInOneBlock`, `measure#printsTheNumbersTheReviewerAsksFor`) — all three assert the
engine's use of *a* pattern list and are untouched — plus one profile line, one matcher and one existing mutation
record (`43.2`, the suite pair).

surface: 2 cases added, 0 rewritten, 1 helper added (`profile_value`), 0 fixtures realigned, 1 profile line, 1
function (`matches`), 2 mutation records — the `grep -rn` above, whose result is 3 untouched cases that build a
profile of their own (`gate#theDeltaSelectsTheSuitesItTouches`, `measure#namesTheOwedChecksInOneBlock`,
`measure#printsTheNumbersTheReviewerAsksFor`), 1 profile line, 1 matcher and 1 existing record (`43.2`), and a
diff whose 469 lines are 2 cases, 1 helper, 2 records, the profile line, the matcher and the artifacts (review 1).

## What it does not change

The suite map is untouched: `tools/fixtures/gate-timings/**` owes no suite at all today (no `LOOP_GATE_SUITES`
pair names that tree, although `tools/gate-timings-selftest.sh` reads it) — a gap in a different key, filed by
whoever owns the tracker, not fixed here.
