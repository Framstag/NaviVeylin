# Design — the read doubles its window and stops on the short page

## Open decisions

none

## Root cause, in one sentence

`tools/issues/check-metadata.sh:150` and `tools/sensors/lib.sh:476` ask `gh issue list --state all --limit 400`, which
answers the tracker's **newest** 400 items, so the read is a slice rather than the tracker: the 738-item tracker of
2026-11-26 left 338 items unchecked, and the count the pass printed read as the population it covered (#1091).

## The fix

One function in `tools/issues/lib.sh` — the file both call sites already source (`check-metadata.sh:38`,
`sensors/lib.sh:47`) — one shared list helper delegating to it, and two call sites moved onto it. Nothing else
changes.

```
issues_issue_list_all <json fields> → the tracker, every item of it
  window=1000
  loop:
    json = gh issue list --state all --limit $window --json <fields>
    if length(json) < window: print json; return 0     # a short page: nothing was cut off
    window = window * 2
```

Three properties are what make this the mechanism rather than a bigger number:

1. **A short page is the proof.** The read is not trusted because a number was raised; it is trusted because the last
   page came back shorter than the window that was asked for, which is the only observation a newest-first list can
   give that nothing was dropped.
2. **One call while the tracker fits.** The first window is 1000 (the tracker is 738 today), so the ordinary run
   costs exactly the one `gh issue list` call it costs now — which is what the existing case
   `metadata#bothPerItemModesCheckIt` counts (`grep -c 'issue list'` = 1).
3. **One home.** `sensors/lib.sh` already sources `tools/issues/lib.sh` for the profile reader, the policy gate and
   the exit-code contract, so the rows and the pass read the tracker through the same function rather than two
   copies that drift.

## Blast radius

- `tools/issues/lib.sh` — one added function, and `issues_issue_list` (`:329`) delegates to it: the dedup and
  selection queries read the tracker through the same read, because the item a window drops is exactly the one a
  duplicate search cannot see. The citation repair (`resolve-stale.sh:116`) keeps the window it has, and is named
  here rather than half-fixed.
- `tools/issues/check-metadata.sh` — one line: the read. `checked` now counts the tracker.
- `tools/sensors/lib.sh` — one line: the read inside `sensors_issues_of_row`, which the stub already serves as one
  call for any tracker inside the first window.
- **Not reachable**: `gh issue view`, the write paths, the marker grammar, the row's guard (`checked` non-zero),
  and the profile. The suite's stub already slices a fixture longer than its `--limit` newest-created first
  (`tools/issues/tests/fixtures/gh:355-360`), which is why the red case needs no stub change.

## Rollback

`git revert` of the one commit: the helper goes, the two lines go back to `--limit 400`, the case goes. Nothing is
migrated and nothing outside the four files is touched.

## The alternatives the evidence eliminated

- **Raise the bound (400 → 2000).** Eliminated: the tracker's growth is unbounded and the bound would be the same
  defect one year later, with the same silent shape — a full-looking population over an unread tail.
- **Count first, then read `count + 1`** — the mechanism `tools/issues/decisions.sh` uses (`guidelines/Backlog.md`
  §5, #805). Eliminated here: the count is a second `gh` call (`gh api graphql` `totalCount`) for a read whose own
  page already proves coverage, and this read is a hot path — `sensors_issues_of_row` runs once per row of the
  pipeline, so the count would be paid per row. Where the queue's set is a label query the CLI cannot page, the
  count is what makes a *full* window meaningful; here a *short* window is the same proof for free.
- **`gh issue list --paginate`.** Eliminated: `issue list` has no offset-based paging (`--paginate` belongs to
  `gh api`), so paging the CLI means asking a larger window — which is this fix.
- **Read once at a very large limit and never re-check.** Eliminated: the same as raising the bound, with the
  read's own cost unbounded on every run.
