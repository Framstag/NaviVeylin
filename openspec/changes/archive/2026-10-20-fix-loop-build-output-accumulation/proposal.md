# fix-loop-build-output-accumulation

## Why

The loop keeps its worktree at the end of a run on purpose — the next run inherits the checkout, the
submodule and the git state instead of paying the measured 11-minute cold build again (design D4,
`.pi/skills/fix-loop/REFERENCE.md`, "Measured costs"). Nothing ever removes what a build leaves inside that
kept tree, so the tree it keeps is not a checkout plus a build cache: it is a checkout plus *every* build the
run paid for, and every run adds to it. The self-test has the same shape in miniature: it removes its scratch
tree with one `EXIT` trap, and a suite the harness kills leaves the tree behind.

Measured on the developer host on 2026-10-20 (issue #210, `state: OPEN`, `class/improvement`,
`category/build-and-harness`):

- `du -sh app/build` = **6.5 GB** in the project tree;
- `du -sh .pi/worktrees/loop` = **3.2 GB**, of which `.pi/worktrees/loop/app/build` alone was **1.9 GB**;
- `.pi/tmp` held **90 MB** of `loop-selftest.*` scratch trees, dated 2026-10-19 21:04 and 2026-10-20
  07:06/07:13/07:15 — four suites that were killed;
- `df -h /` = **88 %** (391 GB of 471 GB used, 57 GB free).

Re-measured in this worktree while the change was written, with the command it introduces:
`tools/loop/loop-clean.sh --worktree <the developer host's kept loop worktree>` reported **55 271 424 B**
across `auto/build` (26 714 112 B), `core/build` (24 743 936 B), `buildSrc/build` (2 596 864 B) and
`osmscout-client-java/build` (1 216 512 B) — and removed nothing, which is what a report does
(`evidence/green/loop-clean-on-the-kept-worktree.txt`).

**Root cause and its evidence.** `tools/loop/worktree.sh` (`cmd_create`, `cmd_remove`, and `end_run` in
`tools/loop/run.sh:219-226`) knows exactly two outcomes for the worktree — *keep it whole* and *remove it
whole* — and `end_run`'s kept branch prints `worktree kept for the next run: …` and does nothing else; no
script under `tools/loop/` names a build directory, so nothing in the loop can remove one.
`tools/loop-selftest.sh:46` removes the scratch tree from one `trap 'rm -rf "$tmp"' EXIT`, and it created the
tree under a path of its own (`"$root/.pi/tmp"`), so no tool in the repository can find the trees of a suite
that died without its trap.

The three cases this change adds fail on HEAD `5dd5b35` (`evidence/red/`):

