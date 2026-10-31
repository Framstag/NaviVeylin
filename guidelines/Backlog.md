# Backlog — the concept

The project's backlog: where an item comes from, what the tracker holds about it, who may write what, how it
becomes work, and the decisions behind each of those with the alternatives they eliminated. The tooling that
keeps it honest — the declared checks, its portability, and where an agent runs — is
`guidelines/BacklogTooling.md`, which carries those sections under their own numbers.

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
| 3 | `agent-issue-loop` | the budgeted loop, its isolation, its refusals | **applied 2026-10-17** — `tools/loop/**` (the entry `run.sh`, the pre-screen, the state and its lock, the worktree and the branches, the label transitions, the report), two prompt skeletons filled from `loop.conf`, a stub `pi` and an offline self-test with one CI step; the two write paths the loop needed landed in the tooling (`update.sh --remove-label`, `close.sh --comment-only`); §7 of this document, §11 of `BacklogTooling.md` and the spec `agent-issue-loop` describe it |
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
The participants and the flow are the procedure layer — the producers, the tracker and the three consumers, the
run's script and the prompts its phases use. The diagram, the daily metadata pass and the write boundaries it
rests on are `guidelines/Backlog.recipes.md` §2.
## 3. What the tracker holds

**Rule: one item is one issue, and everything a reader selects on is a label.** Selection is a query and never a read:

| namespace | carries | why it is separate |
|---|---|---|
| kind | a defect, a quality gap, or a capability that does not exist | it drives the order work is taken in, and it is the only fact a claim's shape is judged from |
| area | where the item lives | it replaces the hand-maintained index that drifted in the prose backlog |
| state | open, in flight, fixed, on hold, awaiting a device, unverified | the lifecycle, read at the moment of a query |
| provenance | imported, filed by a check, filed by an agent, filed by a person | a reader can tell a migrated item from a filed one |
| priority | one of a fixed set of bands, and nothing else | it must be selectable without reading the item |
| ownership | which check owns the item, when a check filed it | it is what bounds that check's reach (`BacklogTooling.md` §9) |
| authorisation | whether automated work is permitted | the one label whose absence is a refusal (§5) |

The **names of the namespaces and the shape of the item are the contract**; the values inside them are the
project's, held in one profile (`BacklogTooling.md` §10). As landed by change 1, the namespaces are `class/` (kind),
`category/` (area), `status/` (state), `source/` (provenance), `prio/` (priority band), `sensor/`
(ownership, its values declared by the check registry) and `agent/` (authorisation) — and GitHub's own
defaults are never used for logic. An item also carries, in its own body, the facts a machine needs and
a reader should not have to: the identifier it came from, the fingerprint that says what it is, how many
times it has recurred, and the basis its priority was computed over.

**Rule: priority is a stored fact with a recorded basis, not a number recomputed on every reading.** The band is
selectable; the components and the basis explain it; the value is reused while the basis is unchanged, so
ordering the whole backlog costs a query and a pass over a stable backlog interprets nothing.

## 4. The invariants

**Rule: an invariant is stated with the observation that would falsify it.** These are the rules the mechanism must satisfy. Each names the mechanism that holds it and the observation
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
| I18 | A settled state is a claim about the item's premise, and a routine write does not remove it: a hold says something about a run's capacity, and the second claim SHALL NOT erase the first | the write path reads the profile's `roles.settledStatuses` and refuses a displacement that names no transition (exit 3, `status-settled`) — `update.sh --displace-settled` names it, and the three verbs whose purpose the transition is, a closure, a reopen and the loop's own `close` verdict (`tools/loop/labels.sh`), pass it; cases `update#refusesToDisplaceASettledState`, `labels#closeNamesTheSettledTransition` | a verdict's hold or a resolver's park removes `status/fixed-by` or `status/retired`, or a call that displaces a settled state and says so is refused instead of landing |

## 5. Who may write what

The single table that answers most of the questions about this mechanism. **A writer that does not appear
in a row does not write that thing.**

