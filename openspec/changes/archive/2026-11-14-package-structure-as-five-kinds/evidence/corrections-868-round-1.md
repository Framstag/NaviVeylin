# The corrections review round 1 asked for

Round 1 (`review-868-round-1-passed.md`, `LOOP-BLOCKING: 0`, `VERDICT: PASS`) raised seven `NOTE:` findings and no
blocking one. Every note about a record this slice writes is corrected here, in the same directory; the two notes
that are not this slice's belong to the open change's own later tasks and are carried, not fixed.

## 1 — `architecture/baseline.tsv`'s figures had no output in the record — **corrected**

`evidence/baseline-figures.log` now carries the three commands and their output, run at `2b795305` on 2026-11-15:
the 59 `*.kt` files of package `com.naviveylin.core`, `tools/look.sh '^import (android|androidx)\.' --path
core/src/main/java` (26 matches in 11 files), and `tools/look.sh '^import com\.naviveylin\.' --path
core/src/main/java/com/naviveylin/core` (9 matches in 6 files, the three in the package itself being
`AutoEntryPoint.kt`, `AutoSearchProvider.kt` and `ResultMarkings.kt`). The header's parenthetical counts reproduce;
the file is named from the attribution.

## 2 — "8 of the class's 9 cases passed" had no command — **corrected**

`gate-attribution-868.md` now states what the gate log does carry (`gate-output-868.log:305`: "2294 tests completed,
1 failed") and grounds the class's case count in `evidence/flake-case-green-alone.xml` (`tests="9"`), which is the
class's own run.

## 3 — the gate record does not name the tree it measured — **corrected, with the limit stated**

The line is printed at `tools/loop/gate.sh:262`, before the gate creates its log, and the invocation's stdout was
read through `tail -40`, so only a recomputation exists. `gate-attribution-868.md`'s "## The tree this gate measured"
carries it (`head=2b795305c7b9f2805cfe913fb9d300e82434efc3 diff=07272d69e375ee25`), names the command
(`bash .pi/review-868/identity.sh <base>`, which calls the `loop_change_identity` both `gate.sh` and `run.sh` use),
and states the two derivations are not compared: the gate's `diff=` is a content digest, the round's identity names
the reviewed patch's `sha256`. The equality the reviewer asked for is on `head=` plus the argument that no
`architecture/**` file was written after the gate — commit `77a51af1` is exactly the tree the gate measured.

## 4 — the 3.5 record did not name its mutation, and was not the prescribed one — **corrected, and the prescribed one
measured**

`gate-attribution-868.md` now has "## The two falsifications, and the mutations they performed", naming each mutation
by file and condition. Two facts it records correct the note's own premise: the 3.5 red XML's message reads `rules=4`,
not `rules=0` (`LayerRules.ALL.size` is four and the mutation never touched it), and the named cases of 3.1 and 3.5
assert over source lists and baselines the tests build, so **no** tree edit can redden them — which is why each
falsification carries the case level. A tree-level run was then measured beside 3.5's, not 3.5's prescribed file move
(which no case can see, for the reason above): one stale entry in `architecture/baseline.tsv` reds
`greenRunPrintsFigures` naming it, `evidence/revert-check-stale-entry-tree-red.xml`
(`timestamp="2026-11-15T06:48:15.327Z"`), restored with `git checkout architecture/baseline.tsv` and re-run green in
16 s.

## 5 — `greenRunPrintsFigures` hard-codes 27 / 338 / 14 — **carried, not fixed**

The case is the measurement the change's task 1.8 asks for ("the green run prints its figures — declaration rows,
classified files, outstanding exemptions, rules evaluated"), and a figure the check must print is a figure a case has
to hold it to; the brittleness the note names is real and belongs to that task's own contract, which lives in the open
change (`openspec/changes/package-structure-as-five-kinds/tasks.md` task 1.8), not in this slice's delivery. Nothing
here depends on the numbers being fixed at these values: the check reports them either way.

## 6 — the archived `tasks.md` pointed at a change that does not exist — **corrected**

`openspec/changes/archive/2026-11-14-package-structure-as-five-kinds/tasks.md` named
`package-structure-rules-and-suite` as the change carrying the rest of the plan; no such change has ever existed in
the tree. It now names the open working change `openspec/changes/package-structure-as-five-kinds/` and states what
landed behind the archive: this slice (1.5, the baseline half of 1.7, 1.8, 1.10) with its falsifications 3.1 and 3.5.

## 7 — `architecture/**` derives no reading set — **carried, not fixed**

`tools/doc-routes.json` has no glob matching `architecture/**`. The route is the open change's own task 4.4 ("an
`architecture/**` route added only after the module and the declaration are tracked"), and the guideline that owns
the structure is task 4.1. Adding either here would land a rule for a document the same change has not written yet,
so it is not this slice's to write.

## What round 2 asked for, and what it cost

Round 2 (`review-868-round-2-passed.md`) graded these seven dispositions and passed them all, raising five notes of its
own — all of them one-clause corrections to this directory's own sentences, none of them a change to the code or to a
claim the change rests on. They are applied: the tree-identity section now names the archived `tasks.md` as the one
write outside `evidence/**` that moves the identity's digest at HEAD (`f9f6ea9086d70366`), names the red step's path
list accurately, calls the tree-level run what it is (a stale entry, not task 3.5's prescribed file move), and marks
`.pi/review-868/*` as run-local artifacts the merged tree does not carry. Round 2's fifth note is a reminder to the
owner of task 1.8 that the case's hard-coded figures are the task's to settle.
