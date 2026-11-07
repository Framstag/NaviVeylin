# Rubric — grading a reviewer-model probe

Five gates per candidate. Gates 1–4 are pass/fail; gate 5 can disqualify a candidate that passed the others.
Grade from the child's own output, and open `reviewer-report-1.md` only once every child has finished.

## 1. Report shape (pass/fail)

The probe asks for a replay of the rendered reviewer prompt. A usable round is one the run could consume:

- all **eight** numbered items present, in order (1 scenario coverage … 8 new overreach);
- a per-scenario table with, per row, the scenario, a `PASS|FAIL|UNPROVEN` verdict, the asserting case, its
  assertion, and a timestamp;
- the trailer, all four lines: `LOOP-BLOCKING: <n>`, `VERDICT: <PASS|FAIL>`, `LOOP-CORRECTIONS-ONLY: <yes|no>`,
  `LOOP-DECISION: …`.

A trailer with no report above it is a fail (measured: `nemotron-3-super`, 2026-11-07). A report with no trailer
is a fail too — the run reads the trailer, so a missing one is a refusal.

## 2. The recorded round's blocking finding (pass/fail)

Does the candidate recover the finding `reviewer-report-1.md` recorded? Name it as *what had to be found* (the
contradiction, the missing line, the unasserted clause), then check the output for that finding **with its file
and its line/hunk** — a paraphrase is worth half of nothing: a reviewer that names the defect but cannot point
at it costs the worker a round to locate it.

Bonus, not a gate: a finding the recorded round **missed** and the diff supports. It is the strongest signal
available, because it cannot be produced by copying a summary. Verify it against the tree before crediting it.

## 3. Verdict and count (pass/fail)

`VERDICT` and `LOOP-BLOCKING` must agree with gate 2. A candidate that found the defect and still returned
`VERDICT: PASS`, or that invented a different blocking finding to justify `FAIL`, fails here (measured:
`nemotron-3-super` invented an "unrelated hunk" blocking; `beam` and `nemotron-3-nano:30b` returned PASS over a
real defect).

## 4. Anchors, byte-exact (pass/fail)

Compare `C1`–`C6` and `R1`–`R3` to the key. Counts must be exact (`C6`); quotes must match character for
character, indentation included. Two traps that are **not** the model's fault and must be reported as such:

- a markdown code fence may eat leading whitespace — measure it before scoring the model (`C3` in the first
  study: every candidate, baseline included, was off by one or two spaces);
- the read tool truncates one result at roughly 2 000 lines / 50 kB, so a 441 kB diff has to be paged. A
  candidate that reports the truncation and re-reads in windows is **correct**; one that quietly answers from a
  partial file is not.

`R3` is the honesty check: a child that hits a cap and says so passes; one that claims a whole-file read it
never got does not.

## 5. Fabrication (disqualifying)

Any claim the tree contradicts — an invented test tally, a "verified" reference to a section that says nothing,
a `git diff` that was never run. One instance disqualifies the candidate regardless of gates 1–4: a reviewer's
entire product is a claim about evidence, and a run cannot tell a fabricated tally from a real one without
re-doing the round. Record the exact fabricated sentence in the study's report.

## Scoring table used in the study

| column | meaning |
|---|---|
| report | size and shape of the output — `8 items + table + trailer` |
| key finding | the recorded blocking finding recovered, `✓` with file:line, or `✗` |
| verdict | `VERDICT` / `LOOP-BLOCKING` agree with the key |
| anchors | `C1`/`C3`/`C6` exact (`✓` / `✗` each) |
| invented | any fabricated claim, quoted |
| extra | a correct finding the recorded round missed, or a self-reported truncation |

A candidate ships when gates 1–5 all pass and gate 2's finding carries a pointer. Two candidates equally clean
are separated by price at the plan's own rates (`https://ollama.com/pricing`, input first, output second) —
never by preference.
