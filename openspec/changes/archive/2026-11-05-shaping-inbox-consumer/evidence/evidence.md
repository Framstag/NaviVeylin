# Evidence — shaping-inbox-consumer (item #764)

Every run below is on the change root `/home/tim/projects/NaviVeylin/.pi/worktrees/loop`, base commit
`46f1889c53dc5eb0039a6793960f9671f4656b73`, offline, one invocation per suite.

## The gate

Run once by this item's first process at HEAD `98640a10` (`gate.log`), which carries the gate's own line. Its
per-suite tallies are in `gate-tallies/` — 33 XMLs whose `tests`, `failures` and `errors` attributes sum to the
numbers on that line. `tools_issue-tooling-selftest.sh.xml` carries `tests="158" failures="0" errors="0"`, so the
three cases this change adds are inside the green count; `gradlew_test_-PforceTests_-PjvmTestsOnly_--no-build-cache.xml`
carries `BUILD SUCCESSFUL in 4m 47s`. The tree touches no Gradle module — a skill, one guideline clause and three
tooling cases — so the coverage it owes is the tooling steps, all of them gate steps and all of them green.

This phase does not re-run the gate: the item is allowed one run, and a second run is a second item's budget.

## Red on HEAD

`red-on-HEAD.xml` — the three cases before `.pi/skills/reshape-issue/SKILL.md` existed:

```
<testsuite name="tools/issue-tooling-selftest.sh shaping#theConsumer*" tests="3" failures="3" errors="0" timestamp="2026-11-05T10:47:42Z">
  the inbox has no reader: .pi/skills/reshape-issue/SKILL.md does not exist
```

`tests="3" failures="3" errors="0"` — each case fails on the missing path, not on a missing assertion, which is the
falsification the change owes (`tasks.md` 3.2): the case file was added first, its run named the absent skill, then
the skill was written.

## Green after the skill

`issue-tooling-selftest.xml`: `tests="158" failures="0" errors="0"` at `2026-11-05T10:49:29Z`, system-out
`all 158 cases passed (JDK 27, offline, no token, HEAD 46f1889c, tools b13e7e05d445)`; the log beside it
(`issue-tooling-selftest.log`) is the same run's output. 9 `shaping#` cases existed before, 12 are listed after —
the three this change adds, and no existing case rewritten.

## The mutation

`mutation-stop-sentence.log` — the skill's stop sentence replaced with its opposite ("writes only after the owner
chooses"), one invocation:

```
FAIL shaping#theConsumerWritesNothingBeforeTheOwnerChooses
mutated-case exit=1
```

The case reads the sentence verbatim, so a sentence saying the wrong thing is red; the sentence was restored and
the suite is the green run above (`tasks.md` 3.3).

## The pointer and the counts

| check | result | artifact |
|---|---|---|
| `grep -n 'reshape-issue' guidelines/Backlog.md` | one line, `guidelines/Backlog.md:181`, beside `tools/issues/shaping.sh` | `tasks.md` 2.1 |
| `tools/check-doc-routes.sh` | `all 329 section references resolve, all 5 skill references resolve` | `doc-routes.txt` |
| `openspec validate shaping-inbox-consumer --strict` | `Change 'shaping-inbox-consumer' is valid` | `openspec-validate.txt` |
| `tools/issue-tooling-selftest.sh --list \| grep -Ec 'shaping#(theConsumer\|theApproved)'` | `3` | `tasks-3.1-verification.md` |

`tasks-3.1-verification.md` records why the `tasks.md` 3.1 check is the `(theConsumer|theApproved)` alternation and
not the `shaping#theConsumer` prefix: the third scenario's `**Case**:` is
`shaping#theApprovedReshapeLeavesThroughTheExistingWriters`, and a case a scenario does not name is a planning
defect, so the prefix was corrected rather than the case renamed.

## What the change cites

| claim | pointer |
|---|---|
| the three cases, in the array and in the dispatch table | `tools/issue-tooling-cases.sh:99`, `:100`, `:101` |
| the skill's text | `.pi/skills/reshape-issue/SKILL.md` |
| the consumer named beside the queue | `guidelines/Backlog.md:181` |
| the requirement and its three scenarios | `openspec/changes/shaping-inbox-consumer/specs/shaping-inbox/spec.md` |
| the writers the approved reshape leaves through | `tools/issues/create.sh`, `tools/issues/update.sh` |
