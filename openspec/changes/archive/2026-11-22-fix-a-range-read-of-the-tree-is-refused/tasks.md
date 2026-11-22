# Tasks — a range read of the tree is refused, and a log's is not

- [x] 1. Add the case, its `LOOP_SHELL_CASES` row and its dispatch arm to `tools/loop/tests/loop-cases.sh`, and take
      the red proof on the run's base commit `abe49a0f`:
      `bash tools/loop/tests/loop-selftest.sh --case 'policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot'` — exit
      1, `a range read of a tracked file is refused (exit 0): ALLOWED: expected '3', got '0'`, whose first arm was read
      off the base module copied out with `git show` (2026-11-22T19:28:21Z, `tests=1 failures=1 errors=0`, transcribed
      beside the item's `red-on-HEAD.xml`).
- [x] 2. `tools/pi-shell-refusal.mjs`: `sed` in `REFUSED_VERBS`, `isRangePrint` for the quiet arm whose script is a
      line range, `refusesSegment` reading `sed`'s first operand as its script and allowing a `sed` that names no
      path, and `refusalReason`'s `sed` arm naming `ctx_read path=<path> offset=<N> limit=<M>` and its cap.
      Verification: `node --check tools/pi-shell-refusal.mjs`, and
      `policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot` green.
- [x] 3. The block in the three skeletons a child can be handed for work that writes the tree
      (`tools/loop/prompts/worker.md`, `worker-gate.md`, `worker-tail.md`): the verb list gains `` `sed` `` and the
      sentence that offered a range read states which arms stand instead. Verification:
      `prompt#thePhasePromptNamesWhatItsShellRefuses` green — red before this task, because the case reads the
      policy's export and fails on a verb the block does not name.
- [x] 4. `guidelines/BuildGate.md` §2a, the adapter's comment (`tools/pi-child-events.sh:68-70`) and the two
      `AGENTS.md` routing rows the paragraph's growth moves, all of which stated the exemption this change narrows.
      Verification: `tools/check-doc-values.sh`, `tools/check-doc-routes.sh` (green only after the rows state
      1.4–7.9 kB), `bash -n tools/pi-child-events.sh`.
- [x] 5. The structural checks this delta owes, none of its files compiled:
      `bash -n tools/loop/tests/loop-cases.sh`, `node --check tools/pi-shell-refusal.mjs`,
      `tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'`,
      `bash tools/loop/tests/loop-mutations.sh --check`, `tools/check-portability.sh`,
      `tools/check-todo-references.sh` and `tools/gen-feature-list.sh --check-classification` — all green in
      `evidence/structural.log`. The two mutations in the item scratch (`range-read-mutations.json`: `sed` out of the
      refused set, and every `sed` refused) each reddened an arm and restored green (`evidence/mutations.log`).
      `tools/check-openspec-hygiene.sh` is red in this tree for another reason — the 19 open change directories the
      prepared patch brought, none of them this one, carry no `x-issue:` — and it is not among the 19 commands
      `tools/loop/gate.sh --plan` names for this delta.
- [x] 6. This session — the item's continuation — completed the change, ran `tools/loop/evidence-check.sh` on it,
      archived it (`openspec archive --yes`) and ran `tools/loop/gate.sh` over that tree.
