---
name: fix-loop
description: 'Run a budgeted, pre-authorized loop that closes defect-shaped backlog items one at a time — bugs, and improvements that clear the same bar. The entry is `tools/loop/run.sh`, which owns the budget, the worktree, the branches, the labels and the state; a fresh agent process works one item from a prepared package, a second fresh process reviews it, and the run closes the item only after it has read the archive and the gate evidence itself. Use when asked to "run the bug fix loop", "fix bugs until <time>/<count>", "work the backlog in a loop", or when several triaged defects should be closed in one session without exhausting the context window.'
---

# fix-loop

Closes defect-shaped backlog items — bugs, and improvements that clear the same bar — one at a time, on a
pre-authorized budget. **The loop is a script** (`tools/loop/run.sh`), not a session: since
`agent-issue-loop` landed, one command starts a run, and no part of the budget, the queue, the version
control or the tracker bookkeeping is improvised by a model.

```
run.sh  pre-flight → decision cap → one worktree per run → per item:
          prescreen (a script) → a branch of its own → a fresh agent process with a prepared package
          → commits the child's work itself → a second, fresh process reviews it
          → archive → labels (`labels.sh`) and closure (`close.sh`) → the next item
```

A **bug** contradicts a spec. An **improvement** enters when it is defect-shaped: one named gap, one fix, and
a red case that asserts a number rather than "better" (phase B, condition 7). A **feature** never enters.

## How to start a run

```bash
tools/loop/run.sh --items N --minutes M [--review on|off] [--may-close yes|no] [--push yes|no]
                  [--max-decisions N] [--max-review-rounds N] [--review-read-only on|off] [--dry-run]
                  [--clean-worktree]
```

Every flag's default is recorded in `tools/loop/loop.conf`, and the run header prints the values it actually
used, so a run is repeatable and auditable. **There is no budget prompt**: a script cannot ask, and an
unrecorded budget cannot be audited. `--dry-run` prints the queue, the exclusions and the report, and creates
nothing. What the flags mean:

| flag | what it decides |
|---|---|
| `--items N` | how many items this run may finish |
| `--minutes M` | when the run must stop even if items remain |
| `--review on\|off` | whether a second, fresh process reviews each change |
| `--may-close yes\|no` | whether a finished item may be **closed**; `no` leaves the issue open with `status/fixed-by` and still posts the evidence comment |
| `--push yes\|no` | the only thing that publishes anything; this loop never opens a change request |
| `--max-decisions N` | the pile of open `agent/needs-decision` items that stops a run **before** it starts |
| `--max-review-rounds N` | how many review rounds one item gets (default 2); a refusal whose findings are strictly subtractive earns exactly one more, and a refusal that counts **no blocking finding** costs no round at all — it closes |
| `--clean-worktree` | remove the run's worktree at the end (default: keep it, so the next run keeps its warm build) |

Exit codes: `0` the run ended (including "nothing eligible"), `2` usage, `3` stop (the identity is not
authorised, or nothing is eligible), `5` refuse (another run holds the lock, the decision cap is reached, the
base has moved).

## What the wrapper owns, and what an agent may not touch

| owner | what it does | why |
|---|---|---|
| `run.sh` | the budget, the lock, the worktree, the branches, the commits, the labels, the closure, the report | a script cannot be talked into "one more" |
| `state.sh` | the only writer of `.pi/issue-loop/run.json` (machine-local, gitignored), and the keeper of the state it replaces (`runs/<startedAt>.json` beside it, or `LOOP_RUNS_DIR`) | a run that survives an interrupt has one writer, and a finished run's counters outlive the next one starting (issue #207) |
| `prescreen.sh` | the mechanical half of the gate, before any agent process | measured: 9 of 12 candidates were gated out, and the cheap filters are what a script does better |
| `worktree.sh` | one worktree per run, one branch per item off the run's base, the two commits, the stale-base refusal | the landing step is this repository's most fragile |
| `labels.sh` | every label transition, through the tooling's own write paths | the authorisation label stays unwritable by the loop it authorises |
| `report.sh` | the run's numbers, read from the state file and from git | concept doc §6 asks a loop to justify itself |
| the **worker** process | phases A–F for exactly one item — root cause, red test, change, apply, archive | its contract is `tools/loop/prompts/worker.md` |
| the **reviewer** process | the fixed eight-item review, read-only | its contract is `tools/loop/prompts/reviewer.md` |

