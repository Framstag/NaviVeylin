---
name: fix-loop
description: Run a budgeted, pre-authorized loop that closes defect-shaped backlog items one at a time — bugs, and improvements that clear the same bar. Per item: pre-screen the backlog, prove the root cause with a test that is red on HEAD, create a `fix-*` OpenSpec change, apply it, get an independent review, archive it, update `TODO.md`, take the next item. Stops at an item cap, a deadline or a blocker. Use when asked to "run the bug fix loop", "fix bugs until <time>/<count>", "work the backlog in a loop", or when several triaged defects should be closed in one session without exhausting the context window.
---

# fix-loop

Closes defect-shaped backlog items — bugs, and improvements that clear the same bar — one at a time, on a
pre-authorized budget. One item per iteration, and the orchestrator (this session) reads no artifacts: each
child runs with fresh context, which is what lets a long run survive.

```
pre-screen the whole backlog → eligibility gate → root cause + red test → fix-* change
  → apply → independent review → verify + archive → TODO.md bookkeeping → next item
```

A **bug** contradicts a spec. An **improvement** enters when it is defect-shaped: one named gap, one fix, and
a red case that asserts a number rather than "better" (Phase B condition 7). A **feature** never enters.

## What the loop guarantees

Four properties. Each is enforced by a mechanism, and each mechanism was measured (see `REFERENCE.md`).

| property | mechanism |
|---|---|
| confident analysis and fix | the defect is reproduced by a test that is **red on HEAD before the fix** (Phase A), and a fresh `reviewer` child confirms every scenario against the diff and the gate XMLs before archive (Phase E) |
| unambiguous specification, no options to weigh | gate condition 3; `design.md` records the alternatives the evidence **eliminated**, never viable ones |
| manageable implementation | gate condition 5 — one capability, bounded files (run 1: every archived change touched one production file) |
| all tests green | one forced both-flavor gate per change shape (Phase D), plus one revert-check per new invariant (Phase C) |

The loop is **pre-authorized** by the user's request: it may create changes, edit code, archive changes and
repair `TODO.md` metadata without asking per step. It may **not** exceed the budget, invent scope, or delete a
`TODO.md` entry the run was not allowed to delete.

## When to use

- "Fix bugs in a loop until <N items / M minutes>", "work the backlog", "run the bug fix loop"
- Several triaged defects should close in one session — bugs, and defect-shaped improvements alike

## When NOT to use

- The user named one entry → one ordinary `fix-*` change via `openspec-propose`. The loop adds overhead.
- `class: feature` → the ordinary change flow. A feature has no wrong behaviour to falsify, so only condition
  5 bounds its scope, and scope growth — not the gate — is a feature's failure mode.
- Cleaning `TODO.md` → `cleanup-todo`. Auditing proof gaps → `openspec-proof-gap-audit`.
- No `TODO.md` entry exists → triage first with `triage-todo`, then report.

## Start — the budget (the only prompt of the run)

Ask **once**, in ONE `ask_user` call. Start nothing before the answers exist.

| input | why it is mandatory |
|---|---|
| item cap `N` | how many items this run may close — bugs and improvements together |
| wall-clock budget `M` minutes | when the run must stop even if items remain |
| review `on/off` | `on` = one fresh `reviewer` child per change (costs time, buys the correctness property) |
| `TODO.md` removal allowed `yes/no` | whether an archived entry may be deleted or only marked `fixed-by` |

```bash
.pi/skills/fix-loop/scripts/loop-state.sh init --items N --minutes M --review on|off --allow-todo-removal yes|no
.pi/skills/fix-loop/scripts/loop-state.sh show
```

State: `.pi/bugfix-loop/run.json`. The directory keeps its original name so that naming the skill after the
artifact it produces cannot orphan an open run; it is machine-local and gitignored. Never commit it, never
hand-edit it — `loop-state.sh` is its only writer (invariant 3).

## Pre-screen the backlog (one pass, not eight)

Build the queue once, from a full pass over the backlog; the iteration child only *re-confirms* the verdict.
The first run ranked the top 8 by score and then spent 8 iterations discovering that 5 of them were not
eligible, so the pass covers everything and concludes in minutes.

1. The triage child (`scout`, `skill: ["triage-todo"]`) enumerates **every** in-scope entry with `status:
   open|unverified` — not the top N — carrying its class, its category from the file's own vocabulary, and a
   per-candidate verdict. In scope: `bug`, and `improvement`. A `feature` is dropped, with its class as the
   reason.
