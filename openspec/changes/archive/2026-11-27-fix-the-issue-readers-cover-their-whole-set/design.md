# Design — the readers' window is the counted set, not a constant

## Root cause

One mechanism: each reader asked `gh` for a fixed number of the newest issues and treated the answer as the set.
`gh issue list` answers newest-created first, so on a tracker larger than the constant the oldest items were never
read — and every reader's report was a claim about a set it had not covered.

## The fix

`tools/issues/lib.sh` already knows how to count the set (`issues_set_count`, #1093) and how to refuse a read that
comes back as full as the window it asked for (`issue-set-uncovered`, `issues_page_records`). The fix names both:

- `issues_set_count [open|all]` — `all` is `states: [OPEN, CLOSED]`, `open` is `states: [OPEN]`; the resolver
  walks the backlog, the other two decide on the tracker.
- `issues_set_window [open|all]` — the counted set plus one. The plus one is what makes a full read visible.
- `issues_set_covered <set> <window> <read>` — the refusal, extracted from `issues_page_records` so that the pass
  and the three readers refuse in one voice instead of three messages drifting apart.

Each reader then asks the count, reads at that window, and hands the read to `issues_set_covered`. The
duplicates sweep keeps `--limit` as a *narrowing* (a person may want a bounded sweep) and refuses a narrowed read
that fills its window, so no flag value can make it report a partial read as the tracker.

## Blast radius

- `tools/issues/lib.sh` — two new functions, one extraction. `issues_page_records` keeps its behaviour: same
  refusal code, same message.
- `tools/issues/duplicates.sh` — the default read widens from 400 to `count + 1`; `--limit` still parses.
- `tools/issues/check-metadata.sh`, `tools/issues/resolve-stale.sh` — one extra `gh api graphql` count call per
  run, before the single `gh issue list` read they already took. No per-item call is added.
- `.github/workflows/sensors.yml` — one flag dropped from the sweep's invocation.
- `tools/issues/tests/fixtures/gh` — the stub answers a count scoped to the states the query names (`[OPEN]` vs
  `[OPEN, CLOSED]`), because it already refuses a query it does not describe; before this, an open-scoped count
  could only be answered by the whole tracker, which is the conflation the fix is about.
- `guidelines/BacklogTooling.md` §9 — one sentence.
- Three suites gain one case each.

Runtime: the three sweeps read the whole tracker instead of 400 items, which on today's 739 issues is 340 more
bodies in the resolver's walk. That is the cost of the claim being true, and it is bounded by the tracker.

## Rollback

Revert the commit; no data is written differently, no label or state changes shape, and the count call is the
pass's own.

## Alternatives the evidence eliminated

- **A bigger constant.** The tracker's size is the fact the constant guessed at; the count read already exists and
  is the same call the pass makes. A bigger constant is the same defect with a later date.
- **Drop `--limit` entirely.** It would make a bounded sweep impossible and break the scheduled workflow line in
  the same commit; keeping it as a widening-only-narrowing bound with the refusal attached leaves no way to report
  a partial read as the set.
- **A per-reader window helper.** Three copies of "count, add one, read, refuse" is three places for the message
  to drift; the extraction keeps `issue-set-uncovered` single.

LOOP-ROUTE: guidelines/BacklogTooling.md §9
