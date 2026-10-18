# fix-loop-child-working-directory — design

## D1 — The child's working directory is the project, and the tree it changes is named by path

The run starts each agent process with `cd "$LOOP_ROOT"`. Two properties come from that one choice, and both
were measured before it was made (proposal, "Why"):

* **the policy is resolved where the process starts.** The project's tool policy lives in the project root's
  `.pi/extensions/…` directory, which is untracked and therefore absent from a fresh worktree. A process
  started in the worktree finds no policy and asks for every call, which a one-shot process cannot do.
* **everything the child is handed lies inside the boundary.** The work package, the item's evidence
  directory and the worktree are all under the project root. A path tool refuses anything outside the
  directory its process started in, so the worktree-as-working-directory makes the package unreadable and
  the evidence directory unwritable.

The prompt carries the worktree's path (`{{CHANGE_ROOT}}`) and says to work there, so where a process *runs*
and which tree it *changes* are two separate facts — the first is the run's decision, the second is the
item's.

| alternative | why rejected |
|---|---|
| seed the worktree with a copy of the project's policy config | it embeds a foreign tool's configuration format into the loop engine, which is exactly what D16 forbids ("no script under `tools/loop/` names a skill, a gate command or an agent command"); the copy would also need the run's own staging directory added to it, i.e. the engine would be *authoring* policy. |
| move the run's staging directory into the worktree, so the child's world is the worktree alone | the run's state must outlive the worktree (`--clean-worktree` removes it, and the kept worktree is the next run's), and the state is the one thing a run must be able to read after its tree is gone. It also fixes nothing about the policy discovery. |
| keep the worktree as the working directory and hand the child copies of the package and the evidence under the worktree | the same machinery again — a second copy of every artifact, and a copy step whose failure mode is "the run closed an item whose evidence it cannot see". |

## D2 — A write outside the worktree is caught by the run, not by the child's discipline

The child now has access to the project's own tree, so "one writer per tree" (D4, and this skill's
invariant 2) needs a check rather than an assumption — the same reasoning that already makes the reviewer's
round read the tree before and after it runs.

`run_child()` reads `git -C "$LOOP_ROOT" status --porcelain` before and after each child and appends
`LOOP-OUTSIDE-WRITE` to the child's report when it changed. The item loop reads that field and blocks the
item with its own reason. Three properties of that shape are deliberate:

* **one place, three children.** The worker, the reviewer and the round-2 worker all go through
  `run_child()`, so none of them can be forgotten; the marker lands in the report file the run already
  reads, so no extra state and no extra git read at the call sites.
* **the project's *tracked* state is the guide.** `git status --porcelain` ignores what the project ignores,
  so the run's own staging directory (`.pi/issue-loop/**`, gitignored) is not a false positive — the child
  must be able to write its evidence there.
* **a mechanical block, not a decision.** The item is blocked with the tree named; the work is not closed
  and the next run can pick the item up again.

The reviewer's read-only digest gains the same `project_state` clause, because the reviewer can now reach the
project's tree too: the round is discarded with the existing reason when *either* tree changed.

## D3 — The fixture stands in for a child honestly

`PI_STUB_ACTION` is what a case uses to stand in for a worker's edits. The action cannot read the prompt, so
the stub runs it in the tree the prompt *would* have named — `$LOOP_WORKTREE`, which the wrapper exports —
and exports `LOOP_CHILD_ROOT` (the directory the child was really started in) so a case can write outside
the worktree on purpose and expect the run to catch it. Without that split, every case's action would land
in the project's own tree and the suite would have proven nothing about either tree.

## D4 — Verification: the loop's own shell gates

No compiled file changes. The gate is `tools/loop-selftest.sh` (49 cases, offline, no model), the mutation
set `tools/loop-mutations.sh` (each new check has one mutation that must turn its case red and whose restore
must turn it green), and the portability suite `tools/check-portability-selftest.sh`, which runs the loop
against a foreign project and would catch a change that only works here. The red-on-HEAD case is the
falsification for the fix itself: two of the three cases fail before it and pass after it
(`evidence/red-on-head-two-new-cases.log`).

## Open question — the project's own `ask` rules

The project's policy is a blacklist with `ask` for a few things a child could still reach for: the `skill`
tool for `build-app`, `run-tests` and `revert-check` (the `openspec-*` names are allowed) and `lean_ctx`. A
one-shot process cannot answer an `ask`, so such a call is refused inside the child. This change does not
touch the project's policy — that file is the owner's, and it is denied to tools by its own rules. Whether
those three skill names should be allowed for the loop's children is the owner's decision, and the
measurement that would settle it (a real run's worker report) is worth taking before the next unattended
run. Named here rather than silently patched.
