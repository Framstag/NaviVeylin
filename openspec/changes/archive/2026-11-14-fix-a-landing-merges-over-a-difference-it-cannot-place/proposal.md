# Proposal — a landing merges over a difference it cannot place

## Why

**Observed.** `cmd_adopt_item` and `cmd_merge_item` refuse to merge over any `git status --porcelain` line, with no
attribution clause (`tools/loop/worktree.sh:709` and `:752` as this change leaves them, both reading
`landing_refuses_the_dirt`), while `run.sh`'s own `outside_write_decides()` (`tools/loop/run.sh:1377`, issue #286)
states that a difference the run can tie to no child of its own is recorded and **decides nothing**. Two rules are
therefore applied to one tree, and the stricter one is applied where the most work has already been paid for: an item
that is green, red-tested and reviewed is dropped for a path its own children never touched, and the work is left on a
branch for a person.

**Evidence.** Run `2026-11-11T20:36:10Z`, item #859 — `stopReason: unmergeable`, "the project tree is not clean —
refusing to merge fix/issue-859-… onto the base: M tools/loop/loop.conf, ?? docs/2026-11-11-thinking-level-model-analysis.md,
?? tools/bench/thinking_probe.py". The same run's `outside-write` step had already written
`LOOP-OUTSIDE-WRITE-ALIVE: … attributed=none` into `.pi/issue-loop/items/859/worker-report.md.outside-write`, so the
run knew the dirt was not its own. That item's price: 9 840 445 tokens, 22 m 19 s wall, gate
`tests=245 failures=0 errors=0` (that gate's tally is quoted from the work package, not retained as a file), review
round 1 PASS, 5 commits the base does not have, 0 items closed in the run.

**Scope.** 5 of the 145 archived states carry `stopReason: unmergeable` (against 6 `bound`, 8 `refused`, 104
unclassified of 123 blocked items). This is issue #862's count, quoted from the item; no command in this change's
record produces it.

**Same class, later.** The next four starts refused in four minutes — `2026-11-12T05:28:51Z` (`.agents/`),
`05:29:07Z` (`.agents/`), `05:31:07Z` (`.gitignore`) — 0 items each, 3 refusals in 136 s.

## The red test

`evidence/red-on-HEAD.log` (2026-11-13T23:25:24Z), the three cases as this change leaves them run against
`run.sh` and `worktree.sh` taken from the base commit `0848c35dee76123bc3ec8d7221c16bcf7fa0bf2c`
(`git show HEAD:<path>`): **tests=3 failures=3**, no JUnit XML because `loop-selftest` is shell.

```
FAIL run#mergesOverADifferenceItCannotPlace
  a difference the run placed in nothing of its own does not end the item at its landing: expected 'done', got 'blocked'
FAIL run#anAdoptionLandsOverADifferenceItCannotPlace
FAIL worktree#theLandingDecidesByTheReadingItIsHanded
  a difference the run placed in nothing does not stop the landing: expected '0', got '5'
```

## Owning requirement

`openspec/specs/agent-issue-loop/spec.md`. Two requirements are MODIFIED, because the rule is one rule stated where
each half of it is read: **"Each item is worked by a fresh process, reviewed by a different one"** (`:448`), whose
scenario "A difference the run cannot attribute does not decide the item" (`:477`) said the landing rule refuses a
base that is still not clean, and now says the landing is handed the same recorded reading; and **"The item is closed
on a base that carries its fix"** (`:1502`), whose body and scenarios now state which differences the two merges
refuse and which they merge over. Two `**Case**:` citations outside those blocks named cases this change folds away
and are re-pointed in the spec directly (`:2532` → `state#aBlockedItemNamesTheMechanismThatStoppedIt`, `:3751` →
`run#anAdoptionLandsOverADifferenceItCannotPlace`); no scenario is added or removed for them.

## Owning guideline sections

`guidelines/Backlog.recipes.md` section 7a "Running one item: the script, the child and the package" (`:256`) carried
"What is not relaxed is the landing rule", which this change replaces with the rule itself; and section 7d "Refusals,
records and the landing" (`:1166`) states what the dirt is, the three case names and the mutation, beside the paragraph
that already says why a refused landing blocks an item.

## The single fix

The reading the run already takes travels with the merge call, and the two merges decide the dirt by it instead of
re-judging the tree:

- `worktree.sh`: `landing_args()` (`:629`) reads `--outside-write-decides yes|no` and `--outside-write-note <note>`;
  `landing_refuses_the_dirt()` (`:651`) prints `LOOP-OUTSIDE-WRITE: the landing merges over a difference no process of
  this run was tied to: <paths> — <the reading>` and returns 0 when the reading says `no`, and keeps the existing
  refusal (exit 5) when it says `yes` or when the call carries no reading at all. Both merges call it where they read
  the tree (`:709`, `:752`), and both accept the two options.
- `run.sh`: `merge_committed_branch()` hands `merge-item` the readings the item's own phases took — the worker's first,
  then the gate's, then the last outside-write note — and `yes` where either phase placed the difference or where the
  run was started `--strict-outside-write yes`; the line the merge prints becomes the item's `outside-write-landing`
  step record (`:507`). `adopt_finished_attempt()` hands `adopt-item` `no` with the sentence that the pre-flight read
  the tree clean and no child of this run has started since, or `yes` under `--strict-outside-write yes`.

No state field, no new command and no change to `create` (which still refuses a dirty tree before a worktree exists):
the landing is the only place the two rules disagreed.

surface: 3 cases added to the delta — 2 rewritten in place, 1 new — 1 case folded into a rewritten one, 2 production
files (`tools/loop/run.sh`, `tools/loop/worktree.sh`), 1 spec, 1 guideline, 1 skill example and 2 mutation records
re-pointed, `7 files changed, 315 insertions(+), 118 deletions(-)` — from
`git diff --stat $(git merge-base main HEAD)...HEAD` on the item's branch, and the case count from
`bash tools/loop/tests/loop-selftest.sh --list` (`427 cases`) and the delta's `LOOP-MEASURE-CHANGE … scenarios=21
cases=21 unnamed=0` (`evidence/measure.log`).