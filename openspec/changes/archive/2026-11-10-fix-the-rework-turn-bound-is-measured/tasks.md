# Tasks

Reference: `proposal.md` for the priced raise, `specs/agent-issue-loop/spec.md` for the contract.

## 1. The cases, red first

Requirement: "Every role's turn bound is the one the run's header prints, and the rework round's is the measured 150",
its two scenarios.

- [x] 1.1 `run#eachChildIsBoundedByItsOwnRolesTurns` extended rather than a second reader of `child-turns-max=` added:
      the header's token is asserted whole,
      `child-turns-max=worker:20,worker-gate:100,worker-tail:65,worker-rework:150,reviewer:8,reviewer-pre-gate:110` —
      the rework role's 150 and the five other roles at the figures this case's conf records, which is the same
      `child_turn_bound` read the run's header prints. Its `# files:` line already declares `tools/loop/run.sh`,
      `tools/loop/loop.conf` and `tools/pi-child-events.sh`, and it is unchanged.
- [x] 1.2 `state#theReworkRoundIsARoleOfItsOwn` extended: the header carries `worker-rework:3`, the figure the case
      records in its own throwaway conf, beside the `LOOP-TIMEOUT` line that names the same key and role — so the bound
      the transcript applies and the bound the header prints are the same figure.
- [x] 1.3 Red on the base engine (`fde7757c`, `tools/loop/loop.conf` and `tools/loop/run.sh` stashed, the cases as
      edited): `run#eachChildIsBoundedByItsOwnRolesTurns` FAILs on
      *and every role's figure in that order, the rework round's 150 among the five the project records (issue #842):
      'child-turns-max=worker:20,worker-gate:100,worker-tail:65,worker-rework:150,reviewer:8,reviewer-pre-gate:110'
      not found in: … child-turns-max=worker:20,worker-gate:100,worker-tail:65,reviewer:8,reviewer-pre-gate:110 …* —
      the role is absent from the token and the key reads 100. `state#theReworkRoundIsARoleOfItsOwn` FAILs on
      *',worker-rework:3,' not found in: … child-turns-max=worker:200,worker-gate:100,worker-tail:65,reviewer:110,reviewer-pre-gate:110 …*.

## 2. The change

Requirement: the same, both scenarios.

- [x] 2.1 `tools/loop/loop.conf`: `LOOP_CHILD_TURNS_MAX_WORKER_REWORK` 100 → 150, the derivation in the same comment
      block in the shape the neighbouring bounds use — #831's 82 084 tokens a turn (16 252 700 over 198 turns), fifty
      turns ≈ 4.1 Mtok = 3.7 % of that run's 109.8 Mtok for an item already 4 279 s in, the counter-weight #686 (44 % of
      the archive's spend in the 35 items past 250 turns), and the statement that this is a measured experiment read on
      the cut items' overshoot and the after-the-first-gate share (78 % of 70.2 Mtok), with the context bound's refusal
      named where it is recorded rather than copied.
- [x] 2.2 `tools/loop/run.sh`: `worker-rework` added to the `child_turn_bounds` loop, in the position `agent-roles=`
      uses, and the comment above it extended with the rule — a bound the header does not print is one a run cannot be
      audited for (issue #842).
- [x] 2.3 Green: both cases PASS; `--group run` (142 cases) PASS; `run#childPastItsTurnBoundIsCut` PASS;
      `driver#everyCaseDeclaresTheFilesItCanReach` PASS.
- [x] 2.4 No other key and no other bound: `child_turn_bound` over the six roles reads 200 / 100 / 65 / 150 / 110 / 110
      after the change against 200 / 100 / 65 / 100 / 110 / 110 before it.

## 3. The falsification

- [x] 3.1 `269.1` (`tools/loop/tests/loop-mutations.sh`) restores the key to 100 → `RED
      run#eachChildIsBoundedByItsOwnRolesTurns` on the same assertion, restore green.
- [x] 3.2 The header omission, by hand, because one record names one file: `worker-rework` dropped from the header loop
      → `RED run#eachChildIsBoundedByItsOwnRolesTurns` on the same assertion, restored, PASS.
- [x] 3.3 `tools/loop/tests/loop-mutations.sh --check` over the whole set: 316 records apply to the files they name.

## 4. The prose the change makes false

- [x] 4.1 `guidelines/Backlog.recipes.md` §7b — the section that owns a bound's value: the rework role's 150 in the
      value list, the header's `child-turns-max=` token and its role rule, the raise as a paragraph of its own with the
      priced derivation and the counter-weight, and #836 + this run's six-over/one-under cluster appended to the
      recorded refusal (one place per fact, no second refusal).
- [x] 4.2 `tools/check-doc-routes.sh` at 0 after the prose edit.
- [x] 4.3 `guidelines/BacklogTooling.md` §11 lists the same keys, so `WORKER_REWORK` moves there too (100 → 150); its
      other four figures and §7b's five are left as they are — a finding of its own, reported with this change and
      filed separately, since a doc stating values stale against the configuration is not this item's one-value diff.

## 5. The gate (not run by this change's own pass)

- [ ] 5.1 `tools/loop/gate.sh --base main` on the change, with `LOOP-GATE-TREE` and `LOOP-GATE` quoted into the archive.
- [ ] 5.2 The whole loop suite standalone: `tools/loop/tests/loop-selftest.sh --from-git main`.
- [ ] 5.3 The independent read-only review, then `openspec archive
      2026-11-10-fix-the-rework-turn-bound-is-measured`.
- [ ] 5.4 The re-read the raise is an experiment for: the next run's cut items' overshoot and its after-the-first-gate
      share, against 2026-11-09's six-over/one-under split.
