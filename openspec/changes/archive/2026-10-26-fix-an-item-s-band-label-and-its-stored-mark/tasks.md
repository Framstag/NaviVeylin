# Tasks — fix-an-item-s-band-label-and-its-stored-mark

## 1. The red tests, before the fix

- [x] 1.1 `tools/issue-tooling-cases.sh` — the `band_tracker` helper (one item with a `prio/p1` label and no
  marker, one with the pair) and the three cases declared in `ISSUES_SHELL_CASES` and dispatched in
  `issues_run_shell_case`:
  `update#refusesABandLabelWithoutItsMarker`, `update#writesABandLabelWithTheBodysMarker`,
  `create#refusesABandLabelAtFilingTime`.
  Verify: `bash tools/issue-tooling-selftest.sh --case <name>` — three `FAIL` lines on the base, log
  `evidence/red-on-head.log`, tallies `evidence/red-on-head-<case>.xml` (`tests="1" failures="1"`).
- [x] 1.2 The condition, re-read rather than remembered:
  `bash tools/issues/check-metadata.sh --live` → `333 issue(s) checked · 2 violation(s)`, `#305` and `#304` both
  `label-without-marker` (2026-10-26). That is the drift's state, not a test, and it is why the change is a
  write gate rather than a repair.

## 2. The rule, in one home

- [x] 2.1 `tools/issues/lib.sh`, "the band pair": `issues_band_label_values` (the profile's namespace through the
  core) and `issues_band_pair_gate` (`band-label-unpaired`, `band-label-disagrees`, both exit 3 via
  `issues_refuse`, before any read or write).
  Verify: `bash -n tools/issues/lib.sh`, and the three cases of 1.1 turn green.
- [x] 2.2 `tools/issues/update.sh` — one call after the vocabulary gate, with the body text the call is about to
  write. `tools/issues/create.sh` — one call with an empty body.
  Verify: `bash -n` on both, then `bash tools/issue-tooling-selftest.sh` → `all 124 cases passed` (121 before the
  change + 3).

## 3. The falsification the invariant owes

- [x] 3.1 `tools/issues/metadata-mutations.sh` — `the-writer-skips-the-marker` re-based onto the gate: the patch
  empties the namespace resolution in `lib.sh` (so neither the writer nor the gate sees a band label) and the
  probe is `update#refusesABandLabelWithoutItsMarker`. `probe_marker_drift`, unreachable once the writer cannot
  be made to drift, is removed.
  Verify: `bash tools/issues/metadata-mutations.sh --only the-writer-skips-the-marker` → `the rule fired with the
  mutation in place` / `and it is quiet again after the restore`, log `evidence/mutation-671.log`; then the whole
  set → `7 of 7 mutations noticed`.

## 4. The guideline states the landed fact

- [x] 4.1 `guidelines/Backlog.md` §9 — the sentence that called the refusal "a later item" now says it landed,
  names the two reason codes and the one home, and keeps the detection/repair split as it was.
  Verify: `tools/check-doc-routes.sh` (the §9 route resolves) and `tools/check-portability.sh` (the engine names
  no project vocabulary).

## 5. Evidence and archive

- [x] 5.1 Every cited XML copied into `openspec/changes/<change>/evidence/` — `git status` after the copy shows
  the red tallies, the focused log, the mutation log and the gate log.
- [x] 5.2 `bash tools/loop/evidence-check.sh openspec/changes/<change> --root .` → no dangling pointer.
- [x] 5.3 `./tools/loop/gate.sh` — once, after the tree is final. No compiled file changed, so the gate owes the
  shell suites the pathspec names, not the Gradle build.
  Verify: the `LOOP-GATE:` line in `evidence/gate.log`, quoted in the change's evidence.
- [x] 5.4 Archive: `openspec archive <change> --yes`, then the delta's requirement present in
  `openspec/specs/issue-priority/spec.md` and the directory under `openspec/changes/archive/`.
