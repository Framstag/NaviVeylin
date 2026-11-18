# Design — a path no child wrote is not a write

## Context

See `proposal.md` — Why. Constraints:

- **Two readers, two shapes.** The run compares two readings as text (`project_state_diff`); the landing reads one
  `git status --porcelain` and refuses on any line. One filter has to serve both, or the two will drift.
- **A tracked edit is always a write.** The filter may only drop *untracked* entries at the landing: a child that
  edited a tracked file wrote something, whatever the file is called.
- **The list is a project's fact.** An editor's swap file is not this repository's invention, but what a toolchain writes
  varies by project, so the list is `loop.conf`'s, with the engine's default so a profile that has not widened it yet
  still gets the rule.

## Goals / Non-Goals

Goals: an editor or a language server writing into the project tree stops no item and blocks no landing.

Non-goals: changing what counts as work inside the item's own worktree; a general `git status` parser; dropping a
*changed* entry at a landing, where only an untracked one may be dropped. The run's side is looser on purpose and the
difference is deliberate: it compares two `git status` readings and asks whether a difference appeared while a child
stood, so a tracked file whose name matches a pattern and that changed during that window is dropped there too — the
pattern says what a person's tooling writes, not what is tracked. At a landing the same name in tracked position
still refuses, because there the run is about to merge a branch and a changed file is the merge's own risk.

## Decisions

### D1 — The filter is applied where the difference is taken, not where it is described

`outside_write_decides` reads whether a note exists, so filtering only the *clause* would have left the phase decided by
the line's existence. `project_state_differs` compares the two readings as the filter leaves them, and the caller prints
its FIRST line only when that is true. Measured while building this: the first draft filtered `project_state_diff` alone
and `run#anEditorSwapFileIsNotAChildsWrite` still ended `blocked`, because the readings differ and the note said so.

### D2 — `-uall` at the landing, and only untracked entries dropped

`git status --porcelain` collapses an untracked directory to its root (`?? architecture/`), so a `*/bin` pattern cannot
see it; `-uall` lists `architecture/bin/Thing.class` and the pattern matches. The `untracked` argument keeps every
non-`??` line: a tracked edit refuses the landing exactly as before, which is `worktree#aTrackedEditStillBlocksTheMerge`.

### D3 — The default list lives in `lib.sh`, the profile may override it

`loop_write_ignore` reads `${LOOP_WRITE_IGNORE-$LOOP_WRITE_IGNORE_DEFAULT}` — the `-` form, not `:-`. `loop_conf_value`
takes no default (it `eval`s the variable, so an empty value is what the caller gets), and an empty value is a
statement: `LOOP_WRITE_IGNORE=""` means the raw reading, the tree with nothing dropped. With `:-` the profile could
not say that, because the empty string would silently re-arm the default and the run would filter again. The key is
documented in `loop.conf` beside
`LOOP_STRICT_OUTSIDE_WRITE_DEFAULT`, the other key about this same reading.

## Risks / Trade-offs

- **A real write could match a pattern** — a child that writes `foo.bin/bin/x` would be ignored at the landing. Accepted:
  the patterns name build-output and editor temporaries, the list is a project's to narrow, and the run's own
  inside-the-worktree reading is untouched.
- **`.DS_Store` and `*~` are broad** — a project that commits such files keeps them tracked, and a tracked edit is never
  dropped (D2).

## Open decisions

none
