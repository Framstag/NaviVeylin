# gate run — item #830, `./tools/loop/gate.sh` run once on the merged tree (the run that attests it)

LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
LOOP-GATE-POOL: at most 4 suite step(s) in flight at once — the first steps and the build run alone
LOOP-GATE-TREE: head=6f67ca1858818eddbacaa7e64f37cf5b1c959725 diff=7f2fdad8b4779bd5

LOOP-GATE: tests=997 failures=0 errors=0 (tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 14s, tools/loop/tests/loop-selftest.sh --from-git 8c726e152feb314c3e3e4a5851ea72872b29bf27 597s, bash tools/loop/tests/loop-mutations.sh --check 6s, tools/loop/tests/loop-mutations-selftest.sh 629s, bash tools/gen-feature-list.sh --check-classification 8s, tools/check-doc-values.sh 1s, tools/tests/check-doc-values-selftest.sh 1s, tools/check-doc-routes.sh 2s, tools/tests/check-doc-routes-selftest.sh 12s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/tests/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/tests/check-portability-selftest.sh 28s, tools/tests/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/tests/portability-mutations.json --list 0s, tools/issues/tests/issue-tooling-selftest.sh 108s, tools/mutate.sh --mutations tools/issues/tests/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/decision-question-mutations.json --list 0s)

The step lines above the aggregate are the gate's own per-step durations — the tallies behind them
were read from `app/build/test-results/loop-gate/`, which this repository shares with any other suite
running at the time, so this record keeps the gate's own console log (`gate.log`, beside this file)
instead of copying that directory: a copy was taken once and two of its files carried another run's
1800 s step. Round 1's tallies stay as `gate-tally-round1/` and round 2's as `gate-tally-round2/`.

The first run on this tree was red for a collision, not for the change: `loop-selftest.sh --from-git`
found `.pi/tmp/.loop-selftest.lock` held by another suite (owner 2800790) and exited in 1 s, and the gate
reported `tests=598 failures=0 errors=1`. The re-run above is the one that attests the tree.
