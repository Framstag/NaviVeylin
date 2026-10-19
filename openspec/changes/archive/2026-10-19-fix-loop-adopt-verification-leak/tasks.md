# fix-loop-adopt-verification-leak — tasks

Spec requirement the defect contradicts: `openspec/specs/tooling-portability/spec.md` — **"An engine names
no project value"**, whose scenario "The check passes on the project it guards" is the contract HEAD broke.
The change declares no spec delta: the requirement already states the rule, and this change restores
compliance with it rather than changing it.

## 1. The red case (before the fix)

- [x] 1.1 The reproducing check is `bash tools/check-portability.sh`, which is the case the requirement names
      (`portability#passesOnTheRealTree` drives the same command).
- [x] 1.2 Red on HEAD `a45fcc4`: the command reports
      `FAIL tools/loop/worktree.sh:241 … names the project value 'verification' (tools/tooling.json
      namespaces.category)`, the same at line 261, `2 leak(s)`, exit 1 →
      `evidence/red-on-head.log`.

## 2. The fix

- [x] 2.1 `tools/loop/worktree.sh`, `cmd_adopt_item`: rename the local variable `verification` → `evidence`
      in its declaration, its `[ -n … ]` refusal check and the `adopted: … evidence %s` argument. No value,
      output or behaviour changes.
- [x] 2.2 `grep -rn verification tools/loop/` shows the remaining occurrences are the function name
      `carried_verification` and prose in comments — neither is read as a named value.

## 3. The gates

- [x] 3.1 `bash tools/check-portability.sh` — `check-portability: OK — 40 engines and 2 templates name no
      project value (0 value(s) read from the project)`, exit 0 → `evidence/green-on-fix.log`. This is the
      falsifier of §1's red state and the structural check of the file this change edits (no compiled file
      is touched: `guidelines/Build.md` §2 scope block).
- [x] 3.2 `bash tools/check-portability-selftest.sh --case portability#passesOnTheRealTree` — green; the case
      that caught the leak on the real tree → `evidence/scoped-cases.log`.
- [x] 3.3 `bash tools/loop-selftest.sh --case run#mergesAFinishedAttemptAndClosesTheItem` — green; the adopt
      path this rename touches still merges and closes → `evidence/scoped-cases.log`.
- [x] 3.4 No new invariant, bound or refusal path is introduced (a rename), so no revert-check is added
      beyond §1.2/§3.1, which are each other's falsification.
- [ ] 3.5 **Owed**: the full `bash tools/loop-selftest.sh` suite and the loop mutation set
      (`bash tools/loop-mutations.sh`) — not run here to keep this change inside its budget; neither the
      change's target line nor an invariant changes, and the next full gate run closes this gap.

## 4. The records

- [x] 4.1 `openspec validate fix-loop-adopt-verification-leak --type change` — exit 0; `openspec validate
      --specs` — 0 failed.
- [x] 4.2 The change is archived as `openspec/changes/archive/2026-10-19-fix-loop-adopt-verification-leak/`
      with this evidence.
- [x] 4.3 Committed in the repository's two-commit shape: the fix, then the archive with its evidence.
- [x] 4.4 Issue #176 carries one comment naming the regression, the rename and the evidence. Its labels are
      not touched (`agent/eligible` is reserved to a person, `tools/issues/update.sh` refuses it), so the
      closure of #176 remains the owner's, as `guidelines/Backlog.md` §5 requires.
