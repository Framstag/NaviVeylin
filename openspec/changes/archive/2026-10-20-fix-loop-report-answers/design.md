# Design — fix-loop-report-answers

## The root cause, measured

Both facts the report was missing were already written down, and neither reader read them:

| fact | where it lives | who read it before this change |
|---|---|---|
| what an item cost | `.items[].metrics.wallSeconds`, written by `cmd_done --wall` (`tools/loop/state.sh`) | nothing — the table's jq program at `state.sh:382-395` never touched `metrics` except for `rootCause`, `redTest`, `reviewRounds` and `gate` |
| why an item did not close | `.items[].note`, written by the same `cmd_done` call, and `.excluded[].reason` | `report.sh`'s `skipped:` line only, which prints the verdict — and `#\(.issue)` for the decision list (`report.sh:69`) |
| which branch carries work | git (`git branch --list 'loop-*' 'fix/issue-*'`), and `.items[].merged.branch` for an attempt that was merged | `report.sh:75` printed the names only — no commit count, no owner |
| which gate never completed | `.items[].metrics.gate`, empty when the item ended before the gate produced a result | nothing |
| where an item's transcript is | `<state dir>/items/<issue>/worker-report.md`, the directory `run.sh` stages each item in | nothing |

The red evidence (`evidence/red/`) is the three new cases against these two scripts restored to HEAD:

```
the table has a column for the item's wall time: '| wall |' not found in: | issue | class | verdict | change |
  root cause | red test | gate | review | outcome |
the escalated item is an action of its own: 'decide #21' not found in:
the action list is printed even when it is empty: 'actions the caller owes:' not found in:
```

So the defect is not missing data — it is two readers that print a fraction of what they are handed.

## The fix

`cmd_report` appends two columns, in this order after `review` and `outcome`:

```
| … | gate | review | wall | outcome | reason |
```

- **`wall`** is `.metrics.wallSeconds` rendered as `12m34s`, or `42s` below a minute, or `-` when the item was
  recorded without `--wall` (a skip that never reached a child). Minutes and seconds, because the run's floor
  arithmetic is in minutes and the measurements in REFERENCE.md are in minutes, while a skip is measured in
  seconds and would read as `0m`.
- **`reason`** is the item's own `.note` — the field `run.sh` writes with `--note` from the blocked reason, the
  timeout line or the review notes — falling back to `.metrics.rootCause` when the note is empty, collapsed to
  one line (`gsub("\n"; " ")`, and `|` to `/` so a note cannot break the table), and `-` for a closure, whose
  reason is the closure itself. The fallback matters for a `done` item that was recorded without a review note
  and for a state written before this change.
- **Nothing is removed and nothing is reordered**, because three self-test cases and the skill's template
  quote the earlier shape, and a caller may parse it. The table simply has two more columns.

`report.sh` grows the tail:

```
actions the caller owes:
  1. decide #126 — the run escalated it (blocked): <question> · the comment itself: gh issue view 126
  2. decide #44 — the pre-screen refused it (needs-decision): <reason> · the comment itself: gh issue view 44
  3. land fix/issue-126-pinned-band-height — 3 commit(s) the base does not have — item #126
  4. keep or remove the worktree .pi/worktrees/loop — …
  5. re-run the gate for #130 — the run recorded no gate result — transcript .pi/issue-loop/items/130/worker-report.md
  6. repair the citation in #57 — the pre-screen refused it as excluded-stale: <reason> — tools/issues/resolve-stale.sh --id 57
```

Five sources, in a fixed order, each read from the state or from git:

1. **owed decisions** — every item whose `escalatedLabel` is set (the label the run's escalation *wrote*, the
   predicate `report.sh` already uses for the compact `decisions the owner owes:` line) and every exclusion
   whose verdict is the profile's `decisionAgent` role. The question is the exclusion's `reason` (which the
   pre-screen wrote and `cmd_queue` keeps) or, for an item, its note and root cause — the two fields its
   escalation was built from. Where neither holds text, the line says `the run's state does not hold the
   question — gh issue view <n>`.
2. **branches** — every `loop-*` / `fix/issue-*` branch whose `git rev-list --count base..branch` is greater
   than zero. The owner is the number in the branch name (`fix/issue-<n>-<slug>`) or, for a branch the name
   does not identify, the item whose `merged.branch` equals it. A range git cannot answer is **named**, not
   counted as zero: honesty here is the difference between "no branch carries work" and "this branch could not
   be counted".
