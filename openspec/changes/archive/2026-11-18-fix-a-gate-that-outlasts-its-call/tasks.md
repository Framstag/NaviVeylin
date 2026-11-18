# Tasks — the prompt names the wall, and the way through it

Spec: `agent-issue-loop` (delta `specs/agent-issue-loop/spec.md`, two ADDED requirements). No compiled file changes, so
no Gradle build is owed (`guidelines/Build.md` §2): the structural check of the files this delta edits — `bash -n` on
the two edited shell files, `tools/check-portability.sh` over the two skeletons it scans as templates, and
`openspec validate fix-a-gate-that-outlasts-its-call --strict` — stands in for the build, and the gate for
`tools/loop/**` is `tools/loop/tests/loop-selftest.sh`.

## 1. The two cases, red on the base

- [x] 1.1 `prompt#theGatePromptNamesTheWallAndTheWayThrough` in `tools/loop/tests/loop-cases.sh`: both skeletons that
  hand a child its gate (`worker-gate.md`, `gate-own.md`) are asserted to name the tool call, its ceiling, the detached
  start (`nohup`), the log at `{{EVIDENCE_DIR}}/gate.log`, the poll (`sleep`), the range read (`tail -n`) and the
  refusal a restart meets (`LOOP-GATE-REFUSED`). Registered in `LOOP_SHELL_CASES` and the dispatch table, declaration
  `# files: tools/loop/prompts/`.
  **Verified** red on the base's prompts — `evidence/red-on-HEAD.log`: `worker-gate.md names the call a gate can
  outlast: 'tool call' not found in: # Gate child — phase E, the item's one gate` (2026-11-18T20:08:30Z); carrier
  `evidence/red-on-HEAD.xml`, `tests=1 failures=1`.
- [x] 1.2 `prompt#aValueArrivesAsTheTextItWasHanded`: a template carrying `{{CMD}}` twice is rendered with a value
  holding `cd x && nohup ./gate.sh > gate.log 2>&1 &` plus a second, plain value, and the rendered text must hold both
  values byte for byte. Declaration `# files: tools/loop/lib.sh`.
  **Verified** red on the base's renderer — `evidence/red-on-HEAD-render.log`: `expected 'start cd x && nohup ./gate.sh
  > gate.log 2>&1 & end /e/d', got 'start cd x {{CMD}}{{CMD}} nohup ./gate.sh > gate.log 2>{{CMD}}1 {{CMD}} end
  /e/d'`.

## 2. The two skeletons

- [x] 2.1 `tools/loop/prompts/worker-gate.md`, step 3: the command block becomes the detached start
  (`cd {{CHANGE_ROOT}} && nohup {{GATE_CMD}} > {{EVIDENCE_DIR}}/gate.log 2>&1 &`) and the wall paragraph follows it —
  the ceiling, the profile's two longer clocks, the poll, the two lines that end the gate, the resume, and the refusal a
  second gate meets. The section's duplicated `3.` markers are renumbered (`4.`, `5.`) and `5.` becomes `6.`
  **Verified** by 1.1 green on this tree (`evidence/focused-case.log`, 2026-11-18T20:11Z) and by
  `prompt#thePhasePromptNamesWhatItsShellRefuses`, which reads the same skeleton (`evidence/prompt-group.log`).
- [x] 2.2 `tools/loop/prompts/gate-own.md`: the same rule for the one-child shape — the detached start and one
  paragraph naming the ceiling, the poll and the resume — so a child is never handed the gate in a shape the other
  skeleton forbids.
  **Verified** by 1.1's `gate-own.md` half green (`evidence/focused-case.log`) and by
  `gate#theGateCompletesTheChangeBeforeTheGate`, whose own assertions on this file still hold
  (`evidence/focused-cases.log`, 2026-11-18T20:11:52Z).

## 3. The renderer, which the first gate found

- [x] 3.1 `tools/loop/lib.sh`: `loop_render` clears bash's `patsub_replacement` around its substitution loop — behind
  `shopt -q`, so a bash without the option is untouched — and restores it afterwards, so a value arrives as the text it
  was handed.
  **Verified** by 1.2 green (`evidence/focused-cases.log`) and by the three run cases the first gate reded, each green on
  this tree (`evidence/gate-round-1-attribution.log`, 2026-11-18T20:36:10Z). The defect it repairs, and why the two
  other ways out were eliminated, are in `design.md`, "The renderer defect the first gate found".

## 4. Documents and structure

