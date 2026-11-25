# Proposal — a continuation is handed the ledger of what its first process read

## Why

**Root cause.** A continuation is a **fresh session** on the same tree (issue #1074): it is handed the work package and
the patch its first process left, and not one path that process opened stands in its context. So every file the first
process read is a first read again, at full price, in the session that exists to be the cheaper half of the split —
and the record of them already existed and nothing named it. `tools/pi-child-events.sh` exports
`LOOP_TOOL_RESULT_LEDGER` beside the item's event stream for every child it starts (`(dirname "$events")/tool-result-
ledger.tsv`, issue #858), each child appends one line per path it was handed, and the checkpoint skeleton
(`tools/loop/prompts/worker-continuation.md`, 4 665 B as the issue measured it) named the tree, the work package, the
patch, the evidence directory and the scratch directory — and not that file. The second process therefore resumed from
a position the run held and never stated.

**The cost the item measured.** `worker-rework` is 1 session / **11.34 Mtok**, twice a worker session's 6.63 Mtok:
an item that splits pays a whole extra worker's readings again. The bound the change claims is 1–3 Mtok per split
item and not the whole 11.34, because the reading a continuation must not repeat is the *diagnosis*, and it still
re-reads the diff it continues and every file it edits.

**The correction the implementation established, and the rule the prompt states.** The rule that answers a re-read
inside one session cannot answer a continuation at all. `repeatReadResult` (`tools/pi-trim-result.mjs`, issues #925
and #989) keeps its tally on `read.child` — a `Map` that lives as long as one child process — so a new session's tally
is empty and every path is a first read for it; the file says so itself ("One invocation is one child that reads one
path once, so the repeat-read tally is always empty here"). What answers a read **across a session boundary** is the
item's own ledger rule, `repeatResult` (issue #685): it reads `read.ledger`, the file every child of the item opens,
and answers a path whose content is unchanged since it was read with one line naming the turn, the size, the session
and the range call, and a path that changed since with the file's first 2 000 characters and a `[LOOP-REPEAT-EDIT: …]`
line (`verificationNotice`, issue #886). The new bullet therefore states *that* rule, and not the per-child one: a
child told to expect the per-child rule would be told about a rule that cannot fire in a session that has just
started. What the ledger cannot carry — what the first process **made** of what it read — is its own report,
`{{EVIDENCE_DIR}}/worker-report.md`, and the bullet names it too.

**The red, on the unfixed source.** `run#theContinuationIsToldWhatItsFirstWorkerRead`
(`tools/loop/tests/loop-cases.sh`) on the tree this commit's parent holds, with only that case file checked out over
it (`git checkout 8386579a -- tools/loop/tests/loop-cases.sh`):

```
run#theContinuationIsToldWhatItsFirstWorkerRead|   the continuation is told the ledger of what the first process read, so it starts from that instead of reading the tree again: '…/items/21/tool-result-ledger.tsv' not found in: # Worker, continued — one item, the work a bound ended
FAIL run#theContinuationIsToldWhatItsFirstWorkerRead
loop-selftest: 1 of 1 cases failed
```

(`evidence/red-on-base.log` is the whole run, verbatim; the paths above are the only shortening here, and the
assertion's own message carries the rendered skeleton, which is why that log is longer than its verdict.)

**The fix** ➜ `run.sh` names the ledger beside the item's event stream (`item_ledger="$item_dir/tool-result-
ledger.tsv"`, the path the adapter exports to every child) and renders it as `{{LEDGER}}` at **both** sites the
continuation skeleton is rendered — the split's continuation and the retry of a phase that answered nothing — so no
placeholder of that skeleton is left unfilled at either site. The skeleton's new bullet lists the ledger's five
columns (`sha`, turn, size, session, path), says that the session column names the prompt the reading child was given
(`worker-prompt`, `worker-gate-prompt`), says that not one of those paths is in the continuation's context, states the
item-ledger rule that answers a read of one, and points at the report the first process was ended with, which is the
half the ledger cannot carry. The scratch bullet says the first process's own notes still stand there. The two lines
that enumerate what a continuation is handed — `guidelines/Backlog.recipes.md` and the `agent-issue-loop` spec's two
continuation scenarios — say so too.

**The requirement this defect contradicts** is `agent-issue-loop`'s "A worker that reaches the checkpoint is continued
before the bound" and "The checkpoint is recorded where the split pays": both said the continuation child is started
on "the work package plus the patch", and both now name the item's own tool-result ledger of what its children read.

**The bound it does not claim.** It does not claim a continuation stops reading the tree: it still reads the diff it
continues, the work package and every file it edits, and the ledger names reads and not conclusions. It takes no
position on the split's own price — one session's baseline paid twice is #1003's question — and it does not touch the
per-child repeat rule, which is unchanged and still the rule inside one session.

## What Changes

- **`tools/loop/run.sh`** — `item_ledger="$item_dir/tool-result-ledger.tsv"` named beside `events_file`, with the
  comment stating why the file is the item's and not a child's, and `"LEDGER=$item_ledger"` added to both
  `render_prompt` calls that render `tools/loop/prompts/worker-continuation.md` (the split's continuation and the
  retry of a phase that answered nothing).
- **`tools/loop/prompts/worker-continuation.md`** — the bullet naming `{{LEDGER}}` with its columns, the session
  column's meaning, the item-ledger rule over it and the report beside it; the scratch bullet gains the sentence that
  the first process's notes are still in that directory.
- **`tools/loop/tests/loop-cases.sh`** — one case, `run#theContinuationIsToldWhatItsFirstWorkerRead`, its row in
  `LOOP_SHELL_CASES` and its dispatch arm, which reads the ledger path out of the environment the adapter handed the
  **first child** rather than typing it; and `run#aPhaseThatWroteNothingIsAskedOnceMore` gains the same two
  assertions at the retry site (no `{{` left, the ledger named), with `tools/loop/prompts/` added to its `# files:`
  declaration.
- **`guidelines/Backlog.recipes.md`** §7d — the sentence that enumerates what the checkpoint prompt already names now
  names the item's own ledger of what its children read.
- **`openspec/specs/agent-issue-loop/spec.md`** — the two continuation scenarios above, in the shipped spec.

## Spec delta: none, and why

The two scenarios' text was edited **by the fix commit itself**, inside `openspec/specs/agent-issue-loop/spec.md`,
and not as a change-relative `specs/` delta. Recording the same sentences a second time as a `MODIFIED` block would
merge nothing new at archive and risk a stale copy of text the spec already carries, so this change carries no
`specs/` directory, declares `skip_specs: true` in its `.openspec.yaml` — the declaration without which `openspec
validate --strict` refuses a change with no delta — and archives with `--skip-specs`, the shape
`openspec/changes/archive/2026-11-25-fix-a-tail-range-read-of-the-tree-is-refused` landed in. The archive is then only
the move of this directory and the record it holds, and the shipped spec is untouched by it.

## Capabilities

`agent-issue-loop` — the scenarios "A worker that reaches the checkpoint is continued before the bound" and "The
checkpoint is recorded where the split pays", and the cases `run#theContinuationIsToldWhatItsFirstWorkerRead` and
`run#aPhaseThatWroteNothingIsAskedOnceMore` that exercise them. No other capability is touched: the checkpoint
skeleton is the loop's own tooling and no product requirement reads it.

## Impact

Tooling only — no app, auto or core source, no native source, no manifest, no resource, no string, no Gradle input.
Files, 5 (`git show --stat 8386579a`): `tools/loop/run.sh`, `tools/loop/prompts/worker-continuation.md`,
`tools/loop/tests/loop-cases.sh`, `guidelines/Backlog.recipes.md`, `openspec/specs/agent-issue-loop/spec.md` — 119
insertions, 7 deletions, no compiled file. Nothing is narrowed for a child and nothing it was handed is withdrawn: the
change adds one line of a rendered prompt and two of prose, and the placeholders it fills were already being filled
for other keys at the same two calls.

**Rollback** is one `git revert`: removing the bullet and the two `LEDGER=` arguments restores the base's prompts
exactly, and the new case goes red with it — which is also the falsification of the rule (see `tasks.md`).

## Verification

The case is red on the unfixed source and green after (`evidence/red-on-base.log`, `evidence/green.log`); the focused
selections are `run#theContinuationIsToldWhatItsFirstWorkerRead`, `run#aContinuationRunsToTheStop`,
`run#aChainOfZeroContinuationsEndsAtTheCheckpoint`, `run#aContinuationEndedOnTheSameCeilingStopsTooWide`,
`run#aPhaseThatWroteNothingIsAskedOnceMore` and `driver#everyCaseDeclaresTheFilesItCanReach`, plus `bash -n
tools/loop/run.sh`. The rule the bullet states is re-derived
through the trim CLI over an item ledger in `evidence/measure.log`, beside the real ledger's own columns and the
two-session proof. No build and no full suite: no compiled file is touched, so the structural checks of the files
edited stand in (`guidelines/Build.md` §2, the scope block), and the parent gates the branch once. The one red the base
carries, `driver#anOwnedFunctionIsInItsOwnersFile` (`report.sh defines action_add and its row does not own it`), is red
at the base itself and is untouched by this change.
