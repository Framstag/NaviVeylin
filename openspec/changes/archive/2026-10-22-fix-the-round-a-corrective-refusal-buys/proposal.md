# fix-the-round-a-corrective-refusal-buys

## Why

Issue #245. Verified in the run's worktree on 2026-10-22 against the run's base commit
`c70be3d50e613`, with the change root `.pi/worktrees/loop`.

**What the item claims, and what the tree says.** The item measured the cost of the round a corrective
refusal buys: three of 18 items with a full review history went `FAIL > FAIL > PASS`, and #98's rounds 2 and 3
moved four documentation and evidence files and no compiled file
(`.pi/issue-loop/items/98/reviewer-report-2.md`, `-3.md`, `-run.json` `metrics.reviewRounds=3`). Its fix shape
was to close such an item without a reviewer at all. The **decision recorded on the item is option 3**, and it
is a different fix: keep the third round, and hand it only what it must verify — the correction diff plus the
refusing round's findings — instead of the whole change (`write_review_diff "$base"`, `run.sh:890` in the
item's own line numbers, `write_review_diff "$diff_base"` here). That decision is settled; this change
implements it and does not re-open it.

**Where the tree is today, with a line.** Every round's diff is built from the run's base:
`tools/loop/run.sh:931` (`write_review_diff "$diff_base" "$diff_file"`, where `diff_base` is `$base` for every
round) and the same at `run.sh:1014`. The round a corrective refusal buys is granted at `run.sh:990-992`
(`round_cap=$((round_cap + 1))`) and changes nothing about what the next round is handed: its diff is the whole
change again, and its prompt carries no trace of the findings it exists to decide
(`tools/loop/prompts/reviewer.md`, whose only mention of the extra round was "a strictly subtractive finding …
needs no third review" — the sentence #187 already made false for the budget, and which said nothing about
what a third round reads).

**Root cause, one sentence:** the round a corrective refusal buys is built and prompted exactly like the round
before it — its diff from the run's base and its prompt with no refusing findings — so a reviewer that exists
only to decide "were these subtractive findings answered?" re-reads the whole change, which is the #204
measurement at a smaller scale (item #126's round-3 diff: 435 KB, 9 935 changed lines, 9 364 of them generated
output, against 112 lines of Kotlin).

**Red test on HEAD, before the fix.** One case in `tools/loop-cases.sh` — `run#aCorrectiveRoundIsHandedTheCorrections`
— run against the base commit with the case file in place and the engine at `c70be3d50e613`:

```
FAIL run#aCorrectiveRoundIsHandedTheCorrections
  and not the line the corrections replaced, which the refusing round already graded — as context it may sit
  beside the hunk, as a change it may not: '+the first pass' should not appear in: diff --git a/demo-fix.txt
EXIT=1
```

`evidence/red-HEAD.log` (2026-10-22T07:49:59Z, `EXIT=1`) and `evidence/red-HEAD.xml`
(`tests="1" failures="1"`, counted from the case's own `FAIL` line the way `tools/loop/gate.sh` derives a
suite's tally).

**Round 1's refusal, and what this round changed.** The first review round refused the change with one blocking
finding: `render_reviewer_prompt` substituted `{{CORRECTION_SCOPE}}` **last**, and `loop_render`
(`tools/loop/lib.sh:409`) substitutes one pair at a time on the template it has accumulated so far — so the
`{{DIFF_PATH}}` and `{{CHANGE_ROOT}}` written *inside* the section survived verbatim, and the round that exists
to verify corrections pointed its reviewer at a file called `{{DIFF_PATH}}` (`items/245/review-1.diff`). No case
asserted the absence of a leftover placeholder in a reviewer prompt, which is how it passed a gate at
`tests=477 failures=0`. Three things follow, and they are this round's delta:

- the pair is substituted **first** (`run.sh:686-690`), so the paths the section names are the pairs that follow;
- the case asserts both halves — `review-3.diff` is named and no `{{` survives in the round-3 prompt, and the
  round-1 prompt names `review-1.diff` with no leftover either. Red on the round-1 engine, for the right reason:
  `every placeholder in the correction section is substituted: '{{' should not appear in: # Reviewer — the fixed
  contract` (`evidence/red-placeholder.log`, 2026-10-22T08:10:43Z, `EXIT=1`); green after the two-line order fix
  (`evidence/focused-cases-green.log`, 08:11:03Z);
- mutation `40.3` is that order, so the invariant is falsifiable and not only demonstrated once
  (`evidence/mutation-40.log`, `evidence/mutation-40.3-reason.log`).

**Owning capability and guideline section.** `agent-issue-loop` — the requirement "Each item is worked by a
fresh process, reviewed by a different one" carries the round budget and its corrective extension, and this
change adds the round's input contract beside it. The guideline that owns the loop is `guidelines/Backlog.md`
§7 "The loop", which states what a round is handed nowhere today.

surface: 1 case added (extended by the round-1 review with two placeholder assertions), 0 cases rewritten, 3
mutations added, 1 prompt section and 2 prompt paragraphs, 1 guideline paragraph, 0 fixtures —
`grep -c 'aCorrectiveRoundIsHandedTheCorrections' tools/loop-cases.sh` (3: the registration, the dispatch and the
case itself), `grep -c "^  '40\\." tools/loop-mutations.sh` (3).

## What Changes

- `tools/loop/run.sh` — `diff_base` is a loop variable: `$base` for every round, the refusing round's own HEAD
  for the round a corrective refusal buys, taken by both `write_review_diff` call sites.
- `tools/loop/run.sh` — `review_correction_scope` renders the section that round's prompt carries (range,
  refusing report's path, report quoted), passed as `{{CORRECTION_SCOPE}}` and substituted **first** so the paths
  the section names resolve.
- `tools/loop/prompts/reviewer.md` — the placeholder, and the sentence about a subtractive finding replaced by
  what the round is handed and what it grades.
- `guidelines/Backlog.md` §7 "The loop" — the paragraph that states what the round is handed and the cost it
  avoids.
- `tools/loop-cases.sh`, `tools/loop-mutations.sh` — the case `run#aCorrectiveRoundIsHandedTheCorrections` (its
  range, its findings and its no-leftover-placeholder assertions) and mutations `40.1`–`40.3`.

## What the decision leaves out, and why this change does not touch it

Option 3 keeps the reviewer process: it removes the re-reading, not the 300 to 420 s a round costs. The item's
own `Bound: 0` (no reviewer process for a corrections-only round) is therefore **not** what landed, and the
closure comment records that the item's fix shape was narrowed by its recorded decision. The options 1, 2 and
4 the decision lists are answered by it; none of them is re-derived here.
