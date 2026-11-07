# A start the worktree refuses mid-run names the item whose work the tree holds

## Why

**Verified in the tree today.** `tools/loop/worktree.sh:138` refuses a start on a tree that is not clean ("one
writer per tree", `tools/loop/worktree.sh:134`–`139`), and the run's catch-all arm for a start refusal —
`tools/loop/run.sh:850` before this change, `*) stop_run "the item could not be started: $(printf '%s' "$start_out"
| head -1)"; break` — records the refusal's **first line** and ends the whole run. The dirt belongs to the
*previous* item, but the run names that item nowhere: the item whose uncommitted work dirtied the tree was already
recorded `blocked` for another reason, the leftovers are attributed to nothing, and the item that could not start
is left in the queue untouched.

**Root cause, one sentence.** The start-refusal arm stops the run on the refusal's first line instead of reading
the tree, so the item the worktree's branch marker names — the item whose unfinished work the refusal is
about — is never recorded, escalated or named.

**Measured, 2026-10-23 (the run the item quotes).** Run 2026-10-23T13:38:42Z (480 min) recorded
`stop: {"at":"2026-10-23T16:24:40Z","reason":"the item could not be started: loop: the worktree is not clean —
refusing to start #270:"}` in `.pi/issue-loop/run.json`. #274's round-2 correction was left uncommitted in
`.pi/worktrees/loop` when the run recorded #274 `blocked`; #270 then hit the dirty tree. The run ended 166 min
into an 8 h budget with 7 eligible items unworked (#270, #260, #259, #258, #257, #256, #60) and #270 with no
state entry at all.

**The red test.** `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds` (`tools/loop-cases.sh`, added by this
change): a two-item run whose first item's work cannot be committed (the stub installs a `pre-commit` hook that
refuses both of `end-item`'s commits), so #21 ends blocked with its work still in the tree and #22's start is
refused. On HEAD the run ended on the refusal and recorded neither #21 nor the files. Taken against the engine as
committed at the run's base `f2d27735818acd6d05095a9b331f4ee2bae1bb74`, 2026-10-23T17:11:48Z, 6.2 s wall clock:

```
  and names the item whose work the tree holds, not only the candidate: '#21' not found in: the item could not be
  started: loop: the worktree is not clean — refusing to start #22:
FAIL run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds
```

`evidence/red-HEAD.xml` carries the tally (`tests="1" failures="1"`), `evidence/red-HEAD.log` the transcript.

**Owning requirement.** `agent-issue-loop`, "The run survives interruption" — its scenario "A kept worktree a
previous run left dirty is attributed and refused" (`openspec/specs/agent-issue-loop/spec.md:525`) attributes a
dirty *kept* worktree at run start: it names the item the branch marker holds, records the refusal under it,
escalates it and prints the report (`run#dirtyKeptWorktreeIsARecordedRefusal`). That path attributes and records;
the refusal that happens *mid-run* did neither. The delta adds the requirement that owns the mid-run case.

**Owning guideline section.** `guidelines/Backlog.md`, section "7. The loop" — the paragraph that states what the
run does with a refusal that is the run's own rather than the item's (`guidelines/Backlog.md:304`, "What it does
with a child that never started"), whose new neighbour is the paragraph this change adds
(`guidelines/Backlog.md:315`, "What it does with a start the worktree refuses").

## What Changes

- **`tools/loop/run.sh`** — one arm for the refusal that reads `not clean`, beside the arms that already decide
  what a start refusal means (`base moved`, `carries commits the base does not`): it reads the worktree's own
  branch marker (`owner_of_marker`, the same reading the start-time pre-flight does, now one definition with two
  readers), records the leftovers under that item and escalates it exactly as the pre-flight does, names the item
  and the files in the stop reason, and stops. The candidate is neither recorded nor escalated.
- **`tools/loop/state.sh`** — `done` keeps **one record per item**: a second account of an item this run already
  recorded joins the note it has and writes the escalation label it earned, instead of appending a second entry.
  Without it the run's own record of #21 would be counted twice by every figure `report.sh` derives from `.items`
  (`blocked`, `budgetLeft`) and would read to `state.sh next` as two consecutive blocked items.
- **`tools/loop-cases.sh`** — one case, `run#aDirtyStartNamesTheItemWhoseWorkTheTreeHolds`, registered in
  `LOOP_SHELL_CASES` and in the dispatch.
- **`tools/loop-mutations.sh`** — three records, one per invariant: `53.1` (the attribution), `53.2` (one record per
  item) and `53.3` (the stop reason naming the item and the files). Each makes the one case red on its own.
- **`guidelines/Backlog.md`**, section "7. The loop" — one paragraph: what the run does with a start the worktree
  refuses, why it stops rather than taking the next candidate, and the measurement behind it (#284).
- **the spec `agent-issue-loop`** — one ADDED requirement, "A start the worktree refuses mid-run names the item
  whose work it holds", with its one scenario naming the case above.

surface: 1 case added, 0 rewritten, 0 fixtures realigned; 3 mutation records added, 0 checks rewritten, 2 derived
counts realigned by declaring the records inside `MUTATIONS=( … )` — `git show HEAD:tools/loop/run.sh | grep -c 'the
item could not be started: $(printf'` → 1.

Counted before the plan was fixed, command by command: `git show HEAD:tools/loop/run.sh | grep -c 'the item could
not be started: $(printf'` → **1** (the catch-all arm, the whole defect); `git grep -n 'loop-branch' f2d2773 -- 'tools/loop/*.sh' | wc -l` → **5** at the base
(`run.sh` 1, `worktree.sh` 4 — three writers, one reader in `adopt-item`; the `run.sh` one is the pre-flight that
this change turns into the shared `owner_of_marker`), and **6** in this tree, the refusal arm's read being the
sixth; `git show HEAD:tools/loop/run.sh | grep -c 'owner_item'` → **6** (all of them in that pre-flight,
none mid-run);
`grep -c 'items | length' tools/loop-cases.sh` → **11** cases asserting how many records the run wrote — every one
of them a place the duplicate entry would have shown up. The start-refusal behaviour itself is asserted by
`worktree#oneBranchPerItemOffTheBase` (the refusal, at the `worktree.sh` seam) and by
`run#dirtyKeptWorktreeIsARecordedRefusal` (the run-start attribution), and neither reaches the mid-run arm.

A new mutation record is not one line: `tools/loop-mutations-selftest.sh:130` and the case
`mutation#everyDeclaredRecordApplies` derive the declared set by reading the text of `MUTATIONS=( … )`, so `53.1`,
`53.2` and `53.3` are declared inside that array rather than appended after it.
