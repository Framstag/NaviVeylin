# Design — fix-a-bound-is-clamped-by-the-callers-ceiling

## Root cause

A bound this project applies is a recorded value, and the ceiling of the process that started the call is not an
input to it: `tools/loop/loop.conf:174` (`LOOP_CASE_MAX_SECONDS=900`) and `tools/loop/loop.conf:187`
(`LOOP_GATE_STEP_MAX_SECONDS=1800`) are read by `tools/loop-selftest.sh:137` and `tools/loop/gate.sh:60`, and nothing
in the tree reads a ceiling at all. A caller whose ceiling is smaller therefore ends the call first — measured twice
on 2026-10-28 at `900000ms`, with the child's work uncommitted — and the project's own clock never fires, so neither
the suite's summary nor the gate's `LOOP-GATE-STEP:` line is written.

## The single fix

The ceiling becomes an input, and the two invocations that apply a bound honour it:

- `tools/loop/loop.conf` records `LOOP_CALL_CEILING_SECONDS`, empty by default, and a fill-if-unset expansion lets a
  caller name it at the call site without editing a file.
- `tools/loop/lib.sh` reads it once (`loop_ceiling`) and answers the smaller of a bound and the ceiling
  (`loop_bound_under_ceiling`).
- `tools/loop-selftest.sh` starts one watcher for the ceiling beside the per-case watcher it already starts, and the
  handler prints `LOOP-SUITE-CEILING:` with the counts it holds, then exits non-zero through the EXIT trap that
  already ends the pool's process groups, its watchers and its scratch tree (`on_exit`). The watcher is ended in
  `on_exit` on the normal path too, so a green suite leaves no `sleep` behind.
- `tools/loop/gate.sh` clamps `step_max`, so the gate's own poll loop ends the step and writes
  `LOOP-GATE-STEP: … exceeded <n> s`, which the tally and `tools/gate-attribute.sh` read.
- Every bound watcher — the ceiling's and each case's — is started under `set -m` and ended by its process **group**
  (`loop_end_group`), not by the subshell that forked it: ending the subshell alone left the `sleep` holding the
  bound standing, one per case, which the two `driver#` cases now count at their own scratch root.

The semantics of the key are "the last second this invocation may still be running", not "the caller's hard kill":
a caller that knows it is killed at 900 s names 890, and the margin between the two is the caller's to choose. That
keeps the project from inventing a margin nobody measured, and it keeps the number the caller's rather than a
project constant — the defect the item names.

## Blast radius

Four engine paths, one case file, one mutation set and one guideline section. Nothing reads the new key when it is
empty: `loop_ceiling` prints nothing and the suite starts no watcher, so a tree that never sets it behaves as it does
today, including every existing case. The gate's clamp is a `min()` of two whole seconds; when no ceiling is named
`step_max` is the recorded value it always was, which is why the existing `gate#*` cases are untouched.

## Rollback

Revert the change. The configuration key disappears with it and no state, tally or evidence field is added, so a tree
that reverts and a tree that never carried it are the same tree.

## Alternatives the evidence eliminated

- **Raise the harness ceiling** (the item names this as what this is not). The ceiling is the caller's and differs per
  caller; the harness that killed these two children exports none — `grep -n 'TIMEOUT' docs/environment-variables.md`
  names only `PI_TUI_ESC_TIMEOUT` — so the project could neither read it nor raise it, and a project that assumes a
  large one is killed by a smaller one. That is what happened here twice in one day.
- **One suite per host.** Already a recorded rule: "One suite at a time runs in a tree"
  (`openspec/specs/agent-issue-loop/spec.md:2152`) and the suite refuses a second one in the same repository with
  exit 2 (`tools/loop-selftest.sh:11-13`). It also bounds nothing about a *single* call: one 500 s suite plus the gate
  steps around it still passes a 900 s ceiling, and the second child of 2026-10-28 was killed with no peer running.
- **A `--group`-per-call driver.** The filters exist (`tools/loop-selftest.sh:12`: `--case`, `--group`, `--files`) and
  `guidelines/Build.md:144-149` already routes an edit through them, so there is no missing mechanism to add. What a
  filter cannot do is bound the one invocation that must be whole — the gate's own step, or the `--from-git` suite the
  gate owes — which is exactly the invocation that was killed.
