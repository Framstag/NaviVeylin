# Backlog — procedure and recipes

This is the procedure layer of `guidelines/Backlog.md`: the phases, prompts, commands and transcripts that
section refers to, read against the numbered section the base document carries. An anchor here names a section
number the base document carries; the base document states the rules, this companion the procedure that puts them
into practice. See `guidelines/Backlog.md` §7 for the index of the loop's sub-sections, and §2 and §8 for the
lane; the procedure behind the check registry and the portability boundary is
`guidelines/BacklogTooling.recipes.md`.

## §2 The participants and the flow


```
   PRODUCERS                          THE TRACKER                        CONSUMERS
   ---------                          -----------                        ---------

   a person  ────────────────┐
                             │      +---------------------------+
   a declared check ─────────┼─────>│  one issue per item       │
   (sensors)                 │      │  labels: kind · area ·    │
                             │      │  state · provenance ·     │
   the migrated backlog ─────┘      │  priority                 │
   (one-time import)                +-------------+-------------+
                                                  │
                                    +-------------v-------------+
                                    |  classification +         |   the metadata pass
                                    |  persisted priority       |   (interprets only what moved)
                                    +-------------+-------------+
                                                  │
             +------------------------------------+------------------------------------+
             v                                    v                                    v
   +---------------------+          +-------------------------+          +---------------------+
   | the AGENT LOOP      |          | the HUMAN LANE          |          | a person reading    |
   | closes defect-shaped|          | decisions, conception,  |          | a query: what is    |
   | items unattended    |          | anything needing taste  |          | open, what is next  |
   +---------------------+          +-------------------------+          +---------------------+
```

The three consumers are deliberately separate. The loop is a **budgeted batch**; the human lane is
**interrupt-driven**; a reader's query is neither. They share the tracker and nothing else, which is why a
decision the loop cannot take does not block the loop — it becomes an item in the lane.

The metadata pass runs on a **daily schedule of its own** (`.github/workflows/metadata.yml`), through the
deterministic stand-in `helper/backlog-pass.sh`. The heuristic tables need no provider key, so the schedule
keeps the stored priority and classification from going stale without putting a model credential in a
pipeline. The model-reading half of the pass stays an agent-session job when judgement matters.


## §7a Running one item: the script, the child and the package


One command starts a run; the run's script owns the budget, the working tree, the branch, the labels and the
state; and each item is worked by a fresh agent process with a different process reviewing it.

```
the run's script                          the change child (fresh, per item)      a reviewer (fresh)
  preflight: is the identity allowed?       phase A  the root cause, proven by a     the fixed review
  decision cap: too many open decisions?             case that is RED before the fix    contract, must
  prepare one working tree, once            phase B  the eligibility gate             not be the writer
  per item:                                 phase C  the change, planned
    a branch of its own, off the base       phase D  applied — the session ends at
    the change the item names, copied in              the diff, not at the gate
    select, then authorise by label                   │ LOOP-DIFF-READY
    hand over a prepared package                      ▼
    verify, close, record                   (E)      the diff is read first, by the reviewer beside it
     commit, merge, record                  phase E  the findings applied in one pass,
                                                     then the gate — one fresh child
                                            phase F  archived, with the evidence
                                                     (another fresh child, below)
```

What makes it survivable over a long run: the orchestrator is a **script**, not a session, so it reads no
artifact in full; isolation is **structural** (a fresh process) rather than a discipline a session must
keep; state has exactly **one writer** and is written only between processes; and the run decides an item's
outcome from what it reads itself (I10).

What makes it safe: it works only authorised items (I1, I2); it never writes the authorisation itself; git
is the script's (I8); and the tracker credential is not in the agent's environment (§5).

The diff is read **before** the item's gate, and the gate is a phase of its own (issue #758). Measured on this
loop's archive (`python3 tools/loop-spend.py --json`, 2026-11-03, 227 items, 2 591 Mtok): **1 581 Mtok — 61 % — is
spent after the item's first gate**, and 117 child sessions ran the gate three times or more. The reviewer is not
what costs that — 7 522 turns at 53.6 k tokens each, against the worker's 84.5 k at 25 654 turns — it is what a
review *triggers*: a finding a reader could have made from the diff alone was paid for by the gate it did not need
and then by the full pass the rework that answered it made, on top of a full gate of its own. So the change child's
session ends at the diff (`LOOP-DIFF-READY`, `tools/loop/prompts/worker.md` and its `{{GATE_STEP}}` section), a fresh
reviewer reads that diff (`prompts/reviewer.md` with the section `prompts/pre-gate-review.md`, whose item 3 — the gate
reality — it records as not yet applicable), and the child that runs the gate (`prompts/worker-gate.md`) applies the
findings in one pass and then runs the item's **one** gate. The round before the gate is **not** a round of
`--max-review-rounds`: it refuses nothing, it never blocks the item, and its report stays in the item's directory; the
round the budget bounds is the one after the gate, which is where a change is judged on its verification as well.
`--pre-gate-review off` (`LOOP_PRE_GATE_REVIEW_DEFAULT`) keeps the older shape — one child from the diagnosis to the
gate — and is folded to `off` by every run with `--review off`, because a run with no reviewer has none to place in
front of the gate. A change child that runs the gate anyway is read as it always was: the run does not re-run a gate,
and a round before a gate that has already run has nothing to read. The child **grades its own diff first**, against
the review contract's own three predicates — a clause the case that names it does not exercise, a claim in
`design.md`/`tasks.md`/`proposal.md` that the diff contradicts, an artifact the change cites that is not where it
says — because the writer holds the diff before the reviewer does and the finding costs it nothing to make (issue
#812; measured: of the 165 round-1 reports the loop's item archive holds, 103 refuse, and those three classes are
what their 141 blocking findings say).

The gate is where a gated item's **first** child ends, and the archive is written by a second one. Measured on the
archive (`python3 tools/loop-spend.py`, 2026-11-02, 213 items / 524 sessions / 30 795 turns): of the 274 sessions
that ran a gate, **1.43 B of their 1.83 B tokens were charged after the first `LOOP-GATE` line** — 13 700 turns at
104 k of context per turn, against 7 868 at 52 k before it — because everything the derivation had put into that
session (Phase A, the plan, the focused rounds, every tool result they injected) is re-read on each turn of the
evidence-and-archive tail. The tail decides none of it: it reads the gate's line, the change's `tasks.md` and the
gate's own failing output, so it is a fresh child charged the project's baseline once (`LOOP-SPEND-BASELINE
tokens=17056`) instead of the first child's context once per turn, and the four facts it needs travel as prompt
values so it re-derives nothing. A mean item is 2.88 Mtok with no review round, 11.81 with one and 19.16 with two,
which is the trend this split is measured against. The case that pins it is `run#childUsageIsRecordedPerItem`: its
fixture's second child starts at 17 056 input tokens, the item's spend is the sum of the two children, and the
largest context recorded is still the first child's. The repair a refusal buys is split the same way
(`worker-tail-round2-prompt.md`), because a rework round that paid the tail in-session would cost what the item did.
The bound this split moved: the turn bound was the **item's** (`LOOP_ITEM_TURNS_MAX`), read over the one event stream
its children share, so a gate child that spent it left the tail none and the item was blocked at the bound without
its commit — the shape the reviewer's rounds already had, and the reason the split's own case asserts a tail of one
turn rather than a long one. Since issue #784 each role carries its own key
(`LOOP_CHILD_TURNS_MAX_WORKER_GATE`, `…_WORKER_TAIL`, `…_REVIEWER`, …), so the tail is bounded by what a tail costs
and neither the gate's work nor the worker's can spend it.

