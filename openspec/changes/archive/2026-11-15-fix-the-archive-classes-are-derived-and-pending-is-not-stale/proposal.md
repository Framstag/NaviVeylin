# The archive's classes are derived on read, and the two stale states are reported apart

## Why

Two figures the loop's own tooling printed were **a dash read as a measurement**, and both were shipped by one commit
(`76588853`), so this change records them together.

**#922 — the archive's tally.** `tools/loop/report.sh` counted an archived record's class from its `stopReason` field
alone. The archive predates that field (`done --stop-reason` arrived with #865), so 119 of its 140 blocked records and
15 of its 39 cut ones name no mechanism: the absence was counted as a class, and the analysis pass printed it as
`blocked by stop class: … unclassified 119` — the figure read from `(.stopReason // "unclassified")`
(`.pi/skills/analyse-loop-run/bin/analysis.sh`, both halves replaced by `stop_class` in `76588853`). The shipped repair
derives the class instead — `tools/loop/lib.sh:346` `stop_class`, inside `bound_class_jq` (`:326`) — and prints what
the derivation cannot decide as a figure of its own (`analysis.sh:276`, `:286`).

**#926 — the feature index's entry states.** `tools/gen-feature-list.sh` reported every classification entry whose
capability is not shipped as `stale`, which conflated two states: an id an open change's delta introduces, whose line
is due *before* the archive and is therefore unfinished work, and an id the tree no longer holds, which is the entry
`guidelines/FeatureList.md` §4 rules a re-shortlist rather than a failure. `stale: 0` was unreachable while an open
change owed a line. The shipped repair splits them: `pending` (`:205`) and `stale` (`:206`), both report-only.

**Root cause, in one sentence.** Each tool read a field's absence as the value of the field, so a record with nothing
recorded was totalled into a class and an entry for work not yet shipped was reported as an entry for something gone.

**The owning capability of #922 is `agent-issue-loop`.** Read, not assumed: the report, the class vocabulary and the
analysis pass are stated there — "A block records the mechanism that stopped it" (`openspec/specs/agent-issue-loop/spec.md:2511`,
the closed vocabulary `bound | child-blocked | refused | unmergeable | …` the line totals) and "The rate of items the
run's own bound ended is printed beside the archive's" (`:4504`, the one archive-wide figure the shipped spec states).
`openspec/specs/build-test-gate/spec.md` names no loop report, no archived state and no stop class.

**ADDED, not MODIFIED, and why.** No shipped requirement states an archive-wide census of classes: `:4504` totals
bound-ended items only and its body says so ("A run's report SHALL print how many of the items it attempted the run's
own bound ended"), and `:2511`'s one report scenario, "The report tables the mechanism beside the spend" (`:2600`), is
the per-item table of a run, not the archive. The archive census, the undecidable figure beside it and the analysis
pass reading the same derivation are new rules, so the delta adds a requirement rather than rewriting one.

**The owning capability of #926 is `spec-feature-index`, not `feature-list-generation`** — the dispatch's label, and
the reading contradicts it. The entry states are stated in `openspec/specs/spec-feature-index/spec.md:54`, "An
unclassified spec id stops the run": its second paragraph is the one that says what a classification entry for an
open change's capability is reported as, and the scenario that reports it as `stale` is
`gate: a classified delta passes before it ships` (`:87`–`92`). `feature-list-generation` mentions the state once, in
a comparison inside `openspec/specs/feature-list-generation/spec.md:156`–`160` ("the same way a stale classification
entry is reported without failing"); that sentence stays true, so its requirement is not modified.

**#926 is folded into this change, as a second capability.** Decision and why: both defects are one shape (an absence
read as a measurement), both were filed against the same tooling, both shipped in one commit (`76588853`), and the one
record a reviewer reads should be the one that says so. A change may carry more than one capability delta
(`openspec/changes/<change>/specs/<capability>/spec.md`), so folding costs nothing structurally: the change carries
`specs/agent-issue-loop/spec.md` (ADDED) and `specs/spec-feature-index/spec.md` (MODIFIED, two paragraphs changed, one
scenario's answer changed from `stale` to `pending`, two scenarios added). Splitting it would have produced two change
directories whose gate verdicts, evidence and archive dates are the same commit's.

**The figures, measured over the archive** (`report.sh` over `.pi/issue-loop/runs/`, 2026-11-15 — the reading is in
`evidence/archive-census.md`): **158** archived states, **511** item records, **179** of them `blocked` or `cut`,
**66** decided — `bound 51 · child-blocked 1 · refused 9 · unmergeable 5` — **107** undecidable, and **6** ended by a
review round the item did not carry. The analysis pass's `unclassified 119` was the blocked half of that absence; the
difference between the report's 107 and the pass's 98 is the cut records that name no class either
(`76588853`, corrected by `1eddc98c`).

**The classification figures, measured on this tree** (`bash tools/gen-feature-list.sh --check-classification`,
2026-11-15, `evidence/classification-check.txt`): `specs read: 191`, `classified: 197`, `unclassified: 0`,
`delta spec ids: 2`, `stale: 6`, `pending (classified ahead of its archive): 0`. The two delta ids are this change's
own two capabilities (`agent-issue-loop`, `spec-feature-index`), both classified, which is why they are neither
unclassified nor pending. Before #926's split the same set printed `stale: 7` with five of the seven being open
changes' deltas (`tools/gen-feature-list.sh:203`) — the figure its own comment records as measured on 2026-11-15.

**The cases.** `report#theArchivesClassesAreDerivedNotInvented` (`tools/loop/tests/loop-cases.sh:13541`, registered
`:378`, dispatched `:1226`) archives a run whose four `blocked`/`cut` records are a refused one, one whose note quotes
the turn bound, one that recorded nothing, and one a review bound ended, and asserts the printed line:
`bound 1`, `refused 1`, `of 4 blocked or cut record(s): 2 decided, 1 undecidable, 1 ended by a review round, not by
the item`, no `unclassified` in the line, no closed record counted, and `archive classes: -` for a project with no
archive. Mutation **922.1** (`tools/loop/tests/loop-mutations.sh:1170`) puts the old reader back
(`{cls: (.stopReason // "unclassified"),`) and the case must fail on it.
`tools/tests/gen-feature-list-selftest.sh:326`–`331` holds #926: `gate: a delta-only classification is reported as
pending, not as stale` (`pending …: 1`), `gate: a delta-only classification is not a stale entry` (`stale: 0`),
`gate: the pending entry is named as one` (the stderr sentence `classified ahead of its archive`) and `gate: a pending
entry is not reported as stale` (no `stale classification entry` for it).

**Surface**, counted on this tree (`grep -rn` over `tools/` and `guidelines/`, 2026-11-15): `stop_class` 8 matches in
5 files, `pending (classified` 2 in 2 files (`tools/gen-feature-list.sh`, `tools/tests/gen-feature-list-selftest.sh`),
`unclassified` 56 in 18 files — the word the analysis pass printed and the spec's own requirement title. The change
touches no code.

**What is not established.** #926 carries **no mutation record**: `tools/tests/` holds mutation sets for
`check-change-readiness`, `check-doc-routes`, `check-glossary`, `check-rule-routes` and the loop engine, and none for
`gen-feature-list.sh`, so the split is held by the four self-test lines above and by no falsification record. The
shipped commit records no gate verdict for either half in its message: `76588853` states the figures and the repair,
and the follow-up `cd45d567` records that `1eddc98c` had put its new case row *over* the row of
`state#aCutRecordsTheFilesItsPatchCarries`, leaving the #922 case registered everywhere and selectable nowhere — the
row was restored; that is the only gate-shaped evidence in the pair.

surface: 2 delta files — 1 requirement and 4 scenarios added (`agent-issue-loop`), 1 shipped requirement MODIFIED with
its 9 scenarios repeated in order, 1 answer changed and 2 scenarios added (`spec-feature-index`), 0 source files
changed by this change directory — `grep -c '^#### Scenario:'
openspec/changes/fix-the-archive-classes-are-derived-and-pending-is-not-stale/specs/*/spec.md` → 4 and 11; the
behaviour they state is read back with `grep -rn 'archive classes:' tools/ | wc -l` → 4 in 2 files and
`grep -rn 'pending (classified' tools/ | wc -l` → 2 in 2 files.