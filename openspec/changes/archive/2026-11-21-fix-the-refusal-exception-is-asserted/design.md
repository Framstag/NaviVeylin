# Design — one assertion, and the record that makes it falsifiable

## Root cause

`case_prompt_reviewerCarriesTheEightItems` (`tools/loop/tests/loop-cases.sh:4769`) asserts three phrases of the
#954 clause over the rendered reviewer prompt — `non-interactive` (`:4795`), `never ask a question` (`:4796`) and
`an answer that is a question is a cut-short answer` (`:4797`). The clause also carries the exception that keeps a
refusal's `LOOP-DECISION` line (`tools/loop/prompts/reviewer.md:64`), and no assertion anywhere names it: the
mutation record `954.1` rewrites `never ask a question` (`tools/loop/tests/loop-mutations.sh:1485`), a phrase the
exception does not contain, so deleting the exception's whole sentence leaves both cases green and the record
applying.

That state is the defect issue #894 measured, restored silently: the clause forbids a question of the round's own,
the contract twenty lines below requires a refusal to end with a `LOOP-DECISION` line, and a reader told only the
first half writes a refusal the run's own reader cannot use.

## The single fix

Two edits, both of them the same claim:

1. **The assertion.** A fourth `assert_contains` in `case_prompt_reviewerCarriesTheEightItems`, on the sentence's own
   words: ``the `LOOP-DECISION` line a refusal owes is part of that contract``. It sits on one line of the template
   (`tools/loop/prompts/reviewer.md:64`), which is what `assert_contains` over `cat` needs — the case does not
   flatten the text the way its sibling does.
2. **The record.** `965.1` in `tools/loop/tests/loop-mutations.sh` deletes the two template lines that carry the
   sentence (`/^the \`LOOP-DECISION\` line a refusal owes/,/^you are putting to anyone[.]$/d`), so the new assertion
   is the thing that goes red and the harness's own `restore … green again` is the proof it was the assertion.

The assertion and the delta's one scenario are one claim, clause for clause: the scenario's `**THEN**` says the
exception says this, and the assertion is that sentence of the template. The sentence's tail — `written as the
contract says, and not a question you are putting to anyone` — is the same claim refined, and it is not a second
`THEN` clause: a clause no assertion can point at is the finding this shape avoids.

No third edit. In particular the prompt is not touched: the sentence is on the base, and a change that rewrote it to
be more assertable would be a change to the clause #954 landed rather than the case #954 left unowned.

## Why not the alternative the item names as its sibling

`954.1` looks like it covers the clause and does not. Extending it to delete the exception as well would give one
record two invariants, which `.pi/skills/revert-check` refuses (*one mutation per check*): a record that breaks two
things reports one red and hides which invariant its red belonged to. So the exception gets its own label, `965.1`,
beside `954.1`, and the two clauses are falsified separately.

## Blast radius

- One case body (`case_prompt_reviewerCarriesTheEightItems`) and one record. Both files are inside `tools/loop/tests/`,
  which `tools/loop/layout.tsv` does not bound: `layout_table_refusals` reads the engine's top-level `*.sh`
  (`tools/loop/tests/loop-cases.sh:18600`).
- The case's `# files:` line already declares `tools/loop/prompts/reviewer.md`, so a delta to the prompt selects it —
  `driver#everyCaseDeclaresTheFilesItCanReach` passes unchanged.
- The delta writes no compiled file, so it owes the structural checks of the files it edits and the loop's own suite
  (`guidelines/BuildGate.md` §2a): the suite selection the gate derives from this diff, and
  `tools/loop/tests/loop-mutations.sh --check`.
- No other case asserts these words: `tools/look.sh 'a refusal owes is part of that contract'` names
  `tools/loop/prompts/reviewer.md:64` and two archived files, and no case or record.

## The red case, and why it is taken on another blob

The assertion's claim is *the template carries this sentence*, and the template carries it on the base, so the case
is green against the tree as it stands — it has to be, or the change would be asserting a sentence it also has to
add. The red this item owes is therefore taken the way #954's own correction was measured: an engine copy whose
`prompts/reviewer.md` is `41dc62e3`'s blob — the pre-correction prompt, which carries no exception — and the case run
against it. It fails with the new assertion's own message (`evidence/red-on-41dc62e3.log`), and it fails for that
reason and no other: the case is green against the tree's own template
(`evidence/green-on-change.log`, 2026-11-21T05:45:09Z, `exit=0`), and `965.1` deletes only that sentence and sees it
red then green again (`evidence/mutation-965.1.log`).

## Rollback

Revert the commit: the case loses one `assert_contains` and the set loses `965.1`, which is the state the base is in.
No tree, artefact or evidence outside the change is touched.
