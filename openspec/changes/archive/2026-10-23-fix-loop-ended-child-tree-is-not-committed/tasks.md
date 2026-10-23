# Tasks — the run stops committing the tree of a child it ended

## 1. The red cases

- [x] `tools/loop-cases.sh`: the case `run#treeOfAChildTheCapEndedIsNotCommitted` (registered in
  `LOOP_SHELL_CASES` and in the dispatch), a stub child that writes `half-applied.txt` into the change root and
  then blocks on a fifo, so the 2 s cap ends it with the file in the tree. Its assertions are the shipped scenario's:
  the branch is at the base, the tree still carries the file, the item is `blocked` with its `LOOP-TIMEOUT`
  transcript, **and the run's own line says the commit was skipped** — that last one was missing until the round-1
  review read the scenario against the case and found `out=$(cat "$d/out")` captured and never asserted.
  Verify: `bash tools/loop-selftest.sh --case 'run#treeOfAChildTheCapEndedIsNotCommitted'` — red on HEAD, for the
  reason the case names; the transcript and the tally are `evidence/red-HEAD.log` and `evidence/red-HEAD.xml`
  (`tests="1" failures="1"`).
- [x] `tools/loop-cases.sh`: the case `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`, the same rule at
  the second commit site: round 1's reviewer refuses, the rework worker writes `half-applied.txt` into the change
  root and then `sleep 30`s past the 2 s cap (it cannot use `PI_STUB_WAIT` — that blocks every child, so the round's
  reviewer would block first). Asserted: no commit on the branch names the file, the tree still carries it, the item
  is `blocked`, and the blocked reason names the tree it did not commit.
  Verify: `PASS` on the tree this change leaves; its red proof is task 3.2 (mutation `45.2` restores the
  unconditional commit and the case goes red), because the pre-change line there is a single call and the mutation
  restores it byte for byte.

## 2. The fix — one guard per call site

- [x] `tools/loop/run.sh`: read the child's status before the commit; when the run ended the child
  (`worker_exit` is `LOOP_CHILD_TIMEOUT` or the transcript carries `LOOP-TIMEOUT`), `end-item` is not called, and
  `head_now` is read either way.
  Verify: the first case goes green (`bash tools/loop-selftest.sh --case …`, `PASS`).
- [x] `tools/loop/run.sh`: the same guard around the rework round's commit — `round2_exit` is read, the round's
  `LOOP-TIMEOUT` is read, and a capped rework worker blocks the item (`review_verdict=round2-ended`,
  `outcome=blocked`) instead of having its tree committed. The behaviour this settles, which the item itself left
  open: a capped rework round goes to the item's owner, not to another round — a run that ended a child has no claim
  to make about it. Found by this change's round-1 review, which read the shipped universal requirement against this
  call site and refused the change for the disagreement.
  Verify: the second case goes green (`PASS`), and `run#aCorrectiveRefusalEarnsOneMoreRound`,
  `run#aCorrectiveRoundIsHandedTheCorrections` and `run#aBlockingRefusalStillTakesTheRoundTwoPath` still pass in the
  gate's full self-test run.

## 3. Each invariant's own falsification

- [x] `tools/loop-mutations.sh`: record `45.1` restores the unconditional commit for the item's worker and names
  its case. Verify: `bash tools/loop-mutations.sh --only 45.1` — `RED` for
  `run#treeOfAChildTheCapEndedIsNotCommitted`, an exact restore, and the case green again
  (`evidence/revert-check-45.1.log`).
- [x] `tools/loop-mutations.sh`: record `45.2` restores the unconditional commit for the rework round and names its
  case. Verify: `bash tools/loop-mutations.sh --only 45.2` — `RED` for
  `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`, an exact restore, and the case green again
  (`evidence/revert-check-45.2.log`). Both logs carry the moment they were taken, on this branch, and `selected 1 of
  113 records` — the set the rebased tree declares.
- [x] The declared set applies as a whole: `bash tools/loop-mutations.sh --check` → `all 113 cases passed — every
  selected record applies to the file it names`.

## 4. The guideline