3. **the kept worktree** — `-d "$LOOP_WORKTREE"`, in the same test the existing `worktree:` line uses.
4. **gates that never completed** — items whose result is not `done` and whose `metrics.gate` is empty, with the
   transcript path `run.sh` stages beside the state (`<state dir>/items/<issue>/worker-report.md`).
5. **refused citations** — exclusions whose verdict is the profile's `excludedStaleAgent` role, with the
   tooling's own repair entry (`tools/issues/resolve-stale.sh --id <n>`, the tool that parks or repairs a stale
   citation).

`none` is printed with the five sources it covers, and only when all five were read and empty. The old lines
above the block (`skipped:`, `excluded before any work:`, `decisions the owner owes:`, `branches:`, `worktree:`,
`state:`) stay as they were: they are the compact index, the block is the detail.

No threading, dispatcher or lifecycle question arises: this is `jq`, `git` and `printf` in the two scripts the
wrapper already runs between items and at its end, and the report writes nothing.

## Alternatives the root-cause evidence eliminated

**A. Add the two columns only, leave the tail alone.** Eliminated by the third question: with `wall` and
`reason` in the table the caller still cannot act — the decision's question, the branch that carries commits,
its commit count, its item, the worktree and the gate's transcript are all outside the table, and the red case
`report#actionsNameDecisionAndBranch` fails on the missing heading before it reaches any of them. The issue's
own expectation is one list of the actions the caller owes, "each with what to do, where …, and why".

**B. Print the raw state JSON in the report.** Eliminated because nobody reads it: the state is machine-local,
carries `stateDigest`, the whole queue and every step boundary, and says nothing about git — so the branch's
commit count, the worktree on disk and the transcript's path would still be missing. A machine-readable form is
already the report's `--json` flag (`{run, items, excluded, budgetLeft}`); the printed report is for a person.

**C. Put the actions in the state only (a `run.actions` array) and leave the print alone.** Eliminated by what
the state is: `.pi/issue-loop/run.json` is replaced wholesale by the next run (the same reason issue #207 had
to archive it), so a field no one prints is a field the next run deletes; and the run's last printed block is
what a person sees at the end of 90 minutes, not a file they would have to open.

**D. Rewrite the existing `decisions the owner owes:` line to carry the question.** Eliminated by its readers:
it is one line, three self-test cases pin it to the ids (`report#escalatedSkipNamesTheLabelWritten`,
`report#failedReviewEscalationIsNamed`, `report#aVerdictAloneIsNoEscalation`), and a question is prose — it can
carry a newline, as the escalation's `**The decision:**` block does. The compact line stays the index; the block
carries the prose.

**E. Derive the branch's commit count from `.items[].merged.commits`.** Eliminated by the case the report is
about: that field is recorded only for an attempt the run *merged*. A blocked or skipped item's branch is
recorded nowhere in the state — which is exactly the branch that still carries unmerged work — so the count has
to come from git, and a branch the state does not know must still be named (the "no item of this run owns it"
line).

**F. Reconstruct the question from the verdict when the state holds no text.** Eliminated by the escalation's
own shape: for an *item* escalation the run writes the label and the comment, and the question lives in the
comment (`run.sh`'s `escalation_reason`, built from the reviewer's `LOOP-DECISION` field). A verdict is a
category, not a question — printing `blocked` as the question is inventing one. Naming `gh issue view <n>` costs
one command and cannot be wrong.

**G. Count only `fix/issue-*` branches, so the list is "the run's items".** Eliminated by the run's own branch:
`run.sh` creates `loop-<timestamp>` as the worktree's branch and the report lists it today. Excluding it would
hide commits a person may have to land, and the honesty rule for `none` is about branches that carry commits,
not about which pattern they match.

## Verification

- The three cases above, red on HEAD and green on the fix (`evidence/red/`, `evidence/green/`).
- The six existing cases on this surface, re-run green: the table's shape and the two decisions cases pin what
  this change must not break.
- `bash -n` on the three changed scripts, `tools/check-portability.sh --quiet` (no engine names a project
  value), `openspec validate fix-loop-report-answers --strict` and `openspec doctor` — recorded in
  `evidence/green/validation.txt`.
- No device, emulator, gate or Gradle run: the change touches no compiled file, so the structural checks above
  are the verification `guidelines/Build.md` §2's scope block asks for.
