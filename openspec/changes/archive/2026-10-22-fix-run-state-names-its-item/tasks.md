# tasks — fix-run-state-names-its-item

- [x] 1. Red case on HEAD: `run#stateNamesTheItemInProgress` in `tools/loop-cases.sh` (registration in
      `LOOP_SHELL_CASES`, dispatch entry, the case body), read against the engine at
      `522d69a7ac21aebe8a542983671c5f57963ebf5a` —
      `bash tools/loop-selftest.sh --case 'run#stateNamesTheItemInProgress'` exits `1` with
      `the state names the item while it is being worked: expected '21', got 'null'`
      (`evidence/red-HEAD.log`, `evidence/red-HEAD.xml`, `tests=1 failures=1`, started 2026-10-22T20:44:30Z).
- [x] 2. `tools/loop/run.sh` — record the picked item in the state before its first child, guarded like the
      label write above it. Verified by task 1's case turning green:
      `bash tools/loop-selftest.sh --case 'run#stateNamesTheItemInProgress'` exits `0`
      (`evidence/focused/stateNamesTheItemInProgress-green.log`).
- [x] 3. `openspec/specs/agent-issue-loop` — the requirement and its one scenario, which names exactly the case
      from task 1. Verified by `openspec validate 2026-10-22-fix-run-state-names-its-item --strict`.
- [x] 4. `guidelines/Backlog.md`, heading `## 7. The loop` — the sentence about what a state names gains the item. Verified
      by task 5's `tools/check-doc-routes.sh`, which the gate runs for `*.md`.
- [x] 5. Gate, once: `./tools/loop/gate.sh` from the change root — the run's own command, quoted rather than
      re-run (`evidence/gate.log`, `evidence/gate-tallies/`).
- [x] 6. Evidence pointers checked mechanically from the change root:
      `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/2026-10-22-fix-run-state-names-its-item --root .`
- [x] 7. Archive the change and confirm the delta merged into `openspec/specs/agent-issue-loop/spec.md`.
