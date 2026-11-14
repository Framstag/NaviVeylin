# Tasks — a red gate records which tree fails the case it names

Ordered; each task names its own verification. The cases are written first, so their red on HEAD is a measurement and
not a claim.

- [x] 1.1 Add `run#aRedGateIsAttributedToTheTreeThatFailsIt` to `tools/loop/tests/loop-cases.sh`, registered in the
      case list and the dispatcher, declaring its files. Verified: on the base it failed with `expected 'delta', got
      'null'` (`evidence/red-on-HEAD.log`, `evidence/red-on-HEAD.xml`, tests=1 failures=1), and it passes on this tree
      (`evidence/red-case-green.log`, `evidence/red-case-green.xml`, tests=1 failures=0).
- [x] 1.2 Add `gate#aRedGateWritesTheLogItIsAttributedFrom` for the gate's own half: it runs `tools/loop/gate.sh` in a
      throwaway project and asserts that the log stands beside the tallies, that `LOOP-GATE-LOG:` names it, that it
      carries the step block and the verdict, and that `tools/gate-attribute.sh` reads the case out of it. Verified:
      `evidence/gate-log-green.log`, `evidence/gate-log-green.xml` (tests=1 failures=0). It cannot pass at the base —
      `LOOP-GATE-LOG` is a line only this change's `gate.sh` prints.
- [x] 1.3 `gate#aCompiledStepsTallyIsNotHidden` counted every visible file in the tally directory, and the gate's log is
      now one of them. Its count is of the tallies a `*.xml` glob finds, so it still asserts what it was written for —
      a plain listing finds the compiled step's tally — without reading a log as a tally. Verified:
      `loop-selftest --case gate#aCompiledStepsTallyIsNotHidden` passes.
- [x] 2.1 `tools/loop/gate.sh`: a `log_line` that appends what the console prints, set to
      `<LOOP_GATE_TALLY_DIR>/gate.log` when the tally directory is claimed, announced as `LOOP-GATE-LOG: <path>`, with
      the step body and the verdict routed through it. Verified: `gate#aRedGateWritesTheLogItIsAttributedFrom`, and the
      gate group stayed green in the focused run below. The project's own gate over this tree is the gate child's.
- [x] 2.2 `tools/loop/run.sh`: `attribute_red_gate` and its one call site, with the base tree made, bounded, removed and
      mapped (`0` → `delta`, `2|124|137` → `unattributable`, other → `base`). Verified: the case's three halves, and
      `run#aRedGateIsBlockedByTheTail` / `run#aCloseOverARedGateIsWaivedOrBlocked` unchanged in the focused run.
- [x] 2.3 `tools/loop/run.sh`: `--gate-attribution` on the `closed`, `blocked` and `cut` record calls when the field is
      set. Verified: the case's `metrics.gateAttribution` assertions (`delta`, `base`, `unattributable`).
- [x] 2.4 `tools/loop/state.sh`: the flag, the closed-vocabulary refusal, the refusal beside a tally that passed, and
      `metrics.gateAttribution` after `gateWaivedSuite`. Verified: the state group green in the focused run, and the
      blocked arm of the case records the field beside the tally it did not rewrite.
- [x] 2.5 `tools/loop/report.sh`: the `attributed:` line beside `waived:`. Verified: the case's report assertion —
      `attributed: 1 of 1 items recorded which tree fails the case their gate named (delta ×1)`.
- [x] 3.1 `guidelines/BuildEvidence.md` §4d gains the rule the engine implements. Verified:
      `bash tools/check-doc-routes.sh` (all 339 section references resolve), `bash tools/check-doc-values.sh` and
      `bash tools/check-todo-references.sh` pass.
