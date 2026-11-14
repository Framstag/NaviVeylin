# A case that flaked once is off the blocked path, not the delta's

## Why

Issue **#861**. `tools/loop/run.sh:1264` attributes a red gate from one base run (#853): a case that fails there is
recorded `delta`, and the item takes the blocked path. One base run answers `delta` for a case the delta broke **and**
for a case that flaked once, because nothing ever runs the failing case a second time in the tree the item's own work
already warmed — the state "passes on both sides, at another time" has no field.

The measured instance is the run of `2026-11-11T11:37:08Z`, item #849: its gate tally named
`worktree#keepsSourcesNotBuildOutput` as the single failure (`tests=1016 failures=1 errors=0`, not retained — that
run's build tree was overwritten, as #853 records), the case passes at HEAD
`7e441575` (two runs, 7 490 ms and 7 533 ms) and at #849's own gate head `9c5d6cbb` (7 514 ms, in a detached tree), and
the run spent 2 714.012 s and 17 882 005 tokens to block on a red that reproduced at neither head.
`tools/gate-attribute.sh:371` states the same hole in its own words: `flake: not recorded — this tree keeps no flake
list`.

**Root cause, in one sentence.** `attribute_red_gate` decides a red tally from a single rerun — the item's own red test
by name, any other case in one detached base tree — so a case that flaked once and a case the delta broke record the
same answer and both block.

**The red case.** `run#aFlakedCaseIsOffTheBlockedPath` (`tools/loop/tests/loop-cases.sh:10983`) drives two runs whose
gate tally is red and whose gate log names a case the item's red test does not name: the case passes on the run's
re-run in the item's own worktree (the tree carrying the change's own file, which the detached base tree does not) and
must record `metrics.gateAttribution: flaked` with the item off the blocked path and the report counting the answer;
the sibling run's case fails both times and must record `delta` and block. On HEAD the first half fails with
`expected 'flaked', got 'delta'` (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, tests=1 failures=1) — the
field's vocabulary at the run's base had no `flaked` member at all (`tools/loop/state.sh:712` named
`delta|base|unattributable`, and `tools/look.sh 'flaked' --path tools/loop` matched nothing there).

**The owning capability** is `agent-issue-loop`; the requirement it extends is `openspec/specs/agent-issue-loop/spec.md`
"An item's record keeps what the run measured to decide it" (its `metrics.gateAttribution` clause, `:4165`). The owning
guideline section is `guidelines/BuildEvidence.md:106` §4d "Attributing a red run", whose rule already states the closed
vocabulary and now states the third answer.

**The decision this continues.** The owner's recorded decision of `2026-11-14T05:59:03Z` chose option 1 — land #853
first, then run #861 against a base carrying `metrics.gateAttribution` — and left the item's own sub-question (one probe
or a bound, and whether a flake is ever listed) to this change. The design settles both: one probe, no flake list.

**Surface**, counted before the plan was fixed (`git show 869a92fc:<path> | grep -c flaked`, one line per file):
`flaked` is a word this engine does not hold — 0 matches in `tools/loop/run.sh`, `tools/loop/state.sh`,
`tools/loop/tests/loop-cases.sh`, `tools/loop/tests/loop-mutations.sh` and `guidelines/BuildEvidence.md` at the run's
base — so the surface is what the new answer has to be added to rather than rewritten, and it is small: 1 case added,
2 cases rewritten (#853's, whose "no base tree is paid" assertion a second look changes, and the state case whose
refusal message named the old vocabulary), 3 mutation records added, 0 fixtures. Diff against the base:
`5 files changed, 173 insertions(+), 41 deletions(-)` (`git diff --stat`).

Files this change touches: `tools/loop/run.sh`, `tools/loop/state.sh`, `tools/loop/tests/loop-cases.sh`,
`tools/loop/tests/loop-mutations.sh`, `guidelines/BuildEvidence.md`.
