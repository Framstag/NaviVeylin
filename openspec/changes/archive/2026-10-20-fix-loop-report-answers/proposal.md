# fix-loop-report-answers

## Why

The report a run prints at its end is the run's product — the thing a caller reads instead of the tracker. It
carried the budget, a per-item table and a set of one-line lists, and it could not answer three questions an
operator actually asks (issue #208):

- **which items were handled, and how each ended** — the table's `outcome` column said `blocked` or
  `skipped`, and the item's own reason for that result sat in the state under `.items[].note` and was printed
  nowhere;
- **how long each item took** — `state.sh done --wall SECONDS` records `.items[].metrics.wallSeconds` for
  every finished item and no column showed it; the only duration was one run-level `spent T min` in the
  header, which cannot say which item cost the clock;
- **what the caller must do now** — `decisions the owner owes:` printed bare issue numbers (`#126`) without
  the question the escalation had written as a comment, and nothing named the branch that carries unmerged
  work together with the item that owns it, the kept worktree, a gate that never completed and where its log
  sits, or a citation the pre-screen still refuses. The caller had to open the tracker and reconstruct all of
  it.

**Root cause and its evidence.** The state recorded both missing facts and neither reader printed them:

- `tools/loop/state.sh` `cmd_report` (before this change, the jq program at lines 382-395) built its header
  from nine columns — `| issue | class | verdict | change | root cause | red test | gate | review | outcome |`
  (line 385) — and wrote a row from the item without ever touching `.metrics.wallSeconds` or `.note`. The same
  file's `cmd_done` writes both fields (`.items[] += [{… metrics:{wallSeconds:$wall …}}]`).
- `tools/loop/report.sh` reduced the decision list to the ids: `'#\(.issue)'` inside the jq at line 69, one
  line beginning `decisions the owner owes:`; it listed the branches as bare names (`git … branch --list
  'loop-*' 'fix/issue-*'`, line 75) with no commit count and no owner, and it printed nothing about a gate
  that never completed or a refused citation.

The red evidence is the three new cases run against this tree with only `state.sh`/`report.sh` restored to
HEAD (`evidence/red/`):

```
the table has a column for the item's wall time: '| wall |' not found in: | issue | class | verdict | change |
  root cause | red test | gate | review | outcome |
the escalated item is an action of its own: 'decide #21' not found in:
the action list is printed even when it is empty: 'actions the caller owes:' not found in:
```

**The single fix.** `state.sh report` prints the item's wall time and, when its result is not a closure, the
reason it recorded — two columns appended, so no reader that quoted the earlier shape loses a column — and
`report.sh` grows its tail into a named `actions the caller owes:` list built from the state and from git: each
owed decision with the question the run wrote beside it (or the place the question is, `gh issue view <n>`),
each branch that carries commits the base does not have with its commit count and the item that owns it, the
kept worktree, each item whose gate never completed with its transcript, and each citation the pre-screen
still refuses. `none` is printed only when all five sources were read and empty.

**Spec requirement the defect contradicts:** `agent-issue-loop`, "One command starts a budgeted run, with no
dialogue" — its scenario "A run is recorded" already promised that "a report exists naming each item, its
verdict, **its wall time**, its review rounds and what it closed"; the wall time was recorded and not
printed, and nothing in the requirement asked what the caller must now do.

## Cases and evidence

Three cases in `tools/loop-cases.sh`, registered in **both** the `--list` array and the dispatch map, each
driving the real `report.sh` over a real state and a real git repository:

- `report#wallTimeAndReasonInTheTable` — a `done` item with `--wall 754` and a `blocked` item with a note: the
  table has a `wall` and a `reason` column, the row prints `12m34s` and the blocked item's own reason, and each
  row still has its eleven columns (twelve separators), so nothing was dropped.
- `report#actionsNameDecisionAndBranch` — an escalation recorded on an item plus a `fix/issue-21-…` branch one
  commit ahead of the base: the action list names `decide #21` with the question the run wrote, and
  `land fix/issue-21-two-mechanisms` with `1 commit(s)` and `item #21`; it does not print `none`.
- `report#emptyRunSaysNone` — a state with nothing in it: the heading is printed and says `none` with the five
  sources it covers, rather than the section being omitted.

Red on HEAD `8461bd2`: `evidence/red/`. Green on the fix: `evidence/green/`, which also re-runs the six
existing cases on this surface (`report#headerAndTableShape`, `run#stopReasonIsPrintedAndReported`,
`report#yieldIsCountedFromState`, `report#escalatedSkipNamesTheLabelWritten`,
`report#failedReviewEscalationIsNamed`, `report#aVerdictAloneIsNoEscalation`) plus
`tools/check-portability.sh`, `openspec validate … --strict` and `openspec doctor`. The whole offline suite is
**not** run by this change: the orchestrator runs it once on the merged tree.

## What Changes

- `tools/loop/state.sh` `cmd_report`: the table gains a `wall` column (the recorded wall time as `12m34s`, or
  `-`) and a `reason` column (the item's note, falling back to its root cause, single-lined; `-` for a
  closure). Eleven columns, appended, none removed. The function's comment states what the table answers.
- `tools/loop/report.sh`: the tail gains the `actions the caller owes:` block — decisions with their
  questions, branches carrying commits with their item, the kept worktree, items whose gate never completed
  with their transcript, refused citations with the repair command — and `none` only when all five are empty.
  The header comment names the three questions the report answers.
- `tools/loop-cases.sh`: the three cases, registered in both places.
- `.pi/skills/fix-loop/SKILL.md`: the report template carries the two new columns and the action list, and
  says the list is the answer to "what must I do now".
- `guidelines/Backlog.md` §7 "The loop": states the three questions the report answers and the honesty rule
  behind `none`.

## Impact

Affected files, all of them the loop's own harness — no app, native or Gradle file is touched:

| file | module / area |
|---|---|
| `tools/loop/state.sh` | the run's state, budget and lock; `report` prints the run's per-item table |
| `tools/loop/report.sh` | the report a run prints at its end |
| `tools/loop-cases.sh` | the loop's offline self-test cases (`tools/loop-selftest.sh`) |
| `.pi/skills/fix-loop/SKILL.md` | the `fix-loop` skill's report template |
| `guidelines/Backlog.md` | §7 "The loop", where the run's report is described |

No Android module, manifest, resource, native source or JNI bridge is touched, so the phone/car parity rules,
the native-build rules and the `submodule patch` / `local override` decision do not arise. No new project
value enters an engine: the two role values the new lines read (`decisionAgent`, `excludedStaleAgent`) are
already read through `loop_role`, and `tools/check-portability.sh` still reports 42 engines naming no project
value.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **MODIFIED** "One command starts a budgeted run, with no
  dialogue": the scenario "A run is recorded" now names the wall time, the reason of a non-closure and the
  actions the caller owes, and two scenarios are added (the action list names each decision and each branch
  that carries commits; an empty list says `none`). The requirement's other scenarios are restated verbatim;
  no unrelated text is rewritten.

No other capability owns the loop's report.

## Additive or breaking, and rollback

**Additive.** Two columns are appended to the table and one block to the report; every earlier column and line
keeps its name, its order and its meaning, no argument, state field, exit code or recorded configuration
changes, and the new lines read only facts the state and git already hold. A caller that parsed the old table
still finds its columns; one that reads the tail finds more than it did.

Rollback: revert this commit. The table returns to nine columns, the report's tail to the bare branch list and
the bare decision ids; no state file, branch, label or archived change is affected, because the change writes
none of them. A run in flight during a revert is stopped first — `state.sh next` and the lock refuse a second
run anyway.
