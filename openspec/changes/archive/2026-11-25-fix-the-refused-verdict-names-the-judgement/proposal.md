# The refused-class verdict names the judgement, not a class value

## Why

`tools/loop/prescreen.sh:408` refuses an item whose class the profile does not work, and the verdict it writes is
the fixed word `class-feature`, while the class that was actually refused appears only in the reason
(`:409`). The word is a value of this project's class namespace: an item refused for `class/improvement` is logged
as a refused *feature*, and the run's log is what a person reads to learn why their item was ignored. The same
literal is the engine's own key for the refusal label (`tools/loop/prescreen.sh:700`,
`class-feature) refusal_label="agent${separator}${refusal_feature}"`), so the engine that may name no project
value (`guidelines/BacklogTooling.md` §10, "Portability") carries one.

Nothing caught it. The two fixtures that exercise the branch set `refusedClasses = ["feature"]`
(`tools/loop/tests/loop-cases.sh:3043`, `:3087`) and assert the same word back
(`:3048`, `:3090`) — the one class for which `class-feature` happens to read true, so the case was green over a
verdict that names a class the refused item need not carry at all.

**Root cause, in one sentence:** the refused-class branch writes a class value into its verdict instead of the
judgement, and both fixtures refuse `feature`, the one value for which that literal is indistinguishable from a
verdict word.

**The red test:** `prescreen#theRefusedVerdictNamesNoClassValue` refuses `improvement` — issue 24 of the `six`
fixture — and is red on the base commit `ae2373cb` with the production code untouched
(`evidence/red-on-head.log`, 2026-11-25T04:40:23Z, exit 1):

```
  the verdict names the judgement, not a class value: expected 'class-refused', got 'class-feature'
FAIL prescreen#theRefusedVerdictNamesNoClassValue
loop-selftest: 2 of 5 cases failed — 1.93 s
```

The two existing assertions the rename moves are red on the base commit in the same run
(`prescreen#aRefusedClassStillStopsTheScreen`, `red-on-head.log`) and in the run on its own
(`prescreen#refusalLeavesATraceInTheTracker`, `evidence/red-on-head-trace.log`: `1 of 1 cases failed`). A shell
suite has no JUnit XML of its own (`.pi/skills/revert-check/SKILL.md`), so the tally is the suite's own line.

**The owning requirement:** `agent-issue-loop`, "The run acts only on authorised, eligible items", whose scenario
"A refusal the item cannot escape is written to the tracker" spelled the verdict
(`openspec/specs/agent-issue-loop/spec.md:247`); the rule the verdict broke is `guidelines/BacklogTooling.md` §10,
"Portability", "an engine names no project value".

**Surface, counted before the plan was fixed** (`bash tools/look.sh 'class-feature'`, base commit): 15 matches in 8
files, of which **2 engine lines** (`tools/loop/prescreen.sh:408`, `:700`), **3 fixture assertions**
(`tools/loop/tests/loop-cases.sh:3048`, `:3090`, plus the registry rows a new case needs) and **3 shipped spec
lines** (`openspec/specs/agent-issue-loop/spec.md:247`, `:1851`, `:1864`); the other 10 matches are archived
changes, which are records and are not rewritten: **1 case added, 1 case's fixture changed to a second class, 2
existing assertions updated, 0 fixtures realigned**.

## What changes

surface: 2 engine lines, 1 case added with 2 registry rows, 2 existing assertions updated, 3 spec lines — `bash
tools/look.sh 'class-feature'` on the base commit (15 matches in 8 files: 2 engine, 3 fixture-assertion, 3 shipped
spec, 10 archived).

- `tools/loop/prescreen.sh` — the branch's verdict becomes `class-refused`, with the reason left as the one place
  the refused class is named (`:408-411`), and the refusal-label mapping keys on the same word (`:700`).
- `tools/loop/tests/loop-cases.sh` — `prescreen#theRefusedVerdictNamesNoClassValue` refuses `improvement` instead of
  `feature` and asserts the verdict is `class-refused`, that the reason names `class/improvement`, and that a class
  the profile *does* work (`feature`, issue 25) is untouched; the two existing assertions follow the word.
- `openspec/specs/agent-issue-loop/spec.md` — the refusal-trace scenario and the two feature scenarios carry
  `class-refused`, the requirement says the verdict's word names the judgement and never a value of the profile's
  vocabulary, and the new scenario names the case.

## Alternatives eliminated

- **Make the verdict the refused class itself** (`class/improvement`). Refused: the verdict vocabulary would become
  unbounded — every reader that branches on it (`:700`'s label mapping, the run's report) would need a shape, and
  the engine would carry a namespace rather than a judgement. The reason already carries the class, once.
- **Tighten `tools/check-portability.sh`'s `name.awk` so a hyphenated identifier no longer passes as path-ish.**
  Refused for this item: the check's exemption is what keeps every legitimate hyphenated engine identifier
  (`author-not-allowlisted`, `missing-path`) out of the scan, so tightening it is a scan change with its own
  fixtures, and it would still leave the verdict naming a class the item need not carry. The leak is repaired where
  it is written; the scan's shape is filed separately if it is wanted.
- **Add the class to the profile's `refusedClasses` vocabulary instead of renaming.** That is not this defect: the
  refusal works, and what it writes is wrong.
