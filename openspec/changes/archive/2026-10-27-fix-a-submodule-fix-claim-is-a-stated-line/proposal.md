# Proposal — fix-a-submodule-fix-claim-is-a-stated-line

## Why

**Root cause.** The pre-screen's submodule verdict reads the item's *vocabulary* where it means to read the item's
*claim*: `tools/loop/prescreen.sh:145-154` (`names_the_submodule`) returns 0 when the title or body contains the
profile's `LOOP_SUBMODULE_PATH` as a substring (`:150`) or its basename as a word (`:153`), and the verdict at
`:391-395` then answers `needs-decision` with `the fix has to be committed inside …`. A body that quotes the path as
*evidence* — the file a dirty tree was seen on, a quoted stop reason — is therefore indistinguishable from a body
that states the fix lands there.

**The cost, measured.** The adoption run of 2026-10-26 (`tools/loop/run.sh --items 1 --minutes 30`) excluded
`#310` and `#309` as submodule patches (`.pi/issue-loop/adopt-run.log:15-16`, `.pi/issue-loop/prescreen.log:9-10`),
and neither item's fix lands in the submodule: #309's is `tools/loop/worktree.sh`'s reused-worktree path, #310's is
`tools/loop/run.sh`'s reset path. The pass wrote `agent/needs-decision` and `status/on-hold` on both, so a run
leaves them out of the queue until a person repairs them; #305 sits parked the same way, for the same reason. Two
working-tree items of the loop's own tooling were taken out of the queue by their own evidence (issue #314).

**The recorded decision settles the fork.** `Option 1` of the item, recorded 2026-10-26T06:40:41Z: only a
purpose-written marker line decides it — the pre-screen refuses an item when its title or body carries a line of its
own beginning `The fix lands in the submodule`, and stops searching for the path or the basename entirely. The match
is line-anchored, so a body quoting the phrase inline, in backticks or as an example does not match. The line is
documented beside the body fields in `.pi/skills/create-issue`. The accepted cost: a genuine submodule item that
omits the line is queued and spends one worker phase before the worker's gate 3 reaches the same question (the #254
cost, measured on #30, #60, #122).

**The red cases on the base (`98673c50`), 2026-10-27T13:56Z** (JUnit-shaped tallies in `evidence/`):

| case | XML | what it reads |
|---|---|---|
| `prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim` | `tests=1 failures=1` | `expected 'eligible', got 'needs-decision'` — a body quoting the path as evidence is parked |
| `prescreen#aFixInTheSubmoduleIsAPersonsPatch` | `tests=1 failures=1` | `expected 'needs-decision', got 'eligible'` — the old rule knew nothing of the stated marker |

surface: 1 case added, 1 case flipped onto the new signal, 2 fixture items realigned and 2 added, 1 mutation record
added — `tools/loop-selftest.sh --list` reads `cases=251` on the base (`LOOP-MEASURE-SUITE cases=251
cmd=tools/loop-selftest.sh --list`) and 252 with this change, and `grep -ln 'scenarios/submodule' tools/*.sh` names
the one case that reads the fixture.

**Owning requirement.** `agent-issue-loop` — the new requirement "The pre-screen refuses only a fix stated to land in
the submodule".

**Owning guideline.** `guidelines/Backlog.md`, section 7 "The loop" (the pre-screen paragraph, "What the gate and
the pre-screen are worth"), and section 6 "How an item becomes work" for the discovery step the pre-screen is.

## What changes

1. **`tools/loop/prescreen.sh`** — `names_the_submodule` becomes
   `states_the_fix_lands_in_the_submodule`: the path and the basename are no longer searched at all; the answer is
   whether the title or body carries a line beginning `The fix lands in the submodule`. The profile's
   `LOOP_SUBMODULE_PATH` is still read — a project with no submodule has no such refusal, and the reason the verdict
   writes still names the path a person's patch lands in.
2. **`tools/loop/fixtures/scenarios/submodule/issues.json`** — items 71 and 72 state the marker on a line of their
   own instead of naming the path; items 74 and 75 are new: 74 quotes `app/src/main/cpp/libosmscout` as the file the
   dirt was seen on, 75 quotes the marker inline and in backticks.
3. **`tools/loop-cases.sh`** — `prescreen#aFixInTheSubmoduleIsAPersonsPatch` flips onto the marker and the queue
   assertion follows the three app-side items; the new `prescreen#aQuotedSubmodulePathIsEvidenceNotAClaim` owns the
   quoting half of the rule.
4. **`tools/loop-mutations.sh`** — record `69.1` relaxes the line anchor to a substring match, which turns the
   quoting case red.
5. **`.pi/skills/create-issue/SKILL.md`** — the marker is documented beside the body fields, so a genuine submodule
   item is written with it.

## Impact

- Affected files: `tools/loop/prescreen.sh`, `tools/loop/fixtures/scenarios/submodule/issues.json`,
  `tools/loop-cases.sh`, `tools/loop-mutations.sh`, `.pi/skills/create-issue/SKILL.md`,
  `openspec/specs/agent-issue-loop/spec.md`.
- No compiled file, no Android module, no log line, no coordinate in any diagnostic.
- The verdict's shape is unchanged (`needs-decision`, the same reason line, the same label write): only the signal
  that reaches it moves.
- Rollback: revert this change. Every item the marker refuses is still queued and reaches the worker's gate 3, which
  asks the same question — the change costs one worker phase per genuine submodule item, and parks nothing a run
  cannot deliver.
