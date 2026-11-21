# Tasks

The change's line numbers are the tree's as this change leaves it; the base is `8e3861c4`. The red, green, mutation
and record-check runs are kept in the run's evidence directory, `.pi/issue-loop/items/965/`, and copied into the
change's own `evidence/`.

The delta writes no compiled file: it adds one assertion to a shell case and one record to the loop's mutation set,
and the change directory's own spec delta. There is no build task, and the checks it owes are the structural checks of
the files it edits (`tools/check-openspec-hygiene.sh`, `openspec validate --strict`), the one case whose body holds
the hunk, the record check, and — at the gate — the selection the diff itself derives.

## 1. The red case

- [x] 1.1 The assertion is red on the pre-correction prompt. Verify: an engine copy whose `prompts/reviewer.md` is
      `git show 41dc62e3:tools/loop/prompts/reviewer.md` — the blob that carries no exception — and
      `LOOP_ENGINE=<copy> bash tools/loop/tests/loop-selftest.sh --case prompt#reviewerCarriesTheEightItems` →
      `FAIL prompt#reviewerCarriesTheEightItems`, the case's own line `and that a refusal's LOOP-DECISION line is not
      the question the clause forbids: 'the \`LOOP-DECISION\` line a refusal owes is part of that contract' not found
      in: # Reviewer — the fixed contract`, `loop-selftest: 1 of 1 cases failed`, `exit=1`
      (`evidence/red-on-41dc62e3.log`, 2026-11-21T05:45:02Z). The copy is a full copy of `tools/` so `../issues` and
      `../pi-child-events.sh` resolve beside it, and the shared tree's `prompts/reviewer.md` is never mutated:
      `git status --porcelain tools/loop/prompts/reviewer.md` → empty.
- [x] 1.2 The case declares the file it reads. Verify: its first-line `# files:` line already names
      `tools/loop/prompts/reviewer.md` and `bash tools/loop/tests/loop-selftest.sh --case
      driver#everyCaseDeclaresTheFilesItCanReach` → `PASS` (`evidence/declaration-check.log`, 2026-11-21T05:45:40Z).

## 2. The assertion — `tools/loop/tests/loop-cases.sh`

- [x] 2.1 `case_prompt_reviewerCarriesTheEightItems` asserts the exception's own sentence, on the one template line
      that carries it (`tools/loop/prompts/reviewer.md:64`), with the comment that carries the reason the three #954
      phrases do not cover it. Verify: `bash tools/loop/tests/loop-selftest.sh --case
      prompt#reviewerCarriesTheEightItems` → `PASS`, `exit=0` (`evidence/green-on-change.log`,
      2026-11-21T05:45:09Z).
- [x] 2.2 The template is not touched: the sentence is the base's (`c2d326ef`), and the case's other three assertions
      are unchanged. Verify: `git diff --stat tools/loop/prompts/reviewer.md` → no output.

## 3. The record — `tools/loop/tests/loop-mutations.sh`

- [x] 3.1 Record `965.1` deletes the two template lines that carry the sentence, so the new assertion is what goes
      red. Verify: `bash tools/loop/tests/loop-mutations.sh --only 965.1` → `selected 1 of 432 records`,
      `RED    965.1  prompt#reviewerCarriesTheEightItems`, `restore 965.1 prompt#reviewerCarriesTheEightItems green
      again`, `exit=0` (`evidence/mutation-965.1.log`, 2026-11-21T05:45:18Z).
- [x] 3.2 The record applies to the file it names — a record whose expression names no line is the harness's own
      stale-record failure (issue #264). Verify: `bash tools/loop/tests/loop-mutations.sh --check --only 965.1` →
      `all 1 cases passed — every selected record applies to the file it names`, `exit=0`
      (`evidence/record-check-965.1.log`, 2026-11-21T05:45:37Z).

## 4. The requirement — `openspec/specs/agent-issue-loop/spec.md`

- [x] 4.1 The delta MODIFIES "A review round is non-interactive, and a question is a cut-short answer" by adding the
      scenario `A refusal's decision line is not the question the clause forbids`, whose `**Case**` is
      `prompt#reviewerCarriesTheEightItems` — a case that exists and whose assertion is 2.1. Verify:
      `openspec validate --strict fix-the-refusal-exception-is-asserted` → `is valid`, and every `**Case**` the delta
      names is a case the suite lists: `bash tools/loop/tests/loop-selftest.sh --list | grep -c
      '^prompt#reviewerCarriesTheEightItems$'` → `1`. `tools/check-doc-values.sh` exits 0;
      `tools/check-openspec-hygiene.sh` exits 1 on this tree, and none of its five `::error` lines names this change
      — they are other changes' `.openspec.yaml` (`a-case-name-resolves-to-one-harness`, `run-event-stream-is-typed`,
      `settings-taxonomy-and-surface`, `the-bounds-spend-and-budget-are-typed`, `the-print-sites-are-converted`), a
      base-red the gate subtracts, as item #978's own gate log records it
      (`.pi/issue-loop/items/978/cut-gate.log`, `base-red: tools/check-openspec-hygiene.sh:1`).
- [x] 4.2 The requirement body is the shipped one, byte for byte, except for the added scenario — a MODIFIED
      requirement that rewrote the body would change the clause this item only adds a case to. Verify: the body's
      first four lines diff clean against `openspec/specs/agent-issue-loop/spec.md:5863-5867`.

## 5. The plan the gate will run

- [x] 5.1 The plan was asked for once the case and the record were written, and its first line run before anything
      else: a delta that writes a case is refused in seconds by the one case that reads the declaration, not by the
      suite that arrives at the end of the gate (issue #792). Verify: `./tools/loop/gate.sh --plan --base 8e3861c4c0c8bff22a36ff291e30385b7497f420`
      lists `tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` first
      (`evidence/gate-plan.log`, 2026-11-21T05:46:03Z), and that line's run is `PASS`
      (`evidence/declaration-check.log`).
