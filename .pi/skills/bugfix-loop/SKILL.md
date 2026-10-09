---
name: bugfix-loop
description: Run a budgeted, pre-authorized loop that closes bugs one at a time. Each bug: pre-screen the backlog, prove the root cause with a test that is red on HEAD, create a `fix-*` OpenSpec change, apply it, get an independent review, archive it, update `TODO.md`, take the next bug. Stops at a bug cap, a deadline, or a blocker. Use when asked to "run the bug fix loop", "fix bugs until <time>/<count>", "work the backlog in a loop", or when several triaged bugs should be closed in one session without exhausting the context window.
---

# bugfix-loop

Closes triaged bugs one at a time, on a pre-authorized budget.

One bug per iteration. The orchestrator (this session) reads no artifacts. Each child runs with fresh
context, so the loop survives a long run without exhausting the window.

## The loop in one picture

```
pre-screen the whole backlog → eligibility gate → root cause + red test → fix-* change
  → apply → independent review → verify + archive → TODO.md bookkeeping → next bug
```

## What the loop guarantees

Four properties. Each is enforced by a mechanism, not by intention. The measurements are from the first
run (2026-10-09: 12 candidates, 3 archived, 9 gated out).

| Property | Mechanism (and its measurement) |
|---|---|
| Confident analysis and fix | The defect must be **reproduced by a test that is red on HEAD before the fix** (phase A — all 3 archived changes had one, e.g. `42.67 dp < 48 dp`). A fresh `reviewer` child must confirm each scenario before archive (4 review rounds; both FAIL rounds were about claims and pointers, never about the fix) |
| Unambiguous specification, no alternatives to weigh | Gate condition 3 rejected 5 of the 9 gated candidates for exactly this reason (`needs decision`: an owner decision, unspecified semantics, three competing fixes). `design.md` records eliminated alternatives, never viable options |
| Manageable implementation | Gate condition 5 — each of the 3 archived changes touched **one** production file |
| All tests green | One forced both-flavor gate per change shape (measured 4m42s–6m41s, e.g. `mobile 1752/0 · automotive 1752/0 · 20 executed · FROM-CACHE 0`), plus one revert-check per new invariant |

The loop is **pre-authorized** by the user's request. It may create changes, edit code, archive changes and
repair `TODO.md` metadata without asking per step. It may **not** exceed the budget, invent scope, or
delete a `TODO.md` entry the run was not allowed to delete.

## Cost of one iteration (measured — plan with these numbers)

| Outcome | Cost | Notes |
|---|---|---|
| archived change | 40–65 min | gate 5–7 min, review 5–10 min, phase F 5 min. §138 took 65 min because it needed review round 2 |
| `gated-out` skip | 10–15 min **with** the cheap-first rule, 20–25 min without | The gate is 3 greps and 1 file read. Never analyse before it passes |
| cold loop worktree, first build | 11 min | One-time. After that a single test class is 1–3 min |
| forced both-flavor gate | 4m42s–6m41s | **It overwrites the XML evidence of the previous run** — see "Evidence pointers" |

Hard rule: **never start an iteration you cannot finish.** With less than ~45 min left, take the next
candidate only if the pre-screen named its host seam. Otherwise stop and report. Two thirds of the first
run's candidates were skips, so a budget that ignores skip cost plans for the wrong shape.

## When to use

- "Fix bugs in a loop until <N bugs / M minutes>", "work the backlog", "run the bug fix loop"
- Several triaged bug entries should be closed in one session
- A session whose whole purpose is closing `TODO.md` bug entries

## When NOT to use

- The user named one bug → one normal `fix-*` change via `openspec-propose`. The loop adds overhead.
- Improvements or features (`class: improvement|feature`) → the ordinary change flow. This loop is **bugs only**.
- Cleaning `TODO.md` → `cleanup-todo`. Auditing proof gaps → `openspec-proof-gap-audit`.
- No `TODO.md` bug entry exists → triage first with `triage-todo`, then report.

## Step 0 — ask for the budget (the only prompt of the run)

Ask **once**, in ONE `ask_user` call. Start nothing before the answers exist.