2. It applies the conditions of **Phase B** while ranking, cites `file:line`, and drops what fails. Rank in
   `triage-todo`'s order — **bugs first, then improvements** — so the budget goes to bugs before an
   improvement is reached.
3. The queue holds **survivors only**. Every rejection is reported with the condition that failed and one
   clause of evidence. A distinct defect the pass uncovers is filed as a new `TODO.md` entry (append-only,
   new highest id, cross-reference instead of duplicating).
4. **Two triages per run, maximum.** The second is for a queue that emptied mid-run; a third pass over a file
   that already answered "eligible: none" is budget burned on a known answer.

The child's contract:

```js
await runs.run('triage', { agent: 'scout', skill: ['triage-todo'], task:
  'Run the triage-todo skill on TODO.md. Cover EVERY entry with status open or unverified whose class is bug '
  + 'or improvement (not the top N); drop feature entirely, and anything in-flight, on-hold, fixed-by or '
  + 'device-gated. Output one line per surviving candidate: "§<id> | score | <class> | <category from the '
  + 'file vocabulary> | <one-line claim> | <file:line evidence> | eligible: yes", then "queue: §a §b §c" in '
  + 'rank order (bugs first), then up to 5 lines "rejected: §<id> — <condition number + one clause of '
  + 'evidence>", then "visited: <checked>/<total>". Apply the conditions of that skill\'s Phase B while '
  + 'ranking, and name the condition number each rejection fails.' })
```

## Roles — who does what (context-window discipline)

| role | who | owns |
|---|---|---|
| Orchestrator | the current session | the budget, the queue, the final report. Reads **only** child reports (≤ 40 lines) and mechanical checks (`git status`, `git diff --stat`, `openspec list`, XML tallies, `evidence-check.sh`). It never reads a change artifact, a guideline or a test file in full |
| Triage child | one `scout` child, `skill: ["triage-todo"]` | the full pre-screen and the ranked survivor queue |
| Iteration child | one `worker` child per item, **fresh context** | phases A–F for exactly one item and one change |
| Review child | one `reviewer` child per change, **fresh context**, read-only | the fixed review contract (Phase E) |

The JS blocks in this document state each child's **contract**. Use them as a workflow script or as a direct
`subagent({agent, task, skill})` call — the guarantees come from the contract, not the transport.

## Recording progress

`loop-state.sh` owns the run state and is its only writer, and it exits 3 whenever the run has nothing left to
spend — so a caller stops on an exit code instead of on its own arithmetic.

```bash
S=.pi/skills/fix-loop/scripts/loop-state.sh
$S show                      # which item, which phase, the result of every step so far
$S next                      # next candidate; exit 3 when the queue or the budget is spent
$S start fix-<slug> --id §N  # begin one iteration at phase A
$S step <phase> --name <step> --status running|ok|fail|skip --note "<one clause>"
$S done <change> --result done|skipped|blocked [--id §N] [--verdict V] [--note TEXT]
$S report                    # per-iteration table plus every iteration's steps
```

Record the pre-screen as `step prescreen --name <step>` **before** the queue is stored, then one step as each
phase ends. `<phase>` is one of `prescreen A B C D E F`. Run `$S show` before each iteration and paste its
`steps` block into the run report. If the last step is still `running`, that iteration is mid-flight — **do not
start another**.

## Phase A — root cause and the red test (iteration child)

**Measure first, never fix from the symptom description** (`guidelines/Build.md` §2).

1. Verify the entry against the tree: the entry is a memory, not a fact. Confirm the condition still holds
   (`file:line`) and that the defect is not already fixed.
2. Name the root cause — one mechanism that explains the symptom, in one sentence.
3. **Produce the red test**: the smallest case that exercises the defect and fails on HEAD. Record the JUnit
   XML `tests=/failures=`, the case name and the timestamp, and retain it under invariant 1.
4. Name the owning requirement and the owning `guidelines/*.md` section. Route with `AGENTS.md`
   "Documentation Map"; never read a guideline in full.
5. Record `step A --name root-cause` and `step A --name red-test` with the XML tally.

The red case has three shapes, and the shape decides what the revert-check mutates:

| the defect is | the red case is | its revert-check mutates |
|---|---|---|
| code that contradicts a spec | a case asserting the spec's WHEN/THEN | the fixed code line |
| a spec that contradicts the code (doc-vs-code divergence) | a conformance case that reads the spec text and compares it to the code's constants — precedent: `FavAutoZoomClampRangeTest`, `MapMenuBackOrderComposeTest` | the **code constant**, not the test |
| an improvement's missing bound, count or absence (condition 7) | a case asserting the number the delta requires, read today | the code constant that produces the old number |

