# Tasks

Reference: `proposal.md` for the root cause and the red run, `specs/agent-issue-loop/spec.md` for the contract, and
`design.md` for the one fix and the alternative the evidence eliminated.

The measure block this change was planned against (`./tools/loop/measure.sh --change
fix-a-withheld-volume-prints-its-share`, `evidence/measure-block.log`): `LOOP-MEASURE-SUITE cases=427
cmd=tools/loop/tests/loop-selftest.sh --list`, `LOOP-MEASURE-CHANGE fix-a-withheld-volume-prints-its-share scenarios=2
cases=2 unnamed=0`, and `LOOP-MEASURE-SURFACE surface: 1 report clause and its comment rewritten, 1 case rewritten with
1 phase added, 1 mutation record added, 2 guideline statements realigned, 1 requirement modified`. The block read over the
run's base after the archive (`./tools/loop/measure.sh --base d25972c1 --change fix-a-withheld-volume-prints-its-share`,
`evidence/measure-block-round2.log`) carries that surface line whole — one line, as `measure.sh` reads it — and names the
change as `2026-11-14-fix-a-withheld-volume-prints-its-share`. Its
`LOOP-MEASURE-CHECK` lines are the gate's selection and this pass runs none of them as a gate: the case it rewrites,
the two mutation records it keeps and adds, and the structural checks a delta that compiles nothing owes.

## 1. The red case, before the fix

Requirement: "A run counts the reads its sessions repeated", its first scenario.

- [x] 1.1 `tools/loop/tests/loop-cases.sh`, `report#anItemRecordsItsRepeatReads`: the `reads:` assertion expects the
      withheld volume as a share of the repeat results — `60 chars withheld by the repeat rule (66.7 % of the repeat
      results)` — and a phase is added after it that zeroes `dupChars` on a copy of the state and asserts the reason an
      empty repeat volume prints instead of a share of zero. The case reaches one file more than before and no file it
      did not: its first-line `# files:` declaration already names `tools/loop/report.sh` and is unchanged.
- [x] 1.2 The red run on the base `d25972c1`, this case and nothing else: FAIL —
      *'…60 chars withheld by the repeat rule (66.7 % of the repeat results)…' not found in:   reads:  3 of 7 file reads
      were a second read of a path the session already had (43 %) · 90 chars in the repeat results · 60 chars withheld by
      the repeat rule · worst #21 2 of 5* — at 2026-11-14T04:19:42Z. `evidence/red-on-head.log` is that run and
      `evidence/red-on-head.xml` its tally (`tests=1 failures=1 errors=0`).

## 2. The fix

Requirement: the same.

- [x] 2.1 `tools/loop/report.sh`: the branch that builds `$wline` prints the withheld volume and, in the same clause,
      `(<pct> % of the repeat results)` — `$w|add` over `$tc`, one decimal — and where `$tc` is 0 prints the volume with
      the reason it carries no share, rather than a `jq` division by zero that would fail the whole report.
- [x] 2.2 `tools/loop/report.sh`: the block comment above the line names the share the figure now is, the above-100 %
      reading the item's ledger allows, and the window — a path read in ranges of itself is counted as repeats, so the
      repeat volume is what the rule saw and not volume it saved, and a low share is not a rule that failed.
- [x] 2.3 `tools/loop/tests/loop-mutations.sh`: `96.4` mutates the share away (`% of the repeat results` →
      `of the repeat results`), because the case now fails on a line that loses it; `96.2` still applies to the clause
      it quotes.

## 3. The guideline and the delta

- [x] 3.1 `guidelines/Backlog.recipes.md` section 7a: the paragraph that says what the report prints carries the share, and
      the sentence about the repeat being counted per session gains its other half — per path and not per range, with
      the window's reading of a low share.
- [x] 3.2 `guidelines/Backlog.recipes.md` section 7e: the list of figures the report prints names the withheld volume as a
      percentage share of the repeat results, with issue #866.
- [x] 3.3 this change's own `specs/agent-issue-loop/spec.md` (worked under `openspec/changes/fix-a-withheld-volume-prints-its-share/`,
      archived as `openspec/changes/archive/2026-11-14-fix-a-withheld-volume-prints-its-share/`): the requirement
      gains the share, the above-100 % reading and the empty-denominator clause, and the first scenario's `THEN` and one
      `AND` carry them; the case that names it is the case of 1.1.

## 4. Verification

- [x] 4.1 The case green after the fix: `bash tools/loop/tests/loop-selftest.sh --case
      report#anItemRecordsItsRepeatReads` → `PASS`, 2026-11-14T04:22:25Z (`evidence/green-report.log`), and green again
      in this round's own run (`evidence/green-report-round2.log`).
- [x] 4.2 The two mutation records: `--only 96.4` → `RED 96.4 report#anItemRecordsItsRepeatReads`, restore green;
      `--only 96.2` → `RED 96.2`, restore green (`evidence/mutations-96.log`).
- [x] 4.3 A delta that compiles nothing owes the structural checks of the files it edits, and no gate:
      `driver#everyCaseDeclaresTheFilesItCanReach` PASS, `tools/check-portability.sh` OK (60 engines, 8 templates),
      `tools/check-openspec-hygiene.sh` OK (23 open changes), `openspec validate --strict` → *Change
      'fix-a-withheld-volume-prints-its-share' is valid*, and `tools/loop/tests/loop-mutations.sh --check` → *all 39
      cases passed* over the three files this change touches (`evidence/structural-checks.log`).
- [x] 4.4 The gate is this child's: the change is archived at
      `openspec/changes/archive/2026-11-14-fix-a-withheld-volume-prints-its-share/`, so the archive stands before the one
      gate run and the tree the gate names is the tree the round reads. The gate runs over the run's base
      (`--base d25972c1`), so its identity is derived over the paths the round's diff carries
      (`guidelines/Backlog.recipes.md` §7c) — the comparison the reviewer's third item makes. The measure block over that
      base is `evidence/measure-block-round2.log`.
      That gate is in the tree as `evidence/gate-run-round2.log`: its `LOOP-GATE-TREE` line records
      `head=3fbec6ee… diff=cb188f93…` — the round's `diff=` field for field — its `LOOP-GATE-TALLY` line records
      `run=919045`, and its failing step is `tools/loop/tests/loop-selftest.sh --from-git d25972c1…`, the base this
      round reads. `evidence/gate-run.log` is the earlier gate, which ran over the stale `fcbe328d` (`--from-git fcbe328d`,
      `run=2360277`); round 1's identity mismatch came from it and nothing else. The two runs' per-step tallies are kept
      apart by directory, each XML under the `run=` value it was written with and never edited: `evidence/gate-xml/` is
      run `2360277` and `evidence/gate-xml-round2/` is run `919045` — the second under a name that does not collide with
      the first, because `app/build/test-results/loop-gate` is one directory every gate run reuses and the round's copy
      had already replaced run 2360277's there.
- [x] 4.5 `tools/loop/evidence-check.sh` over the archive after those copies (`evidence/evidence-check-final.txt`): the
      round-2 tallies are now carried by `evidence/gate-xml-round2/`'s XMLs, and the one pair it still reports is the
      gate's total (1 068 tests, 1 failure), which is the sum over the gate's steps and so is carried by no single XML
      under either directory — both directories' XMLs sum to it, and that figure is unchanged by this round's gate.
      `evidence/evidence-check-round2.txt` is the run before the copies and records the same one figure as `C2 DRIFT`.
