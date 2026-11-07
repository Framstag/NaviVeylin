# Round 2 — the review's one blocking correction, applied and gated

The run announced review round 2 of this change (`evidence/review-round-1-requested.md` is the round-1 record). The
reviewer's report carried one `BLOCKING:` and nine `NOTE:` lines, and its `LOOP-CORRECTIONS-ONLY:` said the fix was
strictly substitutive — no new claim, case or assertion. This round applies that one correction and nothing else.

## The correction

`tools/loop/loop-clean.sh` grew its header by seven lines (the block "The loop's own trees are leftovers too",
lines 30–36), so the last header line moved from 38 to 45 and `--help`'s range stopped one line short. One line, at
`tools/loop/loop-clean.sh:60`:

```
-    -h|--help) sed -n '2,44p' "$0"; exit 0 ;;
+    -h|--help) sed -n '2,45p' "$0"; exit 0 ;;
```

`tools/loop/loop-clean.sh:45` is now `#   --quiet           print the summary only, not one line per path`, so
`--quiet` is back in the help text and `tools/loop/loop-clean.sh:46` is `set -uo pipefail`. No case asserts the help
text, so no case, assertion or claim changed with it.

## The gate

One gate run, on the corrected tree, against the run's base commit `4522a4124feec63c0394343f7420e5ce8176b6a7`
(`tools/loop/gate.sh --plan --base <that commit>` printed the 21 steps):

```
LOOP-GATE: tests=854 failures=0 errors=0 (tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach 10s, tools/loop-selftest.sh --from-git 6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db 438s, bash tools/loop-mutations.sh --check 3s, tools/loop-mutations-selftest.sh 265s, bash tools/gen-feature-list.sh --check-classification 4s, tools/check-doc-routes.sh 1s, tools/check-doc-routes-selftest.sh 7s, tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --list 0s, tools/check-todo-references.sh 2s, tools/check-openspec-hygiene.sh 0s, tools/check-openspec-hygiene-selftest.sh 0s, tools/check-portability.sh 1s, tools/check-portability-selftest.sh 24s, tools/mutate-selftest.sh 1s, tools/mutate.sh --mutations tools/portability-mutations.json --list 0s, tools/issue-tooling-selftest.sh 94s, tools/mutate.sh --mutations tools/status-mutations.json --list 0s, tools/mutate.sh --mutations tools/create-mutations.json --list 1s, tools/mutate.sh --mutations tools/close-mutations.json --list 0s, tools/mutate.sh --mutations tools/duplicates-mutations.json --list 0s, tools/mutate.sh --mutations tools/decision-question-mutations.json --list 0s)
```

The log is `evidence/gate-round2.log`; the tallies it wrote are the 21 XMLs under `evidence/gate-results-round2/`.
Their sum, re-read from those files:

```
$ sed -n 's/.*tests="\([0-9]*\)" failures="\([0-9]*\)" errors="\([0-9]*\)".*/\1 \2 \3/p' evidence/gate-results-round2/*.xml \
  | awk '{t+=$1;f+=$2;e+=$3} END {printf "sum tests=%d failures=%d errors=%d\n",t,f,e}'
sum tests=854 failures=0 errors=0
```

That is the gate's own line. 21 tallies against round 1's 18: `gen-feature-list --check-classification`,
`check-openspec-hygiene` and `check-openspec-hygiene-selftest` are in this round's set and were not in
`evidence/gate-results/`.

The cases this change answers for pass in this run: `worktree#aSetAsideBranchThatCarriesWorkIsNamedNotRemoved` and
`worktree#aSetAsideBranchWhosePatchesLandedIsRemoved` at `evidence/gate-round2.log:192` and `:216`, and
`clean#reportsAWorktreeNoRunKeeps` at `evidence/gate-round2.log:919`. The corrected `tools/loop/loop-clean.sh` is the
file the suite read: `tools/loop-selftest.sh --from-git 6ad5c485e6e3bc206a311ee7cc5fa2c70d3d59db` selected 336 of 336
cases (`evidence/gate-round2.log:7`), passed all of them in 435.90 s (`evidence/gate-round2.log:983`), and the log
carries no `FROM-CACHE` line.

## What this round did not touch

The nine `NOTE:` lines of the round-1 report each say "needs no round"; none was applied and none is answered here.
The red test the change was planned against is unchanged and still carried by `evidence/red-on-HEAD.xml` —
`worktree#aSetAsideBranchWhosePatchesLandedIsRemoved` (and its set-aside sibling) failing on HEAD, tests=2 failures=2.
