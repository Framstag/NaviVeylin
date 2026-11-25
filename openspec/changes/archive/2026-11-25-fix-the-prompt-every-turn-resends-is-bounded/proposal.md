# Proposal — the prompt every turn re-sends is smaller and bounded

## Why

**Root cause.** The composed worker prompt is byte-identical for every item and is re-sent with **every turn** of the
worker it starts, so its size is not a cost paid once: it is paid at every turn of every item, and nothing in the tree
recorded the number. The issue measured it over the 8 newest items: 2 523 turns at a 15 649 tok/turn baseline, of which
about 11.6 k tokens was the prompt — ~29 Mtok of the 90.6 Mtok night, ~26 % of all spend — for text that is the same in
every item. Beside it, the review-round budget was already a recorded value
(`run#reviewBudgetIsARecordedValue`), so the one figure that entered every turn was the one nobody counted.

**Evidence** (issue #1082; `evidence/measure.log`): `tools/loop/prompts/worker.md` was 27 516 B, and the prompt the run
composed from it for item #1030 is **32 580 B** (`wc -c .pi/issue-loop/items/1030/worker-prompt.md`, read-only; the same
template rendered at the commit this fix lands on measures **32 674 B**, the 94 B being the sentence issue #1083 added
to the shell-refusal block). Three of its sections are consulted rather than obeyed on every turn — where the
project's rules are, what the child's own shell and tools refuse, and how a measurement is taken and independent calls
batched — and together they were the bulk of the file.

**What moved, and what did not.** Those three blocks moved **verbatim** into one file,
`tools/loop/prompts/worker-reference.md` (9 443 B), which the run renders beside the prompt and which the prompt names
as `{{WORKER_REFERENCE}}`: read once, when the question arises, and never re-sent with the prompt. The prompt keeps the
short form of each rule and the whole of what has to change behaviour before a question can be asked: the document map
and the section-list recipe, every verb `tools/pi-shell-refusal.mjs` exports with the call that answers a refused tree
search, `ctx_read`'s own `offset`/`limit` for a range, the trimmed-result rule, and the rule that independent calls
belong in one turn with the `tools/loop/phase.sh` split.

**The size after the move.** On item #1030's own values the composed prompt is **27 112 B**, **5 562 B less per turn**;
on the case's own throwaway project (deeper paths, so a larger render) it is **28 389 B against the 33 726 B** the same
fixture rendered before the move. The template fell from 27 516 B to 21 789 B, and the 9 443 B the prompt no longer
carries are charged once per item instead of once per turn.

**The recorded bound.** `LOOP_WORKER_PROMPT_MAX_BYTES` (`tools/loop/loop.conf`, **29 000**) is read — never sourced — at
the two sites `run.sh` renders a worker prompt, and `worker_prompt_bound_check` (`child.sh`) measures the file the run
has just rendered: a prompt over it is one line in the run's own record and console (`worker-prompt-over-bound`,
`events.tsv`) naming the size and the bound, not a silence. `state.sh init` records the number as
`run.workerPromptMaxBytes` beside the three bounds of #815, and `state.sh show` and `report.sh` print it with them. `0`,
or a configuration that records no key, is no bound — the value every reader of these keys already treats as one.

**The red, on the base.** `run#theWorkerPromptFitsItsBudget` (`tools/loop/tests/loop-cases.sh`) on `23deb8c3` with only
the two case files checked out over it:

```
run#theWorkerPromptFitsItsBudget|   loop.conf records no LOOP_WORKER_PROMPT_MAX_BYTES — the size every turn of an item re-sends has no bound to be read against
--- run#theWorkerPromptFitsItsBudget[0] (exit 1)
FAIL run#theWorkerPromptFitsItsBudget
loop-selftest: 1 of 1 cases failed
```

and, with the key added to the base's `loop.conf` and the prompt **not** moved, the case's own measurement:

```
run#theWorkerPromptFitsItsBudget|   the composed worker prompt is 33726 B, over the recorded bound of 29000 B (LOOP_WORKER_PROMPT_MAX_BYTES), and every turn of the item re-sends it
loop-selftest: 1 of 1 cases failed
```

(`evidence/red-on-base.log`, both runs verbatim.)

**The bound it does not claim.** The 5 562 B saved on the item's own render is a saving per turn, not a claim about
tokens or turns: the moved text is still read, once per item, so the net is the −10…−13 % of tokens the issue states
and not the −26 % its "half the prompt" arithmetic would give, and the change takes no position on how many turns an
item takes or on `LOOP_ITEM_CALLS_PER_TURN_MIN`. It also does not claim the prompt is now small — only that its size is
a number a run records and a line a growth crosses.

**Two figures the fix's own text prints do not re-render, and this landing records the measured ones.** The fix commit's
message states `32 580 B → 27 073 B (−5 507 B)` and the comment over the key in `loop.conf` states "fell to 26 385 B"
with "10 130 B it no longer carries". Neither reproduces here: rendered through the loop's own renderer over one value
set the pair is 32 674 → 27 112 (−5 562), the case's fixture renders 33 726 → 28 389, and the archived item's own
prompt is 32 580 B. The measured figures are what this archive's evidence holds; the two sentences in the merged fix
are a number to correct in a change of their own, not here, and the headroom matters to that correction — the bound is
**611 B** above the case's own render, not the ~1 275 B the comment implies.

