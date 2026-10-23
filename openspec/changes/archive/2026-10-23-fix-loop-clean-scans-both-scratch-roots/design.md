# Design — fix-loop-clean-scans-both-scratch-roots

## The rule

**The report looks where the trees are made.** A suite creates its scratch tree under the scratch root of the
tree it runs in (`tools/loop-selftest.sh` resolves `LOOP_TMP_DIR` from `lib.sh` against its own `root`), and the
gate runs the suite inside the kept worktree — so `loop-clean.sh` must scan the kept worktree's root beside the
one its own invocation resolved. One age rule and one process test apply to both: a tree younger than
`LOOP_TMP_STALE_MINUTES` is a live suite's, wherever it stands, and a process standing in a tree the report is
about to remove is the leftover that goes with it.

## D1 — where the second root comes from

**Chosen: the worktree's own configuration, read and resolved against that worktree.**
`loop_tmp_dir_for <tree>` (`tools/loop/lib.sh`) reads `LOOP_TMP_DIR` from `<tree>/tools/loop/loop.conf` and
resolves it exactly as `loop_tmp_resolve` does for the invocation — empty means `<tree>/.pi/tmp`, a relative
value is relative to `<tree>`, an absolute one is itself.

**Eliminated: the literal `<worktree>/.pi/tmp`.** It is right for the default profile and wrong for a project
that moved `LOOP_TMP_DIR` in `loop.conf`: the suites of that worktree would write into the moved root and the
report would name a directory nothing ever used — the same silence this item is about, one configuration away.

**Eliminated: sourcing `<tree>/tools/loop/loop.conf` in a subshell.** It is a config of this project and the gate
in that tree does source it, but a *report* is a look at a tree: running a tree's configuration to find out where
it would have put something hands that file a code path into a command whose job is to delete directories. The key
is one line of `KEY="value"`, so it is read as data.

## D2 — how the second root reaches the loop

**Chosen: one more word in the existing `for dir in …` glob, plus one heading line.** The loop body — the age
gate, the process scan, the removal, the counts — is untouched, so the two roots get one rule and one tally by
construction rather than by two code paths that could drift. The three mutation records that quote lines inside
that body (`25.2`, `28.2`, `42.3`) keep matching, because only the glob line and the heading change.

**Eliminated: a second copy of the loop.** Two copies of an age gate and a process sweep are two chances to fix
one and not the other; this item exists because one scanner had one root, and the fix is not a second scanner.

**Eliminated: a `loop_scratch_roots` list fed through a `while read` loop.** It reads better, but it re-indents
the whole body, and a record that quotes `  done <<< "$(leftover_pids "$dir")"` would stop applying — the failure
mode issue #264 records, where two invariants silently lost their falsification.

## D3 — the invocation's own root when the worktree is this tree

**Chosen: scan it once.** `--worktree .` (or a name this tree resolves to) makes the two roots one; the second is
dropped when the resolved paths are the same string, which is how this script already compares a `--worktree`
argument with `$LOOP_ROOT`. Scanning twice would list every tree twice and count its bytes twice while removing it
once — a report whose total is a lie.

## D4 — what the report says

Two heading lines, one per root, each naming the path it scanned relative to the project
(`.pi/tmp`, `.pi/worktrees/loop/.pi/tmp`) and the threshold; then the same `would remove` / `removed` / `kept … —
younger than N min` lines, each naming the tree by basename. The first line is the one readers already see,
unchanged.

## Risk

| risk | why it is bounded |
|---|---|
| `--apply` removes a tree a live suite is writing into | the trees of the second root carry the same age rule as the first: only trees older than `LOOP_TMP_STALE_MINUTES`, and a live suite touches its tree continuously |
| a tree is listed and counted twice | the second root is dropped when it is the first (`--worktree .`), and the default worktree root is never the invocation's own |
| a foreign tree's configuration is executed | it is read with `sed`, never sourced |
| the report ends a process it should not | the process test is unchanged and still scoped to trees the report is about to remove; a process inside a kept tree is never signalled |
| the new heading breaks an existing reader | no case matches the heading text; the three existing `loop-clean` cases were re-run green (`evidence/affected-cases.log`) |

## Verification

- The loop's own suite (`tools/loop-selftest.sh`), offline: the new case is red on HEAD
  (`evidence/red-HEAD.xml`, `tests="1" failures="1"`) and green after the fix (`evidence/green-fix.log`); the
  whole `clean` group is green (`evidence/affected-cases.log`).
- Falsification: mutation `53.1` (`tools/loop-mutations.sh --only 53.1`) takes the resolved second root out of
  the report and the new case goes red — `RED 53.1`, restore, green (`evidence/revert-check-53.1.log`).
- The project's gate `./tools/loop/gate.sh` once for the change's pathspec, and `openspec validate --strict`.
- No compiled file is touched, so no Gradle task, no device check and no visual measurement is owed.
