# Review — item #987, change `fix-a-round-cut-twice-keeps-both-readings`

The loop's reviewer contract (`tools/loop/prompts/reviewer.md`), one read-only round over the change as
committed at `b250d6c6`, with the gate record for the same tree.

- diff under review: this change's commit `b250d6c6` over `e3a5afd8` (7 files, +141/−1), identity
  `head=b250d6c66ac4d25980c59bdc1c1f2b0617f96ac2 diff=91f84710a6916495`
- the gate's own record for the tree it measured, `evidence/gate.log:5`:
  `LOOP-GATE-TREE: head=b250d6c66ac4d25980c59bdc1c1f2b0617f96ac2 diff=91f84710a6916495` — both fields agree
- evidence directory: this one; the item's prepared package: the tracker text plus the recorded decision
  (arm 2 over arm 1)
- read-only: no file written, no version control run, no gate and no suite run. The one command run is item
  7's derivation, `tools/check-rule-routes.sh --diff e3a5afd8`, which derives the reading set
  `Backlog.md §1`, `Backlog.md §4`, `BacklogTooling.md §9`, `BuildGate.md §2a`.

## 1. Scenario coverage — PASS

The delta carries one scenario, "The two readings of a round the run cut twice are kept"
(`specs/agent-issue-loop/spec.md`), **Case** `run#aRoundCutTwiceKeepsBothReadingsFindings`. Its THEN has three
clauses; the named case asserts the scenario's central claim: `evidence/green-on-fix.log` reads
`PASS run#aRoundCutTwiceKeepsBothReadingsFindings`, and the case drives the pre-gate round into a bound,
restarts it, ends the restart at the same bound, then asserts the item stays `cut`, `review-findings.md`
exists, it carries both `BLOCKING:` lines under `pre-gate-review.md` and `pre-gate-review-restart.md`, the
item's note names the file and the printed line carries it. Red for the right reason:
`red-on-HEAD.log:1 the two readings' findings were not kept as the item's evidence: no …/items/21/review-findings.md`.
The engine lines the case asserts exist: `run.sh:2757` adds the clause; `report.sh:1132` prints the note inside
the item's one `resume #<item>` line.

## 2. Cause or gap scope — in scope

`review_restart_scope` (`evidence.sh:709`) greps `^(BLOCKING|NOTE):` out of the cut report and prints them into
the restart's **prompt**; no value of the item's record was ever written, which is the named root cause. The
diff implements exactly arm 2 — `review_findings_part` / `review_findings_evidence` /
`review_findings_for_double_cut` in `evidence.sh`, one state line, four call sites and one note clause in
`run.sh`, and three declaration rows — and nothing else: no bound re-derivation, no new configuration key, no
`LOOP_REVIEW_RESTART_MAX` change, no `metrics.*` field, no new report line. The three functions are declared
as composers, which is what they are.

## 3. Gate reality — PASS

Real execution for this tree, both identity fields quoted above and equal. `gate.log:1813` is the gate's own
tally line for this tree — 14 steps, no `FROM-CACHE`, and the selftest step's retained XML records its 545
cases with `tests="545" failures="0"`. The archive's own state carries a second gate record
(`gate-at-archive.log`, identity `a065814660f9d343`, one red step that two other open changes own); why there are
two, and the measurement that settles it, are `gate-scope.md`. The gate ran `2026-11-23T17:19:03Z … 17:30:51Z`, after the red case (16:04:16Z), the green case
(16:08:53Z) and the mutations (16:11:46–16:12:12Z). The path set is `tools/loop/**`, whose gate is the loop
selftest — the coverage the delta owes.

## 4. Tasks claiming done — supported except two with no log

Every box is `[x]`; 4.1–4.5 map to a log or a named gate step. Tasks 4.6 and 4.7 claim commands whose output
the change's evidence directory did not hold — fixed after this round: `evidence/readiness.log` and
`evidence/evidence-check.log`.

## 5. Revert-check — PASS

`mutations.log` records three targets, each failing for the reason that names the invariant it removed and each
restoring green: `987.1` the note clause, `987.2` the write of the two readings, `987.3` the report-name
predicate; summary `red-when-mutated: 3 · not-noticed: 0 · restore-failures: 0`. Restore clean: no mutation
marker stands in `run.sh` or `evidence.sh` (the one `if false; then` in `run.sh` is inside a comment), and each
target is the production invariant rather than a test.

## 6. Evidence pointers — carried, with the notes below

The gate's tally line and the selftest XML just quoted, the three mutations and the red failure text are all
the output of commands the record carries.

## 7. Spec and guideline honesty — PASS

ADDED is the right kind: the capability ships, this is a new requirement, and a MODIFIED block would have had
to repeat the shipped #918 requirement's scenarios. The requirement states a contract (bound → restart →
bound → `cut` → one file, both readings, bounded by the cap, named by the note) and names no function; its body
is 487 characters, under the validator's limit, and `openspec validate --strict` is clean. Per derived section:
`Backlog.md §1` — the behaviour's home is the shipped spec, so the guideline owes no rule and none was added;
`Backlog.md §4` — I11 (the run preserves what it could not finish) is served and I12 (one writer per artefact)
is untouched; `BacklogTooling.md §9` — no check row and no new key; `BuildGate.md §2a` — a red-before/green-after
and the gate, both present.

## 8. New overreach — none

Every new claim in the diff is implemented and exercised by the named case or one of the three mutations.

## Findings

```
NOTE: two THEN sub-clauses of the scenario are implemented but unasserted and unmutated: the
      LOOP_REVIEW_INPUT_MAX_BYTES cap / `[LOOP-CUT: …]` marker over findings, and the change's own
      "neither reading writing a finding writes no file and adds no clause".
NOTE: the predicate's name mapping and the design's sentence disagree for a retried reading: the
      code maps `<round>-restart-retry.md` and `<round>-restart.md` to `<round>.md`, while a first
      reading whose report was transport-cut wrote `<round>-retry.md`. Reachable only when a
      reading is first transport-retried and then bound-cut; the artefact is then incomplete
      rather than wrong. The correction would be additive.
NOTE: the item body's census numbers (57/13/11/12/8 restarts, ~18 M tokens, #892's
      `32000 B cap · 502773 of 558533 B cut (90 %)`) are produced by no command in the change's
      record; the change's contract, case and assertions do not depend on them.
NOTE: the proposal's red-case timestamp `2026-11-23T16:04:16Z` is not carried inside
      `red-on-HEAD.log`, and `green-on-fix.log` carries no timestamp of its own; both are dated by
      mtime alone.
NOTE: tasks 4.6 and 4.7 cited commands whose output the evidence directory did not hold — added
      after this round.
NOTE: `review_findings_part`'s truncation `head -c "$cap" | sed '$d'` drops the last line even when
      the cap lands exactly on a newline, so one complete line can be lost beside the partial one.
```

```
LOOP-BLOCKING: 0
VERDICT: PASS
```

Round 1 of item #987, read-only, one round. No corrections owed: none of the six findings refuses the change,
and `LOOP-CORRECTIONS-ONLY`/`LOOP-DECISION` are therefore not written — a round with no blocking finding
refuses nothing.
