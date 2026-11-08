# Proposal — a gate record names the tree it measured, so a gate cannot be claimed over a tree it never saw

## Why

**The entry, verified in the tree (issue #819).** The gate's per-run record names the child and the arguments and
nothing else: `tools/loop/gate.sh:290` opens the record (`runs_record="$LOOP_CHILD_EVENTS.gate-runs"`) and
`tools/loop/gate.sh:304` writes one line per full run, `$session_key\t$gate_argv` — the identity this change adds is
that line's third field. Neither of the base's fields identifies the tree that was measured — no commit, no digest of
the diff — so a reviewer answering "was this tree gated?" reads timestamps, and the only case that asserts the record's
content asserts its line count (`tools/loop-cases.sh:12615`, `grep -c '' "$events.gate-runs"`). Not already fixed:
`./tools/look.sh 'LOOP-GATE-TREE'` over the base tree finds it in no engine file, prompt, case or guideline.

**The measurement the item carries, re-verified here (2026-11-08, `evidence/archive-stamps.log`).**
`openspec/changes/archive/2026-10-22-fix-staging-leftover-discovered-as-map/tasks.md` §5.6 (line 92) states "The run's
gate re-run once over the corrected tree → `GATE-RC=0`, `LOOP-GATE: tests=481 failures=0`" — that tally is not retained
as one JUnit XML — and its line continues "→ `evidence/gate/` (replaced with the round-2 run's tallies and XMLs)". The XMLs under that archive's `evidence/gate/` are stamped
`2026-10-22T12:23:41.923Z` through `2026-10-22T12:26:30.823Z`, while the round-2 runs the same `tasks.md` records are
stamped `2026-10-22T12:42:56.420Z` (`evidence/revert-check-round2/`) and `2026-10-22T12:43:06.048Z`
(`evidence/green-round2/`). `evidence/gate/totals.txt` still reads "the run's gate, taken once". So the corrected tree
was never gated by the run that archived it, and the claim stood in an archived, reviewed, merged change: nothing in
the record could say so, because the record names no tree.

**Root cause, one sentence.** The gate's record line carries the child's session and the arguments it ran with and
nothing that identifies the tree it measured, so a run over a tree and a run over that tree corrected afterwards
leave the same record.

**The red tests, on the branch's base (`ba96953f6d43df8ac5926c23697a75294cc10af2`).**
`gate#aThirdFullRunInOneSessionIsRefused` (extended with the record's own tree assertions) fails because the line the
gate writes carries no identity — `'LOOP-GATE-TREE: head=' not found`, `loop-selftest: 1 of 1 cases failed`
(`evidence/red-on-HEAD-aThirdFullRunInOneSessionIsRefused.log`, 2026-11-08T07:19:09Z). `run#aReviewDiffNamesTheTreeItWasWrittenFrom`
(new) fails because no identity is written beside the diff the round is handed — `the diff under review was handed no
tree identity` (`evidence/red-on-HEAD-aReviewDiffNamesTheTreeItWasWrittenFrom.log`, 2026-11-08T07:19:09Z).
`gate#theGateAndTheRoundNameOneTree` (new) fails because the gate names no tree at all — `the gate names the tree it
measured` (`evidence/red-on-HEAD-theGateAndTheRoundNameOneTree.log`, 2026-11-08T07:19:09Z). Each is one case:
`tests=1 failures=1` — each red run's printed tally, not retained as one JUnit XML (the prints are the three
`evidence/red-on-HEAD-*.log` files named above).

**The owning requirement and guideline section.** Capability `agent-issue-loop`: "A child pays for the full gate at
most the recorded number of times" (`openspec/specs/agent-issue-loop/spec.md:3742`) owns the record, and "A review's
task states what the round may run, and quotes the gate instead of running it" (`:1562`) owns item 3, which is what
reads it. The rule the record serves is `guidelines/Backlog.recipes.md`:567 "The gate and its suite" (the
record's paragraph is `:599`).

**The item's first step, answered and not guessed.** The item asks whether §5.6's claim is repaired by re-running the
gate over the corrected tree or by deleting the sentence. Neither: an archived record is not rewritten — the rule is
`openspec/specs/documentation-ownership/spec.md:735`, "An archived record SHALL NOT be inspected or rewritten, because
it is history rather than a defect in the tree". The archive stays as it is, and `## Open decisions` records this with
the line it rests on.

**Surface, counted before the plan was fixed** — `bash tools/loop-selftest.sh --list | wc -l` → `373` (**2 cases
added**, 371 before, `evidence/case-count.log`), the same for `gate#` → `30` (**1 case rewritten**, the one that owns
the record, and 1 added), and `./tools/look.sh 'gate-runs'` names the files that hold the rule's text and its symbol —
`tools/loop/gate.sh`, `tools/loop-cases.sh`, `tools/loop/loop.conf` and `guidelines/Backlog.recipes.md` among them
(`evidence/surface-counts.log`); the case list, not the first file the plan names, is what the estimate is set
against.

surface: 2 cases added, 1 rewritten, 0 fixtures realigned — bash tools/loop-selftest.sh --list | wc -l (373, 371 before); ./tools/look.sh 'gate-runs' (gate.sh, loop-cases.sh, loop.conf and the guideline among the files that hold it)

## What changes

1. `tools/loop/lib.sh` gains `loop_tree_identity <tree> <base> [<pathspec>]` — the commit and a digest of the change
   itself, over every path the base names plus every path git does not track, each with the hash of its worktree
   content — and `loop_change_identity <tree> <base>`, the one derivation both ends call, which takes its path set
   from the profile (`LOOP_REVIEW_EXCLUDE_PATHSPEC`) instead of from a caller. The digest is commit-invariant on
   purpose: the change child measures an uncommitted tree and the run commits it before any reviewer reads the diff,
   so a digest of `git diff` or of `HEAD` would make the ordinary flow read as a corrected one; and it leaves out the
   generated trees the round's diff leaves out, because the gate's own evidence lands in the change directory between
   the gate's moment and the run's (this change's round-1 review, `evidence/review-round-1-requested.md`).
2. `tools/loop/gate.sh` writes that identity into the run's own record (a third field of `<events>.gate-runs`), names
   it on its console as `LOOP-GATE-TREE: head=<sha> diff=<digest>` before the first step, and names each earlier run's
   tree beside its command in a refusal.
3. `tools/loop/run.sh` writes the same identity beside the diff it hands a round (`<diff>.identity`), and
   `tools/loop/prompts/reviewer.md` item 3 compares the two: a gate whose digest is not the diff's gated a tree this
   round is not reading.
4. `guidelines/Backlog.recipes.md`:567 states the rule, the observation that would falsify it and the measurement
   behind it; `tools/loop-cases.sh` carries the three cases above and `tools/loop-mutations.sh` four records
   (`262.1`–`262.4`) for the new invariants.

## What this does not change

The gate's steps, clocks, tallies, exit codes `0`/`1`/`2`/`3`, and what the record counts. The identity adds a field
to a line that already exists and one file beside each diff; it is not a new record of its own, and no evidence tree,
archive or existing record is rewritten.
