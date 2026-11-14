# A red gate records which tree fails the case it names

## Why

Issue **#853**. A nonzero `failures=`/`errors=` in the gate's tally takes the blocked path (`tools/loop/run.sh:3059`,
the arm #811 landed) and the record it writes says only what the gate reported: the tally's prose and its numbers,
and nothing about the tree that made the case fail. `guidelines/BuildEvidence.md` §4d, "Attributing a red run", is the
project's own rule for the question the record leaves open — a case that also fails at the base commit is not a
finding about the delta, and an unattributed red is not a verdict. The run holds both halves of the discrimination
(the tally and the base commit the item descends from) and writes neither beyond the prose claim.

The measured instance is the run of `2026-11-11T11:37:08Z` (`jq` over `.pi/issue-loop/runs/2026-11-11T11:37:08Z.json`):

    { "issue": 849, "result": "blocked", "stopReason": null,
      "gate": "tests=1016 failures=1 errors=0 (…; failing: loop-selftest --from-git db1971d1, case worktree#keepsSourcesNotBuildOutput)",  # not retained as one JUnit XML — no `tests="1016"` XML exists today; the run's build tree was overwritten
      "waived": null, "wall": 2714.012,
      "tokens": { "cacheRead": 17502080, "cacheWrite": 0, "input": 257257, "output": 122668 } }

Item #849 had a demonstrated red on HEAD (`run#theGateColumnNamesThisItemsGateResult`, `XML tests=1 failures=1`), its
own cases ran green, and the run could still not say whether `worktree#keepsSourcesNotBuildOutput` was the delta's or
the base's: 2 714 s and 17 872 005 tokens spent to produce a record a person has to re-diagnose by hand.

**Root cause, in one sentence.** The run's red-gate arm reads the tally as a single verdict bit and never asks the
second question the guideline requires — whether that case also fails at the base commit — so a pre-existing red and
the delta's own red are recorded identically and the only verdict the run can write is `blocked`.

**The red cases.** `run#aRedGateIsAttributedToTheTreeThatFailsIt` (`tools/loop/tests/loop-cases.sh`) drives three runs
whose gate is red and whose gate log names one case: the case the item's own `LOOP-RED-TEST` names (`delta`, no base
tree paid), a case it does not name whose rerun fails in a detached tree at the run's base commit (`base`), and a red
tally with no readable gate log (`unattributable`). `gate#aRedGateWritesTheLogItIsAttributedFrom` runs `tools/loop/gate.sh`
itself in a throwaway project and asserts the log it now writes beside its tallies — the file the first case's runs
read. On HEAD all three records carry `metrics.gateAttribution: null` and there is no field for it
(`tools/look.sh 'gateAttribution'` → 0 matches in 0 files), and no gate writes that log (`LOOP-GATE-LOG` → 0 matches).

**The repair the first gate forced.** The self-test that mutates a *copy* of the engine went red on the delta's own
records: `attribute_red_gate` reads the gate-log reader as a sibling of the engine
(`$LOOP_DIR/../gate-attribute.sh`, the reach `tools/loop/lib.sh:45` already makes for `$LOOP_DIR/../issues`), and that copy
carried `tools/loop` with `tools/issues` beside it and nothing else — so the tool was missing, the block came back
empty, and the pristine copy could only answer `unattributable` (`RESTORE-FAILED 853.2
run#aRedGateIsAttributedToTheTreeThatFailsIt stayed red after the restore`). The copy now carries the second sibling,
`--engine` refuses one without it, and the self-test asserts that refusal (its 36th guard). No production file changes;
the step reads `all 36 cases passed` where the failing gate's tally read `tests="34" failures="2"`.

**The owning capability** is `agent-issue-loop`; the requirement it extends is `openspec/specs/agent-issue-loop/spec.md`
"A close over a gate that did not pass records its waiver" (the red gate's record), and the owning guideline section is
`guidelines/BuildEvidence.md:106` §4d "Attributing a red run".

**Surface**, counted before the plan was fixed (`tools/look.sh '<pattern>'`, 2026-11-13): `gateAttribution` 0 matches
in 0 files (nothing to rewrite), `gateWaived` 33 in 14 files (the field this one stands beside), `gate_tally_state` 19
in 14 files (the predicate the arm reads), `gate-attribute` 36 in 17 files (the one derivation of the rerun command),
`red_test` 18 in 5 files (the change's own case names, read out of the record).

surface: 2 cases added, 1 rewritten, 0 fixtures added — `tools/look.sh 'gateAttribution'` → 0 matches in 0 files at
the base; `tools/look.sh 'gateWaived'` → 33 matches in 14 files

The repair after the first gate adds no case to `tools/loop/tests/loop-cases.sh`: it adds one guard to the mutation
harness's self-test and the second sibling a copied engine is given (`tools/gate-attribute.sh`).
