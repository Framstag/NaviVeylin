# Tasks

Every task below is owed by the one added requirement, `agent-issue-loop` — "One suite at a time runs in a tree".

## 1. The lease — the requirement, first half

- [x] 1.1 `tools/loop-selftest.sh`: `selftest_lock_take` creates `<scratch root>/.loop-selftest.lock` holding the pid that took it and the moment it did.
- [x] 1.2 The refusal: exit 2, the rule named, the owner's own record repeated, and nothing of the holder's removed.
- [x] 1.3 The lease: an owner that is not running any more is taken over and named; a live owner is the refusal.
- [x] 1.4 The release last in `on_exit`, after the suite's own scratch tree is gone.
- [x] 1.5 `selftest_runs_one_named_case` — the exemption the requirement's second half states — with `is_glob` moved up beside it, one definition and two readers.

## 2. The case — the requirement's three scenarios

- [x] 2.1 `driver#aSecondSuiteInOneRepositoryIsRefused` in `tools/loop-cases.sh`, registered in `LOOP_SHELL_CASES` and in the dispatch.
- [x] 2.2 Scenario "A second suite in one repository is refused before it runs a case": its first half starts the suite that holds the repository's lock when this invocation holds none, and refuses the second suite by the name of the process that holds it.
- [x] 2.3 Scenario "One named case is not a suite, and a pattern is": its second half runs one named case and is refused for a pattern and for a group.
- [x] 2.4 Scenario "A lease whose owner is gone is taken over and released": its third half, in a scratch root of its own — the takeover, the name of the process that is gone, and the release.

## 3. Falsification

- [x] 3.1 `tools/loop-mutations.sh`: four records in a new `55.x` block, one per invariant — `55.1` the lock is taken, `55.2` a pattern is a suite while one named case is not, `55.3` the release, `55.4` the takeover.
- [x] 3.2 The red case on HEAD, before the fix: `evidence/red-HEAD.log` and `evidence/red-HEAD.xml` (`tests="1" failures="1"`).
- [x] 3.3 `bash tools/loop-mutations.sh --only "55.1 55.2 55.3 55.4"`: every record red when applied, green again after the restore (`evidence/revert-check-55.log`).
- [x] 3.4 `bash tools/loop-mutations.sh --check`: every record still applies to the file it names.

## 4. The documents

- [x] 4.1 `guidelines/Backlog.md`, section "9. Checks": one paragraph — the lease, what it refuses, what it exempts, and the measurement behind it.
- [x] 4.2 The spec `agent-issue-loop`: the ADDED requirement with its three scenarios.

## 5. The gate and the evidence

- [x] 5.1 `bash tools/loop-selftest.sh`: the whole set (211 cases), because this delta touches the driver and the case file, both core files — `evidence/full-suite.log`.
- [x] 5.2 Every red case of all three whole-set runs attributed — isolated on this tree and on the pristine base, or named as the load's: `evidence/whole-set-runs.log`, `evidence/isolated-reds.log`, `evidence/baseline-reds.log`, `evidence/gate.log`.
- [x] 5.3 `bash tools/loop/gate.sh --base a0ca0f35…`: the delta's own gate — the whole set, `tools/loop-mutations-selftest.sh`, `bash tools/loop-mutations.sh --check`, `tools/check-doc-routes.sh`, `tools/check-todo-references.sh`; every tally in `evidence/`.
- [x] 5.4 `bash .pi/skills/fix-loop/scripts/evidence-check.sh openspec/changes/fix-loop-one-suite-per-repository --root .`: PASS — `evidence/evidence-check.log`.
- [x] 5.5 An independent read-only review of the diff and the evidence (`reviewer`, 2026-10-24), and every blocking finding answered: the per-tree bound worded where it was overstated, the absent `revert-check-55.log` produced, the takeover's staging window closed (an unreadable owner is read again), the case's own way out made to end the suite it started, and the citations corrected.
- [x] 5.6 Archive the change with its evidence and leave the tree clean.
