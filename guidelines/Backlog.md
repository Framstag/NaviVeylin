# Backlog — the concept

The project's backlog, and the automation over it: where an item comes from, what the tracker holds about
it, who may write what, how it becomes work, and where an agent runs when it does the work.

**Read the section that owns your topic, not this whole document.** The section list is
`grep -n '^## ' guidelines/Backlog.md`; AGENTS.md's Documentation Map routes a kind of change here.

## 1. Status, and what this document owns

**Status.** This document describes an **agreed target**, not the tree as it stands. The backlog is the issue
tracker, and the automation over it is the tooling under `tools/issues/**` plus eight skills. The target is
reached by seven changes, in order; each change's tasks include keeping this document true for its own
sections, so the document becomes a description of the tree exactly as fast as the tree moves.

The first two changes landed the tracker and the import, the third the loop and the fifth the metadata pass; this
document's fourth change, the check registry, has landed too, and so has the sixth — the portability proof.
Only the seventh, the human lane, has not. Concretely, on the tracker today: the label
taxonomy exists (`class/` · `category/` · `status/` · `source/` · `agent/` · `prio/` · `sensor/`, plus GitHub's nine
defaults), the imported backlog carries it — **137** items, every one open and none carrying `agent/eligible`,
plus **21** `status/retired` tombstones for the ids whose entry had already been removed before the migration,
so that a `§N` a live file cites still resolves — and `tools/check-todo-references.sh`, one CI step with no
token, proves exactly that on every push.

