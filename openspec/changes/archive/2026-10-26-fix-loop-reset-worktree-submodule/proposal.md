# Proposal — fix-loop-reset-worktree-submodule

## Why

One defect, verified on this tree, issue #310: **`--reset-worktree` cannot discard a submodule a previous build left
on another commit, so the repair its own refusal names leaves the next start refused by the same check.**

**Root cause (one).** The repair runs two verbs of the superproject only (`tools/loop/run.sh:504` and `:505` on the
base): `git reset -q --hard` and `git clean -qfd`. A submodule's checkout is neither. The index inside the module
already matches the module's own `HEAD`, so the reset has nothing to undo there, and the module's path is *tracked*,
so the clean leaves it alone — `git status --porcelain` keeps reporting the module path as modified, the tree stays
dirty, and the next start refuses it at the same check with the same advice.

**Verified, not quoted from the tracker.** Measured in this tree 2026-10-26, on a throwaway repository whose
configured submodule path is a real module of two commits (`evidence/red-on-head.log`, and the probe the case was
built from):

```
$ git -C "$wt" reset -q --hard && git -C "$wt" clean -qfd
$ git -C "$wt" status --porcelain
 M app/src/main/cpp/libosmscout
$ git -C "$wt" submodule update --init --checkout app/src/main/cpp/libosmscout
$ git -C "$wt" status --porcelain
                                    # empty, the module on the pin the superproject records
```

The refusal the run prints for this tree is `repair: run.sh --reset-worktree discards them, and names them before it
does` (`tools/loop/run.sh:515` on the base) — advice that cannot be followed for the dirt it was printed for.

**The red tests** — two cases, because the repair makes two claims (it repairs, and it does not claim what it did not
repair). Both are red on the base and green after the fix:

- `run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard` — `evidence/red-on-head.log`, tally
  `evidence/red-on-head.xml`, `tests="1" failures="1"`:

```
FAIL run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard
  the repair leaves a tree the next start accepts: expected '', got ' M app/src/main/cpp/libosmscout'
loop-selftest: 1 of 1 cases failed
```

- `run#resetWorktreeNamesTheDirtItCannotDiscard` — `evidence/red-on-head-names.log`, tally
  `evidence/red-on-head-names.xml`, `tests="1" failures="1"`:

```
FAIL run#resetWorktreeNamesTheDirtItCannotDiscard
  the repair refuses a tree it cannot make clean: expected '5', got '0'
loop-selftest: 1 of 1 cases failed
```

**Owning capability and guideline section.** The behaviour is a new requirement of `agent-issue-loop`, beside *The
run survives interruption* — whose scenario *A kept worktree a previous run left dirty is attributed and refused*
already owns the refusal this repair answers (`openspec/specs/agent-issue-loop/spec.md:565`). The prose that owns the
loop's phases and refusals is `guidelines/Backlog.md`, section "The loop"; it states the refusal and names
`--reset-worktree` as the repair (`guidelines/Backlog.md:516`) without describing what the repair does, so no
guideline sentence is rewritten; the two comments that do describe its reach are `tools/loop/loop.conf:269` (the
submodule key) and `PORTING.md:56` (the keys a copy edits), and both said "a fresh worktree" only.

surface: 2 cases added, 0 rewritten, 2 mutation records added (13.8, 13.9), 1 requirement added with 2 scenarios, 2
comments realigned — the counts are `grep -c 'reset-worktree' tools/loop-cases.sh` (11 mentions at the base, 13
after), `grep -n 'reset-worktree' tools/loop-mutations.sh` (one record before, three after) and
`grep -c 'reset-worktree' openspec/specs/agent-issue-loop/spec.md` (4 mentions, none of them a requirement body) —
`evidence/surface-head.log`.

## What changes

- `tools/loop/run.sh` — the `--reset-worktree` path gains one step after its two verbs: when the profile names a
  submodule path and the kept worktree carries a `.gitmodules`, `git submodule update --init --checkout --force
  <path>` places the module on the gitlink the superproject records, best-effort with a note exactly as the fresh
  worktree's checkout is (D4). The path is the project's, read through `loop_conf_value LOOP_SUBMODULE_PATH`, so the
  engine still names no project path. A second read of `git status --porcelain` then decides the claim: empty, the
  existing "is clean again" line; not empty, the run prints what the reset *cannot* discard, names those paths and
  refuses to start with exit 5 instead of announcing a tree the next start would refuse.
- `tools/loop-cases.sh` — the two cases above, in the declared set and the dispatch table, with the shared fixture
  `submodule_fixture` that makes the configured path a real module of two commits and `submodule_in_worktree` that
  places its checkout.
- `tools/loop-mutations.sh` — `13.8` makes the checkout never run (reddens the first case), `13.9` makes the repair
  skip its own verification (reddens the second). Applied, red, restored, green:
  `evidence/mutation-13.8.log`, `evidence/mutation-13.9.log`.
- `tools/loop/loop.conf:269` and `PORTING.md:56` — the key's comment says both places place the module, not only the
  fresh worktree.
- `openspec/specs/agent-issue-loop/spec.md` — one added requirement, two scenarios.

## Spec

`ADDED` **The repair a refusal names repairs the tree it was refused for** — `--reset-worktree` leaves a kept
worktree the next start accepts: a submodule whose worktree `HEAD` differs from the gitlink the superproject records
is checked out at that gitlink, and dirt the reset cannot discard stops the run naming it instead of being reported as
a clean tree. Body: 303 characters against the validator's 500. Scenarios: *A submodule left on another commit is
checked out at the recorded one* (`run#resetWorktreeSyncsTheSubmoduleTheResetCannotDiscard`) and *The repair names
dirt it cannot discard* (`run#resetWorktreeNamesTheDirtItCannotDiscard`).

No existing requirement changes: *The run survives interruption* keeps its body and every scenario, including the one
that owns the refusal and names `--reset-worktree` as its repair — the new requirement states what that repair now
has to achieve.

## Impact

`tools/loop/run.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `tools/loop/loop.conf`, `PORTING.md`, the spec
delta. No compiled file, so the Gradle suites are owed by nothing this change touches; the shell suites the profile
maps for `tools/loop/**` are owed, and the gate ran them once. Rollback: revert the branch — the added block reads a
key that already existed, and a run started by an older engine resets exactly what it reset before.

## Open

The item's second candidate repair — *stop naming `--reset-worktree` for dirt it cannot discard* — is not taken, and
the measurement eliminates it rather than ranking it: the dirt is reachable (one `submodule update --checkout`
empties the status), so the advice is correct once the repair performs it; withdrawing the advice would leave the
person the repair the loop measured as the expensive one — reconstructing an item's work by hand from `git status`
and a diff (`guidelines/Backlog.md:394`, #308/#311) — for a tree the loop can clean itself. The failure mode the item
names is a false claim, and a claim that is verified before it is printed answers that without giving up the repair.