Deliverable — the evidence block that goes verbatim into `## Why` of `proposal.md`:

```
Root cause: <one sentence>
Evidence:    <test FQCN#case> fails on HEAD — XML tests="1" failures="1" (<message>), <timestamp>
Repro:       <command, if a test is not the fastest proof>
Spec:        <capability> / <requirement name>   Guideline: <file> §<n>
```

## Phase B — eligibility gate (strict skip)

An item is eligible only if **all** conditions below hold. Condition 7 applies to an improvement; a bug
satisfies it by definition, because it already contradicts a spec.

| # | condition | reject verdict |
|---|---|---|
| 1 | the claim is verified in the tree today (`file:line` or a run) | `stale` |
| 2 | one root cause explains the symptom | `needs-diagnosis` |
| 3 | exactly **one** fix follows — no trade-off, no option to weigh, no already-shipped fix, no reversal of a recorded owner decision | `needs-decision` |
| 4 | the behaviour is a falsifiable WHEN/THEN that is **false on HEAD**: a bug asserts what the code does *wrong*; an improvement asserts the bound, count or absence its delta requires and the code does not yet meet | `unspecifiable` |
| 5 | it fits one capability delta and one `fix-*` change (bounded files) | `too-wide` |
| 6 | correctness is decidable by a **host** test (device verification may remain a follow-up task) | `needs-device` |
| 7 | *(improvement only)* the delta is owned by an existing capability — or adds one small requirement — and its red case asserts a number, a count or an absence, not "better" | `not-spec-shaped`, `needs-target` |

The gate is cheap: a few greps and one file read. Apply it **before** any analysis, and on any reject **do not
ask the user** — record it and take the next candidate:

```bash
loop-state.sh done --id '§63' --result skipped --verdict needs-decision --note "<what decision>"
```

Then leave the entry honest: a decision → `status: on-hold <the decision>`; a device-gated one → `open
(device)`; a refuted one → `fixed-by <change>`; a `not-spec-shaped` one → reclassify to `feature` or
`status: on-hold <what it needs>`; a class that no longer matches the evidence → reclassify and add a
`**Loop verdict**` bullet quoting the deciding sentence.

**Never renumber, never re-rank, never delete** on a skip. File anything the skip uncovered as an append-only
new entry. Reporting the verdicts *is* loop output: the gate rejected 9 of 12 candidates in the first run
(4 refuted, 5 needing a decision), and that report was the run's main product.

## Phase C — planning the `fix-*` change (iteration child)

Use `openspec-propose` (planning only — it must not touch project code). Change name: `fix-<slug>`.

- `## Why` carries the Phase A evidence block verbatim. No speculation.
- `specs/<capability>/spec.md` — the delta only. Every scenario names exactly one test case; a scenario with
  no case is a planning defect, so fix the plan instead of promising to test later. A MODIFIED requirement the
  fix does **not** deliver keeps the baseline's claim strength and says so in its task.
- `design.md` — always. Root cause, the single fix, blast radius, rollback, and the alternatives the evidence
  **eliminated**. A list of viable options means condition 3 was wrong: stop and re-gate.
- `tasks.md` — ordered, each with its own verification: the red case with its quoted XML; the fix; one task
  per scenario naming its case; a revert-check **only** for an invariant the fix adds beyond the original
  symptom; the forced gate; device or measurement follow-ups stated as pending; the `TODO.md` status and any
  guideline section the fix supersedes.
- Before the gate, `grep` the suite for the constants the change moves (`run-tests` step 5) and plan those
  expectation updates explicitly.
- New `TODO.md` ids come from the max over **both** trees when a parallel branch exists (see `REFERENCE.md`,
  "Running the loop in a worktree").

Record `step C --name propose --note "<change name>"`.

## Phase D — apply (iteration child)

`openspec-apply-change` plus `build-app`, `run-tests`, `revert-check`. Iterate with focused suites and run
**one** forced both-flavor gate per change shape:

- `./gradlew test -PforceTests --no-build-cache` when only test sources changed.
- `./gradlew test -PforceTests --rerun-tasks` when the change touched more than the test sources.
- **Do not re-run the gate to "refresh" evidence** — the re-run is what destroys it. If a re-run is forced
  (test sources changed), copy the XMLs you cite out first (invariant 1).
