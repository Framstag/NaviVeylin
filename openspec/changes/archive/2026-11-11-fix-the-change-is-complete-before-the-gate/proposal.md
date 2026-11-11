# Proposal — the change is completed before the gate, so the gate and the round name one tree

## Why

**The root cause.** The run gates the tree and then lets a second child write the change directory: `run.sh` starts the
gate child (`tools/loop/run.sh:2539`) and `tail_after_gate` after it (`tools/loop/run.sh:2613`), and that tail's Phase F
archives the change — `openspec archive --yes`, which merges the delta into `openspec/specs/<capability>/spec.md` — and
repairs `design.md`/`tasks.md` when `tools/loop/evidence-check.sh` reports a pointer
(`tools/loop/prompts/worker-tail.md:96-105` on the base). Both writes are paths the round's diff carries, so the digest
the gate recorded (`LOOP-GATE-TREE`, `tools/loop/gate.sh:209`) and the digest beside the round's diff
(`tools/loop/run.sh:1380`, `loop_change_identity`) can only agree when the tail writes nothing at all.

**Second half of the same defect, found by this item's round 1.** An open change is gitignored (`.gitignore:76-77` —
`openspec/changes/*/`, with `!openspec/changes/archive/`), so the two listings `loop_tree_identity` read named none of a
change being worked while every path of its archived self was named by the base's diff: the archive's own rename moved
the digest of a change nobody corrected. The red case below measures it on HEAD —
`diff=e3b0c44298fc1c14`, the first sixteen hex digits of sha256 over the empty string, for a tree that carries an open
change with three files in it.

**The item's evidence.** #721 round 2: the gate measured `head=706cc2d2 diff=9553991d` while the reviewed diff was
`head=333fce2f diff=f84957f1`, because the tail's evidence-check repair replaced `tests=41` quotes in
`design.md`/`tasks.md` with pointers to `evidence/gate.log`. #90 is the same shape: its archived record named
`head=5d66d307 diff=883f5a82`, the pre-round-2 tree the round-2 reviewer refused. Both were landed by hand — one paying
a manual gate re-run, one a deliberate deletion of a reviewer's recorded verdict.

**The decision this implements.** The owner recorded on the item, 2026-11-11T06:04:18Z: *option A — the change is
completed before the gate, and a red gate restores it to its unarchived name*, with option (1) of the earlier answer
(the relocation-invariant digest) standing. This change carries both: the digest reads a change's own files under the
one name it has before its archive, and the phases are ordered so that nothing writes the change directory after the
gate except the generated trees the round's diff leaves out.

**The red tests.** Three, one per half, all red on the base `6264d0c3`:

- `gate#aRedGateRestoresTheChangeToItsUnarchivedName` — the restore the decision names; on HEAD the helper it calls
  does not exist. XML `tests="1" failures="1"` (`evidence/red-on-HEAD-aRedGateRestoresTheChangeToItsUnarchivedName.xml`,
  2026-11-11).
- `gate#theGateCompletesTheChangeBeforeTheGate` — which child writes the change directory; on HEAD the gate child is
  told to archive nothing and the tail is told to archive and to repair. XML `tests="1" failures="1"`
  (`evidence/red-on-HEAD-theGateCompletesTheChangeBeforeTheGate.xml`).
- `gate#aMovedChangeNamesTheSameTree` — the digest's path set; on HEAD an open change contributes nothing to it and its
  archived self contributes everything. XML `tests="1" failures="1"`
  (`evidence/red-on-HEAD-aMovedChangeNamesTheSameTree.xml`).

**The owning requirement and the owning guideline section.** The capability is `agent-issue-loop`: the requirement
"The gate ends the child that ran it, and a fresh child writes the archive"
(`openspec/specs/agent-issue-loop/spec.md:3203`) — this change removes it and adds its successor — and the digest
paragraph of "A child pays for the full gate at most the recorded number of times" (`:3808`). The prose that states the
rule is `guidelines/Backlog.recipes.md`, the section "The gate and its suite" (`:721`), the paragraph that begins "The
record names the tree the run measured" (`:770`).

**Surface.** `tools/look.sh 'loop_change_identity' --path tools` names every site that reads the digest and
`tools/look.sh 'LOOP_CHANGE_ARCHIVE_PREFIX' --path tools` the two halves that strip the archive's date
(`evidence/surface-counts.log`): one derivation extended and two helpers added in `tools/loop/lib.sh`, one profile key
in `tools/loop/loop.conf`, one call in `tools/loop/run.sh`, three prompts, three cases with their two registration
sites, three mutation records, one requirement removed and its successor added (`:3203`) and one requirement's digest paragraph plus one scenario, and
the one guideline paragraph.
