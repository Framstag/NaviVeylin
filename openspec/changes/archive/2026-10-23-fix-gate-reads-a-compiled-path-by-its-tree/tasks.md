# Tasks — fix-gate-reads-a-compiled-path-by-its-tree

- [x] 1. Red case, before any profile change: `gate#anArchivedArtefactOwesNoBuild` added to
      `tools/loop-cases.sh` and registered, run against HEAD — FAILED with
      `./gradlew test -PforceTests --no-build-cache` owed for a tree whose only change is an archived evidence
      tally (`evidence/red-HEAD.log`, `evidence/red-HEAD.xml`). Its guard,
      `gate#aModuleResourceOwesTheBuild`, PASSED on HEAD by accident (`*.xml`), which is what makes it a guard.
      **Verification:** the two `--case` invocations in `evidence/red-HEAD.log`.
- [x] 2. Plan written before the fix: `proposal.md`, this file, `design.md` and the `agent-issue-loop` delta, with
      the surface counted (`surface: 2 cases added, 0 rewritten, 1 helper added, 0 fixtures realigned, 1 profile
      line, 1 function, 2 mutation records`).
      **Verification:** the plan's four artifacts are the ones this archive carries; an archived change is no longer
      a validate target (`openspec validate <archived change> --type change --strict` → `exit=1`, no deltas), and
      the capability spec it merged into carries only the eight `Requirement text is very long` warnings that
      pre-date this delta, none of them this requirement (469 characters, `evidence/post-archive-checks-round3.log`).
- [x] 3. `tools/loop/loop.conf` — `LOOP_GATE_COMPILED_PATTERNS` bounded to the trees the build reads and the build
      inputs named by convention, with the rule, its residual and the measurement in the comment above it.
      **Verification:** two `PASS` lines in `evidence/fix-cases.log`, one per case — the plan for an archived
      evidence tally names no compiled command and the plan for the module's resource and `lint.xml` names it. The
      plan strings themselves live in each case's assertions, so the log prints them only on failure.
- [x] 4. `tools/loop/gate.sh` — `matches()` reads the pattern list with pathname expansion off.
      **Verification:** the same two cases; without the guard `gate#aModuleResourceOwesTheBuild` fails for every
      anchored pattern, which is what record `50.2` asserts by removing it.
- [x] 5. Mutation records `50.1` and `50.2` declared in `tools/loop-mutations.sh`, each naming one new case.
      **Verification:** `bash tools/loop-mutations.sh --files 'tools/loop/loop.conf tools/loop/gate.sh'` —
      every selected record applies and its case goes red, then green again (`evidence/mutations-44.log`).
- [x] 6. Gate once, from the change root: `./tools/loop/gate.sh`.
      **Verification:** the `LOOP-GATE:` line, its tallies copied to `<change>/evidence/gate-tallies/`
      (`evidence/gate.log`, before the archive) and to `<change>/evidence/gate-tallies-round2/`
      (`evidence/gate-round2.log`, after the round-2 wording correction). Round 3's correction re-ran it with
      `LOOP_CONF` pinned to the tree under test — five checks, no build, `exit=0`, the five tallies adding to 308
      cases and 0 failures (`evidence/gate-round3-pinned.log`, tallies under
      `evidence/gate-tallies-round3-pinned/`) — against the unpinned run of the same tree that names the build
      (`evidence/gate-round3.log`).
- [x] 7. Evidence and archive: the XMLs copied into `<change>/evidence/`, the pointer check
      (`bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-gate-reads-a-compiled-path-by-its-tree
      --root .`) and the archive move.
      **Verification:** the archive directory exists and the capability spec carries the requirement
      (`openspec/specs/agent-issue-loop/spec.md:1572`); the pointer check's own `PASS`/`WARN` lines are in
      `evidence/post-archive-checks.log`, re-run on the archived directory, and again after round 3's wording
      correction in `evidence/post-archive-checks-round3.log`.
