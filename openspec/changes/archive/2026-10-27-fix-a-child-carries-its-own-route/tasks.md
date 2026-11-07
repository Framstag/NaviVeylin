# Tasks — fix-a-child-carries-its-own-route

No compiled file is touched: the delta is two shell/prompt files, two cases, two mutation records and one
guideline section. The build gate is therefore replaced by the structural checks of the files edited
(`tools/loop-selftest.sh`, `tools/loop-mutations.sh --check`, `tools/check-portability.sh`,
`tools/check-doc-routes.sh`) and by the one gate run the run's configuration names.

- [x] 1. Write the two red cases and run them on the base commit.
  Verification: `bash tools/loop-selftest.sh --group adapter` → `loop-selftest: 2 of 2 cases failed`;
  `bash tools/loop-selftest.sh --case prompt#thePhasePromptsCarryTheirOwnRoute` → `loop-selftest: 1 of 1 cases
  failed`. Logs and tallies: `evidence/red-on-head-adapter.log`, `evidence/red-on-head-adapter.xml`,
  `evidence/red-on-head-prompt.log`, `evidence/red-on-head-prompt.xml`.

- [x] 2. Start the child without the project's context files, in the adapter only (`tools/pi-child-events.sh`).
  Verification: `bash tools/loop-selftest.sh --group adapter` → `loop-selftest: all 2 cases passed`.

- [x] 3. Give the phase prompts their own route (`tools/loop/prompts/worker.md`, `reviewer.md`).
  Verification: `bash tools/loop-selftest.sh --case prompt#thePhasePromptsCarryTheirOwnRoute` → `PASS`; and the
  group it lives in, `bash tools/loop-selftest.sh --group prompt` → `loop-selftest: all 13 cases passed`.

- [x] 4. Keep the policy seam green: the child still starts in the project.
  Verification: `bash tools/loop-selftest.sh --case run#childStartsInTheProjectRoot` → `PASS`.

- [x] 5. One mutation record per new invariant (`tools/loop-mutations.sh`, records 66.1 and 66.2).
  Verification: `bash tools/loop-mutations.sh --only "66.1 66.2"` → `RED` twice, `restore … green again` twice,
  `not-noticed: 0 · restore-failures: 0` (`evidence/mutations.md`); and the gate's own cheap check,
  `bash tools/loop-mutations.sh --check` → every record still applies.

- [x] 6. State the rule where it is owned (`guidelines/Backlog.md`, section "The loop").
  Verification, structural and offline: `bash tools/check-doc-routes.sh` → `all 154 section references resolve`;
  `bash tools/check-portability.sh` → `OK — 50 engines and 2 templates name no project value`.

- [x] 7. Measure the section the change removes, not assert it.
  Verification: `python3 openspec/changes/fix-a-child-carries-its-own-route/evidence/sections-probe.py
  /home/tim/projects/NaviVeylin` → `project_context 25624 chars`, `total system prompt 32924 chars`,
  `77.8 % … 9151.4 tokens at 2.8 chars/token` (`evidence/sections-probe.log`); `python3 tools/loop-spend.py` →
  `LOOP-SPEND-BASELINE tokens=17235 charged=354875389` (`evidence/loop-spend.log`).

- [x] 8. Run the project's gate once and copy its tallies into `evidence/`.
  Verification: `./tools/loop/gate.sh` (from the change root, after the fix) printed one aggregate line for 29 steps —
  693 cases, 0 failures, 0 errors, the loop suite being the longest at 418 s — and its whole log is `evidence/gate.log`;
  each step's own tally is `evidence/gate-tallies/`, among them
  `tools_loop-selftest.sh_--from-git_4a4b3a9554dc25fee6fa9cb441b6d2f0ec5c5a46.xml` with
  `tests="243" failures="0" errors="0"`.

- [x] 9. Archive and verify the change's citations.
  Verification: `bash tools/loop/evidence-check.sh openspec/changes/fix-a-child-carries-its-own-route --root .` →
  `ok C1 … ok C5 file:line: 214 resolved … evidence-check: PASS`; the archive directory exists under
  `openspec/changes/archive/` and `openspec/specs/agent-issue-loop/spec.md` carries the new requirement.
