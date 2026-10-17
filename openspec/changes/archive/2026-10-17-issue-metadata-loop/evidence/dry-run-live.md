# The live dry run (task 7.1)

Read-only. `--dry-run` is the default of both entry points, so nothing below could have written: the write
branch is guarded by `[ "$write" -eq 1 ]`, the run's own report says `written=0`, and the tracker was
inspected afterwards and carries no priority metadata at all.

Date: 2026-10-17 · tracker: `Framstag/NaviVeylin`, 159 issues (`gh issue list --state all`)

## The priority half

```
$ tools/issues/prioritise.sh --json --dry-run
```

| counter | value | what it means |
|---|---|---|
| `read` | 159 | every issue was examined — the page set, in one call, with a refusal when the count disagrees |
| `skipped` | 0 | no issue carries a marker yet, so no basis can match: this is the first run |
| `due` | 159 | the whole tracker is due once, and only once |
| `selected` | 20 | the run's bound (`--max-rescore`, default 20) |
| `waiting` | 139 | reported, not hidden — the queue a bounded run leaves behind |
| `unclassified` | 0 | every imported issue carries a `class/*` label, so none is blocked on classification |
| `scored` / `written` | 0 / 0 | a dry run interprets nothing and writes nothing |
| `refused` | 0 | no out-of-scope entry, no fence refusal |
| `batches` | 1 | one model batch for the 20 selected |
| `wallMs` | 7856 | 7.9 s, wall clock, for 159 issues — the script half of the cost |

Two runs of the dry run print the same numbers, because a basis is a pure function of the issue and its
recorded marker (its five inputs are the labels, the body's digest, the citations' existence flag, the
recurrence count and the freshness bucket — none of them is a clock reading).

## The classification half

```
$ tools/issues/classify.sh --json --dry-run
```

| counter | value |
|---|---|
| `read` | 159 |
| `skipped` | 159 |
| `due` | 0 |
| `selected` | 0 |
| `refused` | 0 |

The queue is empty: the migration classified every issue it imported, and no issue carries a category
outside the vocabulary — so nothing is reported for repair either.

## The tracker before and after (the write-nothing proof)

```
$ tools/issues/check-metadata.sh --live
check-metadata: 159 issue(s) checked · 0 violation(s)

$ tools/issues/list.sh --unranked --json | jq length
159
$ tools/issues/list.sh --prio p0,p1,p2,p3 --json | jq length
0
```

Every issue is unranked and none carries a band label, so the dry run left no trace. That is also the
starting point of the first real run (task 7.2): 159 due, 0 skipped.
