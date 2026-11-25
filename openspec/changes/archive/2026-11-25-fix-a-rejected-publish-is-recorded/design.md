# Design — a rejected publish is recorded on the item

## Where the fact lives

The item's record, beside `merged`: `state.sh publish-refused <issue> --branch <name> [--sha
<commit>] [--remote <commit>]` appends to `publishRefused`, a list unique by branch. The verb exists
because the order cannot be changed: `run.sh` merges the branch, releases it and writes the item's
record before the push runs, so `done` cannot carry the refusal. A verb that amends the record after
the push is the smallest write that keeps the fact where the landing's sha is.

`sha` is the local head the push tried to publish (`refs/heads/<branch>`); `remote` is what
`refs/remotes/origin/<branch>` names at the moment of the refusal, or nothing when the remote's
branch did not resolve. Those two facts are the sentence a reader needs when the closure's sha and
the remote's differ.

## Who reads it

`report.sh`, in the branches block it already prints, one line per refused branch. The report reads
the state file and git and nothing else; the refused list is part of the state file, so the report
gains no new input and no new argument.

## Alternatives eliminated

- **Parse `publish-failed` out of the run's events** (the item's own suggestion). The events file is
  a log of what happened, not the record; a report that counts events would have to re-derive the
  branch name from the log and would print a refusal the state does not carry. The push's own exit
  status is the fact and the branch name is already in hand at the call site, so the record is
  written from the status and not from the log. The event stays where it is — it is the transcript's
  copy of the same fact.
- **Move the push before the state write.** The push runs after the branch is merged and released;
  moving it before would publish a branch whose merge is not yet recorded, and the closed arm's
  record would then be written after the push it no longer describes.
- **Extend `loop_landed_on_base` (`tools/loop/lib.sh:601`) to ask the remote.** It asks the local
  base only and its callers depend on that; a refused push does not change what the base carries
  locally. The record beside `merged.sha` is where the difference is explained.
- **A new event type.** `publish-failed` already exists and already names the branch; a second type
  would be a second reading of the same fact.

## The report's line

```
publish refused: <n> — <branch> (local <sha|unresolved>, remote <sha|nothing>)[ · <branch> (…)]
```

It is printed in the branches block, after the "landed, removable" line, so a reader who reads the
branches as leftovers meets the refusal where the leftovers are named. The line names the branch
first, as the push's own stderr line does, and carries the local head the push tried to publish
beside what the remote's tracking ref names instead. The count is the number of distinct branches
refused — the item's and the base's are one list on the record, and a branch refused twice is one
entry.

## The case

`worktree#aRejectedPushIsRecordedOnTheItem` reaches the same `publish-failed` arm as
`worktree#publishesTheBaseItLandedOn`, with a bare remote whose own hook refuses the push. It asserts
the report names the refused branch and the item's record carries it beside the landing's sha. On
the tree before this change the record has no `publishRefused` and the report prints no such line, so
the case fails on the report and the state, not on the push.

## The guideline's edit

`guidelines/Backlog.recipes.md`, section 7e, owns the leftovers and the report. Its sentence that the report
names what the run left behind gains the refused publish: a branch whose push the remote refused is a
leftover, the report names it beside the branches it lists, and the item's record carries the refused
branch and its local head beside the landing's sha. The spec's requirement and the recipe's sentence
say the same thing in their own words.
