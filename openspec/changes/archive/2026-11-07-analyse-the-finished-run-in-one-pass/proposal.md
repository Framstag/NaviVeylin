# A finished run is analysed in one read-only pass that ends in items, and the entry document names the skill that does it

## Why

The loop has no pass that reads a run that already ended. Its evidence is on disk and nobody reads it as a whole:
`tools/loop/report.sh` prints a run's own numbers, `tools/loop/state.sh show` prints progress and exclusions, and
`tools/loop-spend.py` explains the spend — but the three are separate calls with a seam in the middle, and the
question a caller actually asks after a night ("did this run earn its cost, and what would I change?") is answered
by whoever reads the logs by hand. The pass that did it for the run of `2026-11-07T06:33:29Z` found four defects in
one sitting (three filed as #813–#816, one commented onto #755), which is the yield a written recipe is for; the
same pass run from notes twice would find three different things.

Two properties make it a pass rather than a habit, and neither is in the tree today:

- it reads the state that has **ended**, never the one in place while a run is live, because a live run's state is
  half-written and its item streams are still growing (the pass's own §0 states the `live=yes`/`live=no` test);
- it **writes nothing**: not a gate, not a build, not the loop, not a guideline. A defect it finds becomes an item,
  because the analysis and the change must not be the same write.

## What changes

Additive. One new skill, one routing row, one entry-document pointer, and one case that holds the pointer and the
skill's two boundary sentences.

| file | what it gains |
|---|---|
| `.pi/skills/analyse-loop-run/SKILL.md` | the pass: pick the finished run (§0), the numbers the state already holds (§1), where the spend went (§2), what was good and bad (§3), the suggestions table (§4), the five output blocks (§5), and the constraints a pass must not re-learn (§6) |
| `guidelines/Build.md` §1 | one row in the skill table, and the count in the paragraph above it corrected from *five and three* to *five and four* |
| `AGENTS.md`, "Agent iteration loop" | the skill named by its path beside `create-issue` and `device-check`, so a reader is routed to it rather than to a listing |
| `tools/check-doc-routes-selftest.sh` | `routed-read#theEntryDocumentNamesTheLoopSkillsByPath` reads three names instead of two, and holds the analysis skill's own boundary sentences (`Read-only`, `agent/eligible`) so a pointer to a weakened skill fails |

Affected guidelines: `guidelines/Build.md` §1 only. The loop's own rules are not touched — the skill repeats
`guidelines/BuildGate.md` §2a's iteration protocol rather than restating it, and cites `tools/loop/report.sh`,
`tools/loop/state.sh`, `tools/loop-spend.py` and the specs `agent-issue-loop` and `documentation-ownership` as the
owners of everything it applies.

Breaking: no. Rollback: delete the skill directory and revert the three routing edits; nothing else reads them.

## The red run, the red contract, and the green one

```
$ bash tools/check-doc-routes-selftest.sh --case routed-read#theEntryDocumentNamesTheLoopSkillsByPath
FAIL … the entry document names no path for .pi/skills/analyse-loop-run            → evidence/red-run-routing.log
FAIL … the analysis skill states no Read-only                                      → evidence/red-run-contract.log
PASS … the entry document names the three skills … all three                       → evidence/green-run.log
```

The first is HEAD's `AGENTS.md` against the case's three names; the second is the skill with its read-only
sentence weakened by one edit, restored afterwards — the case fails for the contract, not only for the pointer.

## What was checked, and what was not

Verified: the three runs above, and `tools/check-doc-routes.sh` (329 section references, 7 skill references),
`tools/check-todo-references.sh`, `tools/check-openspec-hygiene.sh` (`evidence/checks.log`).

Not run, and owed by nobody: the gate. This change touches no compiled file and no shell logic outside one case
in a self-test — the suite its case belongs to is what was run, and `tools/loop-selftest.sh` and the Gradle suites
are untouched by it.

## Not claimed

That the pass finds what a careful reader would. It fixes the order of the questions and the discipline of the
answer — the state read, the numbers quoted rather than recomputed, the table a caller can act on — not the
judgement. Its own first application filed four items; whether the next pass finds as many is the measurement, and
`tools/loop/report.sh --state` on the next finished run is where it is taken.
