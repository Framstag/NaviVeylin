# The census, before and after

The number the change exists for, taken each time by a command the tree carries and the same rule: an open item
that carries no issue type. It is a **report**, so the items below never turned a run red while they lasted (D1).

## Before

- **2026-11-14, the item's own measurement**: 60 of 77 open items carried no issue type
  (`gh issue list --state open --json number,issueType --limit 200 | jq '[.[]|select(.issueType.name==null)]|length'`).
- **2026-11-20, the backfill**: 53 untyped open items, typed from the labels they already carried by D2's table —
  37 `Task`, 9 `Bug`, 7 `Feature`, 0 `Epic` — one `tools/issues/update.sh --type` per item and nothing else
  written. `backfill-plan.tsv` beside this file is that read's plan: one row per untyped item, its class labels,
  and the type the table gives it.
- **2026-11-20T22:00Z, the residue at this round's base** (`4c7202eb`): **4** untyped open items, `#982`, `#983`,
  `#984` and `#985`, read by the change's own query — `tools/issues/list.sh --untyped` printed `4 match(es)`, which
  is that read. All four carried `class/improvement` and no `parent`, so D2's table typed them `Task`, one
  `update.sh --type` each. The table's container row and its last row (the item it cannot type) were reached by
  none of them, which is why task 4.2 is a no-op whose observation is this paragraph.

## After

Taken 2026-11-20T22:00:51Z with the change applied, at HEAD `4c7202eb` (`evidence/live-census.log`):

```
--- the proposal's surface command
open items with no issue type: 0
--- tools/issues/check-metadata.sh --live | tail -n 1
check-metadata: 400 issue(s) checked · 1 violation(s) · 0 untyped
--- tools/issues/list.sh --untyped
0 match(es)
```

The census reaches **0**, and the tracker holds no item D2's table could not type. The one violation the live pass
reports is not this axis: `#981 label-without-marker: prio/p3` is a band label whose marker is missing on an item
filed after the backfill, and it is what the `metadata-invariant` row files on its own schedule. `--live` reads
`--state all`, so the census asks for `state` in the same call and leaves the 321 closed entries out: an item out
of the backlog is not the open backlog's shape.

## Why the zero is not the report switched off

`check#untypedItemIsReported` drives the census both ways over two committed fixtures
(`fixtures/metadata/untyped-item.json`, `fixtures/metadata/typed-item.json`): the untyped item is named, counted in
the summary and carried under the `--json` `untyped` field; the typed one is not; giving the untyped item a type
empties the set, and taking the typed item's type away puts it back. Three more artifacts say the case measures
something:

- `evidence/red-on-HEAD-4c7202eb.log` — the same two fixtures against the base's own engines: no census line, no
  count, no `untyped` field, and `list.sh --untyped` refused with `unknown argument`.
- `evidence/red-on-HEAD-4c7202eb-case.log` — the case itself, run against the base's own engine: six assertions
  red, exit 1. The three the base cannot satisfy are the census line, the count in the summary and the `untyped`
  field; the rest follow from the same absence.
- `evidence/green-on-change.log` — the suite the case passes in: 188 cases, offline, no token, no device.
- `evidence/mutations.log` — three mutations, each turning its case red and each restored: the census block
  disabled, the census line removed from the report, and `--parent` taken out of `.pi/skills/create-issue/SKILL.md`.
  The second one also turns the check's **no-argument** mode red (`#census not-wired`, exit 1), which is that
  mode's half of the wiring: it names the untyped fixture on every token-free run, so a report that stops firing
  is caught there before any tracker is read.
