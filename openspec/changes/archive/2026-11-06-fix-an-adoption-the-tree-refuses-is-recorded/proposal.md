# An adoption the tree refuses is recorded as `unmergeable`, not escalated

## Why

Issue **#794**. `adopt_finished_exclusions` (`tools/loop/run.sh:397`) hands each excluded branch to
`adopt_finished_attempt` and discards the result — before this change,
`adopt_finished_attempt "$issue" "$branch" >&2 || true` (`tools/loop/run.sh:406` on the base) — while the
pre-screen has already written that branch as the run's decision verdict
(`prescreen.sh:382`, `.excluded[] | select(.verdict == needs-decision) | .evidence = fix/issue-…`). Whatever
`adopt-item` refused for is therefore escalated two steps later by `escalate_prescreen_decisions`
(`tools/loop/run.sh:267`), with the reason "branch … carries a commit the run's base does not — a previous
attempt's work is on it": the owner is handed a merge the loop could retry as a question only the owner can
answer. `unmergeable` is already a word of the block vocabulary (`tools/loop/state.sh:512`,
`guidelines/Backlog.recipes.md`:725 "Leftovers, the report and the queue" beside `refused`), and no path writes it for an adoption.

**Root cause, in one sentence:** `adopt_finished_exclusions` reads the adoption's exit status only to print it, so
a refusal of a branch whose own evidence *is* admissible — the project tree is not clean, the base moved, the
replay conflicts — leaves no record of its own, and the pre-screen's decision verdict stays the only reading the
run's escalation predicate sees.

There is one mechanism, not two: `git grep -n 'adopt_finished_attempt\|adopt_finished_exclusions' tools/loop`
names one pre-screen call site (`run.sh:862`) and one start-time call site (`run.sh:1807`); only the first
discards its result, and the second records the refusal as the block it is. `worktree.sh` answers which branch it
is in one place (`branch_state_of`, `worktree.sh:439`), and `adoptable` there means every input the adoption
decides on its own — one archived change of its own, a green verification, no reviewer's refusal, no unanswered
round — so what is left for such a branch is the merge's inputs, which the next run finds whole.

**Measured** 2026-11-06 on #760, twice, with no work attempted in between: the pre-screen of the 2026-11-06T05:16
run excluded the item at 06:28Z with that reason and set `agent/needs-decision` + `status/on-hold`, 12 h after the
owner answered the item's only real question (2026-11-05T18:59Z) and 1 h after a person had un-held it (05:19Z).
`tools/loop/worktree.sh branch-state 760 fix/issue-760-an-item-s-only-spend-bound-counts-turns` read `adoptable
2026-11-05-fix-a-child-is-bounded-by-its-context` the whole time. The adoption had failed because the project
tree was not clean; the 2026-11-06 replay was refused again, this time by a real conflict —
`git cherry-pick 9a98f616` conflicting in `guidelines/BacklogTooling.md`, `tools/loop-cases.sh`,
`tools/loop/loop.conf`, `tools/loop/run.sh`. Two of that run's ten decision slots went this way (#760, #788).

**The red case, on the base `ed509ee4`** (`bash tools/loop-selftest.sh --case
'run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable'`):

```
2026-11-06T11:48:55Z
run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable|   the refusal is recorded as the item's mechanism: expected 'unmergeable', got 'null'
FAIL run#anAdoptionTheTreeRefusesIsRecordedAsUnmergeable
loop-selftest: 1 of 1 cases failed
2026-11-06T11:48:59Z
```

