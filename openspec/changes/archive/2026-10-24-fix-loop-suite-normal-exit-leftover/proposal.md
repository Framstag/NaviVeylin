# A suite that ends normally leaves no process of its cases behind

## Why

The production fix for issue #233 is already on main — `ec1f8b2b`, "fix(loop): three pre-run blockers", which
gave `tools/loop/lib.sh` the `loop_pids_under`/`loop_end_pids_under` pair, made `loop-clean.sh` use it, and
added the sweep of the suite's own scratch root to `tools/loop-selftest.sh`'s `on_exit`:

```
  loop_end_pids_under "$tmp"
  rm -rf "$tmp"
```

What did **not** land is the second half of the issue's own *Fix shape*: *"a case that runs a suite whose case
starts a run and returns before the run is done, and then asserts the run is gone."* The case set has
`clean#interruptedSuiteEndsItsProcessGroup` (the **signal** path) and `clean#endsARunWhoseTreeIsAlreadyDeleted`
(the sweep's two primitives, driven directly), but nothing drives a suite's **normal exit** with a process left
behind — so that sweep line is asserted by nothing at all.

**Root cause of the remaining defect.** `run_case` clears `case_pgid` once the last case has been waited for
(`tools/loop-selftest.sh:229`–`235`), so a suite that reaches its normal end enters `on_exit` with no group left
to signal, and a process the case put in a group of its own — the shape `run_child` needs for the wall-clock cap
— is unreachable by the group signal. The sweep is the only thing that ends it, and the shipped requirement
("A suite ended by a signal ends the case it was in, and its processes", `openspec/specs/agent-issue-loop/spec.md`)
and every case it names speak of **signals** only. The measured symptom was 44 reparented `run.sh` processes,
2 to 4.6 h old, a `sleep 1` child each, at trees that had already been removed (issue #233, 2026-10-21T09:41Z).

**Repro command** (the defect this change proves, and the only state in which it is observable):

```bash
bash tools/loop-mutations.sh --only 53.11    # drops the sweep line, runs the new case: RED
```

**The single resulting fix**: one case, `clean#suiteThatEndsNormallyEndsTheProcessItsCaseLeft`, which drives the
normal exit — a nested suite whose case starts a process in a group of its own at a working directory inside the
suite's scratch root and returns while it polls — and asserts the process is gone once the suite has exited. Its
permanent form is the mutation record `53.11`, so the sweep cannot be removed again unnoticed.

**The requirement the defect contradicts** is `agent-issue-loop`'s "A suite ended by a signal ends the case it
was in, and its processes": it SHALL hold on **every** exit path, not only on a signal.

The gate's own red set is attributed in `evidence/attribution.md`: 3 of 210 cases fail, of which one is open item
#288's documented red on `main`'s tip and the other two move between runs (the pristine-`main` control fails a
*different* two). The residual — two suites in one repository redden a moving set — is filed as **#295**.

## What Changes

- `tools/loop-cases.sh`: the new case, registered in the case list and the dispatcher, declared for
  `tools/loop-selftest.sh`, `tools/loop/`, `tools/issues/` and `tools/loop-cases.sh` — the paths it reaches.
- `tools/loop-mutations.sh`: record `53.11` — drop the sweep line in `../loop-selftest.sh`, and the new case
  must go red. `--check` already applies it to a copy, so the project's gate owes the record.
- `openspec/specs/agent-issue-loop/spec.md` (after archive): one `MODIFIED` requirement, "A suite ended by a
  signal ends the case it was in, and its processes" — its body states the invariant for every exit path, its
  existing scenario is repeated verbatim, and one scenario is added for the normal exit.

No engine behaviour changes: `tools/loop/run.sh`, `tools/loop/lib.sh`, `tools/loop/loop-clean.sh` and
`tools/loop-selftest.sh` are untouched. The delta is a case, its permanent mutation record, and the requirement
that owns it.

## Capabilities affected

`agent-issue-loop` — one `MODIFIED` requirement, one added scenario, every other scenario repeated verbatim and
the requirement's name kept (it is the requirement the defect contradicts; renaming it would be a removal plus
an addition the validator reports). The requirement's body is rewritten to the invariant the fix implements and
stays under the 500-character limit the validator reads on a shipped spec.

Additive, no interface change: the case names, the suite's exit statuses, the sweep's output and `loop-clean`'s
report are untouched. Rollback path: revert the change; the sweep line is asserted by nothing again, exactly as
it is today, and no engine file changes.

## Impact

| path | what changes |
|---|---|
| `tools/loop-cases.sh` | the case, the case list entry, the dispatcher entry |
| `tools/loop-mutations.sh` | record `53.11` |
| `openspec/specs/agent-issue-loop/spec.md` | the modified requirement and its new scenario, after archive |

No `:app`, `:auto`, `:core`, `buildSrc`, native or Gradle path is touched; no guideline is superseded (the rule
the change adds is owned by the `agent-issue-loop` requirement, and `guidelines/Build.md` §2a's "a defect fix
owes a test that fails on HEAD before the fix" is what the mutation record makes permanent). No submodule patch
and no bridge override.

## Not in this change

- **The engine's own sweep is not widened.** It sweeps the suite's scratch tree, not the whole `LOOP_TMP` root:
  a concurrent suite's processes live under that root and must not be signalled by this one (see `design.md`).
- **The run's own child, and the runs a run's gate starts** — #227's poll loop and the run-side teardown — are
  other items' and are already covered by `run#interruptedRunEndsTheChildItStarted`.
- **The other two blockers of `ec1f8b2b`** (#227, #241) keep their own cases and are not restated here.
- **The moving failures of the full suite** — two `prompt#` cases in this change's run, two `run#` cases in the
  pristine-`main` control — are not fixed here: they move between runs, the only common failure is open item
  #288's documented red, and the residual is filed as **#295** with both failure sets and the live harnesses
  that were running in the same repository (`evidence/attribution.md`).
