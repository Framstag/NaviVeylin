# Tasks — the cut index's write is pinned by the case that names it

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`, one requirement modified). Nothing here compiles,
so no Gradle build is owed (`guidelines/Build.md` §2, the scope block) and the structural check of task 4.1 stands in
for it. The gate belongs to the gate child this run starts after the diff (`tools/loop/prompts/worker-gate.md`), not
to this session: the evidence below is focused and read, never a gate verdict. Every log cited here is carried in this
change's `evidence/` and in the same copy under `.pi/issue-loop/items/924/`.

## 1. The case runs its own block

- [x] 1.1 `tools/loop/tests/loop-cases.sh:4864`: the message beside the `[ -f "$diff" ]` check becomes a
      double-quoted string, so the apostrophe in `round's` is a letter and the lines under it are commands again —
      the index's existence check (`:4865`), the prompt's bound line, the head marker, the index's entries and the
      state record. Verify: the case is green on this tree and **red** when the index's write is redirected.
      Satisfies `The case that names a cut round fails when the index is not written`.
      Evidence: `evidence/focused-case-after-fix.log` (green), `evidence/red-924.2-after-fix.log` (red under `924.2`).
- [x] 1.2 `tools/loop/tests/loop-cases.sh:4871`: the cap-figure assertion reads the file the sentence lives in —
      `assert_contains "$(cat "$cut")" 'Bounded at 64 bytes a part'`, which `tools/loop/run.sh:1826` writes into
      `review-1.cut.md`, not the prompt's scale line (`run.sh:1841`). Verify: 1.1's green run, which fails here when
      the fold is closed and this line is left aimed at `$text`. Evidence: same two logs.

## 2. The record, and its falsification

- [x] 2.1 `tools/loop/tests/loop-mutations.sh:1370`: record `924.2` (written by the item's first process) is kept,
      and its comment (`:1361`–`:1368`) names both mechanisms and the two measurements. Verify:
      `bash tools/loop/tests/loop-mutations.sh --check --only 924.2` → `all 1 cases passed — every selected record
      applies to the file it names`; the full `--check` is 4.2.
- [x] 2.2 The arm the item was filed on, over the base's own case text: `--only 924.2` →
      `GREEN 924.2 … the case did not notice the mutation`, `red-when-mutated: 0 · not-noticed: 1 ·
      restore-failures: 0`, exit 1. Evidence: `evidence/red-924.2-on-base-case.log` (2026-11-20, base `fcdc7ea8`).
- [x] 2.3 After the repair: `bash tools/loop/tests/loop-mutations.sh --only '924.1 924.2'` → both **RED**, both
      `restore … green again`, `red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0`, exit 0.
      Evidence: `evidence/red-924.2-after-fix.log`.

## 3. The cases the delta can reach

- [x] 3.1 `bash tools/loop/tests/loop-selftest.sh --case 'prompt#reviewerInputsAreBoundedAndTheCutIsNamed'` →
      `PASS … all 1 cases passed`. Evidence: `evidence/focused-case-after-fix.log`.
- [x] 3.2 `bash tools/loop/tests/loop-selftest.sh --case 'mutation#*'` → `all 3 cases passed`, which carries
      `everyDeclaredRecordApplies` over the changed table. Evidence: `evidence/mutation-cases.log`.
- [x] 3.3 `bash tools/loop/tests/loop-mutations.sh --check` → `all 429 cases passed — every selected record applies
      to the file it names`. Evidence: `evidence/mutation-check-all.log`.
- [x] 3.4 The declaration of the repaired case is unchanged: `:4823` still names the engine scripts and fixtures the
      body reaches, and the two repaired lines add no path to it. `loop-cases.sh` is the one path a hunk is placed in
      rather than named (issue #917), so the hunk owes this case and nothing else.

## 4. Structural checks, since nothing here compiles

- [x] 4.1 `bash -n tools/loop/tests/loop-cases.sh` and `bash -n tools/loop/tests/loop-mutations.sh` — the two edited
      files parse. Evidence: `evidence/bash-n.txt` (no output, exit 0). A syntax check is **not** what would have
      caught this defect and the change says so: the quotes balance again inside the function, so `bash -n` was clean
      over the folded body too.
- [x] 4.2 `bash tools/loop/evidence-check.sh openspec/changes/fix-the-cut-index-write-is-pinned` → `evidence-check:
      PASS`, every pointer it cites resolving. Evidence: `evidence/evidence-check.txt`.
- [x] 4.3 `bash tools/check-change-readiness.sh openspec/changes/fix-the-cut-index-write-is-pinned` →
      `LOOP-CHANGE-READY: ok`. Evidence: `evidence/change-readiness.txt`.
- [x] 4.4 `openspec validate fix-the-cut-index-write-is-pinned --strict` → `Change … is valid`. Evidence:
      `evidence/openspec-validate.txt`.
- [x] 4.5 `./tools/loop/measure.sh --base fcdc7ea875ccd6c203cc2ebbf2f60ec313430229 --change
      fix-the-cut-index-write-is-pinned` → `paths=2 insertions=12 deletions=6`, the two test files and nothing else.
      Evidence: `evidence/measure.txt`. The base is passed explicitly: `--change` alone resolved `9367d1c0` and an
      86-path delta that belongs to another change — named as a `NOTE:` in the report.

## 5. The gate (not this session's)

- [ ] 5.1 The gate is the gate child's, by the item's own rule (issue #837): the change is completed and archived
      **before** the gate, and the gate child runs `./tools/loop/gate.sh` over that completed tree. This session ends
      at the diff, so no `LOOP-GATE` line is printed here and none is owed by these tasks.
