# Tasks — fix-gate-reads-a-compiled-path-by-its-tree

- [x] 1. Red case, before any profile change: `gate#anArchivedArtefactOwesNoBuild` added to
      `tools/loop-cases.sh` and registered, run against HEAD — FAILED with
      `./gradlew test -PforceTests --no-build-cache` owed for a tree whose only change is an archived evidence
      tally (`evidence/red-HEAD.log`, `evidence/red-HEAD.xml`). Its guard,
      `gate#aModuleResourceOwesTheBuild`, PASSED on HEAD by accident (`*.xml`), which is what makes it a guard.
      **Verification:** the two `--case` invocations in `evidence/red-HEAD.log`.
- [x] 2. Plan written before the fix: `proposal.md`, this file, `design.md` and the `agent-issue-loop` delta, with
      the surface counted (`2 cases added, 0 rewritten, 1 profile line, 1 function, 2 mutation records`).
      **Verification:** `openspec validate fix-gate-reads-a-compiled-path-by-its-tree --strict`.
- [x] 3. `tools/loop/loop.conf` — `LOOP_GATE_COMPILED_PATTERNS` bounded to the trees the build reads, with the rule
      and the measurement in the comment above it.
      **Verification:** the plan for an archived evidence tally names no compiled command, and the plan for
      `app/src/main/res/values/strings.xml` and `app/lint.xml` names it (`evidence/fix-cases.log`).
- [x] 4. `tools/loop/gate.sh` — `matches()` reads the pattern list with pathname expansion off.
      **Verification:** the same two cases; without the guard `gate#aModuleResourceOwesTheBuild` fails for every
      anchored pattern, which is what record `44.2` asserts by removing it.
- [x] 5. Mutation records `44.1` and `44.2` declared in `tools/loop-mutations.sh`, each naming one new case.
      **Verification:** `bash tools/loop-mutations.sh --files 'tools/loop/loop.conf tools/loop/gate.sh'` —
      every selected record applies and its case goes red, then green again (`evidence/mutations-44.log`).
- [x] 6. Gate once, from the change root: `./tools/loop/gate.sh`.
      **Verification:** the `LOOP-GATE:` line, its tallies copied to `<change>/evidence/gate-tallies/`
      (`evidence/gate.log`).
- [x] 7. Evidence and archive: the XMLs copied into `<change>/evidence/`, the pointer check
      (`bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-gate-reads-a-compiled-path-by-its-tree
      --root .`) and the archive move.
      **Verification:** the archive directory exists and the capability spec carries the requirement.
