# Design — the queue reads the labelled set, not a window of the tracker

## Open decisions

none

## Root cause, in one sentence

`tools/issues/decisions.sh:26` set `limit=400` and `:48` asked `gh issue list --state all --limit "$limit"` for that
many of the tracker's **newest-created** items, so the read was a fixed window over the tracker rather than the set
the queue is a query over, and every item older than the 400th was invisible to it — while the count it printed said
how many it had seen, which is the number a person reads as the whole queue (#805: 453 items on 2026-11-06, 53
unread, three of them carrying `agent/needs-decision`).

## The fix

Two passes, and a self-check that costs one item of window:

1. **Count.** `gh api graphql` asks the repository for `issues(states: [OPEN, CLOSED], labels: [$label])
   { totalCount }` — the population carrying the label, which is the set the queue is over. Nothing below names a
   number the queue picked itself, so nothing below goes stale as the tracker grows.
2. **Read, with one more than the count.** `gh issue list --state all --label "$label" --limit $((count + 1))`.
   Scoping the list by the same label is what makes the window the *counted* set's size rather than the tracker's,
   and the extra item is what makes a short read meaningful: a read that comes back shorter than its window had
   nothing cut off it, so the set is covered.
3. **Refuse a full window.** A read that comes back full is a read the tracker overran — items filed between the
   count and the list, or a count that lags — and there the queue exits 3 (`decision-set-uncovered`) naming the
   count and the read, printing no count of awaiting items. A count over a set it could not cover is the defect, in
   its silent form.
4. **State the set.** The human form prints `read from the N item(s) the tracker counts carrying <label>` beside the
   queue's own count, so a reader sees which population the count was taken over.

`--limit` is gone from the tool's usage: it was the guess, and a person who wanted more items was asking the tool to
read a window the tracker had already outgrown. The count is the window now, and the tracker is what sets it.

## Blast radius

- `tools/issues/decisions.sh` — the read, the refusal, the set line, the usage. `+41 −6`.
- `tools/issues/fixtures/gh` — the stub gains the fidelity the red case needs and only that: `gh issue list`
  answers `--label` and `--state` filtering and, when the fixture holds more items than `--limit`, slices
  newest-created first (the real CLI's order), which is what makes an old item fall outside a window; it answers
  `gh api graphql`'s `totalCount` from its own items and refuses a query it does not describe; and
  `GH_STUB_COUNT_LAG` makes that count lag the list, which is the race the refusal exists for. A fixture smaller
  than its window is served whole and in its own order, so no existing case changes shape.
- `tools/issue-tooling-cases.sh` — two cases and one helper (`decision_tracker`), which generates the tracker a case
  needs (405 items, or three) instead of checking in a fixture of hundreds.
- `guidelines/Backlog.md` §5 — the block that keeps the decision queue answerable goes from five rules to six.
- No compiled file is touched: this delta owes the issue tooling's self-test, not a build.

**The same fixed window is not this change's.** `tools/issues/shaping.sh:26` reads its queue with the same
`limit=400` shape, and `tools/issues/lib.sh:282`, `check-metadata.sh:127`, `list.sh:39` and `loop/prescreen.sh:39`
carry windows of their own (`tools/look.sh 'limit 400' --path tools` → 8 matches in 6 files). They are separate
items: a second capability delta (`shaping-inbox`) in this change is what gate 5 refuses, and the queue the item
names — the one a person is pointed at — is this one. The sibling shape is named here so the next item starts from it
rather than from a fresh measurement.

## Rollback

`git revert` of the single commit: the tool returns to the 400-item window and the two cases go red on it, which is
the state the base records (`evidence/red-on-HEAD.log`). Nothing is written to the tracker by either state.

## Alternatives the evidence eliminated

- **A larger constant (or `--limit` above the tracker's size).** The item's own words: a number above the tracker's
  size is a guess that goes stale exactly as 400 did. The tracker grew 53 items past the window between 2026-10-17
  and 2026-11-06, so the next constant has a date on it.
- **`gh api -X GET search/issues` (`total_count`).** Reports the population and is the item's own second suggestion,
  but it caps at 1000 results, so a tracker past that cap reproduces the same defect one order of magnitude later.
  It also spells `search`, which `tools/issue-tooling-cases.sh` `lib#noProjectValueInTheEngine` refuses: that word
  is this project's `category` value, and the engine may not name a profile value. GraphQL's `totalCount` is
  uncapped and names no profile value; the case above caught the search version (one red suite) before the diff
  was written.
- **`gh issue list --paginate`.** `gh issue list` has no `--paginate`: the flag belongs to `gh api`, so the
  population would have to be read as REST issues, which arrive without comments — the queue's whole second half —
  and with pull requests mixed in.
- **Reading the label-scoped set with no count.** `gh issue list --label X --limit 400` narrows the truncation to the
  labelled population, which is small today, but the population grows by every item that ever carried the label,
  and the count printed would still be a window's.
