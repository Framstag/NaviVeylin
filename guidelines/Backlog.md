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
| 3 | `agent-issue-loop` | the budgeted loop, its isolation, its refusals | **applied 2026-10-17** — `tools/loop/**` (the entry `run.sh`, the pre-screen, the state and its lock, the worktree and the branches, the label transitions, the report), two prompt skeletons filled from `loop.conf`, a stub `pi` and an offline self-test with one CI step; the two write paths the loop needed landed in the tooling (`update.sh --remove-label`, `close.sh --comment-only`); §7 and §11 of this document and the `fix-loop` skill describe it |
| 4 | `sensor-registry-and-metric-issues` | declared checks that keep the tracker honest | **applied 2026-10-17** — `tools/sensors/**` (the registry `sensors.json`, the runner, the guards, the report, the hotspot measurement, the four metric rows and the two condition sensors), a fixture harness offline against the stub `gh` with one CI step, the three verbs the runner writes through extended (`create.sh --sensor`, `close.sh --sensor`, the new `reopen.sh`), the isolated pipeline `.github/workflows/sensors.yml` with its CODEOWNERS entry, and `labels.sh` learning the registry's labels; the host dry runs, the falsification run and the not-reproducible CI steps are recorded under the change's `report.md` |
| 5 | `issue-metadata-loop` | persisted priority and classification | **applied 2026-10-17** — `tools/issues/prioritise.sh` (the basis, the partition over every issue, the bounded run, the label+marker writer, the transition comment) and `classify.sh` (the queue of items with no kind), `check-metadata.sh` and its falsification, the `bands` section in `tools/tooling.json`, `list.sh --prio/--unranked`, the `issue-metadata` skill and the `triage-todo` rewrite; the run over the live tracker is recorded under the change's `evidence/` |
| 6 | `tooling-portability` | the proof that another project can use all of it | **applied 2026-10-18** — `tools/check-portability.sh` (the leak check, its value set read from the profile and the per-tool configs at run time) with `tools/check-portability-selftest.sh` and its 21 cases, `tools/mutate.sh` with the seven revert-checks as a versioned mutation set, the two skills `portability-check` and `mutation-runner`, the synthetic project `tools/fixtures/foreign-project/` on which the whole toolchain runs without an engine edit, `PORTING.md` (the recipe, its commands executed by the self-test), one `Check portability` CI step and the check's own classification line; no engine file was changed — the check is the proof, not a fix |
| 7 | `wire-sensor-inputs` | the rows' declared inputs, materialised — and the checks' schedule back on | **applied 2026-10-18** — `tools/build-inputs.sh` (the manifest, `--check` against the registry, the per-module coverage conversion, the result record that survives a red build) and `tools/fetch-build-inputs.sh` (a commit's record, exit 3 = not built yet) with their cases, selftest, eight mutations and one `Check the build inputs` CI step; `build.yml` publishes the record as one artifact, `sensors.yml` fetches it with the workflow's own read-only token and its `schedule` is restored; the host dry run is the change's `report.md` |
| 7 | `decision-queue` | the human lane — the queue, the answer path, the mid-item handover | **named, not written** |

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

The run owes the commits it makes an identity: it is the login the run's own gate preflight resolved (the
identity that authorises it), as that login's noreply address — never the machine's `~/.gitconfig` and never
an invented one, because a fresh container or CI runner has neither and the item would block on its own
commit (`fix-loop-git-identity`, issue #167).

What it does when it cannot proceed: an item that needs a decision is **escalated** — the work already done
is named on the item, the item is marked as awaiting a decision, and the run takes the next candidate. The
work is preserved rather than abandoned (I11), which is the part the prose-era loop got wrong by treating a
mid-item decision as a failure.

An item whose citation nothing accounts for is *not* worked, and it must not be re-decided in silence either:
`tools/issues/resolve-stale.sh` repairs what the repository's own records state (the full tracked path, a
rename, a single surviving declaration, an archived change's new directory) and parks the rest with the
attempts recorded — so the refusal is a state a person can query instead of a line in a machine-local file
(`stale-citation-resolution`, D4/D5).

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

The checks run in a pipeline of their own, off the change-validation path, triggered only by a schedule or
an explicit dispatch, pinned to the main branch by a check at run time, holding issue-write and nothing
else, guarded against overlapping runs, and reviewed like code. The identity that files an item is a
**concrete allowlisted person**, because the gate's author condition is the only attributable one (§5, I2).

A row that measures a build reads a file, so **the pipeline that made the build publishes it**: `build.yml`
writes the gate's wall time, the per-module coverage summary, the build result and the package as one
artifact (`tools/build-inputs.sh`, the same script that refuses a registry naming an input nothing
publishes), and the checks' pipeline restores that record for the commit it examines before it selects a row
(`tools/fetch-build-inputs.sh`, exit 3 = the build has not finished, so a pending build reads as a skipped
run rather than four failed checks). The record is fetched with the workflow's own read-only token; every
write to the tracker is still the repository-scoped one. A red build cannot produce a coverage report —
Kover's report task is skipped once its tests fail — so that record is partial by construction, its result
record still reaches `broken-main`, and the coverage row reports a failed check rather than a healthy one.

One row exists to watch the mechanism rather than the project: `stale-citations` reports the **count of
citations nothing accounts for**, measured by the resolver's own dry run, so a residue that grows — a repair
that stopped reaching something, a scheduled run that stopped happening — becomes a check's verdict instead
of a surprise (`stale-citation-resolution`, D6). The repair itself is a pipeline step, not a row: a row files,
comments on or closes *the issue it owns*, and a citation repair edits other items.

## 10. Portability

The mechanism is meant to be used in another project, so the boundary between **the engine** and **the
project** is a decision rather than a later refactor.

```
engine, copied unchanged            the project, adapted
  the gate, the taxonomy reader       tools/tooling.json      repo · allowlist · reserved labels ·
  identity and dedup                    · vocabulary values · bounds · band edges
  the verbs, the closure rules        sensors.json            commands · cadence · bounds · counts
  the loop's phases and refusals      loop.conf               the agent command · the skills the
  the metadata pass                     · worker uses · the project's gate command
  the checks' runner and guards       the source adapter      the one-time record format of THIS project
```

Every command answers "which project am I in?" from its own working tree, so the copy needs no arguments
and no configuration pointing at itself. **An engine names no project value** — not a repository, not an
author, not a vocabulary value, not a path — and `tools/check-portability.sh` fails when one appears,
reading the values it forbids from the profile and the per-tool configs so that it cannot become a stale
list. A value is a leak when an engine **names** it (inside a quote, as a label, or as a flag's argument):
a project whose category is an ordinary word (`core`, `docs`) must not trip a check that fires on every
identifier and every sentence, or the check becomes a rule nobody keeps.

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
| a band label **and** a body marker, written together by one script | the label is the cheap selector and the marker is the reason: a label alone cannot decide whether to recompute, and a marker alone makes every selection read a body | 5 |
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
| the human lane's queue and answer path (change 7) | it depends on a working loop, and the prepared-options format needs the loop's experience to design |
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
- **The concept is not the implementation.** If an invariant here and a spec disagree, the spec decides
  behaviour and this document is wrong: fix it here, in the same change.
