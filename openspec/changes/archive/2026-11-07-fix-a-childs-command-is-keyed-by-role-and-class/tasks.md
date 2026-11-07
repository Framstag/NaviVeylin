# Tasks

Reference: `proposal.md` for scope, `design.md` for the chain and the eliminated alternatives,
`specs/agent-issue-loop/spec.md` for the contract.

## 1. The red cases

Requirement: "A child is started on the command its role and its class record", scenarios "A role inherits the command
of the role it belongs to", "The rework round is a role of its own" and "An item's class selects the worker's
command".

- [x] 1.1 Add `state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo` to `tools/loop-cases.sh`: two wrapper
      commands log `<prompt> <CMD>`, the project's key records one and `LOOP_AGENT_CMD_REVIEWER` the other, and the run
      takes the pre-gate shape (`STUB_CHANGE_REPORT`, `--review on`). The case asserts the pre-gate child and the round
      after the gate both went through `REVIEWER-CMD`, and the header reads
      `agent-roles=reviewer,reviewer-pre-gate`. Verified red on HEAD: `the pre-gate round ran on the command of the
      role it belongs to, recording no key of its own: expected '1', got '0'`
      (`.pi/issue-loop/items/821/red-on-HEAD-state-aRolesChildInheritsTheCommandOfTheRoleItBelongsTo.log`), and green
      after the change (`…/green-state-aRolesChildInheritsTheCommandOfTheRoleItBelongsTo.log`).
- [x] 1.2 Add `state#theReworkRoundIsARoleOfItsOwn`: the round after the gate refuses once, so the run buys the rework
      round; the rework child's action appends three `turn_end` events to the item's stream and waits, and
      `LOOP_CHILD_TURNS_MAX_WORKER_REWORK` records 3. The case asserts the rework child went through
      `LOOP_AGENT_CMD_WORKER_REWORK` and that its transcript carries the `LOOP-TIMEOUT` line naming
      `LOOP_CHILD_TURNS_MAX_WORKER_REWORK` for the `worker-rework` role. The keys the delta adds are appended to the
      case's copy of `loop.conf` when it records none, so the case reports what the run does with them instead of
      failing on their absence. Verified red on HEAD: `the rework round was started on its own role's command: expected
      '1', got '0'` (`…/red-on-HEAD-state-theReworkRoundIsARoleOfItsOwn.log`).
- [x] 1.3 Add `state#anItemsClassSelectsTheWorkerCommand`: a two-item scenario with one `class/bug` and one
      `class/feature` item, `LOOP_AGENT_CMD_WORKER_BUG` and `LOOP_AGENT_CMD_WORKER_FEATURE` recorded beside a project
      command, and `LOOP_AGENT_CMD_WORKER_TAIL` recorded on the project's one so the tail shows that a role's own key
      wins over a head's class key. Verified red on HEAD: `the bug item's worker pass ran on the command its class
      records: expected '1', got '0'` (`…/red-on-HEAD-state-anItemsClassSelectsTheWorkerCommand.log`).
- [x] 1.4 Rewrite `state#aRolesOwnCommandStartsOnlyThatRolesChild`'s header assertion: with the reviewer's key recorded
      the header names both roles, because the pre-gate round inherits it. Verified red on HEAD —
      `'agent-roles=reviewer,reviewer-pre-gate' not found in: … agent-roles=reviewer`
      (`…/red-on-HEAD-state-aRolesOwnCommandStartsOnlyThatRolesChild.log`) — and green after.

## 2. The engine

- [x] 2.1 `tools/loop/run.sh`: `child_role` gains the `*round2*` arm → `worker-rework`, placed after `*-tail*`,
      `*pre-gate*` and `*gate*` and before `worker-*`, and its comment names the role and the reason a round is a role
      here.
- [x] 2.2 `tools/loop/run.sh`: `child_agent_cmd_key <role> <class>` walks the five candidates of `design.md` D1 and
      `run_child` reads its key from it; the if-line that applies the key is unchanged, so mutation `77.1` still quotes
      it.
- [x] 2.3 `tools/loop/run.sh`: the header's role loop compares each role's resolved key with `LOOP_AGENT_CMD`, so a
      role that inherits is named.
      Verified: the three cases of section 1 pass, `state#aRolesOwnCommandStartsOnlyThatRolesChild` passes,
      `state#agentCommandIsTheOnlyIndirection` (every per-role key blanked) passes, and so does
      `state#agentCommandRefusesAnEmptyValue`; `run#thePreGateReviewRunsBeforeTheGate` passes.

## 3. The configuration

- [x] 3.1 `tools/loop/loop.conf`: record `LOOP_AGENT_CMD_WORKER_GATE`, `LOOP_AGENT_CMD_WORKER_REWORK` and
      `LOOP_AGENT_CMD_REVIEWER_PRE_GATE` beside the existing keys, with the chain and the class suffix in the block's
      comment, and `LOOP_CHILD_TURNS_MAX_WORKER_REWORK=100` in the turn-bound block with its derivation (this change's
      `evidence/round2-turns.log`).
      Verified: `state#defaultsComeFromLoopConf`-class behaviour is unchanged (no key the engine reads lost its value;
      the three added keys are empty, which is what the header and the fallback case read).

## 4. The guideline

- [x] 4.1 `guidelines/BacklogTooling.md` "Where an agent runs": the table gains the per-role and per-class form of
      `LOOP_AGENT_CMD`, and the turn-bound row lists `WORKER_REWORK` with its value. `PORTING.md`'s item 3 names the
      per-role key beside the project's one.
      Verified: `tools/check-doc-routes.sh` — all 329 section references resolve.

## 5. The declaration and the mutation record

- [x] 5.1 Every new case declares the files it can reach in its first comment line.
      Verified: `driver#everyCaseDeclaresTheFilesItCanReach` passes.
- [x] 5.2 `tools/loop-mutations.sh`: record `77.2` mutates `child_agent_cmd_key`'s candidate walk away, so every child
      falls back to the project's command; the case that names the invariant is
      `state#aRolesChildInheritsTheCommandOfTheRoleItBelongsTo`.
      Verified: `bash tools/loop-mutations.sh --check --only 77.2` — the record applies; `--only 77.2` — RED on the
      named case and green again after the restore (`red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`).
