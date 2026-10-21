# Round-3 corrections — item #238, `fix-iteration-protocol-surface-step`

Review round 2 (`reviewer-report-2.md`) returned `VERDICT: FAIL`, `LOOP-BLOCKING: 1`,
`LOOP-CORRECTIONS-ONLY: yes`. This note records what was changed and the verification that postdates the last
edit. Nothing about the change's claim, its cases or its assertions was altered.

## The blocking finding

The gate (`00:51:54 → 01:05:33`) and the last green case run (`01:06:11`) predated the final write to
`guidelines/Build.md` (`mtime 01:07:07`) — the one production file of this change that a case asserts
(`prompt#iterationProtocolNamesTheChangeSurface`). The green therefore did not cover the bytes under review.

## Corrections applied

1. `guidelines/Build.md:132` — the 138-character line re-wrapped into the paragraph's 104-109 norm, and the
   sentence the review called grammatically broken restated in the same edit: "A plan made before this pass is
   the estimate that cost one change a factor of two" → "One change's plan made before this pass cost a factor
   of two". Paragraph lengths after the edit (`awk` over `guidelines/Build.md:121-134`): 100, 103, 105, 105,
   104, 107, 109, 108, 104, 105, 106, 106, 106, 40. The two substrings the case asserts (`count the surface`,
   `surface:`) are untouched, at `:128` and `:131`. The file's final mtime is `01:09:37`; the suite run below
   starts `01:09:48`, so every green artefact postdates the last write to this file.
2. `proposal.md` — the blast-radius count corrected from four files to five: the recorded `grep -rln` path set
   omitted `guidelines/`, i.e. the file the rule's text lives in. Command and output are now
   `tools/ .pi/skills/ AGENTS.md openspec/specs/ guidelines/` → `tools/loop-cases.sh`,
   `.pi/skills/run-tests/SKILL.md`, `AGENTS.md`, `openspec/specs/agent-issue-loop/spec.md`,
   `guidelines/Build.md`; the open change looked up by its own command is "a sixth hit", not "a fourth".
   `design.md`'s blast-radius block carried the same defective path set and is corrected identically.
3. `proposal.md` — the pre-fix pointer `tools/loop/prompts/worker.md:80` is now stated as the base-commit line,
   with `:88` named as the line in the tree after the fifth step (the review's NOTE).
4. `tasks.md` 5.4 and `proposal.md`'s gate paragraph — the gate run is stated with its real end (the log's
   mtime, `01:05:33`, since `gate.log` carries only the start), and the post-edit suite run is recorded
   separately (task 5.4, task 6.1, `suites-after-corrections.log`).

## Verification that postdates the last edit

The suite run was started after the `guidelines/Build.md` edit and before the change-artifact edits; the two
artifacts written afterwards (`tasks.md`, `proposal.md`, plus `design.md`'s path set) are records asserted by
no case, and `tools/check-doc-routes.sh` was re-run after them (below).

```
$ bash tools/loop-selftest.sh                       # whole suite, 2026-10-22T01:09:48+02:00 → 01:19:30+02:00
loop-selftest: all 150 cases passed (JDK 27, offline, no token, no sandbox)
EXIT=0                                              # evidence/suites-after-corrections.log
                                                    # tally evidence/tools_loop-selftest.sh.xml tests="150" failures="0"

$ bash tools/check-doc-routes.sh                    # 01:19:31+02:00
check-doc-routes: all 120 section references resolve, all 5 skill references resolve
EXIT=0                                              # evidence/suites-after-corrections.log
                                                    # tally evidence/tools_check-doc-routes.sh.xml

$ bash tools/loop-selftest.sh --case 'prompt#workerNamesTheChangeSurfaceBeforeThePlan'      # 01:20:00+02:00
PASS prompt#workerNamesTheChangeSurfaceBeforeThePlan
EXIT=0
$ bash tools/loop-selftest.sh --case 'prompt#iterationProtocolNamesTheChangeSurface'        # 01:20:11+02:00
PASS prompt#iterationProtocolNamesTheChangeSurface
EXIT=0                                              # evidence/green-after-corrections.log

$ bash tools/check-doc-routes.sh                    # re-run after the last artifact edit (below, same file)
check-doc-routes: all 120 section references resolve, all 5 skill references resolve
EXIT=0                                              # evidence/doc-routes-after-corrections.log, its header
                                                    # and its `ended` line carry the run's clock times
```

The two suites are exactly the ones `gate-plan.log` names for this delta (`tools/loop-selftest.sh`,
`tools/check-doc-routes.sh`), so the delta's own roster owes no Gradle task; the gate's `*.xml` compiled pattern
pulls the Gradle leg in once evidence tallies sit in the tree, which is the gate's mapping and not a compiled
path of this change. The full gate was **not** run a
second time: it is run once per item, its `00:51:54 → 01:05:33` record stands as that run, and a second
attempt is what the run's own rule forbids. The mutation `38.1` was not re-run either: it targets
`tools/loop/prompts/worker.md`, whose last write (`01:06:32`, the restore) precedes its log
(`01:06:40`), and no later edit touches that file.

## Scope

Files touched by this round: `guidelines/Build.md` (one paragraph, three lines), and the archived change's
`proposal.md`, `design.md`, `tasks.md` and `evidence/`. No compiled path, no case, no mutation, no spec
requirement changed.
