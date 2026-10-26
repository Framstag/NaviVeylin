# Porting the backlog tooling to another project

The tooling under `tools/` is an **engine**: the issue tracker's gate, identity, dedup and closure; the
sensor registry, its runner and its guards; the budgeted loop with its worktree, its phases and its
report; and the metadata pass that persists a priority. Everything that is one particular project's —
the repository, the allowlist, the label vocabulary, the band edges, the declared checks, the gate
command — lives in **committed configuration**, never in a script. So adopting the tooling is a copy, a
profile and this recipe, not a code review.

The boundary is enforced mechanically: `tools/check-portability.sh` reads the project's own values from
its profile and per-tool configs and fails when one of them appears in an engine, so a leak is caught on
the commit that introduces it rather than by the second project.

## Prerequisites

| need | why |
|---|---|
| `git` | every tool resolves the project from its working tree (`git rev-parse --show-toplevel`); there is no other way to run them |
| an authenticated `gh` | the tracker is GitHub Issues; `gh` is the only forge client the engine speaks |
| `bash` | the engine is shell, and it is not `sh`-portable on purpose (arrays, `[[`-free but `local` and `set -o pipefail` are used) |
| `jq` | the profile, the registry, the label sets and the findings are JSON on the way in and out |
| a JDK (17 or newer) | the policy core — the gate, the fingerprints, the band mapping, the taxonomy — is single-file Java compiled once with `javac --release 17` |
| a `gh`-shaped tracker state | the two namespaces of the taxonomy that are *not* values in the profile (`sensor/…`) come from the check registry |

## What to copy

```
tools/tooling.json          → the profile you will edit
tools/issues/               → the verbs and the Java core (keep `import/todo-md.sh` only if you have
                              a prose backlog in that exact format; otherwise delete it)
tools/sensors/              → the registry, the runner, the guards, the report, the measurements
tools/loop/                 → the wrapper, the worktree, the state, the labels, the report, the
                              prompt skeletons and the stub `pi` (drop `fixtures/` in your own repo
                              if you do not want the offline harness)
tools/check-portability.sh  → the leak check, and the proof that your copy is still an engine
tools/fixtures/foreign-project/   → the synthetic project the check's self-test runs (optional; it is
                              test data, and it is useful as a worked example of a different profile)
```

The **copy** is the distribution model: one copy per project, kept in that project's own history. It is
deliberately not a submodule, a package or a published binary (see *What an update costs* below).

## The three files to edit

1. **`tools/tooling.json`** — the project profile: `repo`, `allowedAuthors`, `requiredLabels`,
   `reservedLabels`, every namespace's values, the `roles` section (which value of a namespace each
   engine decision means: the loop's pre-screen verdicts, its label transitions, the band the metadata
   pass writes), the `bands` section (namespace, `ttlDays`, edges, scoring table), the source renames and
   the input bounds. This is the only file that names the project's repository and vocabulary.