- [x] 4.1 `bash -n tools/loop/lib.sh tools/loop/tests/loop-cases.sh`; `tools/check-portability.sh` — its template scan is
  what a hard-coded project value in a prompt skeleton fails; `openspec validate fix-a-gate-that-outlasts-its-call
  --strict`.
  **Verified** `evidence/structural-checks.log`: `bash -n` exit 0 on both, portability `OK — 61 engines and 8 templates
  name no project value`, the validator `Change ... is valid` (2026-11-18T20:10:52Z; re-run after 3.1 — see 5.2).

## 5. Verification

- [x] 5.1 Both cases red on the base and green on the change — 1.1's and 1.2's logs, then `evidence/focused-cases.log`
  for the green side.
  **Verified** `evidence/red-on-HEAD.log` (exit 1), `evidence/red-on-HEAD-render.log` (exit 1), `evidence/focused-case.log`
  and `evidence/focused-cases.log` (`PASS`).
- [x] 5.2 The `prompt` group green, because four other cases read the same two skeletons, and the structural checks
  re-run after the renderer edit.
  **Verified** `evidence/prompt-group.log`: `all 21 cases passed ... selection: --group prompt`, 54.11 s.
- [x] 5.3 Revert-check, both invariants: remove `nohup` from `gate-own.md`'s start and 1.1 fails naming it; remove the
  `shopt` guard and 1.2 fails with the mangled value. Both restored byte-identical (sha256 equal), forced green.
  **Verified** `evidence/revert-check-prompt.log`: `…and starts the gate detached: 'nohup' not found in: **Complete the
  change before you run the gate**` (exit 1) then `PASS`; `evidence/revert-check-render.log`: `expected 'start cd x && …'
  got 'start cd x {{CMD}}{{CMD}} …'` (exit 1) then `PASS`; `sha256` of `gate-own.md` `f6e7e37a…` and of `lib.sh`
  `cb6ec689…` equal before and after each restore.
- [x] 5.4 The gate the change owes (`tools/loop/gate.sh`, which selects the profile's `tools/loop/**` steps) — run from
  the change root and quoted with what it recorded.
  **Verified** in three runs, all in `evidence/gate-runs.md`: round 1 `LOOP-GATE: tests=1157 failures=4 errors=0` — it
  reded and found 3.1, the renderer defect (`evidence/gate-round-1.log`, `evidence/gate-round-1.xml`); round 2
  `tests=1158 failures=1 errors=0`; round 3 over the **archived** change `tests=1165 failures=1 errors=1`
  (`evidence/gate-round-3.log`, `evidence/gate-round-3.xml`). The one remaining red in rounds 2 and 3 is
  `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, a pool-scaled deadline this host cannot meet at sixteen-way
  concurrency: it passes alone on this tree twice, and it fails identically on a **base tree carrying no delta**
  (`evidence/base-run-group.log`, `1 of 166 cases failed`, 2026-11-18T21:31Z) — pre-existing, not this change's.

## 6. The review, and what it corrected

- [x] 6.1 A fresh, read-only reviewer was handed the diff and the delta's two requirements; its report is
  `evidence/review-round-1-full.md` and the table of verdicts is `evidence/review-round-1.md`.
  **Verified**: four items PASS (the two cases cover their scenarios; `gate#aSecondGateRefusesWhileOneRuns` still holds;
  the `shopt -q` guard is correct on a bash without the option and leaks nothing; both new commands answer `ALLOWED`
  through `tools/pi-shell-refusal.mjs`), one blocking finding.
- [x] 6.2 The blocking finding: the renderer requirement promised that a `{{`/`}}` in a value arrives as itself, which
  the engine deliberately does not do (`gate_step_text` substitutes `{{GATE_STEP}}` first so its section's placeholders
  resolve). The requirement now states the rule the engine has, and the shipped spec and the archived delta carry the
  same text.
  **Verified** by re-running `openspec validate agent-issue-loop --type spec` (the two added bodies are 426 and 472
  characters, under the validator's 500, and the warnings it reports are the ones the base already carried) and by the
  `prompt` group green on the corrected tree (`evidence/prompt-group-round-2.log`, 22 cases, 47.51 s).
- [x] 6.3 Its report-only finding: the poll paragraph claimed `grep`/`cat`/`head` over the gate's log are refused,
  while the log sits under the item's directory, which `tools/pi-shell-refusal.mjs` exempts as not the tree. Both
  skeletons now say what is true (a wait and a range read of a file under the item's directory).
  **Verified** by the same `prompt` group run — the case's `sleep ` and `tail -n` assertions still hold on both
  skeletons.
- [x] 6.4 The gate over the corrected tree — round 4.
  **Verified** `evidence/gate-round-4.log`, 2026-11-18T21:50Z: `LOOP-GATE: tests=1165 failures=1 errors=1`, the one red
  the pre-existing `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`, exactly as round 3 measured it; nothing else in the
  corrected tree moved.
