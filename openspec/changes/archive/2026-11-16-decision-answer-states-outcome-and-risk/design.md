# Design — how the answer states outcome, solve and risk

Reference: `proposal.md` for the four reported defects and their pointers, `specs/decision-queue/spec.md` for the
contract, `.pi/skills/answer-decision/SKILL.md` and its `references/answer-shape.md` for the implementation.

## D1 — The shape is restructured, not appended to

**Alternatives.**

1. **Append three lines to the existing option block.** Cheapest: `What you get`, `Does it solve it`, `Risk` after the
   mechanism. Rejected: the option's first line stays the mechanism, so the owner still reads the architect's answer
   first — the reported defect is *ordering*, not omission, and a block that opens with `prescreen.sh:384` is the same
   answer with three words added. Measured: `4b` at HEAD opens each option with "the mechanism in one sentence".
2. **Two separate documents — one per audience.** Rejected: a fork split in two is two forks, and the owner must hold
   both to judge one option. The two lanes are *inside* each option for that reason: everything the owner decides from
   is contiguous, and the mechanism is one heading below it.
3. **Restructure to five labelled lines per option (chosen).** The owner's four lines come first and in the order the
   questions are actually asked — what do I get, is it solved, would I know, what could go wrong — and the architect's
   mechanism moves last, under a heading that names it. Verified against the four reported symptoms one by one.

**Consequence:** the shipped `#788` worked example is rewritten in the new shape, because a skill whose own example
does not follow its shape teaches the old one.

## D2 — Risk is a level *with a clause*, four fields, and the fourth option

**Alternatives.**

1. **A single risk column, kept, with a wider cell.** Rejected: a cell cannot hold four answers, and the reported
   defect is precisely "risk not clearly communicated". It also has no place for the baseline.
2. **A scored risk (1–5) summed into the recommendation.** Rejected: it is the average §9 forbids, wearing a number —
   and a risk score that competes with the benefit invites the reader to trade a silent failure against a visible gain.
3. **Level + the clause that sets it, four fields, and a `do nothing` row (chosen).** The clause is what makes the
   level checkable ("Low — a label write is removed and no reader depends on it"); the fields are the four questions a
   person actually asks (how bad, who, undo, detect), and *what would catch it* has "nothing would" as a legitimate
   answer, which is the most useful sentence on the sheet. The baseline row exists because `guidelines/Backlog.md` §5
   makes a waiting item cost a decision slot: not choosing is not free, and a fork table without it compares every
   option against zero.

## D3 — The presentation is one message, and the question carries the verdict

**Alternatives.**

1. **Keep the current flow (list → ask → read → present → ask).** Rejected: two waits for one answer, and the first
   is avoidable — "which decisions are waiting" *is* answered by the listing.
2. **Stream the sheet as it is built (each section as its block completes).** Rejected: it reads as progress,
   commits the owner to reading a half-built fork, and the reported symptom is exactly this.
3. **One batched read pass, then one message, then one `ask_user` (chosen).** The `ask_user` choices carry what each
   option gets and the `context` repeats the recommendation with the risk levels, because the choice is taken in that
   dialogue — an owner who must scroll back to remember which option was safe chooses by letter. A second question is
   allowed only when it would change the recommendation, and then in the same call.

**Consequence:** the reader needs the reference skeleton; without it "one message" becomes a wall of prose. That is
what `references/answer-shape.md` is for, and it is why the change adds a file rather than only rules.

## D4 — What is deliberately left out

- **The run's escalation comment is not changed.** The loop writes the fork's raw material (question, options,
  recommendation) and `guidelines/Backlog.md` §5 already requires that. Requiring the run to state an *outcome* and a
  *risk* per option would mean the run prices risk — a judgement it has no standing to make (it may not decide). The
  reader prices it, from the tree. If a future measurement shows the run's own option list is systematically unusable,
  that is a separate item against `tools/loop`.
- **No numeric size bound in the skill.** The skeleton's seven checks state the size in prose ("roughly one screen of
  prose plus the four tables"). A byte count in a skill is a number nothing measures; the case counts blocks, not
  bytes.
- **No change to `answer.sh`.** The four outcomes and the `<!-- answers: … -->` marker are untouched, so this change
  cannot affect a tracker write.

## Risks

| risk | level and clause | caught by |
|---|---|---|
| The sheet grows into a wall of text, and the owner skips it | **Medium** — five lines per option plus four tables is more than the old two; the reference's size note is prose, which no check reads | nothing automatic; the owner's report is the only signal |
| A case passes on a phrase while the shape loses a block | **Medium** — cases assert substrings; a rearranged skeleton still matches | the ordering assertion in `decisions#theSheetLeadsWithTheVerdict`, which reads line numbers and fails on a move |
| The lane rule is read as "no technical words at all", producing vague answers | **Low** — the rule is scoped to the owner's four lines and glosses an unavoidable name in place | the reader's own review; the worked example shows a glossed name |
| The skill's size (23.6 kB) costs the agent context on every call | **Low** — one read per invocation, within the range of the shipped skills (largest 33.9 kB) | `wc -c` on the file, if it becomes a complaint |

Every option this design rejects is rejected on a ground a reader can check in the tree, and none of them is rejected
for being expensive.
