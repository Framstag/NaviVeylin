# Rule inventory — `fix-loop` skill, BEFORE the change

One line per rule, taken from `.pi/skills/fix-loop/SKILL.md` as committed in `0444fc6` (479 lines, the
byte-identical rename of `bugfix-loop`). Section names are the document's own `## ` headings at that commit.

Purpose: the restructure of `widen-fix-loop` must lose no rule. The AFTER inventory
(`skill-rule-inventory-after.md`) maps every ID below to the section that states it now.

## What the loop guarantees

- **G1** confident analysis and fix — the defect is reproduced by a test that is red on HEAD before the fix
- **G2** unambiguous specification — gate condition 3; `design.md` records eliminated alternatives only
- **G3** manageable implementation — gate condition 5, one production file
- **G4** all tests green — one forced both-flavor gate per change shape, one revert-check per new invariant

## Cost of one iteration (measured)

- **C1** archived change 40–65 min; gate 5–7 min, review 5–10 min, phase F 5 min
- **C2** `gated-out` skip 10–15 min with the cheap-first rule, 20–25 min without
- **C3** cold loop worktree first build 11 min; a single test class afterwards 1–3 min
- **C4** the forced both-flavor gate costs 4m42s–6m41s and overwrites the previous XML evidence

## Scope

- **S1** "When to use": a loop request, several triaged bugs in one session
- **S2** "When NOT to use": a single named bug → ordinary `fix-*` change; improvements and features → ordinary
  flow (bugs only); `TODO.md` cleaning → `cleanup-todo`; proof gaps → `openspec-proof-gap-audit`; no entry →
  `triage-todo` first

## Step 0 — the budget

- **B1** ask once, in ONE `ask_user` call; start nothing before the answers exist
- **B2** four mandatory inputs: bug cap `N`, wall-clock `M`, review `on/off`, `TODO.md` removal `yes/no`
- **B3** `loop-state.sh init` sets the run; state at `.pi/bugfix-loop/run.json`, machine-local, gitignored,
  never committed, never hand-edited

## Step 0b — pre-screen the WHOLE backlog

- **P1** one pass over the whole backlog, not the top N (run 1: 5 of 8 top-scored were ineligible)
- **P2** the scout enumerates every `class: bug` entry with `status: open|unverified`, carrying class,
  category and a per-candidate verdict
- **P3** conditions 1–6 are applied during ranking; everything that fails is dropped; each rejection is
  reported with the failed condition and one clause of evidence
- **P4** the queue holds survivors only; a new distinct defect is filed as an append-only new `TODO.md` entry
  with a cross-reference
- **P5** two triages per run, maximum; a third pass over a known answer is budget burned

## Roles

- **R1** orchestrator: budget, queue, final report; reads only child reports (≤ 40 lines) and mechanical
  checks; never reads a change artifact, guideline or test file in full
- **R2** triage child: one `scout`, the full pre-screen and the ranked queue
- **R3** iteration child: one `worker` per bug, fresh context, phases A–D
- **R4** review child: one `reviewer` per change, fresh context, read-only
- **R5** one writer per working tree; never two iteration children; the orchestrator writes `TODO.md`,
  `config` or skill files only while no child is running
- **R6** the JS blocks state each child's contract; guarantees come from the contract, not the transport

## Progress tracking

- **T1** `loop-state.sh` owns the run state and is its only writer
- **T2** record the pre-screen as a step before the queue is stored
- **T3** record each phase as it ends
- **T4** run `show` before each iteration and paste its `steps` block into the report
- **T5** a last step still `running` means the iteration is mid-flight — do not start another
- **T6** `<phase>` ∈ `prescreen A B C D E F`

## Running the loop in a worktree

- **W1** run in its own worktree when the main tree carries another change's WIP
- **W2** the worktree must live inside the repo (a sibling path is refused) — put it in gitignored
  `.pi/worktrees`
- **W3** the submodule must be checked out (`submodule update --init`)
- **W4** copy machine-local inputs (`local.prop*`, `VCPKG_ROOT`); never symlink `vcpkg`
- **W5** copy the gitignored skills the worktree lacks
- **W6** give the children `cwd=$W` so the state file resolves
- **W7** `openspec/changes/*` is gitignored — verify an in-flight claim against the main tree
- **W8** allocate new §ids from the max over both trees
- **W9** the orchestrator owns the skill in the repo copy: edit there, copy into the worktree
- **W10** keep the worktree; remove with `git worktree remove`; delete the branch only when merged

## Phase A

- **A1** measure first, never fix from the symptom description
- **A2** verify the entry against the tree — the entry is a memory, not a fact
- **A3** name one root cause in one sentence
- **A4** produce the red test, failing on HEAD; record XML `tests=/failures=`, case name, timestamp
- **A5** doc-vs-code divergence: the red case is a conformance case; the revert-check mutates the code
  constant, not the test
- **A6** name the owning requirement and guideline section; route via `AGENTS.md`; never read a guideline in
  full
- **A7** record `step A root-cause` and `step A red-test`
- **A8** the evidence block (Root cause / Evidence / Repro / Spec) goes verbatim into `## Why`
- **A9** retain the red run's evidence immediately — the next Gradle run overwrites it

## Evidence pointers

- **E1** copy the cited XMLs into `<change>/evidence/` before archive, and immediately for the red and
  mutation runs
