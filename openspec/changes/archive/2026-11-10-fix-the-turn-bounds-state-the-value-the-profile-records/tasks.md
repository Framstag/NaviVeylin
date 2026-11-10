# Tasks

## 1. The red case

- [x] 1.1 Write `tools/check-doc-values.sh` (the check) and `tools/tests/check-doc-values-selftest.sh` (its cases),
      and run `tools/tests/check-doc-values-selftest.sh --case values#proseMatchesTheProfile` against the unchanged
      guideline text — or, as the record was taken, `tools/check-doc-values.sh --root <a copy of the base commit's
      guidelines/Backlog.recipes.md and tools/loop/loop.conf>`. It must fail with one
      `LOOP-CHILD-TURNS-MAX: <role> prose - config <n>` line per role; copy the log and a JUnit-shaped tally of it to
      `evidence/red-on-HEAD.log` and `evidence/red-on-HEAD.xml`.
      *Verification:* the case's exit status is 1 and each of the six roles is named with the profile's figure.

## 2. The prose

- [x] 2.1 `guidelines/Backlog.recipes.md` section 7b :406-407 points at the table below, which states the six values
      `tools/loop/loop.conf` records — worker 200, gate child 100, tail 65, rework round 150, review round 110, the
      round before the gate 110 — each beside the measurement that set it.
      *Verification:* the sentence points at the table, which states all six values.
- [x] 2.2 `guidelines/Backlog.recipes.md` section 7b :446-448 becomes one table: the role's key, the value the profile
      records, and the measurement that set it (the figures the profile's own derivation carries).
      *Verification:* `tools/check-doc-values.sh` exits 0 with `prose matches config` over six roles.
- [x] 2.3 `guidelines/BacklogTooling.md` :137-145 stops restating the six values and points at the section 7b table.
      *Verification:* `tools/look.sh '170 for the worker'` returns no match under `guidelines/` — the only matches left
      are this change's own artifacts, which quote the pre-change text — and the key row still names all six role keys.

## 3. The wiring

- [x] 3.1 `tools/loop/loop.conf`'s `LOOP_GATE_SUITES` gains pairs for `*.md`, `tools/loop/loop.conf` and the check's
      own files, each pointing at `tools/check-doc-values.sh` and at `tools/tests/check-doc-values-selftest.sh`.
      *Verification:* `tools/loop/gate.sh --plan --base 4c400715860c22ae927284a7649acc4b19ba9a87` names both commands.

## 4. The structural check

- [x] 4.1 This change touches no compiled file, so the build it would otherwise owe is replaced by the structural
      checks over the files it edits: `tools/check-doc-routes.sh`, `tools/check-openspec-hygiene.sh`,
      `tools/check-todo-references.sh`, `tools/check-portability.sh` and the new check's self-test (all exit 0, in
      `evidence/green-on-change.log`). `tools/check-rule-routes.sh` is run beside them and exits 1 on a finding this
      change does not touch — its rule "a car map that has never drawn a frame states the failure in the host content
      slot." in `UICar.md` is reached by no glob in `tools/doc-routes.json` — so that red is the tree's before this
      delta and is named rather than counted green.
      *Verification:* the five checks above exit 0; `tools/check-rule-routes.sh` names `UICar.md` and
      `tools/doc-routes.json`, neither of which this change edits.
- [x] 4.2 `tools/loop/evidence-check.sh openspec/changes/fix-the-turn-bounds-state-the-value-the-profile-records`
      reports no finding: every cited figure has its tally and every cited `path:NN` resolves.
      *Verification:* exit 0.