What a child is started with, and what it therefore re-reads. Its system prompt carries **no project context**:
the adapter the configured command names starts it with `--no-context-files`, because a child started in the
project would otherwise take the `AGENTS.md` it discovers there as a `project_context` section — measured on one
item's own stream, 25 624 of the 33 088 characters of system prompt, 77 % of it — and that section is charged on
**every** turn: 9 151 of the 17 235 tokens a turn cost (`python3 tools/loop-spend.py`, issue #661). The routing a
phase genuinely needs is in its own prompt instead (`tools/loop/prompts/worker.md`, `reviewer.md`): the document
map, the recipe for a document's section list (`tools/look.sh '^## ' --path guidelines/<file>.md`), and where the
rules, the capabilities and the
change artifacts live. For a tier-B item the package also carries the change the item was filed with, as its
`ITEM_CHANGE` block — the name, the path the run copied it to in the worktree, and the readiness checker's verdict
line for that copy — so the child applies a plan a person wrote instead of planning one (`agent-issue-loop`,
"The package carries the change beside the item's text and its decision"). The flag must not take the policy with it, so **the start directory stays the project** —
that is what resolves this project's tool policy — and the case that pins it is `run#childStartsInTheProjectRoot`,
beside `adapter#theChildIsStartedWithoutContextFiles`, which refuses any word that moves the trust, the prompt or
the tools.

The one tool that was missing, and the call it is answered with instead (issue #796). A child's search has to be a
call that runs: `python3 evidence/ctx-grep-census.py` over this run archive reports `CTX-GREP-CENSUS
items=252 items_failed=125 calls=482 failed=282` — 282 of 482 `ctx_grep` calls answered `zsh:1: command not found:
rg` — although the binary sits in the agent's own directory (`~/.pi/agent/bin`), and the recipe every phase prompt and
every guideline header named for a document's section list, `grep -n '^## '`, is refused by the policy the same
adapter loads. So the child was handed one call it could not make and one tool that does not run. Three things
follow, and each has its case: the adapter prepends the agent's own tool directory to the child's `PATH`
(`adapter#theChildIsStartedWithItsToolDirectoryOnPath`) — the inherited `PATH` is the run's, and a run started from a
context without that directory leaves every worker without `rg`, the binary `ctx_grep` and the agent's own
subprocesses look for, while the wrapper keeps working; `tools/look.sh --path <file>` scopes the one wrapper to one
document, so a section list is one call (`look#aPathScopesTheWalkToIt`); and every phase prompt, `AGENTS.md` and the
guideline headers name that call instead of the shell recipe its own policy refuses
(`prompt#thePhasePromptsCarryTheirOwnRoute`). What this tree cannot fix is where `ctx_grep` resolves `rg`: the shell
it spawns does not carry the caller's `PATH` — the same failure reproduces in an interactive session whose `PATH`
holds the directory — so that half is an upstream report, and the wrapper is why no item depends on it.

What it therefore re-reads of its own work, and the one bound on it. A tool result the child asks for enters its
context at its own size and stays there, so every later turn of that session re-reads it: over the archive the
children injected **59.2 M characters** of tool output once and re-read 876.4 M tokens of it — 53 % of the 1.66 B
the archive spent — with a 116.6 k `ctx_read` and a 51.3 k `ctx_shell` at the top (`python3 tools/loop-spend.py`,
issue #660). A result above `LOOP_TOOL_RESULT_MAX_CHARS` (8 000, the archive's p90 rounded up) is therefore cut to
its first 8 000 characters and carries one line naming the tool, the size it had and how to read the rest, and the
trim is recorded beside the child's event stream in the item's directory, where a reader of the item can re-read the
range the notice names. Nine results in ten are handed over byte for byte: the archive's median result is 825
characters and 9.0 % of its 22 901 results are above the bound, which is where 14 % of the spend sits
(`LOOP-SPEND-CAP8K`; the probe is `evidence/result-sizes.py` in the change
`2026-10-27-fix-a-tool-result-over-its-bound-is-trimmed`, which is archived and so is reached by that path only
inside the change). The rule is applied where a result passes —
`tools/pi-trim-result.mjs`, loaded by the adapter with every child — and not by asking the child to read less: the
prompt has asked for that since #246, and the archive still records `LOOP-SPEND-READS calls=4798 duplicate=2174` —
45 % of its reads are a second read of a path it already read. `0` disables it, and a ported project that deletes
the adapter owes nothing by it (`adapter#theChildIsStartedWithTheResultBound`,
`trim#aResultAboveTheBoundIsTrimmedAndNamed`).

The bound's other half, which a prompt states and the trimmer cannot (issue #818). The read tool caps one result at
~2 000 lines / 50 kB (`docs/2026-11-07-reviewer-model-analysis.md:47`), so a large diff must be paged, and a round
that does not page grades its head: over one 891 687 B diff the probe's seven children answered the diff's own last
line byte-exact once (`docs/2026-11-07-reviewer-model-analysis.md:55`, column `C1/C3/C6`). Every phase prompt therefore names the recorded bound, the marker a
cut result carries (`[LOOP-TRIMMED: … read the rest in ranges …]`), the call and arguments that page the rest until
the last line, and the rule that only a window that arrived whole may be quoted —
`prompt#thePhasePromptsCarryTheirOwnRoute` renders the four skeletons and fails when one of them is missing, and
mutation record `258.1` stops the sentence in the reviewer's prompt to prove the case notices.

What it does **not** claim: the project's own tree is not the child's, so a difference between the two readings of
it taken around a child is recorded and not turned into a verdict. The transcript gains one line naming what
changed — the differing status entries and any process whose working directory is inside the project but outside
the worktree — the item keeps the outcome its work earned, and a review round that ran during a difference stands.
Measured 2026-10-23 (#274, #276, #285): 5 of 139 items across four runs were blocked by that comparison, and in
the three cases of that day the child had written nothing there — every write target in the three transcripts lay
under `.pi/worktrees/loop/` or the item's evidence directory. The comparison measures a tree the run itself
rewrites (every landing fast-forwards the base in the project tree) and that a peer session writes too, so two
readings of it are not evidence about one child. `--strict-outside-write yes`
(`LOOP_STRICT_OUTSIDE_WRITE_DEFAULT`, default `no`) restores the verdict for a tree that is exclusive: the
worker's item is blocked, the round-2 worker's item is blocked, and a review round that ran during a difference
is discarded. A difference the run **can** place decides in both modes, and placing one means this: the heartbeat
beside every child reads the project's tree **once per whole second of that child's life until the first difference
is recorded**, on a cadence of its own and not on the reporting one — `LOOP_PROGRESS_SECONDS` says how often a person
is told, and an interval configured for reading progress is not a resolution for evidence (issue #673: the reading
is the only one that can place a transient write, the case that pins it had to lower the reporting interval to 1 s to
be answered, and under that interval a reading delayed past the writer's 2 s life records the item `done` — the flake
the issue reports as 1 run in 3) — and, at that first difference, marks the processes of the child's own process
tree — its process group, or anything descended from it, however it left that group — that **left the worktree**: a
process whose working directory is inside the project and outside the worktree while the tree it came from works in
the worktree. The child itself and the shells the run starts it through are excluded, because
`run_child` starts them in the project and they stand there for the whole item: measured 2026-10-24 (issue #288),
marking that chain turned a peer's write into `blocked` for an item whose work was never touched. What the narrow
rule therefore cannot place is recorded, and the item keeps the outcome its work earned: a shell **below** the
child whose working directory never left the project — the worker's own shell, since `run_child` starts the child
there — and a process that joined the child's group whose parent chain has since died, the ancestor walk ending at
the dead parent (#704). The first shape is pinned by `run#theWorkersOwnShellWriteIsRecordedAndNotPlaced`, which
stages it through the fixture's own hook and not through the action shell, whose `cd` into the worktree is a cwd no
real child has. The reading asks `pgrep` for that tree, the run's only procps dependency since
`pid_in_child_tree`'s `ps` call went with the wide rule, and both of its calls swallow their stderr: a host without
procps answers nothing, so the walk would name only the child, every difference would be recorded and none placed,
and no line would say so. The run therefore refuses such a host at its own header, before a worktree, a branch or a
child exists, and the **suite** refuses it too — a run started by hand on such a host would otherwise record every
difference and place none, and say nothing (#703).
The child's own tree is read **before** the list of everyone else standing in the project, because that
list is a `readlink` for every process on the host — 1.0 s for 731 of them when #288 measured it — and the writer
the mark is about is gone by the time such a reading ends. Two sweeps were what the placement was built on, and
the second one listed none of the nine pids the first had listed 1.1 s earlier, so nothing was ever placed. A
writer is gone when the transcript is written, so a reading taken afterwards attributes nothing: measured
2026-10-23 while adding it, the exit-time sweep found no process for a child's one-shot write and the case built on
it failed with `expected 'blocked', got 'done'`.
What is not relaxed is the landing rule — a base that is still not clean when the item lands
refuses the merge and records the status line it carries, because that guard is about what may be merged onto,
not about who wrote it.

A placement is **recorded where it is read**: the step is recorded beside the reading that took it, in the branch
that runs before the gate, and no pre-gate reviewer, gate child or tail is started for it, because a gate result
about a change standing in a tree the run cannot vouch for is not evidence (#800 — the six items of 2026-10-20 to
2026-10-23 each paid a whole item *and* the gate the worker runs itself (`prompts/gate-own.md`) for that reading).
The item's `blocked` result and the reason for
that shape are written by the outcome block, from the same reading.

And a child has one place beside its change to write. Every child is started in the project's own tree, so a write
with a **relative path** lands in the tree the landing step needs clean and `write beside the change` was the only
path a child had; the item's own directory therefore carries a scratch directory — `<item dir>/scratch`, created
before the prompt that names it is rendered and named beside the change root in each of the three prompts — and a
file written there is no difference in the project's tree, because the run's own evidence directory is not one
either (#800).

## §7b What an item costs, and the bounds on it


What an item costs, and the floor it is refused by. Measured over the loop's runs: an archived change 40–65 min
(gate 5–7 min, review 5–10, archive 5, and #138 took 65 because it needed a second review round), a gated-out skip
10–15 min **with** the cheap-first rule and 20–25 without, a cold loop worktree's first build 11 min (a single test
class is 1–3 min after that), a forced both-flavor gate 4m42s–6m41s — and that run overwrites the previous run's
XML evidence, which is why a cited XML is copied into the change's `evidence/` — and one item 100 min
(2026-10-19, #126: a worker phase of 83 min plus three review rounds, the item the constant floor could not pay
for). The floor `state.sh next` refuses by is therefore not a constant: the **cheapest** of the run's own finished
wall times × 1.5, rounded up — 1.5 being the spread the numbers above already show (65/45 inside the change range,
100/65 for the #126 item). The cheapest item is the one the run has proof it can pay: a run of typical items gets a
floor just above their cheapest, a run of 10-15 min skips gets 15-25 min instead of the 65-minute constant that
stopped it up to ~50 min before its deadline (issue #674), and one expensive item among cheap ones cannot raise the
floor the way a median would — the median of [600 s, 3600 s, 3600 s] is 60 min and derives 90, its cheapest is 10
and derives 15. `LOOP_ITEM_FLOOR_MINUTES` (65 min) is the whole floor only for a run with no measurement, and then
it holds what one item's gate owes (issue #663). The cost is named with the measurement: a cheap history admits a
gated item into the tail, the run's own deadline cuts that item, and it is left for the next run — bounded at one
item per run, because the deadline ends that child and the run stops right after it. The work it left is kept as
the leftover patch, the item's state names that patch and the report prints the recovery, and the cut item is not
escalated as a decision: the clock that ended it is the run's budget, not a finding about the item (issue #674).
It is recorded `cut` there as well, for the reason the review round's own cut is (§7e, issue #785).
That holds for every child the item starts — the worker, the rework worker of a refusing round, and the reviewer
of a review round (the round-1 review of #674 found the reviewer's `LOOP-DEADLINE` line written by `run_child` and
read by nobody, so a cut round bought a rework worker for a change nobody refused) — and the run takes
no second pass over the queue once the deadline has ended one: the pass would re-exclude the item whose branch the
cut run had just made, and the report's decision list would name a decision for the item the clock cut.

The clock is not the only budget an item spends. Measured 2026-10-19 (issue #209) on one host: #126's gate found
194 MB free RAM and 18 GB of swap and spent 30 min inside `:app:kspMobileDebugUnitTestKotlin`, the same 5 680
tests took 3m47s on the same tree when the machine was quiet, and a `free` taken during another run read 467 MB
free / 2 499 MB available with 13 915 MB of 37 887 MB swap used — Firefox 7 600 MB, three VS Code language-server
JVMs 2 282 MB, Gradle/Kotlin daemons 1 625 MB, `cpptools` 407 MB, the loop's own children 649 MB. The loop was the
smallest consumer and the one that failed, and its `wallSeconds` measured the desktop session. The run therefore
reads `MemAvailable` (the file `LOOP_MEMINFO_PATH` names, `/proc/meminfo` here) before an item's first child and
compares it with `LOOP_MIN_FREE_MIB` (1024 MiB): below the floor the item is recorded `blocked` with the
measurement and the run stops, because a shorter `wallSeconds` on a starved host is not a cheaper item.
`MemAvailable`, not `MemFree`, is the number: on the host this was measured, `free` read 8 383 MiB free against
11 505 MiB available at the same moment, so `MemFree` would refuse a host that can pay.

What the gate and the pre-screen are worth, measured on the first run (2026-10-09): 12 candidates, 3 archived, 9
gated out; a second pass over all 31 `class: bug` entries → `eligible: none`; a later run 0 closed, 32 skipped. All
3 archived changes had a red-on-HEAD case (e.g. `42.67 dp < 48 dp`); review took 4 rounds and both FAILs were
about claims and pointers, never the fix; condition 3 rejected 5 of the 9 gated candidates (an owner decision,
unspecified semantics, competing fixes); each of the 3 archived changes touched **one** production file; the gate
read `mobile 1752/0 · automotive 1752/0 · 20 executed · FROM-CACHE 0`. Without the pre-screen 5 of 8 candidates
were ineligible, each rejection taking ~10 min with a citation against a ~40 min analysis. The skip verdicts of
that second pass were `needs-device 8 · needs-decision 7 · in-flight 7 · refuted 4 · too-wide 2 · stale 2 ·
unspecifiable 1 · needs-diagnosis 1` — **not one** died on the item's class, which is why the class is a ranking
input and not a gate, and the category beside it is ranked the same way: the profile's `firstCategories` lead the
queue in the classes `firstCategoryClasses` names (harness bugs), and their other classes are mixed in one after
every `firstCategoryEvery` item.

The state a run replaces, and what a kept tree keeps. `.pi/issue-loop/run.json` is the run's only record, and
`state.sh init` writes over it — so it copies the file it replaces to `runs/<run.startedAt>.json` beside it
(`LOOP_RUNS_DIR` moves that directory) and announces on stderr, without refusing, when the state it replaces never
recorded a stop reason. Measured 2026-10-20: a later 30-minute run re-initialised the file, and the 100-item /
480-minute run under review had to be reconstructed from the console transcript and `.pi/issue-loop/items/*`. Such
a run is reported with `tools/loop/report.sh --state .pi/issue-loop/runs/<startedAt>.json`, and a state whose
window closed without a stop reads `budget left unknown: these numbers belong to another run …` rather than a
negative budget (issue #207). Keeping the worktree is a decision about the *checkout* — the next run inherits the
repository, the submodule and the git state and skips the measured 11-minute cold build — and not about the build
output: no item reads it, every build regenerates it, and it is the largest thing on the developer host's disk.
Measured 2026-10-20 (issue #210), on a root filesystem at 88 % (391 GB of 471 GB): the kept loop worktree 3.2 GB,
of which one module's build output 1.9 GB, and `loop-selftest.*` scratch trees of killed suites 90 MB across four
trees. `tools/loop/loop-clean.sh` reports what is left, `--apply` removes it, and the command is safe to repeat.

What bounds it: the budget is consulted between items, so the run also bounds **one child** by its own
recorded wall-clock — the larger of a floor (`LOOP_CHILD_MAX_SECONDS`, default 45 min) and what one item's gate
owes (`LOOP_ITEM_GATE_SECONDS`, 65 min here, and left out by a project with no gate). The bound follows the gate
because one constant for every item is smaller than an item that owes two gate rounds: measured 2026-10-26, a
`tools/loop/**` item spent 957 s on a red round, 233 s on one focused falsification suite, 972 s on a green round
and 542 s on reading, edit, evidence, archive and report — 2 704 s against the 2 700 s the constant allowed — and
its child was ended 8 s after the green `gate-summary.xml` was written; #664 and #666 ended the same way (issue
#663). The floor `next` refuses an item below is the same measurement: `LOOP_ITEM_FLOOR_MINUTES` is 65 min, so a run
with no history that starts an item with the floor's minutes left can also finish its gate and the phases beside it.
Once a run has measured an item, the floor follows the cheapest measured one (issue #674), and a gated item started
in that tail is ended at the run's deadline with its tree uncommitted and its work kept as a patch — recorded
`blocked`, named by its state and the report, and left for the next run rather than escalated as a decision. A
child that outlasts the cap is ended together with
everything it started — a gate leaves daemons and test workers behind, and those are the memory the next item
needs — its transcript gains one machine-readable line (`LOOP-TIMEOUT: the child exceeded <n> s`), and the item
is recorded `blocked` with that transcript rather than spending the run's clock. "Everything it started" is
more than the child's process group: a descendant that gave itself its own session (`setsid`) is in no group
the cap signals, so the run also ends every process still working in the change root — the test
`loop_pids_under` owns — and names each one in the transcript (`LOOP-ENDED: ended <pid> — its working directory
is inside <tree>`). Measured 2026-10-22: the mutation harness a child had started kept mutating and restoring
the worktree for seven minutes after the run had recorded the child as ended, and its last restore is what
left that tree dirty for the next run (#272). What such a child leaves is **not committed**: at the moment the cap
fires the tree holds whatever the child was doing, so a commit of it asserts work the child never claimed — the
run reads the child's status before it commits, skips `end-item`, and the branch stays where it stood (at the base,
when the item's own worker is the one the cap ended), the tree kept exactly as the child left it (nothing is
discarded without `--reset-worktree`). The rule covers both commits a run makes for an item: the item's worker, and
the rework worker a refusing review round starts, whose round blocked the item instead of committing what it left
(the round-1 review of #271 found that second site unguarded, hours after the rule was written as if it were
universal). Measured 2026-10-22: the worker of
#262 was ended at 23:48 with mutation 19.2 applied to `tools/loop/run.sh`, the run committed that tree 18 s later,
and `if false; then` in place of the corrective-round test stayed on `origin/fix/issue-262-…` until a person
amended it by hand at 05:05 (#271). Measured 2026-10-19: an item's
worker phase ran 83 minutes inside a single wait, and the deadline, read only at the top of the loop, never
fired (#203).

…and the wait that ends on the child's own record reads **one condition that is not a bound** (issue #795): the
record is written by the run's own subshell, so a `kill -0` on that pid is the only reading that tells a child still
working from a child whose writer was killed before it wrote one. It is taken only while the record is absent, and used
only to end the wait — this poll never `wait`s on the pid, which is the shape #227 measured (a poll that believed a
child gone and then blocked for ever behind a `wait`). Measured 2026-11-06: the run started at 06:27:14Z was
reparented to `systemd --user` when the session that owned it died, and it sat in that poll 17 minutes into a
600-minute budget with its lock still held and item #793 still `agent/in-progress`; the cap would have ended the wait
at 45 minutes, an item ended at a bound for a dead child and a lock that would have refused every run after it. The transcript
names what happened (`LOOP-TIMEOUT: the child's writer (pid <n>) vanished before it wrote its completion record`), the
item is recorded `cut` with `bound` as its mechanism (issues #786, #790), and a child that is merely slow is waited for
exactly as it was.

A second bound is **not a clock** (issues #686, #784): `LOOP_CHILD_TURNS_MAX_<ROLE>` (170 for the worker, 80 for the
gate child, 65 for the tail, 75 for a review round and 75 for the round before the gate; `0` or absent = no bound)
ends a child that has spent **its own role's** turns, read from the item's event stream — one `turn_end` per turn,
which the adapter already writes — minus the count standing when that child started, and checked in the same whole
second as the two clocks, so the wait costs one `grep` per second and not one per look. That stream is **bounded**
now (issue #802): `LOOP_CHILD_EVENTS_MAX_BYTES` (16 MiB on this project) is the size past which `tools/pi-child-events.sh`
drops the oldest half of the lines and writes a `stream_rotation` record first — the lines, the bytes and a cumulative
`dropped_turn_ends` — because the file grew for the item's whole life and was re-read whole by this bound every
second. Measured 2026-11-06 over this archive (`evidence/archive-census.py` in the change
`fix-an-item-stream-is-bounded-and-reported`): 254 streams held **3 483 MiB**, p50 11.84 MiB, p90 29.88 MiB, one
63.47 MiB, and a 16 MiB bound leaves 2 573 MiB — above the median item's whole history, so an ordinary item is not
rotated at all. The count adds what the record says was dropped to what the stream still holds, so a rotation cannot
fall a child's count back to zero; `state.sh`'s usage sum still reads the stream that remains, which is the price of a
bounded file and not a second reading of it. It exists because the clock
is the wrong unit for a child that keeps re-reading: cache re-read is **97 %** of what a loop child is charged, so a
turn and not an edit is what a child spends, and on 2026-10-28 the 35 items past 250 turns held **878 of 1 982 Mtok
(44 %)** of the archive's spend at 25.07 Mtok each against 1.23 for the 20–50 band. A child that has spent its turns
is a runaway and not a slower convergence: it is ended the way the cap ends it, its transcript gains `LOOP-TIMEOUT:
the child reached the recorded turn bound of <n> turns (<KEY> for the <role> role)`, and the item is recorded
`cut` with that count and `bound` as its mechanism (issues #786, #790).

**Where a stop bound sits** (measured 2026-11-06, issue #786): each value is the archive's own p95 or above, per
role — worker 170 (p50 77.5, p90 138.5, p95 161.8, max 219 over 346 sessions), gate 80 (p95 69, max 71 over 11),
tail 65 (p95 56.2, max 60 over 20), review round 75 (p95 57.7, p99 68 over 234), the round before the gate 75 (n=12,
max 55, every session censored). The first values sat at the **p75 of a twelve-session sample**, and the first run
that used them ended three of its six items' workers at exactly 110 turns after 4.5–12 min, each leaving a 4–6 k-line
patch behind, and stopped on the streak with 324 of its 480 minutes unused; over the whole archive **77 of 346 worker
sessions (22 %) sit at or over 110**, which is what a stop bound at p75 buys. A bound above the population costs one
runaway child (~18 Mtok at the measured 106 k tokens a turn), and the token band is what flags that item; the cost a
bound at p75 pays is the item that was working.

The bound was the item's first (one count over the stream every child appends to), and that made it decide *which
phase* was discarded rather than whether the item was converging: measured 2026-11-05 over the ten items that ran
all five phases (`.pi/issue-loop/items`, per-child `turn_end` counts), #780 spent 80/34/42/41 turns on its worker,
its round before the gate, its gate and its tail and the round-1 reviewer was ended at 53 of the same 250, and #767's
round-2 worker got three turns before the item's pot ended it; six of that day's eight blocks were this bound, while
the two items that closed used 192 and 241 of it. Each value is the same measurement per role, so a phase is
bounded by its own work; the p95 rule stated above replaced the first per-role numbers, which were that
population's p75 (worker p75 104 over the archive's 341 worker sessions, review round p75 40 over 232, the round
before the gate p75 46, the gate child p75 50, the tail p75 39 over the ten post-#758 items). The
**item's** total is no longer a stop: it is still counted (`metrics.turns`) and reported (the
`calls:` line), the way the token band is.

A turn is a unit of **shape**, not of cost (issue #760): every turn re-reads the whole conversation, so a count of
turns bounds how many times the conversation is paid for and never what one payment costs, and the turn bound
therefore fires at a price it cannot see. So a third bound is priced in what a turn costs, from the reading the
report already takes (`tools/loop/state.sh`'s `child_usage_json`: the greatest `input + cacheRead` a usage record
reports, or a compaction's `tokensBefore`) but which nothing read while a child ran: `LOOP_ITEM_CONTEXT_WARN`
prints one `LOOP-CONTEXT:` line naming the reading and never ends anything, and `LOOP_ITEM_CONTEXT_MAX` ends the
child exactly as the turn bound does, its transcript carrying `LOOP-TIMEOUT: the child reached the recorded context
bound of <n> tokens (LOOP_ITEM_CONTEXT_MAX) at <m> tokens`. Measured over the 300 item records of
`.pi/issue-loop/run.json` and `runs/*.json` (2026-11-05): the largest context per item is p50 107 669, p75 143 124,
p90 174 984, p95 191 815, maximum 244 764. The checkpoint is the p75 (143 124) — one line on about a quarter of the
items, the hardest among them — and the stop is 200 000, above the p95, where a turn costs 1.86× the archive's
median largest context; 10 of the 300 recorded items (3.3 %) reached it, and `evidence/context-distribution.txt`
lists them. Both are `0` or absent =
no bound, and neither replaces another: the turn bound still ends a session that batches badly at a small context,
and *splitting* the session at the checkpoint — a fresh child handed the change and the evidence, the shape
`tools/loop/prompts/worker-tail.md` already has — is the cheaper half of the same idea and a change of its own.

The run's own deadline bounds that child as well: the wait reads `run.deadlineEpoch` from the run's state once a
second — the deadline is a whole-second clock, and reading it on every look would spend the finer granularity's
saving on two forks — and a child still running when the deadline passes is ended exactly as the cap ends one — its
transcript carries `LOOP-DEADLINE: the run's deadline passed at <deadlineAt> …` beside the `LOOP-TIMEOUT` line,
and the run records the deadline as its stop, so a run that ends there is not replaced as one that "never
recorded a stop reason". Every child is read back that way, the reviewer of a review round included: a round whose
transcript carries `LOOP-DEADLINE` was cut rather than answered, so no retry and no rework worker follows it and
the item is not escalated (the round-1 review of #674). Measured 2026-10-20: eleven of fifteen stuck runs were
past their own `--minutes 90`,
the longest heartbeat read `291m30s`, and not one carried a `LOOP-TIMEOUT` line although the cap was 2 700 s
(#227). The completion record the wait ends on is cleared **before** the child is started, not after it: cleared
after, a child that finished inside the window between the fork and the clear had its own record deleted, and
the run waited the cap's length for a child that had already exited — the same symptom, one fork wide.

The clock and the gate are not what a run spent. What an item cost in tokens had no bound either: the report
printed an item's tokens, its largest context and a run-level total and compared them with nothing, so a run that
cost twice what the one before it cost printed the same shapes as a healthy one — over the archive's states the
per-item median rose from 9.51 M to 18.30 M tokens across three days and no report said a word, and the drift was
found only by reading every child's event stream by hand (issue #662). `LOOP_ITEM_TOKENS_WARN` records the band
— 16 858 818, the p75 of the 164 items whose spend a state records, 1.95× their median of 8 656 911 (p90 22 958
848, maximum 44 163 688; read over `.pi/issue-loop/run.json` and `runs/*.json`) — and it is the p75 rather than the
p90 because "twice what it used to" is where 1.95× sits, while 2.65× would sit above most
of the items of the run that was missed, whose per-item median had already reached 18.30 M. The cost is a flag on
about one item in four, the hardest among them — which is the point, since a regression multiplies exactly those.
Nothing in the run reads the key: an item above the band is worked, reviewed, gated and closed exactly as before,
and a project that records no key (or `0`) flags nothing. *Why* an item cost what it did stays the analysis
question, and `tools/loop-spend.py` (skill `loop-spend`) is its tool.

The band bounds *what* an item cost; the shape it spent it in had no number either (issue #757). Every cost in
this loop is turns times context — a turn re-reads the whole conversation — so a session that makes one tool call
per turn pays the conversation again for every independent read it could have batched, and nothing in the report
said so. Measured over `.pi/issue-loop/items` (227 items, 2026-11-03, the streams `tools/loop-spend.py` reads):
**36 825 `tool_execution_end` lines in 33 183 `turn_end` lines — 1.11 calls per turn** — at 78 k tokens a turn and
97 % of the spend charged as cache read; per item p10 0.99, p25 1.05, p50 1.11, p75 1.22, p90 1.32, maximum 1.61,
so no item of the archive ever amortised two calls in a turn. The state now records the two counts the rate is
derived from (`metrics.calls`, `metrics.turns`, summed over the item's children exactly as its tokens are), the
table prints the item's own ratio in a column appended after the band, and the report's trend prints the run's
ratio beside the items below the floor `LOOP_ITEM_CALLS_PER_TURN_MIN` records — 1.05, the archive's p25, which
names the worst quarter, the band the single-call chain sits in. It is the token band's mirror in every other
respect: a **warning, never a stop**, nothing in the run reads the key, and `0` or no key prints the floor as off
rather than as `none`. The rule the number implies is in the worker's prompt: independent reads, greps and lookups
belong in **one** turn, and a path already read is cited rather than opened again. The levers that raise the ratio
are that rule, `tools/look.sh` (#687) and the session split at the first gate (#688); the **stops** it complements are
the per-role turn bounds (`LOOP_CHILD_TURNS_MAX_<ROLE>`) and the item's context bound (`LOOP_ITEM_CONTEXT_MAX`, issue
#760), which end a child inside one unbounded grind.

The shape was one number; **where the spend went** was none (issue #759). Measured over the same archive (227
items, 2026-11-03, the streams' own `tool_execution_end` durations): **89.6 h spent, of which model time 30.1 h,
the loop's gate 35.1 h and the loop's own self-tests 11.6 h — and 1 581 of 2 591 Mtok, 61 %, spent after the
item's first gate**, the reviewer holding 402 Mtok against the worker's 2 168. Not one of those splits was in a
report the loop had written; they were readable only by summing the raw streams by hand. A run that paid two
thirds of its clock inside the gate and a run that paid it reading printed the same shapes — the defect
`LOOP_ITEM_TOKENS_WARN` was added to cure one level down (#662). The item's record now carries the three splits,
read from the stream `state.sh done` already opens: `metrics.phases` (the turns' own `durationMs` as model time, the
`tool_execution_end` durations of the calls whose command runs the project's declared gate (`LOOP_GATE_CMD`) as the
gate, every other tool's as the third bucket — three disjoint buckets, since a turn's duration is the model response
and a tool's the execution after it), `metrics.roles` (each session's tokens keyed by the role its prompt **path**
names — `worker-tail` is the tail, which a role read from the word "reviewer" alone would fold into the worker), and
`metrics.afterGate` (the tokens from a session's first gate turn on, and that session's own tokens). The report's
trend prints the run's three sums beside each other, each `-` with its reason where nothing was measured. It is a
figure, never a stop: nothing in the run reads it, and the keys it would bound do not exist yet.

**The wait's granularity is a fraction of a second**, and that is separate from how often it reports. The poll that
watches for the child's own completion record sleeps `LOOP_POLL_SECONDS` (default 0.05) and the heartbeat beside it
reports on elapsed **seconds** (`SECONDS` against its own base), not on the number of looks it took, so
`LOOP_PROGRESS_SECONDS` still means seconds of wall clock. Measured 2026-10-22 (issue #259): with a whole second in
both places a child that answered at once still cost the run 1–2 s of pure sleep, one run case drives up to three
children, and a copy of the engine whose `sleep 1` became `sleep 0.05` ran `run#recordsAFailedReviewOnTheBranch` in
9 s instead of 15 s — 40 % of that case was the sleep. Re-measured with the case that now guards it, on the base
this was fixed from: 2 008 ms to notice a worker that answered in 150 ms, and 261 ms after. The cap and the deadline
are unchanged whole-second clocks, and the `TERM`→`KILL` grace after either fires is still one second.

**A case is held to a bound, and a stopped run keeps what it found.** The self-test bounds every case it starts with
`LOOP_CASE_MAX_SECONDS` (default 900) and answers one that outran it with `FAIL <case> (exceeded <n> s)` after ending
its process group, and a case's lines reach the suite's own log — prefixed with the case's name — **while it runs**, so
a case that has not ended still names itself. Measured 2026-10-25 (issue #308): a case that never returned held the
suite, and the gate behind it, for **56 minutes**; the gate's log showed 222 `PASS` lines and then fifty minutes of
nothing; the worker that ran that gate was ended by its own `LOOP_CHILD_MAX_SECONDS` with the item's work
uncommitted; and the next item's start was refused for the dirty tree, whose only named repair was
`--reset-worktree` — the work of that item had to be reconstructed by hand from `git status` and a diff (a previous
session had done the same by hand for #260). A run ended by a signal now ends the heartbeat it started: the heartbeat
beside a child is a job of the run's own shell and not a member of the child's group, and the `$stop` file it loops on
is only created where the wait returns — an orphan was still printing `running 56m00s` after its run had exited, and
another, from a self-test of the day before, stood until the next afternoon. Both paths that stop on a dirty tree — the
child the cap or the deadline ended, and the start the worktree refused — write the work they found to
`<item dir>/leftover-from-killed-run.patch` before the run moves on: the tracked diff against `HEAD` (staged and
unstaged, because a refused `end-item` leaves its work staged), each untracked file, and the ignored files under
`LOOP_UNTRACKED_RECORD_DIR` — every other ignored path is named in the patch's header instead of copied, and so is any
file above the size bound. The tree is still kept exactly as the child left it: the patch is a record **beside** it,
never a replacement, and nothing is committed. The suite's slowest case is 9–15 s, and all 223 cases took 273 s on the
same day.

Inside that bound the project's gate is what fills the log, and it streams: each suite's output is read from the
file the suite writes as the suite writes it, and the summary, the tally and the failure text are read from that
same file, so a suite that runs for a quarter of an hour shows its own lines instead of a two-line log. Measured
2026-10-23: a gate run held
its log at two lines while `tools/loop-selftest.sh` ran past eleven minutes, and an earlier red — a build that
died at configuration in 21 s — was found only in the Gradle daemon log, because the gate had kept its output in
a variable (#269).

**The steps that share nothing run together.** The four shell suites a `tools/loop/**` delta owes share nothing but
the read-only engine — each resolves a scratch tree of its own (`mktemp -d` per invocation) and writes a tally of its
own — and only the build has to be alone, because it owns the build directory the suites read through the
environment. The gate ran all five steps in one serial loop until issue #801: `run_step` held one output file, one
status record and one clock for the whole gate, so four independent suites were added instead of overlapped. The
engine now carries each owed step with the kind of list that owed it — `LOOP_GATE_FIRST_STEPS` and
`LOOP_GATE_COMPILED_CMD` run alone, and the suites that follow run up to `LOOP_GATE_POOL_JOBS` at a time (default 4;
`1` is the serial gate) — and each step keeps its own output, status, clock, tally, bound and console block, recorded
in the profile's order once the wave has drained. Measured on this host by the change's own probe (`evidence/pool-probe.sh`
in the change for issue #801): the same three 3 s stub suites cost **10 s** of gate wall clock with the pool at 1 —
the serial engine, and the clock the red case read on the base — and **3 s** with the pool at 3, each step still
writing its own tally. The shape a reader depends on is kept: a wave of one still streams its output while it runs
(#269), a wave
of several prints each step whole under its own `gate: <command>` … `— <n>s` block, which is what
`tools/gate-attribute.sh` attributes a failure and its step from, and a failing step still fails the gate with its own
numbers.

**A gate step is held to a bound of its own.** The run bounds the child and the self-test bounds a case, and a
*step* of the gate had no clock at all: measured 2026-10-25 (issue #311), one `tools/loop-selftest.sh` step held a
worker for its whole `LOOP_CHILD_MAX_SECONDS` — the item's gate log already read `LOOP-GATE: tests=558 failures=0
errors=0` and its evidence check already read `PASS` — the run ended that child, and the item was recorded `blocked`
with its work uncommitted. `gate.sh` now bounds every step with `LOOP_GATE_STEP_MAX_SECONDS` (default 1 800 s), read
from the profile like the other two clocks, and a step that reaches it is ended together with the processes it
started (`set -m`, then `loop_end_group`), named on the console (`— the step overran its bound of <n>s and was
ended`) and in a `LOOP-GATE-STEP:` line of its own tally, and counted as an **error** of that step — so the gate
exits non-zero and says which suite overran instead of silently spending the child's budget. The default is ~5× the
slowest step this host measured: 345 s for the whole build-and-test gate (`guidelines/Build.md` §2c, 2026-10-21,
16-core host), against 273 s for all 223 cases of the self-test the same week, and it leaves 900 s of a 2 700 s child
for the phases the gate is not.

## §7c The gate and its suite


A *measured baseline* cannot stand in for that bound: the same gate measured 8m25s, 9m46s and 5m45s on three
occasions, and the same 5 680 tests took 3m47s on a quiet host and spent 30 min at 194 MB free RAM (the measurement
behind `LOOP_MIN_FREE_MIB` in the profile) — a budget raised from the last run's number is a guess about the next
one, and a hung step has no baseline at all. The bound answers the step that never returns, which is the state the
item was filed about.

A suite's numbers are only ever the suite's own: a suite that fails without writing a summary contributes an
**error**, never a case. Its tally carries `tests="0"` with `errors="1"`, and the gate's own line carries the
error count beside the case and failure counts, so a reader can tell "no case ran and it failed" from "one case
failed". Measured 2026-10-23 (issue #268): a hand-run gate's compiled step died at configuration
(`Could not create task ':app:mergeNativeSbom'`, `BUILD FAILED in 21s`, no test task reached its action) and its
tally read `tests="1" failures="1"` — a number the suite never reported, in the record the run's own check
reads for a green claim.

A tally's name is the command that wrote it, and it is never a dotfile: the profile's compiled command runs as
`./gradlew`, and the `./` is dropped before the name is built, so `ls` and a `*.xml` glob find the one tally that
records the build as well as `find` and `grep -r` do. Measured 2026-10-23 (issue #278): the name had kept the
leading `.`, and the round-3 reviewer of #273 counted five files where the directory held six, refusing a round on
a tally that was present.

A gate reads the profile beside its own engine — the tree whose paths it measures — and its log says which file that
was: `LOOP-GATE-PROFILE: <path>` is the first line of a gate that runs something, and a profile read from outside
that tree is named as pinned beside the one the engine ships. The run resolves its own configuration and keeps it
to itself, because a child works in the worktree and runs that tree's own engine. Measured 2026-10-23 finishing
#273 (issue #277): the run exported the profile of the project it was started in, so a change whose delta rewrote
`LOOP_GATE_COMPILED_PATTERNS` was gated by the rule it was replacing — the log named the compiled command first
where the tree's own profile owed five shell checks and no build, and it carried no pin at all, so the round-3
reviewer could only refuse the round and the repair was a hand-pinned re-run.

A child may pay for the full gate only the number of times the profile records: `tools/loop/gate.sh` leaves one
**record per full run it starts** beside the stream the child's adapter keeps (`<events>.gate-runs`), under the
`LOOP_CHILD_EVENTS` the run exports, and counts the records that carry the id of the session it is running in, so a
run that would exceed `LOOP_GATE_RUNS_MAX` is refused with exit `3` instead of run — it names every run already
started, names the bound, and prints `scoped: <command>` for every command the delta owes, which is `--plan`'s own
list, so the child is handed a scoped step rather than a third full round that repeats suites the first one already
passed. The count is the child's and not the item's because the session id is: the stream holds every child the item
has had, and the run's own phase split gives the gate child its first full run. The signal is produced by the run it
counts, and not read out of the stream's own tool calls: a command that *mentions* the gate's file — the `grep` and
`sed` a child working on the gate runs over it — cannot be told from one that runs it.
Measured 2026-11-03 over the archive's 227 items and 558 child sessions (`.pi/issue-loop/items/*/events.jsonl`,
durations from each `tool_execution_end`): the gate was started **910 times and spent 35.1 h** — 60 % of all tool
wall clock — 245 sessions ran it at all, and **117 of those (48 %) ran it three or more times** (#670 44 times in
174 turns, #116 39 times in 152). A full round is 600–1 100 s, so a 480-minute run could spend its budget on gate
rounds and never reach the review (issue #756).

A suite the gate owes can narrow itself to the delta being measured: a profile's command may carry `{base}`, which
the gate replaces with the base it resolved, and the loop's self-test is run that way. Its cases declare the files
they can be affected by in the first line of their body (`# files: <token> …`, a token ending in `/` for a
directory), `tools/loop-selftest.sh --files <path> …` / `--from-git <base>` selects on that declaration, and a file
set that names no case is refused with exit 2 rather than read as an empty suite — the rule the mutation harness
has applied to its own records since #235. A caller handed a real delta asks a different question — does this delta
owe any case? — and its ordinary answer is no: most paths in this repository, application code and capability specs
among them, are declared by no case, because a case declares the engine scripts it can be reached through. That is
why the refusal has a third outcome beside it: `--allow-empty` is the caller saying it expects emptiness, and it is
answered with `LOOP-SELFTEST: no case declares the delta` beside the file set it read and the declared set it
selected against, nothing run and exit 0, so the step's log says which delta was covered by nothing rather than
saying nothing. The flag states the intersection and nothing else: without it, a path the tree has and no case
declares is the refusal it was, and CI's own step passes it (`--allow-empty --from-git <base>`, the pull request's
base else the pushed branch's `before`, the whole set when that value is the all-zeros SHA). A delta to the library, the profile, the case file or the driver itself
owes the whole set, and so does the archive: a case reaches behaviour through the scripts it starts, and only a
hand-run of all of them covers that. Measured 2026-10-23 (issue #276): 190 cases and 723 s were owed for every
`tools/loop/**` gate run, a delta to `tools/loop/worktree.sh` alone is declared by 31 of them, and a delta to
`tools/loop/run.sh` by 110 — the end-to-end `run#*` cases are the half that cannot narrow. Measured the same day:
the declaration was generated once from the case bodies and is kept honest by
`driver#everyCaseDeclaresTheFilesItCanReach`, which reads every body and refuses a declaration narrower than the
case it describes.

That case is the suite's cheapest and was, until 2026-11-06, reachable only through the suite's most expensive command
(issue #792): a delta that wrote a declaration owed `tools/loop-selftest.sh --from-git <base>` and nothing cheaper, so
#790's attempt passed the 11 cases its own `--case` selection owed and was refused by the one case that reads a
declaration twelve minutes into its gate — `1 of 320 cases failed`, reported by `tools/loop-selftest.sh --from-git
528d8e2a` (435 s) after `./gradlew test` (285 s) — over one path token missing from a comment, and the attempt was
recorded `blocked` with nothing archived. The profile's `LOOP_GATE_FIRST_STEPS` has the suite map's `<glob>|<command>`
shape and the gate reads it before the build and before every suite, so `tools/loop-cases.sh` owes
`tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach` first. The child's own step is written the other
way round: `tools/loop/prompts/worker.md` is an engine template, so it sends the child to the plan's first line and
names neither this case nor this command — a project value in the template would arrive in another project's child
and order it to run a case that project does not have — because the order saves the gate's tail and only the child's
step saves the attempt. Measured on `528d8e2a`: the case alone is 10 s.

The suite runs its cases in a bounded pool rather than one after another (issue #260). A case owns a throwaway
repository, a scratch tree under the suite's root and a process group of its own, and shares nothing but the
read-only engine, so the serialization bought nothing and cost the sum of every case. Measured 2026-10-25 with
`time bash tools/loop-selftest.sh` (the groups with `--group <name> --jobs 1` against `--group <name>`; the wrapper's
output is transcribed in `openspec/changes/archive/2026-10-25-fix-loop-selftest-case-pool/proposal.md`, and no log
holds it) on 221 cases: 4 min 24 s at 439 % CPU for the whole suite, against 1 min 0 s for `state`'s 18 cases run
one at a time and 21 s for the same 18 pooled, and 1 min 8 s against 20 s for `worktree`'s 18. A case is handed a
scratch root of its own (`LOOP_CASE_TMP`, the directory `scratch()` creates the case's trees under) and ends with it:
its process group is signalled, and the processes standing in its own root are swept, before its slot is handed to the
next case — which is why the exit trap's `/proc` sweep, one pass over every process and 0,83 s on a host of 664, is
made per case and not per suite. The worker count is `--jobs` on the invocation, else `LOOP_SELFTEST_JOBS` in the
profile, else the host's CPU count; the `PASS`/`FAIL` line per case, the counts, the summary and the exit status are unchanged.

The suite's wall clock was the runner's core count and nothing the suite did could shorten it, so the pipeline runs it
as **legs** (issue #781): `.github/workflows/build.yml`'s `loop-suite` job is a matrix of three, `fail-fast: false`,
and a leg is the driver invoked once per group of its own selection — `run` (114 cases),
`prescreen gate state worktree report driver` (138) and the remaining thirteen groups (62) — on its own
`ubuntu-24.04` runner with `contents: read` and no SDK. The driver's `--group` stayed a single value: a leg loops over
its groups in one step, so its log carries one summary per invocation and each names its own selection. A leg owes
what any invocation of the suite owes — its summary counts its cases against the declared set, so a leg that silently
covered less is visible in its own log, and the leg's exit status is a verdict of the run, so one red leg reddens the
run exactly as one red case did when the suite was one step — and the whole set is still owed before a change that
owns `tools/loop/**` is archived: a leg bounds what a push that owes the suite pays, it does not narrow what the
archive owes. A leg needs no pair of its own in the profile's suite map: it runs the driver the map already carries,
narrowed by a selection that runs fewer of that entry's cases, and `gate#theMapCoversEveryPipelineStep` strips the
`--group <selection>` from both sides before it compares (`guidelines/Build.md` §3). Measured 2026-11-05 (issue
#781): `bash tools/loop-selftest.sh --list | cut -d'#' -f1 | sort | uniq -c` counts the three selections above, and
on 2026-11-04 the whole set at `--jobs 4` took 469 s on a 16-core host with `--group run` 261 s of them (56 %),
against the 17 min 15 s the same set spends as one step on a four-core runner (run 41810844107); the three legs
of the first green dispatch (2026-11-05, run 42094414229) took 361 s, 416 s and 107 s, so the suite's wall clock in
the pipeline is the slowest leg's 6 min 56 s rather than the 17 min 15 s the one step spent.

The suite states what it cost, and that figure is what a case is priced against (issue #782). Before this the summary
named its case count, its JDK feature and its selection and nothing about time: a case's start was recorded only to
stop its bound's watcher and was cleared when the case ended, so a group's cost existed only as the difference
between two step durations a person read in the CI log. Now every invocation that prints a summary prints one line per
group it executed — that group's case count and the sum of its cases' own elapsed times — and its summary line carries
the selection's own wall clock beside the counts and the selection:
`loop-selftest: state 24 cases 39.47 s` and `loop-selftest: all 24 cases passed (JDK 27, offline, no token, no
sandbox) — 54.96 s, selection: --group state` (measured 2026-11-06 at base `0fb7a80`, `--group state --jobs 1`). A
group's figure is a sum where the selection's is a wall clock, so the two differ by the pool's parallelism — `driver`
paid 98.87 s of case time in 32.28 s of wall clock at `--jobs 4` the same day — and neither is compared with a stored
number: a baseline's disagreement is a disagreement about the host, not a verdict on the change. `--times` adds the
figure to a `PASS` line when it is asked for and never otherwise, because the mutation harness and the leftovers cases
invoke the driver with one named `--case` and parse what that shape prints.

**Read a group's cost before adding a case to it**, and price a trim against what a case costs and not against what its
fixture looks like it costs: the three groups the plan blamed for paying a prepared project per case (`state` 24,
`report` 20, `prescreen` 30 — 74 cases, 101.97 s of case time, 1.38 s a case) pay 13 ms of that for
`new_project` — 0.8–1.2 % of a case — so a merge of all 74 into one per fixture would have saved 0.95 s of their
101.97 s. The measurement is
`openspec/changes/archive/2026-11-06-loop-suite-cost-is-reported/evidence/trim.txt`, and the report is what
lets a later trim aim at the group that pays.

One tree runs one suite at a time: two suites in one scratch root redden a different set of cases each run, so a
red gate can belong to the neighbour rather than to the change under test. The driver holds a lease in the root the
tree resolved — a second invocation that may run more than one case is refused with exit 2, naming the rule, the
lock's owner and the moment it took it; a lease whose owner is not running any more is taken over and named in one
line — and an invocation that runs no case (`--list`, `--case-files`) or exactly one named case takes none, which is
the mutation harness's shape and the shape a case's own nested suite uses; refusing those would make a mutation run
read a refusal as a mutation it noticed. The lease is a suite's and not a case's: a suite holds it while its cases
run at once, and a case never takes one — a nested suite a case starts resolves the scratch root its own profile
names, which is what keeps the two leases apart. The lease is re-covered after a `kill -9` that leaves it behind, but a pid
the kernel has since given to another process refuses that tree until the process exits, and the refusal prints the
removal. Two worktrees of one repository are one repository and two scratch roots and are not refused, so what two
suites in different trees cost each other is the host's load — measured 2026-10-24, issue #295, where the pair whose
failing sets moved was a change worktree and a detached control worktree: the pair this lease does not cover.


## §7d Refusals, records and the landing

What it does with a child that never started: a status alone cannot tell a child whose argv the environment
refused from one that worked the item and failed, so the run reads the item's own evidence instead — a worker
that wrote no verdict line and left the tree at the base commit never read the item, **whatever status it ended
with**. The status is not the test: an adapter that refuses the child's argv leaves exit 1 and a complaint, and a
provider that refuses every request leaves exit 0 and a 0-byte answer (measured 2026-11-01T23:30:01Z, issue #742:
two items left a 0-byte `worker-report.md` with every `turn_end` event on `stopReason: error`, were recorded
`blocked` on "reported success but the tree is still at the base commit" and escalated, and the run stopped on two
consecutive blocked items 121 s into a 360-minute budget). That is the run's fault, recorded once as its own stop
reason and naming the status, the size of the answer and the child's own last line: no item record is written,
nothing is escalated, the in-progress mark comes off and the item stays exactly as its owner set it.
Measured 2026-10-20: both children of that run died in under 4 s with the same 35-byte complaint, the run
recorded two items `blocked`, escalated both, and stopped on "two consecutive items ended blocked" 3 minutes
into an 8 h budget — two questions nobody had asked, and two of its three decision slots (#212). A child the run ended
itself, and one that left work behind, stay blocked items: the first carries its own transcript line and no
commit, the second its branch, which is what a later pass escalates.

A run one of the traps it installs ended records that signal as its own stop: `HUP` is trapped beside `INT` and
`TERM` — a terminal or a harness that goes away sends it — and the trap writes `interrupted by SIG<NAME>` into the
run's record before the in-progress mark comes off and before the lock is released. 50 of the 105 runs
this repository has archived record no stop, 18 of them having worked items, so a third of the loop's own history has
no cause, and an item a run left in progress is the one thing a person has to find by hand before the queue can take
it again (#755). The item's own record is the one it always was — `blocked`, with "interrupted by a signal" — and the
cases are `run#interruptedRunEndsTheChildItStarted` and `run#aHupEndedRunRecordsItsStopAndUnpicksItsItem`.

What it does with a tree the child claims and did not commit: the reading that needs no child at all is the item's
HEAD against the run's base, so it is taken where the run itself makes the commit — the child the gate is deferred to
stops at the diff, the run commits that tree and reads the HEAD it just made, and a HEAD still at the base ends the
item there (`tools/loop/run.sh:2075`). Nothing follows that reading: the reviewer and the gate child are already
guarded on the same HEAD, the tail is guarded on it too, and the item is recorded `blocked` on "reported success but
the tree is still at the base commit — nothing was committed" — the reason the outcome block's own arm writes, kept
there as the invariant rather than written twice. It is not the never-started stop above: a child that answered the
diff marker did read the item, and its own claim is what says so. Measured 2026-11-06 (issue #797): eight records over
the archive carried that reason, each read after every child of its item had been paid for (1 915 s for #260, 1 422 s
for #319, 916 s for #721), and with the gate deferred the same tree beside an answer that claims a diff left no item
record at all — the tail's empty answer was read as "the child could not start".
`run#anEmptyTreeIsRefusedBeforeTheGateChild` is the case, and `tools/loop-mutations.sh` 91.1 the record that keeps it.

What it does with a start the worktree refuses: a tree that is not clean is the *previous* item's unfinished work
— `start-item` refuses it, because committing over it would attribute two changes to one branch — so the run
attributes the refusal before it ends on it. The worktree's own branch marker names the item whose branch the tree
is on, which is the reading the start-time pre-flight already makes, and that item is recorded `blocked` with the
leftovers and escalated, exactly as that pre-flight records it; the stop reason names the item and the files it
found, not the refusal's first line. The candidate that could not start is neither recorded nor escalated:
nothing was done to it, so it stands in the queue as its owner set it. The run then stops, and continuing is not
an alternative it has — the tree is still dirty and nothing is discarded without `--reset-worktree`, so every
later candidate would be refused the same way and one cause would leave one blocked item per candidate. That
argument is about dirt the run did not make. Dirt it *did* make it discards and carries on from: a child this run
ended at its cap left the tree dirty and `keep_leftovers` wrote that work to the item's own patch, so the record the
refusal protects is in place beside the tree — the run discards the tree with the same verbs `--reset-worktree`
runs, says which item's work it discarded and where the patch is, and retries the start once. A retry refused again
stops the run with that refusal's own reason — the dirt the run itself removed is not what the stop names — and it
does not write the item's patch again, because the tree it would read is the one it just discarded. While
this happens one item keeps one record: a second account of an item the run already recorded joins the note that
record has, because the report's four tallies, its budget-left figure and the "two consecutive items ended
blocked" stop condition are all derived from the same list of records. What the later account may write is the note
and the escalation label and nothing else: the first record's `result`, `verdict` and metrics stand
(`tools/loop/state.sh:443`–`446`), so an item that ended `done` is not turned into a `blocked` one by the leftovers
found later — the stop reason is what names that item, and the tally that counts it stays the first account's.
Measured 2026-10-23: the run stopped 166
minutes into an 8 h budget with `the item could not be started: loop: the worktree is not clean — refusing to
start #270:`, 7 eligible items unworked, the leftovers attributed to nothing and #270 carrying no state entry at
all (#284). Measured 2026-10-26 the other way: #310 was ended at its cap at 08:08:45Z and its patch written at
08:08:38Z, the start of #309 was refused at 08:09:04Z and the run stopped having spent 166 of its 400 minutes —
234 minutes and the queued item unspent, for work that was green and archived (#664).

The run owes the commits it makes an identity: it is the login the run's own gate preflight resolved (the
identity that authorises it), as that login's noreply address — never the machine's `~/.gitconfig` and never
an invented one, because a fresh container or CI runner has neither and the item would block on its own
commit (`fix-loop-git-identity`, issue #167).

The branch is the whole of an item's fix until the base has it, so the run merges the branch it committed
before it closes the item: the branch's commits are fast-forwarded onto the base the run recorded, the base
advances to that merge, and the closure names the branch. A merge the run cannot take — the project tree the
base branch is checked out in is not clean, or the branch does not descend from the recorded base — blocks the
item with its branch as the evidence rather than closing it, because `status/fixed-by` and "the fix is in the
tree" have to be one claim: measured 2026-10-20, a run closed #201 with its two commits still on
`fix/issue-201-…` and the base at the run's base, and a person fast-forwarded `main` 35 minutes later (#229).
The merge lands in the project's **own** tree, and what a merge there moves is the branch that tree holds — so
the landing step reads the checkout instead of assuming it, and refuses a tree switched off the base branch
while the run is open, naming both branches (the start's own guard covers only the tree a run is *opened* on,
#301). Without it a checkout that merely fast-forwards absorbs the merge: the item's commits land on a third
branch, `main` does not move, the run re-reads the unchanged base and closes the item with `status/fixed-by` on
a fix the base never had (issue #308, from #301's review, 2026-10-24). For the same reason the base the run
records is only ever a commit the base *branch* carries: `set-base` asks the branch and refuses anything else,
so a closure cannot be recorded against a tree the base does not have.
The same pre-flight reads that tree's **uncommitted work**, because every merge the landing step makes lands there
and refuses dirt: a run whose project tree carries a modified, staged or untracked path git does not ignore is
refused at exit 5 before the pre-screen, before any item and before any adoption, with the paths printed and
recorded in the stop reason `report.sh` prints. It is a refusal and not a cleanup: the kept worktree's
dirt has `--reset-worktree` because the run wrote it, while this tree's dirt may be its owner's edit made while no
run was live, and nothing the run can read tells the two apart, so the refusal discards nothing. Measured over this
loop's archive (#798): four items were recorded `unmergeable` for a tree the landing step refused only after their
budget and their gate (#668, #675, #769, #782), the last of them for two files that cost one run and two items.
No item is replayed onto a tree nobody reviewed: a branch that is not the run's own work is `adopt-item`'s
decision, taken on what a stranger to the run can check. A review round is part of that record, in both
directions: the run commits the round onto the branch before its reviewer starts and commits the answer when
it arrives, so a round whose reviewer was killed mid-answer reads as unanswered instead of as a pass. Measured
2026-10-22: the round-2 reviewer of #249 was killed, its report was 0 bytes, and the run that started 15
minutes later adopted the branch, merged it into `main` and closed the item — no reviewer had graded the change
(#262). A branch whose last round answered `PASS` is adoptable; one whose round was announced and never
answered, and one the reviewer refused, is the owner's, and one that carries no round at all was never asked to
a review (`--review off`) and stays adoptable.

The adoption the run takes by itself (`adopt_finished_exclusions`) has a result of its own, and its two refusals are
not the same fact. A branch whose own evidence does not admit it — no archived change of its own, no green
verification record, a reviewer's refusal, a round announced and never answered — is the owner's, and the
pre-screen records it as the decision the run escalates. A branch that **is** admissible and still cannot be
merged is the tree's or the base's refusal: the project tree is not clean, it is on another branch, the base
moved — each of them a state a later run finds whole, so the item is work. A branch whose replay conflicts is the
same work retried and unresolved by the run: nothing here rebases or reworks it, so it is refused and recorded
again on every run, with no question asked while the branch stays stale. The run records that refusal as the
item's mechanism `unmergeable` beside the pre-screen's exclusion
(`state.sh adoption-refused`, which keeps the verdict, the reason and the branch as the evidence and refuses to
write a second mechanism onto an item the run has already recorded), escalates nothing and spends no decision
slot, and the report prints it: `excluded: #21 needs-decision (unmergeable)`. Measured 2026-11-06 on #760: the
pre-screen excluded an item whose branch `branch-state` read `adoptable` the whole time, the adoption had failed
on a project tree that was not clean, and the run escalated it after its owner had answered the only question
it had — one of that run's ten decision slots.

A number a change carries is the output of a command in its record, or it is not a claim. The worker's package
hands it the block that reads the change's own numbers — the paths, the checks the pathspec owes, the tallies the
tree carries, the suite's declared case count with the command that printed it, the gate's own timing, and the
delta's scenarios against the cases that name them (`tools/loop/measure.sh`, named by `LOOP_MEASURE_CMD`) — and a
number it takes outside that block travels with the command that took it. Both task texts say so: the worker's
that a number with neither is left out, the reviewer's that a number no command produced is a `NOTE:` (a
correction — deleting or fixing it needs no new claim, case or assertion) and only a number the change's own
contract depends on is `BLOCKING:`. Measured 2026-10-21 (issue #244): 11 `VERDICT: PASS` against 29 `FAIL` in the
round-1 reports, with 147 `UNPROVEN` claims, most of them a count or a timing nobody measured — and a second round
is another whole agent pass, 90 to 99 % of an item's wall clock being model latency.

A branch the run made is a leftover once its work is in the base, and it goes when it goes: the run removes the
branch it committed for an item and merged, releasing the kept worktree from it first (git refuses to delete a
branch a worktree holds) and deleting it with git's own safe delete, which refuses a branch whose work is not
in the base. The run's **end** sweeps the rest — every branch the loop names whose patches the base already
holds — by asking all **four** readings: **patch identity** (`git cherry`), ancestry, identical content of the
files the branch touched, and the branch's archived change on the base. Patch identity is the one the loop needs,
because it lands work as `fix: … (#N)`, so a landed branch is not an ancestor of the base; the four together are
what the item's measurement used, and they keep a branch that merely looks landed from being deleted. The sweep
refuses a branch a
worktree holds, a branch that still carries unlanded work, and a branch whose change carries
`review-failed-round-*.md`, which is its owner's verdict (#185). It prints what it removed and why it kept the
rest, and without `--apply` it removes nothing at all. The manual command that answers "what has the loop left behind" (`tools/loop/loop-clean.sh`) removes the
same landed branches together with the scratch paths. Measured 2026-10-27 on the tree that carried #239: 28
branches were leftovers, 12 of them `loop-*` run branches, and the run's end removed none of them.

The candidate set is the **names the loop makes**, and one of those names is a thing set aside rather than a thing
in flight: `start-item` renames a blocked attempt's branch — an attempt whose rework the run's own bound ended
included (§7e) — to `loop/leftover/issue-<n>-<slug>-<utc>` so the attempt's commits stay reachable while the item is
worked again. That namespace was in no pattern either path enumerated
(`loop_own_branches`, `git branch --list 'fix/issue-*' 'loop-*'`), so a set-aside branch was not *kept* by the rule
that protects unreviewed work — it was invisible to it, and one whose work had landed by hand stayed for ever.
Measured 2026-11-06 (issue #803): eight set-aside branches on this tree, one to three commits each, and neither
`prune-landed` nor `loop-clean.sh` named one of them. It is a candidate now, judged by the same four readings as
every other branch: patch identity removes a set-aside branch whose work the base carries, and a branch whose
patches the base does not have is kept and named as work the base does not have. No disposition record is read
beside it — the branch's own name carries the issue, the slug and the UTC it was set aside, and what was missing was
a reader of that name. The remote arm's two patterns stay two: a blocked attempt's branch is renamed locally and
never published.

The sweep has **two arms**, and both read the base the remote publishes. A branch the loop published is in no local
namespace: the run merges the work on the base and drops the branch it committed, while the push that published it
never deletes a remote ref, so `git branch --list` cannot see it and every sweep missed it. Measured 2026-11-03:
`gh api repos/Framstag/NaviVeylin/branches` listed 16 `fix/*` heads beside `main`, and `git merge-base --is-ancestor
origin/<b> origin/main` held for 10 of them — and the loop's own adoption, a `merge:` commit on the base, left
`origin/fix/issue-728-…` standing (#763). The remote arm's candidates are the names the local arm owns, resolved on
the remote — the two patterns are fetched (with `--prune`) before they are read, so the tips judged are the published
ones and not this clone's cache of the last fetch: read from the cache, a head somebody advanced meanwhile was judged
landed at its stale tip and deleted with the commits it had gained since, unattended, on a shared remote. So a ref
the loop never made is never a candidate, and a head the base does not carry stays whatever this clone last fetched;
its base is the fetched `origin/<base branch>`,
because `run.base` is the pin the run started on and a branch that landed while the run was open is unlanded against
it — that reading kept a fully landed branch with `carries work the base does not have` (#727, #763). Before it
deletes anything on a shared remote it takes the one reading nobody else can make: `gh pr list` says which heads are
open pull requests, and a branch that is somebody's review stays. A forge that cannot be read keeps **every** remote
ref: an outage is not a forge with no pull requests. This is the loop's first unattended destructive write to a
shared remote, so the delete is the remote's own `git push origin --delete`, which a protected branch refuses, and it
carries a `--force-with-lease` for the tip the ref was judged at — a ref that moved between the read and the delete
is kept, not removed with whatever it gained.


## §7e Leftovers, the report and the queue

The report reads the scratch root its own invocation resolves **and** the root the kept worktree resolves, because a
suite creates its trees under the root of the tree it runs in and the gate runs the suite inside the worktree. When
`--worktree` names this tree the two are one directory spelled two ways, so it is scanned once — compared as written
they never match, and the report would list each tree twice and count its bytes twice. The same age rule and the same
process evidence apply to both, so a tree a live suite is writing into stays kept wherever it stands. Measured
2026-10-23: 305 MB in four `loop-selftest.*` trees and four `run.sh` poll loops under `.pi/worktrees/loop/.pi/tmp`,
three of the trees past the threshold and three of the runs past their budget, and the report printed its heading with
no entry at all.

The report also answers for the **trees** the loop made and stopped keeping. The run keeps one worktree —
`LOOP_WORKTREE`, `$LOOP_ROOT/.pi/worktrees/loop` — and the trees of the runs that kept one per item are named by
nothing: not the configuration, not a state field, not a branch marker, so no arm reported them. Measured 2026-11-06
(issue #803): `.pi/worktrees/` held ten directories, the run's own and nine beside it, four of them still registered
with git. The arm walks the worktrees git knows, under that directory only, skips the one the run keeps and the one
the invocation was pointed at, and judges each of the rest by what it holds — the reading a branch gets one level
down: uncommitted changes, or a HEAD the base does not carry, keep the tree and are named as the reason it is kept,
and anything else is named with its size and removed with `git worktree remove --force` only under `--apply`. It
runs after the branch arm, so a landed branch that a tree nobody keeps holds is judged as checked out first and the
tree is released afterwards: the next run finds the branch landed and removes it, rather than this one deleting a
branch a live tree stands on. A directory under `.pi/worktrees/` that git does not know as a worktree is not read at
all — removing it would need the refusals `loop_scratch_remove` owns for the build paths, and that is a second rule
for a second case.

The report also answers for the **item directories**, the largest thing the loop keeps and the one no arm named: one
directory per item holding that item's event stream, its package, its reports and its leftover patch. Measured
2026-11-06 (issue #802): `.pi/issue-loop/` held 3.6 GB over 264 item directories, one event file 63 MB, and
`loop-clean`'s arms were the kept worktree's build paths, the scratch trees, the branches, the worktrees and the
change sources — none of them this. They are also the loop's evidence, so a directory is released only when two
readings agree: nothing inside it has been written for `LOOP_ITEM_ARCHIVE_DAYS` (14 days here, which released 105
directories and 1 411 MiB on the day it was measured and nothing younger), **and** the run's own state records the
item `done` with `metrics.closed: yes` — the offline reading of "the tracker closed it". No record, another result,
or a landing the run was not allowed to close is an issue still open and still citing its directory, so it is kept and
named with that reason. `--apply` removes exactly what the report named, a state file that cannot be read keeps every
directory, and a `0` retention is the switch that releases none.

What it does with an answer that arrived cut: the report a review round is decided from is the file the child's
answer reached, and what reaches that file can be short. On 2026-10-20 an item's round-2 report stopped
mid-word — `… and the post-restore gre`, 6 067 bytes, ending on the newline the adapter adds — while its own
text recorded all seven delta scenarios as PASS and round 1's blocking finding as closed; it carried no
`VERDICT:` line and no `LOOP-BLOCKING:` line, so the run read the missing count as a refusal, took another
worker round, recorded the item `blocked` and escalated it, and a person had to read the report to learn that
nothing was wrong with the work (#224). The **shape** of the answer decides it, never its content: the adapter
prints exactly one newline after every answer it hands the run, and the prompt asks for a `VERDICT: PASS|FAIL`
line in the report's closing block, so a report that carries no `VERDICT:` line — or one whose last byte is not
the adapter's newline — is an **incomplete child answer**. The missing line is what caught the measured
incident; the missing newline covers a report that was never handed over whole. The line is read where it stands,
fence or no fence: a reviewer that writes its closing block as inline code — `` `LOOP-BLOCKING: 3` `` then
`` `VERDICT: FAIL` `` — wrote the line its prompt asked for, so one backtick at each end is taken off before the
answer is judged, and both the completeness reading and the count are the ones inside the fence (#793; measured
2026-11-06, both of item #788's pre-gate reports ended that way and a complete refusal was escalated as no
reviewer having refused the change). The run says so, asks the same
round once more (same prompt and same diff, the answer kept beside the cut attempt as
`reviewer-report-<n>-retry.md`) and decides on the answer that arrived. A
round the run itself ended already says so in its report (`LOOP-TIMEOUT`) and is not retried; two cut answers
in one round block the item with *the reviewer's report was cut short twice*, and the branch carries no
review-failure marker, because no reviewer refused that change.

What a bound cut is, and what it is not: a round the run's own bound ended — the run's deadline, or the role's
turn bound — carries `LOOP-TIMEOUT`/`LOOP-DEADLINE` and no `VERDICT:`, and reading that as a refusal named a
reviewer that refused nothing and bought a rework worker for it (#247). The round being *cut* was recorded, but the
**item** was recorded `blocked` and escalated, which is a different fact and was wrong: measured 2026-11-05 on
#780 — its tail wrote `LOOP-VERDICT: closed`, its gate read `tests=460 failures=0`, `evidence-check.sh` passed, its
change was archived and its branch held three commits — and review round 1 was ended at the turn bound after 53
turns, after which the item was recorded `blocked`, given `agent/needs-decision` and `status/on-hold`, and counted
towards the run's two-blocked stop; #783, #767 and #763 reached the same state the same day. An item the loop's own
bound cut is therefore recorded **`cut`**, the fifth result beside `done`, `skipped`, `blocked` and `falsified`:
it writes no decision label, it is named on a line of its own (`cut short by the run's own bound: …`) instead of in
the blocks, it does not enter the two-blocked streak `state.sh next` stops on, and the run takes no second pass over
the queue after it, because that pass would re-exclude the item whose branch the cut child had just made (#674's
rule, one result wider). The same holds one level down, for the **item's own child** (issue #790): a worker, a gate
child, a tail or the rework worker of a refusing round the run ended at its role's turns, at the child clock or at
the run's deadline leaves a phase, not a finding — #788, #778 and #759 each sat in one run's state as `blocked`
with a bound's reason and a 4–6 k-line patch beside them, so the next reader had to read the mechanism to know the
item was work. What the branch carries is that phase, recorded where a later run reads it: a round the **reviewer's
child** never answered is `review-round-<n>-cut.md`, with the announcement of the same round withdrawn in that
commit, so `branch-state` reads `adoptable` rather than `review-unanswered`; a refusal whose **rework** the bound
ended keeps both the announcement and the refusal, and puts `review-round-<n>-rework-cut.md` beside them (issue
#788), so `branch-state` reads `rework-cut` — a phase its next attempt resumes, never a question — and the next
pre-screen offers the item as work with its branch named, `start-item` setting that branch aside. Both readings
are the same rule: nothing the loop ended itself may arrive at the pre-screen as an item its owner owes an answer
to, because that answer is a slot of the decision ceiling spent on a question the loop asked itself. A round the
*transport* cut twice is not this: no bound ended it, so it stays a block with its reason, because nothing about it
says the item is converging.

A **block** the run's own bound caused was the same fact one result over (issue #786), and is now that fact: the
item's tree was left mid-phase, its work is kept exactly as the child left it and its patch is named in the
record — but its result is `cut`, like every other phase the run's own bound ended (issue #790), and the mechanism
`bound` rides beside it as the third value of the vocabulary beside `refused` and `unmergeable`. That one word is
what a state's own table and a mechanism's spend are read from, and it is never the word a reader needs to tell
work from a refusal: no decision label is written (so the cut spends none of the decision ceiling), the note says
the attempt waits for the next run, and `state.sh next` never reads a cut as a refusal, because a refusal predicts
the next item's failure — the same gate, the same provider, the same tree — and a bound predicts nothing about an
item that starts a fresh worker on a fresh tree. A state a run wrote before issue #790 carries the same fact as
`blocked` with `stopReason: bound`, and `next` still keeps that pair out of the streak, so a run that resumes an
older state reads it as it always did. Measured 2026-11-05: five of six runs stopped on the two-block streak with
758 of 3 211 budgeted minutes used, six of that day's eight blocks were bound cuts, and the first run carrying the
per-role turn bounds ended after 156 of its 480 minutes with three of six items ended at exactly 110 turns.

What the round a corrective refusal buys is handed: a refusal whose every finding is subtractive —
`LOOP-CORRECTIONS-ONLY: yes` — at the round budget buys one more round (#187), and that round exists to decide
whether those findings were answered, not to grade the change a second time. So it is handed the correction
round alone, `git diff <the refusing round's HEAD>..HEAD`, with the refusing report quoted in its prompt; a
finding the tree still carries is a `BLOCKING:` finding there, and a correction whose hunk is missing is an
unanswered finding rather than an absence to report against the diff. The range is why the round is affordable
at all: the whole change is the #204 reading surface, measured at 435 KB and 9 935 changed lines (9 364 of them
generated output) for item #126, and two rounds have already graded it. What the narrowed input does *not*
remove is the reviewer process: #245's owner kept the round and narrowed what it reads, so the re-reading is
what this change saves and the 300 to 420 s a round costs still stands.

What it does when it cannot proceed: an item that needs a decision is **escalated** — the work already done
is named on the item, the item is marked as awaiting a decision, and the run takes the next candidate. The
work is preserved rather than abandoned (I11), which is the part the prose-era loop got wrong by treating a
mid-item decision as a failure. A label write the tracker refuses is a fact of the run, not a swallow: the
transition that failed is recorded with the item, so the report names an item whose decision was never written
and an item whose in-progress mark the run could not take off — measured 2026-10-23T11:38Z, a run recorded
item #260 blocked with no decision label while nothing was ever posted on it (issue #279).

The report a run prints at its end is the run's product, and it answers three questions without the caller
opening the tracker (#208): **which** items were handled and how each ended — the per-item table, with the
item's own recorded reason when the result is not a closure, and, when a block stopped on one of the loop's two
known mechanisms, which one (`refused`: the last review round refused the change; `unmergeable`: the base would
not take the branch), so the spend beside it is totalled by mechanism from the table instead of from the note's
prose (issue #280 — 198.8 M of refusing rounds against 22.9 M of merge refusals over the archive, both recorded
as `blocked`); **what each one cost** — the wall time
`done --wall` records, and beside it the spend read from the child's own event stream: the tools it called with
their count, the tokens it used and what they cost, the largest context it reached, with `-` where nothing was
reported rather than a zero that reads as a measurement (#211) — beside the run-level `spent`, and each item's tokens read against the band
`LOOP_ITEM_TOKENS_WARN` records: the row of an item above it carries how far above it is in a column of its own
and the run-level line names the items above it, which is a **warning and never a stop** (#662), and the row's
last column carries the item's tool calls per turn, the shape the spend was made in, read against the floor
`LOOP_ITEM_CALLS_PER_TURN_MIN` records and named for the items below it (#757); and **what the
caller must now do** — a named action list
whose lines are each read from the state or from git: an owed decision with the question the run wrote (or,
where the state does not hold it, where it is), a branch that carries a patch the base does not have with the
item that owns it, the kept worktree, an item whose gate never completed with its transcript, and a label write
the tracker refused with the item it left stale. A count is not
an answer, so `none` is printed only when all five sources were
read and empty — it never hides a branch that carries work.

Every one of those figures is a comparison, and since #815 the bound it is read against travels with it: the state
records the three numbers its children's numbers were measured against — the character bound a tool result is cut at
(`run.toolResultMaxChars`) and the two context bounds an item's largest context is read against
(`run.itemContextWarn`, `run.itemContextMax`), each the value the configuration recorded and `0` when it records none
— `state.sh show` prints them on a `bounds` line and the report names them beside the budget. Both readings depend on
that number: the archive's `LOOP-SPEND-CAP4K` against `LOOP-SPEND-CAP8K` is two bounds' worth of spend, and an item's
`context` column is only readable as "near the stop that ended it" beside the stop. Without it in the record, the
comparison was of whatever `tools/loop/loop.conf` held when somebody read it — and that file is one line a person
edits between runs, which is exactly how the experiment #791 asks for (one run at another cap) is run.

Beside those three, and about the loop rather than about one run, the report says whether the loop is getting
faster and more often right (#247): the review round-1 pass rate, the item wall median, minimum and maximum, the
run's tool calls per turn beside the items below its recorded floor (#757), and — since #759 — where the spend went,
the clock split by phase (model, gate, every other tool), the tokens split by role (worker, reviewer, tail) and the
share of the tokens spent after an item's first gate, all totalled from the state the report already reads, each
with `-` and its reason where nobody measured one. The
rate is counted from the **verdict** a round ended on (`done --review-verdict`), never from the round count: the
count is the round the loop *attempted* — `run.sh` writes it before the reviewer runs — so a round 1 that refused
and then blocked carries the same `1` as one that passed, and review round 1 of #247 measured exactly that on
`#16` and `#197` (both `blocked`, count 1, `VERDICT: FAIL`). The same count also could not tell a refusal from a
round nobody answered: `#51` carries count 1 as well, and its round 1 was **cut** at the 250-turn bound — its
transcript holds `LOOP-TIMEOUT` and no `VERDICT:` at all — which the run read as a refusal until review round 2.
So the state records the round's verdict (`done --review-verdict`) and four things are told apart and printed: a
round 1 that passed; a round 1 that refused and whose round 2 passed (in the rate's denominator, in no
numerator); a round that ended without a verdict (the answer was cut, the run's clock or its role's turn bound
ended it, it wrote, or the state predates the field — neither a pass nor a refusal, so in no rate); and an item no
round ran for (`reviewRounds` 0, which is `never reviewed` and not a refused round). The third figure the issue asked for,
the landings that reddened `main`, is in neither source the report reads: `state.sh done` keeps a landing's
branch, its commit count and its evidence and no commit, and the `broken-main` sensor identifies its condition by
job and case, so no `gh` read could attribute a red base to the landing that caused it. The report therefore
names the command that answers it (`gh issue list --label broken-main`) instead of reading the tracker from
itself; recording the attribution at landing time is its own item (#709).

What a branch carries is asked by **patch identity**, never by ancestry — `git cherry`, the same question the
item's start and the pre-screen ask. The run lands an item's work as a commit of its own (`fix: … (#N)`), so a
branch whose work was landed, or re-landed, is not an ancestor of the base while its patch is on it: ancestry read
such a branch as unlanded work, refused its item for ever and spent a decision slot on it every run (measured
2026-10-21: one branch at `1` commit ahead and `0` unlanded, issue #228). A branch is a leftover only while it
carries a patch the base does not have, and a landed one is reused by the next start rather than named as work
to land.

An item whose citation nothing accounts for is **reported, not refused** (#281): `tools/issues/resolve-stale.sh`
repairs what the repository's own records state (the full tracked path, a rename, a single surviving
declaration, an archived change's new directory) and reports the rest — the token is named in the run's own
report and the item's state, labels and body are untouched, because a path the tree does not hold may be one
the change itself creates. The pre-screen names the same token in the item's record (`absentCitation`) and
queues the item. Because a park written before #281 is skipped by the next run, it is reversible on request:
`--revisit-parked` reopens an item this tool parked once nothing it cites is absent any more — a body repaired
by hand, a file the tree gained, or a rule that stopped calling a word a citation — and leaves a person's own
hold alone. One reversal is not a reopen: a tombstone keeps its settled state in its own body (the migration's
`Retired` record, whose marker the profile declares as `settledRecordMarker`), the park erased the label that
used to say so, so the reversal restores `retiredStatus` instead of `openStatus` and reports it as `restored` —
otherwise a settled tombstone is read as work, which is what #681 measured on #147: a park, a reversal, and a
worker phase spent concluding `stale`. A citation that stands only in an item's **title** is never rewritten — the resolver writes bodies,
so a bare name there is reported for a person to fix. Two outcomes follow from what the tree holds: a title
naming a file whose tracked path the body already carries leaves the item settled and unwritten (the state every
item repaired from the doubled-directory form ends in), while a title citing a file the tree does not hold is
reported, because the citation is unaccounted for wherever it stands (#226).

What it does when another run is already working: the lock that keeps one run per repository is taken **before**
the run writes anything — before the state it would replace, before the pre-screen's queue and before the
adoption path can merge a branch. A run that finds a live lock is refused and writes nothing, so the live
run's record, its queue and its branches stay its own; a lock whose owner is not running any more is taken over
and the takeover is announced, because a run killed with a signal no trap can catch leaves its lock behind, and
a tombstone would refuse every run after it. The state names the process that wrote it, so the report tells a
run that was killed from one that is still going instead of printing a budget left for a run that is spending
nothing, and it names the item it is working on and the phase it last recorded — `.current`, the first line
`state.sh show` prints — so a state kept from a killed run says which item was in flight instead of a phase
alone, and every step the run records is attributed to that item rather than to `0` (#263). Measured 2026-10-22: the run that started at 18:46 was killed at 19:19 with its round-2 review
unanswered, and the run that started at 19:34 replaced its state, merged its unfinished item into `main`, closed
it and was refused at the lock two minutes into a 240-minute budget.

## §8 The human lane


The lane is everything the mechanism must not decide: a design question, a conception, a taste call, a
bound nobody can falsify, a choice between two defensible fixes.

```
the loop hits a question it must not answer
   -> stop the item, keep the branch and the unarchived change, post ONE prepared comment:
        the question in a sentence · the options with their consequences
        what is already PROVEN: root cause, the failing case, the specification passage
        what is already WRITTEN: the change, the branch, the diff
        what the answer unblocks
   -> mark it awaiting a decision; take the next candidate, or stop if the cap is reached
a person answers (label or comment)
   -> the item returns to the queue and the run RESUMES from the recorded phase
```

The lane is a **sibling workflow over the same tracker**, not a phase of the loop, because it is
interrupt-driven while the loop is a batch. But there are exactly two places the loop meets it: the cap
that stops a run when decisions pile up, and the mid-item escalation. **A pile of open decisions stops the
run** rather than feeding it — the failure the lane exists to prevent is a machine producing more work while
nothing gets decided.

One consequence from experience, and the reason the escalation carries evidence: **conceptual questions
appear during implementation, not only at triage.** A lane that only sees items before they are started
loses exactly what the work already established.


