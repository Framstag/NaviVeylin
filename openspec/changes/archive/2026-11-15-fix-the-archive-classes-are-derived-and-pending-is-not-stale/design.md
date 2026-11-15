# Design — the archive's classes are derived on read, and the two stale states are reported apart

Both faults are the same shape: **an absence read as a measurement**, and a figure printed where the reader cannot
tell what was measured from what was not.

## Fault one — a dash counted as a class (#922)

`done --stop-reason` arrived with #865, after most of the archive was written, so **119 of the archive's 140 blocked
records and 15 of its 39 cut ones name no mechanism at all**. The analysis pass totalled them with
`(.stopReason // "unclassified")` — `//` fires on a missing key and the records carry an empty string, so the absence
was printed as a class of its own beside the real ones, and read as a zero by everyone who quoted it. The published
figure — `blocked by stop class: bound 27 · child-blocked 1 · refused 9 · unmergeable 5 · undecidable 98` — was right
about everything it counted and silent about the 119 it could not.

The engine already owns the reader: `tools/loop/lib.sh:326` `bound_class_jq` defines `bound_named`, `bound_round`,
`bound_of` and `stop_class` (`:346`–`348`), and two of the engine's own surfaces already read a record through it —
the run's per-item listing (`tools/loop/state.sh:338`) and its record report (`:1510`) — so the fix is one derivation
read by a further caller, not a second one written:

1. **`tools/loop/report.sh:295`–`321`** totals the archive with that `stop_class`, over the population a class can
   belong to — `blocked` and `cut` (`:307`), because a closed, skipped or falsified item is recorded with no class by
   design, and a denominator of all 511 records would drown the figure the line exists to print — and prints three
   sides: the decided classes, `undecidable`, and `ended by a review round, not by the item` (`:310`–`317`). Undecidable
   is summed into neither, because counting it as a class invents one and counting it as decided calls a measurement
   nobody made an answer. An archive with no `blocked`/`cut` record prints `none — no archived record ended blocked or
   cut`, and a project with no archive at all prints `archive classes: -` (`:304`, `:313`, `:320`).
2. **`.pi/skills/analyse-loop-run/bin/analysis.sh`** sources that reader instead of writing a second one
   (`:31`–`38`, `stop_class_defs=$(bound_class_jq)`), and its two `rates` blocks read it (`:272`, `:276`–`280`,
   `:284`–`286`) — so the pass's `bound` and the report's `bound` cannot drift, and the pass prints `undecidable` as a
   figure rather than as a class.

The census the shipped change measured (2026-11-15, `guidelines/Backlog.recipes.md:1628`–`1643`): **158** archived
states, **511** item records, **179** `blocked` or `cut`, **66 decided** = `bound 51 · child-blocked 1 · refused 9 ·
unmergeable 5`, **107 undecidable**, **6 ended by a review round**. `bound 51` rather than the pass's 27 because the
21 blocked records whose own note still quotes the `LOOP-TIMEOUT` sentence that ended them are `bound` — the reader
decides them and the field alone could not.

## Fault two — two states printed as one (#926)

`tools/gen-feature-list.sh` compared the classification's ids against the *shipped* ids and printed every difference as
`stale`. A classified line for a capability an open change introduces is a difference of exactly that kind — and it is
not stale at all: the line is due **before** the archive, so it is pending work, and the `stale: 0` a change promised
itself was unreachable while any open change owed a line (measured 2026-11-15: `stale: 7`, five of them deltas;
`tools/gen-feature-list.sh:198`–`203`). The fix is one split, report-only on both sides:

- `pending` = classified ∩ delta ids (`:205`), `stale` = classified − shipped − delta ids (`:206`);
- neither sets `gate_errors` (`:259`–`265`), which is the shipped rule `guidelines/FeatureList.md:154` — a stale entry
  is reported without failing — and its scenario `gate: a stale classification entry does not fail the run`;
- the check prints both figures (`:288`–`289`) and the report prints both (`:438`–`439`);
- `guidelines/FeatureList.md:158`–`159` does not change: it already says the two states were conflated until
  2026-11-15 and names the same run. The split is what its paragraph describes.

## Why #922 and #926 ride one change

They were committed together (`76588853`, `1eddc98c`, `cd45d567`), they are one shape — absence read as a measurement,
a figure the reader takes for a zero — they are the same kind of tooling (a jq derivation over a file the tool does not
own), they are the same defect class with the same remedy (derive, do not infer from a missing field), and they are one
record for the reviewer that will read them. Two change directories would describe one commit twice and split the
evidence of one gate. The change carries two capability deltas because the behaviour lives in two capabilities: the
archive census in `agent-issue-loop`, the classification report in `spec-feature-index`.

