## 1. The resolution moves, and nothing about it changes

- [x] 1.1 Read `tools/loop/evidence-check.sh` C6 (lines 185–240) and `tools/loop/lib.sh`'s tail, and state in the
      change's evidence the three functions' signatures before writing them. The signatures the restatement was
      written to: `loop_route_lines <dir>` → the anchored `LOOP-ROUTE:` lines of the markdown under `<dir>`;
      `loop_route_ids <doc>` → the ids the document's own `## ` headings carry, one per line; and
      `loop_route_resolves <root> <doc> <id>` → 0 when `<doc>` exists under `<root>` and carries `<id>`. Which
      documents may be cited at all — the guideline directory — stays with the caller, because it is the project's
      configuration and `tools/check-portability.sh` forbids a project value in an engine.
- [x] 1.2 Add `loop_route_lines`, `loop_route_ids` and `loop_route_resolves` to `tools/loop/lib.sh`, carrying the two
      `sed` heading shapes and the anchored line rule C6 applies today, verbatim. The functions are
      `lib.sh:711`, `lib.sh:724` and `lib.sh:733`; the anchor rule moved as `grep -nE '^[[:space:]>*-]*LOOP-ROUTE:'`
      over the markdown a `find` lists, which is the expression C6 read.
- [x] 1.3 Restate C6 onto the three functions, keeping every verdict string and both counts. C6 now reads
      `route_lines=$(loop_route_lines "$change")` (`evidence-check.sh:204`) and closes its loop on
      `done <<<"$route_lines"` (`evidence-check.sh:231`); the four `FAIL`/`WARN` texts, the `ok   C6 route: …` count
      and the `note` arms are the same strings. `evidence-check.sh --selftest` prints **23** `ok` arms before and
      after (the plan said 24; the count the file prints is 23) and `evidence-selftest: PASS` on both —
      `evidence/evidence-selftest-before.log`, `evidence/evidence-selftest-after.log`.
- [x] 1.4 Confirm no project value entered the engine: `tools/check-portability.sh` exits 0 over its 67 files and
      `tools/tests/check-portability-selftest.sh` passes all 31 cases — `evidence/portability.log`.

## 2. The report prints the figure

- [x] 2.1 Add the `route:` line to `tools/loop/report.sh` beside the `bound:` line: numerator, denominator, share
      through `bound_share`, and the archive clause in the one-pass shape the bound rate uses
      (`report.sh:493`; the absent-directory arm at `report.sh:482`). The line it prints on this tree's archive:
      `route: 0 of 0 item(s) whose report routes (-) · archive: 28 of 28 (100.0 %)` — `evidence/report-route-line.log`.
- [x] 2.2 Resolve each item's change directory live first and archived second, and print `-` with the reason when the
      configured guideline directory is absent. The directory is looked up in an index `route_dir_index`
      (`report.sh:432`) builds once over the change family's live and archived directories, each name passed through
      `loop_change_path` — the one derivation that strips the archive's prefix — with the live directory assigned last
      so it wins. That is the order the plan asks for, and it is an index because the per-item form was measured:
      calling `loop_change_archive_dir` per item walked the archive's 788 directories once per unresolved name and the
      whole report took 2 m 19 s, against 3.9 s with the index. A record keeps the spelling the change had when the
      item started, so the archived directory's own name is aliased to the same key; without that alias the archive's
      figure read 22 of 22 instead of 28 of 28, every record that kept the archived spelling being lost.
- [x] 2.3 Record the printed line over the run states the tree holds (`.pi/issue-loop/runs/`), and state the figure
      the run of 2026-11-27 answers beside #974's 55 % baseline. The line prints on every state the tree holds and on
      the live one — `evidence/report-route-line.log`; the population behind it, counted over the 238 archived run
      states — `evidence/archive-counts.log`: 219 item records, 135 naming a change, 97 whose change directory the
      tree still holds, **28 carrying a `LOOP-ROUTE:` line, and 28 of those 28 resolving** (100.0 %). The figure #974's
      "Measure after" asks to move is therefore 28 of 28 from here on; the 55 % baseline counted a different
      population — the items that wrote a route line at all — and on the same tree 28 of the 97 items whose change is
      still held (28.9 %) carry one, which is the number that has to rise.

## 3. The cases and the falsification

- [x] 3.1 Add `report#theRouteShareIsPrinted` to `tools/loop/tests/loop-cases.sh` with its `# files:` declaration — a
      state of three items: one resolving route, one `§99`, one naming no change — asserting `route: 1 of 2 item(s)`,
      the `50.0 %` share and the `archive: -` clause (`loop-cases.sh:16401`). Green: `evidence/cases-green.log`.
- [x] 3.2 Add `report#anItemWithNoRouteIsOutsideTheCount`, asserting the denominator is zero, the share prints `-`, and
      a project with no guideline directory prints the dash with the reason (`loop-cases.sh:16428`).
