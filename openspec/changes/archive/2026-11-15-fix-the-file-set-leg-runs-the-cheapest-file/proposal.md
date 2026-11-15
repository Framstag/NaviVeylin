# Proposal — the file-set leg runs the cheapest file's records

## Why

**Root cause.** The mutation harness's self-test spells the file its own file-set leg scopes by —
`scoped_file="tools/loop/state.sh"` (`tools/loop/tests/loop-mutations-selftest.sh:140`) — and that file owns 39 of the
401 records the set declares. The leg runs each of them twice, mutated and restored (`:152`), under `timeout 900`, so
the bound is spent before the selection finishes and the leg answers `FAIL a file set runs the records its file owns:
expected [0] got [124]` beside `FAIL exactly the owned records ran` — on every delta, including one that touches
nothing the leg reads.

**Evidence, re-measured on the base commit `8f84a8b8` (2026-11-15T21:00:24Z).** `evidence/per-file-counts.log`:

- `bash tools/loop/tests/loop-mutations.sh --files tools/loop/state.sh --list` ends
  `loop-mutations: selected 39 of 401 records (files: tools/loop/state.sh)` — the selection the leg runs;
- the declared set counted per file gives `2 tools/loop/phase.sh` as the fewest records any file that owns at least two
  carries, `3 ../look.sh` and `3 tools/loop/labels.sh` beside it.

The item filed it from two runs of `bash tools/loop/tests/loop-mutations-selftest.sh` on 2026-11-15 at `main`
(`4227aab7`), 26 min each, the leg killed at exit 124 having printed 33 `RED` lines of 37 and the other 34 checks green
in both. The count moved with the set (37 then, 39 now) and the shape did not: the constant is spent whatever the
delta is.

**Consequence.** `tools/loop/loop.conf:856` owes this step for any path under `tools/loop/*`
(`tools/loop/tests/loop-mutations-selftest.sh` is its own entry there), so a step of the gate is red on the base and an
item is charged for it — and the leg spends the self-test's own bound proving a selection that a two-record file proves
as well. None of the leg's four assertions reads the size of the set it could have picked; each counts against
`$owned_count`, derived from the declaration (`:141-143`), so a cheaper file buys the same assertions.

**The red test.** `mutation#theFileSetLegScopesByTheCheapestFile` (`tools/loop/tests/loop-cases.sh`), red on the base
with the self-test file as the base writes it: the file the leg scopes by owns 39 records where the file the
declaration gives fewest records to (at least two of them) is `tools/loop/phase.sh`. The run is
`evidence/red-on-HEAD.log`, its JUnit carrier `evidence/red-on-HEAD.xml` (`tests=1 failures=1`).

**The requirement the item corrects.** `openspec/specs/agent-issue-loop/spec.md:1762`, "A mutation run is scoped by
the files the change touches": its four scenarios name this self-test's leg, and no requirement said which file the leg
scopes by — the delta adds one requirement and its scenario, and moves no existing text.

## What changes

1. `tools/loop/tests/loop-mutations-selftest.sh`: the file-set leg scopes by `tools/loop/phase.sh` — the file the
   declaration gives fewest records to among the files owning at least two — and its comment states the rule the pick
   follows and the case that holds it to the declaration. Nothing else in the file moves: the leg's four assertions,
   the other legs and every `timeout` are unchanged.
2. `tools/loop/tests/loop-cases.sh`: the case `mutation#theFileSetLegScopesByTheCheapestFile` reads the pick out of the
   self-test and the per-file counts out of the declaration, and asserts they are the same file.
3. `tools/loop/tests/loop-mutations.sh`: records `934.1` (the leg pointed back at `tools/loop/state.sh`, which owns 39
   of the declared records) and `934.2` (a pick the declaration gives one record to, which the case names rather than
   reads as a set) — the declared set goes from 401 records to 403.
4. The delta spec under `specs/agent-issue-loop/spec.md`, and no guideline edit: `guidelines/Backlog.recipes.md`
   section 7c
   ("The gate and its suite") owns what a suite leg costs and carries no sentence about this harness's own leg
   (`sed -n '873,1108p' guidelines/Backlog.recipes.md | grep -n 'mutation'` — four matches, lines 113, 203, 219 and 220
   of the section, none about the self-test's scoped file), so the rule's prose is the file's own comment and this
   change's `design.md`.

surface: 1 case added, 0 cases rewritten, 0 fixtures realigned, 2 mutation records added — `tools/look.sh 'scoped_file'`
(8 matches in `tools/loop/tests/loop-mutations-selftest.sh`, 1 in the case driver) and `bash
 tools/loop/tests/loop-mutations.sh --files tools/loop/state.sh --list`, which is what the leg's pick cost before the
change and what the case now holds to the declaration.

## What does not change

- The leg's own assertions, its `timeout 900` and the other legs of the self-test.
- The harness (`tools/loop/tests/loop-mutations.sh`): its selection, its listing and its refusal are untouched; the
  change only adds a record to its declared set.
- `tools/loop/loop.conf`: the step is the same step, only cheaper.
