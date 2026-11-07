# Tasks

Reference: `proposal.md` for scope, `design.md` for why, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The role and the label

Requirement: "A next-up label leads the queue and decides nothing else".

- [x] 1.1 `tools/tooling.json`: the value `next-up` in `namespaces.agent`, the role `nextUpAgent: "next-up"`, and
      the roles comment naming `prescreen.sh` as a reader. Verify: `jq -r '.roles.nextUpAgent' tools/tooling.json`.
- [x] 1.2 `tools/loop/lib.sh`: the optional-key list (`LOOP_OPTIONAL_ROLE_KEYS`) carries `nextUpAgent` beside the
      shaping keys, so a profile that omits it reads as "this project has no next-up label" instead of stopping.

## 2. The ranking

- [x] 2.1 `tools/loop/prescreen.sh`: the role is read once (`next_up_agent`, `next_up_label`), every candidate's
      record carries `pinned`, and the ranking lifts the flagged records ahead of the joined order, in the class
      and issue order the other streams keep.
- [x] 2.2 `tools/loop/state.sh` (`queue`) and `tools/loop/run.sh` (the dry run's listing) carry and print the flag.

## 3. The cases and the falsification

- [x] 3.1 The fixture `tools/loop/fixtures/scenarios/pinned/` (seven items: two labelled, a leading defect, an
      unpinned app bug, a held labelled item, a labelled item by an unauthorised author, an unpinned improvement).
- [x] 3.2 `tools/loop-cases.sh`: `prescreen#aPinnedItemLeadsTheQueue` and `prescreen#aProfileWithNoPinPinsNothing`,
      registered in the case list and the dispatch. Verify: both green (`evidence/cases.log`).
- [x] 3.3 `tools/loop-mutations.sh`: `259.1` takes the lift out, and `22.1` is re-pointed at the renamed join line.
      Verify: both red on their mutation and green on restore (`evidence/mutation.log`); `--check` over
      `tools/loop/prescreen.sh` reports all 11 records apply.

## 4. The documents

- [x] 4.1 `guidelines/Backlog.recipes.md`: the ranking paragraph names the per-item override, the label, the rank
      rule and the measurement.
- [x] 4.2 `guidelines/Backlog.md`: the capability table gains the row for this change.
