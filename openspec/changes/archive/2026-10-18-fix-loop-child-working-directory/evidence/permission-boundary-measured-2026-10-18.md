# The permission boundary a child process gets — measured 2026-10-18

Two throwaway children (one session each, read-only, no model state kept) were started with nothing but a
different working directory, and asked to read the same paths in order (native `read`, then `ctx_read`, then
`cat` through a shell). Only the error text matters here; it is quoted verbatim.

## Child started in the worktree (`cwd = <project>/.pi/worktrees/loop`)

Both children of the loop are started the same way, so this is the worker's condition before the fix.

```
pwd: /home/tim/projects/NaviVeylin/.pi/worktrees/loop

ctx_read .pi/loop-branch                                  -> ok   (loop-20261018-092659)
ctx_read AGENTS.md                                        -> ok   (# AGENTS.md — AI Agent Context for NaviVeylin)
ctx_read /home/tim/projects/NaviVeylin/.pi/issue-loop/items/109/work-package.md
  -> denied: [pi-permission-system] A policy rule in the session serving this request denied this
     'external_directory' call for tool 'ctx_read' for agent 'delegate' for path
     '/home/tim/projects/NaviVeylin/.pi/issue-loop/items/109/work-package.md' (rule '*'): outside working
     directory '/home/tim/projects/NaviVeylin/.pi/worktrees/loop'.
  -> bash `cat` on the same path: succeeded, exit 0, first line '---- issue work package ----'
ctx_read /home/tim/projects/NaviVeylin/tools/loop/run.sh
  -> denied: … (rule '*'): outside working directory '/home/tim/projects/NaviVeylin/.pi/worktrees/loop'.
```

The boundary is the directory the process was started in, and the run's own staging directory — the package
a child is handed, and the evidence directory it must write — is outside it. A shell command is not gated
the same way (the `cat` above read the file), which is why the defect shows first as a worker that reports
it has no usable tool rather than as a session that cannot start at all.

## Child started in the project (`cwd = <project>`)

```
pwd: /home/tim/projects/NaviVeylin

ctx_read /home/tim/projects/NaviVeylin/.pi/worktrees/loop/.pi/loop-branch   -> ok (loop-20261018-092659)
ctx_read /home/tim/projects/NaviVeylin/.pi/issue-loop/items/109/work-package.md -> ok (---- issue work package ----)
ctx_read /home/tim/projects/NaviVeylin/tools/loop/run.sh                    -> ok (#!/usr/bin/env bash)
```

No error text arose: every path the item's phases need is inside the boundary.

## What is *not* measured here

The child in the second measurement inherited this session's loaded policy rather than discovering the
project's config file itself, as a fresh `pi --print` process does. What the real run's worker saw is in
`.pi/issue-loop/items/109/worker-report.md`: a fresh process started in the worktree, where the policy file
is absent (`.pi/extensions/**` is untracked), answering every tool call with "requires approval, but no
interactive UI is available". The end-to-end confirmation — a fresh process started in the project, whose
report shows readable tools and a committed change — is the next real `tools/loop/run.sh`.
