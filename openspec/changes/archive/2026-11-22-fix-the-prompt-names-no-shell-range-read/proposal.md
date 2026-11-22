# Proposal: fix-the-prompt-names-no-shell-range-read

## Why

**Root cause.** The block that tells a child what its own shell refuses ends by handing the refused search's
alternative back in the shell it just refused from: `tools/loop/prompts/worker.md:77` — "A range read
(`sed -n 'N,Mp'`, `tail -n`) and a filter of another command's output are not refused" — and the notice a cut result
carries repeats it, `tools/loop/prompts/worker.md:124`: `read the rest in ranges (ctx_read mode="lines:N-M", or sed
-n 'N,Mp')`. The child takes the alternative, and its own prompt is the file it goes back to hardest.

**Evidence** ⊨ (change root at base `5e77d751`, the run's base commit):

- **the string is in the tree, nine times over** ⊨ `git grep -c 'sed -n' 5e77d751 -- tools/loop/prompts
  tools/pi-trim-result.mjs` counts seven skeleton lines and one line of the module: the worker's refusal
  bullet (`worker.md:77`) and its trim-notice quote (`worker.md:124`), the same pair in `worker-gate.md:44,68` and
  `worker-tail.md:48,72`, and the reviewer's trim-notice quote (`reviewer.md:35`). `sed` is not in the policy's own
  verb list (`tools/pi-shell-refusal.mjs:50`: `["grep", "cat", "head", "tail", "ls", "find", "wc", "awk"]`), so
  nothing refuses it — which is exactly why the sentence is what a child follows.
- **the cost, from the item's own measurement** ⊨ `.pi/issue-loop/items/1007/work-package.md`: over
  `.pi/issue-loop/items` (47 items, 9 811 read-verb shell calls) the calls aimed at the child's own prompt are 675
  (6.9 %) for 2 332 026 characters at 3 447 per call — the archive's most expensive class per call, against
  `ctx_read`'s 1.5 k — taken in chunks of 100–230 lines whose ranges repeat (item #991 read the same
  `worker-prompt.md` ranges three times). `tools/loop/phase.sh` exists for this (#688: `read:own-prompt ->
  read:own-prompt` 485x across 162 items) and the worker prompt names it once.
- **the notice is the module's string, not the prompt's copy** ⊨ `tools/pi-trim-result.mjs:101-104` emits
  `[LOOP-TRIMMED: … the first N are shown — read the rest in ranges (ctx_read mode="lines:N-M", or sed -n 'N,Mp')]`,
  so a prompt that stops naming the alternative and a notice that keeps it disagree in one commit.
- **the guideline the block renders stated the alternative too** ⊨ `guidelines/BuildGate.md:115-121`, "answer a tree
  question with one call", closed its bullet with "the child's own policy refuses a shell *tree search* and names the
  alternatives: `sed -n 'N,Mp'` and `tail -n` to read a range of a known file, and a filter of another command's
  output (`… | grep -c`), still stand" — the same pair the prompt's bullet carried, in the document a child reads when
  the prompt's summary is not enough, so the rule's own text and the prompt it renders would have disagreed in one
  commit.
- **red on HEAD** ⊨ (`prompt#thePhasePromptNamesWhatItsShellRefuses`, extended by this change),
  `loop-selftest: 1 of 1 cases failed`, 2026-11-22T06:19:38Z, first failure:
  `the block names no shell range read beside the tools it hands the child (issue #1007): 'sed -n' should not appear
  in: # Worker — one item, phases A–D` (`evidence/red-on-HEAD-1007.log`). A shell suite has no JUnit XML of its own
  (`.pi/skills/revert-check`, "When the gate is a shell self-test, not Gradle"): the tally is the suite's own line,
  `loop-selftest: 1 of 1 cases failed`.

**The honest bound** ⊨ — and it is the item's, not this change's. A prompt that stops naming `sed` does not stop a
child typing it: the archive is what says advice alone fails (the paragraph has asked for a section read since #246,
and the mix moved only where a policy landed — `grep` 4 997 → 1 718 after #687 — while `sed` kept rising). So this
change is the advice half of a pair, it claims no spend it cannot see, and issue #1006 owns the refusal that would
make the habit expensive. What it can claim is the smaller thing: the prompt and the notice no longer *hand* the
alternative to a child that was just told a shell search is refused.

## What changes

- `tools/loop/prompts/worker.md:76-77`, and the same bullet's copies in `worker-gate.md:67-68` and
  `worker-tail.md:71-72`: the bullet names `ctx_read`'s own `offset` and `limit` as the range, and the
  `sed -n 'N,Mp'` arm is gone; `tail -n` goes with it, because the sentence was one carve-out for the shell read this
  bullet exists to refuse.
- `tools/loop/prompts/worker.md:124`, `worker-gate.md:44`, `worker-tail.md:48`, `reviewer.md:35`: the quoted
  `[LOOP-TRIMMED: …]` notice reads `ranges (ctx_read mode="lines:N-M")`, the string the module emits.
- `guidelines/BuildGate.md` §2: the rule the bullet renders no longer names `sed -n 'N,Mp'` and `tail -n` as how a
  range of a known file is read — it names `ctx_read`'s own `offset` and `limit` and keeps the true half, that neither
  verb is refused — and `AGENTS.md`'s two routing rows for that section's sub-sections carry its measured size
  (1.4–7.4 kB → 1.4–7.7 kB).
- `tools/pi-trim-result.mjs:101-104`: `trimNotice` drops `, or sed -n 'N,Mp'`, so the notice a child actually reads
  and the prompt quoting it are one string.
- `tools/loop/tests/loop-cases.sh`: `prompt#thePhasePromptNamesWhatItsShellRefuses` extended — the rendered worker
  prompt names no `sed -n` and does name the read that pages a range, and `trimNotice`'s own output names
  `ctx_read`'s range call alone; `trim#aResultAboveTheBoundIsTrimmedAndNamed` rewritten from `sed -n` to the call it
  now names.
- `tools/loop/tests/loop-mutations.sh`: three records — the alternative back in `worker.md`'s bullet, back in
  `trimNotice`, and back in the reviewer's quote of the notice — each reddening the extended case.
- `openspec/changes/fix-the-prompt-names-no-shell-range-read/specs/agent-issue-loop/spec.md`: two MODIFIED
  requirements, "A child is told what its own shell and tools refuse" and "A child's tool results are bounded and a
  trim is named".

## Surface

surface: 7 lines in 4 skeletons rewritten, 1 module line rewritten, 1 guideline rule rewritten, 2 `AGENTS.md` figures
updated, 1 case extended, 1 case rewritten, 4 mutation records added — `git grep -c 'sed -n' 5e77d751 --
tools/loop/prompts tools/pi-trim-result.mjs guidelines/BuildGate.md` (`evidence/surface.log`)

`tools/look.sh 'sed -n'` over the prompt skeletons, the module and the guideline the block renders: **7 lines in 4
skeletons rewritten, 1 module line rewritten, 1 guideline rule rewritten, 2 `AGENTS.md` routing figures updated, 1 case
extended, 1 case rewritten, 4 mutation records added** — `git grep -c 'sed -n' 5e77d751 -- tools/loop/prompts
tools/pi-trim-result.mjs guidelines/BuildGate.md`, plus the diff of `tools/loop/tests` and `AGENTS.md`
(`evidence/surface.log`).

## Consequence

A child that arrives at a cut result is handed one call — `ctx_read`'s `offset` and `limit` — instead of a call and a
shell alternative, and the block that refuses the tree search no longer contradicts itself two lines later. The
`sed -n` recipes in `guidelines/Build.md` §10 stay: they read a build's log, not the tree, and the bullet's scope is
"over the tree".
