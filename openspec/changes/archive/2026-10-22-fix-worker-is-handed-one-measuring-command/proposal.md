# fix-worker-is-handed-one-measuring-command

## Why

Issue #246. Verified on the run's base commit `80df3a3` on 2026-10-21, in the loop's worktree
(`.pi/worktrees/loop`).

**The observation reproduces, count for count.** Over the five items the issue names, the tool calls in
`.pi/issue-loop/items/<n>/events.jsonl` (`tool_execution_end`, `toolName`) are:

```
69:  total=266 shell=142 reads=79 writes=38
133: total=256 shell=157 reads=66 writes=27
11:  total=221 shell=141 reads=33 writes=45
13:  total=112 shell= 73 reads=22 writes=16
98:  total=373 shell=244 reads=74 writes=48
```

— `grep -c '"tool_execution_end"'` and `grep '"tool_execution_end"' | grep -c '"toolName":"ctx_shell"'`
per item, which is the issue's 140 to 244 shell calls (the first row read 144 when this table was first
written, and the tree says 142: re-measured 2026-10-22, review round 1). What the calls *are* is the other half of the
diagnosis: over the same files, `sed -n` and `grep` are the two commonest shapes after the `cd` every call
opens with, and the paths they read are mostly `guidelines/` (47, 52, 9, 8, 68 calls) and `openspec/` (70, 64,
100, 32, 119). And 8 to 49 of each item's 22 to 79 file reads are a **second** read of a path the item had
already read (`jq -r … .args.path | sort -u | wc -l` against the total). The facts are gathered one call at a
time, and the run pays a model turn per call.

**The absence is in the tree today.** `tools/loop/prompts/worker.md` hands the worker the gate command and
its skills and nothing else: before this change `grep -o '{{[A-Z_]*}}' tools/loop/prompts/worker.md` listed
`{{ISSUE}} {{CHANGE_ROOT}} {{WORK_PACKAGE}} {{EVIDENCE_DIR}} {{BASE}} {{GATE_CMD}} {{WORKER_SKILLS}}`, so
*which* checks the change's pathspec owes (`tools/loop/gate.sh:74-88`, the same mapping the gate reads),
*which* paths it touches, what tallies the tree already holds and whether the delta's scenarios each name a
case were all found by hand, one call each.

**Root cause, one sentence:** the worker's task names the gate and no reading command, so every fact the
item needs before it plans — the pathspec, the checks that pathspec owes, the tallies the evidence already
carries, and the delta's scenarios counted against their cases — is a separate discovery call, and the run
pays one model turn per call.

**Red test on HEAD, before the fix.** Three cases in `tools/loop-cases.sh`, run one at a time with
`bash tools/loop-selftest.sh --case '<case>'` against the base commit; all three fail on HEAD because what
they assert does not exist yet:

```
FAIL measure#namesTheOwedChecksInOneBlock        — the measuring command succeeded: bash: …/tools/loop/measure.sh: Datei oder Verzeichnis nicht gefunden: expected '0', got '127'
FAIL measure#printsTheNumbersTheReviewerAsksFor  — the measuring command succeeded: bash: …/tools/loop/measure.sh: Datei oder Verzeichnis nicht gefunden: expected '0', got '127'
FAIL prompt#workerIsHandedTheMeasureBlock        — the configuration names no measuring command
```

`evidence/red-HEAD.log` (run copy, 2026-10-21T23:58:48Z, each case `EXIT=1`) and `evidence/red-HEAD.xml`.

**The block exposed one defect on the way.** `gate.sh --plan` reads the profile's suite map with
`while … read` over `printf '%s' "$suites" | tr ';' '\n'`; a value printed without a trailing newline leaves
its last line undelimited, so `read` returns non-zero and the final pair is never consulted. Measured
2026-10-22 (`evidence/gate-suite-map-probe.txt`, `2026-10-22T00:24:05Z`): the project's value is 18 pairs, the
undelimited form iterates 17 times and the delimited one 18, and the two-pair value the first case uses
iterates 1 time against 2 — so the pair the profile ends with (`AGENTS.md|tools/check-doc-routes.sh`) was
never owed. The delta's contract — the block names *every* check the pathspec owes — is false until that line
ends with a newline, so the fix is part of this change and `measure#namesTheOwedChecksInOneBlock` asserts the
second pair (`tools-check`) by name.

