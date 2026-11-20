# Tasks — the count of cut rounds, printed beside the rate

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`, one requirement modified, one scenario added).
Nothing here compiles, so no Gradle build is owed (`guidelines/Build.md` §2, the scope block) and the structural
checks of task 4 stand in for it. The gate belongs to the gate child this run starts after the diff
(`tools/loop/prompts/worker-gate.md`), not to this session: every log below is a focused run, never a gate verdict.
Each log cited here is carried in this change's `evidence/` and in the same copy under
`.pi/issue-loop/items/936/`.

## 1. The red case, on the base

- [x] 1.1 `tools/loop/tests/loop-cases.sh:15609`: `case_report_aCutRoundIsCountedBesideTheRate` — three items,
      three round-1 passes, two of them recording the scale the cap measured (`--review-input`), then a second state
      whose one part was under the cap. Registered in `LOOP_SHELL_CASES` (`:420`) and in the dispatch (`:1207`).
      Verify: with `tools/loop/report.sh` at the base, the case fails on the first assertion — the line it prints is
      `review: 3/3 first rounds passed · 0 undecided · 0 never reviewed`, the two cut rounds invisible. Evidence:
      `evidence/red-on-HEAD-936.log` (2026-11-20T07:18:41Z, exit 1, `loop-selftest: 1 of 1 cases failed`),
      `evidence/red-on-HEAD-aCutRoundIsCountedBesideTheRate.xml`. Satisfies
      `A round the input cap cut is counted beside the rate`.
- [x] 1.2 The declaration the case owes: `bash tools/loop/tests/loop-selftest.sh --case
      'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`. Evidence: `evidence/declaration.log`. The `# files:`
      line names `report.sh`, `state.sh`, `pi-child-events.sh` and `tooling.json`, the four the body reaches.

## 2. The render

- [x] 2.1 `tools/loop/report.sh:508`–`:523`: `$it` carries `cut: ((.metrics.reviewInput // "") != "")`, the count is
      bound once (`| ([$it[] | select(.cut)] | length) as $cut`), `def cut_note` (`:510`) renders it, and the rate
      interpolates `\($cut | cut_note)`. Verify: 1.1's case goes green and prints `review: 3/3 first rounds passed
      (2 truncated)`; the uncut state keeps `review: 1/1 first rounds passed · 0 undecided · 0 never reviewed`.
      Evidence: `evidence/green-after-fix-936.log` (exit 0, `PASS`).
- [x] 2.2 The neighbours whose states record no scale stay green without an edit:
      `report#trendFiguresFromState` (`evidence/neighbour-trendFiguresFromState.log`, `PASS`) and
      `run#aBlockedItemRecordsTheRoundThatReadIt` (`evidence/neighbour-aBlockedItemRecordsTheRoundThatReadIt.log`,
      `PASS`), and the whole group the delta reaches: `--case 'report#*'` → `all 44 cases passed`
      (`evidence/focused-report-cases.log`).
- [x] 2.3 The comment above the `def` states the field it reads, who writes it (`state.sh done --review-input`) and
      what an empty record means — a state written before the field existed records no cut. Verify: read at
      `tools/loop/report.sh:506`–`:510`.

## 3. The mutation record

- [x] 3.1 `tools/loop/tests/loop-mutations.sh:1500`: record `936.1` replaces `def cut_note: …` with an empty note, so
      the line loses the count it prints. Verify: `--check --only 936.1` →
      `all 1 cases passed — every selected record applies to the file it names` (evidence:
      `evidence/mutation-check-936.1.log`).
- [x] 3.2 The same record run against the case: `--only 936.1` → `RED 936.1 report#aCutRoundIsCountedBesideTheRate`,
      `restore 936.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`, exit 0.
      Evidence: `evidence/mutation-936.1.log`.
- [x] 3.3 The table as the suite reads it: `--case 'mutation#*'` → `all 3 cases passed`, which carries
      `mutation#everyDeclaredRecordApplies` over the changed table. Evidence: `evidence/mutation-cases.log`.

## 4. Structural checks, since nothing here compiles

- [x] 4.1 `bash -n tools/loop/report.sh`, `bash -n tools/loop/tests/loop-cases.sh`,
      `bash -n tools/loop/tests/loop-mutations.sh` — the three edited files parse (no output, exit 0). Evidence:
      `evidence/bash-n.txt`. A syntax check is not what would have caught this defect and it is not offered as the
      gate: the case of 1.1 is.
- [x] 4.2 `tools/check-openspec-hygiene.sh` — this change's directory reports no finding (`.openspec.yaml` carries
      `x-issue: 936`, the delta's every scenario names a case). The run is exit 1 on five **other** open changes that
      carry no `x-issue:` — a tree state this change does not touch and cannot repair from here. Evidence:
      `evidence/openspec-hygiene.log`.

## 5. The prose the delta owes

- [x] 5.1 `guidelines/Backlog.recipes.md:1808`–`:1813`: the paragraph ("Leftovers, the report and the queue")
      that says what the rate tells apart gains
      the count, the `LOOP_REVIEW_INPUT_MAX_BYTES` key it is read from, the record field (#924), the shape the line
      prints and the case and mutation record that hold it. Verify: the sentence names
      `report#aCutRoundIsCountedBesideTheRate` and `936.1`, both of which exist at the lines this change cites.
