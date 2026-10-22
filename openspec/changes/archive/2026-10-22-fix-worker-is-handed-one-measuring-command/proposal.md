# fix-worker-is-handed-one-measuring-command

## Why

Issue #246. Verified on the run's base commit `80df3a3` on 2026-10-21, in the loop's worktree
(`.pi/worktrees/loop`).

**The observation reproduces, count for count.** Over the five items the issue names, the tool calls in
`.pi/issue-loop/items/<n>/events.jsonl` (`tool_execution_end`, `toolName`) are:

```
69:  total=266 shell=144 reads=79 writes=38
133: total=256 shell=157 reads=66 writes=27
11:  total=221 shell=141 reads=33 writes=45
13:  total=112 shell= 73 reads=22 writes=16
98:  total=373 shell=244 reads=74 writes=48
```

— `grep -c '"tool_execution_end"'` and `grep '"tool_execution_end"' | grep -c '"toolName":"ctx_shell"'`
per item, which is the issue's 140 to 244 shell calls. What the calls *are* is the other half of the
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
its last line undelimited, so `read` returns non-zero and the final pair is never consulted. Measured: the
project's 18-pair value iterated 17 times, a 2-pair value once. The delta's contract — the block names *every*
check the pathspec owes — is false until that line ends with a newline, so the fix is part of this change and
`measure#namesTheOwedChecksInOneBlock` asserts the second pair (`tools-check`) by name.

**Owning capability and guideline section.** `agent-issue-loop` (the run renders the worker's task; the
requirement added below). The rule it acts on is the iteration protocol's — `guidelines/Build.md` §2,
"Agent iteration protocol", rule 3 ("batch independent questions") and rule 1 ("look, then measure") — which
states the discipline without stating the command a worker is given.

surface: 3 cases added, 1 case rewritten, 3 template placeholders added, 0 fixtures — `grep -c '^case_prompt_'
tools/loop-cases.sh` (10 on the base, 11 with the new case) and `grep -o '{{[A-Z_]*}}' tools/loop/prompts/worker.md
| sort | uniq -c` (`{{GATE_CMD}}` 3, `{{MEASURE_CMD}}` 0 → 3).
