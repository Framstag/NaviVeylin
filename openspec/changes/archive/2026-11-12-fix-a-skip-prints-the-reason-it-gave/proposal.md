# Proposal — a skip's reason is the line the report prints

## Why

**Root cause.** The run's `skipped` arm records the child's own sentence as the item's `rootCause` and writes the
constant `skipped by the worker` into the item's `note`, on the same call (`tools/loop/run.sh:3405`, base
`0e1ad308`):

```
--root-cause "$root_cause" --red-test "$red_test" --note "skipped by the worker" \
```

`report.sh:288` prints `.note` on its `skipped:` line (and its decision list takes `.note` first, `.rootCause`
second, `report.sh:625`), so every skip taken on the plain worker path reads as reasonless in the one account a caller
gets, while the child's own sentence sits in the same record, unread.

**Evidence, verified against the tree (2026-11-12, issue #854).** `tools/look.sh 'skipped by the worker' --path tools`
finds the constant on exactly one line, `tools/loop/run.sh:3405`; `tools/look.sh 'result skipped' --path tools/loop`
finds exactly one writer, `run.sh:3407` as this change leaves it (`:3403` at the base), and five `state.sh` calls in
the cases — so there is no second skip arm and no writer whose note already carries a reason. The item's measurement is the run of `2026-11-11T11:37:08Z`: two of its
four skips printed no reason — `#838 too-wide (skipped by the worker)` and `#844 stale (skipped by the worker)` —
over children that had written a paragraph each (`items/838/too-wide-verdict.md`, `items/844/worker-report.md`), so
441 s and 5 221 257 tokens (6.9 % of the run) had to be re-opened by hand to answer `decide #838` and `decide #844`.
The other two skips read fully because their note was overwritten by the applier's own account, which is what shows
the field can carry the reason.

**The record that run left, read again (2026-11-12).**
`jq -r '.items[] | select(.issue==838 or .issue==844) | "\(.issue) note=\(.note) rootCause=\(.metrics.rootCause)"'
.pi/issue-loop/runs/2026-11-11T11:37:08Z.json` prints`838 note=skipped by the worker rootCause=the handed change is one item for 35 tasks — a new Gradle module, …` and
`844 note=skipped by the worker rootCause=`. The first is the defect this change fixes, exactly. The second is not:
#844's child wrote no `LOOP-ROOT-CAUSE` line — its report carries `LOOP-SKIP-VERDICT: stale` and puts the reason
inside `LOOP-DECISION` prose (`items/844/worker-report.md:12-15`) — so the sentence was never in the record any
reader prints. This change recovers #838's reason and cannot recover #844's; an arm that read a skip's question out
of `LOOP-DECISION` is a second field and a second change.

**The single fix.** One expression: the note is the child's reason, with the constant kept as the fallback for a child
that gave none.

```
--note "${root_cause:-skipped by the worker}" \
```

**The red test.** One case, `run#aSkipPrintsTheReasonTheChildGave`
(`tools/loop/tests/loop-cases.sh`), drives a skip through the real run against the stub child and asserts the note and
the report's line; on the base's reading it fails with
`the note is the child's own sentence, not the constant the skip arm wrote: expected 'one item of 35 tasks, …', got
'skipped by the worker'` — `bash tools/loop/tests/loop-selftest.sh --case 'run#aSkipPrintsTheReasonTheChildGave'`,
`tests=1 failures=1`, 2026-11-12T13:40Z (`evidence/red-on-head.log`, `evidence/red-on-head.xml`). With the fix the same
case passes (`evidence/green-case.log`, 2026-11-12T13:40:33Z), and the invariant's own record,
`tools/loop/tests/loop-mutations.sh` `854.1`, puts the constant back and the case goes red again
(`evidence/mutation-854.1.log`, 13:40:47Z).

**Owning requirement.** `openspec/specs/agent-issue-loop/spec.md`, the requirement "A state the run writes is readable
from the item" (`:771`) states that a state is accompanied on the item by the one line that accounts for it; nothing
states what the report prints for a skip, which is why the delta ADDS one requirement beside it rather than rewriting
it. The read of `report.sh:288` and `:625` is why the fix is at the writer: the reader already prefers a note.

**Owning guideline section.** `guidelines/Backlog.recipes.md` section 7e "Leftovers, the report and the queue" (`:1258`),
whose paragraph beginning "The report a run prints at its end is the run's product" (`:1394`) states what the three
answers are read from; it gains the paragraph that says a skip's line names the reason the item records.

## Surface

surface: 1 case added, 0 rewritten, 1 mutation record added, 1 engine line (plus its comment) in one file, 1
requirement added, 1 guideline paragraph — counted with
`tools/look.sh 'skipped by the worker' --path tools` → `LOOK: 1 matches in 1 files` at the base
(`git show 0e1ad308:tools/loop/run.sh | grep -c 'skipped by the worker'` → `1`), against `LOOK: 10 matches in 3
files` as this change leaves it — the writer, the new case and its record; `tools/look.sh 'result skipped' --path
tools` → `LOOK: 6 matches in 2 files`, of which `run.sh:3407` is the one skip arm and the five in the cases are
`state.sh` calls, so no second writer exists; and `tools/loop/tests/loop-selftest.sh --list | wc -l` → `415` cases
as this change leaves it, against the measure block's `LOOP-MEASURE-SUITE cases=414` taken before the case was added.

## What Changes

1. **The skip arm's note** (`tools/loop/run.sh:3405`): `--note "${root_cause:-skipped by the worker}"`, with the
   comment that names why the note is read by the report and where the constant still belongs.
2. **One case** (`run#aSkipPrintsTheReasonTheChildGave`, `tools/loop/tests/loop-cases.sh`): a `too-wide` skip whose
   child gave a reason asserts the note and the report's line; a second run whose child gave none asserts the
   constant is still what the note and the line carry.
3. **One mutation record** (`tools/loop/tests/loop-mutations.sh` `854.1`): the constant written back, the case must go
   red, the restore must go green.
4. **The guideline paragraph** (`guidelines/Backlog.recipes.md` section 7e) and the delta
   (`specs/agent-issue-loop/spec.md`).

## What this does not do

- It does not change what `report.sh` prints or reads. The reader takes `.note` and falls back to `.metrics.rootCause`,
  which is unchanged; a skip that carries the applier's own note keeps printing it, because the note is the field the
  applier writes.
- It does not rewrite the states earlier runs recorded. A skip already written with the constant keeps it; those
  records are history and are read from `.pi/`, not from this change.
- It does not recover a reason the record never held. #844's state carries an empty `metrics.rootCause` because its
  child wrote no `LOOP-ROOT-CAUSE` line, so the report can print only the constant for it; a reader of a skip whose
  child wrote its reason inside `LOOP-DECISION` prose is a second field and is not this change.
- It does not touch the escalation comment or the decision label. `loop_labels` already passes
  `${root_cause:-$skip_verdict}` as its reason (`run.sh:3399`), so the item's comment has named the reason since #192.
