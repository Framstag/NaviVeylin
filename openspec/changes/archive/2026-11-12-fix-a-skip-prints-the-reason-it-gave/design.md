# Design — a skip's reason is the line the report prints

Line numbers are the base's (`0e1ad308`) where a paragraph quotes a place as this change found it, and the tree's as
the change leaves it where a paragraph describes the fix.

## Root cause

The run records an item it skipped twice on one call: the child's sentence goes to `metrics.rootCause`, and a
constant goes to `note`. `report.sh` prints the note.

```
tools/loop/run.sh:3402  loop_state done "$issue" --result skipped --verdict "${skip_verdict:-unspecified}" \
tools/loop/run.sh:3403    --wall "$wall" --pi-exit "$worker_exit" --rounds "$review_rounds" \
tools/loop/run.sh:3404    --gate "$gate_field" \
tools/loop/run.sh:3405    --root-cause "$root_cause" --red-test "$red_test" --note "skipped by the worker" \
```

and the two readers of the record take different fields:

```
tools/loop/report.sh:288  … select(.result == "skipped") | "#\(.issue) \(.verdict)\(if .note == "" then "" else " (\(.note))" end)"
tools/loop/report.sh:625  ["item", (.issue | tostring), .verdict, (.note // ""), (.metrics.rootCause // "")]),
```

So the `skipped:` line — and the `decide` line built from the same record, which takes the note first — carried the
constant for every skip the plain worker path took, while the reason a person needed sat in the record beside it. The
measured run is `2026-11-11T11:37:08Z`: `#838 too-wide (skipped by the worker)` and `#844 stale (skipped by the
worker)` over two children that had each written a paragraph, and 441 s / 5.2 Mtok re-opened by hand to answer the two
`decide` lines. The record itself states the defect in one command — `jq -r '.items[] | select(.issue==838 or
.issue==844) | "\(.issue) note=\(.note) rootCause=\(.metrics.rootCause)"'
.pi/issue-loop/runs/2026-11-11T11:37:08Z.json` — which prints #838's sentence in `rootCause` beside the constant in
`note`, and prints #844 with an empty `rootCause` because that child wrote no `LOOP-ROOT-CAUSE` line (its reason sits
inside `LOOP-DECISION` prose, `items/844/worker-report.md:12-15`). The second shape is not this change's, and the
proposal says so.

## The fix

One expression, in the writer:

```
--root-cause "$root_cause" --red-test "$red_test" --note "${root_cause:-skipped by the worker}" \
```

Three reasons this end owns the sentence, each one a fact the base already holds:

- **The reader is already the note's reader.** `report.sh:288` prints `.note` and `:625` prefers `.note` to
  `.metrics.rootCause`, and the `cut` and `blocked` arms (`run.sh:3436`, `:3502`) pass their own `$cut_note` /
  `$blocked_note` as the note. The note is the field a writer puts the run's account of an ending in; the skip arm was
  the one writer that filled it with a constant instead.
- **The alternative loses text.** Reading `.metrics.rootCause` first in `report.sh` would print a *less* complete
  sentence wherever the note carried the applier's own account — the two skips of the measured run that did read fully
  are exactly that shape — so the reader is the wrong end of the wire (#854's own note).
- **No new field, no new state key, no new reader.** The record already carries everything; only the note's value was
  wrong.

The constant stays as the fallback (`${root_cause:-…}`), which is the same shape `loop_labels` uses for the item's
comment (`run.sh:3399`, `--reason "${root_cause:-$skip_verdict}"`): a child that gave no reason leaves the line naming
no reason rather than printing an empty parenthesis.

## Blast radius

- `tools/loop/run.sh`: one continuation line and its comment — the only production change.
- `tools/loop/tests/loop-cases.sh`: one case added (list entry, dispatch entry, body), which also runs a second skip
  with no reason to pin the fallback.
- `tools/loop/tests/loop-mutations.sh`: record `854.1`.
- `guidelines/Backlog.recipes.md` section 7e and the spec delta — the rule and its requirement.

No compiled file is touched: the delta owes `tools/loop/tests/loop-selftest.sh` and the structural checks of the
documents it edits, not Gradle.

## Alternatives eliminated

- **Print `.metrics.rootCause` on the report's `skipped:` line instead.** Rejected: the note is where the `cut` and
  `blocked` arms and the applier put the run's own account, so preferring the root cause would drop that text for
  every skip that has one — and the note's reader is the same one the decision list uses, so the two lines would
  disagree.
- **Special-case the constant in `report.sh`** (`if .note == "skipped by the worker" then .metrics.rootCause else
  .note`). Rejected: a literal in a reader is a second place the sentence is owned, and the next writer that fills the
  note with a constant arrives with a reader that does not know it.
- **Record the sentence in a new state field** (e.g. `--reason`). Rejected: `state.sh done` already takes
  `--root-cause`, the escalation comment already uses it, and a second field for one sentence is a key that every
  reader would then have to learn.
- **Rewrite the states earlier runs recorded.** Rejected: the state is machine-local and replaced wholesale by the
  next run; the records the change is measured on are history, and the archive's own evidence stays as it was.

## Rollback

Reverting the one continuation line in `tools/loop/run.sh`, the case (its list entry, its dispatch line and its body),
the mutation record and the two documents restores the base byte for byte, and the case returns to red. Nothing reads
the note differently after the revert, so no state written by this change has to be migrated: a state written with the
note carrying the reason reads correctly either way.
