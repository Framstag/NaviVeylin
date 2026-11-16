# Tasks — a skip is no gate to re-run

Ordered; each task names its own verification. The case was written before the arm was narrowed, so its red on the base
commit is a measurement and not a claim.

## 1. The case and the fixtures it realigns

- [x] 1.1 Add `run#aSkipIsNoGateToRerun` to `tools/loop/tests/loop-cases.sh`, registered in the case list and in the
      dispatcher: a state holding two `skipped` items (no change, no gate result) and one blocked item that recorded a
      change and no gate result — the list holds no `re-run the gate` line for either skip, holds one for the item with
      a change, and that line still names the transcript. Verified: red on the base commit `b865087f` with
      `tools/loop/report.sh` unmodified (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, tests=1 failures=1,
      2026-11-16T01:52:04Z), green on this tree (`evidence/green-case.log`, `evidence/green-case.xml`, tests=1
      failures=0, 2026-11-16T01:53:12Z).
- [x] 1.2 Realign `run#theGateColumnNamesThisItemsGateResult`'s two action assertions: the skipped item it builds
      records no change, so the line it asserted until now is the one this change removes. The case's own subject — the
      gate column, the empty gate field, the tally the closing item read — is untouched. Verified: the case passes
      (`evidence/realigned-case.log`).
- [x] 1.3 Give `report#anActionNamesAnItemPathThatExists`'s second item a `--change fix-demo`: the case reads the
      transcript path off the gate line, and an item with no change is asked for no gate, so the fixture — not the arm —
      was what a narrowed arm made stale. Verified: the case passes (`evidence/fixture-case.log`).
- [x] 1.4 The files the new case can be reached by, which `driver#everyCaseDeclaresTheFilesItCanReach` is the one
      reader of. Verified: the case declares `tools/loop/report.sh tools/loop/state.sh tools/pi-child-events.sh
      tools/tooling.json`, and the driver passes (`evidence/declaration-case.log`).

## 2. The arm

- [x] 2.1 `tools/loop/report.sh`: `select((.change // "") != "")` beside the existing gate-result test, with the
      comment that says which arm records a change and which does not. Verified: task 1.1's case, red without the clause
      and green with it — the clause is the only difference between the two runs of it.

## 3. The owning guideline

- [x] 3.1 `guidelines/Backlog.recipes.md:925-932`, the paragraph on the empty gate field (its home in section 7c "The gate and
      its suite"): the line is owed only for an item that had something to gate, with this item's measurement beside it.
      Verified: the two structural checks of the files this change edits and the change directory's own hygiene check —
      `tools/check-doc-values.sh` (exit 0, `LOOP-CHILD-TURNS-MAX: 6 roles, prose matches config`),
      `tools/check-doc-routes.sh` (exit 0, `all 341 section references resolve, all 7 skill references resolve`) and
      `tools/check-openspec-hygiene.sh` (exit 0) — logged in `evidence/check-doc-values.log`,
      `evidence/check-doc-routes.log` and `evidence/check-openspec-hygiene.log`. No compiled file is touched, so no
      build stands in this change's place.

## 4. The evidence

- [x] 4.1 The red and green logs, their JUnit carriers, the realigned cases' logs and the declaration log copied into
      `evidence/` when they were produced, never after, and into the item's evidence directory beside the change.
      Verified: `evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, `evidence/green-case.log`,
      `evidence/green-case.xml`, `evidence/realigned-case.log`, `evidence/fixture-case.log`,
      `evidence/declaration-case.log`.
