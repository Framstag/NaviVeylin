# Tasks — fix-metadata-drift-check-never-runs

No compiled file is touched, so no task runs a build and no task owes a test suite; each task names the
requirement that holds the claim it verifies, and the structural check that shows it. Parent requirement:
`backlog-sensors` — "The priority pair is re-checked on a schedule" (this change's delta) and "A check is a
declared row, not a script".

## 1. Before the fix — the drift and the red case

- [x] 1.1 Verify the premise against the tree, not against the item: `check-metadata.sh`'s per-item half is only
  reachable through `--live`/`--issues` (`tools/issues/check-metadata.sh:113-124`), CI runs the token-free mode
  (`.github/workflows/build.yml:195`), the only `--live` caller is `helper/backlog-pass.sh:144`, and no registry
  row reads the pair (`jq -r '.rows[].id' tools/sensors/sensors.json` → 8 rows, none of them it)
  (`evidence/surface-head.log`). Parent: `backlog-sensors` — adding a check is a row.
- [x] 1.2 Take the drift the check would report, with the check's own live mode rather than a second copy of it:
  `bash tools/issues/check-metadata.sh --live` → `#305`, `#304` `label-without-marker`, `306 issue(s) checked ·
  2 violation(s)`, exit 1, 2026-10-25T19:22:52Z, base `0f342656` (`evidence/live-check-head.log`). Parent:
  `issue-priority` — *The label and the record agree*.
- [x] 1.3 Write the red case and run it on HEAD: `bash tools/sensor-selftest.sh --case sensors#metadataDriftRow`
  → `the registry declares no metadata-invariant row`, `FAIL`, exit 1; and
  `--case sensors#metadataDriftClearsUnderItsOwnIdentity` → `… metadata-invariant.sh not found`, `FAIL`, exit 1,
  2026-10-25T19:24:04Z (`evidence/red-case-head.log`).
- [x] 1.4 Count the surface before the plan was fixed (`evidence/surface-head.log`): one tracker-reading caller
  in the tree, one CI caller of the token-free mode, eight registry rows, two files that name a row id in a
  pipeline, and the row's own two cases — the delta is one new script, one registry entry, one `--rows` list,
  two cases, one mutation, one guideline paragraph and two header lines.

## 2. The row

- [x] 2.1 `tools/sensors/metrics/metadata-invariant.sh`: the delegation to `check-metadata.sh --live --json`, the
  per-`(issue, code)` violation, the recorded-condition clear, the row-level clear, and the two third-outcome
  paths (`unreadable-tracker-check`, `no-item-read`). Verified by 3.1 and 3.2. Parent: `backlog-sensors` — the
  three outcomes, and only clearing closes.
- [x] 2.2 `tools/sensors/sensors.json`: the row as `design.md`'s table states it. Verified by
  `bash tools/sensor-selftest.sh --case 'Registry#everyRowIsComplete'` → `PASS` (the registry's own completeness
  reader) and by 3.1's shape assertions.
- [x] 2.3 `.github/workflows/sensors.yml`: the row joins the step that runs for any record, and that step's
  comment names it. Verified by 3.1 and by `workflow#aRedRecordRunsNoRecordRow` (`declared == selected`, no row
  in both steps) → `PASS` (`evidence/focused-green.log`). Parent: `backlog-sensors` — "The checks run in a
  pipeline of their own".
- [x] 2.4 `guidelines/Backlog.md` §9: the row's paragraph, and `tools/issues/check-metadata.sh`'s `--live` header
  line names its unattended caller (its `--help` range follows the added lines). Verified by
  `bash tools/check-doc-routes.sh` in 4.1.

## 3. Verification — the cases the delta owes

- [x] 3.1 `bash tools/sensor-selftest.sh --case sensors#metadataDriftRow` → `PASS`
  (`evidence/focused-green.log`): the row's identity is `["issue","code"]`, it is selected by the record-agnostic
  row step, the measurement names `check-metadata.sh` and restates no part of the marker grammar, one drifted
  item is one violation, an agreeing item is none, a clean tracker is the row-level clear, and an unreadable read
  is `error`.
- [x] 3.2 `bash tools/sensor-selftest.sh --case sensors#metadataDriftClearsUnderItsOwnIdentity` → `PASS`: a
  still-drifted pair is no clear, and a repaired one clears under the fingerprint the issue carries
  (`8f63298d…` for `issue=278 code=label-without-marker`).
- [x] 3.3 Falsify the change's one new invariant (`revert-check`): `bash tools/sensor-mutations.sh --only 8.19`
  mutates the violation verdict to a clear → `sensors#metadataDriftRow` red, restored → green
  (`evidence/focused-green.log`).
- [x] 3.4 The registry and the credential: `Registry#everyRowIsComplete`,
  `sensors#theDocumentedCredentialCoversEveryDeclaredRow` and `sensors#engineNamesNoProjectValue` → `PASS`; the
  row's command calls no `gh` verb itself, so `Backlog.md` §9's documented scope is unchanged.
- [x] 3.5 Run the project's gate once (`./tools/loop/gate.sh --base 0f342656…`), which is the structural
  half this pathspec owes — `tools/sensors/*`, `tools/sensors/sensors.json`, `tools/issues/*`,
  `.github/workflows/sensors.yml`. The gate is red on one line: `tests=211 failures=0 errors=1` (not retained as one
  XML — it is the sum of the 23 per-suite tallies) failing `tools/build-inputs.sh --check`
  (`evidence/gate.log`). The suite the delta owns is green
  (`tools_sensor-selftest.sh.xml`: `tests="51" failures="0"`), and the one error is not this change's: the
  profile invokes `tools/build-inputs.sh` directly while the file is committed `100644`, which reproduces on the
  pristine base commit (`evidence/gate-attribution.log`: `git ls-tree 0f342656` → `100644`, and
  `tools/build-inputs.sh --check` → `exit=126`, `keine Berechtigung`). Left named rather than repaired or
  re-run (`guidelines/Build.md` §4d — quote both numbers, name the residual, do not claim the run).

## 4. Evidence and archive

- [x] 4.1 Copy every cited log into `openspec/changes/fix-metadata-drift-check-never-runs/evidence/` and into the
  run's `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/292`, and quote the gate's tallies beside the tally
  XMLs that carry them (`evidence/evidence.md`). Parent: `build-test-gate` — a green claim has a result in the
  tree. Note the change directory is gitignored (`openspec/changes/*/`), so the archive move is what makes these
  artifacts tracked.
- [x] 4.2 Pointers mechanically: `bash .pi/skills/fix-loop/scripts/evidence-check.sh
  openspec/changes/fix-metadata-drift-check-never-runs --root .` → `evidence-check: PASS` (one C3 note: the
  pristine-base probe cites an ephemeral `/tmp` extraction that one `git archive` command reproduces).
- [x] 4.3 `openspec validate fix-metadata-drift-check-never-runs --strict` → "Change 'fix-metadata-drift-check-never-runs' is valid".
- [x] 4.4 Archive the change and confirm the delta merged into `openspec/specs/backlog-sensors/spec.md` —
  `openspec archive … --yes` → `+ 1 added`, `Specs updated successfully`, archived as
  `2026-10-25-fix-metadata-drift-check-never-runs`; the merged requirement is
  `openspec/specs/backlog-sensors/spec.md` "The priority pair is re-checked on a schedule".
- [x] 4.5 Do not commit, do not push, do not touch the tracker: the run's script owns git and the labels. The
  `update.sh` refusal the owner asked for in the same decision is a later item, not this change's.