- `worktree#keepsSourcesNotBuildOutput` — `the kept worktree still carries its build output: 116K` (the
  throwaway project's `app/build` after a run that kept the tree);
- `clean#interruptedSuiteRemovesItsScratchTree` — `the suite never created a scratch tree under the recorded
  root …/case.8IAOM0/tmp` (the suite writes its scratch tree wherever it likes, so `kill -TERM` and
  `loop-clean` cannot be shown to meet at one path);
- `clean#reportsWithoutApplyAndRemovesWithIt` — `…/tools/loop/loop-clean.sh: Datei oder Verzeichnis nicht
  gefunden` (there is no command that reports what the loop left).

**The single fix.** Three connected pieces, one root cause — *the loop's leftovers have no owner*:

1. when the run ends and keeps the worktree, it removes the module build directories the project records in
   `LOOP_WORKTREE_SCRATCH_PATHS` (`worktree.sh strip-scratch`, called from `end_run`), confined to
   `$LOOP_WORKTREE` and refusing to follow a symlink, to leave the tree, or to touch a path git tracks;
2. `tools/loop-selftest.sh` traps `HUP`, `INT` and `TERM` beside `EXIT`, so an interrupted suite removes its
   scratch tree the way a failing one does, and it creates that tree under the loop's recorded temporary root
   (`LOOP_TMP_DIR`) so a tool can find it;
3. `tools/loop/loop-clean.sh` reports — and with `--apply` removes — the recorded build paths of a kept
   worktree and the self-test scratch trees under the loop's temporary root that are older than
   `LOOP_TMP_STALE_MINUTES`, idempotently.

**Spec requirement the defect contradicts.** `agent-issue-loop`, "The wrapper owns the worktree, and its cost
stays a decided number" (this delta). The loop already promises the caller a *decided* budget rather than an
accumulating one — §"A run is recorded" and the item floor derived from measured cost — and it reports the
worktree it keeps as an action the caller owes. A tree that grows by gigabytes per run, and scratch trees no
command reports, are the opposite: a cost nobody decided and nothing measures.

## Cases and evidence

Three cases in `tools/loop-cases.sh`, each registered in **both** the `--list` array and the dispatch map:

- `worktree#keepsSourcesNotBuildOutput` — a run whose child builds `app/build` and leaves a symlink at another
  recorded path (`core/build` → a directory outside the tree) ends by keeping the worktree: `app/build` is
  gone, the symlink and the file it points at are untouched, the change's source file is still there, the tree
  is clean, and the run prints the bytes it removed (≥ the 102 400 B the child wrote).
- `clean#interruptedSuiteRemovesItsScratchTree` — the project's own driver, driven with a cases file whose one
  case sleeps and with `LOOP_TMP_DIR` pointing at a directory of the case's own, creates its scratch tree
  there; `kill -TERM` ends the suite, the tree is gone and the exit status is not 0 (no false PASS).
- `clean#reportsWithoutApplyAndRemovesWithIt` — `loop-clean.sh` on a kept worktree carrying 65 536 B of build
  output plus one stale and one fresh scratch tree: without `--apply` it names all of them with a total and
  removes nothing; with `--apply` it removes exactly the build output and the stale tree, leaves the fresh
  tree, the sources and the git state alone, and reports the same bytes it reported before; a second
  `--apply` prints `nothing to remove` and exits 0.

Red on HEAD `5dd5b35`: `evidence/red/`. Green on the fix: `evidence/green/`, which also holds the two existing
cases on this surface, `worktree#keepsByDefaultAndRemovesOnRequest` and
`run#resetWorktreeDiscardsWhatItNames`, and the real measurement of the developer host's kept worktree
(`evidence/green/loop-clean-on-the-kept-worktree.txt`). The whole offline suite is **not** run by this change:
the orchestrator runs it once on the merged tree.

## What Changes

- `tools/loop/loop.conf` — three recorded keys: `LOOP_WORKTREE_SCRATCH_PATHS` (the module build directories a
  kept worktree loses, empty for a project with no build system), `LOOP_TMP_DIR` (where the tooling puts
  throwaway trees; empty means `.pi/tmp` under the project root) and `LOOP_TMP_STALE_MINUTES` (the age below
  which a scratch tree may belong to a live suite).
- `tools/loop/lib.sh` — `LOOP_SELFTEST_PREFIX`, `loop_tmp_resolve`, `loop_scratch_paths`, `loop_bytes_of` and
  `loop_scratch_remove`: the one implementation of "remove a recorded build path from a tree, or say why it
  stays", shared by the run's end step and by `loop-clean.sh`.
- `tools/loop/worktree.sh` — the new `strip-scratch` command, and its header/usage lines.
- `tools/loop/run.sh` — `end_run` calls `worktree.sh strip-scratch` when it keeps the tree, and not under
  `--clean-worktree` or `--reset-worktree`. Nothing else in `run.sh` changes: not `run_child`, not the report,
  not the state, not the floor.
- `tools/loop/loop-clean.sh` — the new report-and-remove entry point (`[--worktree <dir>] [--apply] [--quiet]`).
- `tools/loop-selftest.sh` — the `HUP`/`INT`/`TERM` traps beside `EXIT`, the scratch root read from
  `LOOP_TMP_DIR` through `lib.sh`, and the comment that says `SIGKILL` cannot be trapped.
- `tools/loop-cases.sh` — the three cases, registered in both places, plus the `run_clean` helper and the
  assertion in `run#engineNamesNoProjectFact` that the recorded list stays in the configuration.
- `tools/check-portability.sh` — the five recorded build paths join the value set, so an engine that names one
  fails the check.
- `.pi/skills/fix-loop/SKILL.md` — a new section "What the loop leaves on disk", the owner table's
  `worktree.sh`/`loop-clean.sh` rows, the `--clean-worktree` row and the reference list.
- `.pi/skills/fix-loop/REFERENCE.md` — "Running the loop in a worktree" now says what a kept tree keeps, and a
  new section "What a kept worktree keeps, and what the loop leaves on disk" carries the measurements, the
  two flags the removal respects and the `loop-clean` commands.

## Impact

Affected files, all of them the loop's own harness — no app, native, Gradle or manifest file is touched:

| file | module / area |
|---|---|
| `tools/loop/lib.sh` | the shared helpers every loop script sources (nothing here runs on its own) |
| `tools/loop/worktree.sh` | the worktree's lifecycle: create, item branches, commits, remove, and now strip |
| `tools/loop/run.sh` | `end_run` only — the run's end step |
| `tools/loop/loop-clean.sh` | new entry point, no caller inside the loop |
| `tools/loop/loop.conf` | the loop's recorded configuration (three keys) |
| `tools/loop-selftest.sh` | the loop's offline gate: its traps and its scratch root |
| `tools/loop-cases.sh` | the gate's cases (three new, one assertion added) |
| `tools/check-portability.sh` | the value set that keeps the engine portable (five values) |
| `.pi/skills/fix-loop/SKILL.md`, `REFERENCE.md` | the skill that owns the loop's rules and its facts |

No Android module, resource, native source or JNI bridge is touched: this is not a phone, car or AAOS change,
so the parity rules, the native-build rules and the `submodule patch` / `local override` decision do not
arise. `guidelines/Build.md` does not describe the loop's disk cost anywhere (checked: the loop's cost lives in
`.pi/skills/fix-loop/REFERENCE.md`, "Measured costs"), so no guideline is superseded; the portability seam is
unchanged in kind — three keys in `loop.conf`, one of them derived — and `tools/check-portability.sh` now reads
103 values over 43 engines and 2 templates, naming no project value.

