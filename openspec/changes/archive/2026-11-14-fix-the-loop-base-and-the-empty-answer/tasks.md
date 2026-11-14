# Tasks — the loop reads its own base, and an empty answer is asked again

- [x] 1.1 `tools/loop/gate.sh`: the base is resolved to the first of `--base`, the run's recorded base, the base
      branch's merge-base, `origin/main`'s merge-base and `HEAD~1` that this tree knows and that is an ancestor of
      HEAD, with the branch's tip beating a recorded base; the gate prints `LOOP-GATE-BASE: <sha> (from <source>)`
      before its first step and keeps its `loop_die` when nothing resolves.
      Verified: `bash tools/loop/tests/loop-selftest.sh --case gate#theBaseIsReadFromTheRunNotFromTheRemote` —
      `PASS gate#theBaseIsReadFromTheRunNotFromTheRemote`.
- [x] 1.2 `gate#theBaseIsReadFromTheRunNotFromTheRemote` added to `tools/loop/tests/loop-cases.sh`, registered in
      the case list and the dispatcher, declaring its files.
      Verified: with `main`'s engine in the tree the same case reads
      `FAIL gate#theBaseIsReadFromTheRunNotFromTheRemote` / `loop-selftest: 1 of 1 cases failed`.
- [x] 1.3 Mutation record `882.1` in `tools/loop/tests/loop-mutations.sh` removes the branch's tip beating the
      run's record.
      Verified: `--check` — `loop-mutations: all 1 cases passed — every selected record applies to the file it
      names`; `--only 882.1` — `RED 882.1 gate#theBaseIsReadFromTheRunNotFromTheRemote` …
      `restore 882.1 … green again` … `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`.
- [x] 1.4 The five sources and the printed line are prose in `guidelines/BuildGate.md` §2.
- [x] 2.1 `tools/loop/run.sh`: the `answer-empty` class — a final report that exists, is 0 bytes and carries no
      verdict, `LOOP-TIMEOUT` or `LOOP-DEADLINE`, over a tree that is not at the base — spends one retry of the
      same budget, re-runs the same child with the same prompt in place, prints the class and records the step;
      a second empty answer stops the run with both attempts and their byte counts, and a report carrying a
      verdict is never re-run. A prompt of the chosen child is tracked beside its report so the retry has it.
- [x] 2.2 `run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop` added to `tools/loop/tests/loop-cases.sh`, registered
      in the case list and the dispatcher, declaring its files. Verified:
      `PASS run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop`.
- [x] 2.3 The case is red on the base engine. Verified: with `main`'s `tools/loop/run.sh` in the tree —
      `FAIL run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop` / `loop-selftest: 1 of 1 cases failed`.
- [x] 2.4 Mutation record `882.2` in `tools/loop/tests/loop-mutations.sh` makes the arm ask its question only
      while the tree is at the base. Verified: `--check` — `all 1 cases passed`; `--only 882.2` —
      `RED 882.2 run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop` … `restore 882.2 … green again` …
      `red-when-mutated: 1 · not-noticed: 0 · restore-failures: 0`.
- [x] 2.5 The `answer-empty` class is prose beside `child-start` in `guidelines/Backlog.recipes.md`, naming the
      same child and prompt, the in-place retry and the stop with both byte counts.
- [x] 3.1 The delta in `openspec/changes/fix-the-loop-base-and-the-empty-answer/specs/agent-issue-loop/spec.md`:
      two `ADDED` requirements, each body under 500 characters with its example in the scenario.
      Verified: `openspec validate fix-the-loop-base-and-the-empty-answer --strict`.
- [x] 3.2 The named cases, each run on its own with the tree as this change leaves it. Verified:
      `bash tools/loop/tests/loop-selftest.sh --case gate#theBaseIsReadFromTheRunNotFromTheRemote` —
      `PASS gate#theBaseIsReadFromTheRunNotFromTheRemote`; and
      `bash tools/loop/tests/loop-selftest.sh --case run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop` —
      `PASS run#anEmptyAnswerOverWorkIsRetriedBeforeTheStop`. The four cases of the `state#` group this change's
      prompt tracking runs through were re-run the same way, each `PASS`: `state#projectKeysAreReadFromLoopConf`,
      `state#agentCommandIsTheOnlyIndirection`, `state#aRolesOwnCommandStartsOnlyThatRolesChild`,
      `state#theReworkRoundIsARoleOfItsOwn`. The wide `--from-git` leg over every declared case was started and
      abandoned by hand: it selects the whole set in this tree and does not finish inside a bounded call.
- [x] 3.3 The structural checks, all four green: `bash tools/check-doc-routes.sh` — `check-doc-routes: all 340
      section references resolve, all 7 skill references resolve`; `bash tools/check-doc-values.sh` —
      `LOOP-CHILD-TURNS-MAX: 6 roles, prose matches config`; `bash tools/check-openspec-hygiene.sh` —
      `OpenSpec artifacts are consistent: 1 open change(s) carry task markers, the operation guidance is quoted,
      and every capability spec states its Purpose.`; `bash tools/check-portability.sh` —
      `check-portability: OK — 60 engines and 8 templates name no project value (126 value(s) read from the
      project)`; and `bash tools/loop/tests/loop-mutations.sh --check` —
      `loop-mutations: all 362 cases passed — every selected record applies to the file it names`.
- [x] 4.1 `a889b477` repaired the tracking the retry reads: a shape with no gate child tracks the worker's prompt
      (and a continuation's), so the run does not die under `set -u`.
      Verified: `state#agentCommandIsTheOnlyIndirection` and its three siblings `PASS` after the repair, where
      each was `FAIL` before it.