- Never mark a task `[x]` on a cached or partial run, and never while a failure is open.
- Known pre-existing flake: `MapCanvasViewModelModeTest` (`Dispatchers.Main` teardown race, `TODO.md` §148).
  Attribute it (victim alone, then together); do not "pass on re-run".

Record `step D --name apply --note "<gate tally>"`.

## Phase E — independent review (review child, default on)

Spawn `reviewer` with fresh context; it must not be the writer. **The contract is fixed** — items 4 and 6 are
the two that failed in the first run.

```
1. scenario coverage — PASS|FAIL|UNPROVEN per delta scenario, with the asserting case name + assertion + timestamp.
   A scenario whose only support is a different claim is FAIL/UNPROVEN, never PASS.
2. root cause scope — does the diff fix the named cause and nothing else? quote the hunk.
3. gate reality — is `tests=<n> failures=0` a real execution for THIS change (timestamps after the fix, executed
   count, no FROM-CACHE)? is the flavour/module coverage the right requirement?
4. tasks claiming done — any `[x]` whose scenario or evidence is not satisfied?
5. revert-check — mutation targets the production invariant, fails for the RIGHT reason (quote the message + ts),
   restore clean (no marker, `git diff` of production empty).
6. evidence pointers — every quoted number carried by the artifact it names; the archived text carries it too; a
   gitignored build-dir copy alone is fragile.
7. spec/guideline honesty — is the delta the right kind (ADDED vs MODIFIED), does it state a contract rather than
   an implementation, did the guideline get a rule (not a restatement)?
8. new overreach — every claim NO evidence supports, verbatim as "UNPROVEN: …".
VERDICT: PASS|FAIL — on FAIL, the exact things to change.
```

- `PASS` → archive.
- `FAIL` → back to the same change as a new `worker` task (fix, re-run the affected cases), then one more
  review. **Max 2 review rounds**; after that record `blocked`, leave it unarchived, take the next item.
- **Subtractive round rule.** A round-2 `FAIL` whose findings are *strictly subtractive* — delete a claim,
  correct a pointer, remove a wrong quote, narrow a scenario mapping — needs no third review: the remediation
  contract is the reviewer's own quote, and the orchestrator verifies it mechanically (one `grep` per quoted
  number ↔ named artifact pair, plus the round's `git diff`). Anything that **adds** a claim, a case or an
  assertion gets a third review, and a round that finds *new* overreach means `blocked`, not a longer round.

Record `step E --name review --status ok|fail --note "<round count / verdict>"`.

## Phase F — verify, archive, bookkeeping (iteration child)

1. `openspec-verify-change` — CRITICAL issues zero or explained. Name every skipped check.
2. Copy the XMLs the change cites into `<change>/evidence/` (invariant 1), then run `evidence-check.sh` in
   live mode, so the claims travel with the archived change instead of depending on a build directory the next
   run overwrites.
3. `openspec-archive-change`. Confirm the directory, and that the delta merged into the main spec (no delta
   headers left; `openspec validate <capability> --type spec`). Re-run `evidence-check.sh` against the
   archived directory (report-only mode) and quote its drift count — a drifted row is a claim a reader can no
   longer check.
4. `TODO.md`: entry → `**status:** fixed-by \`<change>\``. Update the **Clusters** index so it matches the
   metadata (a `class` change is an index change). Delete the entry **only** when the run was started with
   `--allow-todo-removal yes` and the archive exists; otherwise report it as a removal candidate. Re-run the
   structure checks: `##` count == `**id:**` count, every heading followed by its metadata line, no id
   collision.
5. `loop-state.sh done <change> --result done --id '§<n>'`.
6. Commit on the loop branch: `fix: <slug> (§N)` for the fix, `fix: <slug> (§N) — archive with its evidence`
   for the archive. Do not push unless asked.

## Invariants

Each is stated once here and referenced by name from the phases.

### 1. Evidence pointers — what a quoted number must point at

**Every number, tally or timestamp quoted in an artifact must name an artifact that actually carries it, and
that artifact must still say the same thing when the change is archived.** Both review rounds that failed in
the first run failed on this class, and both times the *fix* was sound. It is an invariant, not a style note.

| rule | why |
|---|---|
| Copy the cited XMLs into `<change>/evidence/` before archive — and immediately for the red and mutation runs | the archive is the only versioned part of a change; a cited XML left in `app/build/test-results/` is overwritten by the next run |
| Quote the JUnit XML, not the Gradle log, for test-derived numbers | Gradle prints no test stdout, so a line from a case was once attributed to a log that never contained it |
| Make the case print what it measures (`println` → XML `system-out`) | the after-fix geometry then survives the run that produced it and is re-checkable |
| Never point at a `/tmp` log | the next run overwrites it — a quoted focus tally became `BUILD SUCCESSFUL in 2s … FROM-CACHE` under the same path |
| A row you can no longer reproduce is labelled **not retained**, with how it was obtained | an honest prose record beats a dangling pointer |

