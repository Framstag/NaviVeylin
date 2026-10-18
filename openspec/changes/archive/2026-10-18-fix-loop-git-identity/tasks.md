# Tasks — fix-loop-git-identity

Parent requirement: `agent-issue-loop`, "Version control belongs to the run's script, not to the agent" (one
MODIFIED scenario added). `[#167]` marks the tracker item this closes.

## 1. The red case first (the defect proven before the fix)

- [x] 1.1 `tools/portability-cases.sh`: `foreign_env()` runs with a synthetic global git config that carries
      the neutral defaults and **no** identity, plus `GIT_CONFIG_SYSTEM=/dev/null` and a `HOME` without a
      `.gitconfig`. Verifies *a commit carries the run's own identity* in the environment a runner has —
      case `foreign#emptyGateCommandStillRunsThePhases`.
- [x] 1.2 Record the red-on-HEAD run: `bash tools/check-portability-selftest.sh --case
      foreign#emptyGateCommandStillRunsThePhases` must fail with `expected 'done', got 'blocked'` — the CI
      symptom — and the case name plus the message go into the change's `report.md` (with `git commit`'s own
      "no identity" message from a throwaway repo as the mechanical cause).

## 2. The fix

- [x] 2.1 `tools/loop/lib.sh`: `loop_commit_identity()` — the acting login from
      `loop_issue_tool policy.sh preflight --json`, rendered `name<TAB>email`; a login that cannot be
      resolved stops the run with a named reason (design D1).
- [x] 2.2 `tools/loop/worktree.sh`: both `end-item` commits pass `-c user.name=… -c user.email=…` from that
      identity, so no ambient configuration is consulted.
- [x] 2.3 `bash tools/check-portability-selftest.sh --case foreign#emptyGateCommandStillRunsThePhases` is
      green, and the whole portability suite (21 cases) is green.

## 3. The case of its own

- [x] 3.1 `tools/loop-cases.sh`: `loop#commitsWithoutAnAmbientIdentity` — unset the throwaway repository's
      local identity, run one full item with the isolated configuration, and assert the result is `done`
      **and** that both commits' author is `<login> <login@users.noreply.github.com>` (the login the stub
      `gh` answers). Verifies the scenario on both of its clauses (the run completes; the identity is the
      run's own).
- [x] 3.2 `bash tools/loop-selftest.sh` green (all cases), and the new case fails when the fix is reverted
      (that is 4.1's mutation).

## 4. Falsification

- [x] 4.1 `tools/loop-mutations.sh`: one mutation that drops the identity options from the commit — the case
      `loop#commitsWithoutAnAmbientIdentity` must go red, the file is restored, the case green again.
- [x] 4.2 `bash tools/loop-mutations.sh` and `bash tools/loop-mutations-selftest.sh` green; the mutation's
      line is recorded in the change's `report.md`.

## 5. Documentation and closure

- [x] 5.1 `guidelines/Backlog.md` §7 (the loop): one sentence — a commit is attributed to the identity the
      run acts as, resolved by the same preflight that authorises the run, so no ambient git configuration
      is needed.
- [x] 5.2 `openspec validate fix-loop-git-identity --strict`; the change's `report.md` carries the red-on-HEAD
      evidence, the fix, the mutation and the traceability (scenario → case).
- [x] 5.3 `[#167]` After the push, the CI run's `Check portability` step must be green (the end-to-end check,
      recorded in `report.md`), and the item is commented with the fix and its evidence.
