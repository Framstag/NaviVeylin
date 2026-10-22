# Tasks — fix-mutation-harness-file-scope

The delta this change implements is `agent-issue-loop`'s *A mutation run is scoped by the files the change
touches* (`specs/agent-issue-loop/spec.md`). No compiled file changes, so no build task is owed by these paths:
the structural check of each edited file stands in (`guidelines/Build.md` §2, the scope block).

## 1. The red case on HEAD

- [x] 1.1 Add checks 7–9 to `tools/loop-mutations-selftest.sh` — a scoped `--list` and the count line it
      prints, a scoped run whose REDs are exactly the records the file owns, an empty file set that exits 2 and
      runs nothing, and `--from-git HEAD` on a synthetic repository whose working tree changed one record's
      file. The expected records are read from the declared set, not spelled in the test. Reference: the
      delta's four scenarios.
      **Verification:** with the harness unchanged at HEAD `11123fb`, the suite is **9 of 34 cases failed**,
      exit 1, `2026-10-22T07:08:27Z` (`evidence/red-on-HEAD.log`); the new cases answer
      `loop-mutations: unknown argument --files` and `--from-git`.
- [x] 1.2 Give the suite a tally line in the shape the loop's gate parses (`all N cases passed` /
      `M of N cases failed`) through `ok`/`bad`/`contains` helpers, so a change that maps this file in
      `tools/loop/loop.conf` reports numbers. Reference: the delta's scenarios are counted as cases.
      **Verification:** the green run ends `all 34 cases passed` (`evidence/green-selftest.log`).

## 2. The fix

- [x] 2.1 `selected()` takes the record's file as its second argument and applies the file set; `--files`
      (repeatable, space-separated inside one value) and `--from-git <base>` (the diff against the base plus
      `git status`) build that set. Reference: the delta's four scenarios.
      **Verification:** `bash tools/loop-mutations.sh --files tools/loop/state.sh --list` prints `9.1` and
      nothing else; `--files tools/loop/nothing-owns-this-file.sh` exits 2; `--only 99.9` now exits 2 too.
- [x] 2.2 Print `selected N of M records` before the first record, and refuse a selection of zero records with
      exit 2 before the scratch directory exists. `--list` applies the same filter, and a probe ignores the
      file set (the harness's own guard is not a record). Reference: the delta's third scenario.
      **Verification:** the scoped run prints `selected 1 of 81 records`; the empty set prints `the selection
      names no record of the 81 declared` and exits 2 with nothing mutated (checks 7–8 of the suite).
- [x] 2.3 Map both harness paths to the harness's own self-test in `tools/loop/loop.conf`, so a change to
      `tools/loop-mutations.sh` or `tools/loop-mutations-selftest.sh` is gated by the suite that proves it.
      **Verification:** `LOOP_CONF=<this tree>/tools/loop/loop.conf ./tools/loop/gate.sh --plan` lists
      `tools/loop-mutations-selftest.sh` (`evidence/gate-plan.log`, second block). The run's own gate reads the
      *project's* `tools/loop/loop.conf` — `LOOP_CONF` is exported by `run.sh` and points outside this
      worktree — so the mapping takes effect with this change's commit; the first block of `gate-plan.log`
      records what the run's gate planned.
- [x] 2.4 State the two flags and the count line in `.pi/skills/revert-check`'s loop-harness blocks, so the
      iteration recipe a session reads names the bound. Reference: `guidelines/Build.md` §2 rule 2, which the
      mechanism serves.
      **Verification:** the block lists `--files` and `--from-git` and says a filtered run prints its selection
      and refuses an empty one; `tools/check-doc-routes.sh` passes (`all 120 section references resolve`).

## 3. Falsification of the new invariants

- [x] 3.1 Revert-check: remove the empty-selection refusal (line 263), run the suite, restore the file
      byte-for-byte; then remove the selection line (line 268), run it, restore again; then run it once more.
      Reference: the delta's third scenario; `.pi/skills/revert-check`.
      **Verification:** `evidence/falsification.log` — mutation 1 is **2 of 34 cases failed** (the empty-set
      cases), mutation 2 is **3 of 34 cases failed** (the count-line cases), each restore is `cmp`-identical
      (sha256 `fe8b15fb…88c911` before and after) and the restored suite is `all 34 cases passed`, exit 0.

## 4. Verification and archive

- [x] 4.1 Structural checks for a delta that compiles nothing: `bash -n` on both scripts,
      `openspec validate fix-mutation-harness-file-scope --type change`, `openspec doctor`, and
      `tools/check-doc-routes.sh`.
      **Verification:** `bash -n` silent, the change is valid (one length warning on the requirement text),
      doctor `OpenSpec root: ok`, `check-doc-routes: all 120 section references resolve, all 5 skill
      references resolve`.
- [x] 4.2 Run the project's gate once — `./tools/loop/gate.sh` — and quote its `LOOP-GATE` line. Reference:
      the delta's scenarios; the gate's own suite mapping.
      **Verification:** `LOOP-GATE: tests=442 failures=0 (./gradlew test -PforceTests --no-build-cache,
      tools/loop-selftest.sh, tools/issue-tooling-selftest.sh, tools/sensor-selftest.sh,
      tools/check-action-runtimes-selftest.sh, tools/gen-feature-list-selftest.sh, bash
      tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)`, `2026-10-22T07:12:11Z`,
      exit 0 (`evidence/gate.log`, tallies in `evidence/gate/`). Named exclusions: (a) the gate read the
      project's suite map, so it did **not** run `tools/loop-mutations-selftest.sh` — that suite is this
      delta's own proof and is quoted green above; the mapping that puts it in the gate is task 2.3. (b) The
      gate measured the tree as it stood at `07:12:11Z`; one line changed afterwards — the `--help` range
      (`sed -n '2,31p'` → `'2,32p'`, which was printing a truncated usage block) — and no suite asserts it.
      The gate was not re-run (the run's rule: one gate per item, its result quoted); the shipped tree's
      harness self-test was re-run instead: `all 34 cases passed`, `2026-10-22T07:28:02Z`
      (`evidence/green-selftest-final.log`).
- [x] 4.3 Archive the change and confirm the requirement merged into
      `openspec/specs/agent-issue-loop/spec.md`. Reference: the delta.
      **Verification:** `openspec/changes/archive/2026-10-22-fix-mutation-harness-file-scope/` exists and the
      shipped spec carries *A mutation run is scoped by the files the change touches*.
