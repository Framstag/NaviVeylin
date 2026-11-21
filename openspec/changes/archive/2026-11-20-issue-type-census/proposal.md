# Issue type census

## Why

The tracker's own classification has three parts that work and one that does not. `create.sh --type/--parent` and
`update.sh --type/--parent` write them, `roles.containerType` makes the pre-screen refuse an `Epic` as work, and
`guidelines/Backlog.md` §3 owns the rule. What is missing is every path *to* that knowledge and every path that
*measures* it:

- The document a filer is handed — `.pi/skills/create-issue/SKILL.md` — named neither `type` nor `parent`, in its
  field list or in its variants block, so a filing that followed the routed skill could not learn the axis exists
  (measured 2026-11-14: a series of nine items was filed as one `class/feature` item plus eight unlinked siblings,
  and the repair was nine `update.sh` calls).
- Nothing reads the item's type. `check-metadata.sh` and `list.sh` never look at `issueType`, so an untyped item is
  invisible: the container rule silently does not apply to it, and no run reports the gap (measured 2026-11-14:
  **60 of 77** open items carry no issue type).
- The routed skills were repaired in the same session that found this, doc-only and with no case behind them, so
  the next edit can take the pointer away again exactly as it did before.

## What Changes

- **A census the tooling can run.** `check-metadata.sh` reports an open item with no issue type as a *report*, not a
  refusal — 60 legacy items must not turn every run red — and `list.sh --untyped` makes the same set one query.
- **A case that locks the route.** The issue tooling's own suite asserts that the two filing skills name `--type`
  and `--parent`, so a skill that loses the pointer fails the suite instead of a session.
- **A bounded backfill.** The 60 untyped open items are typed from the labels they already carry (`class/bug` →
  `Bug`, `class/feature` → `Feature`, the rest `Task`), one item at a time through `update.sh`, and a container
  among them is `Epic` with its children linked — the census is the acceptance number, and it reaches 0 for items
  whose labels allow a type.

## Capabilities

- `issue-backlog` — **ADDED**: the census requirement and the routed-pointer requirement (2 new requirements).

## Impact

- `tools/issues/check-metadata.sh` — the report, its `--json` field and the self-check.
- `tools/issues/list.sh` — one query for the same set.
- `tools/issues/tests/check-metadata-selftest.sh` — the case, with its two fixtures.
- `tools/issues/tests/issue-tooling-cases.sh` — the route case over the two skills, and the query case.
- `.pi/skills/list-issues/SKILL.md` — the query, beside the ones it already documents.
- `guidelines/Backlog.md` §3 — the census sentence and the command that re-takes it.

## Decisions

- **Report, not refuse** (D1). A violation list that cannot go green is a list nobody reads; the exit status stays
  what it was, and the number is the measure.
- **Label-derived typing for the backfill** (D2). A type is the tracker's word for the item's shape; the labels the
  item already carries are the project's word for the same thing, so the mapping is mechanical and reviewable, not
  a judgement per item.
- **The route is a case, not a paragraph** (D3). Documentation-ownership's own lesson: a pointer that is only
  written is a pointer that drifts.

## Open questions

- none — the three decisions above are taken, and the remaining question (which of the 60 items deserves a type at
  all) is answered by D2's rule, with anything it cannot type left untyped and reported.

surface: `gh issue list --state open --json number,issueType --limit 200 | jq '[.[]|select(.issueType.name==null)]|length'`
reported **60** when this change was filed and reports **0** after the backfill (`evidence/census.md`); the number it
was filed for is the one it drove down.