`evidence/red-on-HEAD.log` is that run and `evidence/red-on-HEAD.xml` its tally (`tests=1 failures=1 errors=0
time=4`), kept beside it because the shell runner writes no JUnit XML of its own. The case drives a run whose
excluded branch passes every input `adopt-item` decides on and whose project tree carries an uncommitted file,
and it asserts what the pre-screen's `needs-decision` verdict currently hides: no `agent/needs-decision` write, no
close, the item's own record carrying `unmergeable` and the refusal's text, and the branch still the evidence.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, **added** — "An adoption the tree refuses is
recorded as `unmergeable`, not escalated" — because the requirement that owns the branch ("Version control
belongs to the run's script, not to the agent", `:306`) already draws the line this change keeps: a branch that is
*not admissible for adoption* "SHALL be refused and escalated as a decision" (`:313`), and this change says what an
admissible branch's own refusal is instead. Nothing in the shipped spec contradicts it, so no requirement is
modified and no shipped scenario is carried over.

**Owning guideline section:** `guidelines/Backlog.recipes.md`:586 "Refusals, records and the landing",
the paragraph that says what a branch the run may adopt carries (`:640`) — the adoption's two refusals are named
there beside the branch's evidence, and the run's own report line that prints them comes from `state.sh report`
and `report.sh`, which this change edits rather than re-states.

surface: 2 cases added, 0 rewritten, 0 fixtures realigned, 1 requirement added (3 scenarios) — counted with
`bash tools/loop-selftest.sh --list | wc -l` (330 on the change), `grep -c '^#### Scenario'
openspec/specs/agent-issue-loop/spec.md` (326) and `grep -c '^case_run_' tools/loop-cases.sh` (124) — 3 engine
sites changed (`run.sh`'s exclusion loop and its escalation predicate, `state.sh`'s new `adoption-refused` writer
and its excluded line, `report.sh`'s two decision predicates and its excluded line), 1 guideline section, and 1
record of `tools/loop-mutations.sh` (86.1, added for the new record; the base's highest id is 85.2).

## What Changes

1. **The refusal is the adoption's own result.** `adopt_finished_attempt` keeps what `adopt-item` printed in
   `adoption_refusal`, beside the `merge_refusal` the run's own merge already keeps, and
   `adopt_finished_exclusions` reads the status it used to discard.
2. **Only an admissible branch's refusal becomes `unmergeable`.** The exclusion loop asks
   `worktree.sh branch-state` what the branch is. `adoptable` means nothing on the branch refused it, so the
   refusal is the tree's, the base's or the replay's: the run records it with
   `state.sh adoption-refused <issue> --reason <the refusal>` as the item's mechanism `unmergeable` on its
   pre-screen exclusion — the verdict, the pre-screen's reason and the branch as evidence stay as they were.
   A branch the pre-screen read as the owner's for its own evidence (`archived-not-green`, `review-refused`,
   `review-unanswered`) keeps its decision exactly as before.
3. **No decision slot is spent, and the item is work.** Every predicate that reads a pre-screen exclusion as a
   question the owner owes — `escalate_prescreen_decisions` (`run.sh:272`), `report.sh`'s decision list (`:181`,
   `:325`) — skips an entry carrying `mechanism: unmergeable`. Nothing is recorded in `.items`, so the
   two-blocked streak is untouched, and nothing is closed: the branch stays where the next run's pre-screen
   finds it, and that run retries the adoption on a tree that may be clean. A pre-screen the run pays for again
   mid-run rewrites `.excluded` from the tracker and git, so `state.sh queue` carries the mechanism and the
   refusal over for the same issue: the branch it excluded twice is the same branch.
4. **The record is printed.** The report's "excluded before any work" line reads
   `#21 needs-decision (unmergeable)`, so a reader of the run's own output sees which exclusion the run decided
   for itself and which one it left for a person.

## What this does not do

- It does not touch the start-time path (`run.sh:1784`): an item that is already being picked has an item record,
  and a refusal there is a block that is escalated, as the run's own merge refusal is (`run.sh:2586`).
- It does not reclassify a branch: `branch-state`'s vocabulary and `adopt-item`'s refusals are unchanged, and no
  new refusal word is added to `worktree.sh`.
- It does not retry the adoption inside the run that refused it: the dirt that refused the merge is still there,
  so the retry is the next run's, exactly as it is for the item a dirty start leaves in the queue.
- It does not change the decision cap, the hold, the two-blocked streak or the `refused` mechanism.
