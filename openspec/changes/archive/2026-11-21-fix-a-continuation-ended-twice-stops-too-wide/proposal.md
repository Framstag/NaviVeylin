# A continuation the same ceiling ends stops the item too wide

## Why

`tools/loop/run.sh` continues a context-cut worker exactly once (`worker-continuation`, or `worker-checkpoint` for
the split below the stop). When that continuation is ended at the same ceiling, the run's guards decide the item —
`blocked`, `refused` or `child-blocked` — and nothing records that the item is too large to finish in one child's
context, so the next run picks it up at full size.

**The evidence.** Run of 2026-11-17T10:58Z → 16:25Z (`.pi/issue-loop/runs/`): five items were continued from a killed
run's kept patch (#883, #917, #950, #894, #911), of which the four that stayed blocked paid 78.7 Mtok — 65.4 % of
the run's 120.3 Mtok — for none of them: 0 closed, and each of those four reached a reading of 142 576–143 943
against the recorded checkpoint of 143 124. #894's own continuation report states the shape in its prose — "sections 2.2-2.6,
3.x and 4.x do not fit one child's context bound (this session reached ~123k tokens on 2.1 alone)" — and asks for the
phases to be split into items of their own. The run printed `cut 0` and `bound 0 of 14`, so the shape is invisible in
its report as well.

**Root cause, in one sentence:** the reading a second child the ceiling ended is a measurement of the *item's* size,
and the run records it as a bound it enforced on a phase — a `cut` or a `block` — so the vocabulary that says "this
does not fit one child" exists, is wired (`too-wide` → `agent/needs-shaping`, `tools/tooling.json`
`roles.shapingVerdicts`) and is never written here.

**The red test**, on the base `8e3861c4c0c8bff22a36ff291e30385b7497f420`:

```
LOOP-RED-TEST: run#aContinuationEndedOnTheSameCeilingStopsTooWide fails on HEAD — XML tests=1 failures=1 (2026-11-21T02:54:53Z)
```

The driver's own line is `expected 'skipped', got 'cut'`: the item is recorded `cut` with `bound` beside it, which is
the shape the next run re-enters (evidence: `evidence/red-on-HEAD-8e3861c4.xml`, `.log`).

**Owning capability and guideline section.** Capability `agent-issue-loop`, the requirement "The bound on a child is
priced in what it spends, not in how many turns it takes" (`openspec/specs/agent-issue-loop/spec.md`), and
`guidelines/Backlog.recipes.md`:1690 "Leftovers, the report and the queue" — the paragraph, which before
this change named the second context cut as the item's `cut`.

## What Changes

A continuation child the run ends at a context bound — the same test the first cut is read from,
`child_ended_by_context_bound` over the child's own `LOOP-TIMEOUT` line — is recorded as the shaping verdict
`too-wide` instead of being left to the guards: the item's outcome is `skipped`, its verdict is `too-wide`, the
shaping label and the hold are written by `labels.sh`, no decision label is written, and the reason names the ceiling
the second child reached and the phases that child listed (the last line of its own prose, which is all a child the
run ended leaves). No third child starts: the ceiling one continuation per item is unchanged, and its meaning
changes from "the item's `cut`" to "the item's shape".

Two other readings are unchanged and stay stated: a patch the applier does not take still leaves the item `cut` with
the non-apply recorded, and a continuation started past the run's deadline is still not started at all.

**Reading set** (derived by `tools/check-rule-routes.sh --diff` for the paths touched): `guidelines/Backlog.md` §1,
`guidelines/Backlog.md` §3, `guidelines/Backlog.md` §4, `guidelines/Backlog.md` §5,
`guidelines/Backlog.md` §14, `guidelines/BacklogTooling.md` §9, `guidelines/BuildGate.md` §2a — named beside this
change's own `guidelines/Backlog.recipes.md`:1690 paragraph (the migration's § → issue table, which evidence-check
reads, archives no row 7; `guidelines/Backlog.md` §7 is named here for the map alone).

**Surfaces.** `tools/loop/run.sh` (the arm, and `continuation_listing` beside `child_ended_by_context_bound`),
`guidelines/Backlog.recipes.md:1690` (the paragraph realigned), `openspec/specs/agent-issue-loop/spec.md` (one
requirement modified, one scenario added, one clause of an existing scenario corrected),
`tools/loop/tests/loop-cases.sh` (1 case added, 1 rewritten), `tools/loop/tests/loop-mutations.sh` (1 record). No
prompt skeleton and no fixture changes: the continuation's own prompt already names the bound, and the case drives it
with the fixture the neighbouring cases use.

surface: 1 case added, 1 case rewritten, 1 mutation record added, 1 guideline paragraph realigned, 1 spec
requirement modified (1 scenario added, 1 clause corrected) — `tools/look.sh 'second context cut'` on the base
`8e3861c4c0c8bff22a36ff291e30385b7497f420` (3 files: `tools/loop/run.sh`, `guidelines/Backlog.recipes.md`, and the
archived design of the change that introduced the rule, which is a record and is not edited; both edited files dropped
the phrase) and `tools/look.sh 'childPastItsContextBoundIsCut'` (the case whose
tail asserts the `cut`).