| # | change | what it lands | state |
|---|---|---|---|
| 1 | `github-issue-tooling` | the tracker surface: taxonomy, gate, identity, closure, the project profile | **applied 2026-10-17** — `tools/issues/**`, the project profile `tools/tooling.json`, four skills, an offline self-test and one CI step; the dry run over the prose backlog is recorded under the change's `evidence/`, and the tracker holds the 36 taxonomy labels and no issue |
| 2 | `migrate-todo-to-github-issues` | the live import, the prose file deleted, every instruction rewired | **applied 2026-10-17** — 137 items imported (open, none authorised) and 21 retired tombstones, `list.sh --id N` resolving a citation, `tools/check-todo-references.sh` and its CI step, every instruction rewired, the prose file deleted; the run's record is `import-evidence.md` |
| 3 | `agent-issue-loop` | the budgeted loop, its isolation, its refusals | **applied 2026-10-17** — `tools/loop/**` (the entry `run.sh`, the pre-screen, the state and its lock, the worktree and the branches, the label transitions, the report), two prompt skeletons filled from `loop.conf`, a stub `pi` and an offline self-test with one CI step; the two write paths the loop needed landed in the tooling (`update.sh --remove-label`, `close.sh --comment-only`); §7 and §11 of this document, the spec `agent-issue-loop` and the `fix-loop` skill describe it |
| 4 | `sensor-registry-and-metric-issues` | declared checks that keep the tracker honest | **applied 2026-10-17** — `tools/sensors/**` (the registry `sensors.json`, the runner, the guards, the report, the hotspot measurement, the four metric rows and the two condition sensors), a fixture harness offline against the stub `gh` with one CI step, the three verbs the runner writes through extended (`create.sh --sensor`, `close.sh --sensor`, the new `reopen.sh`), the isolated pipeline `.github/workflows/sensors.yml` with its CODEOWNERS entry, and `labels.sh` learning the registry's labels; the host dry runs, the falsification run and the not-reproducible CI steps are recorded under the change's `report.md` |
| 5 | `issue-metadata-loop` | persisted priority and classification | **applied 2026-10-17** — `tools/issues/prioritise.sh` (the basis, the partition over every issue, the bounded run, the label+marker writer, the transition comment) and `classify.sh` (the queue of items with no kind), `check-metadata.sh` and its falsification, the `bands` section in `tools/tooling.json`, `list.sh --prio/--unranked`, the `issue-metadata` skill and the `triage-todo` rewrite; the run over the live tracker is recorded under the change's `evidence/` |
| 6 | `tooling-portability` | the proof that another project can use all of it | **applied 2026-10-18** — `tools/check-portability.sh` (the leak check, its value set read from the profile and the per-tool configs at run time) with `tools/check-portability-selftest.sh` and its 21 cases, `tools/mutate.sh` with the seven revert-checks as a versioned mutation set, the two skills `portability-check` and `mutation-runner`, the synthetic project `tools/fixtures/foreign-project/` on which the whole toolchain runs without an engine edit, `PORTING.md` (the recipe, its commands executed by the self-test), one `Check portability` CI step and the check's own classification line; no engine file was changed — the check is the proof, not a fix |
| 7 | `wire-sensor-inputs` | the rows' declared inputs, materialised — and the checks' schedule back on | **applied 2026-10-18** — `tools/build-inputs.sh` (the manifest, `--check` against the registry, the per-module coverage conversion, the result record that survives a red build) and `tools/fetch-build-inputs.sh` (a commit's record, exit 3 = not built yet) with their cases, selftest, eight mutations and one `Check the build inputs` CI step; `build.yml` publishes the record as one artifact, `sensors.yml` fetches it with the workflow's own read-only token and its `schedule` is restored; the host dry run is the change's `report.md` |
| 7 | `decision-queue` | the human lane — the queue, the answer path, the mid-item handover | **applied 2026-10-20** — `tools/issues/decisions.sh` (the queue, each item with the question it carries) and `tools/issues/answer.sh` (the answer verb: comment first, then `--continue`/`--device`/`--retire` through the existing writers), the worker's `LOOP-DECISION` field carried into every escalation; spec `decision-queue`; the twelve-item drain that unblocked the loop is its field test (`openspec/changes/archive/2026-10-19-decision-queue/evidence/drain.txt`) |

**What this document owns.** The concept: the participants and the flow, what the tracker holds, the
**invariants** the mechanism must satisfy, who may write what, and the decisions with the reasons they were
taken and the alternatives they eliminated.

**What it deliberately does not own**, because a second home for a fact is how two documents drift apart:

| concern | its home |
|---|---|
| the behavioural contract, as testable conditions | `openspec/specs/issue-backlog`, `backlog-source-of-truth`, `agent-issue-loop`, `backlog-sensors`, `issue-priority`, `tooling-portability` |
| the reasoning behind one change, and the proof it owes | that change's `design.md` |
| the operational recipe — how to run a thing | `.pi/skills/*` |
| conventions of the app itself | `Design.md`, `UI.md`, `MapRendering.md`, `Build.md` — this document routes to them, it does not restate them |
| the evidence rules a session follows | `Build.md` §2 and its scope block |

The reference input for all of it is `Agentisches-Coding-NaviVeylin.md` at the repository root: the survey
of the approach, the measurements taken from this repository, and the later steps this document treats as
deferred (§13).

## 2. The participants and the flow

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

## 3. What the tracker holds

One item is one issue. Everything a reader selects on is a label, so selection is a query and never a read:

| namespace | carries | why it is separate |
|---|---|---|
| kind | a defect, a quality gap, or a capability that does not exist | it drives the order work is taken in, and it is the only fact a claim's shape is judged from |
| area | where the item lives | it replaces the hand-maintained index that drifted in the prose backlog |
| state | open, in flight, fixed, on hold, awaiting a device, unverified | the lifecycle, read at the moment of a query |
| provenance | imported, filed by a check, filed by an agent, filed by a person | a reader can tell a migrated item from a filed one |
| priority | one of a fixed set of bands, and nothing else | it must be selectable without reading the item |
| ownership | which check owns the item, when a check filed it | it is what bounds that check's reach (§9) |
| authorisation | whether automated work is permitted | the one label whose absence is a refusal (§5) |

The **names of the namespaces and the shape of the item are the contract**; the values inside them are the
project's, held in one profile (§10). As landed by change 1, the namespaces are `class/` (kind),
`category/` (area), `status/` (state), `source/` (provenance), `prio/` (priority band), `sensor/`
(ownership, its values declared by the check registry) and `agent/` (authorisation) — and GitHub's own
defaults are never used for logic. An item also carries, in its own body, the facts a machine needs and
a reader should not have to: the identifier it came from, the fingerprint that says what it is, how many
times it has recurred, and the basis its priority was computed over.

**Priority is a stored fact with a recorded basis, not a number recomputed on every reading.** The band is
selectable; the components and the basis explain it; the value is reused while the basis is unchanged, so
ordering the whole backlog costs a query and a pass over a stable backlog interprets nothing.

## 4. The invariants

These are the rules the mechanism must satisfy. Each names the mechanism that holds it and the observation
that would falsify it — a rule with no falsifying observation is a preference, and this document does not
carry preferences.

| # | invariant | held by | falsified when |
|---|---|---|---|
| I1 | An item is authorised for automated work only by a person, and never by the tooling, a check or the loop | the authorisation label is reserved: every write path refuses it | any write path can set it, or an escalated item becomes eligible |
| I2 | Only an allowlisted author's item is acted on, and the tooling refuses rather than narrows when the identity is wrong | the gate; a refusal before any write | an unlisted author's item is worked, or a wrong identity yields "nothing to do" |
| I3 | Duplication is impossible: one item per distinct finding | a fingerprint; a refusal that names the existing item | the same finding exists twice, or a refusal is indistinguishable from a failure |
| I4 | Deletion does not exist; closure is the only terminal state and carries evidence | no delete path anywhere; closure requires a reason and evidence | any operation can destroy the only record of a finding |
| I5 | A check cannot reverse a person's decision | only a closure recorded as a completed fix is reopened | a check reopens an item a person closed as "not to be done" |
| I6 | A failing check is not a cleared condition | three-valued outcome; only "cleared" may close | a crash, an unreachable input or an unreadable result closes an item |
| I7 | A check acts only on what it owns | ownership label checked at the write | a check closes, reopens or annotates another's item |
| I8 | Version control belongs to the script that starts a run, never to an agent | the wrapper; no phase can invoke git on its own behalf | an agent creates a branch, a tree or a commit, or a push happens unasked |
| I9 | A reviewer never wrote what it reviews | a separate process; a write during review discards the round | a round is accepted although the tree changed during it |
| I10 | The loop verifies; it does not trust a claim | the wrapper reads the tree, the archive and the evidence itself | a claimed success closes an item with nothing committed |
| I11 | An item that cannot be finished is preserved, not thrown away | the escalation carries the root cause, the failing case and the branch | work is abandoned and only its absence is recorded |
| I12 | Exactly one writer per file, per tree, per tracker field, at a time | one lock per run; state written only between agent processes | two writers touch a file, or a bookkeeping edit lands inside another's commit |
| I13 | A project fact exists in one file | the project profile; a check that forbids it in an engine | a repository name, an author, a vocabulary value or a path appears in an engine |
| I14 | Every historical reference still resolves | identifiers in item bodies; a check over the tree | a document cites an item identifier that finds nothing |
| I15 | The mechanism can run somewhere else without a rewrite | one indirection for the agent command; the project's own gate and skills in configuration | a phase names a skill, a command or a path of its own |
| I16 | A state transition replaces the item's state: one item carries exactly one value of the state namespace | the write path reads the item and removes every displaced value in the same edit; one shared scan (`tools/issues/lib.sh`), and the case `update#statusReplacesTheOldStatus` | an item carries two `status/*` labels, or a transition leaves the old value behind — the loop's own matrix case names the displaced label, so a verdict that only adds one fails there |
| I17 | An unreachable tracker is not an answer: a transport failure yields no verdict about an item, its author or a decision | one seam (`tools/issues/lib.sh`, `issues_login`) every `gh` call goes through, and a status of its own — `75` — that no path reads as allow, as refuse or as cleared; the guard's identity step and `policy.sh preflight` both branch on it | an outage is recorded as `author-not-allowlisted`, as "nothing to do", or as a cleared condition |

## 5. Who may write what

The single table that answers most of the questions about this mechanism. **A writer that does not appear
in a row does not write that thing.**

| | a person | a declared check | the metadata pass | the agent loop | the resolver | an agent process |
|---|---|---|---|---|---|---|
| file an item | yes | yes | no | no | no | no |
| classify (kind, area) | yes | its row declares them | yes | no | no | proposes only |
| set priority | yes | no | yes | no | no | proposes only |
| authorise automated work | **yes, and only here** | **no** | **no** | **no** | **no** | **no** |
| repair a citation in an item's body | yes | no | no | no | **yes** — the citation token only, and `status/on-hold` to park an item nothing accounts for | no |
| comment a measurement | yes | yes | yes | yes | yes (one per item per run) | no |
| close | yes | only its own items, only when cleared | no | after a passing review, with evidence | **no** | no |
| reopen | yes | only its own items | no | no | no | no |
| commit, branch, push | yes | never | never | the wrapper | never | never |
| decide a design question | **yes, and only here** | no | no | no | no | proposes only |
| write the run's state | no | no | no | the wrapper, between processes | no | no |

Two consequences worth stating plainly, because both were load-bearing decisions:

- **The tracker credential is never in an agent's reach.** The loop's script owns every write, and an agent
  process receives a prepared package instead. That is what lets an agent run in an isolated environment
  later without a new security design (§11).
- **The authorisation label is the only key to unattended work.** Everything else about an item may be
  machine-set; that one may not. Filing is not authorising. Change 1 landed this as a **reservation** rather
  than a convention: the label is listed in the profile's `reservedLabels`, every write path refuses it in
  any input (exit 3, `label-reserved`), the tooling ships no authorise command, and the self-test drives all
  three write paths plus the other label flags to prove no route sets it. The import is also asserted to
  arrive with 0 eligible items.

Four rules keep the decision queue answerable, because it is a query over one label and the run's ceiling
counts that query (`tools/issues/decisions.sh`, `LOOP_MAX_DECISIONS_DEFAULT`):

- **An answer reaches the next worker's package.** The answer is a comment, and the item's body still states
the question it settled — so nothing but the package can carry it: the run asks the tracker for the item's
comments and the package renders the last recorded decision beside the question it answered (`<<<ITEM_DECISION`,
`recorded: none` when there is none), and `answer.sh` writes that question into the answer with an
`<!-- answers: … -->` marker. The question is the escalation's `**The decision:**` line, never the verdict word
(`needs-decision`) that opens its comment, and the queue reads it the same way (`tools/issues/lib.sh`,
`ISSUES_QUESTION_JQ`) — the verdict word is what a bare answer bound to before that (issue #243). Without the
package carrying the answer a fresh worker re-derives the same fork with a fresh option list, and
the recorded `Decision: 2` silently means a different option (`issue #196`: recorded 04:52, re-asked 08:48). A
worker that reads a recorded decision treats it as settled and says what it leaves open when it has to ask
again.
- **A closure leaves no decision label behind.** An item that carries the label and is closed would stay in
  the queue for ever and keep spending a decision slot; `tools/issues/close.sh` takes the label off in the same
  write that records the settled state, for every path that closes — the run, a person, a declared check.
- **A block asks its own question.** When the reviewer wrote no decision, the run states the only two-way
  choice a block leaves — retry the item, or abandon it — because a reason is not something a person can
  answer (`issue #225`).
- **A reviewed falsification closes the item as mis-diagnosed, and only on an unchanged tree.** A run that
  measured that the item's premise does not hold holds the evidence that settles it, so it closes the item in
  the settled state a decision not to act records, with the measurement as the comment — under the same
  `--may-close` permission a fix closure rides, and only after the review read the claim. A falsification whose
  tree moved during the review is a change, so it is escalated instead (openspec change
  `falsified-items-close-in-the-loop`).

## 6. How an item becomes work

```
intake        a person, a check or the one-time import           -> an item, classified by what filed it
grouping      an item with no kind is classified by a pass       -> kind + area, written by the tooling
priority      the band is computed once, with its basis          -> reused while the basis holds
selection     open + a band + the gates, read from labels        -> a ranked working set, no bodies read
eligibility   author allowlisted AND authorisation label present -> authorised for unattended work
discovery     the loop's own cheap checks before spending        -> pre-filtered queue, unjudged
```

The split matters: **a script decides what can be decided cheaply** (kind, state, author, whether the code
an item cites still exists, whether a device is needed) and **interpretation is spent only where it is
unavoidable** — grouping an item, scoring it, judging whether one cause explains a symptom. The prose
backlog had no such split: every reading interpreted everything.

The intake step is a write, so it is the filing path's job to make an item exist in a selector's queue: a
finding states its `class` and its `category`, and `tools/issues/create.sh` files both as labels of their
namespaces — the fields are required, a value outside the closed vocabulary is refused rather than dropped, and
a field that contradicts the labels the same finding carries is the one-value-per-namespace refusal. An item
filed from a finding whose kind lived only in those fields used to arrive with no label at all, present in the
tracker and in no queue (#199, #200, issue #201).

The last of those cheap decisions has a home of its own: **what counts as a citation** is defined once, in
`tools/issues/lib.sh` (`stale-citation-resolution`), and asked by the loop's pre-screen and by
`tools/issues/resolve-stale.sh` — so detection and repair cannot disagree about a body. A citation that
resolves by a suffix of a tracked path, inside a submodule, or as an artefact the project generates is not
staleness; only a name nothing accounts for is. Measured 2026-10-18: the extraction before that shared rule
was wrong about 48 of 49 verdicts, and two thirds of an authorised set was excluded over prose.

Three consequences the design takes as decisions, not accidents:

- the pass is **bounded per run**, so a project-wide vocabulary change becomes a sequence of bounded runs
  rather than one sweep of the whole backlog;
- **selection never samples**: it examines every item and skips by an unchanged basis, because skipping an
  item that had changed would leave a wrong priority in place indefinitely;
- the conditions that **gate** an item are derived from labels at reading time and never stored with the
  priority, because they move far more often than a score does.

## 7. The loop

One command starts a run; the run's script owns the budget, the working tree, the branch, the labels and the
state; and each item is worked by a fresh agent process with a different process reviewing it.

```
the run's script                          an agent process (fresh, per item)      a reviewer (fresh)
  preflight: is the identity allowed?       phase A  the root cause, proven by a     the fixed review
  decision cap: too many open decisions?             case that is RED before the fix    contract, must
  prepare one working tree, once            phase B  the eligibility gate             not be the writer
  per item:                                 phase C  the change, planned
    a branch of its own, off the base       phase D  applied, with the project's gate
    select, then authorise by label         (E)      reviewed by the separate process
    hand over a prepared package            phase F  archived, with the evidence
    verify, close, record
```

What makes it survivable over a long run: the orchestrator is a **script**, not a session, so it reads no
artifact in full; isolation is **structural** (a fresh process) rather than a discipline a session must
keep; state has exactly **one writer** and is written only between processes; and the run decides an item's
outcome from what it reads itself (I10).

What makes it safe: it works only authorised items (I1, I2); it never writes the authorisation itself; git
is the script's (I8); and the tracker credential is not in the agent's environment (§5).

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
beside every child reads the project's tree once per `LOOP_PROGRESS_SECONDS` and, at the first difference, marks the
processes of the child's own process tree — its process group, or anything descended from it, however it left that
group — that **left the worktree**: a process whose working directory is inside the project and outside the worktree
while the tree it came from works in the worktree. The child itself and the shells the run starts it through are
excluded, because `run_child` starts them in the project and they stand there for the whole item: measured
2026-10-24 (issue #288), marking that chain turned a peer's write into `blocked` for an item whose work was never
touched. The child's own tree is read **before** the list of everyone else standing in the project, because that
list is a `readlink` for every process on the host — 1.0 s for 731 of them when #288 measured it — and the writer
the mark is about is gone by the time such a reading ends. Two sweeps were what the placement was built on, and
the second one listed none of the nine pids the first had listed 1.1 s earlier, so nothing was ever placed. A
writer is gone when the transcript is written, so a reading taken afterwards attributes nothing: measured
2026-10-23 while adding it, the exit-time sweep found no process for a child's one-shot write and the case built on
it failed with `expected 'blocked', got 'done'`.
What is not relaxed is the landing rule — a base that is still not clean when the item lands
refuses the merge and records the status line it carries, because that guard is about what may be merged onto,
not about who wrote it.

What bounds it: the budget is consulted between items, so the run also bounds **one child** by its own
recorded wall-clock (`LOOP_CHILD_MAX_SECONDS`, default 45 min). A child that outlasts it is ended together with
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

The run's own deadline bounds that child as well: the wait reads `run.deadlineEpoch` from the run's state on
every tick, and a child still running when the deadline passes is ended exactly as the cap ends one — its
transcript carries `LOOP-DEADLINE: the run's deadline passed at <deadlineAt> …` beside the `LOOP-TIMEOUT` line,
and the run records the deadline as its stop, so a run that ends there is not replaced as one that "never
recorded a stop reason". Measured 2026-10-20: eleven of fifteen stuck runs were past their own `--minutes 90`,
the longest heartbeat read `291m30s`, and not one carried a `LOOP-TIMEOUT` line although the cap was 2 700 s
(#227). The completion record the wait ends on is cleared **before** the child is started, not after it: cleared
after, a child that finished inside the window between the fork and the clear had its own record deleted, and
the run waited the cap's length for a child that had already exited — the same symptom, one fork wide.

Inside that bound the project's gate is what fills the log, and it streams: each suite's output is `tee`d as the
suite writes it, and the summary, the tally and the failure text are read from that same file, so a suite that
runs for a quarter of an hour shows its own lines instead of a two-line log. Measured 2026-10-23: a gate run held
its log at two lines while `tools/loop-selftest.sh` ran past eleven minutes, and an earlier red — a build that
died at configuration in 21 s — was found only in the Gradle daemon log, because the gate had kept its output in
a variable (#269).

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

A suite the gate owes can narrow itself to the delta being measured: a profile's command may carry `{base}`, which
the gate replaces with the base it resolved, and the loop's self-test is run that way. Its cases declare the files
they can be affected by in the first line of their body (`# files: <token> …`, a token ending in `/` for a
directory), `tools/loop-selftest.sh --files <path> …` / `--from-git <base>` selects on that declaration, and a file
set that names no case is refused with exit 2 rather than read as an empty suite — the rule the mutation harness
has applied to its own records since #235. A delta to the library, the profile, the case file or the driver itself
owes the whole set, and so does the archive: a case reaches behaviour through the scripts it starts, and only a
hand-run of all of them covers that. Measured 2026-10-23 (issue #276): 190 cases and 723 s were owed for every
`tools/loop/**` gate run, a delta to `tools/loop/worktree.sh` alone is declared by 31 of them, and a delta to
`tools/loop/run.sh` by 110 — the end-to-end `run#*` cases are the half that cannot narrow. Measured the same day:
the declaration was generated once from the case bodies and is kept honest by
`driver#everyCaseDeclaresTheFilesItCanReach`, which reads every body and refuses a declaration narrower than the
case it describes.

One tree runs one suite at a time: two suites in one scratch root redden a different set of cases each run, so a
red gate can belong to the neighbour rather than to the change under test. The driver holds a lease in the root the
tree resolved — a second invocation that may run more than one case is refused with exit 2, naming the rule, the
lock's owner and the moment it took it; a lease whose owner is not running any more is taken over and named in one
line — and an invocation that runs no case (`--list`, `--case-files`) or exactly one named case takes none, which is
the mutation harness's shape and the shape a case's own nested suite uses; refusing those would make a mutation run
read a refusal as a mutation it noticed. The lease is re-covered after a `kill -9` that leaves it behind, but a pid
the kernel has since given to another process refuses that tree until the process exits, and the refusal prints the
removal. Two worktrees of one repository are one repository and two scratch roots and are not refused, so what two
suites in different trees cost each other is the host's load — measured 2026-10-24, issue #295, where the pair whose
failing sets moved was a change worktree and a detached control worktree: the pair this lease does not cover.

What it does with a child that never started: a status alone cannot tell a child whose argv the environment
refused from one that worked the item and failed, so the run reads the item's own evidence instead — a worker
that ended non-zero, wrote no verdict and left the tree at the base commit never read the item. That is the
run's fault, recorded once as its own stop reason and naming the child's own last line: no item record is
written, nothing is escalated, the in-progress mark comes off and the item stays exactly as its owner set it.
Measured 2026-10-20: both children of that run died in under 4 s with the same 35-byte complaint, the run
recorded two items `blocked`, escalated both, and stopped on "two consecutive items ended blocked" 3 minutes
into an 8 h budget — two questions nobody had asked, and two of its three decision slots (#212). A child the run ended
itself, and one that left work behind, stay blocked items: the first carries its own transcript line and no
commit, the second its branch, which is what a later pass escalates.

What it does with a start the worktree refuses: a tree that is not clean is the *previous* item's unfinished work
— `start-item` refuses it, because committing over it would attribute two changes to one branch — so the run
attributes the refusal before it ends on it. The worktree's own branch marker names the item whose branch the tree
is on, which is the reading the start-time pre-flight already makes, and that item is recorded `blocked` with the
leftovers and escalated, exactly as that pre-flight records it; the stop reason names the item and the files it
found, not the refusal's first line. The candidate that could not start is neither recorded nor escalated:
nothing was done to it, so it stands in the queue as its owner set it. The run then stops, and continuing is not
an alternative it has — the tree is still dirty and nothing is discarded without `--reset-worktree`, so every
later candidate would be refused the same way and one cause would leave one blocked item per candidate. While
this happens one item keeps one record: a second account of an item the run already recorded joins the note that
record has, because the report's four tallies, its budget-left figure and the "two consecutive items ended
blocked" stop condition are all derived from the same list of records. What the later account may write is the note
and the escalation label and nothing else: the first record's `result`, `verdict` and metrics stand
(`tools/loop/state.sh:443`–`446`), so an item that ended `done` is not turned into a `blocked` one by the leftovers
found later — the stop reason is what names that item, and the tally that counts it stays the first account's.
Measured 2026-10-23: the run stopped 166
minutes into an 8 h budget with `the item could not be started: loop: the worktree is not clean — refusing to
start #270:`, 7 eligible items unworked, the leftovers attributed to nothing and #270 carrying no state entry at
all (#284).

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
a fix the base never had (issue #302, from #301's review, 2026-10-24). For the same reason the base the run
records is only ever a commit the base *branch* carries: `set-base` asks the branch and refuses anything else,
so a closure cannot be recorded against a tree the base does not have.
No item is replayed onto a tree nobody reviewed: a branch that is not the run's own work is `adopt-item`'s
decision, taken on what a stranger to the run can check. A review round is part of that record, in both
directions: the run commits the round onto the branch before its reviewer starts and commits the answer when
it arrives, so a round whose reviewer was killed mid-answer reads as unanswered instead of as a pass. Measured
2026-10-22: the round-2 reviewer of #249 was killed, its report was 0 bytes, and the run that started 15
minutes later adopted the branch, merged it into `main` and closed the item — no reviewer had graded the change
(#262). A branch whose last round answered `PASS` is adoptable; one whose round was announced and never
answered, and one the reviewer refused, is the owner's, and one that carries no round at all was never asked to
a review (`--review off`) and stays adoptable.

A branch the run made is a leftover once its work is in the base, and it goes when it goes: the run removes the
branch it committed for an item and merged, releasing the kept worktree from it first (git refuses to delete a
branch a worktree holds) and deleting it with git's own safe delete, which refuses a branch whose work is not
in the base. The one command that answers "what has the loop left behind" (`tools/loop/loop-clean.sh`) removes
the rest — the branches whose patches the base already holds, asked by **patch identity** (`git cherry`), never
by ancestry, because the loop lands work as `fix: … (#N)` and a landed branch is not an ancestor of the base.
A branch that still carries unlanded work is never removed by the tooling: it is named as work to land, and a
branch a worktree holds is a tree in use. The report names the two kinds apart, so a flat list of branch names
says which are leftovers. Measured 2026-10-22: 53 local branches, 43 of them already merged into the base and
none of them removed by anything.

The report reads the scratch root its own invocation resolves **and** the root the kept worktree resolves, because a
suite creates its trees under the root of the tree it runs in and the gate runs the suite inside the worktree. When
`--worktree` names this tree the two are one directory spelled two ways, so it is scanned once — compared as written
they never match, and the report would list each tree twice and count its bytes twice. The same age rule and the same
process evidence apply to both, so a tree a live suite is writing into stays kept wherever it stands. Measured
2026-10-23: 305 MB in four `loop-selftest.*` trees and four `run.sh` poll loops under `.pi/worktrees/loop/.pi/tmp`,
three of the trees past the threshold and three of the runs past their budget, and the report printed its heading with
no entry at all.

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
incident; the missing newline covers a report that was never handed over whole. The run says so, asks the same
round once more (same prompt and same diff, the answer kept beside the cut attempt as
`reviewer-report-<n>-retry.md`) and decides on the answer that arrived. A
round the run itself ended already says so in its report (`LOOP-TIMEOUT`) and is not retried; two cut answers
in one round block the item with *the reviewer's report was cut short twice*, and the branch carries no
review-failure marker, because no reviewer refused that change.

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
reported rather than a zero that reads as a measurement (#211) — beside the run-level `spent`; and **what the
caller must now do** — a named action list
whose lines are each read from the state or from git: an owed decision with the question the run wrote (or,
where the state does not hold it, where it is), a branch that carries a patch the base does not have with the
item that owns it, the kept worktree, an item whose gate never completed with its transcript, a citation
the pre-screen still refuses, and a label write the tracker refused with the item it left stale. A count is not
an answer, so `none` is printed only when all six sources were
read and empty — it never hides a branch that carries work.

What a branch carries is asked by **patch identity**, never by ancestry — `git cherry`, the same question the
item's start and the pre-screen ask. The run lands an item's work as a commit of its own (`fix: … (#N)`), so a
branch whose work was landed, or re-landed, is not an ancestor of the base while its patch is on it: ancestry read
such a branch as unlanded work, refused its item for ever and spent a decision slot on it every run (measured
2026-10-21: one branch at `1` commit ahead and `0` unlanded, issue #228). A branch is a leftover only while it
carries a patch the base does not have, and a landed one is reused by the next start rather than named as work
to land.

An item whose citation nothing accounts for is *not* worked, and it must not be re-decided in silence either:
`tools/issues/resolve-stale.sh` repairs what the repository's own records state (the full tracked path, a
rename, a single surviving declaration, an archived change's new directory) and parks the rest with the
attempts recorded — so the refusal is a state a person can query instead of a line in a machine-local file
(`stale-citation-resolution`, D4/D5). A citation that stands only in an item's **title** is never rewritten —
the resolver writes bodies, so a bare name there is a title for a person to fix. Two outcomes follow from what
the tree holds: a title naming a file whose tracked path the body already carries leaves the item settled and
unwritten (the state every item repaired from the doubled-directory form ends in), while a title citing a file
the tree does not hold parks the item, because the citation is stale wherever it stands (#226).

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

## 8. The human lane

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

## 9. Checks

A check is a **declared row**, not a script: the command to run, how often, the bound it compares against,
how many consecutive runs its verdict needs, the kind and area its items carry, and whether it owns the
item's closure. Adding a check is a row.

```
outcome        present  -> file, or reopen the item this condition already has, or annotate it
               cleared  -> comment the measurement, close once the consecutive count is met
               failed   -> write NOTHING, fail the run loudly            (I6)
```

Three rules make a check trustworthy rather than noisy, all three arising from failures this repository has
already had:

- **Identity is finer than the check.** The ownership label says which check owns an item; a condition
  fingerprint says which item it is. The same condition returning reopens; a different cause under the same
  check is a new item. A graveyard of near-duplicate items would lose the one signal that matters — that a
  fix did not hold.
- **Hysteresis in both directions**, so a flapping condition churns nothing; and **past a recurrence bound
  it escalates instead of reopening forever**, because a condition that keeps returning is a specification
  or design problem rather than an implementation one.
- **The issue is the measurement history.** Numbers live as comments; nothing is committed, because a check
  must not become a second committer (I12). The previous measurement *is* the baseline, so growth and level
  are both answerable without a store.

Beside the rows, the same scheduled worker runs the two repairs that are pure functions of the tracker's own
fields, and one report that is deliberately not one: `tools/issues/labels.sh --apply` (a label outside the
closed vocabulary), `tools/issues/resolve-stale.sh --apply` (a citation whose target moved), and
`tools/issues/duplicates.sh` — **a duplicate found by its own fingerprint marker is closed against the older
item**, but only when that item carries the authorisation label: without it the sweep posts one pointer comment
and leaves the closure to a person, because the label that authorises unattended work is never widened to make a
rule fire. An `nvid` collision is reported and never repaired, because which item legitimately carries the
identifier is recorded in the migration table rather than derivable from the tracker. The same run prints, and
never acts on, the items whose number a commit on `main` names: a commit that mentions an id has not necessarily
delivered the item, and today's landing practice cherry-picks, so one message names the id on `main` and on the
item's branch. Its two bounds are separate — `--limit` is what it reads, `--max-items` what it writes in one run.
What needs a judgement — a merge of two items whose fingerprints differ, a closure of work that is provably done,
a status a run left behind — stays `cleanup-todo`, a skill a person runs.
The checks run in a pipeline of their own, off the change-validation path, triggered only by a schedule or
an explicit dispatch, pinned to the main branch by a check at run time, guarded against overlapping runs, and
reviewed like code. Its credential is one repository secret (`SENSORS_TOKEN`), created with
**Issues: Read and write** plus **Pull requests: Read**, its repository access limited to this repository and
its owner an allowlisted person: Issues is what files, annotates and closes an item, and Pull requests is
what a row listing pull requests needs — a secret without it reports that row as failed on every run. The
identity that files an item is a **concrete allowlisted person**, because the gate's author condition is the
only attributable one (§5, I2).

A row that measures a build reads a file, so **the pipeline that made the build publishes it**: `build.yml`
writes the gate's wall time, the per-module coverage summary, the build result and the package as one
artifact (`tools/build-inputs.sh`, the same script that refuses a registry naming an input nothing
publishes), and the checks' pipeline restores that record for the commit it examines before it selects a row
(`tools/fetch-build-inputs.sh`, exit 3 = the build has not finished, so a pending build reads as a skipped
run rather than four failed checks). The record is fetched with the workflow's own read-only token, with the
conclusion the build's run ended with reported beside it; every write to the tracker is still the
repository-scoped one. A red build cannot produce a coverage report — Kover's report task is skipped once its
tests fail — nor, if it fails earlier, the package or the timing record, so that record is partial by
construction. The rows that read the build's own outputs are therefore attempted only for a run that
concluded `success`; the rest run for any record, so **"the build is red" is exactly one row's verdict** —
`broken-main`, which reads the result record the publisher always writes — and no row reports a failure a
person would have to read as "the build was red, the check was fine".

One row exists to watch the mechanism rather than the project: `stale-citations` reports the **count of
citations nothing accounts for**, measured by the resolver's own dry run, so a residue that grows — a repair
that stopped reaching something, a scheduled run that stopped happening — becomes a check's verdict instead
of a surprise (`stale-citation-resolution`, D6). The repair itself is a pipeline step, not a row: a row files,
comments on or closes *the issue it owns*, and a citation repair edits other items.

One row watches a source unit's **size**, because nothing else does: `hotspot` scores size × change count, so
a file nobody touches scores nothing however large it is. `source-size` measures **lines per tracked file**
over `*.kt` and `*.java`, tests included and the native sources out, and files one item per path over the
bound — the path is the identity, so an item closes when that file drops under it. The number, the unit and
the scope are the owner's recorded decision (2026-10-21: 1 500 lines, file level, C++ out); the bound is the
row's, so retuning it is one edit in `sensors.json`. It bounds a *file* and not a class: a file carrying
several classes is one condition here, and separating them needs a counter this tooling does not have.

## 10. Portability

The mechanism is meant to be used in another project, so the boundary between **the engine** and **the
project** is a decision rather than a later refactor.

```
engine, copied unchanged            the project, adapted
  the gate, the taxonomy reader       tools/tooling.json      repo · allowlist · reserved labels ·
  identity and dedup                    · vocabulary values · the engines' roles ·
  the verbs, the closure rules          · bounds · band edges
  the loop's phases and refusals      sensors.json            commands · cadence · bounds · counts
  the metadata pass                   loop.conf               the agent command · the skills the
  the checks' runner and guards         · worker uses · the gate command ·
                                        · the submodule a worktree checks out ·
                                        · the branch every item descends from
                                      the source adapter      the one-time record format of THIS project
```

Every command answers "which project am I in?" from its own working tree, so the copy needs no arguments
and no configuration pointing at itself. **An engine names no project value** — not a repository, not an
author, not a vocabulary value, not a path — and `tools/check-portability.sh` fails when one appears,
reading the values it forbids from the profile and the per-tool configs so that it cannot become a stale
list. A value is a leak when an engine **names** it: inside a quote, as a `<namespace>/<value>` label, or
as the argument of a flag. A bare word that happens to be a value and stands before another tool's own
option — GitHub's `--state open`, `awk -v` — is not a name: no scan can tell the two apart, and a rule
that fires on both is a rule nobody keeps (measured: the whole status, class, agent, source and prio
vocabulary, 91 values, over 40 engines). The values the profile's `roles` section declares — which value
of a namespace each engine decision means — are forbidden exactly as the namespaces' are, and the one
read-only constant for the authorisation label is asserted as the single exception and printed.

The rows' file inputs are the one thing a second project adapts beside its configuration: the *producer*
(`tools/build-inputs.sh`) knows which paths this project's rows declare and how to derive them from its
own build outputs, and the *fetcher* (`tools/fetch-build-inputs.sh`) knows the name of its build workflow.
The checks' runner, the guards and the registry's `input` fields are unchanged — a row declares the path,
the producer's manifest is what proves that path exists, and the artifact carries the declared tree as it is
declared.

The proof is a **synthetic project** in the repository's fixtures (`tools/fixtures/foreign-project/`) — a
different repository, a different allowlist, a different vocabulary — on which the whole toolchain runs,
and a porting recipe (`PORTING.md`) whose every command is executed by that same check. Distribution is a
copy per project; sharing the engines from one place stays deferred, and it stays cheap precisely because
the profile boundary already exists.

## 11. Where an agent runs

The loop's script owns everything that touches the project's history and its tracker; an agent process
receives a prepared package and produces a change. That boundary already partitions the system, so running
an agent in an isolated environment — a container, a sandbox, another machine — is a **value in the
configuration**, not a rewrite:

```
LOOP_AGENT_CMD   how a child is started          on the host  |  inside an isolated environment
LOOP_GATE_CMD    how this project's gate runs    empty for a project with no build
LOOP_CHILD_MAX_SECONDS   how long one child may run   2700 s (45 min); a longer child is ended and blocked
LOOP_WORKER_SKILLS   which of this project's skills the worker is told to use
```

Five properties make that true, and each is required by another decision anyway: one injectable child
command (I15); git, labels, state and closure on the script's side (§5); a reviewer that needs no write
access, so a read-only mount can later enforce what a prompt enforces today (I9); work needing a **device**
recorded as a verdict rather than attempted inside the environment, because a device is attached to the
host; and evidence landing on the shared tree, so the script archives it.

Running agents in an isolated environment is **not built here**: no sandbox invocation, no image, no secret
registration, no network policy, no dependency on such an environment existing. The wrapper's own tests must
pass on a machine where none of it is installed.

## 12. The decisions, and why

Every decision below eliminated at least one alternative. The full reasoning, with the alternatives named,
is in the change's `design.md`; this is the map, so a reader can find it and so a reversal is visible.

| decision | reason | change |
|---|---|---|
| the tracker, not a prose file | a prose backlog is read whole to answer any question, and its index drifted; labels make selection a query | 1, 2 |
| shell wrappers over a small policy core in single-file Java | a gate must be testable offline and must not pay a build system to run | 1 |
| one project profile, discovery instead of arguments | "adapt one file" and "always the current project" are the adoption cost | 1, 6 |
| the gate is an allowlist **and** a reserved authorisation label | the two terms share an actor, so the label alone would be self-served | 1 |
| closure only; no deletion anywhere | an item is the only record of a finding, and closure is reversible | 1 |
| fingerprint dedup checked live, never a committed ledger | a second source of truth would disagree with the tracker | 1 |
| the record format behind a source adapter | the format is one project's; the reconciler is everyone's — the reconciler and the adapter both landed in change 1, the live run in change 2 | 1, 2 |
| the removal of the prose file happens **last** | between import and rewiring, twelve skills would point at a deleted file | 2 |
| citations preserved, instructions rewired | 300+ citations are history; the rewiring is 107 lines in 23 files, and no compiled file's *behaviour* changes | 2 |
| a citation of an already-removed entry gets a tombstone rather than a rewrite | 87 citations name 23 ids whose section an earlier cleanup had removed; rewriting them would touch 64 sites inside compiled sources and lose which entry proved the rule, so each id keeps its citations and gains one `status/retired` issue carrying its text from git | 2 |
| the reference check is one script, and its exclusions are printed | a proof that nothing points at a deleted file must be mechanical and must not hide an exclusion: a citation that resolves to nothing fails it, and every allowlisted path is named with its reason on every run | 2 |
| a budget as arguments, not a dialogue | a script cannot ask, and an unrecorded budget cannot be audited | 3 |
| the loop writes only through the tooling's own write paths, so the two it was missing were added there | taking a label off and commenting without closing are authorisation decisions, and a raw tracker write inside the loop would have skipped the reserved-label refusal and the evidence-before-closure rule | 3 |
| one fresh process per item, and a separate reviewer | isolation becomes structural instead of a discipline | 3 |
| git owned by the script | the landing step is this repository's most fragile, and it is mechanical | 3 |
| the run verifies rather than trusts | a claim of success is not evidence | 3 |
| escalation instead of abandonment | a mid-item decision must not discard the work that found it | 3 |
| a check is a row; its pipeline is separate and pinned | a check must not be able to block a change, and a pipeline credential must not sit near PR content | 4 |
| three outcomes, and only "cleared" closes | a broken check reading as health is the classic monitoring failure | 4 |
| reopen rather than re-file; hysteresis; escalation | the record of a fix that did not hold is the valuable part | 4 |
| ownership scope on a check's reach | a subverted pipeline must still not touch what it does not own | 4 |
| priority persisted with a recorded basis | recomputation proportional to the backlog is the cost being removed | 5 |
| a band label **and** a body marker, written together by one script | the label is the cheap selector and the marker is the reason: a label alone cannot decide whether to recompute, and a marker alone makes every selection read a body — so the marker is a line of its own at column 0, closing at its end and outside a fenced span, and a body that quotes the grammar is prose rather than a record (issue #293) | 5 |
| classification rides the same pass | two queues, two write paths and two CI steps for one selection mechanic; and keeping it here is what lets classification write a kind and an area and nothing else | 5 |
| only the changed subset interpreted, bounded per run | a vocabulary change must not become a sweep | 5 |
| priority is not authorisation | else a threshold or a check could promote work into the loop | 5 |
| a correctness check over a value set read from the profile | a hand-written ban list fails open — change 1 landed the check over the tooling's own vocabulary, change 6 adds the synthetic project that proves it in a project that differs | 6 |
| a leak is a **named** value, not a word that appears | a category can be an ordinary word (`core`), so the check matches only values that are quoted, labelled or a flag's argument — a rule that fires on identifiers and prose is a rule that gets switched off (measured: a plain word-boundary scan reported 3 false positives on the foreign fixture, 14 over this tree) | 6 |
| a synthetic project as the proof | only a project that differs tests the profile | 6 |
| a recipe whose commands the self-test executes | a recipe that is never run drifts away from the tooling it describes; the copy itself stays prose because it runs in the source project | 6 |
| one indirection for the agent command | running agents elsewhere must be configuration | 3 |

## 13. Deferred, and why

Named so a reader can tell a decision from an omission:

| deferred | why it is deferred |
|---|---|
| the human lane's queue and answer path (change 7) | **landed 2026-10-20** — see the change table above; what remains deferred is a *prepared-options* format richer than the escalation comment, which needs more real escalations than the twelve the drain produced |
| a shared home for the engines, or a published tool | premature with one consumer; the profile boundary keeps it cheap, and the copy's own cost is stated in `PORTING.md` |
| scheduled unattended runs, budgets read from a file | it would put a model provider's key in a pipeline, which is the one place a credential is hardest to bound |
| parallel runs and a landing queue | serialising runs is the one-writer rule at run scale; parallelism needs a merge queue |
| the size/frequency loop and a ratified threshold in CI | the measurement and its row have landed (`sensor-registry-and-metric-issues`); what is still deferred is *ratifying* the bound from recorded baselines, which needs the check to have run for a while first |
| periodic spec-drift auditing | it needs a history of checked pairs; the cheap drift forms are worth doing first |
| running the sensors inside the loop's environment | a scheduled producer and a batch consumer should not be coupled |

## 14. Keeping this document honest

- **Status is a section, not a footnote** (§1). When a change lands, its row moves and its sections are
  corrected in the same change — each of the seven carries that task.
- **Every claim here has a home elsewhere** for its detail: a spec for behaviour, a `design.md` for
  reasoning, a skill for the recipe. If a claim has no such home, it is a concept this document owns and it
  belongs in §4 as an invariant with a falsifying observation.
- **A reversal is recorded, not deleted.** A decision that is later reversed moves to §13 or is annotated
  in §12 with what replaced it and why; `Design.md` §13 does the same for the app's superseded decisions.
- **The routing table must name this document.** `tools/check-doc-routes.sh` fails when a guideline is
  unrouted, so AGENTS.md's Documentation Map carries a row for it and CI proves the row resolves.
- **A section reference a body writes resolves too.** The same check resolves the references a guideline
  *body* makes to another guideline document, and refuses a bare number that document uses twice — the
  reason a `§14` of `MapRendering.md` is written with its heading text.
- **The concept is not the implementation.** If an invariant here and a spec disagree, the spec decides
  behaviour and this document is wrong: fix it here, in the same change.
