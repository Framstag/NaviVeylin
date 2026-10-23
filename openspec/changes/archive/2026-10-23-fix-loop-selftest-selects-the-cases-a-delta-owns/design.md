# Design — fix-loop-selftest-selects-the-cases-a-delta-owns

## The root cause

A case carries no record of the files it can be affected by. `LOOP_SHELL_CASES` is a list of
`<group>#<name>` strings (`tools/loop-cases.sh:32`) and the driver filters on nothing else
(`tools/loop-selftest.sh:39`–`:40`), so the gate — which already computes the delta's paths
(`tools/loop/gate.sh:88`) — has no dimension to narrow the self-test with, and a two-file delta pays all
190 cases (723 s, measured 2026-10-22 by issue #261).

## The single fix

One record per case, beside its name, and one filter over it.

1. **The declaration.** The first line of every case body is `# files: <token> …`. A token is a
   repository-relative path; a token ending in `/` is a directory and owns the paths below it; `*` owns every
   delta and is what a case that copies the whole engine — or whose body names nothing — is declared with. 190
   cases, generated once by a scan of the bodies (the paths a body names, with `$LOOP_ENGINE/…` read as
   `tools/loop/…`, plus the files of each helper the body calls: `run_worktree` starts `worktree.sh`,
   `new_project` copies `tools/tooling.json` and `tools/pi-child-events.sh`).
2. **The selection.** `tools/loop-selftest.sh` parses the declaration out of the file it sources — never a
   second copy of it — and selects with `--files <path> …` / `--from-git <base>`. A file set that names no case
   is refused with exit 2, the selection is stated as `selected N of 190 cases (files: …)` before anything runs,
   and `--case-files` prints the table. A changed path that is one of the suite's own files
   (`loop_case_core_files`: `lib.sh`, the profile, the cases file, the driver) owes every case, because every
   engine script sources the first two and every case is dispatched by the last two.
3. **The gate.** A profile's suite command may carry `{base}`; `gate.sh` substitutes the base it resolved, from
   the same diff it computed the paths from, and `loop.conf` runs the self-test as
   `tools/loop-selftest.sh --from-git {base}`. The engine names no suite: it hands over the base and nothing else.

The declaration is kept honest by `driver#everyCaseDeclaresTheFilesItCanReach`, which reads every case body and
refuses a declaration that lacks a path the body names or an engine script a helper it calls starts, or a token
that is not a path in this tree. Over-declaration is allowed on purpose; under-declaration is the defect.

## Blast radius

| what | change |
|---|---|
| `tools/loop-cases.sh` | 190 declaration lines, the core-file list, the helper→files map, one body-path reader, five cases, one dispatcher registration block |
| `tools/loop-selftest.sh` | three flags, the declaration parse, the selection predicate, the refusal, the selection statement |
| `tools/loop/gate.sh` | `{base}` substituted in an owed command |
| `tools/loop/loop.conf` | the self-test's two pairs run with `--from-git {base}` |
| `tools/loop-mutations.sh` | records `53.1`–`53.5`, one per invariant |
| `guidelines/Backlog.md`, section "7. The loop" | the paragraph that states the bound |
| `openspec/specs/agent-issue-loop/spec.md` | one added requirement, five scenarios |

Nothing compiled. The gate owes both shell suites, the record check, the doc-route check and the
classification check; the `*.md` and `openspec/*` paths are what put the last two in.

**What does not narrow, and why that is the honest answer.** A `run#*` case declares `tools/loop/run.sh` — its
entry point — and not the six scripts `run.sh` calls. A delta to `report.sh` therefore selects the 13 cases that
declare it and not the 110 that declare `run.sh`, so the declaration is an over-approximation of what a case
*names* and not of everything it *reaches*. Closing that would need the engine's call graph, which makes every
`run#*` case declare the whole engine and gives up the narrowing entirely — the same trade the mutation harness
made in #235, one file per record. The residual risk is covered by the rule that the whole set is still owed
before a change that owns `tools/loop/**` is archived, which is stated in the driver's header, in `loop.conf`
and in `guidelines/Backlog.md`, section "7. The loop".

## Rollback

`git revert` of the change's commits. Nothing reads the declaration except the three new flags, so a caller that
never passes `--files`/`--from-git` gets today's behaviour exactly; the only file that must move with the code is
`loop.conf`'s two pairs, and a profile without `{base}` is a gate that runs the whole suite again.

## Alternatives the evidence eliminated

- **Declare per group, not per case.** The groups are not the file sets: `gate#*` cases read `gate.sh` *and*
  `loop.conf`, `state#*` cases read `state.sh` through two different helpers, and `driver#*` cases run nested
  drivers. A group-level map is the same 190 decisions with less precision, and the group filter it duplicates
  already exists.
- **Derive the file set at selection time from the case body, with no declaration.** That is what generated the
  table, but done on every call it cannot be reviewed: a reader cannot say what a case owns without running the
  extractor, and the extractor is exactly the thing that needs a case guarding it. A declaration is data a
  reviewer reads and a red case compares.
- **Pass the changed paths to the suite instead of the base.** The command would carry an unbounded list through
  word splitting (`paths=$(…)` is newline-separated and a profile's command is run as `$cmd`), and a path with a
  space would break it silently. `{base}` is one token, and `--from-git` derives the paths from the same two
  sources the gate used.
- **Narrow by a hand-maintained list of groups in `loop.conf`.** Rejected by the item itself: it makes the worker
  name its group by hand, and a mis-named group costs a full run in the other direction.

## The eliminated question the item left open

The item's fix shape says "with the full set still owed before a change is archived". This change keeps that as
a rule and not as a second run inside the gate: the narrowed gate *is* the item's gate, and the archive pass runs
`tools/loop-selftest.sh` whole by hand — the same shape `loop.conf` already states for the mutation set
(`82–116` records: the check in the gate, the whole set by hand). A gate that ran the narrowed set and then the
whole one would pay more than it does today, which is the opposite of the item.