## Why `spec-feature-index` and not `feature-list-generation`

The tool is the feature catalogue's generator, so the label on #926 says `feature-list-generation`. The capability
that owns the rule is not where the reader looks first: `openspec/specs/spec-feature-index/spec.md:54` holds the
requirement *An unclassified spec id stops the run*, with its stale-entry scenarios and its pending-before-archive
scenarios; `openspec/specs/feature-list-generation/spec.md:156`–`160` mentions `stale` only in a comparison of the
three documents' audiences, and every sentence there stays true after the split. A delta against
`feature-list-generation` would be a MODIFIED block repeating a requirement this change does not alter, which is the
one thing a MODIFIED block may not do.

## The two requirements: one MODIFIED, one ADDED

`spec-feature-index`'s requirement is shipped and states the two states already — what was missing was the split that
makes `stale` mean stale — so it is **MODIFIED**, with every shipped scenario repeated verbatim and the three that
speak of the pending state rewritten to say which of the two figures reports them.

The archive census has **no shipped requirement**: `openspec/specs/agent-issue-loop/spec.md:4504` totals
bound-ended records only, and the table at `:2511` is per-run. So it is **ADDED**, and its body states what the line
totals, that undecidable and round-ended are figures of their own, and that the analysis pass reads the same
derivation.

## Alternatives the evidence eliminated

- **Keep `(.stopReason // "unclassified")` and document the dash** — the figure stays a class, and a reader comparing
  two runs a year apart cannot tell a run that ended on a bound from one that recorded nothing.
- **Sum undecidable into decided** — calls a measurement nobody made an answer, and moves the total without moving any
  record.
- **Count the whole 511-record archive as the denominator** — drowned the figure: 179 of the 511 can carry a class.
- **A second predicate in `analysis.sh`** — its `bound 27` and the report's `bound 51` then differ for a reason a
  reader cannot see; the correction was to source one derivation, not to reconcile two.
- **Make `pending` a gate error** — an open change classifying its delta ahead of the archive is the workflow, not a
  defect: `guidelines/FeatureList.md:154` reports stale entries without failing, and pending is the earlier state of
  the same line.
- **Move the `stale` line to `feature-list-generation`** — the shipped text it edits lives in `spec-feature-index`, and
  the one that mentions `stale` in passing stays true.

## Blast radius

- `tools/loop/lib.sh`: no change — `stop_class` already existed and is now the single reader.
- `tools/loop/report.sh`: one line, and one derivation block that is duplicated nowhere.
- `.pi/skills/analyse-loop-run/bin/analysis.sh`: one source line and one variable, read by two blocks.
- `tools/gen-feature-list.sh`: one split, two figures, two stderr notices, no new gate error.
- `guidelines/FeatureList.md` and `guidelines/Backlog.recipes.md`: the measured figures, already written by the shipped
  commit.
- Tests: `report#theArchivesClassesAreDerivedNotInvented` (`tools/loop/tests/loop-cases.sh:378`, dispatch `:1226`, body
  `:13541`–`13569`) and four self-test lines (`tools/tests/gen-feature-list-selftest.sh:328`–`331`) beside the shipped
  ones at `:298`–`301` and `:324`.

## Rollback

`git revert` of the three commits: the report prints a census that counts the absence as a class, the pass prints its
own predicate again, and `gen-feature-list.sh` prints one `stale` figure. Nothing reads either output as an input, so a
tree that does revert reads an archive or a classification written under the new shape exactly as before.

## The measurement, and its limits

The census is a reading of `.pi/issue-loop/runs/`, which is **gitignored and machine-local**: it cannot be re-derived
from a clone, and this change directory does not carry it — the figures above are quoted from
`guidelines/Backlog.recipes.md:1628`–`1643` and from `76588853`'s own message, which is the form the shipped change
recorded them in.

One falsification record exists for the pair, `922.1` (`tools/loop/tests/loop-mutations.sh:1170`): it restores
`(.stopReason // "unclassified")` into `report.sh` and names `report#theArchivesClassesAreDerivedNotInvented` as the case
that must fail on it. `tools/tests/` holds no mutation set for `gen-feature-list.sh`, so #926's split is held by its four
self-test lines and by no mutation.

The gate of the pair found the one thing worth knowing about how the evidence was made: `1eddc98c` inserted its new case
row *over* the row of `state#aCutRecordsTheFilesItsPatchCarries`, so the #922 case kept its dispatch arm and its body
and lost the only entry that could select it. `cd45d567` restored the row, measured after the insert (459 bodies, 455
selection rows, 455 dispatch arms, equal before and after). That is the only gate-shaped evidence in the pair, and it is
a repair of the evidence rather than a verdict on the change.

## Open decisions

none