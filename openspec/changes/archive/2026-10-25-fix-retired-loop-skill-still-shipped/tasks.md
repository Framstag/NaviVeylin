# fix-retired-loop-skill-still-shipped — tasks

- [x] 1.1 (`agent-issue-loop` — *The evidence check is the loop's own tool*) `tools/loop/evidence-check.sh` is the
      skill's script moved out: `--conf`/`--scan`/`--xml-dir` beside `--root`/`--table`, the two project paths read
      from `tools/loop/loop.conf`, a configuration that names no scan root refused with exit 2. Verify:
      `bash tools/loop/evidence-check.sh --selftest` (13 checks, the fixtures' own paths).
- [x] 1.2 `tools/loop/loop.conf`: `LOOP_EVIDENCE_SCAN_PATHS`/`LOOP_EVIDENCE_XML_DIR`, the gate pair
      `tools/loop/evidence-check.sh|bash tools/loop/evidence-check.sh --selftest`, and the floor comment's citation
      repointed at `guidelines/Backlog.md`, section 7. Verify: `bash tools/loop/gate.sh --plan --base <base>` names the pair.
- [x] 1.3 `tools/check-portability.sh` declares both keys as project values; `tools/loop/gate.sh:93`'s comment stops
      quoting the resource-tree literal. Verify: `bash tools/check-portability.sh` OK, and the value appears in
      `--values` beside `LOOP_SUBMODULE_PATH`.
- [x] 1.4 `tools/loop/worktree.sh` seeds `openspec-*` only; `tools/portability-cases.sh` drops the scaffolding for
      the directory nothing copies. Verify: `worktree#seedsAllFourInputs`, `foreign#emptyGateCommandStillRunsThePhases`.
- [x] 1.5 `tools/loop/prompts/worker.md` phase F and `tools/loop/prescreen.sh`'s citation name the new homes.
      Verify: `grep -rn 'tools/loop/evidence-check.sh' tools/loop/prompts/worker.md` and `check-doc-routes` over the
      documents that quote them.
- [x] 1.6 `tools/loop-cases.sh`: the seeding case asserts the generated skill is copied and a versioned one is not;
      the new case `evidence#scansWhatTheProfileNames` exercises the moved check. Verify:
      `bash tools/loop-selftest.sh --case worktree#seedsAllFourInputs --files …` — both red on the base
      (`evidence/red-on-head.log`), both green after (`evidence/green-cases.log`).
- [x] 2.1 `AGENTS.md`'s skill table drops the row; `guidelines/Backlog.md:29` drops the clause. Verify:
      `bash tools/check-doc-routes.sh` green, and `grep -rn '\.pi/skills/fix-loop' AGENTS.md guidelines/
      openspec/specs/` empty.
- [x] 2.2 `guidelines/Backlog.md`, section 7 gains the measurements the skill carried — costs and the floor's derivation, the
      host's memory, the first run's tallies and the state/kept-tree numbers. Verify: each number in
      `.pi/skills/fix-loop/REFERENCE.md` at the base is in section 7 (`evidence/measurements-moved.log`).
- [x] 2.3 `openspec/config.yaml`'s apply guidance points at the worker prompt, not at the skill.
- [x] 2.4 `.pi/skills/fix-loop/` deleted; the archived change `2026-10-22-loop-harness-bugs-and-features` records
      that the skill is gone. Verify: `test ! -d .pi/skills/fix-loop`, `bash tools/check-doc-routes.sh`,
      `openspec validate --changes --strict`.
- [x] 3.1 Structural checks for a delta that touches no compiled file: `check-doc-routes` (+ its self-test and its
      mutation list), `check-portability` (+ its self-test and its mutation list), `check-openspec-hygiene` (+ its
      self-test), `gen-feature-list.sh --check-classification`, `check-todo-references`, `mutate-selftest`, the issue
      tooling self-test and the mutation lists. No Gradle build is owed.
- [x] 3.2 Gate: `./tools/loop/gate.sh --base <the run's base>` once. Result quoted in `evidence/gate.log`.
- [x] 3.3 The change's own evidence pointers: `bash tools/loop/evidence-check.sh openspec/changes/
      2026-10-25-fix-retired-loop-skill-still-shipped --root .` PASS (`evidence/evidence-check.log`).
