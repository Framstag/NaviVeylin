# Start the loop's agent processes where the project's own tool policy applies

## Why

Issue **#169**. A real run on 2026-10-18 (base `8c6c1e1`, `tools/loop/run.sh --items 1 --minutes 90`) spent
10.9 s and recorded the item blocked:

```
item #109: blocked (reported success but the tree is still at the base commit — nothing was committed) · 10.984s · 0 review round(s)
```

The worker's own report (`.pi/issue-loop/items/109/worker-report.md`) is the evidence that it never had a
usable session:

```
Blocked. No tool usable in this session.
All calls rejected by permission system:
- `ctx_read .../worker-prompt.md` → `This 'external_directory' call for tool 'ctx_read' ... requires approval,
  but no interactive UI is available.`
- `ctx_shell cat ...` → `This 'ctx_shell' call requires approval, but no interactive UI is available.`
- `ctx_ls` worktree path, `ctx_read` worktree `AGENTS.md` → same
- `contact_supervisor` → same
```

**Root cause**: `run.sh`'s `run_child()` starts every agent process in the worktree
(`cd "$worktree" && $LOOP_AGENT_CMD …`). The policy that governs this project's tools lives in the project
root's `.pi/extensions/pi-permission-system/config.json`, which is **untracked** and therefore absent from a
fresh worktree — so a one-shot child finds no policy and resolves every call as an approval it cannot ask
for (design D3: "a one-shot process cannot ask for permission"). A second boundary follows from the same
directory, measured with a throwaway child on 2026-10-18:

```
child at cwd=<project>/.pi/worktrees/loop:
  ctx_read <project>/.pi/issue-loop/items/109/work-package.md
  → denied: outside working directory '<project>/.pi/worktrees/loop'
child at cwd=<project>:
  ctx_read <project>/.pi/worktrees/loop/.pi/loop-branch            → ok
  ctx_read <project>/.pi/issue-loop/items/109/work-package.md       → ok
  ctx_read <project>/tools/loop/run.sh                              → ok
```

So even with the policy in place, a child started in the worktree cannot reach the work package it is handed
or the evidence directory it must write. The tree it must change is inside the project; its working
directory should be the project.

**The requirement the defect contradicts** is `agent-issue-loop`, "Each item is worked by a fresh process,
reviewed by a different one": a process that can read neither its package nor its evidence directory is not
a process that worked the item — and the run had no way to tell that from a refusal, so it reported the
symptom ("nothing was committed") rather than the cause.

## What changes

1. **`run_child()` starts the child in the project**, not in the worktree: the project's own policy is
   resolved where the process starts, and the work package, the evidence directory and the change root all
   lie inside that boundary. The tree a phase changes is still the worktree — its path is what the prompt
   carries.
2. **The prompts name the change root explicitly** (`worker.md`, `reviewer.md`): the child's working
   directory is the project, so it is told to `cd` into the change root and to leave the project's own tree
   alone.
3. **The run reads the project's own tree around every child** (`project_state()`): a child that writes
   outside the worktree is caught by the run and blocks the item with that reason — the "one writer per
   tree" invariant survives the child gaining access to the project. The new report field
   `LOOP-OUTSIDE-WRITE` carries that verdict, and the reviewer's read-only comparison now covers the
   project's tree as well as the worktree's.
4. **Three loop cases** (red on HEAD, green after the fix): `run#childStartsInTheProjectRoot`,
   `run#childWriteOutsideTheWorktreeIsBlocked`, `run#reviewerWriteInTheProjectIsDiscarded`.
5. **Four mutations** (`tools/loop-mutations.sh`): `9.9` re-pointed at the new `run_child` line, and
   `12.2`, `12.3`, `12.4` for the child's directory, the outside-write marker and the reviewer comparison.
6. **The stub child in the loop's fixture** runs a case's action in `$LOOP_WORKTREE` — the prompt names the
   change root, and a stub that cannot read a prompt takes it from the environment the wrapper exported —
   and exports `LOOP_CHILD_ROOT`, the directory the child was actually started in, so a case can write
   outside the tree deliberately.

## Impact

| file | change |
|---|---|
| `tools/loop/run.sh` | `project_state()`, `run_child()` starts in the project and marks an outside write, the blocker that reads the mark, the reviewer digest covers the project |
| `tools/loop/prompts/worker.md`, `prompts/reviewer.md` | the change root is named as the directory to work in |
| `tools/loop/fixtures/pi` | the action runs in `$LOOP_WORKTREE`; `LOOP_CHILD_ROOT` is exported |
| `tools/loop-cases.sh` | three cases |
| `tools/loop-mutations.sh` | `9.9` updated, `12.2`, `12.3`, `12.4` added |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement modified: where a process runs, and that a write outside its tree is caught |
| `.pi/skills/fix-loop/REFERENCE.md` | the worktree recipe's step 5 states where children are started and where the state lives |

No compiled file changes, so the verification is this repository's shell gates — the loop suite, the
mutation set and the portability suite — not the Gradle gate.

**Rollback**: one line in `run_child()` (back to `cd "$worktree"`), the marker read and the digest's
`project_state` clause. The cases then fail for the original reason, which is what the red-on-HEAD evidence
records.

**Not claimed here**: the project's own policy may still answer `ask` for a tool a non-interactive child
reaches for — the `skill` block allows the `openspec-*` names but not `build-app`, `run-tests` and
`revert-check`, and `lean_ctx` is `ask`. What a child does with a refusal is the child's business and is not
measured by this change; it is named as an open question in `design.md`.