The phases the worker runs are the ones this skill has always stated — A (root cause and the red test), B (the
eligibility gate below), C (plan the change), D (apply, with the project's gate), F (evidence, archive) — and
the reviewer's independent contract (E) is unchanged. **The normative text for a child is the prompt
skeleton in `tools/loop/prompts/`, not a copy here**: one home per rule. This skill owns the *rules* — the
gate, the invariants, the stop conditions, the failure handling, the report — and the wrapper owns the
mechanics.

### What a reviewer receives

The round's diff is the **change**, not the tree the change sits in. The run writes `review-<round>.diff` with
the project's exclusion pathspec (`loop.conf`'s `LOOP_REVIEW_EXCLUDE_PATHSPEC` — `**/evidence/**` here), so the
generated material an item archives beside its code — gate HTML, JUnit XML, mutation transcripts — never
reaches the reading surface. It is an exclusion by *directory* and not by extension, because a `.txt` a
reviewer has to read can live outside an evidence tree. The evidence still reaches the reviewer, by path,
through `{{EVIDENCE_DIR}}`, and the prompt says the diff excludes it, so an absence in the diff is not read as
a missing artefact. Measured: item #126's round-3 diff carried 9 935 changed lines of which 9 364 (94.3 %)
were evidence, against 112 lines of Kotlin (issue #204; re-measured on the run's own file at 10 883 lines, 9 574
of them inside `evidence/`) — a cost paid three times, once per review round.

### How a finding is graded

A round states a number, not an adjective: `LOOP-BLOCKING: <n>`, the count of its own `BLOCKING:` findings, and
`VERDICT: PASS` is allowed only at 0. `BLOCKING:` means the change cannot be merged as it stands — the contract
is contradicted, a scenario is not exercised by the case that names it, or the gate or the red-case evidence is
missing or false. `NOTE:` means it can: wording, a pointer, a naming, an ordering, anything the change does not
depend on. A finding the reviewer cannot grade is blocking.

| the round's lines | what the run does |
|---|---|
| `VERDICT: PASS`, count `0` or absent | closes, as it always has |
| `VERDICT: FAIL`, `LOOP-BLOCKING: 0` | closes: the refusal blocked nothing, and the `NOTE:` findings are recorded in the closure comment and in the item's state note |
| `VERDICT: FAIL`, `LOOP-BLOCKING: n ≥ 1` | a refusal — another round inside the budget, then `blocked` and escalated |
| no `LOOP-BLOCKING` line at all | a refusal whatever the verdict says, because a run cannot tell an old-shaped review from a quiet one |

`LOOP-CORRECTIONS-ONLY` describes the *shape of a refusal*, so it is read only when the blocking count is not
zero: a report with zero blocking findings does not refuse, and has no round for that line to buy. The count
wins because it is the machine-readable line and the shape of a refusal is not a refusal.

