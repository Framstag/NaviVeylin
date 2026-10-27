# Tasks — fix-a-tool-result-over-its-bound-is-trimmed

No compiled file is touched: the delta is one new extension script, one adapter, one recorded key, five cases, four
mutation records and one guideline paragraph. The build gate is therefore replaced by the structural checks of the
files edited (`tools/loop-selftest.sh`, `tools/loop-mutations.sh --check`, `tools/check-portability.sh`,
`tools/check-doc-routes.sh`) and by the one gate the run's configuration names.

- [x] 1. Write the red cases and run them on the base commit.
  Verification: `bash tools/loop-selftest.sh --group trim` → `loop-selftest: 3 of 3 cases failed`;
  `bash tools/loop-selftest.sh --group adapter` → `loop-selftest: 2 of 3 cases failed`. Logs and tallies:
  `evidence/red-on-head-trim.log`/`.xml` (`tests="3" failures="3"`), `evidence/red-on-head-adapter.log`/`.xml`
  (`tests="3" failures="2"`), 2026-10-26T23:58:46Z.

- [x] 2. Measure the distribution the bound is placed against, instead of asserting a number.
  Verification: `python3 evidence/result-sizes.py` → `LOOP-RESULT-SIZES at=2026-10-27T00:17:42Z n=22901
  chars=59179014`, `p50=825`, `p90=7194`, `bound=8000 above=2052 share=9.0%` (`evidence/result-sizes.log`);
  `python3 tools/loop-spend.py` → `LOOP-SPEND-CAP8K saved=228988001` of `total=1663853998`
  (`evidence/loop-spend.log`).

- [x] 3. Land the rule (`tools/pi-trim-result.mjs`): the cut, the notice and the record, as a hook and as a CLI.
  Verification: `bash tools/loop-selftest.sh --group trim` → `loop-selftest: all 3 cases passed`; and the hook the
  child actually goes through, driven through the factory with a stand-in for pi's API:
  `node evidence/hook-probe.mjs` → `prefix=unchanged first 8000`, `tail=cut`, `notice=named`,
  `record=LOOP-TOOL-RESULT-TRIM: ctx_read chars=20005 kept=8000`, `undersized=untouched`, `bound-0=no bound`
  (`evidence/hook-probe.log`).

- [x] 4. Load it with every child and record the trim beside the child's event stream
  (`tools/pi-child-events.sh`), and record the bound (`tools/loop/loop.conf`).
  Verification: `bash tools/loop-selftest.sh --group adapter` → `loop-selftest: all 3 cases passed`;
  `bash tools/loop-selftest.sh --case run#childStartsInTheProjectRoot` → `PASS`, the seam the tool policy is
  resolved on, unchanged.

- [x] 5. One mutation record per invariant (`tools/loop-mutations.sh`, records 67.1–67.4: the cut, the notice, the
  record, the adapter's `-e`).
  Verification: `bash tools/loop-mutations.sh --only "67.1 67.2 67.3 67.4"` → `RED` four times, `restore … green
  again` four times, `not-noticed: 0 · restore-failures: 0` (`evidence/mutations.md`); and the gate's own cheap
  check, `bash tools/loop-mutations.sh --check` → every record still applies.

- [x] 6. State the rule where it is owned (`guidelines/Backlog.md`, section 7 "The loop").
  Verification, structural and offline: `bash tools/check-doc-routes.sh` → every section reference resolves;
  `bash tools/check-portability.sh` → `OK`.

- [x] 7. Run the project's gate once, from the change root, and copy its tallies into `evidence/`.
  Verification: `./tools/loop/gate.sh` → one aggregate `LOOP-GATE:` line over 29 steps — 701 cases, 0 failures, 0
  errors, the loop suite the longest at 411 s — and its own tally
  `tools_loop-selftest.sh_--from-git_<base>.xml` reads `tests="247" failures="0" errors="0"`
  (`evidence/gate.log`, `evidence/gate-tallies/`); the block beside it reports the same run as
  `LOOP-MEASURE-GATE seconds=844 suites=29` (`evidence/measure-block.log`).

- [x] 8. Archive the change and check its citations.
  Verification: `bash tools/loop/evidence-check.sh openspec/changes/fix-a-tool-result-over-its-bound-is-trimmed
  --root .` → PASS; the archive directory exists under `openspec/changes/archive/` and
  `openspec/specs/agent-issue-loop/spec.md` carries the new requirement.

- [x] 9. Align every quoted number with the evidence log, and re-verify what that touches.
  The archive grows while a run is in flight — this item's own session is written into it — so the probes were run
  once more at the end and the numbers quoted in the plan, the guideline and the two scripts are that log's
  numbers, not an earlier run's. Only comments changed after the gate; the affected suites were re-run scoped:
  `bash tools/loop-selftest.sh --group trim|adapter|state` → all green (3, 3 and 19 cases),
  `bash tools/loop-mutations.sh --check` → `all 186 cases passed`, `tools/check-openspec-hygiene.sh`,
  `tools/check-doc-routes.sh`, `tools/check-portability.sh` → OK, and
  `bash tools/loop/evidence-check.sh <this archived change> --root .` → `PASS`.

