# Tasks

The change's line numbers are the base's (`b114dc61`) where they are a claim about the defect, and the tree's as
this change leaves it where they are a claim about the fix; the red run's tally is `evidence/red-on-HEAD.xml`.

## 1. The red case

- [x] 1.1 Extend `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault` (`tools/loop-cases.sh:7080`) with the
      refusal a refused provider leaves in the child's own stream — a `turn_end` on `stopReason: error` naming
      `ollama-cloud`/`deepseek-v4.1-flash`, and an `auto_retry_end` carrying the `429 … weekly usage limit`
      `finalError` — set through `PI_STUB_EVENTS` so the adapter keeps it in `events.jsonl` and the answer stays
      empty. Verify: against the base's reading,
      `bash tools/loop-selftest.sh --case 'run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault'` →
      `FAIL … the stop names the provider and model the answer was refused by: 'ollama-cloud/deepseek-v4.1-flash'
      not found in: the child could not start — …` (2026-11-08T17:07:54Z; `evidence/red-on-HEAD.log`,
      `evidence/red-on-HEAD.xml`, `tests=1 failures=1 errors=0`).
- [x] 1.2 The case's first-line `# files:` declaration is unchanged and the declaration case accepts it:
      `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS` (11.65 s,
      2026-11-08T17:23Z); re-proven on the finalize tree → `PASS` (`evidence/green-declaration.log`).
- [x] 1.3 The sibling `run#childThatNeverStartedIsTheRunsFault` gains the negative guard — a stream with no
      `auto_retry_end` adds no refusal clause — so the delta's "leaves the reason exactly as the base wrote it"
      clause has a line that would fail a reader inventing one. Verify: `PASS` (5.12 s, 2026-11-08T17:23Z);
      re-proven on the finalize tree → `PASS` (`evidence/green-sibling-no-refusal.log`).

## 2. The engine — `tools/loop/run.sh`

- [x] 2.1 `child_refusal` (`run.sh:1389`), beside `child_turns` and `child_context`: the last refused request in the
      child's event stream, printed as `<provider>/<model>: <refusal>`, read from the last 200 lines and squeezed to
      one line; absent, unreadable or refusal-less streams print nothing. Verify: the reader over
      `.pi/issue-loop/items/85/events.jsonl` prints
      `ollama-cloud/deepseek-v4.1-flash: 429: {"message":"you (framstag) have reached your weekly usage limit…"}`
      and prints nothing over the adapter-refused-argv stream of `run#childThatNeverStartedIsTheRunsFault`.
- [x] 2.2 The stop reason names it (`run.sh:2485`, `:2490`): `refused_by=$(child_refusal "${events_file:-}")` and
      `${refused_by:+ — refused by $refused_by}` on the `stop_run`. Verify: the extended case passes
      (`evidence/green-red-case.log`, 2026-11-08T17:23:32Z): `PASS run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault
      (4.76 s)`; re-proven on the finalize tree → `PASS` (`evidence/green-red-case-finalize.log`).

## 3. The mutation record

- [x] 3.1 `tools/loop-mutations.sh` `102.1` empties the reading (`refused_by=""`); the case must go red.
      Verify: `bash tools/loop-mutations.sh --only '102.1'` →
      `RED 102.1 … the stop names the provider and model the answer was refused by: … not found` then
      `restore 102.1 … green again`, `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`
      (`evidence/mutation-102.1.log`, 2026-11-08T17:22:48Z); and `--check --only '102.1'` →
      `all 1 cases passed — every selected record applies to the file it names`. Re-proven on the finalize tree:
      `evidence/mutation-check-finalize.log`, `evidence/revert-check-finalize.log`.

## 4. The records

- [x] 4.1 `guidelines/Backlog.recipes.md` §7d "What it does with a child that never started" (`:754`): the paragraph
      gains the sentence that the reason names the provider, the model and the refusal the child's stream carries.
- [x] 4.2 The delta `specs/agent-issue-loop/spec.md` ADDS the requirement "The run's stop reason names the refusal
      it stopped on" beside the two scenarios that state the stop, and its one scenario names the case. Verify:
      `openspec validate fix-the-stop-reason-names-the-refusal --strict`.

## 5. The landing

- [x] 5.1 The run's base was `b114dc61`, which predates the finalize `main` (`f3395137`). The four touched files
      were re-applied on `main` with three-way context and carry the same change — only the hunk offsets move — and
      the focused cases above were re-run green on that finalize tree.
- [x] 5.2 The delta was merged into the shipped spec and the change directory moved by `openspec archive`:
      `openspec/specs/agent-issue-loop/spec.md` gains the one requirement, and the change now lives at
      `openspec/changes/archive/2026-11-08-fix-the-stop-reason-names-the-refusal/`. Verify:
      `openspec validate fix-the-stop-reason-names-the-refusal --strict` before the move.
- [x] 5.3 The gate this delta owes (`tools/loop/gate.sh --base main`) was run on the finalize tree; its full output,
      with `LOOP-GATE-TREE` and `LOOP-GATE`, is `evidence/gate.log`. The evidence-only commit that follows the gate
      adds no non-evidence path, so the gate's own `diff` digest still names this tree.
