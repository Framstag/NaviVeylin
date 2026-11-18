# Review — issue #957 (read-only, uncommitted tree)

Scope read: `git diff` over `openspec/specs/agent-issue-loop/spec.md`, `tools/loop/lib.sh`,
`tools/loop/prompts/{worker-gate,gate-own}.md`, `tools/loop/tests/loop-cases.sh`; the untracked archive
`openspec/changes/archive/2026-11-18-fix-a-gate-that-outlasts-its-call/`; and the seams it must satisfy —
`tools/loop/gate.sh`, `tools/loop/run.sh`, `tools/loop/loop.conf`, `tools/look.sh`,
`tools/pi-shell-refusal.mjs`, `tools/loop/tests/loop-selftest.sh`.

## Answers

1. **PASS** — `prompt#theGatePromptNamesTheWallAndTheWayThrough`. Both skeletons carry all five elements and no
   third skeleton hands a child a gate: `worker-gate.md:100-138` and `gate-own.md:12-38` carry
   `cd {{CHANGE_ROOT}} && nohup {{GATE_CMD}} > {{EVIDENCE_DIR}}/gate.log 2>&1 &`, the "tool call"/"ceiling" wall,
   `sleep 600 && tail -n 40 {{EVIDENCE_DIR}}/gate.log`, and `LOOP-GATE-REFUSED: another gate of this tree holds …`
   + exit `4`. `gate-deferred.md:1-4` and `worker-tail.md:13` both *forbid* a gate; `worker.md`/`worker-continuation.md`
   reach the gate only through `{{GATE_STEP}}`, which `run.sh`'s `gate_step_text()` (`run.sh:2540-2556`) fills from
   `gate-own.md` (`--review off`) or `gate-deferred.md` (deferred). The case
   (`loop-cases.sh:5050-5065`) loops exactly `worker-gate.md` and `gate-own.md` and asserts `tool call`, `ceiling`,
   `nohup`, `{{EVIDENCE_DIR}}/gate.log`, `sleep `, `tail -n`, `LOOP-GATE-REFUSED` — one assertion per element the
   scenario names. It can fail: `evidence/revert-check-prompt.log` drops `nohup` from `gate-own.md` and the case
   goes red, restores, goes green.

2. **PASS** — `gate#aSecondGateRefusesWhileOneRuns` still holds and is untouched. `tools/loop/gate.sh` is not in the
   diff (`git status`: only the five files above). `gate_take_tally` (`gate.sh:478-497`) prints
   `LOOP-GATE-REFUSED: another gate of this tree holds <lock> (owner <pid>)` and `exit 4` when the holder's pid is
   alive, before any tally is written; `case_gate_aSecondGateRefusesWhileOneRuns` (`loop-cases.sh:6035-6062`) asserts
   exit `4`, the lock+owner in the line, no `LOOP-GATE: tests=`, no step run, lock left as found. The new prompt text
   quotes the same string, so the two agree.

