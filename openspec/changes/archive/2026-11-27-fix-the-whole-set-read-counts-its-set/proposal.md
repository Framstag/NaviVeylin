# The whole-set read counts the set before it reads it

## Why

`issues_page_records` (`tools/issues/lib.sh:942`) is the one read both halves of the metadata pass
(`tools/issues/prioritise.sh:103`, `tools/issues/classify.sh:85`), the dedup
(`tools/issues/create.sh:383`) and the import (`tools/issues/import.sh:54`) go through. It took both
numbers it compares from the same answer: `issues_issue_list` asked
`gh issue list --state all --limit 400`, and `count` was `jq 'length'` of that very answer — so the
refusal compared the read to itself and could not fire.

Measured 2026-11-26 (`gh issue list --state all --limit 2000 --json number | jq length` → `739`): the
tracker holds 739 issues and the newest 400 begin at `#681`, so a pass reads 400 of 739 and reports
success. 339 issues are never examined, and a wrong band or a missing kind in them stays in place
indefinitely — the outcome `tools/issues/prioritise.sh:96`–`:99` argues against ("the selector examines
every issue and skips by *basis*, never by sampling", spec `issue-priority` "Selection never samples").

## What changes

The set is counted first and the read is asked for exactly that count plus one, which is the shape the
decision queue already uses (`tools/issues/decisions.sh:71`–`:95`): a read that comes back full is a read
the tracker overran, and it is refused as `issue-set-uncovered`. The counted number is the tracker's own
(`repository.issues(states: [OPEN, CLOSED]) { totalCount }`), never a constant of this engine.

Root cause, owning requirement and route:

- **root cause** — the read window and the coverage target were the same truncated answer, so the
  partial-page refusal could never see the remainder.
- **requirement** — `issue-priority`: "Selection never samples".
- **route** — "How an item becomes work", whose consequence list states *selection never samples*; the
  change adds the counted-set clause to that bullet.

LOOP-ROUTE: guidelines/Backlog.md §6