- **E2** quote the JUnit XML, not the Gradle log
- **E3** copy the red/mutation XML out right after the run, or quote its numbers verbatim before the next run
- **E4** make the case print what it measures (`println` → XML `system-out`)
- **E5** never point at a `/tmp` log
- **E6** a row you cannot reproduce is labelled "not retained", with how it was obtained
- **E7** re-check every pair mechanically before archive
- **E8** `evidence-check.sh` resolves `file:line`, tallies, `/tmp` pointers, `§N` against `TODO.md` and the
  absence of `REVERT-CHECK MUTATION`; exits non-zero on a dangling claim; runs in C/D before the gate and
  before archive
- **E9** two modes — live (strict) and archived (report-only for `§N` and tallies, strict for structure)
- **E10** its own `--selftest` proves each check can go red

## Phase B

- **B4** six conditions; a candidate is eligible only if all hold
- **B5** on any reject do not ask the user: record and take the next candidate
- **B6** leave the entry honest — decision → `on-hold`, device → `open (device)`, refuted → `fixed-by`,
  mismatched class → reclassify + `**Loop verdict**` bullet
- **B7** never renumber, never re-rank, never delete on a skip; file anything uncovered as a new entry
- **B8** reporting the verdicts is loop output

## Phase C

- **C5** use `openspec-propose`, planning only, it must not touch project code; change name `fix-<slug>`
- **C6** `## Why` carries the phase-A evidence block verbatim
- **C7** the spec delta is the delta only; every scenario names exactly one test case; a scenario with no case
  is a planning defect; a MODIFIED requirement the fix does not deliver keeps the baseline's claim strength
  and says so
- **C8** `design.md` always: root cause, single fix, blast radius, rollback, eliminated alternatives; viable
  options mean condition 3 was wrong → stop and re-gate
- **C9** `tasks.md` ordered, each with its own verification: red case with XML, the fix, one task per scenario,
  revert-check only for a new invariant, the forced gate, pending device follow-ups, `TODO.md` status
- **C10** before the gate, grep the suite for the constants the change moves and plan those updates
- **C11** new `TODO.md` ids come from the max over both trees when a parallel branch exists

## Phase D

- **D1** `openspec-apply-change` plus `build-app`, `run-tests`, `revert-check`; focused suites while iterating
- **D2** `-PforceTests --no-build-cache` when only test sources changed, `--rerun-tasks` when more
- **D3** do not re-run the gate to "refresh" evidence; copy the cited XMLs out first if a re-run is forced
- **D4** never mark a task `[x]` on a cached or partial run, or while a failure is open
- **D5** attribute the known `MapCanvasViewModelModeTest` flake; do not "pass on re-run"
- **D6** record `step D apply` with the gate tally

## Phase E

- **E11** spawn `reviewer` with fresh context; it must not be the writer; the contract is fixed
- **E12** `PASS` → archive; `FAIL` → same change as a new task, one more review; max 2 rounds, then `blocked`
- **E13** the subtractive-round rule — a strictly subtractive round-2 failure needs no third review; anything
  adding a claim, case or assertion does; new overreach means `blocked`
- **E14** record `step E review`

## Phase F

- **F1** `openspec-verify-change`; CRITICAL issues zero or explained; name every skipped check
- **F2** copy the cited XMLs into `<change>/evidence/`, then run `evidence-check.sh` live
- **F3** `openspec-archive-change`; confirm the delta merged (no delta headers); re-run `evidence-check.sh` in
  archived mode and quote the drift count
- **F4** `TODO.md`: entry → `fixed-by`; update the Clusters index; delete only when removal was allowed and the
  archive exists, otherwise report a candidate; re-run the structure checks
- **F5** `loop-state.sh done <change> --result done --id §N`
- **F6** commit `fix: <slug> (§N)` and the archive commit; do not push unless asked

## Stop conditions

- **X1** check `loop-state.sh next` (exit 3) before starting an iteration
- **X2** stop on: cap reached / deadline passed; queue empty or all gated out (re-triage once, then stop on
  `eligible: none`); two consecutive `blocked`; less time left than the next iteration costs; user interrupt
- **X3** never start an iteration you cannot finish (under ~45 min: only with a named host seam)
- **X4** a stopped clock is not a failed loop — report what closed, what was skipped and why, the yield, the
  state file

## Failure handling

- **H1** child `blocked` mid-change → record, write a `ki_processing_failures.log` entry, leave unarchived,
  next bug
- **H2** the fix needs a decision or grows beyond one capability → re-gate; on fail abandon the change and say
  so
- **H3** tests red after 3 focused attempts → `blocked`; never weaken a test
- **H4** a defect found that is not this change's → append a new `TODO.md` entry and continue
- **H5** the gate needs an absent device → pending task in the report, residue filed; closed only on host
  evidence

## Report

- **RP1** the report is `loop-state.sh report` verbatim, plus a header table
  (`§ | verdict | change | root cause | red test | gate | review | TODO`) and closing lines (skipped list, new
  entries, decisions owed, budget left, state path)

## Pitfalls (15 items)

- **PT1** fixing before reproducing
- **PT2** treating "the fix is obvious" as condition 3
- **PT3** asking the user inside the loop
- **PT4** the orchestrator reading artifacts
- **PT5** two writers in one tree
- **PT6** archiving on the writer's word
- **PT7** `openspec-propose` writing code
- **PT8** deleting a `TODO.md` entry the run was not allowed to remove
- **PT9** exceeding the budget because "one more bug"
- **PT10** a tall, convincing artifact with one false pointer
- **PT11** reading a round-2 `FAIL` as "the fix is wrong"
- **PT12** a mis-scoped candidate: the loop is bugs-only
- **PT13** starting an iteration the clock cannot finish
- **PT14** forgetting the step log
- **PT15** allocating `§ids` from one tree only
