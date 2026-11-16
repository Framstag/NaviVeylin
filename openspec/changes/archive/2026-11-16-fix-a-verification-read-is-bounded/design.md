# Design — the read that follows an edit is the file's head, not the file

## The root cause, in one sentence

The repeat rule's content test answers a changed sha with the text it was handed, so the one read a session makes
*because* it just wrote a file is the read that injects that whole file again.

## The single fix

`repeatResult` (`tools/pi-trim-result.mjs:287`) keeps its ledger write and its two answers, and the sha-difference
branch (`:294`) calls `verificationResult` instead of returning the text. `verificationResult` (`:316`):

- hands a text that fits `VERIFICATION_READ_BOUND` (`:138`, 2 000 characters) over whole, so a read of a small file
  after an edit is unchanged from before this change;
- hands gate/evidence output over whole whatever its size, because `guidelines/BuildEvidence.md` §4d reads such a log or
  suite XML for the `timestamp` order a red run is attributed in, and the 8 000-bound already exempts that class;
- otherwise hands `boundedHead(text, VERIFICATION_READ_BOUND)` — never cut inside a surrogate pair — plus one
  `[LOOP-REPEAT-EDIT: <path> changed since turn <n>; the first <bound> of <total> characters are shown — read the rest in
  ranges (ctx_read mode="lines:N-M")]` line (`verificationNotice`, `:145`);
- appends `LOOP-TOOL-RESULT-REPEAT: <tool> chars=<n> kept=<k>` to the record the adapter names, which is the line the
  unchanged repeat already writes, so `metrics.withheld` (`tools/loop/state.sh`) counts this volume with no reader to
  add and the report's "withheld by the repeat rule" share stays one figure.

The ledger entry is still written from the *whole* text (`sha`, turn, size, session), so the read after the bounded read
— unchanged content — is a repeat measured against what the edit left, and its notice names the file's own size and the
turn it came in at. The bound `0` still switches both rules off (`repeatResult` returns early on `!(bound > 0)`), and
`LOOP_REPEAT_READ=refuse` still answers ahead of the content test, so nothing this change does reaches the arm's answer.

## Blast radius

One engine file, one case, one mutation record, one guideline paragraph, one requirement of the delta spec. No compiled
file: nothing in this change is built, and the structural checks stand in for the build (see `tasks.md`). The bound is
applied in the same place the recorded result bound is applied, so no adapter, prompt, profile key or state field is
added — a ported project that deletes `tools/pi-trim-result.mjs` owes nothing by it.

Reads that change shape: a verification read of a file larger than 2 000 characters. The archive's median result is 825
characters (`evidence/result-sizes.py` in `2026-10-27-fix-a-tool-result-over-its-bound-is-trimmed`), so most
verification reads are handed over whole as before, and the ones that are not are the ones the item measured 2 838 881
characters of.

## Rollback

Revert the hunk in `repeatResult` — `return verificationResult(...)` back to `return text` — and the case
`repeat#aVerificationReadIsBoundedNotHandedOverWhole` goes red, which is exactly the state the base has and the state
mutation record `886.1` reproduces. Nothing written into a record changes shape, so an archived item's evidence stays
readable either way.

## Alternatives the evidence eliminated

1. **Return the changed region** (the first half of the item's direction). Eliminated by the ledger's own shape: an
   entry carries `sha`, turn, size and session (`repeatResult:292`, `readLedger:337`) and no text, so the region the
   edit changed is not computable from it. Computing it means storing every read's text in the item's ledger — the
   volume the rule exists to keep out of the context, now written to the item's disk instead, in a `tsv` whose lines are
   deliberately path-and-size only and whose record lines deliberately name no path. The tool call that precedes the
   read carries a path and no diff, so the region is not available from the input either. This is why the item's second
   half is the one implemented rather than a preference between two viable options.
2. **Raise `LOOP_TOOL_RESULT_MAX_CHARS` instead, or narrow it.** It is the bound this rule already runs beside, not the
   one it is missing: a verification read of a 6 000-character file is under the 8 000 bound and was handed over whole
   by this branch alone, and lowering the recorded bound would cut every first read to fix a repeat's answer.
3. **Withhold the verification read entirely** (the `refuse` arm's answer for it). Rejected on the rule's own stated
   reason: the child is checking what its own edit did, and a notice naming a range it cannot hold costs a turn to read
   what it just wrote.