| Input | Why it is mandatory |
|---|---|
| bug cap `N` | How many bugs this run may close |
| wall-clock budget `M` minutes | When the run must stop even if bugs remain |
| review `on/off` | `on` = one fresh `reviewer` child per change (costs time, buys the correctness property) |
| `TODO.md` removal allowed `yes/no` | Whether an archived entry may be deleted or only marked `fixed-by` |

```bash
.pi/skills/bugfix-loop/scripts/loop-state.sh init --bugs N --minutes M --review on|off --allow-todo-removal yes|no
.pi/skills/bugfix-loop/scripts/loop-state.sh show
```

State: `.pi/bugfix-loop/run.json`. It is machine-local and gitignored. Never commit it, never hand-edit it.

## Step 0b — pre-screen the WHOLE bug backlog (one pass, not eight)

The first run triaged the top 8 by score. It then spent 8 iterations discovering that 5 of them were not
eligible. The third pass checked **all 31 `class: bug` entries** instead and concluded in minutes that none
was left. So build the queue once, from a full pass. The iteration child only *re-confirms* the verdict.

1. The triage child (`scout`, `skill: ["triage-todo"]`) enumerates **every** `class: bug` entry with
   `status: open|unverified` — not the top N. It carries the class, the category from the file's own
   vocabulary, and a per-candidate gate verdict (`eligible: yes/no (<first condition it fails>)`).
2. Conditions 1–6 (below) are applied during ranking. The scout sees `TODO.md`, cites `file:line`, and
   drops everything that fails: a decision, a stale claim, a device-only observable, an already-shipped
   fix, a non-`bug` class.
3. The queue holds **survivors only**. Every rejection is reported with the condition that failed and one
   clause of evidence. When the pre-screen uncovers a distinct defect, file it as a new `TODO.md` entry
   (append-only, new highest id, cross-reference instead of duplicating).
4. **Two triages per run, maximum.** The second one is for a queue that emptied mid-run. A third pass over
   a file that already answered "eligible: none" is budget burned on a known answer.

## Roles — who does what (context-window discipline)

| Role | Who | Owns |
|---|---|---|
| Orchestrator | the current session | The budget, the candidate queue, the final report. Reads **only** child reports (≤ 40 lines) and mechanical checks (`git status`, `git diff --stat`, `openspec list`, the XML tallies, `evidence-check.sh`). It never reads a change artifact, a guideline or a test file in full |
| Triage child | one `scout` child, `skill: ["triage-todo"]` | The full pre-screen (step 0b) and the ranked survivor queue |
| Iteration child | one `worker` child per bug, **fresh context** | Phases A–D for exactly one bug and one change |
| Review child | one `reviewer` child per change, **fresh context**, read-only | The fixed review contract (phase E) |

One writer per working tree. Never run two iteration children at once. The orchestrator writes `TODO.md`,
`config` or skill files **only while no child is running** — a bookkeeping edit during a child's `git add -A`
is committed under the child's message.

The JS blocks below state each child's **contract**. Use them as a workflow script (`runs.run`, `async: true`)
or as a direct `subagent({agent, task, skill})` call. The guarantees come from the contract, not the transport.

### The triage child (full pre-screen)

```js
await runs.run('triage', { agent: 'scout', skill: ['triage-todo'], task:
  'Run the triage-todo skill on TODO.md. Cover EVERY class:bug entry (not the top N) with status open or '
  + 'unverified; drop improvement/feature (out of the loop scope however urgent) and anything in-flight, '
  + 'on-hold, fixed-by or device-gated. Output one line per surviving candidate: '
  + '"§<id> | score | <category from the file vocabulary> | <one-line claim> | <file:line evidence> | eligible: yes", '
  + 'then "queue: §a §b §c", then up to 5 lines "rejected: §<id> — <condition number + one clause of evidence>", '
  + 'then "visited: <checked>/<total bug entries>". Apply the six conditions while ranking: (1) the claim is '
  + 'verified in the tree today; (2) one named root cause; (3) exactly one fix — no trade-off, no product '
  + 'decision, no already-shipped fix, no reversal of a recorded owner decision; (4) a falsifiable WHEN/THEN '
  + 'that is FALSE on HEAD; (5) one capability, one fix-* change; (6) a host test can decide it.' })
```

