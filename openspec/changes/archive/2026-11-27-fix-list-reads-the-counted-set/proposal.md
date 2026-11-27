# list.sh reads the set the tracker counts

## Why

`tools/issues/list.sh:49` is the window both of the tool's reads are asked for, and it was a fixed `400`: the
marker lookup (`tools/issues/list.sh:95`) and the filter selection (`tools/issues/list.sh:147`). The tracker holds
more than 400 issues, so `--id N` for a migrated entry older than the newest window answered
`list: no issue carries <!-- nvid: N -->` — the exit-4 "this entry does not exist" answer — over an item the
tracker holds, and every filtered selection (`--eligible-only`, `--prio`, `--class`, `--category`) was drawn
from an arbitrary 400 rather than from the set the labels name, with nothing in the output naming the window.

The two answers a caller acts on are "this item is here" and "this item does not exist". A window makes the
second untrue without saying so, which is a defect and not a tuning: a wider constant would return to the same
silent partial read at the next growth.

- **root cause** — one constant (`limit=400`) is at once the size of the read and, silently, the set the read
  claims to select from.
- **requirement** — `issue-backlog`: the read a lookup and a selection are taken over is the set the tracker
  counts, not a page of it.
- **route** — `guidelines/Backlog.md` §6 "How an item becomes work", whose consequence list states that the set
  selection examines is the tracker's own **counted** set — counted before the read, and a read that comes back
  as full as the window it was asked for is refused.

LOOP-ROUTE: guidelines/Backlog.md §6

## What changes

Both reads go through the whole-set reader the tooling already carries — `issues_set_count` and
`issues_issue_list` (`tools/issues/lib.sh:315`, `:340`) — in the shape `issues_page_records`
(`tools/issues/lib.sh:963`) and the decision queue (`tools/issues/decisions.sh:71`–`:95`) already use: the set is
counted, the list is asked for that count plus one, and an answer that full is refused as `issue-set-uncovered`
instead of being reported as the set.

`--limit` stays and becomes what its name says: the window a caller explicitly asks for. With no `--limit`, the
default is the set the tracker counts.

The three call sites that inherited the window and would keep the truncation drop it: `tools/loop/prescreen.sh:39`
and its read at `:262`, and `tools/loop/run.sh:674`, `:679`, `:684`.
