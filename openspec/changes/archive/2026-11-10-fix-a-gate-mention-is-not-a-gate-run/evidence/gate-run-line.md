# gate run — item #830, `./tools/loop/gate.sh` run once on the merged tree (the run that attests it)

LOOP-GATE-PROFILE: /home/tim/projects/NaviVeylin/tools/loop/loop.conf
LOOP-GATE-STEP-BOUND: 1800s per step — a step that reaches it is ended, named and counted as an error
LOOP-GATE-POOL: at most 4 suite step(s) in flight at once — the first steps and the build run alone
LOOP-GATE-TREE: head=2452c0075529ce543370421d5a71fb3b3cdca108 diff=b299aa4b3adbdc95

LOOP-GATE: tests=997 failures=0 errors=0 (tools/loop/tests/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 13s, tools/loop/tests/loop-selftest.sh --from-git 8c726e152feb314c3e3e4a5851ea72872b29bf27 733s, bash tools/loop/tests/loop-mutations.sh --check 4s, tools/loop/tests/loop-mutations-selftest.sh 739s, bash tools/gen-feature-list.sh --check-classification 7s, tools/check-doc-values.sh 0s, tools/tests/check-doc-values-selftest.sh 0s, tools/check-doc-routes.sh 1s, tools/tests/check-doc-routes-selftest.sh 7s, tools/mutate.sh --mutations tools/tests/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 1s, tools/check-openspec-hygiene.sh 0s, tools/tests/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/tests/check-portability-selftest.sh 26s, tools/tests/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/tests/portability-mutations.json --list 0s, tools/issues/tests/issue-tooling-selftest.sh 96s, tools/mutate.sh --mutations tools/issues/tests/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/create-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/issues/tests/decision-question-mutations.json --list 0s)

This is the gate of the tree the change lands on: `diff=b299aa4b` at `head=2452c007`. The commits after it
touch only `evidence/` — a path `LOOP_REVIEW_EXCLUDE_PATHSPEC` (`loop.conf:800`) leaves out of the digest,
so `diff` stays `b299aa4b` while `head` moves; both are recorded here rather than one being called the other.

Rejected runs, with their numbers:

- first run on the merged tree: `tests=598 failures=0 errors=1`, `diff=7f2fdad8` at `head=6f67ca18` — a
  collision, not the change: `loop-selftest.sh --from-git` found `.pi/tmp/.loop-selftest.lock` held by
  another suite (owner 2800790) and exited in 1 s;
- second run, also green, was on a tree that then moved: `diff=7f2fdad8` — superseded by the one above.

Per-suite tallies are not copied: `app/build/test-results/loop-gate/` is shared with any other suite on
the host and two files in it carried another run's 1800 s step. The console below is the record.

Round 1's and round 2's own lines stay as `gate-run-line-round1.md` and `-round2.md`, with their tallies.
