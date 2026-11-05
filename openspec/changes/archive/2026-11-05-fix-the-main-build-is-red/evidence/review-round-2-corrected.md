# Review round 2 — the corrections applied

The run's review round 1 (`review-round-1-requested.md`) returned one blocking finding and five notes. The
round that answered them was ended by the run's turn bound (`LOOP_ITEM_TURNS_MAX`, 250 turns) before it committed,
so the corrections below were applied by hand afterwards, on this branch, against the tree the round-1 reviewer
saw. Nothing in `app/` was touched: this record is the review's answer, not a code change.

## The blocking finding

The shipped requirement "A user orientation choice outranks an in-flight settings read" carried a second scenario
whose WHEN and THEN described a fixture its own named case does not use — "the persisted settings carry 'North
always up' for both modes" / "both orientations SHALL be 'North always up'", while
`NorthUpLateRestoreTest.orientationTheUserDidNotChooseIsRestoredFromDisk` persists `navNorthUp = true,
freeFormNorthUp = false` and ends in `assertFalse(freeFormNorthUp)`. The case proves the stronger, different claim
"each persisted value reaches the state, both opposite their defaults".

**Correction, subtractive** — the scenario was reworded in both copies (`openspec/specs/compass-settings/spec.md`
and this change's delta), no case, claim or assertion added:

```
- **WHEN** the persisted settings carry a navigation orientation and a free-form orientation that each differ
  from its default
- **AND** the user changes no orientation while the start-up read is open
- **WHEN** that read completes
- **THEN** each orientation SHALL be the persisted value, read from disk
```

The fixture was not changed instead: free-form's default *is* north-up ("Default orientation is north-up"), so a
"both persisted north-up" fixture cannot distinguish restore from default on the free-form half. The sentence was
what lagged behind the corrected fixture.

## The five notes

- `proposal.md` `surface:` — the test grep prints **7** files (the two changed/added plus five untouched), and the
  main-source grep is narrowed to `app/src/main/java/com/naviveylin/ui/map` (46 lines) with the changed sites named
  as the enumeration, instead of the tree-wide count read as an enumeration.
- `proposal.md`/`design.md` — the `§6e` quote is replaced by the rule that section states ("a test that must wait
  drives the component's seam and awaits observable state"); the quoted sentence was the change's own, not the
  guideline's. The `MapCanvasViewModelFollowModeTest.kt:257-269` pointer for the deleted workaround text is marked
  as a base-`ec58cb91` pointer, since that text no longer exists on this branch.
- `design.md` — the base-relative line pointers were re-read against the tree under review: `isNorthUp`
  `1031 → 1032`, the unconditional north-up commit `1053-1054 → 1055-1056`, the `lastUsedAngle` fallback
  `1070 → 1072`, the failing case's second-fix block `428-437 → 429-436`.
- `tasks.md` — task 6's automotive timestamps are the carried tallies' exact values
  (`09:14:44.508Z` / `09:14:44.337Z`), and task 7 says its own step carried no tally and names the gate run
  (`tests=458 failures=0`) as what measures the classes it covers.
- `evidence/` — the `tests=458` aggregate is labelled as the gate script's own count (its XML wrapper is
  `tests="0"`), and the correction round's two tallies are copied in beside the rest
  (`green-discriminating-…`, `red-freeFormRestoreRemoved-…`, documented in `gate-summary.txt`).

## What this does not change

The production guard, the two red cases, the test-fixture seam and the gate run of `2026-11-05T09:25Z–09:27Z`. This
round edits Markdown only, so no build and no suite is owed (`AGENTS.md`, "Verification follows the changed
files"): the gated tree and the merged tree differ in prose.
