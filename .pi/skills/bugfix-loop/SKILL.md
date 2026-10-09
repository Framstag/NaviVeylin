---
name: bugfix-loop
description: Runs a budgeted, pre-authorized loop that closes bugs one at a time — triage `TODO.md`, prove the root cause with a test that is red on HEAD, create a `fix-*` OpenSpec change (proposal, specs, design, tasks), apply it, have it reviewed by a fresh independent child, archive it, update `TODO.md`, then take the next bug — and stops on a bug cap, a deadline, or a blocker. Use when asked to "run the bug fix loop", "fix bugs until <time>/<count>", "work the backlog in a loop", or when several bugs from a triage should be closed in one session without exhausting the context window.
---

# The bug-fix loop

One bug per turn, in this order:

```
triage → eligibility gate → root cause + red test → fix-* change (planning) → apply
      → independent review → verify + archive → TODO.md bookkeeping → next bug
```

The loop exists to make four properties enforceable instead of aspirational:

| Property | What enforces it here |
|---|---|
| High confidence in analysis and fix | the defect must be **reproduced by a test that fails on HEAD before the fix** (phase A); a fresh `reviewer` child must confirm each scenario before archive (phase D) |
| Unambiguous specification, no alternatives to weigh | the **eligibility gate** rejects any bug whose fix needs a choice; `design.md` records the eliminated alternatives as *eliminated by the root-cause evidence*, never as options for the user |
| Manageable implementation | one bug, one capability, one `fix-*` change; the gate rejects anything wider |
| All tests green | one forced both-flavor gate per change (`build-app` + `run-tests`), plus the revert-check for new invariants |

The loop is **pre-authorized** by the user's request: it may create changes, edit code, archive changes and repair
`TODO.md` metadata without asking per step. It may **not** exceed the budget, invent scope, or remove a `TODO.md`
entry the run was not allowed to remove.

## When to use

- "Fix bugs in a loop until <N bugs / M minutes>", "work the backlog", "run the bug fix loop"
- Several triaged bug entries should be closed in one session
- A session whose whole purpose is closing `TODO.md` bug entries

## When NOT to use

- The user named one bug → one normal `fix-*` change via `openspec-propose` (the loop adds overhead)
- Improvements/features (`class: improvement|feature`) → ordinary change flow; this loop is bugs only
- Cleaning `TODO.md` → `cleanup-todo`; auditing proof gaps → `openspec-proof-gap-audit`
- No `TODO.md` bug entry exists → triage first with `triage-todo` and report

## Step 0 — the budget (the only prompt of the run)

Ask **once**, in ONE `ask_user` call, and do not start before the answers exist:

| Input | Why it is mandatory |
|---|---|
| bug cap `N` | how many bugs this run may close |
| wall-clock budget `M` minutes | when the run must stop even if bugs remain |
| review `on/off` | `on` = one fresh `reviewer` child per change (costs time, buys the correctness property) |
| `TODO.md` removal allowed `yes/no` | whether an archived entry may be deleted or only marked `fixed-by` |

Then initialise the mechanical budget:

```bash
.pi/skills/bugfix-loop/scripts/loop-state.sh init --bugs N --minutes M --review on|off --allow-todo-removal yes|no
.pi/skills/bugfix-loop/scripts/loop-state.sh show
```

State: `.pi/bugfix-loop/run.json` (machine-local, gitignored). Never commit it, never hand-edit it.

## Roles — who does what (context-window discipline)

| Role | Who | Owns |
|---|---|---|
| Orchestrator | the current session | the budget, the candidate queue, the final report. Reads **only** child reports (≤ 40 lines) and mechanical checks (`git status`, `git diff --stat`, `openspec list`, the XML tallies). It never reads a change artifact, a guideline or a test file in full |
| Triage child | one `scout` child, `skill: ["triage-todo"]` | the ranked bug queue with citations |
| Iteration child | one `worker` child per bug, **fresh context** | phases A–F for exactly one bug and one change |
| Review child | one `reviewer` child per change, **fresh context**, read-only | PASS/FAIL per spec scenario, with quoted evidence |

One writer per working tree (the rule from `AGENTS.md`): never run two iteration children at once in this repo, and
never let the orchestrator edit the same files as a running child.

The JS blocks below state each child's **contract**. Use them either as a workflow script (`runs.run` / `runs.all`,
launched with `async: true`) or translated into the matching direct `subagent({agent, task, skill})` call — the
loop's guarantees come from the contract, not from the transport.

### The triage child

```js
await runs.run('triage', { agent: 'scout', skill: ['triage-todo'], task:
  'Run the triage-todo skill on TODO.md. Return ONLY the ranked bug segment (class: bug, open or unverified, '
  + 'not in-flight, not device-gated) as lines "§<id> | score | <category> | <one-line claim> | <file:line evidence>", '
  + 'highest score first, at most 8 lines, then one line "queue: §a §b §c"' })
```