3. **PASS** — `prompt#aValueArrivesAsTheTextItWasHanded`. `lib.sh:966-984`: `shopt -q patsub_replacement 2>/dev/null`
   makes the guard correct on bash <5.2 (option absent → status 1, `subs_off` stays 0, nothing toggled); on bash 5.3.20
   here it unset the option around the loop and restores it. Verified by running the new body: value
   `x\y && z 2>&1 &` renders byte for byte, `\` literal, and `shopt patsub_replacement` reads `on` again after the
   call. No shell-option leak found: the only early exit inside `loop_render` is the `loop_die` on the `cat` of the
   template (`lib.sh:969`), which is **before** the toggle; the loop body is assignments only, so no path returns with
   the option still off (and a `loop_die` that did would end the process that owns the option anyway). Also correct
   in the direction that matters: an option found off is left off.
   Related and in the diff's favour: `HEAD` numbered the gate and the evidence-copy step both `3.`; the diff
   renumbers them 3/4/5/6 and leaves step 2c's "written after step 3" pointing at the gate, which is still step 3.

4. **PASS** — the new prompt text asks for nothing the child's policy refuses. Run through the policy that actually
   decides it, `node tools/pi-shell-refusal.mjs --command …`: `sleep 600 && tail -n 40 <…>/gate.log` → `ALLOWED`
   (`tail -n` is a range read, `pi-shell-refusal.mjs:17-20`), and
   `cd <root> && nohup ./tools/loop/gate.sh > <…>/gate.log 2>&1 &` → `ALLOWED`. No `sh -c`, no `$( … )`, no `grep`,
   `cat` or `head` is handed to the child. The "What your own shell and tools refuse" block
   (`worker-gate.md:60-80`) permits exactly these two shapes.
   Note (P2, not blocking): the sentence the poll introduces — "`grep`, `cat` and `head` over that log are the tree
   search it refuses (`tools/look.sh` and `ctx_read` answer those)" — is not what the policy does: a path under `.pi`
   is exempt (`pi-shell-refusal.mjs:22-25`, `NOT_THE_TREE`), and `EVIDENCE_DIR` is
   `$LOOP_ROOT/.pi/issue-loop/items/<issue>` (`run.sh:1186,2909`), so `grep`/`cat`/`head` over that log are
   `ALLOWED`. `tools/look.sh --path` does answer it only in project-relative form (grep's `--exclude-dir=.pi` does not
   exclude the operand — tried) and refuses the absolute path the prompt hands the child. Smallest fix: drop the
   refusal claim and keep the poll line, or say "`tools/look.sh '<re>' --path .pi/issue-loop/items/<issue>/gate.log`
   or `ctx_read` reads it". Nothing breaks: the poll itself is allowed.

## BLOCKING

- **P1 — the added requirement's second sentence states a behaviour the renderer deliberately does not have.**
  `openspec/specs/agent-issue-loop/spec.md` (new block at the end): "a `&`, a `\`, a `{{` or a `}}` in a value SHALL
  reach the rendered prompt **as itself**". `&` and `\` now do (verified). `{{`/`}}` do **not**, and must not:
  `run.sh:2551-2554` substitutes `{{GATE_STEP}}` **first** precisely because the section it inserts "*names
  `{{GATE_CMD}}` and `{{EVIDENCE_DIR}}` itself*", so those placeholders are resolved by the later pairs — which is
  what makes both prompts of this very change correct. Repro against the current `lib.sh`: template
  `A {{CMD}}` + `CMD=a {{EVD}} b` + `EVD=/e/d` → `A a /e/d b`, i.e. the value's `{{EVD}}` did not arrive as itself.
  Neither case covers it, so no case would report it. Smallest fix: narrow the sentence to the characters the change
  actually fixed and say what a brace pair in a value is for — e.g. "a `&` and a `\` in a value SHALL reach the
  rendered prompt as themselves, and a `{{KEY}}` a value carries SHALL be read only as the placeholder a later pair
  names, never as the text the pattern matched" — in the shipped spec (the archived delta is then the historical
  record of what landed, and `diff` of delta vs shipped will differ, which is expected for a later correction; if the
  change must stay byte-identical to its archive, correct both).

## Notes (report-only)

- P2, as item 4 above: the `grep`/`cat`/`head`-refused claim and the `tools/look.sh` pointer over the evidence log.
- The gate's red in `evidence/gate.log` (`failures=1 errors=1`, `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`) is
  attributed with evidence in `evidence/gate-runs.md` and reproduced on the base tree
  (`evidence/base-run-group.log`, `8b59ce84`), so it is not this delta's; no case here is vacuous (both have a
  revert-check log).
- Untracked `osmscout-client-java/bin/` is outside this change and is already covered by
  `LOOP_WRITE_IGNORE_DEFAULT`'s `*/bin`.