Measured value: without the pre-screen 5 of 8 candidates were ineligible. With it, 4 of 8 survived and the
second pass over all 31 bug entries returned none. Each rejection took ~10 minutes with a citation, instead
of a 40-minute analysis.

## Progress tracking — always know where the loop is

`loop-state.sh` owns the run state and is its only writer. Record every phase boundary as a step, so the
report shows what happened and at which phase the run is now.

```bash
S=.pi/skills/bugfix-loop/scripts/loop-state.sh
$S show                      # which issue, which phase, result of every step so far
$S next                      # next candidate; exit 3 when the queue or the budget is spent
$S start fix-<slug> --id §N  # begin one iteration at phase A
$S step <phase> --name <step> --status running|ok|fail|skip --note "<one clause>"
$S done <change> --result done|skipped|blocked [--id §N] [--verdict V] [--note TEXT]
$S report                    # per-iteration table plus every iteration's steps
```

`<phase>` is one of `prescreen A B C D E F`.

`$S show` prints:

```
bugfix-loop — §138 fix-pinned-band-height · phase D
budget   2 bugs / 120 min · 1 left · deadline 2026-10-09T18:20:00Z · 78 min left
review   on · todo-removal no
progress closed 1 · skipped 0 · blocked 0 of 2
queue    2: 122 130
steps
  A  start        · ok      · -
  A  root-cause   · ok      · one constant vs the font-scale band
  A  red-test     · ok      · BandScaleTest#fs2: tests=1 failures=1
  B  gate         · ok      · 6/6 conditions hold
  C  propose      · ok      · fix-pinned-band-height
  D  apply        · running · -
```

When no iteration is running, `show` falls back to the last one and adds
`· last: §138 fix-pinned-band-height (closed)` to the first line.

Rules:

- Record the pre-screen as `step prescreen --name <step> ...` before the queue is stored.
- Record each phase as it ends: `step A --name red-test --status ok --note "tests=1 failures=1"`.
- Run `$S show` before each iteration and paste its `steps` block into the run report.
- If the last step is still `running`, the iteration is mid-flight — do not start another.

## Running the loop in a worktree (recipe)

Run the loop in its own worktree whenever the main tree carries another change's WIP. Otherwise the loop's
`git add -A` and OpenSpec archive mix with it.

```bash
# 1. policy: a sibling path outside the repo is refused, so put the worktree inside the gitignored .pi/
cd <repo> && mkdir -p .pi/worktrees
git worktree add .pi/worktrees/loop -b bugfix-loop-<date>
W=.pi/worktrees/loop

# 2. the submodule must be checked out (its objects are already in .git/modules, so this is offline)
git -C $W submodule update --init app/src/main/cpp/libosmscout

# 3. machine-local inputs the worktree does not inherit
cp local.prop* $W/                      # SDK/NDK/Java paths; `local.prop*` also dodges the path-read policy
export VCPKG_ROOT=<repo>/vcpkg          # do NOT symlink it: a symlink named vcpkg is untracked and pollutes status

# 4. the gitignored skills the worktree does not have (open changes and the openspec-* skills are local WIP)
cp -a .pi/skills/openspec-* $W/.pi/skills/
mkdir -p $W/.pi/skills && cp -a .pi/skills/bugfix-loop $W/.pi/skills/

# 5. give the children cwd=$W; the state file then resolves to $W/.pi/bugfix-loop/run.json automatically
```

Four facts shape verification in a worktree:

- `openspec/changes/*/` is **gitignored** (only `archive/` is versioned). The worktree therefore sees **no
  in-flight changes**. Verify an `in-flight` claim against the **main tree's** `openspec list --json`.
  Remember that archiving is what makes a change's artifacts versioned on the loop branch.