Store it: `loop-state.sh triage 63 77 81 …`. Re-triage when the queue empties or after 3 iterations, and say so.

## Phase A — root cause and the red test (iteration child)

**Measure first, never fix from the symptom description** (the repo's own rule, `guidelines/Build.md` §2).

1. Verify the entry against the tree — the entry is a memory, not a fact. Confirm the condition still holds
   (`file:line`), and confirm the entry is not already fixed.
2. Name the root cause: one mechanism that explains the symptom, stated in one sentence.
3. **Produce the red test.** Write the smallest test that exercises the defect and show it failing on the current
   tree, quoting the JUnit XML `tests=/failures=`, the case name and the timestamp. A fix without a red-on-HEAD case
   is not evidence; if the defect class cannot be exercised on the host, that is a skip (phase B gate, condition 6),
   not a fix.
4. Name the owning specification requirement and the owning `guidelines/*.md` section (route with `AGENTS.md`
   "Documentation Map"; never read a guideline in full).

Deliverable of phase A: the evidence block that goes verbatim into `## Why` of `proposal.md`:

```
Root cause: <one sentence>
Evidence:    <test FQCN#case> fails on HEAD — XML tests="1" failures="1" (<message>), <timestamp>
Repro:       <command, if a test is not the fastest proof>
Spec:        <capability> / <requirement name>   Guideline: <file> §<n>
```

## Phase B — eligibility gate (strict skip)

Run the gate against the phase-A evidence. A candidate is eligible only if **all six** hold:

| # | Condition | Reject verdict |
|---|---|---|
| 1 | The claim is verified in the tree today (`file:line` or a run) | `stale entry` |
| 2 | One root cause explains the symptom | `needs diagnosis` |
| 3 | Exactly **one** fix follows from that root cause — no trade-off, no option to weigh | `needs decision` |
| 4 | The behaviour is a falsifiable WHEN/THEN that a named test case can assert | `unspecifiable` |
| 5 | It fits one capability delta and one `fix-*` change (bounded files) | `too wide` |
| 6 | Correctness is decidable on the host (device verification may remain a follow-up task) | `needs device` |

Strict rule: **on any reject, do not ask the user.** Record it and take the next candidate:

```bash
loop-state.sh done <not-yet-created-change> --result skipped --todo '§63' --note "needs decision: <what>"
```

Use the candidate's id in place of a change name for a pre-change skip. Optionally record the reason as
`status: on-hold <decision>` on the entry — metadata repair only, never a renumber, never a re-ranking.

If the whole queue is rejected, stop and report the reject verdicts (that is a finding about the backlog, not a
failure of the loop).

## Phase C — planning the `fix-*` change (iteration child)

Use the `openspec-propose` skill (it creates the change and every artifact, **planning only** — it must not touch
project code). Change name: `fix-<slug>`.

- `## Why` carries the phase-A evidence block verbatim. No speculation, no "seems".
- `specs/<capability>/spec.md` — the delta only, with `## MODIFIED`/`## ADDED` for the requirement the defect
  contradicts. Every scenario gets exactly one named test case (the case may be the red test from phase A).
  A scenario without a named case is a planning defect: fix the plan, do not "test later" it.
- `design.md` — always, even for a small fix. Root cause, the single fix, blast radius, rollback. For a `fix-*`
  change the alternatives are the ones the evidence **eliminated** (name them and why they are impossible); a list
  of viable options means gate condition 3 was wrong — stop and re-gate.
- `tasks.md` — ordered, each with its own verification, at minimum:
  1. the failing test that reproduces the defect (red on HEAD) — with the quoted XML of the red run
  2. the fix, minimal and scoped
  3. one task per spec scenario naming the case that exercises it
  4. a revert-check task **only** for an invariant the fix introduces beyond the original symptom
     (`revert-check` skill: one mutation, the named case must fail, restore, forced green)
  5. the forced both-flavor gate (`build-app`, `run-tests`)
  6. any device/measurement follow-up, stated as pending when no device exists — never implied as done
  7. `TODO.md` status update and any guideline section the fix supersedes
- `openspec validate --change <name>` and `openspec doctor` before apply.

## Phase D — apply (iteration child)

Use `openspec-apply-change`, and the repo skills for every build/test call: `build-app`, `run-tests`,
`revert-check`. Focused suites while iterating; **one** full both-flavor forced gate before "done"
(`-PforceTests --no-build-cache`, or `--rerun-tasks` when the change touched more than the test sources).
Never mark a task `[x]` on a cached or partial run; never mark it `[x]` while a failure is open.

## Phase E — independent review (review child, default on)

Before archive, spawn `reviewer` with fresh context. It must not be the writer.

```js
await runs.run('review', { agent: 'reviewer', task:
  'Read ONLY: openspec/changes/<name>/{proposal.md,specs/**,design.md,tasks.md}, `git diff <base>...HEAD`, and the '
  + 'JUnit XML of the gate runs. For each spec scenario: PASS or FAIL, and for PASS quote the case name + the '
  + 'assertion that proves it. For the root cause: does the diff fix the named cause and nothing else? List every '
  + 'claim in the artifacts that no evidence supports. Output: one line per scenario, then "UNPROVEN: …" lines, '
  + 'then "VERDICT: PASS|FAIL". Do not edit files.' })
```

- `PASS` → archive.
- `FAIL` → send the unproven claims back to the same change as a new `worker` task (fix + re-run the affected
  cases), then one more review. **Max 2 review rounds**; after that record the change `blocked`, leave it
  unarchived, and continue with the next bug.

## Phase F — verify, archive, bookkeeping (iteration child)

1. `openspec-verify-change` — CRITICAL issues must be zero; every skipped check is named, never silently passed.
2. `openspec-archive-change`. Confirm the directory: `ls -d openspec/changes/archive/*<name>*`.
3. `TODO.md`: set the entry to `**status:** fixed-by \`<change>\``. Delete the entry **only** when the run was
   started with `--allow-todo-removal yes` and the archive directory exists; otherwise report it as a removal
   candidate. Keep order, ids, separators; never renumber.
4. Close the iteration:

```bash
loop-state.sh done <change> --result done --todo '§63'
```

## Stop conditions

Stop, report, and spend no further budget when any holds — check with `loop-state.sh next` (exit 3 = stop):

- the bug cap is reached (`next` exit 3, "cap reached")
- the deadline passed (`next` exit 3, "deadline passed")
- the candidate queue is empty or entirely gated out
- two consecutive iterations ended `blocked`
- the user interrupts

A stopped clock is not a failed loop: report what closed, what was skipped and why, and the state file.

## Failure handling (never silently absorb)

| Situation | Action |
|---|---|
| Child reports `blocked` mid-change | record `--result blocked --note "<reason>"`, write a `ki_processing_failures.log` entry (timestamp, approach, problem), leave the change unarchived, next bug |
| The fix needs a decision or grows beyond one capability | re-gate; if it fails, abandon the change (`openspec status` → do not archive; say so explicitly in the report) |
| Tests red after 3 focused attempts | `blocked`, as above — do not weaken a test to make it green |
| A problem is found that is not this change's | append it to `TODO.md` as a new entry (append-only, new highest id) and continue |
| The gate needs a device that is absent | record the device task as pending in the report; the bug counts as closed only if host evidence proves correctness |

## Report (end of run)

```markdown
## Bug-fix loop — <date> (budget N bugs / M min, spent X bugs / Y min)

| § | change | root cause (one line) | red test | gate | review | TODO |
|---|---|---|---|---|---|---|
| 63 | fix-car-tile-cache | car path never configured the cache | TileCacheTest#cold... | tests=812 failures=0 | PASS | fixed-by |

Skipped: §77 needs decision (<what>) · §91 needs device (<what>)
Blocked: §102 (<what>, change left unarchived)
Open: §81 §85 …   Budget left: <bugs> bugs / <min> min
State: .pi/bugfix-loop/run.json
```

## Pitfalls

1. **Fixing before reproducing.** A green suite after the edit hides which defect was fixed; the red-on-HEAD case
   is the whole correctness argument.
2. **Treating "the fix is obvious" as gate condition 3.** Obviousness is not uniqueness: if two mechanisms produce
   the symptom, it is `needs diagnosis`.
3. **Asking the user inside the loop.** Only the run-start budget is a prompt. Strict skip replaces per-bug
   questions; a skip is cheap, a stalled loop is not.
4. **The orchestrator reading artifacts.** Context exhaustion is the failure mode this design removes; pass a
   worker report, not a change.
5. **Two writers in one tree.** One iteration child at a time, and the orchestrator edits nothing the child owns.
6. **Archiving on the writer's word.** The review child is read-only and independent on purpose; a `FAIL` with
   unproven claims is a valid outcome.
7. **`openspec-propose` writing code.** It is a planning workflow; apply is a separate, explicit phase.
8. **Deleting a `TODO.md` entry the run was not allowed to remove** — mark `fixed-by` and propose the removal.
9. **Letting the loop exceed the budget because "one more bug".** The cap and the deadline are the user's
   instruction, not a suggestion; `loop-state.sh next` is the authority.
