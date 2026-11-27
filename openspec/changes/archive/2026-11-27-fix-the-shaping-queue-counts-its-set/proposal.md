# The shaping queue counts its set before it reads it

## Why

On the run's base commit `f36d9ee`, `tools/issues/shaping.sh` asked the tracker for a fixed window and never said which
window it read: `limit=400` (`tools/issues/shaping.sh:26` at that commit) reached the list as
`gh issue list --state all --limit "$limit"` (`:48`), and the list answers newest-first, so once the tracker holds more than 400 issues an
older item a run handed back is silently outside the read. Measured 2026-11-26 on the tracker as it stands:
`gh issue list --state all --limit 2000 --json number | jq length` → `739`, the newest 400 begin at `#681`, and the
labelled set is 2 (`repository.issues(labels: [agent/needs-shaping]).totalCount` → `2`). The defect is dormant
today and returns with the next handed-back item older than `#681`; #122 was the measured instance.

What makes it a defect rather than a tuning knob is the answer an empty inbox gives: `no item awaits shaping
(agent/needs-shaping)` is printed whether the set was covered or missed, and the header reads that answer as "the
item is visible work a person owes, and there may be none" (`tools/issues/shaping.sh:12`–`:14`). Nothing in the
output says a window was read, so a queue that missed an item is indistinguishable from a queue with nothing to
list — the one answer a person cannot check.

Root cause, owning requirement and route:

- **root cause** — the window the queue asked for was a constant of the tool rather than the size of the set the
  label selects, and the answer printed no coverage, so a read that stopped short of the set was reported as a queue
  with nothing in it.
- **requirement** — `shaping-inbox`: "The queue names the verdict that put the item there" (it SHALL list *every*
  open item carrying the shaping label).
- **route** — `guidelines/Backlog.md` §5, whose shaping-inbox paragraph states that `tools/issues/shaping.sh` lists
  every open item carrying the label; the change adds the counted-set clause to that sentence.

LOOP-ROUTE: guidelines/Backlog.md §5

## What changes

The count first, then the read — the shape the decision queue already carries since #805
(`tools/issues/decisions.sh:71`, `:88`, `:95`, `:163`), applied to a label-scoped set:

- `tools/issues/shaping.sh` asks `repository(owner, name) { issues(states: [OPEN, CLOSED], labels: [$label]) {
  totalCount } }` for the population the shaping label selects, and asks `gh issue list` for exactly that count plus
  one, scoped by the same label.
- A read that comes back as full as the window is refused as `shaping-set-uncovered` (exit 3), naming the counted set
  and the read — never printed as coverage of the set.
- The human form prints the set the count was taken over beside its own count (`read from the N item(s) the tracker
  counts carrying <label>`), so an empty inbox states what it covered instead of only that it found nothing.
- `--limit N` stays the caller's explicit window: it replaces the counted window, and a window the set overruns is
  the same refusal rather than a short listing.

`--json` is unchanged: it prints the selected items and `LOOP-SHAPING-COUNT` on stderr.
