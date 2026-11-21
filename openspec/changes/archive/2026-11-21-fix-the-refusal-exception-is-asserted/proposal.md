# The refusal's decision line is asserted, and a mutation is what falsifies it

The clause issue #954 landed (`tools/loop/prompts/reviewer.md:60-65`, commit `c2d326ef`) carries an exception: a
refusal still ends with the `LOOP-DECISION` line, and that line is not the question the clause forbids. The
exception is the correction round's fix for issue #894 — the clause as first landed forbade the one line the same
file's contract (`tools/loop/prompts/reviewer.md:165`, `:172`) and the shipped requirement
(`openspec/specs/agent-issue-loop/spec.md:5866-5867`) demand of every refusal.

**Root cause, in one sentence:** the case that owes the clause asserts three phrases the exception does not contain
(`non-interactive`, `never ask a question`, `an answer that is a question is a cut-short answer`), so deleting the
exception's sentence leaves that case, its pre-gate sibling
`prompt#thePreGateRoundRefusesOnlyWhatTheGateCannotSettle` and mutation `954.1` green — the defect #894 measured can
return with no measurement against it.

**The red test:** `prompt#reviewerCarriesTheEightItems` fails on the pre-correction prompt, `41dc62e3`'s
`tools/loop/prompts/reviewer.md` blob, which carries no exception:

```
prompt#reviewerCarriesTheEightItems|   and that a refusal's LOOP-DECISION line is not the question the clause forbids:
'the `LOOP-DECISION` line a refusal owes is part of that contract' not found in: # Reviewer — the fixed contract
loop-selftest: 1 of 1 cases failed
```

(`evidence/red-on-41dc62e3.log`, 2026-11-21T05:45:02Z, `exit=1`; run against an engine copy whose
`prompts/reviewer.md` is that blob, so the shared tree is never mutated.) The loop's gate is a shell self-test, so
there is no JUnit XML of its own (`.pi/skills/revert-check/SKILL.md`, "When the gate is a shell self-test, not
Gradle"): the tally is the suite's own line, `loop-selftest: 1 of 1 cases failed` — `tests=1 failures=1` (not retained as XML: a shell self-test writes no JUnit XML; the record is `evidence/red-on-41dc62e3.log`).

**The owning requirement:** `agent-issue-loop` — "A review round is non-interactive, and a question is a cut-short
answer" already states the exception ("leave a refusal's `LOOP-DECISION` line to the contract it points at") and no
scenario of it names a case that asserts the words. The delta MODIFIES that requirement by adding one scenario,
`A refusal's decision line is not the question the clause forbids`, whose case is
`prompt#reviewerCarriesTheEightItems`.

**The owning guideline section:** `guidelines/BuildGate.md` §2a, "Agent iteration protocol" — the rule that a `tools/loop/**`
delta owes the loop's own suite and that a case's invariant is falsified by a record rather than trusted, which is
the form `.pi/skills/revert-check` states mechanically. No guideline edit is owed: §2a states the rule the delta
obeys, and nothing in it states which phrases a prompt case must carry.

surface: 0 cases added, 1 case rewritten, 1 mutation record added, 0 fixtures realigned —
`tools/look.sh 'a refusal owes is part of that contract'` (3 matches in 3 files: the live
`tools/loop/prompts/reviewer.md:64`, and the archived change of #954 twice; no case and no record held it) and
`tools/look.sh 'never ask a question'` (24 matches in 9 files, of which the live assertions are
`tools/loop/tests/loop-cases.sh:4796` and `:7319` and the record `tools/loop/tests/loop-mutations.sh:1485` — the
phrases `954.1` mutates, which the exception does not carry).

## What changes

- `tools/loop/tests/loop-cases.sh` — `case_prompt_reviewerCarriesTheEightItems` gains one assertion on the
  exception's own sentence, beside the three #954 phrases it already asserts.
- `tools/loop/tests/loop-mutations.sh` — one record, `965.1`, deletes that sentence from
  `tools/loop/prompts/reviewer.md`, so the new assertion is falsified by a record rather than trusted: `954.1`
  rewrites `never ask a question`, which the exception does not contain and therefore cannot prove.
- `openspec/specs/agent-issue-loop/spec.md` — the delta above merges one scenario into the #954 requirement.

Nothing else: `tools/loop/prompts/reviewer.md` is unchanged. The sentence this change asserts already stands on the
base (commit `c2d326ef`), and the gap closed here is the case that owed it, not the wording that carries it.
`tools/loop/layout.tsv` owes no row — its bound reads the engine's top-level `tools/loop/*.sh`
(`layout_table_refusals`, `tools/loop/tests/loop-cases.sh:18600`) and neither edited file is one.
