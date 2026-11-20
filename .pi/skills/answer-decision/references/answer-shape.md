# The sheet — skeleton, and the checks before you send it

Fill this. It is one message, verdict first, and the owner reads it once. `SKILL.md` states the rules; this file is
the shape they produce, so a missing block is visible here rather than in the owner's next question.

Two lanes run through the whole sheet. The **owner's lane** is product language: no path, no identifier, no tool
name, no command — a name that cannot be avoided is glossed where it first appears. The **architect's lane** carries
the mechanism and is entered by a named heading only. A code token in the owner's lane is a defect of the sheet.

## The skeleton

```
# #<n> <title>

**What you are choosing** — <one plain sentence, no code: the fork, as the owner would put it>.

**Recommendation: <option>** — <one sentence>. Risk <Low|Medium|High>: <the clause>. Solves <full|partial|no>:
<the residue>.

**Solved means** — <the item's own observation, and the check that would show it gone, dated or reproducible>.

**If you choose nothing** — <what continues, what it costs, and whether it gets worse>.

## What the item states
- **Observed** — <what is wrong>.
- **Evidence** — <the measurements, each marked measured or claimed, with dates>.
- **Why it matters** — <the cost of leaving it>.
- **What the user sees** (today → after, who is affected: which screen, flow or car surface).
- **Settled, not to reopen** (the decision of record, its date, what it rejected).
- **The decision actually being asked** — <the fork; which options the item already rejects, and on what ground>.

## Options

### <A> — <short name, in the owner's words>
- **What you get** — <the deliverable, concretely>.
- **Does it solve it** — <full|partial|no>; <the residue: what is still broken, parked or asked>.
- **How you'll know** — <the observable that would show it worked>.
- **Risk** — <Low|Medium|High>: <the clause>.
  - *If it is wrong and nobody notices* — <…>
  - *Who it would hit* — <which surface, flow, item, session>
  - *Can it be undone* — <…, at what cost>
  - *What would catch it* — <the check> | *nothing would*
- **For the architect** — <mechanism in one sentence; files; gate minutes; rounds to land>.
  **Example:** <a state and its consequence, before → after> — <measured | unmeasured>.

### <B> — … (the same five lines)
### <C> — … (the same five lines)

## Criteria

| criterion | A | B | C |
|---|---|---|---|
| closes the stated observation | <yes/partly/no + basis> | | |
| what you stop seeing | | | |
| rounds to land | | | |
| risk if it is wrong | | | |

## Risk

| | A | B | C | do nothing |
|---|---|---|---|---|
| risk level, and the clause | | | | |
| if it is wrong | | | | <the state you are already in> |
| undone at | | | | — |
| caught by | | | | nothing |

## The two views (architect)
**Short term** — <the table: gains, costs, rounds to land, what breaks if it is wrong>
**Strategic** — <the table: cause removed or re-counted, survives the next items, effect on the invariants>

## Recommendation

**Recommendation: <option>** — <one sentence>. Risk <level>: <the clause>. Rejected: <option> because <one
clause>; <option> because <one clause>. First sign it is going wrong: <the observable>.

## The score (0–10, the last block)

| option | name | points | what set it |
|---|---|---|---|
| <A> | <the option's short name, in the owner's words> | <0–10> | <the view that decided — the owner's criteria, the short term or the strategic one — and its clause> |
| … | | | |
| do nothing | — | <0–10> | <the clause> |

The recommendation holds the highest number; a delta of 1 is a tie and names the clause that broke it, 2–3 is
"clearly worse", and 4 or more says the row was carried rather than offered.
```

## Before you send it — eight checks

1. **Does it lead with the answer?** The recommendation is in the third line, before any detail. An owner who reads
   two lines and stops still knows what you think and why.
2. **What do I get, for every option?** One concrete deliverable, in the owner's words, not a mechanism.
3. **Is "does it solve it" answered for every option?** *full*, *partial* or *no*, plus the residue in the same
   line. An option that is silent about its residue reads as a promise.
4. **Would I know it worked?** Each option names the observable. "Better behaviour" is not an observable.
5. **Is every risk a level *and* a clause, with its four fields?** And is the *recommended* option's risk stated as
   plainly as the rejected ones'?
6. **Is "do nothing" priced?** The queue does not wait for free, so the baseline has a row.
7. **Is there a code token in the owner's lane?** Then the sheet is not finished. Move it under "For the
   architect", or say what it means in the reader's words.
8. **Do the numbers agree with the sheet, and does every row carry its clause?** The recommendation holds the highest
   score, `do nothing` is on the same scale, no delta is reported without its reading (1 = tie, 2–3 = clearly worse,
   4+ = carried, not offered), and no row is a number with nothing behind it.

Size: the whole sheet should fit roughly one screen of prose plus the four tables. If it needs more, the item is
carrying two forks — say so in "The decision actually being asked" rather than answering both.
