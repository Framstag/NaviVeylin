# Proposal — the read that follows an edit is the file's head, not the file

## Why

**Root cause.** The repeat rule's content test has two answers, and only one of them is bounded:
`repeatResult` (`tools/pi-trim-result.mjs:287`) compares the read's sha with the ledger's entry for that path, replaces
an unchanged repeat with one line (`alreadyReadNotice`, `:238`), and — when the sha differs, which is the session's own
edit coming back — returned the read **whole** (`if (!earlier || earlier.sha !== sha) return text;` in the base
`8493f017`). So a verification read, the one read a child makes *because* it just wrote the file, re-injected the file it
had just edited: the branch hands over the volume the rule exists to keep out, and it is the branch that fires on the
reads an editing session makes most.

**Evidence.** The item's own measurement, run `2026-11-13T05:47:11Z`: 604 of 1 281 file reads (47 %) were a second read
of a path the session already had, and the repeat results carried 2 838 881 characters against 200 379 withheld, worst
item #725 at 97 of 209 reads. The rule's header cites the archive for the other half: 728 of 2 042 per-session repeats
follow an edit of that path (`tools/pi-trim-result.mjs:30-32` as the base has it) — every one of them a full file paid
for again.

**The red test.** `repeat#aVerificationReadIsBoundedNotHandedOverWhole`
(`tools/loop/tests/loop-cases.sh:19195`, registered in the list at `:464` and the dispatch at `:1216`), which replaces
`repeat#aRepeatAfterAnEditIsHandedOverWhole` — that case asserted exactly the behaviour this change removes, so it is
rewritten and renamed rather than left to pass by the accident of a small file. Red on the base
`8493f017c5d6aeb2a3e731188e8a80fd6580958e`: `loop-selftest: 1 of 1 cases failed`, exit 1, `tests=1 failures=1` (XML not retained — the selftest tree it was written in is gone; the failure text is kept),
2026-11-16T03:55:58Z, the failure being the case's first clause —
`the read after the edit is not the whole file again: 'LAST-LINE' should not appear in: …`
(`evidence/red-on-head-repeat-886.log`). Green once the branch is bounded, and mutation record `886.1` puts the whole
text back to prove the case notices.

**The owning capability and the requirement.** The capability is `agent-issue-loop`, and the owning requirement is
"The repeat rule's ledger is the item's, not the child's"
(`openspec/specs/agent-issue-loop/spec.md:4906`), whose third sentence states the clause this change modifies: *"unless
the content that path holds has changed, in which case the read SHALL be handed over whole."* The delta rewrites that
clause and adds the scenario the bound owes.

**The owning guideline section.** `guidelines/Backlog.recipes.md` section 7b "What an item costs, and the bounds on it" is
where the item's bounds are stated, and it carries the ledger's line shape and the repeat rule's record — it is edited
beside them, in the paragraph that names `sha`, turn, size and session (`:201-215`).

**Surface, counted before the plan was fixed.**
`./tools/look.sh 'verificationResult|VERIFICATION_READ_BOUND|LOOP-REPEAT-EDIT'` → 19 matches in 4 files
(`evidence/surface-886.txt`): 1 requirement clause and 1 scenario rewritten in the delta spec, 1 case rewritten and
renamed, 2 cases' adjacent prose realigned, 1 engine file (the branch, two prose paragraphs, one exported bound and two
exported functions), 1 guideline paragraph, 1 mutation record. The count is what the estimate is set against: one
function and its call site, plus the prose each place states the rule in.

surface: 1 case added, 1 case rewritten and renamed, 2 cases' prose realigned, 1 requirement clause and 1 shipped
scenario rewritten, 1 scenario added, 1 guideline paragraph, 1 mutation record — ./tools/look.sh
'verificationResult|VERIFICATION_READ_BOUND|LOOP-REPEAT-EDIT'

## What changes

`tools/pi-trim-result.mjs` gains `VERIFICATION_READ_BOUND` (2 000, `:138`), `verificationNotice` and `verificationResult`
(`:316`), and `repeatResult`'s sha-difference branch (`:294`) calls the last of them instead of returning the text. A
verification read is
handed the file's head at that bound plus one `[LOOP-REPEAT-EDIT: …]` line naming the path, the turn the item read it
at, the size the file now has and the call that reads the rest; what the cut withheld is the same
`LOOP-TOOL-RESULT-REPEAT: <tool> chars=<n> kept=<k>` line the unchanged repeat writes, so `metrics.withheld` counts it.
A text that fits the bound, and the class the 8 000-bound already exempts (a gate log, a suite XML — the `§4d` files a
red run is attributed from), are handed over as they stand.