- [x] 3.2 The delta in `openspec/changes/fix-a-red-gate-records-which-tree-fails-it/specs/agent-issue-loop/spec.md`,
      kind MODIFIED on requirement "An item's record keeps what the run measured to decide it": `metrics.gateAttribution`
      as the closed vocabulary `delta | base | unattributable` beside a red tally, its refusal on a tally that passed,
      and the three scenarios `run#aRedGateIsAttributedToTheTreeThatFailsIt` exercises. Verified: the delta file
      carries those clauses as written; the `openspec archive --yes` merge is owed at archive time — this round runs
      it before the gate and confirms `openspec/specs/agent-issue-loop/spec.md` then carries the field.
- [x] 4.1 Focused verification of the affected cases, its XML copied into this change's `evidence/`. Verified:
      `bash tools/loop/tests/loop-selftest.sh --from-git 647b289a53e9cf17064c5f341ef8621ee3c3de02` —
      `all 426 cases passed (JDK 27, offline, no token, no sandbox) — 612.29 s` (`evidence/focused-from-git.log`,
      `evidence/focused-from-git.xml`, tests=426 failures=0). That run is against the base the change was written on;
      the base has since moved to the run's `d25972c1d85a990eb8405108c70bbe419c930eb7` and 4.3–4.5 re-measure there.
- [x] 4.2 One mutation per invariant, in the engine's own harness (the item's Cost clause). Verified:
      `tools/loop/tests/loop-mutations.sh --only '853.1 853.2 853.3'` — `red-when-mutated: 3 · not-noticed: 0 ·
      restore-failures: 0`, and `--check` reports every selected record applies (`evidence/mutations-853.log`); and
      `--check` on the tree as it stands reads `loop-mutations: all 360 cases passed — every selected record applies to
      the file it names` (`evidence/mutations-853-check-2026-11-14.log`, tests=360 failures=0).
- [x] 4.3 Re-measured on the run's base, the case as the only delta: a tree at `d25972c1` with this change's
      `tools/loop/tests/loop-cases.sh` copied over it and no production hunk of the change. Verified:
      `expected 'delta', got 'null'` / `FAIL run#aRedGateIsAttributedToTheTreeThatFailsIt` / `loop-selftest: 1 of 1
      cases failed` (`evidence/red-on-HEAD-2026-11-14.log`, `evidence/red-on-HEAD-2026-11-14.xml`, tests=1 failures=1).
- [x] 4.4 The repair of the first gate's red on the mutation self-test: the copy it mutates carries the engine's second
      sibling, `--engine` refuses a copy without it, and the self-test asserts that refusal.
      Verified: `bash tools/loop/tests/loop-mutations-selftest.sh` — `all 36 cases passed`
      (`evidence/loop-mutations-selftest-2026-11-14.log`, `evidence/loop-mutations-selftest-2026-11-14.xml`,
      tests=36 failures=0), where the gate's own tally of that step on the tree before the repair read
      `tests="34" failures="2"` (`evidence/gate-tallies/tools_loop_tests_loop-mutations-selftest.sh.xml`); and
      `tools/loop/tests/loop-mutations.sh --files tools/loop/state.sh --engine <copy>` — `selected 36 of 360 records
      (files: tools/loop/state.sh)` … `red-when-mutated: 36 · not-noticed: 0 · restore-failures: 0`, with `853.2`'s own
      `restore 853.2 … green again` (`evidence/mutations-853-statefileset-2026-11-14.log`,
      `evidence/mutations-853-statefileset-2026-11-14.xml`, tests=36 failures=0).
- [x] 4.5 The delta's own cases on this tree, after the repair. Verified: `--case '*RedGate*'` — `all 7 cases passed
      (JDK 27, offline, no token, no sandbox) — 32.63 s` (`evidence/focused-red-gate-2026-11-14.log`,
      `evidence/focused-red-gate-2026-11-14.xml`, tests=7 failures=0), and `--case gate#aCompiledStepsTallyIsNotHidden`
      — `PASS` (`evidence/focused-tally-count-2026-11-14.xml`, tests=1 failures=0).

No build task: this change touches no compiled file, so the structural checks above stand in for the build, and the
gate child runs the project's own gate over the completed tree.