- The worktree's `TODO.md` is the branch's copy. When the main tree has another change's WIP in the same
  file, **allocate new §ids from the max over both trees**. Otherwise the merge produces two sections per
  id (measured: main's WIP owned §155–§164 while the loop filed §154–§158, so `cleanup-todo` had to
  renumber the loop's five to §165–§169).
- The loop owns the branch. The orchestrator owns the skill in the **repo** copy. Edit the repo copy, copy
  it into the worktree, and commit it with the next iteration commit — otherwise the branch's children read
  stale rules.
- Keep the worktree after the run for the next loop. Remove it with `git worktree remove`, and delete the
  branch only when the run's commits are merged.

## Phase A — root cause and the red test (iteration child)

**Measure first, never fix from the symptom description** (`guidelines/Build.md` §2).

1. Verify the entry against the tree — the entry is a memory, not a fact. Confirm the condition still holds
   (`file:line`), and that the defect is not already fixed.
2. Name the root cause: one mechanism that explains the symptom, in one sentence.
3. **Produce the red test** — the smallest case that exercises the defect, failing on HEAD. Record the
   JUnit XML `tests=/failures=`, the case name and the timestamp.
   For a **doc-vs-code divergence** (the spec is the wrong side) the red case is a conformance case that
   reads the spec text and compares it to the code's constants — precedent: `FavAutoZoomClampRangeTest`,
   `MapMenuBackOrderComposeTest`. Its revert-check mutates the code constant, not the test.
4. Name the owning specification requirement and the owning `guidelines/*.md` section. Route with `AGENTS.md`
   "Documentation Map". Never read a guideline in full.

Record `step A --name root-cause --status ok` and `step A --name red-test --status ok` with the XML tally.

Deliverable — the evidence block that goes verbatim into `## Why` of `proposal.md`:

```
Root cause: <one sentence>
Evidence:    <test FQCN#case> fails on HEAD — XML tests="1" failures="1" (<message>), <timestamp>
Repro:       <command, if a test is not the fastest proof>
Spec:        <capability> / <requirement name>   Guideline: <file> §<n>
```

**Retain the red run's evidence immediately** — see the next section. The very next Gradle run overwrites it.

## Evidence pointers — what a quoted number must point at

Both review rounds that failed in the first run failed on this class, and both times the *fix* was sound.
This is a loop invariant, not a style note: **every number, tally or timestamp quoted in an artifact must
name an artifact that actually carries it, and that artifact must still say the same thing when the change
is archived.**

| Rule | Why (the case that proved it) |
|---|---|
| **Copy the cited XMLs into `<change>/evidence/` before archive** (and immediately for the red and mutation runs) | The archive is the only versioned part of a change. A cited XML that stays in `app/build/test-results/` is overwritten by the next run: all three changes of the first run now report `DRIFT C2 … carried by no XML on disk today`, and only a quoted number in the artifact survives |
| Quote the JUnit XML, not the Gradle log, for test-derived numbers | Gradle prints no test stdout, so a `BandGeometry` line was attributed to a `/tmp/loop-*.log` that never contained it |
| **Copy the red/mutation XML out of `app/build/test-results/` right after the run** (to the change dir or a task note), or quote its numbers verbatim in `tasks.md` **before the next run** | The next gate overwrote it twice, leaving two `not retained` rows whose numbers no reviewer could check |
| Make the case print what it measures (`println` → XML `system-out`) | The after-fix geometry then survives the run that produced it and is re-checkable by a reader |
| Never point at a `/tmp` log | The next run overwrites it — a quoted focus tally turned into `BUILD SUCCESSFUL in 2s … FROM-CACHE` under the same path |
| A row you can no longer reproduce is labelled **not retained**, with how it was obtained | An honest prose record beats a dangling pointer |
| Re-check every pair mechanically before archive | `scripts/evidence-check.sh <change-dir>` — `grep` each quoted number in the named artifact. A `FAIL` on this class is cheap to prevent and expensive to argue about |

`scripts/evidence-check.sh` is the mechanical form of this table. It resolves every `file:line`, every
quoted `tests=/failures=`, every `/tmp` pointer, every `§N` (against `TODO.md`) and the absence of
`REVERT-CHECK MUTATION` in the tree. It exits non-zero on a dangling claim. Run it in phase C/D before the
gate, and again before archive. Two modes:

- **live** (a change under `openspec/changes/<name>/`) is strict.
- **archived** (a change under `openspec/changes/archive/`) is report-only for the `§N` and tally checks —
  an archived artifact keeps its historical citations, and a later run may legitimately have overwritten
  its XML — while `file:line`, mutation and structure stay strict in both.

Its own selftest (`--selftest`) proves each check can go red.

## Phase B — eligibility gate (strict skip)

A candidate is eligible only if **all six** conditions hold.

| # | Condition | Reject verdict |
|---|---|---|
| 1 | The claim is verified in the tree today (`file:line` or a run) | `stale` |
| 2 | One root cause explains the symptom | `needs diagnosis` |
| 3 | Exactly **one** fix follows — no trade-off, no option to weigh, no already-shipped fix, no reversal of a recorded owner decision | `needs decision` |
| 4 | The behaviour is a falsifiable WHEN/THEN that is **false on HEAD** | `unspecifiable` |
| 5 | It fits one capability delta and one `fix-*` change (bounded files) | `too wide` |
| 6 | Correctness is decidable by a **host** test (device verification may remain a follow-up task) | `needs device` |

Strict rule: **on any reject, do not ask the user.** Record it and take the next candidate:

```bash
loop-state.sh done --id '§63' --result skipped --verdict needs-decision --note "<what decision>"
```

Then leave the entry honest:

- a decision → `status: on-hold <the decision>`
- a device-gated one → `open (device)`
- a refuted one → `fixed-by <change>`
- a class that no longer matches the evidence → reclassify (`bug` → `improvement`) and add a
  `**Loop verdict**` bullet that quotes the deciding sentence

**Never renumber, never re-rank, never delete** on a skip. File anything the skip uncovered as an
append-only new entry.

Measured: the gate rejected 9 of 12 candidates — 4 refuted (a measurement, a documented owner decision, an
already-shipped fix, a half-reachable effect) and 5 needing a decision. Reporting those verdicts *is* loop
output.

## Phase C — planning the `fix-*` change (iteration child)

Use `openspec-propose` (planning only — it must not touch project code). Change name: `fix-<slug>`.

- `## Why` carries the phase-A evidence block verbatim. No speculation.
- `specs/<capability>/spec.md` — the delta only. Every scenario names exactly one test case. A scenario
  with no case is a planning defect: fix the plan instead of promising to test later. A scenario of a
  MODIFIED requirement that the fix does **not** deliver keeps the baseline's claim strength and says so
  in its task.
- `design.md` — always. Root cause, the single fix, blast radius, rollback, and (for a `fix-*`) the
  alternatives the evidence **eliminated**. A list of viable options means gate condition 3 was wrong:
  stop and re-gate.
- `tasks.md` — ordered, each with its own verification:
  1. the red case with its quoted XML
  2. the fix
  3. one task per scenario naming its case
  4. a revert-check **only** for an invariant the fix adds beyond the original symptom
  5. the forced gate
  6. device/measurement follow-ups stated as pending
  7. `TODO.md` status and any guideline section the fix supersedes

  Before the gate, `grep` the suite for the constants the change moves (`run-tests` step 5) and plan those
  expectation updates explicitly.
- New `TODO.md` ids come from the max over **both** trees when a parallel branch exists (worktree section).

Record `step C --name propose --status ok --note "<change name>"`.

## Phase D — apply (iteration child)

`openspec-apply-change` plus `build-app`, `run-tests`, `revert-check`. Use focused suites while iterating,
and **one** forced both-flavor gate per change shape:

- `./gradlew test -PforceTests --no-build-cache` when only test sources changed.
- `./gradlew test -PforceTests --rerun-tasks` when the change touched more than the test sources.
- **Do not re-run the gate to "refresh" evidence** — the re-run is what destroys it. If a re-run is forced
  (test sources changed), copy the XMLs you cite out first, then re-run.
- Never mark a task `[x]` on a cached or partial run. Never mark it `[x]` while a failure is open.
- Known pre-existing flake: `MapCanvasViewModelModeTest` (`Dispatchers.Main` teardown race, `TODO.md` §148).
  Attribute it (victim alone, then together). Do not "pass on re-run".

Record `step D --name apply --status ok --note "<gate tally>"`.

## Phase E — independent review (review child, default on)

Spawn `reviewer` with fresh context. It must not be the writer. **The contract is fixed** — every review
round in the first run used these items, and the two FAILs came from items 4 and 6.

```
1. scenario coverage — PASS|FAIL|UNPROVEN per delta scenario, with the asserting case name + assertion + timestamp.
   A scenario whose only support is a different claim is FAIL/UNPROVEN, never PASS.
2. root cause scope — does the diff fix the named cause and nothing else? quote the hunk.
3. gate reality — is `tests=<n> failures=0` a real execution for THIS change (timestamps after the fix, executed
   count, no FROM-CACHE)? is the flavour/module coverage the right requirement?
4. tasks claiming done — any `[x]` whose scenario or evidence is not satisfied? (the round-1 FAIL: two `[x]` named
   cases that did not assert their scenario, one of them asserting the opposite)
5. revert-check — mutation targets the production invariant, fails for the RIGHT reason (quote the message + ts),
   restore clean (no marker, `git diff` of production empty).
6. evidence pointers — every quoted number carried by the artifact it names; the archived text carries it too; a
   gitignored build-dir copy alone is fragile. (the round-2 FAIL)
7. spec/guideline honesty — is the delta the right kind (ADDED vs MODIFIED), does it state a contract rather than
   an implementation, did the guideline get a rule (not a restatement)?
8. new overreach — every claim NO evidence supports, verbatim as "UNPROVEN: …".
VERDICT: PASS|FAIL — on FAIL, the exact things to change.
```

Then:

- `PASS` → archive.
- `FAIL` → back to the same change as a new `worker` task (fix + re-run the affected cases), then one more
  review. **Max 2 review rounds.** After that record the change `blocked`, leave it unarchived, and continue
  with the next bug.
- **Subtractive round rule.** A round-2 `FAIL` whose findings are *strictly subtractive* — delete a claim,
  correct a pointer, remove a wrong quote, narrow a scenario mapping — needs no third review. The
  remediation contract is the reviewer's own quote, and the orchestrator verifies it mechanically (one
  `grep` per quoted number ↔ named artifact pair plus the round's `git diff`). Anything that adds a claim, a
  case or an assertion gets a third review. A round that finds *new* overreach means `blocked`, not a longer
  round.

Record `step E --name review --status ok|fail --note "<round count / verdict>"`.

## Phase F — verify, archive, bookkeeping (iteration child)

1. `openspec-verify-change` — CRITICAL issues must be zero or explained. Name every skipped check.
2. **Copy the XMLs the change cites into `<change>/evidence/`** (the red case, the mutation case, the new
   cases' gate XMLs):
   `cp app/build/test-results/*/TEST-*<Case>.xml openspec/changes/<name>/evidence/`.
   Then run `evidence-check.sh` in live mode, so the claims travel with the archived change instead of
   depending on a build directory the next run overwrites.
3. `openspec-archive-change`. Confirm the directory, and that the delta merged into the main spec (no delta
   headers left; `openspec validate <capability> --type spec`). Re-run `evidence-check.sh` against the
   archived directory (report-only mode) and quote its drift count — a drifted row is a claim a reader can
   no longer check.
4. `TODO.md`: entry → `**status:** fixed-by \`<change>\``. Update the **Clusters** index so it matches the
   metadata (regenerate the row; a `class` change is an index change). Delete the entry **only** when the
   run was started with `--allow-todo-removal yes` and the archive exists; otherwise report it as a removal
   candidate. Re-run the structure checks (`##` count == `**id:**` count, every heading followed by its
   metadata line, no id collision).
5. `loop-state.sh done <change> --result done --id '§<n>'`.
6. Commit the iteration on the loop branch: `fix: <slug> (§N)` for the fix, and
   `fix: <slug> (§N) — archive with its evidence` for the archive. Do not push unless asked.

## Stop conditions

Check with `loop-state.sh next` (exit 3 = stop) before starting an iteration. Stop when:

- the bug cap is reached, or the deadline passed
- the candidate queue is empty or entirely gated out — then re-triage **once** (step 0b) and stop if that
  pass reports `eligible: none`
- two consecutive iterations ended `blocked`
- less time remains than the next iteration can cost (see "Cost of one iteration")
- the user interrupts

A stopped clock is not a failed loop. Report what closed, what was skipped and why, the yield, and the state
file.

## Failure handling (never silently absorb)

| Situation | Action |
|---|---|
| Child reports `blocked` mid-change | Record `--result blocked --note`. Write a `ki_processing_failures.log` entry (timestamp, approach, problem). Leave the change unarchived, take the next bug |
| The fix needs a decision or grows beyond one capability | Re-gate. On a fail, abandon the change (do not archive; say so explicitly) |
| Tests red after 3 focused attempts | `blocked`, as above. Never weaken a test to make it green |
| A defect is found that is not this change's | Append a new `TODO.md` entry (append-only, new highest id, cross-reference instead of duplicating) and continue |
| The gate needs a device that is absent | State the device task as pending in the report and file the residue (`class: improvement`, `status: open (device)`). A bug counts as closed only if host evidence proves correctness |

## Report (end of run)

`loop-state.sh report` prints the per-iteration table plus every iteration's steps. Use that output
verbatim, then add the header and the closing lines:

```markdown
## Bug-fix loop — <date> (budget N bugs / M min; closed X, skipped Y, blocked Z, spent T min)

| § | verdict | change | root cause (one line) | red test | gate | review | TODO |
|---|---|---|---|---|---|---|---|
| 138 | closed | fix-pinned-band-height | constant reservation vs font-scale band | BandScale#…fs2: 42.67<48 dp | 1750/0 | 2 rounds | fixed-by |
| 122 | refuted | — | stop control already its own node (48×48 dp, disjoint) | — | — | — | fixed-by + §165/§166 |

Skipped: §130 needs-device+decision · §56 needs-decision (owner decision D7) · …
New entries filed: §165 §166 §167 §168 §169
Decisions the owner owes: <list of on-hold entries with the decision named>
Budget left: <bugs> bugs / <min> min · State: .pi/bugfix-loop/run.json
```

## Pitfalls

1. **Fixing before reproducing.** The red-on-HEAD case is the whole correctness argument.
2. **Treating "the fix is obvious" as gate condition 3.** Obviousness is not uniqueness. Two mechanisms
   producing the symptom means `needs diagnosis`.
3. **Asking the user inside the loop.** Only the run-start budget is a prompt. Strict skip replaces per-bug
   questions.
4. **The orchestrator reading artifacts.** Context exhaustion is the failure mode this design removes.
5. **Two writers in one tree** — including the orchestrator's own `TODO.md` bookkeeping during a child's run.
6. **Archiving on the writer's word.** The review child is read-only and independent on purpose.
7. **`openspec-propose` writing code.** It is planning. Apply is a separate phase.
8. **Deleting a `TODO.md` entry the run was not allowed to remove** — mark `fixed-by` and propose it.
9. **Exceeding the budget because "one more bug".** `loop-state.sh next` is the authority.
10. **A tall, convincing artifact with one false pointer** — the change's own review failed twice on this
    alone. Copy the red/mutation XML out before the next run, quote the XML, never a `/tmp` log, and run
    `evidence-check.sh` before archive.
11. **Reading a round-2 `FAIL` as "the fix is wrong".** Separate the classes. A fix/gate/revert-check `PASS`
    with a pointer `FAIL` is a documentation round — and a strictly subtractive one needs the orchestrator's
    mechanical check, not a third full review.
12. **A mis-scoped candidate: the loop is bugs-only.** A triage summary can rank a `class: improvement` entry
    (or a recorded owner decision) as the top bug. The pre-screen carries the class and the six conditions,
    and drops everything else before the queue is built.
13. **Starting an iteration the clock cannot finish.** A 15-minute skip and a 65-minute change both start
    with the same command. The pre-screen and the measured costs are what tell them apart.
14. **Forgetting the step log.** Without `loop-state.sh step` the report cannot say which phase produced
    which result, and a resumed run cannot tell whether it was mid-iteration. Record every phase boundary.
15. **Allocating `§ids` from one tree only** when a parallel branch edits the same `TODO.md` — the merge then
    duplicates every new id, and `cleanup-todo` has to renumber them afterwards.
