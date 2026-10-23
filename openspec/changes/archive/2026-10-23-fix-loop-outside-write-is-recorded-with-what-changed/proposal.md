# The project-tree difference is recorded with what changed, and the default run continues through it

## Why

`project_state` is the project's own `git status --porcelain`, read once when a child starts and once when it
ends (`tools/loop/run.sh`). Any difference between the two readings is turned into a verdict about that one
child: the worker's item is `blocked` (`run.sh:1014`), the round-2 worker's item is `blocked` (`:1161`), and a
review round that ran during a difference is discarded (`:1094`).

Measured 2026-10-23, over the runs recorded in `.pi/issue-loop/runs/` and `run.json`: **5 of 139 items** carry
that reason — #198 (2026-10-20), #264 (2026-10-22), #276 and #274 (the same run, 2026-10-23T13:38Z) and #285
(2026-10-23T16:43Z). Three of them on one day, and in all three of those the child did not write into the
project tree: every write target in the three transcripts (`items/{274,276,285}/events.jsonl`, 6.6 / 8.9 / 13 MB)
lies under `.pi/worktrees/loop/` or the item's own evidence directory, both gitignored.

The surface cannot carry that verdict:

- **the project tree is not the child's.** The run writes it itself: `worktree.sh merge-item` / `adopt-item`
  fast-forward the base **in the project tree**, and every landing rewrites tracked files there (measured
  2026-10-23: `tools/loop/*.sh`, `guidelines/Backlog.md` and `openspec/specs/agent-issue-loop/spec.md` all carry
  mtimes from inside the run with content identical to HEAD). A peer session, a background build and a second
  run write it too.
- **two samples are not attribution.** The window is the child's whole life, up to `LOOP_CHILD_MAX_SECONDS`
  (2700 s). Whatever changed in it — including something that changed and was restored — is credited to the
  child, which is the only process the run knows nothing about.
- **the claim is unanswerable.** Until #285 the reason named no path and no process, so the escalation asked the
  owner a question that carried nothing to act on; the item's work was thrown away on it.

## What Changes

- **The difference is recorded, not acted on.** The child's transcript gains one line naming what changed — the
  differing `git status` entries and the processes standing in the project but outside the worktree — and the
  item keeps its outcome: the worker's item goes on to review and landing, and a review round that ran during a
  difference stands.
- **The landing rule stays the landing rule.** A base that is still not clean when the item lands refuses the
  merge — that guard is about what may be merged onto, not about who wrote it — and the reason it records names
  the path, so it is answerable without a claim about the child.
- **`--strict-outside-write yes|no`** (`LOOP_STRICT_OUTSIDE_WRITE_DEFAULT`, default `no` in `loop.conf`) restores
  today's behaviour for a project whose tree is exclusive: block the worker's item, block the round-2 worker's
  item, discard the review round.
- **The naming half of #285 is carried in.** Its branch (`fix/issue-285-loop-outside-write-names-no-path-so-the`)
  was blocked by this very defect before any review round, and conflicts with the base; its measured line and its
  case are what the note is worth reading, so they are folded here rather than landed twice.

## Capabilities affected

`agent-issue-loop` — one `MODIFIED` requirement, "Each item is worked by a fresh process, reviewed by a
different one": two scenarios changed, two added.

## Not in this change

Attribution — deciding *whose* write it was by process evidence rather than by a two-sample comparison — is the
next step and is not attempted here: a note that names a path and a process is the evidence such a decision
needs, and none of it existed before.
