# The eligibility gate's `too-wide` row names a measure

Row 5 of the worker's eligibility gate read, on the base `8e3861c4`, exactly:

```
| 5 | it fits one capability delta and one change (bounded files) | `too-wide` |
```

(`tools/loop/prompts/worker.md:221`.) It is the one row of the seven that names no measure: rows 1, 2, 3, 4 and 6 name
what is verified (the claim, one root cause, one change, a falsifiable WHEN/THEN, a host test), and row 7 names the
shape the red case must have. Row 5 names "bounded files" and a parenthetical, so a child takes the count from
scratch every time, and taking it costs nothing — which is why it is usually not taken.

The run started 2026-11-17T10:58Z and ended 16:25Z priced the two halves of that asymmetry: its six refusals
(`too-wide`, `needs-diagnosis` three times, `needs-decision`, `stale`) cost 8.29 Mtok together at a mean of 140 s,
while the four items the same gate admitted onto one 4 379-line engine file cost 78.7 Mtok — 65.4 % of the run's
120.3 Mtok — and all four ended `blocked`, their own reports saying the change does not fit one child (`#894`:
`sections 2.2-2.6, 3.x and 4.x do not fit one child's context bound`; `#917`, `#950` and `#911` the same shape at
143 k context, twice each). One item closed in that run.

**Root cause, in one sentence:** the row that decides whether an item's change is wider than one child is the only
row of the gate with no count behind it, so `too-wide` is a judgement taken fresh by each child and the admission it
should have refused costs a whole worker spend (78.7 Mtok over four items) before anyone learns it.

**The red case, on the base `8e3861c4` —** `tools/loop/tests/loop-selftest.sh --case
'prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict'`, one run, 2026-11-21T03:54:29Z:

```
prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict|   row 5 decides the too-wide verdict by a count and not by a fresh judgement (issue #959): 'counted, not judged' not found in: # Worker — one item, phases A–D
FAIL prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.xml` is that run's tally (`tests=1 failures=1 errors=0`), transcribed into a `testsuite` from
the runner's own summary — the loop's shell runner writes no JUnit XML — and kept beside
`evidence/red-on-HEAD-8e3861c4.log`, whose own lines are the failure and the `1 of 1 cases failed` summary.
`evidence/green-on-change.log` is the same case over the applied change (`PASS`), and
`evidence/mutation-959.1.log` is the record that keeps the row from arriving without its bound
(`red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`).

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "The run acts only on authorised, eligible items"
(`:216`) — the requirement the gate's verdicts hang from. This change adds one requirement to it, "The `too-wide`
verdict is a counted surface, not a judgement", with the one scenario the case above answers.

**Owning guideline section:** `guidelines/Backlog.recipes.md`, section 7b, "What an item costs, and the bounds on it"
— the
paragraph that states what the gate and the pre-screen are worth, measured on the first run, and closes with that
run's skip-verdict distribution (`needs-device 8 · needs-decision 7 · in-flight 7 · refuted 4 · too-wide 2 · stale 2 ·
unspecifiable 1 · needs-diagnosis 1`). That section already prices a refusal against an admission and owes the
sentence that says which measure decides `too-wide`; it gains it here.

surface: 1 prompt row rewritten (`tools/loop/prompts/worker.md:221`), 1 guideline paragraph added
(`guidelines/Backlog.recipes.md`, section 7b), 1 case added with its `LOOP_SHELL_CASES` row and its dispatch arm, 1 mutation
record added (`959.1`) — counted with `./tools/look.sh 'fits one capability delta' --files` (20 matches in 13 files
with the change in place: the row, the new case, the new record, the guideline paragraph, and nine archived and open
`evidence/` logs of earlier changes) and `./tools/look.sh 'row 5' --files` (19 matches in 6 files with the change in
place: the case file, the mutation set and this item's own reports).

## What changes

- `tools/loop/prompts/worker.md` — row 5 becomes a count the child takes from the census Phase A already ran
  (`tools/look.sh '<the rule>'`, whose output reads `LOOK: <n> matches in <m> files`) compared with the turns the
  worker role is given (`LOOP_CHILD_TURNS_MAX_WORKER` in `tools/loop/loop.conf`): one site is ten turns — the read it
  owes, the edit, its own case and the correction a red case asks for — so the change fits only while `<n>` is no
  more than a tenth of those turns. A refusal records `surface: <n> sites over <m> files against <t> turns` and the
  split it proposes: each child named, the sites of the census it takes, and the epic the children are filed under.
- `guidelines/Backlog.recipes.md`, section 7b — one paragraph, beside the paragraph that prices the gate against the
  pre-screen, stating that row is a count rather than a judgement, with the 2026-11-17T10:58Z figures and the record a
  `too-wide` skip now carries. The rule has two homes because it has two readers: the prompt is what a child applies,
  the guideline is what a session outside the loop reads (`prompt#thePhasePromptsCarryTheirOwnRoute` asserts the
  routing that puts a child in front of the first).
- `tools/loop/tests/loop-cases.sh` — `prompt#theTooWideRowCountsTheSurfaceBeforeTheVerdict`, its `LOOP_SHELL_CASES`
  row and its dispatch arm; the case reads the rendered worker prompt (`items/21/worker-prompt.md`) and asserts the
  five pieces of the row a reader would otherwise take on trust: the count, the census line it is taken from, the
  bound, the key the bound is read from, and the record with the split.
- `tools/loop/tests/loop-mutations.sh` — `959.1`, which removes the bound the count is read against, so the census
  prints a number nothing compares.

Not this change: the bound itself is a policy, not a measurement (see `design.md`, "Alternatives the evidence
eliminated"); nothing here re-derives the run's spend, moves the pre-screen, or adds a key to `tools/loop/loop.conf`.
