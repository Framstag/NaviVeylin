# Tasks — a child is told what its own shell and tools refuse

No task compiles anything: the change touches three prompt skeletons and one case file, no source file of the app,
the core or the engine. The structural check of the files it edits is the case that renders them.

- [x] **1. Verify the claim against the tree.** `tools/loop/prompts/worker.md:39-56` on the base commit routes the
  child to the document map, the section-list call, the capabilities and the change artifacts and names no refusal
  but the shell search's; `worker-gate.md:31-41` and `worker-tail.md:32-42` carry one bullet less. Verified by reading
  the three sections once, and by `./tools/look.sh 'pi-shell-refusal' --files`, which finds the pointer in
  `worker.md` alone.

- [x] **2. Write the red case.** `prompt#thePhasePromptNamesWhatItsShellRefuses` in `tools/loop-cases.sh`: it renders
  the worker prompt through a stub run, asserts the block and its four families are in it, asserts each of the three
  skeletons states the block's heading, and reads `REFUSED_VERBS` out of `tools/pi-shell-refusal.mjs` to require
  every verb in the rendered prompt. Verification: `bash tools/loop-selftest.sh --case prompt#thePhasePromptNamesWhatItsShellRefuses`
  on the base commit, red — `evidence/red-on-HEAD.log`, tally `evidence/red-on-HEAD.xml` (`tests=1 failures=1`).

- [x] **3. Declare what the case can be reached by.** The first line of the body names
  `tools/loop/prompts/ tools/pi-shell-refusal.mjs tools/look.sh tools/loop/run.sh tools/pi-child-events.sh
  tools/tooling.json`. Verification: `bash tools/loop-selftest.sh --case driver#everyCaseDeclaresTheFilesItCanReach`,
  which named the missing `tools/look.sh` before it was declared and passes after.

- [x] **4. Put the block in the three skeletons.** `## What your own shell and tools refuse` in `worker.md`,
  `worker-gate.md` and `worker-tail.md`, and `worker.md`'s existing clause points at the block instead of naming five
  of the policy's eight verbs. Verification: the same case, and `prompt#thePhasePromptsCarryTheirOwnRoute`, which
  resolves every repository path a skeleton names — green, `evidence/green-on-change.log`. The case asserts the
  heading in each of the three, not that the three blocks are byte-identical; the block's own text is asserted once,
  against the rendered `worker.md` prompt.

- [x] **5. Keep the prompt's list beside the policy's.** The case reads the list from the module rather than from a
  second copy, so the two cannot drift. Verification: task 2's case is the assertion; a verb added to
  `REFUSED_VERBS` without a prompt that names it fails it.

- [ ] **6. The gate.** `./tools/loop/gate.sh` from the change root, run once — by the gate child this run defers it
  to, not by the worker. Verification: its plan's first line, then the gate's own block. Ran once:
  `tests=868 failures=0 errors=1`, the error being the compiled step's pre-existing paging flake in a class this
  change does not touch (`SearchHistoryScreenTest#thePageRowOpensTheNextSliceOfTheHistory`), attributed in
  `evidence/gate-attribution.md`; this change's own case is green in that run (`evidence/gate.log:623`). The box
  stays unchecked because the gate did not pass.
