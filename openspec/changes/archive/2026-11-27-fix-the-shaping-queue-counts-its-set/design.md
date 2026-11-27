# Design — the counted set, and the one read that covers it

## Root cause

One mechanism: on the base commit `f36d9ee`, `tools/issues/shaping.sh` took the window it asked the list for from a
constant of the tool (`limit=400`, `tools/issues/shaping.sh:26` at that commit), passed it straight to
`gh issue list --state all --limit "$limit"` (`:48`), and printed no coverage at all — not the window, not the
counted set. The list answers
newest-first, so on a tracker larger than the constant the oldest items carrying the label are never read, and the
queue's empty answer carries no way to tell a covered inbox from an unread one.

## The single fix

Count the set, then read it — the shape `tools/issues/decisions.sh:71`–`:95` already carries for the decision queue
since #805, applied to the shaping label:

1. `shaping.sh` resolves the repository the same way `decisions.sh` does (`$ISSUES_REPO` from `issues_resolve`) and
   asks `repository(owner, name) { issues(states: [OPEN, CLOSED], labels: [$label]) { totalCount } }` — the labelled
   population, not the tracker's whole size. A count the tracker does not answer stops the queue with
   `issues_die`, because a queue that cannot count its set cannot claim to cover it.
2. The list is asked for `count + 1`, scoped by the same label, so the window is the inbox's own size.
3. A read that comes back as full as the window is refused as `shaping-set-uncovered` (exit 3), naming the counted set
   and the read: it is the read the tracker overran (#805's own reason, unchanged).
4. Both human branches print the set the count was taken over — `read from the N item(s) the tracker counts carrying
   <label>` — after the queue's own count line, and the empty inbox prints it too. That line is the coverage claim the
   output was missing, and it is what makes "no item awaits shaping" checkable.
5. `--limit N` stays the caller's explicit window (the flag's documented meaning): it replaces the counted window
   instead of being added to it, and a window the labelled set overruns is the same refusal. The count is still taken
   under `--limit`, because the coverage line states a number the queue did not pick.

`lib.sh` is not touched: unlike the tracker-wide read #1093 repaired (`issues_page_records`), this queue has one
call site and one label, so a helper would be a seam with a single caller.

## Blast radius

- `tools/issues/shaping.sh` — one added count call, the window, one refusal and two coverage lines. `--json` output is
  unchanged; `LOOP-SHAPING-COUNT` stays on stderr.
- `tools/issues/tests/issue-tooling-cases.sh` — two cases and one fixture helper (`shaping_label`, `shaping_tracker`),
  registered in `ISSUES_SHELL_CASES` and `issues_run_shell_case`.
- `guidelines/Backlog.md` §5 — one sentence in the shaping-inbox paragraph, stating the counted set and the refusal.
- The `gh` stub needs no edit: it already answers a label-scoped `totalCount` (`tools/issues/tests/fixtures/gh:223`–
  `:255`) and already slices a label-scoped list newest-first (`:354`–`:371`).
Every existing shaping case keeps passing: the label-scoped read returns the same items the tracker-wide read
filtered down to, and `--json` is untouched.

## Alternatives the evidence eliminated

- **Raise the constant.** The defect is the constant, not its value: `739` items today, unbounded tomorrow, and the
  output would still say nothing about what it read. This is exactly the tuning #805 refused for the decision queue.
- **Count the whole tracker and read `count + 1` unscoped** (the shape #1093 used). Legal, but it asks a 739-item
  page to answer a 2-item question and pays for it on every read; the label scope is already how the set is defined.
- **Print the window instead of the counted set.** A number the queue picked rather than one the tracker stated is the
  claim a reader cannot check — the reason `decisions.sh:163` prints the counted set.

## Rollback

Revert the change: `shaping.sh` returns to the fixed window with no count call, and both cases leave with the
spec delta. No state is written by this change, so a rollback restores exactly the old behaviour.

## Red test

`shaping#anItemOutsideTheNewestPageIsListed` — a 405-item stub tracker whose `#1` and `#405` carry the shaping label,
so `#1` sits outside the newest 400. On the base tree the queue prints `#405` only and no coverage line. The refusal
half is `shaping#aReadThatCannotCoverTheSetIsRefused`: `GH_STUB_COUNT_LAG=1` over a 3-item tracker with two labelled
items makes the window 2 and the read 2, which must exit 3 naming `shaping-set-uncovered`.