**The requirement this defect contradicts** is `agent-issue-loop`'s "A child carries its own route, and not the
project's whole document" (`openspec/specs/agent-issue-loop/spec.md`): it required the phase prompt to carry the
routing and said nothing about what it does not carry and nothing about its size. It now carries the two scenarios
below, written by the fix commit itself.

## What Changes

- **`tools/loop/prompts/worker-reference.md`** (new) — the long form of the three blocks the worker consults, moved
  verbatim: the rule routing, the four families the child's shell and tools refuse with the exemptions that stand, the
  measure block with the selection split, the number-in-the-record rule, the repeat-read rule and the batching rule.
  Its own header says it is rendered once per item, that it is not re-sent with the prompt, and that nothing in it is a
  second rule.
- **`tools/loop/prompts/worker.md`** — the three sections are replaced by the pointer section
  ("Where the rest of this prompt's rules are", naming `{{WORKER_REFERENCE}}`), a short "The route, and one turn for
  the independent calls" section, and a compressed shell-refusal block that keeps every refused verb, the read call
  that answers one, and the trimmed-result rule.
- **`tools/loop/run.sh`** — renders the reference once per item with the same `KEY=VALUE` pairs as the prompt, names it
  in the item's prompt and in the round-2 prompt, and calls `worker_prompt_bound_check` at both render sites.
- **`tools/loop/child.sh`** — `worker_prompt_bound_check`: measures the rendered file, reads the bound, and emits
  `worker-prompt-over-bound` naming both numbers; a missing file, a non-numeric size or a bound of `0` reports nothing.
- **`tools/loop/loop.conf`** — `LOOP_WORKER_PROMPT_MAX_BYTES=29000`, with the paragraph that states what the key bounds,
  why the prompt's size is a per-turn cost, and why the number sits above the render a throwaway project produces.
- **`tools/loop/events.tsv`**, **`tools/loop/shared.tsv`**, **`tools/loop/layout.tsv`** — the event line, the
  child-defines/run-calls row for `worker_prompt_bound_check`, and the function in `child.sh`'s row.
- **`tools/loop/state.sh`**, **`tools/loop/report.sh`** — the bound recorded as `run.workerPromptMaxBytes` and printed
  beside the three of #815 by both `show` and the report's `bounds:` line.
- **`tools/loop/tests/loop-cases.sh`** — the case `run#theWorkerPromptFitsItsBudget` with its row and dispatch arm; the
  two prompt cases that assert the moved paragraphs read the prompt and the reference together.
- **`tools/loop/tests/loop-mutations.sh`** — mutations `61.3` and `263.1` follow their paragraphs into
  `worker-reference.md`, so a mutation still reddens the case that names it.
- **`guidelines/BuildGate.md`** §2a — the rule that owns this cost: a prompt is charged on every turn, so its size is a
  recorded bound.
- **`openspec/specs/agent-issue-loop/spec.md`** — the two scenarios above, in the shipped spec.

## Spec delta: none, and why

The two scenarios were written **by the fix commit itself**, inside `openspec/specs/agent-issue-loop/spec.md`, and not
as a change-relative `specs/` delta. Recording the same sentences a second time as an `ADDED`/`MODIFIED` block would
merge nothing new at archive and risk a stale copy of text the spec already carries, so this change carries no
`specs/` directory, declares `skip_specs: true` in its `.openspec.yaml` — the declaration without which
`openspec validate --strict` refuses a change with no delta — and archives with `--skip-specs`, the shape
`openspec/changes/archive/2026-11-25-fix-a-tail-range-read-of-the-tree-is-refused` landed in.

## Capabilities

`agent-issue-loop` — the requirement "A child carries its own route, and not the project's whole document" and its two
new scenarios, exercised by `run#theWorkerPromptFitsItsBudget`. No other capability is touched: the prompt, the bound
and the record are the loop tooling's own, and no product requirement reads them.

## Impact

Tooling only — no app, auto or core source, no native source, no manifest, no resource, no string, no Gradle input.
Files, 14 (`git show --stat 681fbd3f`): the two prompt files, five loop scripts and three loop tables, the case file,
the mutation file, one guideline section and one shipped requirement — 340 insertions, 123 deletions, no compiled file.

**Rollback** is one `git revert`: the three blocks return to the prompt, the reference file goes, the bound is
unread and the case goes red with the key's absence — which is also the falsification of the rule (see `tasks.md`).

## Verification

The case is red twice on the base and green after (`evidence/red-on-base.log`, `evidence/green.log`); the focused
selections are the new case, `run#reviewBudgetIsARecordedValue`, the whole `prompt` group (22 cases), `policy#*`,
`adapter#theChildIsStartedWithTheShellPolicy` and the two driver cases that guard the case file's declarations and the
mutation suite's shape, plus `bash -n` on the three changed scripts. The sizes before and after are re-derived in
`evidence/measure.log` through the loop's own renderer, and the composed prompt the fixture renders is named by the
case's own failure when the bound is set under it. No build and no full suite: no compiled file is touched
(`guidelines/Build.md` §2, the scope block), and the parent gates the branch once. The one red the base carries,
`driver#anOwnedFunctionIsInItsOwnersFile`, is red at `ae2373cb` before this change and is compared against this tree in
`evidence/green.log`: this change edits `report.sh` and `state.sh` and adds no function to either, so the row it
reports is the base's own.