2. **`tools/sensors/sensors.json`** — the declared checks: one row per check, each with its command,
   cadence, bound, consecutive count, the kind and area it files, and the fields its fingerprint is
   derived from; plus `record.status` (one of the profile's own status values), the state the record issue a
   green row's measurements land on carries, so no work selector takes it (`backlog-sensors`, issue #257);
   plus `maxFilings`, the most items one run of one row may file, which a row may override — what a run does
   not file leaves as one summary item (`backlog-sensors`, issue #666).
3. **`tools/loop/loop.conf`** — how a child agent is started (`LOOP_AGENT_CMD`), which of the
   project's skills the worker is told to use (`LOOP_WORKER_SKILLS`), **how the project's gate is
   run** (`LOOP_GATE_CMD`), the submodule a fresh worktree — or a `--reset-worktree` — has to check out
   (`LOOP_SUBMODULE_PATH`, empty for a project without one), the command that lists the project's test cases without running one
   (`LOOP_SUITE_CASES_CMD`, empty for a project whose suite cannot list them — the measuring block then says so
   instead of reporting a count of zero) and **the branch every item descends from** (`LOOP_BASE_REF`,
   written `LOOP_BASE_REF="${LOOP_BASE_REF:-main}"` because the file is sourced *after* the environment:
   the expansion is what lets an inherited value win, and a plain assignment would ignore it), the one gitignored
   directory a stopped run copies into its leftovers patch (`LOOP_UNTRACKED_RECORD_DIR` — the project's own
   unarchived changes live in one, and a patch of the tracked diff alone would lose them), and how long one
   self-test case may run (`LOOP_CASE_MAX_SECONDS`), and the per-item token band the report warns above
   (`LOOP_ITEM_TOKENS_WARN` — the project's own p75 rather than this one's; `0` or no key records no band, and
   nothing in the run reads it, so it costs a ported project one line at most). The gate
   command is the project's: leave it empty for a project with no build, and the phases still run while
   the report says the gate was skipped. The base branch is the project's too — a project on `trunk` or
   `develop` records it here, so nothing about the branch lives in the engine.

## What to delete

`tools/issues/import/todo-md.sh` is the **source adapter**: the one file that knows a particular prose
backlog format. It is excluded from the portability check for exactly that reason — it is allowed to
know the format. A project with no such file deletes it, and `import.sh` (the reconciler) still works
for any producer that emits the neutral findings JSON.

## Assumptions this recipe does not remove

- **The forge is GitHub.** Issue numbers, labels, `gh issue list/view/edit/close/reopen` and the
  `stateReason` semantics are assumed throughout.
- **The namespace *names* are the contract.** A profile may choose any values (`bug`, `defect`, …) and
  any category vocabulary, but the namespace names (`class`, `category`, `status`, `source`, `agent`,
  `prio`, `sensor`), the separator and the label shape `<namespace><separator><value>` are what the
  engine reads. Renaming a namespace is an engine change, not a profile change.
- **The label taxonomy is the tool's.** Labels are created from the profile (plus the registry's
  `sensor/<row-id>` values); a project that already has a conflicting vocabulary must rename it.
- **`gh` writes are the only writes.** No tool deletes anything; closure is the terminal state.

## Run it

Every command below is executed by `tools/check-portability-selftest.sh` against the synthetic project
in `tools/fixtures/foreign-project/`, so this recipe cannot drift away from the tooling. That is the
reason the copy itself is described in prose: it runs in the *source* project and has no meaning in a
tree that is already a copy.

```sh porting-step
bash tools/check-portability.sh --quiet
```

The check must pass: no engine names one of your values — every namespace's values, the values the
`roles` section declares, the loop's gate command and submodule path, and the registry's declared paths
and labels. The only value an engine may name is the read-only constant for the label that authorises
work (`LOOP_AUTHORISATION_LABEL`), which the check asserts and prints. If it fails, the value belongs in
your profile or in one of the two per-tool configs — the failing line and the value's source are printed.

```sh porting-step
bash tools/issues/labels.sh --json >/dev/null
```

The taxonomy your profile declares, as the dry-run diff of the labels that would be created. Nothing is
written without `--apply`.

```sh porting-step
bash tools/issues/list.sh --json --limit 1 >/dev/null
```

The tracker is reachable and the verbs can read it.

```sh porting-step
bash tools/sensors/report.sh >/dev/null
```

Every declared row is complete — a row missing its bound or its consecutive count is reported here
rather than at the first scheduled run.

```sh porting-step
bash tools/loop/run.sh --dry-run --items 0 --minutes 0
```

The loop's phases run against *your* project and stop because the budget is empty: no worktree, no
branch, no label and no agent process. Raise `--items` when you mean it.

## What an update costs

- An **engine fix upstream** is a re-copy of the engine files: the profile and the two per-tool configs
  are untouched, because they are the seam the copy exists to preserve.
- A **local engine edit** means you own the merge: your change and the upstream change touch the same
  file, and the next copy will not apply cleanly. Prefer a profile or config change; if the value cannot
  be expressed there, that is a portability defect worth reporting upstream.
- **Sharing the engines from one place** (a submodule, `git subtree`, or a published CLI) remains
  deferred rather than rejected, and it stays cheap to do later precisely because the engines already
  contain no project value: the extraction is a move, not a redesign.

## Not in this recipe

No sandbox, no container, no CI token beyond `gh`'s own, no scheduled run, and no agent budget: the
repository ships an offline self-test for the check and for each engine, and those run with no
credential at all.
