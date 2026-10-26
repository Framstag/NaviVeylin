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
  `label-without-marker` (round-1 reading, 2026-10-26; the live mode needs the tracker, so it is quoted rather
  than re-taken offline). That is the drift's state, not a test, and it is why the change is a write gate rather
  than a repair.

## 2. The rule, in one home

- [x] 2.1 `tools/issues/lib.sh`, "the band pair": `issues_band_label_values` (the profile's namespace through the
  core) and `issues_band_pair_gate` (`band-label-unpaired`, `band-label-disagrees`, both exit 3 via
  `issues_refuse`, before any read or write).
  Verify: `bash -n tools/issues/lib.sh`, and the three cases of 1.1 turn green.
- [x] 2.2 `tools/issues/update.sh` — one call after the vocabulary gate, with the body text the call is about to
  write. `tools/issues/create.sh` — one call with an empty body.
  Verify: `bash -n` on both, then `bash tools/issue-tooling-selftest.sh` → `all 124 cases passed`
  (`2026-10-26T20:48:24Z`), the three cases of 1.1 among them (`bash tools/issue-tooling-selftest.sh --list |
  wc -l` → 124).

## 3. The falsification the invariant owes

- [x] 3.1 `tools/issues/metadata-mutations.sh` — `the-writer-skips-the-marker` re-based onto the gate: the patch
  empties the namespace resolution in `lib.sh` (so neither the writer nor the gate sees a band label) and the
  probe is `update#refusesABandLabelWithoutItsMarker`. `probe_marker_drift`, unreachable once the writer cannot
  be made to drift, is removed.
  Verify: `bash tools/issues/metadata-mutations.sh --only the-writer-skips-the-marker` → `the rule fired with the
  mutation in place` / `and it is quiet again after the restore`, log `evidence/mutation-671.log`; then the whole
  set → `metadata-mutations: 7 of 7 mutations noticed, and every probe quiet again`
  (`evidence/mutation-671-whole.log`, 2026-10-26T20:48:35Z).

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

## 6. Round 2 — the removal half (review round 1's blocking finding)

Review round 1 refused the change on one finding: the gate covered the added-label direction only, so
`update.sh 26 --remove-label prio/p1 --body <a body whose marker records p3>` passed and left
`marker-without-label` (`evidence/review-round-1-requested.md`).

- [x] 6.1 The red case, before the branch: two assertions inside
  `update#writesABandLabelWithTheBodysMarker` on the existing `band_tracker` fixture and its `body-p3` — the
  removal whose body still records a band is refused, and the removal whose body records none is still written.
  Verify: `bash tools/issue-tooling-selftest.sh --case update#writesABandLabelWithTheBodysMarker` →
  `a removal whose body still records a band is refused: … expected '3', got '0'` on HEAD `98135f1d`,
  `evidence/red-on-head-round2.log` and `evidence/red-on-head-round2-update_writesABandLabelWithTheBodysMarker.xml`
  (`tests="1" failures="1"`, 2026-10-26T20:46:47Z).
- [x] 6.2 `tools/issues/lib.sh`, `issues_band_pair_gate`: a removal that adds no band and carries a body whose
  marker records a band is refused with `band-label-disagrees`, before any write; the function's header comment
  states the two shapes the call decides by itself and the two that need a read.
  Verify: same case → `PASS update#writesABandLabelWithTheBodysMarker` (2026-10-26T20:47:03Z), and
  `bash tools/issue-tooling-selftest.sh` → `all 124 cases passed`, so no other write path lost a shape it needs.
- [x] 6.3 The falsification of the branch itself: its condition made unreachable
  (`&& [ -n "$marker_band" ]` → `&& false`), the new assertion red, green after the restore.
  Verify: `evidence/mutation-671-removal.log` (`20:48:27Z`) — `FAIL … expected '3', got '0'` with the mutation,
  `PASS` after it.
- [x] 6.4 The four sentences that called the pair unbreakable are corrected to what the gate refuses, and the
  requirement's sentence no longer blesses the removal: `tools/issues/update.sh:138-143`,
  `tools/issues/metadata-mutations.sh:79`, `guidelines/Backlog.md:822-828`, this change's `design.md`.
  Verify: `grep -rn 'cannot drift apart\|no write path' tools/issues/ guidelines/Backlog.md` → no hit (the one
  remaining hit in the tree is `tools/build-inputs.sh:11`, a different invariant); the shipped
  requirement is in `openspec/specs/issue-priority/spec.md` and is 410 characters, under the 500 limit.
- [x] 6.5 The guideline's sentence keeps the detection/repair split and names what stays ungated (a `--body`
  that names no band label, a hand edit on the web UI) instead of claiming the pair cannot be written apart.
  Verify: `tools/check-doc-routes.sh` → `all 154 section references resolve`; `tools/check-portability.sh` →
  `OK — 50 engines and 2 templates name no project value`.
- [x] 6.6 The residue is named rather than hidden: #305 keeps `label-without-marker` until the pass rewrites it,
  so the closure comment says so (`check-metadata.sh --live` will re-report the same fingerprint).
  Verify: `proposal.md` "Out of scope", and the run's closure comment.
- [x] 6.7 The gate, once, after the correction: `./tools/loop/gate.sh` → its own verdict line
  (`evidence/gate-round2.log:1204`): 611 tests, 0 failures, 0 errors, and the 22 per-suite tallies it wrote are
  copied into `evidence/gate-tallies/` (their tally attributes sum to those numbers;
  `tools/issue-tooling-selftest.sh`: `all 124 cases passed`, 21:03:12Z). No compiled file changed, so no Gradle
  step was owed.
  Verify: `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-26-fix-an-item-s-band-label-and-its-stored-mark --root .`
  → `evidence-check: PASS`.