- [x] 3.3 Add `report#theRouteShareNamesItsDenominator`, asserting a change found only under the archive is counted —
      and recorded under the archive's own spelling, which is what makes the alias falsifiable
      (`loop-cases.sh:16457`).
- [x] 3.4 Add one record to `tools/loop/tests/loop-mutations.sh` that makes `loop_route_resolves` answer true for every
      id (`1113.1`), and record that both `report#theRouteShareIsPrinted` and
      `check#aRouteSectionResolvesAgainstItsDocument` fail on it: one mutation of one function, both readers red, and
      both green again after the byte-for-byte restore (`sha256 48ec6deacc2e…`) —
      `evidence/mutation-one-record-two-readers.log`. The check's own arm is held by `974.1`, restated onto the
      comparison inside `loop_route_resolves` because the line it quoted moved there; all three records redden and
      restore (`evidence/mutations.log`).
- [x] 3.5 Run each new case red on the base commit before the change's line exists, and record the run. All three fail
      on the **absent** line — `'route: 1 of 2 item(s)' not found in: ` with the base commit's `report.sh` restored
      byte for byte afterwards (`evidence/base-red.log`).
- [x] 3.6 `bash tools/loop/tests/loop-mutations.sh --check` is green over all 472 declared records
      (`evidence/mutations-check.log`), and `bash tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` is green over the three new cases. The whole set — the gate this
      change owes as a delta under `tools/loop/**` — ends **581 of 582 cases green in 635 s** on its first run and
      **580 of 582 in 794 s** on its second (`evidence/suite-full.log`, `evidence/suite-full-first-run.log`), and no
      failure either run named is this change's. The one both runs carry is `usage#noUnguardedValueFlagRemains`,
      which names `tools/route-hints.sh` and `tools/route-steps.sh` — two files `git status` lists as untracked and
      that no hunk of this change's diff touches, another session's work in the same tree. The other is a single
      pool-run flake (`driver#aDeclarationNamingAnUnknownFileFails`, `run#aCutRunsItsGateOnTheKeptWork`, one per run)
      and each passes alone and in its group (`evidence/suite-full-cases-rerun.log`), under a machine a second session
      is running its own suites on. The harness's own leg is green too: `tools/loop/tests/loop-mutations-selftest.sh`
      passes all 40 cases with the file-set leg pointed at `evidence-check.sh` (`evidence/mutations-selftest.log`),
      which is the file the declaration now gives two records to.

## 4. The records the tree keeps

- [x] 4.1 Raise `tools/loop/layout.tsv`'s `lib.sh` and `report.sh` bounds only if a file's length passes its recorded
      bound. Neither passed: `lib.sh` is 1 334 lines against its 1 520 and `report.sh` 1 309 against its 1 320, so no
      bound row moved. `driver#noEngineFileExceedsItsBound` is green.
- [x] 4.2 Name the new functions in the table's own columns: `lib.sh`'s `owns` cell gained `loop_route_lines`,
      `loop_route_ids` and `loop_route_resolves`, and `report.sh`'s gained `route_dir_index` and `route_counts_of`.
      The `owns` column is the one the table's rule asks of a function, and `driver#anOwnedFunctionIsInItsOwnersFile`
      is green in both directions. Two further declarations followed from the read a new caller creates:
      `tools/loop/shared.tsv` gained rows for the three functions and for `loop_change_path`, and
      `loop_change_family`'s row gained `report.sh` — `driver#theSharedNamesAreDeclared` and
      `driver#aCallToALaterFilesFunctionFails` are green. `evidence-check.sh` sources `lib.sh` through the `$here`
      form those cases read, so the engine's own derivation sees the call.
- [x] 4.3 `openspec validate the-route-share-is-printed --strict` is green (`evidence/validate.log`), and the change's
      own `tools/loop/evidence-check.sh` run is green — `1 of 1 route line(s) resolved`, `evidence-check: PASS`
      (`evidence/evidence-check.log`). It was red when the change was read: two dangling section tokens, in
      `proposal.md` and `design.md`, name a backlog section the migration's table does not carry, and both were
      rewritten as prose about the loop's procedure. `tools/check-case-names.sh` is **red for a reason this change does not own**:
      `openspec/specs/auto-smooth-follow/spec.md:257` and `:264` cite `auto#theCarOverlayOffsetIsTheFramesShift`, which
      no harness lists, and neither the spec nor the case list is in this change's diff
      (`evidence/case-names.log`).
- [x] 4.4 State in the change's evidence the share #974's "Measure after" asked to be moved, so the parent can be
      closed with the figure it names: **28 of 28 archived items whose change carries a route line resolve (100.0 %)**,
      and on the live tree the same figure is `-` because no item of the run in flight has a change yet —
      `evidence/archive-counts.log` and `evidence/report-route-line.log`.
