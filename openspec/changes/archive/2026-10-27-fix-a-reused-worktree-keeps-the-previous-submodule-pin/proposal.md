# Proposal — fix-a-reused-worktree-keeps-the-previous-submodule-pin

## Why

One defect, verified on this tree, issue #309: **the kept worktree is reused by switching it to the new base, and
the switch moves the superproject's gitlink while the submodule's own worktree stays on the pin the previous branch
recorded — so `create` leaves a tree `start-item` refuses, and the run ends before any item is worked.**

**Root cause (one).** The reused-worktree path of `cmd_create` does one thing after its clean check — `git -C
"$worktree" switch -c "$branch" "$base"` (`tools/loop/worktree.sh:84`) — and the submodule checkout sits only on the
fresh path (`tools/loop/worktree.sh:53`, called at `:100`). A switch rewrites the index's gitlink to the commit the
new base records and does not touch the module's worktree, so the index and the module's `HEAD` disagree and
`git status` reports the module path as modified. `start-item` refuses exactly that state
(`tools/loop/worktree.sh:169`), and the run reaches the refusal after `create` — under no item, because `create`'s
branch names no item, so `run.sh`'s `*"not clean"*` branch records a stop with nothing attributed
(`tools/loop/run.sh:1289`, measured as run 2026-10-25's `18:08:10Z` stop).

**Verified, not quoted from the tracker.** The red case builds a throwaway project whose configured submodule path is
a real module of two commits, leaves the kept worktree clean on the previous pin, commits a new base that records the
other pin, and reuses the tree:

```
$ git -C "$wt" status --porcelain         # before the reuse
$ git -C "$p" rev-parse "$new:app/src/main/cpp/libosmscout"   # the new base's gitlink
  c16bad60…
$ tools/loop/worktree.sh create --base "$new" --branch loop-next
worktree … reused (branch loop-next from …)
$ git -C "$wt" status --porcelain
 M app/src/main/cpp/libosmscout
```

**The red test** — one case, because the defect is one mechanism:

- `worktree#checksOutTheNewPinWhenItReusesTheWorktree` — `evidence/red-on-head.log`, tally
  `evidence/red-on-head.xml`, `tests="1" failures="1"`:

```
FAIL worktree#checksOutTheNewPinWhenItReusesTheWorktree
  the reused worktree is the tree the new base records: expected '', got ' M app/src/main/cpp/libosmscout'
loop-selftest: 1 of 1 cases failed
```

After the fix the same case passes, and the tree it was refused over starts the item:
`evidence/green-after-fix.log` (`all 1 cases passed`), `evidence/focused-worktree-group.log` (the whole `worktree`
group, `all 19 cases passed`).

**Owning capability and guideline section.** The behaviour is a new requirement of `agent-issue-loop`, beside *The
repair a refusal names repairs the tree it was refused for* — which owns the same dirty-module state for
`--reset-worktree` (`openspec/specs/agent-issue-loop/spec.md:2390`) — and beside *The run survives interruption*,
whose scenario *A kept worktree a previous run left dirty is attributed and refused* owns the refusal this defect
walks into (`openspec/specs/agent-issue-loop/spec.md:565`). The guideline that owns the kept tree is
`guidelines/Backlog.md:382` ("Keeping the worktree is a decision about the *checkout* — the next run inherits the
repository, the submodule and the git state"); it states what a kept tree is, not what `create` places, so no
guideline sentence is rewritten. The two comments that describe the key's reach are `tools/loop/loop.conf:341` and
`PORTING.md:58`, and both said "a fresh worktree" only.

surface: 1 case added, 1 mutation record added (13.12), 1 helper introduced and the fresh path routed through it
(`sync_submodule`, `tools/loop/worktree.sh:53`), 1 requirement added with 1 scenario, 2 comments realigned — the
counts are `grep -c 'checksOutTheNewPin' tools/loop-cases.sh` = 3 (declaration, dispatch, case),
`grep -c '13.12' tools/loop-mutations.sh` = 2 (the record and the comment beside it), and
`grep -n 'sync_submodule' tools/loop/worktree.sh` = 3 (the definition and its two callers) —
`evidence/surface.log`.

## What changes

- `tools/loop/worktree.sh` — the submodule checkout becomes one step, `sync_submodule`, read from
  `LOOP_SUBMODULE_PATH` and gated on the worktree's own `.gitmodules` as before. The fresh path calls it where it
  called the inline block; the reused path calls it immediately after its switch, so the module is placed on the
  pin the new base records before `create` returns. Best-effort with the same note, because a project with no
  submodule is a project the loop still has to serve (D16).
- `tools/loop-cases.sh` — the red case above, in the declared set and the dispatch table, built from the
  `submodule_fixture` / `submodule_in_worktree` helpers issue #310 added (`tools/loop-cases.sh:5689`, `:5712`).
- `tools/loop-mutations.sh` — `13.12` makes the reused path skip the checkout. Applied, red, restored, green:
  `evidence/mutation-13.12.log`.
- `tools/loop/loop.conf:341` and `PORTING.md:58` — the key's description names three places that place the module.
- `openspec/specs/agent-issue-loop/spec.md` — one added requirement, one scenario.

## Spec

`ADDED` **A reused worktree is the tree the new base records** — a kept worktree that `create` switches to a new base
SHALL be left with its configured submodule checked out at the gitlink that base records, so the tree the next item
starts on is the one `git status` reports clean. Body: under the validator's 500 characters. Scenario: *The pin the
new base records replaces the previous branch's* (`worktree#checksOutTheNewPinWhenItReusesTheWorktree`).

No existing requirement changes: *The repair a refusal names repairs the tree it was refused for* keeps its body and
both scenarios — it owns `--reset-worktree`, and this change is about `create`.

## Impact

`tools/loop/worktree.sh`, `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `tools/loop/loop.conf`, `PORTING.md`,
the spec delta. No compiled file, so no Gradle suite is owed by anything this change touches; the shell suites the
profile maps for `tools/loop/**` and for `*.md` are owed, and the gate ran them once. Rollback: revert the branch —
the helper is the fresh path's own block moved, and an older engine reuses the tree exactly as it did before.

## Open

The item's second candidate — *refuse the pin difference before the switch* — is not taken, and the measurement
eliminates it rather than ranking it: the dirt is reachable with the verb the tree already carries one path over
(`git submodule update --init --checkout <path>`, measured in `evidence/red-on-head.log` and
`evidence/mutation-13.12.log`), so refusing would decline to place a module the loop can place itself, and leave a
person the repair the loop measured as the expensive one — reconciling a kept tree by hand. The alternative was also
the one the issue's own expected outcome names second.