| | a person | a declared check | the metadata pass | the agent loop | the resolver | an agent process |
|---|---|---|---|---|---|---|
| file an item | yes | yes | no | no | no | no |
| classify (kind, area) | yes | its row declares them | yes | no | no | proposes only |
| set priority | yes | no | yes | no | no | proposes only |
| authorise automated work | **yes, and only here** | **no** | **no** | **no** | **no** | **no** |
| repair a citation in an item's body | yes | no | no | no | **yes** — the citation token only; an item nothing accounts for is reported, never parked (#281) | no |
| comment a measurement | yes | yes | yes | yes | yes (one per item per run) | no |
| close | yes | only its own items, only when cleared | no | after a passing review, with evidence | **no** | no |
| reopen | yes | only its own items | no | no | no | no |
| commit, branch, push | yes | never | never | the wrapper | never | never |
| decide a design question | **yes, and only here** | no | no | no | no | proposes only |
| write the run's state | no | no | no | the wrapper, between processes | no | no |

Two consequences worth stating plainly, because both were load-bearing decisions:

- **The tracker credential is never in an agent's reach.** The loop's script owns every write, and an agent
  process receives a prepared package instead. That is what lets an agent run in an isolated environment
  later without a new security design (`BacklogTooling.md` §11).
- **The authorisation label is the only key to unattended work.** Everything else about an item may be
  machine-set; that one may not. Filing is not authorising. Change 1 landed this as a **reservation** rather
  than a convention: the label is listed in the profile's `reservedLabels`, every write path refuses it in
  any input (exit 3, `label-reserved`), the tooling ships no authorise command, and the self-test drives all
  three write paths plus the other label flags to prove no route sets it. The import is also asserted to
  arrive with 0 eligible items.

Five rules keep the decision queue answerable, because it is a query over one label and the run's ceiling counts
that query (`tools/issues/decisions.sh`, `LOOP_MAX_DECISIONS_DEFAULT`):

- **Rule: an answer reaches the next worker's package.** The answer is a comment, and the item's body still states
  the question it settled — so nothing but the package can carry it: the run asks the tracker for the item's
  comments and the package renders the last recorded decision beside the question it answered (`<<<ITEM_DECISION`,
  `recorded: none` when there is none), and `answer.sh` writes that question into the answer with an
  `<!-- answers: … -->` marker. The question is the escalation's `**The decision:**` line, never the verdict word
  (`needs-decision`) that opens its comment, and the queue reads it the same way (`tools/issues/lib.sh`,
  `ISSUES_QUESTION_JQ`). Without the package carrying the answer a fresh worker re-derives the same fork with a
  fresh option list, and the recorded `Decision: 2` silently means a different option. A worker that reads a
  recorded decision treats it as settled and says what it leaves open when it has to ask again.
- **Rule: a closure leaves no decision label behind.** An item that carries the label and is closed would stay in
  the queue for ever and keep spending a decision slot; `tools/issues/close.sh` takes the label off in the same
  write that records the settled state, for every path that closes — the run, a person, a declared check.
- **Rule: a block asks its own question.** When the reviewer wrote no decision, the run states the only two-way
  choice a block leaves — retry the item, or abandon it — because a reason is not something a person can answer.
- **Rule: a stale skip asks its own question, and is never parked on a bare hold.** The verdict says the item's
  claim no longer holds in the tree, which the run cannot close on: the falsification closure owes a measurement
  and a review that a stale skip does not carry. The run writes the decision label, the hold and the one comment
  naming the reason and the choice — retire the item as stale, or re-diagnose it — because a bare hold is reported
  by the pre-screen as held, which is neither work nor a decision, and leaves the item open for good (spec
  `agent-issue-loop`).
- **Rule: a reviewed falsification closes the item as mis-diagnosed, and only on an unchanged tree.** A run that
  measured that the item's premise does not hold holds the evidence that settles it, so it closes the item in the
  settled state a decision not to act records, with the measurement as the comment — under the same `--may-close`
  permission a fix closure rides, and only after the review read the claim. A falsification whose tree moved
  during the review is a change, so it is escalated instead (openspec change `falsified-items-close-in-the-loop`).

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

