# Tasks — #1096, three readers cover the whole set

## 1. The reader's own vocabulary (`tools/issues/lib.sh`)

- [x] 1.1 `issues_set_count [open|all]` counts a scope, `issues_set_window` returns count + 1, `issues_set_covered`
      is the `issue-set-uncovered` refusal. Verification: `bash tools/check-portability.sh` stays green (no project
      value in the engine) and the three cases below refuse through the one message.
- [x] 1.2 `issues_page_records` refuses through `issues_set_covered` rather than its own copy, same code, same text.
      Verification: `bash tools/issues/tests/check-metadata-selftest.sh --case 'check#theLiveReadIsTheWholeSet'`
      still refuses on `GH_STUB_COUNT_LAG` (the decisions case in `issue-tooling-cases.sh` covers that path).

## 2. The three call sites

- [x] 2.1 `duplicates.sh`: the read is the counted set unless `--limit` narrows it, and a read that fills its
      window is refused. Verification: `bash tools/issues/tests/issue-tooling-selftest.sh --case
      'duplicates#aDuplicateOutsideTheNewestPageIsFound'` (405 items, the pair below the window).
- [x] 2.2 `check-metadata.sh --live`: read at the counted set plus one, refused when full. Verification: `bash
      tools/issues/tests/check-metadata-selftest.sh --case 'check#theLiveReadIsTheWholeSet'` (405 items, the untyped
      child among the oldest).
- [x] 2.3 `resolve-stale.sh`: read at the open set plus one, refused when full. Verification: `bash
      tools/issues/tests/resolve-stale-selftest.sh --case 'resolver#anOldItemsCitationIsStillNominated'` (405 open
      items, the stale citation in the oldest).
- [x] 2.4 `.github/workflows/sensors.yml:186` drops `--limit 400` from the scheduled sweep. Verification: the flag
      is gone from the invocation and `--limit` still parses in 2.1's case.

## 3. The stub, the guideline

- [x] 3.1 `tools/issues/tests/fixtures/gh` answers a count scoped to the states the query names. Verification: 2.3
      passes with an open-scoped count, and the pass's `[OPEN, CLOSED]` count is unchanged (2.1, 2.2 read 405).
- [x] 3.2 `guidelines/BacklogTooling.md` §9 states the whole-set read as the default and `--limit` as a narrowing.
      Verification: the section's sentence names both, and `tools/loop/evidence-check.sh` resolves the route id.

## 4. Falsification

- [x] 4.1 One mutation per reader: the pre-fix window put back with the refusal removed, the case that names that
      reader red for its own reason, the file restored byte for byte, the case green again
      (`evidence/red-when-mutated.log`, `evidence/green-on-change.log`).
- [x] 4.2 The guard on each mutation: the mutated line is printed before the case runs, so a substitution that
      never landed is visible rather than read as a case that cannot fail.

## 5. The change closes on the gate

- [x] 5.1 `bash tools/loop/evidence-check.sh openspec/changes/fix-the-issue-readers-cover-their-whole-set --root .`
      reports no finding.
- [x] 5.2 `openspec archive --yes` merges the delta and moves the change under `archive/`.
- [x] 5.3 `./tools/loop/gate.sh` run once, detached; its `LOOP-GATE:` line is the record.

LOOP-ROUTE: guidelines/BacklogTooling.md §9
