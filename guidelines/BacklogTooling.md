# Backlog tooling — the checks, its portability, and where an agent runs

The tooling over the backlog: the declared checks that file, reopen and close an item, the boundary that lets
another project use the same mechanism, and where the agent process runs when the loop hands it work. The
backlog itself — what an item is, what the tracker holds, who may write what, how it becomes work — is
`guidelines/Backlog.md`; the rule that keeps both documents honest is `Backlog.md` §14.

**Read the section that owns your topic, not this whole document.** The section list is
`grep -n '^## ' guidelines/BacklogTooling.md`; AGENTS.md's Documentation Map routes a kind of change here.

## 9. Checks

- **Rule: a check is a declared row, not a script.** A check is the command to run, how often, the bound it
  compares against, how many consecutive runs its verdict needs, how many items one run of it may file, the kind
  and area its items carry, and whether it owns the item's closure. Adding a check is a row.

The three-value outcome a run of a check writes — a present condition files, reopens or annotates an item; a
cleared one comments the measurement and closes once the consecutive count is met; a failed one writes nothing and
fails the run loudly — is `guidelines/BacklogTooling.recipes.md` §9.


Three rules make a check trustworthy rather than noisy:

- **Rule: identity is finer than the check.** The ownership label says which check owns an item; a condition
  fingerprint says which item it is. The same condition returning reopens; a different cause under the same
  check is a new item. A graveyard of near-duplicate items would lose the one signal that matters — that a
  fix did not hold.
- **Rule: hysteresis in both directions.** A flapping condition churns nothing; and past a recurrence bound
  it escalates instead of reopening forever, because a condition that keeps returning is a specification
  or design problem rather than an implementation one.
- **Rule: the issue is the measurement history.** Numbers live as comments; nothing is committed, because a
  check must not become a second committer (I12). The previous measurement *is* the baseline, so growth and
  level are both answerable without a store.

- **Rule: one run of one check files a bounded number of condition items** — `maxFilings` in the registry, or the
  count the row declares in its place. Past it the run files nothing further, and the conditions it did not file
  reach the tracker as one summary item carrying the count, the bound and each condition's identity and measurement
  (at most twenty of them, the rest named by number), so a mis-fitted bound costs one item instead of one per
  condition it happens to hold. The summary is not a condition of the check's — its identity is the engine's own
  and it carries no measurement marker, so it never reads as one of the row's measurements — and the first run
  that leaves nothing over closes it, while a run in which a finding failed closes nothing and a closure a person
  made is annotated instead of reopened. An annotation, a reopen and a comment spend nothing of the bound: none of
  them makes the tracker grow.

- **Rule: a row's first exercise is reported, not spent.** A row's first exercise is the one run whose blast radius
  nobody has seen, and the state is already printed: the report calls it `stale row=<id> no measurement on record`.
  The unattended tick therefore reports that run — it prints `first exercise: row=<id> findings=<n>` and writes
  nothing at all, no condition, no record issue and no summary — and the first exercise is spent by a dispatch that
  asked to write (`apply=true`), which records the measurement every later run is compared against. The engine reads
  the trigger the pipeline names (`SENSORS_TRIGGER`, GitHub's own `event_name`), so only the scheduled tick prints
  that count line: a run a person started records a measurement only when it asked to write, and a hand run — whose
  mode defaults to `--dry-run` — writes nothing and so leaves the row's first exercise unspent.

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

The declared rows and what each measures — `broken-main`, `stale-citations`, `metadata-invariant`, `hotspot`,
`source-size`, `routed-section-size`, `entry-document-size` and `loop-spend` — are `guidelines/BacklogTooling.recipes.md` §9.

## 10. Portability

The mechanism is meant to be used in another project, so the boundary between **the engine** and **the
project** is a decision rather than a later refactor.



Every command answers "which project am I in?" from its own working tree, so the copy needs no arguments
and no configuration pointing at itself. **Rule: an engine names no project value** — not a repository, not an
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
LOOP_CHILD_MAX_SECONDS   the floor under one child's run   2700 s (45 min); a longer child is ended and blocked
LOOP_ITEM_TURNS_MAX      the turns one child may spend    250; counted from the item's event stream, and a child
                                                       that reaches it is ended and blocked (issue #686), `0` = none
LOOP_ITEM_GATE_SECONDS   what one item's gate owes    3900 s (65 min): two gate rounds and the phases beside them
                                                       (issue #663); a child is given the larger of the two
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