Measured 2026-10-19, item #126: 83 minutes, three review rounds (16.2 KB, 13.4 KB, 15.6 KB — the findings were
rewritten, not closed), then `blocked` and escalated, while the escalation itself said every finding was *a
narrowing or a corrected pointer* and recommended the merge, with code, gate (5 680 tests, 0 failures) and
revert-check all green. The run had no vocabulary for that distinction, so a misnamed constant and a broken
scenario were the same refusal (#205).

## The eligibility gate (phase B)

An item is eligible only if **all** conditions below hold. Condition 7 applies to an improvement; a bug
satisfies it by definition, because it already contradicts a spec. Conditions 1 and 6 are decided by
`prescreen.sh` where a script can decide them; conditions 2–5 are the worker's judgement, and the queue
carries them as `pending` rather than guessing.

| # | condition | reject verdict |
|---|---|---|
| 1 | the claim is verified in the tree today (`file:line` or a run) | `stale` |
| 2 | one root cause explains the symptom | `needs-diagnosis` |
| 3 | exactly **one** fix follows — no trade-off, no option to weigh, no already-shipped fix, no reversal of a recorded owner decision | `needs-decision` |
| 4 | the behaviour is a falsifiable WHEN/THEN that is **false on HEAD** | `unspecifiable` |
| 5 | it fits one capability delta and one `fix-*` change (bounded files) | `too-wide` |
| 6 | correctness is decidable by a **host** test (device verification may remain a follow-up task) | `needs-device` |
| 7 | *(improvement only)* the delta is owned by an existing capability — or adds one small requirement — and its red case asserts a number, a count or an absence, not "better" | `not-spec-shaped` |

The pre-screen also drops `class/feature`, `status/in-flight`, an item the tracker has already settled
(`status/fixed-by`, `status/retired`, a closed issue) and an item whose author is not on the allowlist, and it
ranks by two keys: the profile's `firstCategories` first — the categories of the tooling, the process and the
proof, whose defects every later item pays for — and then bugs before improvements, oldest issue first inside a
group. A leading category is a rank and never a gate: a held harness item is excluded like any other. Marking a
rejection on the issue is `labels.sh <verdict> <issue>`; an item
that turns out to need a decision mid-item is **escalated** (`labels.sh escalate`), which names the root
cause, the failing case and the branch on the issue instead of throwing the work away.

**Never renumber, never re-rank, never delete** on a skip. Reporting the verdicts *is* loop output: the gate
rejected 9 of 12 candidates in the first run, and that report was the run's main product.

## Invariants

Each is stated once here and referenced by name from the phases. The wrapper enforces all three; the
revert-checks in `agent-issue-loop` prove that it does.

### 1. Evidence pointers — what a quoted number must point at

**Every number, tally or timestamp quoted in an artifact must name an artifact that actually carries it, and
that artifact must still say the same thing when the change is archived.** Both review rounds that failed in
the first run failed on this class, and both times the *fix* was sound. It is an invariant, not a style note.

| rule | why |
|---|---|
| Copy the cited XMLs into `<change>/evidence/` before archive — and immediately for the red and mutation runs | the archive is the only versioned part of a change; a cited XML left in a build directory is overwritten by the next run |
| Quote the JUnit XML, not the Gradle log, for test-derived numbers | Gradle prints no test stdout, so a line from a case was once attributed to a log that never contained it |
| Make the case print what it measures (`println` → XML `system-out`) | the after-fix geometry then survives the run that produced it and is re-checkable |
| Never point at a `/tmp` log | the next run overwrites it |
| A row you can no longer reproduce is labelled **not retained**, with how it was obtained | an honest prose record beats a dangling pointer |

`.pi/skills/fix-loop/scripts/evidence-check.sh` is the mechanical form of the table: it resolves every
`file:line`, every quoted `tests=`/`failures=`, every `/tmp` pointer and every `§N` against the recorded
`§ → issue` table (or the tracker), and checks the absence of `REVERT-CHECK MUTATION` in the tree.

### 2. One writer per tree

Never run two items concurrently, and never let a second writer touch the working tree. The wrapper is the
only writer **and it writes only between children** — a bookkeeping edit while a child's `git add` was in
flight is how a change once got committed under another change's message. `labels#writesOnlyBetweenChildren`
holds the wrapper to it by hashing the state file at each child's start and end.

The loops' own rule: the run owns the branch, the *repo* copy owns the skill (see `REFERENCE.md`, "Running
the loop in a worktree" — now the wrapper's job, not a recipe a person follows).

### 3. Budget authority

`tools/loop/state.sh` owns the cap, the deadline, the queue and the state file, and is their only writer.
`state.sh next` (exit 3 = stop) is the authority on whether another item may start — never your own
arithmetic, and never "one more". The wrapper asks it; a session that drives the loop by hand asks it too.

That authority is consulted **between items**, so it cannot see inside one: the wrapper therefore bounds
each child by its own recorded wall-clock (`LOOP_CHILD_MAX_SECONDS`, default 2700 s = 45 min). A child that
outlasts it is ended together with everything it started, its transcript gains
`LOOP-TIMEOUT: the child exceeded <n> s`, and the item is recorded `blocked` with that transcript. A deadline
read only at the top of the loop cannot bound the body of the loop — measured 2026-10-19, when an item's
worker phase spent 83 minutes inside a single wait with the budget untouched.

## Stop conditions

`state.sh next` exits 3 when the run may not continue, and it is the only authority. It stops when:

- the item cap is reached, or the deadline has passed;
- the queue is empty — the wrapper re-runs the pre-screen **once** per run, never twice;
- two consecutive items ended `blocked`;
- less time remains than the next item can cost — the floor is **measured**, not the constant: `next` takes
  the median wall time of the items this run has finished (`done --wall`), multiplies it by the 1.5 spread
  the measurements show, rounds up, and never goes below `LOOP_ITEM_FLOOR_MINUTES` in `loop.conf`, which is
  therefore the floor of a run with no history or with a cheap one. The refusal names both numbers, e.g.
  `only 28 min left, below the 90 min floor for the next item (median 60 min over 3 finished items,
  configured minimum 45)` — so an operator can see whether the floor is honest. The median is the central
  value because one expensive item does not move it; the 1.5 is the spread the measurements already show
  (65/45 inside the change range, 100/65 for #126);
- the user interrupts (then the wrapper removes `agent/in-progress` and records the item `blocked`).

**Never start an item you cannot finish**, and never stop with time a cheap item could have used. Two
thirds of the first run's candidates were skips, so a floor that ignores skip cost plans for the wrong
shape — and a history of skips (median 10-15 min) is exactly the case where the configured minimum, not the
measurement, is the floor. The `--wall` on every `done` is what makes the next floor honest, which is why an
item is never recorded without it.

A stopped clock is not a failed loop. Report what closed, what was skipped and why, the yield, and the state
file — `tools/loop/report.sh` prints exactly that, and the numbers come from the state file rather than from
a child's prose.

## Failure handling (never silently absorb)

| situation | action |
|---|---|
| the child exits non-zero, or claims work it did not produce | the run records the item `blocked` and does **not** close it — it reads the tree, the archive and the gate XMLs itself |
| the child outlasts `LOOP_CHILD_MAX_SECONDS` | the run ends it and everything it started, appends `LOOP-TIMEOUT: the child exceeded <n> s` to the transcript, and records the item `blocked` with that transcript — the run is not waiting any more, and the gate is **never** retried |
| the review refuses with `LOOP-BLOCKING: 0` | **not a refusal**: the item closes, and the `NOTE:` findings travel into the closure comment and the state note — a note is recorded, never absorbed |
| the change fails its last review round | `agent/needs-decision` + `status/on-hold`, recorded `blocked`, and the next candidate is taken. A report whose findings are subtractive (`LOOP-CORRECTIONS-ONLY: yes`) buys one more round instead, and a report with no `LOOP-BLOCKING` line is read as blocking |
| the reviewer wrote to the tree during its round | the round is discarded with a reason (a reviewer that wrote cannot have been the reviewer), recorded `blocked` |
| the fix needs a decision, or grows beyond one capability | the worker re-gates: `skipped` with `needs-decision`, escalated on the item with the work it had already done |
| tests red after three focused attempts | `blocked`, as above. Never weaken a test to make it green |
| a defect is found that is not this change's | file it as a new issue (`tools/issues/create.sh`, fingerprint-deduped, cross-referencing the issue it touches) and continue |
| the gate needs a device that is absent | the verdict is `needs-device`: the run records it and attempts nothing on the device. An item counts as closed only if **host** evidence proves correctness |
| the same item keeps coming back | it is not eligible: re-gate it, or record what the decision is — a third attempt is not a plan |

**The gate is run once.** The gate command is a recorded value (`LOOP_GATE_CMD`), run as it is stated and
quoted from the XML it produced, and never re-run to "refresh" or to "try again": the second attempt is what
spent the clock on 2026-10-19 — `--rerun-tasks` never reached a suite in 30 minutes, the retry died in another
module, and the same 5680 tests through `-PforceTests --no-build-cache` took 3m47s. A gate that does not complete
inside the child's wall-clock is `blocked` with its log, not retried (#203).

## What a run prints while it works

A child runs in print mode, so its own stream is silent until it exits. The run therefore reports the wait
itself: one line when a child starts, one every 30 s (`LOOP_PROGRESS_SECONDS` overrides it, which is what the
case `run#childReportsWhileItWaits` uses), and one when it ends.

```
loop: worker-prompt started (log .pi/issue-loop/items/126/worker-report.md)
loop: worker-prompt running 1m30s — log 0 B, worktree 4 changed file(s), 0 commit(s) since the base
loop: worker-prompt finished in 12m04s (exit 0, log 812 B)
```

The numbers are read from the tree the child writes and never from the child: `log N B` is the transcript's
size (0 until the child exits, because print mode writes its answer at the end), `changed file(s)` is
`git status --porcelain` in the worktree, `commit(s) since the base` is `git rev-list --count <base>..HEAD`.
Numbers that keep moving are a child thinking; `running` lines that stop appearing are a stopped run — and a
child that outlasts `LOOP_CHILD_MAX_SECONDS` ends itself with `exit 124` and the `LOOP-TIMEOUT` line in its
report, so "running" lines that stop appearing without that line are a stopped *host*, not a bounded child.

## Report (end of run)

`tools/loop/report.sh` prints the header, the per-item table (with each item's wall time and, when its result
is not a closure, the reason it recorded), the skip list, the open decisions, the branches, the kept
worktree and the **actions the caller owes**. Use it verbatim; its last block is the answer to "what must I
do now", so do not replace it with a summary of your own:

```markdown
**fix-loop — <date>** (budget N items / M min; closed X, skipped Y, blocked Z, spent T min)

| issue | class | verdict | change | root cause (one line) | red test | gate | review | wall | outcome | reason |
|---|---|---|---|---|---|---|---|---|---|---|
| #138 | bug | closed | fix-pinned-band-height | constant reservation vs font-scale band | BandScale#…fs2: 42.67<48 dp | 1750/0 | 2 rounds | 52m10s | done | — |
| #122 | bug | stale | — | the case it cited no longer exists | — | — | — | 9m04s | skipped | skipped by the worker |

Skipped: #130 needs-device + needs-decision · #56 needs-decision (owner decision D7) · …
New entries filed: #165 #166
Decisions the owner owes: <the items, with the decision named>
Budget left: <items> items / <min> min · State: .pi/issue-loop/run.json

actions the caller owes:
  1. decide #126 — the run escalated it (blocked): the review refused round 2 · the comment itself: gh issue view 126
  2. land fix/issue-126-pinned-band-height — 3 commit(s) the base does not have — item #126
  3. re-run the gate for #130 — the run recorded no gate result — transcript .pi/issue-loop/items/130/worker-report.md
  4. keep or remove the worktree .pi/worktrees/loop — the next run reuses its warm build; --clean-worktree removes it at the end of a run
```

Every action names what to do, where (issue, branch, log, worktree) and why. A decision whose question the
state does not hold says where the question is (`gh issue view <n>`) instead of paraphrasing it, the branch
lines are read from git by what each branch *carries*, and `none` is printed only when all five sources were
read and empty.

A finished run's state is not lost when the next run starts: `state.sh init` keeps the file it replaces under
`runs/<run.startedAt>.json` beside the state file (`LOOP_RUNS_DIR` in `loop.conf` moves it), and
`tools/loop/report.sh --state .pi/issue-loop/runs/<startedAt>.json` reports on that run afterwards — the
counters, the verdicts and the stop reason it recorded (issue #207). Replacing a state that never recorded a
stop reason is announced on stderr and never refused, so a crashed run stays restartable. The budget line
subtracts one run's spend from that same run's budget: when the state's window closed and it recorded no stop,
the line reads `budget left unknown: these numbers belong to another run — …` instead of an impossible
negative number. Everything the tail names is read from the state the report was pointed at, so `--state`
changes what the action list describes as well as the budget line.

## What changed when the loop became a script

Recorded so a reader of the old name is not surprised. The skill kept its name and its rules; the mechanics
it points at moved:

| was | is |
|---|---|
| the session was the entry, and the budget came from an `ask_user` | `tools/loop/run.sh` with flags, whose defaults are recorded in `tools/loop/loop.conf` |
| the orchestrator held the state in `.pi/bugfix-loop/run.json` and drove `scripts/loop-state.sh` | `tools/loop/state.sh` owns `.pi/issue-loop/run.json` (the old file is left alone so an open run is not orphaned); `scripts/loop-state.sh` is a forwarder |
| the orchestrator wrote labels and the item's bookkeeping itself | `tools/loop/labels.sh` and `tools/issues/update.sh` / `close.sh` do it, and the run never writes the authorisation label |
| the phase contract lived in this file | the phase contract is `tools/loop/prompts/worker.md` / `reviewer.md`, and this file owns the rules |
| branch, worktree and commit were steps a person or a session ran | `tools/loop/worktree.sh` creates the worktree, the branch and the two commits; nothing pushes unless `--push yes` |

Still true, unchanged: the gate above, the three invariants, the stop conditions, the failure handling, the
report, and the rule that an item that needs a decision is escalated rather than abandoned.

## Reference

`REFERENCE.md`, beside this file, carries what a run needs but no rule of its own:

- **Running the loop in a worktree** — the five facts that shape verification inside one, including where
  a child process is started (the wrapper now performs the recipe).
- **Measured costs** — the minute budget of a change, a skip and the gate, and the measurements behind the
  rules above, including the 1.5 spread the measured floor is derived with; read it before planning a run's
  clock.
- **Field notes** — ten failure modes, each naming the section above that owns its rule.
