# Proposal — a mutation run is scoped to the files a change touched

## Why

Measured in `.pi/worktrees/loop` on `11123fb` (evidence: `.pi/issue-loop/items/235/red-on-HEAD.log`,
`2026-10-22T07:08:27Z`, and `fix-mutation-harness-file-scope/evidence/red-on-HEAD.log`):

- `tools/loop-mutations.sh` filters its records with `selected()` (`tools/loop-mutations.sh:211` on HEAD),
  which compares the caller's `--only` list against **the record's label alone**. A record's third
  `@`-separated field is the file it mutates (`label@fifo@file@case@desc@exprs`,
  `tools/loop-mutations.sh:63`, the set declared at `tools/loop-mutations.sh:67`), and nothing reads it:
  `--engine` exists for the harness's own self-test, `--timeout` for slow cases, and there is no flag that
  scopes a run.
- What that costs is measured in the item: the #232 change touched three engine files
  (`tools/loop/{worktree,prescreen,run}.sh`) and ran the whole set three times — the set declares **81
  records** today (`tools/loop-mutations.sh --list | wc -l`), each record two case runs — so one pass is 21
  minutes. Choosing labels by hand (`--only "12.5 12.6 12.7 13.1 18.2 33.1 33.2"`) was accurate but had to be
  derived by grepping the record list for the changed files, twice. The pressure this creates is to skip the
  pass.
- **Root cause, one sentence: the harness's selection reads only the label field, so the file a record owns —
  the field that says which change touched it — cannot bound a run, and the only cheap selection is a
  hand-derived label list that is wrong the moment a record is added or the change grows.**
- The red case is the harness's own self-test: with the cases of checks 7–9 added and the harness unchanged at
  HEAD, `tools/loop-mutations-selftest.sh` is **9 of 34 cases failed**, exit 1, `2026-10-22T07:08:27Z` — the
  new cases answer `loop-mutations: unknown argument --files` and `--from-git`. The same suite after the fix:
  **all 34 cases passed**, exit 0, `2026-10-22T07:07:38Z` (`green-selftest.log`).
- The capability that owns it is **`agent-issue-loop`** — the same capability whose requirement *The mutation
  harness leaves the tree exactly as it found it* states what the harness may do to the tree. The rule the
  mechanism serves is **`guidelines/Build.md` §2, "Agent iteration protocol", rule 2 — "Iterate with focused
  suites, gate once"**; the harness's own usage is documented in `.pi/skills/revert-check` and the change
  updates that block. What is missing is the *bound*: the rule says gate once and iterate focused, and for a
  shell gate the focused form did not exist.

surface: 9 cases added to the cases of a 34-case suite, 4 of the 8 tracked files that name the harness edited (engine, self-test, gate suite map, skill), 0 of the 81 records changed, 1 capability requirement added — `git grep -l "loop-mutations" -- . ':!openspec/changes/archive'` (8 files) and the declared set of `tools/loop-mutations.sh` (81 records, `tools/loop-mutations.sh --list | wc -l`)

## What Changes

- **One new requirement in `agent-issue-loop`**: a mutation run SHALL be scoped by the files it touches —
  `--files <paths>` and `--from-git <base>` select the records whose `file` field is in that set — SHALL state
  the selection against the declared set (`selected N of M records`), and SHALL refuse a selection that names
  no record with exit 2, because a harness that passes by doing nothing is the silent lie it exists to prevent.
- **`tools/loop-mutations.sh`** grows `--files` (repeatable, space-separated inside one value) and
  `--from-git <base>` (the diff against that commit plus `git status`, the two sources the loop's gate itself
  reads), prints the count line, and refuses an empty selection — for `--only` too, which could report success
  for a label list that matched nothing.
- **`tools/loop-mutations-selftest.sh`** grows checks 7–9 (a scoped `--list` and its count, a scoped run whose
  REDs are exactly the records that file owns, the refused empty set, the `--from-git` derivation on a
  synthetic repository) and a tally line in the shape the loop's gate parses, so the suite reports numbers
  instead of none.
- **`tools/loop/loop.conf`** maps both harness paths to the harness's own self-test, so a change to either file
  is gated by the suite that proves it.
- **`.pi/skills/revert-check`** states the two flags in its loop-harness block.

The change is additive: no record's label, file, case or expression changes, and the whole set is still what a
run without a filter executes. Rollback is the revert of the four files; nothing else depends on the flags.

## Impact

- Affected files: `tools/loop-mutations.sh`, `tools/loop-mutations-selftest.sh`, `tools/loop/loop.conf`,
  `.pi/skills/revert-check/SKILL.md`, the delta in `openspec/specs/agent-issue-loop/spec.md`.
- Affected modules: none — no Kotlin, Java or C++ file is touched, and no Gradle task is owed by these paths
  (the run's gate still runs the build when its base carries a compiled path, which is the gate's own reading).
- Specifications changed: `agent-issue-loop` (one ADDED requirement; no existing requirement is modified).
- Guidelines: no guideline text changes. The rule (`Build.md` §2 rule 2) already says iterate focused and gate
  once; this change is the shell-gate mechanism that makes the focused form possible, and the skill that owns
  the harness's usage is updated instead.
- Breaking: none. A caller that passes no filter sees the same set and no extra line. A caller whose filter
  matched nothing used to get exit 0 and now gets exit 2 — that is the fix.
