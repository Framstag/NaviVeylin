# Tasks — a cut at the worker's own turn bound is held too-wide

- [x] 1. Write the red proof on the base commit: extend `run#aBoundEndedItemRecordsItsBound`
      (`tools/loop/tests/loop-cases.sh`) with the label assertions — the item carries `agent/needs-shaping` and
      `status/on-hold`, keeps no `agent/needs-decision`, gets exactly one comment whose first line is the verdict's
      meaning, its note says it was held, and the note does not also call the attempt the next run's to resume —
      and run it against `d964b224`'s `run.sh`:
      `1 of 1 cases failed`, `'--add-label agent/needs-shaping' not found in: gh issue edit 21 --add-label
      agent/in-progress gh issue edit 21 --remove-label agent/in-progress`, 2026-11-23T09:41:36Z, kept as
      `evidence/red-on-base.log`.
- [x] 2. Read `bound_key` in the `cut)` arm of `tools/loop/run.sh` and, when the key is
      `LOOP_CHILD_TURNS_MAX_WORKER` with `stop_reason` `bound`, write `loop_labels too-wide "$issue"` after the plain
      `unpick` — reason and evidence carried into the one comment — and give the note the reason *in place of* the
      "left for the next run" sentence, which the arm writes for every other bound: the two are exclusive, because a
      note carrying both is the state this finding exists to replace. Verified by task 1's case and by
      `run#childPastItsTurnBoundIsCut`
      (`evidence/green-cases.log`, both `PASS`).
- [x] 3. Realign `run#childPastItsTurnBoundIsCut`, which asserted the absence of the hold for this same arm: the note
      now says the item is held as too-wide, the shaping label is asserted in place of a decision label, and its
      "no comment" assertion becomes the hold's one comment. Same log as task 2.
- [x] 4. State the carve-out in the owning guideline section, `guidelines/Backlog.recipes.md`'s *Leftovers, the report
      and the queue*, where the paragraph
      called every cut the item's own worker took "a phase, not a finding": the measurements of #1021 and #984, the
      `too-wide` write, and the other bounds that keep the phase reading. Verified with `tools/check-doc-values.sh`
      and `tools/check-doc-routes.sh` inside the gate run of task 5 (both green).
- [x] 5. The whole gate on the change root, `./tools/loop/gate.sh --base d964b224672de49210e662b62dd35791e4cf1f18`:
      `LOOP-GATE: tests=991 failures=0 errors=0`, 2026-11-23T10:04:10Z, kept as `evidence/gate.log`. Its `base-red`
      line names the two quota cases (`run#theQuotaSwitchIsExplicit`, `run#aQuotaHoldPausesTheRunAndResumesIt`), which
      fail on the base commit for want of a pre-flight and are not this change's; the mutation set's `--check` and
      the file-declaration check are green, so no case's `# files:` row moved.
- [x] 6. Structure of the change: `openspec/changes/fix-a-worker-bound-cut-is-held-too-wide/` carries `proposal.md`,
      this file, `design.md` with its `## Open decisions` resolved, the delta under
      `specs/agent-issue-loop/spec.md` (`## MODIFIED Requirements`, the requirement `A block records the mechanism
      that stopped it` re-emitted whole, its scenario's THEN carved for the worker's own turn bound and its case
      left as `run#childPastItsTurnBoundIsCut`), and `.openspec.yaml` with `x-issue: 1039`.