- [x] `guidelines/Backlog.md`, section "7. The loop": the cap's paragraph gains what the ended child's tree does not
  get, and the sentence about a run-ended child as a blocked item says it carries no commit.
  Verify: `tools/check-doc-routes.sh` (the gate's own check for a `*.md` path), plus the structural read that the
  paragraph still sits under `## 7. The loop`.

## 5. The spec

- [x] `agent-issue-loop`: one ADDED requirement, "A child the run ended itself leaves no commit", with one scenario
  per call site (`run#treeOfAChildTheCapEndedIsNotCommitted`,
  `run#treeOfACapEndedReworkOfARefusingRoundIsNotCommitted`), and a body of 471 characters — inside the validator's
  500-character limit.
  Verify: `openspec validate … --strict` before the archive; the delta is merged into
  `openspec/specs/agent-issue-loop/spec.md` and carries the second scenario, which was added when the second call
  site was guarded (the round-1 review's blocking finding).

## 6. The gate, once

- [x] `./tools/loop/gate.sh --base main` from the change root, on the archived tree. No compiled file changed, so
  the profile owes the three shell suites (`tools/loop-selftest.sh`, `bash tools/loop-mutations.sh --check`,
  `tools/loop-mutations-selftest.sh`) and the two documentation checks (`bash tools/gen-feature-list.sh
  --check-classification`, `tools/check-doc-routes.sh`) — five checks, which is what the tally names. The first run
  of the change was red on a realignment it owed (record `45.1` was declared outside `MUTATIONS=( … )`, and two
  checks derive the set from that array's text); it is kept as `evidence/gate-first-red.log` and attributed in
  `evidence/gate-attribution.md`.
  Verify: `evidence/gate.log` and its tallies in `evidence/gate-tallies/` — `tests="192" failures="0"`
  (`tools/loop-selftest.sh`, `all 192 cases passed`), `tests="113" failures="0"` (`loop-mutations --check`, `all
  113 cases passed`), `tests="34" failures="0"` (`loop-mutations-selftest`, `all 34 cases passed`), and the two
  documentation checks tallied `tests="0" failures="0"` with `exit=0`. The gate's own summary line is
  `LOOP-GATE: tests=339 failures=0 errors=0` — the gate's sum, not retained as a tally because no single XML
  carries it.
- [x] Two earlier gate records are superseded rather than deleted. The attempt of 2026-10-23T09 ended at the run's
  cap while the gate was still running, so the `gate.log` it left held two lines and no verdict — the very defect
  this change is about, on this change; that file was replaced by the run above. The run made after the rebase and
  before the archive owed four checks (`tests=337`), because `openspec/changes/*/` is gitignored and the change
  directory was then untracked — the archived record is a tracked path, and the run above owes the two
  documentation checks with it. The first-red aggregate `tests=230 failures=6` is not retained as a tally of its
  own: only the two failing suites' tallies were copied, and they are (`evidence/gate-first-red/`).

## 7. Evidence and archive

- [x] Every cited XML copied into the change's `evidence/`.
- [x] `bash .pi/skills/fix-loop/scripts/evidence-check.sh <the change> --root .` — `evidence-check: PASS`, kept as
  `evidence/evidence-check.log`. It first failed C2 on two quoted aggregates — `tests=230 failures=6` (not retained)
  and `tests=339 failures=0` (not retained) — because no single XML carries a gate's sum: the records now keep the sum
  and that note on one line, beside the per-suite tallies that are retained, and the run's tallies travel in
  `evidence/gate-tallies/`.
- [x] `openspec archive fix-loop-ended-child-tree-is-not-committed --yes` → `openspec/changes/archive/2026-10-23-fix-loop-ended-child-tree-is-not-committed`, the delta merged (`+ 1 added`), then `bash tools/gen-feature-list.sh
  --check-classification` and `tools/check-doc-routes.sh` run again on the archived tree —
  `evidence/structural-checks-post-archive.log`, both `exit=0`.
  Note the pre-existing condition that run shows: `gen-feature-list` names three stale classification entries for
  specs that no longer exist (`search-name-matching`, `search-result-row-icon`, `start-map-selection`) and exits 0.
  Those are the backlog's, not this change's, and this change neither adds nor removes one.

## 8. The round-1 review, and what it changed

- [x] One read-only reviewer round on the archived change. Verdict `FAIL` with four blocking findings; each is
  answered above: the unasserted "the run's own line says the commit was skipped" (task 1), the five-check surface
  `tasks.md` claimed against a four-check tally (task 6), the universal requirement contradicted by the rework
  round's commit (task 2 and task 5), and the stale pointers (`run.sh:900/948/709`, quoted failure strings no
  artifact carries, a pre-rebase revert-check) corrected across `proposal.md`, `design.md` and this file.
