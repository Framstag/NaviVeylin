# Design: fix-an-empty-tree-names-the-outside-write

## Root cause

The reading exists and the sentences do not read it. `outside_write_note` and the two copies taken beside it
(`worker_write_note` where the change child ends, `gate_write_note` where the gate child does) describe the difference
the run saw in the project's own tree, path by path, bounded at ten lines plus `and N more`
(`outside_write_clause_of`, `tools/loop/run.sh:1514`). The three arms that conclude "nothing was committed" from a
tree at the run's base were written before that reading had the shape it has now, and each of them writes its own
sentence from scratch.

The reported item is the shape the three arms cannot tell apart: the child did its work by absolute path into the
project root (`edit` calls the event log carries verbatim), so the branch stayed at the base and the difference stood
in the caller's tree until the run had ended.

## The single fix

`item_outside_write_clause` (`tools/loop/run.sh`, beside `outside_write_decides`) answers one sentence from the
reading the item already holds:

```
 — the run nevertheless recorded a difference in the project's own tree while that child ran: if that difference is
 this item's work it is in the caller's tree and not on the item's branch — <the reading>
```

The conditionality is review round 1's, and it is the point: the reading the run records is usually one it declined
to place (`attributed=none`), and a peer session writes the same tree (#274, #276, #285, #286, #704). A sentence that
said "the work is in the caller's tree" would place on one child what the shipped requirement "A difference the run
cannot attribute does not decide the item" refuses to place. The clause says where the work is *if* the difference is
this item's, and never that the child wrote it.

It reads `worker_write_note` first, then the gate child's, then the tail child's, and answers nothing when the item's
record carries no reading — so the function is total over the three arms and an item with no difference keeps exactly
the sentence it wrote before. Three call sites, each a string append:

1. the pre-gate reading that ends the item before a gate child (`empty_tree_reading`);
2. the outcome block's `head_now = base` arm (`block uncommitted`);
3. the run's own "the child could not start" stop, where the same empty tree is the run's fault rather than the
   item's — the reading is what tells the two apart when the child's only trace is in the caller's tree.

`nevertheless` is deliberate: the arm's first clause is still true and stays, and what follows says what the run saw
beside it. A replacement would have made the sentence a verdict the run does not hold — nothing places the difference
on this child while `--strict-outside-write` is off (#274, #276, #285, #286, #704).

## Why not place the difference on the branch

#983 names that as the larger arm, and it is: it means a landing path for a tree the run cannot vouch for, which is
what #892 and #895 split the engine to reach. The measurement the item was filed with does not need it — the work is
in the caller's tree either way, and the defect is that nothing said so.

## Why not a new key or a new step

The run already writes the reading into the item's steps (`outside-write`, `outside-write-gate`,
`outside-write-tail`). A second record of the same reading would be a second thing to keep true; the clause is derived
at the moment the reason is written, from the value that is already in scope.

## Red case and mutation

- `run#anEmptyTreeWithAnOutsideWriteNamesThePaths` stages the shape: the child reports `LOOP-VERDICT: closed`, writes
  `demo-owned.txt` into `$LOOP_CHILD_ROOT` from the stub's action (after the stub's own `cd "$LOOP_WORKTREE"`, so
  `attributed=none` and the difference decides nothing), and commits nothing. It asserts the item is still `blocked`
  with `stopReason: uncommitted`, that the arm's sentence is still there, and that the reason now carries both
  `the work is in the caller's tree` and the recorded path.
- The other half — a record with no reading keeps today's sentence — is pinned beside it in
  `run#childClaimWithoutCommitIsBlocked`, which is the same shape with no write into the project tree.
- Mutation `983.1` deletes `$(item_outside_write_clause)` from the two arms that quote `nothing was committed`; the
  case goes red for its own reason and restores green.