## Previous specifications changed

- `openspec/specs/agent-issue-loop/spec.md` — **ADDED** three requirements: "A kept worktree carries the
  sources, not the build output", "A suite ended by a signal removes its scratch tree", and "One command
  reports and removes what the loop left". No existing requirement, scenario or wording is modified: the
  worktree requirements that exist (isolation, one branch per item, the stale-base refusal, the kept tree)
  stay exactly as they are, and this delta adds the cost rule they do not state.

## Additive or breaking, and rollback

**Additive.** A new command (`worktree.sh strip-scratch`), a new entry point (`loop-clean.sh`), three new keys
whose defaults are the empty/derived value, three new signal traps in a test driver, and three new cases. The
run's flags, exit codes, report, state, floor, branches and labels are untouched; `--clean-worktree` still
removes the whole tree and `--reset-worktree` still resets what it names and nothing more. A kept worktree that
this change strips is still a valid git worktree on the same branch at the same commit — the removed paths are
gitignored build directories only.

Rollback: revert this commit. The run keeps its build output again, the self-test loses three traps and goes
back to a hard-coded scratch root, and `loop-clean.sh` disappears. Nothing on disk needs repairing: an
already-stripped tree is exactly what the next build expects, and a tree this change left alone is untouched.
No branch, label, archived change or issue is affected, because the change writes none of them.