**Owning capability and guideline section.** `agent-issue-loop` (the run renders the worker's task; the
requirement added below). The rule it acts on is the iteration protocol's — `guidelines/Build.md` §2,
"Agent iteration protocol", rule 3 ("batch independent questions") and rule 1 ("look, then measure") — which
states the discipline without stating the command a worker is given.

surface: 5 cases added (3 in round 1, 2 in the round-2 corrections below), 1 case rewritten, 1 prompt section
extended, 3 mutations, one file mode bit, 0 fixtures — `grep -c "^case_measure_\|^case_prompt_workerIsHandedTheMeasureBlock"
tools/loop-cases.sh` (5), `grep -c '^  .39\.[0-9]' tools/loop-mutations.sh` (3).

## Corrections after review round 1

Review round 1 refused the change on two blocking findings and five notes. Everything below is measured in
`.pi/issue-loop/items/246/` and copied into `evidence/`.

1. **The green artifact was a failing run.** `evidence/focused-cases-green.log` held a pre-fix run of
   `prompt#workerIsHandedTheMeasureBlock` that ends `FAIL`. It is replaced by a real post-fix run of all six
   cases the change's scenarios name, written at `2026-10-22T00:23:26Z`–`00:23:43Z`, each `PASS` and `EXIT=0`
   (`evidence/focused-cases-green.log`). The red half of the two round-2 cases is
   `evidence/red-round2.log` (`2026-10-22T00:22:55Z`–`00:22:59Z`, both `FAIL`, `EXIT=1`), its counts derived
   into `evidence/red-round2.xml` the way `gate.sh` derives a suite's tally (two `FAIL` lines, two cases).
2. **The gate was red because a suite the profile names has no execute bit.** Every other suite the profile
   names is `100755`; `tools/check-action-runtimes-selftest.sh` landed as `100644` in #240 and CI invokes it as
   `bash tools/check-action-runtimes-selftest.sh` (`.github/workflows/build.yml:297`), so nothing noticed. The
   profile names a command and runs it directly, and a direct invocation exits 126 — measured before and after
   in `evidence/suite-mode-probe.txt` (`2026-10-22T00:23:53Z` exit 126; `00:24:00Z` after `chmod +x`, 10/10,
   exit 0). The repair is the mode bit, not a `bash` prefix in the profile: the profile is the same for every
   suite, and the anomaly was the one file's mode. The gate is then green (`evidence/gate.log`).
3. **The block chose between two open changes, and its `--change` resolved nothing.** It measured the
   alphabetically last open change — in this very worktree a change left open by another item — and a bare
   name passed to `--change` failed the `[ -d ]` test, so the flag printed `LOOP-MEASURE-CHANGE none`. The
   block now names two open changes as ambiguous and measures neither, resolves a `--change` name under the
   open changes and then under the archive (where a correction round works), and names a name it cannot
   resolve. Red first: `evidence/red-round2.log` shows the old block reporting `LOOP-MEASURE-CHANGE fix-two`
   for a tree with `fix-one` and `fix-two` open.
4. **The tally list was capped and silent.** `head -20` truncated both directories with no line saying so;
   the block now prints the cap's worth and one line carrying `withheld of total`. Red first, same log.
5. **The gate's exit status was a pipeline's.** `evidence/gate.log` now records `EXIT` taken from the gate
   itself, not from `tee`, and names the JVM half's `tests=0` as the zero it is: this delta touches no compiled
   file, the profile's `*.xml` pattern made the gradle half owed anyway, and it ran up-to-date (`168
   up-to-date`, `BUILD SUCCESSFUL`), so `tests=` counts the tool suites alone.
6. **Two attribution corrections.** The #69 row read `shell=144`; the tree says 142 (re-measured above). And
   the `#240` prose in `guidelines/Build.md` that review round 1 flagged is not this change's delta — no commit
   of this change touches `guidelines/` (`git show --stat b4fd28e 17f77d8`); it is base drift from #240.
