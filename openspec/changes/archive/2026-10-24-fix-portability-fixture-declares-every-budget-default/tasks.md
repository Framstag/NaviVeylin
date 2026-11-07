# Tasks — the portability fixture declares every budget default

## 1. The test that reproduces the defect

- [x] 1.1 The failing case, on a clean checkout of the base, recorded before any edit:
      `bash tools/check-portability-selftest.sh --case foreign#fixtureDeclaresEveryBudgetDefault` on
      `/home/tim/projects/NaviVeylin` at `30c23b52` → `FAIL`, its message naming the key
      (`expected nothing, got:  LOOP_STRICT_OUTSIDE_WRITE_DEFAULT`). The case is the test; the defect is that
      the fixture does not declare a default the engine reads.
      Verify: `evidence/red-HEAD.log`, exit `1`.
      Parent: `tooling-portability` — "The synthetic project is a fixture, not a second project", scenario
      *The fixture declares every budget the engine reads*.
- [x] 1.2 Attribution of the red to the commit that caused it, not to the base:
      `git log -S LOOP_STRICT_OUTSIDE_WRITE_DEFAULT -- tools/loop/loop.conf` → `31e8f0bc`
      ("a-difference-outside-the-worktree-is-recorded (#286)"), the commit that added the key to the engine's
      configuration and left the fixture behind.
      Verify: `evidence/evidence.md`, the command and its one-line output.

## 2. The fix

- [x] 2.1 `tools/fixtures/foreign-project/tools/loop/loop.conf`: `LOOP_STRICT_OUTSIDE_WRITE_DEFAULT=off` with
      the comment the file's other keys carry — the value the engine ships, for a tree a run does not own
      exclusively.
      Verify: `bash tools/check-portability-selftest.sh --case foreign#fixtureDeclaresEveryBudgetDefault` →
      `PASS` (`evidence/green-case.log`).
      Parent: the same scenario.

## 3. The invariant, made permanent

- [x] 3.1 `tools/portability-mutations.json`: record `7.1` — delete the key from the fixture;
      `foreign#fixtureDeclaresEveryBudgetDefault` must go red, the file is restored, the case green again.
      Verify: `bash tools/mutate.sh --mutations tools/portability-mutations.json --only 7.1` →
      `RED(exit 1)` / `GREEN(exit 0)` (`evidence/revert-check.log`).
      Parent: the same scenario — the record is what makes the declared key an asserted invariant rather than a
      line a later edit may drop.

## 4. The suites and the gate

- [x] 4.1 The whole portability set, which is the gate `gate.sh` maps for
      `tools/fixtures/foreign-project/*` and `tools/portability-*`:
      `bash tools/check-portability-selftest.sh` → `all 27 cases passed`.
      Verify: `evidence/full-suite.log`, its summary line quoted.
      Exclusion: nothing — the change touches no other suite's path, so `tools/loop-selftest.sh` and the
      loop's mutation set are not this change's gate (`guidelines/Build.md` §2, the scope block).
- [x] 4.2 The rest of the mapped gate: `bash tools/check-portability.sh` (`OK — 47 engines and 2 templates name
      no project value`), `bash tools/mutate.sh --mutations tools/portability-mutations.json --list`
      (`15 mutation(s)`), and `bash tools/check-doc-routes.sh` with `bash tools/check-todo-references.sh` for the
      `.md` file it edits.
      Verify: `evidence/check-portability.log`, `evidence/mutation-list.log`, `evidence/doc-gates.log`, every exit
      `0`.
- [x] 4.3 The skills that state the record count kept in step: `.pi/skills/mutation-runner/SKILL.md` (14
      records). `.pi/skills/portability-check/SKILL.md` states the case count, which this change does not
      change (27 → 27).
      Verify: `evidence/doc-gates.log` (§4.2) and the diff of the one line.