`evidence-check.sh` is the mechanical form of the table: it resolves every `file:line`, every quoted
`tests=/failures=`, every `/tmp` pointer and every `§N` against `TODO.md`, and checks the absence of
`REVERT-CHECK MUTATION` in the tree. It exits non-zero on a dangling claim, runs in Phases C/D before the gate
and again before archive, and has two modes — **live** (strict) and **archived** (report-only for the `§N` and
tally checks, strict for `file:line`, mutation and structure). Its own `--selftest` proves each check can go
red.

### 2. One writer per tree

Never run two iteration children concurrently. The orchestrator writes `TODO.md`, `config` or skill files
**only while no child is running** — a bookkeeping edit during a child's `git add -A` is committed under the
child's message. The loop owns its branch; the orchestrator owns the skill in the **repo** copy (see
`REFERENCE.md`, "Running the loop in a worktree").

### 3. Budget authority

`loop-state.sh` owns the cap, the deadline, the queue and the state file, and is their only writer. `next`
(exit 3 = stop) is the authority on whether another iteration may start — never your own arithmetic, and never
"one more".

## Stop conditions

Check `loop-state.sh next` (exit 3 = stop) before starting an iteration. Stop when:

- the item cap is reached, or the deadline has passed;
- the queue is empty or entirely gated out — then re-triage **once** and stop if that pass reports
  `eligible: none`;
- two consecutive iterations ended `blocked`;
- less time remains than the next iteration can cost (see `REFERENCE.md`, "Measured costs");
- the user interrupts.

**Never start an iteration you cannot finish**: with less than ~45 min left, take the next candidate only if
the pre-screen named its host seam. Two thirds of the first run's candidates were skips, so a budget that
ignores skip cost plans for the wrong shape.

A stopped clock is not a failed loop. Report what closed, what was skipped and why, the yield, and the state
file.

## Failure handling (never silently absorb)

| situation | action |
|---|---|
| child reports `blocked` mid-change | record `--result blocked --note`. Write a `ki_processing_failures.log` entry (timestamp, approach, problem). Leave the change unarchived, take the next item |
| the fix needs a decision, or grows beyond one capability | re-gate. On a fail, abandon the change (do not archive; say so explicitly) |
| tests red after 3 focused attempts | `blocked`, as above. Never weaken a test to make it green |
| a defect is found that is not this change's | append a new `TODO.md` entry (append-only, new highest id, cross-reference) and continue |
| the gate needs a device that is absent | state the device task as pending in the report, file the residue (`class: improvement`, `status: open (device)`). An item counts as closed only if **host** evidence proves correctness |
| a candidate looks out of scope for its class | the pre-screen carries the class and applies the Phase B conditions; a `feature` is dropped there, not during analysis |

## Report (end of run)

`loop-state.sh report` prints the per-iteration table plus every iteration's steps. Use that output verbatim,
then add the header and the closing lines:

```markdown
**fix-loop — <date>** (budget N items / M min; closed X, skipped Y, blocked Z, spent T min)

| § | class | verdict | change | root cause (one line) | red test | gate | review | TODO |
|---|---|---|---|---|---|---|---|---|
| 138 | bug | closed | fix-pinned-band-height | constant reservation vs font-scale band | BandScale#…fs2: 42.67<48 dp | 1750/0 | 2 rounds | fixed-by |
| 122 | bug | refuted | — | stop control already its own node (48×48 dp, disjoint) | — | — | — | fixed-by + §165 |

An improvement's row reads the same, with `class` = `improvement` and a red case that asserts its number.

Skipped: §130 needs-device+decision · §56 needs-decision (owner decision D7) · …
New entries filed: §165 §166 §167 §168 §169
Decisions the owner owes: <list of on-hold entries with the decision named>
Budget left: <items> items / <min> min · State: .pi/bugfix-loop/run.json
```

## Reference

`REFERENCE.md`, beside this file, carries what a run needs but no rule of its own:

- **Running the loop in a worktree** — the recipe, and the four facts that shape verification inside one.
- **Measured costs** — the minute budget of a change, a skip and the gate, and the measurements behind the
  rules above; read it before planning a run's clock.
- **Field notes** — ten failure modes, each naming the section above that owns its rule.

