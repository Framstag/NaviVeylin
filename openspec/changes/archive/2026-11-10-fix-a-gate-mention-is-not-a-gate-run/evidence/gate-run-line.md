# gate run — item #830, `./tools/loop/gate.sh` run once

LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/.pi/worktrees/loop/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
LOOP-GATE-POOL: at most 4 suite step(s) in flight at once — the first steps and the build run alone
LOOP-GATE-TREE: head=674fbda40b8f9f653c032e120aac6c72f0a6696e diff=3f84ee620ff95e43

LOOP-GATE: tests=978 failures=0 errors=0 (tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 12s, tools/loop/tests/loop-selftest.sh --from-git 8a2f1adbdaced0f79c4c2fccf5dfeab321d07a47 639s, bash tools/loop/tests/loop-mutations.sh --check 4s, tools/loop/tests/loop-mutations-selftest.sh 729s, bash tools/gen-feature-list.sh --check-classification 5s, tools/check-doc-routes.sh 2s, tools/tests/check-doc-routes-selftest.sh 8s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 1s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 0s, tools/tests/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/tests/check-portability-selftest.sh 26s, tools/tests/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/tests/portability-mutations.json --list 0s, tools/issues/tests/issue-tooling-selftest.sh 97s, tools/mutate.sh --mutations tools/issues/tests/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/create-mutations.json --list 1s, tools/mutate.sh --mutations tools/issues/tests/close-mutations.json --list 1s, tools/mutate.sh --mutations tools/issues/tests/duplicates-mutations.json --list 1s, tools/mutate.sh --mutations tools/issues/tests/decision-question-mutations.json --list 1s)

Per-suite tallies copied to `gate-tally/*.xml` (21 files) from
`/home/tim/projects/NaviVeylin/.pi/worktrees/loop/app/build/test-results/loop-gate/`.
