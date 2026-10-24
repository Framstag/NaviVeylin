# The clean command scans the kept worktree's own scratch root beside its own

## Why

**Verified in the tree at the base commit `6683376e`.** `tools/loop/loop-clean.sh:115` iterated exactly one root,
`for dir in "$LOOP_TMP_DIR/$LOOP_SELFTEST_PREFIX"*; do`, and `LOOP_TMP_DIR` is the root *this* invocation
resolved: `tools/loop/lib.sh:153`–`159` (`loop_tmp_resolve`) turns an empty `LOOP_TMP_DIR` into
`$LOOP_ROOT/.pi/tmp`, where `LOOP_ROOT` is `git rev-parse --show-toplevel` of the tree the tooling runs in
(`tools/loop/lib.sh:99`). `leftover_pids` is then called once per directory under that single root
(`tools/loop/loop-clean.sh:144`), so a process standing in a tree elsewhere is neither printed nor ended.

**Root cause, one sentence.** A suite creates its scratch trees under the root of the tree it runs in, and the
gate runs `tools/loop-selftest.sh` inside the kept worktree, so the trees that accumulate are under
`<worktree>/.pi/tmp` — a root the report, run from the project, never looks in.

**Measured, 2026-10-23T10:35Z (the item), and reproduced by the red case on this tree.** `.pi/worktrees/loop/.pi/tmp`
held four `loop-selftest.*` trees, 305 MB in total (57/98/89/61 MB), with mtimes 2026-10-22T15:17,
2026-10-23T05:34, 05:52 and 11:06 local — three of them past the 120-minute `LOOP_TMP_STALE_MINUTES` — while the
project's own `.pi/tmp` held zero, and `loop-clean` printed `self-test scratch trees under .pi/tmp older than
120 min` with no entry. Four `run.sh --items 1 --minutes 90 --review off` processes were still alive, reparented
to `systemd`, each a `sleep 1` poll loop, each with a working directory inside one of those trees: 6 h 53 min,
7 h 04 min, 1 h 27 min and 8 min old at observation, the three oldest long past the 90-minute budget they were
started with.

**The red test.** `clean#reportsTheWorktreesScratchTreesToo` (`tools/loop-cases.sh`, added by this change): a
throwaway project whose run has left the kept worktree in place, a stale `loop-selftest.stale` tree and a young
`loop-selftest.fresh` tree under that worktree's `.pi/tmp`, a `sleep 300` standing inside the stale one, and the
invocation's own scratch root (`LOOP_TMP_DIR` from a case-local `loop.conf`) empty. On HEAD the report names that
empty root and no tree at all. Taken against `tools/loop/loop-clean.sh` at `6683376e`, 2026-10-23T15:36:22Z:

```
  the report names the kept worktree's own scratch root beside the invocation's: 'self-test scratch trees under .pi/worktrees/loop/.pi/tmp' not found in: loop-clean: build output in .pi/worktrees/loop
loop-clean: self-test scratch trees under /home/tim/projects/NaviVeylin/.pi/worktrees/loop/.pi/tmp/loop-selftest.q1j82r/case.TVFSuX/tmp older than 120 min
FAIL clean#reportsTheWorktreesScratchTreesToo
```

`evidence/red-HEAD.xml` carries the tally (`tests="1" failures="1"`), `evidence/red-HEAD.log` the transcript.

**Owning requirement.** `agent-issue-loop`, "One command reports and removes what the loop left" — the
requirement that says what the one entry point removes; this delta says *where* it looks for the scratch trees.

**Owning guideline section.** `guidelines/Backlog.md`, the section "7. The loop", its leftovers paragraph
(`guidelines/Backlog.md:337`–`346`), which names `tools/loop/loop-clean.sh` as "the one command that answers what
has the loop left behind" and states what it removes; it is extended with the two roots and this measurement.

**Surface, counted before the plan was fixed** (`grep -c 'run_clean ' tools/loop-cases.sh` → 9 call sites in 4
cases; `grep -c '@tools/loop/loop-clean.sh@' tools/loop-mutations.sh` → 4 records; `grep -rl 'LOOP_TMP_DIR'
tools/loop/fixtures tools/loop/prompts` → 0 files):

surface: 1 case added, 0 rewritten, 4 cases exercise `loop-clean` (9 `run_clean` call sites, all re-run green), 1
mutation record added (`53.1`; the three existing records that quote `loop-clean.sh` lines are untouched because
those lines do not move), 2 engine files edited (`tools/loop/loop-clean.sh`, `tools/loop/lib.sh`), 0 fixtures
realigned.

## What Changes

- `tools/loop/lib.sh` — `loop_tmp_dir_for <tree>`: the scratch root *another tree* resolves, read from that
  tree's own `tools/loop/loop.conf` (read, never sourced) and resolved against that tree, defaulting to
  `<tree>/.pi/tmp`.
- `tools/loop/loop-clean.sh` — resolves the kept worktree's scratch root and scans it beside the invocation's:
  one heading line per root, and the same `LOOP_TMP_STALE_MINUTES` age rule and the same `leftover_pids` evidence
  for both, so a tree a live suite is writing into stays kept and a process inside a stale tree is reported and
  ended with `--apply`.
- `tools/loop-cases.sh` — the new case, registered in the case list and the dispatcher.
- `tools/loop-mutations.sh` — record `53.1`, which makes the new invariant falsifiable.
- `guidelines/Backlog.md` ("7. The loop") — the leftovers paragraph names the two roots and the measurement.

Nothing in `app/`, `auto/`, `core/` or the native tree changes: this is the loop's own tooling.

## Impact

- **Affected capabilities:** `agent-issue-loop` (one MODIFIED requirement, one scenario added).
- **Affected files:** `tools/loop/lib.sh`, `tools/loop/loop-clean.sh`, `tools/loop-cases.sh`,
  `tools/loop-mutations.sh`, `guidelines/Backlog.md`, this change.
- **Additive, not breaking:** a report that scanned one root now scans two; every path it removed before it still
  removes, and the one root it printed before is still printed, unchanged, as the first line.
- **Rollback:** revert the commit; the command returns to the single root and no other artifact depends on the
  second one.
- **Verification:** the loop's own suite (`tools/loop-selftest.sh`, offline), the mutation harness
  (`tools/loop-mutations.sh`), the project's gate `./tools/loop/gate.sh`, and `openspec validate --strict`. No
  compiled file is touched, so no Gradle task and no device check is owed.