The intake step is a write, so it is the filing path's job to make an item exist in a selector's queue: a finding
states its `class` and its `category`, and `tools/issues/create.sh` files both as labels of their namespaces — the
fields are required, a value outside the closed vocabulary is refused rather than dropped, and a field that
contradicts the labels the same finding carries is the one-value-per-namespace refusal.


The last of those cheap decisions has a home of its own: **what counts as a citation** is defined once, in
`tools/issues/lib.sh` (`stale-citation-resolution`), and asked by the loop's pre-screen and by
`tools/issues/resolve-stale.sh` — so detection and repair cannot disagree about a body. A citation that
resolves by a suffix of a tracked path, inside a submodule, or as an artefact the project generates is not
staleness; only a name nothing accounts for is, and that one is now **reported**, not refused or parked — a
path the tree does not hold may be one the change itself creates (#281). Measured 2026-10-18: the extraction
before that shared rule was wrong about 48 of 49 verdicts, and two thirds of an authorised set was excluded
over prose.

Three consequences the design takes as decisions, not accidents:

- the pass is **bounded per run**, so a project-wide vocabulary change becomes a sequence of bounded runs
  rather than one sweep of the whole backlog;
- **selection never samples**: it examines every item and skips by an unchanged basis, because skipping an
  item that had changed would leave a wrong priority in place indefinitely;
- the conditions that **gate** an item are derived from labels at reading time and never stored with the
  priority, because they move far more often than a score does.

## 7. The loop
`Backlog.md` §7 is an index. The loop's procedure — its phases, the bounds on a child, the gate and its suite,
the refusals it records and the leftovers it reports — is `guidelines/Backlog.recipes.md`, one sub-section per
anchor below. Each anchor names a section number this document carries, so a citation of `§7` still resolves and a
reader lands on the phase they need.

## 7a. Running one item: the script, the child and the package

The run's script, the child's prompt and the result bound are `guidelines/Backlog.recipes.md` §7a.

## 7b. What an item costs, and the bounds on it

The item's cost, the clock, the memory floor, the turn bound and the gate-step bound are
`guidelines/Backlog.recipes.md` §7b.

## 7c. The gate and its suite

The gate's configuration, `{base}` narrowing, the case pool and the suite lease are
`guidelines/Backlog.recipes.md` §7c.

## 7d. Refusals, records and the landing

A child that never started, a dirty tree, commit identity, the branch and the merge, adoption and the numbers are
`guidelines/Backlog.recipes.md` §7d.

## 7e. Leftovers, the report and the queue

The scratch root, the leftovers sweep and the report are `guidelines/Backlog.recipes.md` §7e.

## 8. The human lane
The human lane is the sibling workflow that answers what the loop may not decide. Its protocol and the escalation
it carries are `guidelines/Backlog.recipes.md` §8.

## 12. The decisions, and why
The decisions and the alternatives each eliminated are `guidelines/Backlog.why.md` §12.

## 13. Deferred, and why
What is deferred, and why it is deferred is `guidelines/Backlog.why.md` §13.

## 14. Keeping this document honest

- **Status is a section, not a footnote** (§1). When a change lands, its row moves and its sections are
  corrected in the same change — each of the seven carries that task.
- **Every claim here has a home elsewhere** for its detail: a spec for behaviour, a `design.md` for
  reasoning, a skill for the recipe. If a claim has no such home, it is a concept this document owns and it
  belongs in §4 as an invariant with a falsifying observation.
- **Rule: a reversal is recorded, not deleted.** A decision that is later reversed moves to §13 or is annotated
  in §12 with what replaced it and why; `Design.md` §13 does the same for the app's superseded decisions.
- **The routing table must name this document.** `tools/check-doc-routes.sh` fails when a guideline is
  unrouted, so AGENTS.md's Documentation Map carries a row for it and CI proves the row resolves.
- **A section reference a body writes resolves too.** The same check resolves the references a guideline
  *body* makes to another guideline document, and refuses a bare number that document uses twice — the
  reason a `§14` of `MapFrames.md` is written with its heading text.
- **The concept is not the implementation.** If an invariant here and a spec disagree, the spec decides
  behaviour and this document is wrong: fix it here, in the same change.
