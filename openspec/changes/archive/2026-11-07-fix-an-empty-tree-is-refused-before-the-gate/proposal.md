# An empty tree is refused at the diff, before the child that would run the gate

## Why

Issue **#797**. Every arm that decides what became of an item is read in **one** block, after the item's children have
all run: `tools/loop/run.sh:2360` reads

```
  elif [ "$head_now" = "$base" ]; then
    outcome="blocked"; blocked_reason="reported success but the tree is still at the base commit — nothing was committed"
```

and it is the *last* reading the run takes. The archive shows what that costs. Eight records over seven issues carry
that note, with the wall time the run already held when the reading arrived:

```
$ jq -r '.items[]? | select((.note // "") | test("still at the base commit")) | "\(.issue) \(.metrics.wallSeconds) \(.at)"' .pi/issue-loop/runs/*.json | sort -u
91 83.255 2026-10-20T14:59:04Z
91 70.221 2026-10-21T20:42:25Z
131 182.895 2026-10-29T11:33:58Z
260 1915.330 2026-10-23T11:38:54Z
319 1422.327 2026-10-27T02:41:55Z
721 916.347 2026-11-03T13:27:42Z
739 17.762 2026-11-01T23:32:02Z
740 19.210 2026-11-01T23:31:30Z
```

**Root cause, in one sentence:** the reading that needs no child at all — the item's HEAD against the run's base — was
taken last, in the outcome block, so it arrived after the child that runs the gate and after the tail, and the
worktree's own state was already decidable at the moment the change child answered.

**Verified in the tree today.** The reading exists at exactly one site (`tools/look.sh 'reported success but the tree
is still at the base commit'` → 16 matches in 11 files: `tools/loop/run.sh:2361` is the arm, `:2264` the comment that
records issue #742, one paragraph in the guideline and 13 records in archived changes). The shape the item names is the
one the run works in by default — `LOOP_PRE_GATE_REVIEW_DEFAULT=on` (`tools/loop/loop.conf:44`): the change child stops
at the diff (`LOOP-DIFF-READY`), the run commits its tree and starts a reviewer and a gate child of its own. Nothing in
that block reads the commit it just made before it starts them: `tools/loop/run.sh:2066`-`:2080` commits, prints the
HEAD it found, and guards the reviewer and the gate child on `[ "$head_now" != "$base" ]` — while the tail, which
follows the gate, is guarded on neither, so a tree at the base still pays for one more child
(`tools/loop/run.sh:2188`).

**Root cause of the symptom as it is met today, not only on the archive.** With the gate deferred, a child that
answers the diff marker and commits nothing does not even reach the arm above: the tail runs, its report carries no
verdict, and the run reads its own **"the child could not start"** stop instead (`tools/loop/run.sh:2280`) — no item is
recorded, nothing is escalated, and the run stops. That is the shape the red run below prints, from the same mechanism:
the item's own state was decidable and was read last.

**The red case, on the change root's HEAD `ec6a84a`** — `run#anEmptyTreeIsRefusedBeforeTheGateChild`, one
`bash tools/loop-selftest.sh --case 'run#anEmptyTreeIsRefusedBeforeTheGateChild'` run at 2026-11-06T23:48:16Z:

```
run#anEmptyTreeIsRefusedBeforeTheGateChild|   the item is recorded — the run said: stop: the child could not start — the worker exited 0 with an answer of 188 B and no verdict line, leaving the tree at the base: LOOP-RED-TEST: ... : expected '1', got '0'
--- run#anEmptyTreeIsRefusedBeforeTheGateChild[0] (exit 1)
FAIL run#anEmptyTreeIsRefusedBeforeTheGateChild
loop-selftest: 1 of 1 cases failed
```

`evidence/red-on-HEAD.log` is that run; `evidence/red-on-HEAD.xml` is its tally (`tests=1 failures=1 errors=0`), kept
beside it because the shell runner writes no JUnit XML of its own. The case's first-line `# files:` declaration is
accepted before that run: `bash tools/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` → `PASS`.

**Owning requirement:** `openspec/specs/agent-issue-loop/spec.md`, "The run verifies rather than trusts" (`:544`),
whose first scenario is this exact claim — "A claim without a commit is blocked" — and whose body is what the run reads
the item from. The delta adds the scenario that fixes *when* that claim is read and names the case of each scenario the
requirement already carries (`:549`–`:563`).

**Owning guideline section:** `guidelines/Backlog.recipes.md`, the section "Refusals, records and the landing", the paragraph
that owns "what it does with a child that never started" (`:645`–`:659`) — the same tree, the same reading, and the
paragraph that says the status is not the test.

surface: 1 case added, 0 rewritten, 0 fixtures realigned, 4 engine sites in one file (`run.sh`: the reading's
initialisation, the reading at the diff, the tail's guard, the never-started arm's guard), 1 requirement modified (one
added scenario, three existing scenarios given the case each already has), 1 guideline section, 1 record of
`tools/loop-mutations.sh` (91.1) — counted with `bash tools/loop-selftest.sh --list | wc -l` (344 on the base, 345 on
this change), `grep -c '^#### Scenario' openspec/specs/agent-issue-loop/spec.md` (346),
`tools/look.sh 'reported success but the tree is still at the base commit'` (16 matches in 11 files) and
`tools/look.sh 'LOOP-DIFF-READY'` (50 matches in 20 files: 3 in `run.sh`, the split the reading is taken in).

## What Changes

1. **The reading is taken where the commit was just made.** `tools/loop/run.sh`, in the deferred-gate block: after
   `end-item` and the HEAD it printed, a HEAD still equal to the base records the reading as the item's own step
   (`empty-tree`) and the item ends there.
2. **Nothing follows that reading.** The tail is guarded on it (`run.sh:2188`), so no child is started to archive a tree
   that carries nothing — and the reviewer and the gate child are already guarded on the same HEAD.
3. **The item's block, not the run's stop.** The never-started arm (`run.sh:2271`, `stop_run` at `:2280`) is guarded
   on the reading too: a
   child that answered the diff marker *did* read the item, so its empty tree is the item's `blocked` record with the
   reason the outcome block already writes — `reported success but the tree is still at the base commit — nothing was
   committed` — and not a stop that leaves the item as its owner set it.
4. **One case, one mutation, one paragraph.** `run#anEmptyTreeIsRefusedBeforeTheGateChild` asserts the block, the
   reason, that the change child is the only child started, the `empty-tree` step, that no round, gate child or tail is
   started, and that the answer is not read as "could not start"; `tools/loop-mutations.sh` 91.1 mutates the reading to
   `if false`; that section's paragraph says where the reading is taken and what it leaves.

## What this does not do

- It does not move the outcome block's arm: that arm keeps its place as the invariant, and it is still the only place
  the reason is written. The change moves only the *point the reading is taken at*.
- It does not change the one-child shape (`--review off`, or a change child that ran the gate itself): there the child
  runs the gate inside its own session, so there is no point before the gate that the run controls.
- It does not commit anything earlier than the run already does, and it does not read the worktree's dirty state: a
  change is the commit the run makes, and `end-item` is the run's own statement about it.
- It does not touch the never-started arm for an answer with nothing in it (#742, `run#aChildThatExitsZeroWithAnEmptyAnswerIsTheRunsFault`):
  a 0-byte answer still leaves no claim and still stops the run.
