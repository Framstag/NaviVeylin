## Root cause

`tools/issues/resolve-stale.sh:190` (base `9e7e969`) spends a slot of the run's bound as soon as an item is
*reachable* for the repair ladder — `due=$((due + 1))`, before the two token loops classify anything — while
the write that was supposed to justify the slot happens at `:236` (repair) or `:248` (park) and needs `repairs`
or `unresolved` to be non-empty. The suffix loop's ambiguous branch (`:203`) fills neither: it counts the
ambiguity (`ambiguous`, `item_ambiguous`) and `continue`s, because an ambiguity is reported, never guessed
(D4). So the reachable-but-unwritable case has no write, yet has already paid, and `:168`'s `while … "$due" -lt
"$max_items"` stops the walk there. Run 39296403233: `due=25 repaired=4 parked=5 ambiguous=22 waiting=66` —
16 of the 25 visited items produced no write, the `ambiguous=22` counts tokens.

One mechanism, not two: the bound is charged against a property the run cannot know it has until after the
work that would justify it.

## The single fix

Move the charge to where the evidence is:

```bash
if [ -n "$repairs" ] || [ -n "$unresolved" ]; then due=$((due + 1)); else skipped=$((skipped + 1)); fi
```

placed between the token loops and the two write branches, and delete the `due=$((due + 1))` at `:190`. The
bound now counts the items a run acts on — a repair or a park, and in the default dry run the items it would
act on; an item the ladder can only report is visited, accounted `skipped`, and costs nothing — so the walk
continues to the next item. `read = due + skipped + waiting` still accounts for every
issue in the tracker, `ambiguous` still counts every ambiguous token, and the write branches are untouched:
nothing is rewritten, parked or commented for an ambiguous citation.

The `skipped` counter was already the "visited, nothing to write" bucket (an item with no stale token, and a
parked one), so no new counter and no new line format is introduced: the run's summary line keeps its shape.

## Blast radius

`tools/issues/resolve-stale.sh` (one guard added, one increment removed, comments), `tools/resolve-stale-cases.sh`
(one case, one comment line), `tools/resolve-stale-mutations.json` (one record), the spec delta. No compiled
file, no tracker write of its own, no change to the classifier, the ladder, the parking rule or the residue
metric's measurement (`.parked` is read from a dry run and unchanged for the ambiguous items, which are still
not parked).

## What the evidence eliminated

- **Park the ambiguous item** (the same fate as an `absent` citation with no successor). Eliminated on four
  grounds, in the order they were measured: (a) it is a second delta — the parking requirement's WHEN is "a
  citation is `absent` and no mechanical successor exists", and an ambiguous suffix is `suffix`, so this
  option also reverses the ladder's stated rule that an ambiguous match is reported rather than acted on; (b)
  it would put 22 items on hold whose citations are *not* proven gone, so a person's queue fills with items
  whose only defect is that the tooling could not decide; (c) it moves the residue metric as a side effect —
  `tools/sensors/metrics/stale-citations.sh` reads `.parked` from the bounded dry run against bound 5 with
  `consecutive: 2`, so a budget fix would file a `unresolved-citations` violation on its first scheduled run,
  which is an alarm the repair tool caused rather than a condition it found; (d) it would relax an existing
  assertion — `resolver#ambiguityIsReportedNotRepaired` asserts no `gh issue edit` at all, and a park is one —
  and an assertion rewritten to fit a fix is a falsification spent.
- **Order the queue, writable items first, ambiguous tail last** (a two-pass walk). Eliminated: it needs the
  same classification work once per pass, and every `absent` token costs a `git grep` inside `successor_of`, so
  the second pass doubles the run's most expensive step to produce the same visible result the guard produces
  in one traversal — and it still leaves tracker order deciding which writable items fit.
- **A visit cap beside the write cap** (stop after scanning N items). Eliminated: the daily residue metric
  already walks the whole tracker (`--max-items "${SENSORS_STALE_MAX_ITEMS:-1000}"`), so the walk's cost is
  paid once a day today; a visit cap would only reintroduce this defect at a larger N.

Honest cost of the fix: a run whose head is all-ambiguous now walks further than `max_items` items, so
`wall_ms` grows towards the tracker's size. That is the same walk the metric performs daily, and it is what
"bounded by writes" costs.

## Rollback

Revert the commit. The bound counts visits again. No tracker state exists to undo: this change writes nothing
to the tracker by itself, and the parked items of the base commit stay parked under either behaviour.
