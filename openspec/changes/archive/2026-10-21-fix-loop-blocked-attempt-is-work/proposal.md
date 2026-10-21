# fix-loop-blocked-attempt-is-work

## Why

Measured on 2026-10-21 (issue #232, `class/bug`, `category/build-and-harness`, filed as #232 and measured on
#230): the run started at 05:46Z excluded an item whose branch carried an unmerged fix, with the verdict
`needs-decision` — reason `branch fix/issue-230-condition-marker-parse carries a commit the run's base does not
— a previous attempt's work is on it` — and one comment carrying nothing but that sentence. No question was
stated, the item was labelled `agent/needs-decision` and `status/on-hold`, and it stayed unworked although
`main` still had the defect (`git show 2e732f0:tools/sensors/lib.sh` holds `scan("condition:[ ]*[^>]*")`, the
branch holds `\{[^}]*\}`).

**Root cause, in two sentences.** The pre-screen counts *any* commit the base does not have as "a previous
attempt", so a branch that carries an unarchived, unfinished fix parks its item as an owner decision nobody
asked for — and the loop has no path that clears it, because it never deletes a branch. Second, `carried_change`
credits a branch with *any* archived change its history added, so the adoption refusal for #230 named
`2026-10-19-fix-i18n-gate-state-field-text`, a change added by `b14ab1f` — an unrelated #126 commit lying in the
branch — and refused it for a reviewer verdict about that other change.

The evidence, in the tree at base `2e732f0`:

- `tools/loop/prescreen.sh:233-243` turned every `previous_attempt_branch_of` hit into
  `verdict=$decision_verdict`, and `.pi/issue-loop/run.json` records the result:
  `230 needs-decision branch fix/issue-230-condition-marker-parse carries a commit the run's base does not`.
- `git diff --name-only --diff-filter=A 2e732f0..fix/issue-230-condition-marker-parse -- openspec/changes/archive`
  returns exactly one directory, `2026-10-19-fix-i18n-gate-state-field-text`, added by `b14ab1f` (`#126`) — so
  `carried_change` returned it as the branch's own change, and `cmd_adopt_item` refused with
  `… the reviewer refused 2026-10-19-fix-i18n-gate-state-field-text in round 3`.
- `git cherry main fix/issue-230-condition-marker-parse` lists 4 unlanded commits; the branch's own work is one
  code-only commit with no archived change.

**The three cases this change adds fail on HEAD `2e732f0`** (a shell gate has no JUnit XML; the case's own
message and exit status are the record — `evidence/red/`, produced by running this change's cases file against
`git archive HEAD tools`):

| case | red on HEAD | timestamp |
|---|---|---|
| `prescreen#aBlockedAttemptBranchIsWork` | `a branch carrying no archived change of its own is work, not a decision: expected 'eligible', got 'needs-decision'` (exit 1) | 2026-10-21T06:26:21Z |
| `worktree#setsAsideABlockedAttemptAndStartsFresh` | `a blocked attempt's branch does not park the item: loop: branch fix/issue-21-demo already exists and carries commits the base does not …: expected '0', got '5'` (exit 1) | 2026-10-21T06:26:23Z |
| `worktree#doesNotAdoptAChangeCreditedToAnotherIssue` | `a change credited to another issue does not make the branch adoptable: expected '5', got '0'` (exit 1) | 2026-10-21T06:26:24Z |

**The owning requirement and the owning rule.** Capability `agent-issue-loop`: this delta MODIFIES the
requirement "Version control belongs to the run's script, not to the agent" and two of its scenarios, because
that requirement is what said a branch without a finished change "SHALL be refused and escalated as a decision"
— the sentence the measurement contradicts. The rule the loop lives by is `.pi/skills/fix-loop/SKILL.md`,
§"Report (end of run)": "the branch lines are read from git by what each branch *carries*" — this change makes
the pre-screen read a branch the same way — and `REFERENCE.md`, §"Running the loop in a worktree", whose "the
branches stay until the run's commits are merged" now also names the `loop/leftover/` prefix a blocked attempt
is renamed to. The two are consistent after this change: the work is kept, under a name the run gives it, and
the item is worked.

**Why it matters.** Each leftover branch an interrupted or by-hand attempt leaves behind (exactly the shape
#229 produces) spends a decision slot for ever and its fix rots on a branch, so the tracker says "fixed-by"
nothing while the defect stays in `main`. And a refusal that names another item's change is worse than silence:
the person reading it is told to apply a reviewer's corrections to the wrong change.

## What Changes

- `tools/loop/worktree.sh` — `carried_change` takes the issue and credits a branch only with a change whose
  *adding* commit names `(#<issue>)`, the landing convention `end-item` writes (`fix: <slug> (#<issue>)`); the
  reviewer-refusal reader is factored out as `carried_review_refusal`; `branch_state_of` answers one word
  (`adoptable`, `review-refused`, `archived-not-green`, `unfinished`, `landed`, `absent`); a new read-only
  command `worktree.sh branch-state <issue> <branch>` exposes it; `start-item` renames a branch that is
  `unfinished` aside to `loop/leftover/issue-<n>-<slug>-<utc>` and starts the item on a fresh branch, and keeps
  refusing — with the same message `run.sh` matches — for a branch that carries an archived change.
- `tools/loop/prescreen.sh` — the previous-attempt verdict now reads `branch-state`: a blocked attempt leaves
  the item queued (with the branch as the record's evidence and a reason naming the set-aside), every other
  state keeps the decision. A branch the command cannot classify is the owner's, never work.
- `tools/loop/run.sh` — the refusal path comments corrected (only a finished attempt reaches it) and the start's
  `set-aside:` line is printed instead of swallowed when the start succeeds.
- `tools/loop-cases.sh` — two cases renamed and two added: `prescreen#aFinishedAttemptBranchIsADecision`,
  `prescreen#aBlockedAttemptBranchIsWork`, `worktree#setsAsideABlockedAttemptAndStartsFresh`,
  `worktree#doesNotAdoptAChangeCreditedToAnotherIssue`; `worktree#refusesABranchCarryingAPreviousAttempt` and
  `prescreen#escalatedItemKeepsItsBranchAsEvidence` now build a *finished* attempt (archived change, green XML,
  `(#n)` commit subject) because that is what the refusal and the decision now own;
  `run#branchThatAppearsWhileTheRunIsOpenIsRecorded` becomes `…IsSetAside`; the adopt fixtures carry `(#21)` in
  their commit subjects, as the loop's own commits do.
- `tools/loop-mutations.sh` — 33.1 (the credit) and 33.2 (the verdict), and 12.6, 12.7, 13.1 retargeted to the
  cases that now own their invariant.
- `.pi/skills/fix-loop/REFERENCE.md` — the branch paragraph names the `loop/leftover/issue-<n>-<slug>-<utc>`
  prefix a blocked attempt is renamed to, so a person reading the kept tree's branches knows what they are.
- `openspec/specs/agent-issue-loop/spec.md` — the requirement and two of its scenarios, restated below.

## Cases and evidence

- `prescreen#aFinishedAttemptBranchIsADecision` — a branch that adds one archived change of its own with a green
  verification record keeps its item a decision, named by the branch, recorded in the exclusion list; a branch
  with no commit of its own leaves its item eligible. (Unchanged behaviour, kept as the counter-case.)
- `prescreen#aBlockedAttemptBranchIsWork` — a branch with commits and no archived change of its own, whose only
  archive directory was added by another item's commit (`(#99)`), leaves the item queued, with the branch as
  the record's evidence and no exclusion written.
- `worktree#setsAsideABlockedAttemptAndStartsFresh` — a blocked attempt's branch is renamed to
  `loop/leftover/issue-21-*`, its commit is still reachable there, and the item's own branch is recreated from
  the run's base; the start exits 0.
- `worktree#doesNotAdoptAChangeCreditedToAnotherIssue` — `adopt-item` refuses a branch whose only archived
  change was added in another issue's commit, with the sentence that names what is missing, and merges nothing.
- `worktree#refusesABranchCarryingAPreviousAttempt` — a branch that adds an archived change of its own is still
  refused at the start, with the commits named, so the branch an owner wrote is never overwritten.

Green: `evidence/green/suite.txt` (all 131 cases), the five cases on this change, and
`evidence/green/mutations.txt` (`red-when-mutated: 5 · not-noticed: 0 · restore-failures: 0`).

## Impact

| file | module / area |
|---|---|
| `tools/loop/worktree.sh` | the branch rules: what a branch is credited with, what the start does with it |
| `tools/loop/prescreen.sh` | the verdict recorded before any item is picked |
| `tools/loop/run.sh` | the line the start prints, and the comment on the refusal path |
| `tools/loop-cases.sh` | the gate's cases (two renamed, two added, four fixtures aligned) |
| `tools/loop-mutations.sh` | two mutations added, three retargeted |
| `.pi/skills/fix-loop/REFERENCE.md` | the branch facts: the prefix a blocked attempt is kept under |
| `openspec/specs/agent-issue-loop/spec.md` | one requirement and two of its scenarios modified, one requirement added |

No Android module, resource, native source, JNI bridge, manifest, Gradle file or prompt skeleton is touched, so
the parity, native-build and configuration-portability rules do not arise: `branch_state_of` names no project
value, the new command reads only git and the run's own state, and `tools/check-portability.sh` reads the same
configuration as before. `guidelines/*.md` says nothing about the loop's branches (checked: the loop's rules
live in `.pi/skills/fix-loop/`), so no guideline is superseded.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **MODIFIED** the requirement "Version control belongs to the
  run's script, not to the agent", and within it the scenario "A branch that carries a previous attempt is
  refused, named and recorded" (now: a branch that carries a *finished* change) and "A finished attempt's
  branch is merged and its item closed" (now: the change must be the branch's own, by its adding commit).
  **ADDED** the requirement "A blocked attempt's branch is kept under a name of the run's own, and its item is
  worked" with its two scenarios. The other eight scenarios of the modified requirement keep their wording.

## Additive or breaking, and rollback

**Breaking for the documented verdict, additive for the tooling.** A branch that carries commits but no
archived change of its own no longer produces `needs-decision`; the item is worked and the branch is renamed
aside. Everything else is additive: a new read-only command, a new branch namespace (`loop/leftover/…`) that
belongs to no item's prefix, one extra printed line, and one extra argument to `carried_change`, whose only
callers are in `worktree.sh` and the new command.

Rollback: revert this commit. The pre-screen counts any commit again, `start-item` refuses such a branch again,
`carried_change` credits any archive directory again, and the four new cases fail as they did on `2e732f0`.
Branches under `loop/leftover/` need no repair: no loop path reads that prefix, and the commits stay
reachable.

## OpenSpec

- Change: `fix-loop-blocked-attempt-is-work`
- Issue: #232 (filed 2026-10-21 from the measurement of #230's skip)
