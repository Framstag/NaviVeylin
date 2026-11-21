# Proposal: fix-an-empty-tree-names-the-outside-write

## Why

**Root cause.** Three arms read "the item's tree is still at the run's base" as "the child did nothing", and each of
them prints a sentence that says so with no path in it: the pre-gate reading that ends the item before a gate child
(`tools/loop/run.sh`, `empty_tree_reading`), the outcome block's `uncommitted` arm ("reported success but the tree is
still at the base commit — nothing was committed"), and the run's own "the child could not start" stop. A child that
writes the caller's own tree by absolute path leaves exactly that shape — the branch at the base, the work in the
project tree — and the run already holds the reading that names the paths: the outside-write walk, recorded for every
phase under `worker_write_note`, `gate_write_note` and `outside_write_note`. The three arms never read it, so the
record answers the item's owner with a sentence about a child that never worked.

**Evidence** ⊨:

- Issue #983's item record, run `2026-11-20T09:58:14Z → 17:19:36Z`, item #894: `blocked`, `stop uncommitted`, note
  `reported success but the tree is still at the base commit — nothing was committed`. Its worker reported
  `LOOP-DIFF-READY` and was not lying: `edit` calls with `path: /home/tim/projects/NaviVeylin/tools/loop/layout.tsv`
  appear 45 times in `.pi/issue-loop/items/894/events.jsonl`.
- The item's own record carries the paths the run saw: `LOOP-OUTSIDE-WRITE-FIRST: the project tree changed while this
  process ran: M guidelines/Backlog.md; M guidelines/BuildEvidence.md; M guidelines/Logging.md; M tools/loop/layout.tsv;
  M tools/loop/lib.sh; M tools/loop/loop.conf; M tools/loop/run.sh; M tools/loop/shared.tsv;
  M tools/loop/tests/loop-cases.sh; ?? tools/loop/events.tsv`.
- What it cost: 3.27 Mtok over 69 turns and 68 calls, plus 489 s of that item's gate clock. The work was not lost — it
  stood uncommitted in the caller's tree, where nothing named it as an item's.
- On HEAD before this change the new case fails on the arm's own sentence:
  `…and an item whose child wrote the project's own tree says where the work went: 'the work is in the caller's tree'
  not found in: reported success but the tree is still at the base commit — nothing was committed`
  (`evidence/red-on-HEAD-983.log`).

**Consequence** ⊨: `--strict-outside-write off`, the default, keeps a difference the run cannot place from deciding
the item (`outside_write_decides`), which is right — a peer session writes the same tree. What that measurement never
covered is the *reason*: an item whose branch is empty and whose record carries a reading is not the item
`uncommitted` describes, and the fix is the sentence, not the verdict. Placing the difference onto the item's branch is
the larger arm and stays with the engine split (#892, #895).

## What changes

- `tools/loop/run.sh`: one reader, `item_outside_write_clause`, that turns the recorded reading into a conditional
  clause naming where the work would be; the three arms append it. No new measurement, no new child, no new state key.
- `tools/loop/tests/loop-cases.sh`: `run#anEmptyTreeWithAnOutsideWriteNamesThePaths`, and the negative half beside
  `run#childClaimWithoutCommitIsBlocked`.
- `tools/loop/tests/loop-mutations.sh`: record `983.1` drops the clause from the two empty-tree arms.

## What does not change

The verdict: a tree at the base is still blocked `uncommitted`, and the run still stops on a child that answered
nothing. The clause is a qualification and never a replacement, so an item whose record carries no reading gets
exactly the sentence it gets today.
