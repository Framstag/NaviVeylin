# Tasks — the prompt every turn re-sends is smaller and bounded

No compiled file is touched: the delta is two prompt skeletons, the loop scripts that render and record them, their
three tables, the case and mutation files, one guideline section and one requirement already in the shipped spec. The
structural checks of the files edited stand in for a build, and the focused selections stand in for the suite, which
the parent runs once over the branch (`guidelines/Build.md` §2, the scope block).

## The case, first — red on the base before any production change

- [x] `tools/loop/tests/loop-cases.sh`: the case `run#theWorkerPromptFitsItsBudget` — its body, its row in
  `LOOP_SHELL_CASES` and its dispatch arm — beside `run#reviewBudgetIsARecordedValue`, the recorded-value pattern it
  follows, with the `# files:` declaration its first line names (`tools/loop/prompts/`, `loop.conf`, `run.sh`,
  `child.sh`, `state.sh`, `report.sh`, `events.tsv`, the fixtures and the adapter).
  *Verified:* `driver#everyCaseDeclaresTheFilesItCanReach` prints `PASS` (`evidence/green.log`) — the case that refuses
  a declaration narrower than the body.
- [x] The case reads the bound **out of `loop.conf`** rather than typing it, so a key that moves is a red case and not
  a number the case keeps a second copy of; it then asserts the rendered prompt fits the bound, that the prompt names
  the reference file the run rendered beside it, that the reference still states each of the three moved blocks whole,
  and that a configuration recording 1 000 B gets the over-bound line naming both numbers.
  *Verified:* all of the case's assertions pass on the fixed source (`evidence/green.log`).
- [x] The case checked out alone over the base — `git checkout 681fbd3f -- tools/loop/tests/loop-cases.sh
  tools/loop/tests/loop-mutations.sh` — and run: `bash tools/loop/tests/loop-selftest.sh --case
  'run#theWorkerPromptFitsItsBudget'` prints
  `loop.conf records no LOOP_WORKER_PROMPT_MAX_BYTES — the size every turn of an item re-sends has no bound to be read
  against`, `loop-selftest: 1 of 1 cases failed`, exit 1.
  *Verified:* `evidence/red-on-base.log`, first block. The two case files were then restored to the base.
- [x] The second red, which is the size the case exists for: the key added to the base's `loop.conf` **alone**, with
  the prompt unmoved, and the same case run — `the composed worker prompt is 33726 B, over the recorded bound of 29000
  B (LOOP_WORKER_PROMPT_MAX_BYTES), and every turn of the item re-sends it`, 1 of 1 cases failed, exit 1.
  *Verified:* `evidence/red-on-base.log`, second block, and the same 33 726 B figure the fixture renders before the move
  in `evidence/measure.log`.

## The move

- [x] `tools/loop/prompts/worker-reference.md` (new, 9 443 B): the long form of the three blocks the worker consults —
  "Where the project's rules are", "What your own shell and tools refuse", "One call per measurement, and one turn for
  the independent ones" — **moved verbatim** from `worker.md`, with a header saying it is rendered once per item, that
  it is not re-sent with the prompt, and that nothing in it is a second rule.
  *Verified:* `tools/look.sh 'the text the prompt used to carry whole'` finds that sentence in the new file, and the
  case's three heading assertions pass (`evidence/green.log`).
- [x] `tools/loop/prompts/worker.md` (27 516 B → 21 789 B): the pointer section "Where the rest of this prompt's rules
  are" names `{{WORKER_REFERENCE}}` and says it is read once and not re-sent; the shell-refusal block keeps every verb
  the policy refuses with the call that answers one; the trimmed-result rule and the batching rule with the
  `tools/loop/phase.sh` split stay in the prompt; the untrusted-text section names the reference beside the prompt as
  the child's instructions.
  *Verified:* `prompt#workerNamesEveryPhaseAndSkill`, `prompt#thePhasePromptsCarryTheirOwnRoute` and
  `prompt#thePhasePromptNamesWhatItsShellRefuses` pass in the `prompt` group (`evidence/green.log`), and no placeholder
  is left unfilled in either rendered file (`evidence/measure.log`).
- [x] `tools/loop/run.sh`: the reference is rendered once per item with the same `KEY=VALUE` pairs as the prompt, the
  item's prompt and the round-2 prompt both name that one copy, and `worker_prompt_bound_check` is called at both
  render sites.
  *Verified:* the case asserts the prompt names the rendered reference's own path, and the group's prompt cases render
  through the same code path and pass (`evidence/green.log`).

## The bound, and the record of it

- [x] `tools/loop/loop.conf`: `LOOP_WORKER_PROMPT_MAX_BYTES=29000`, with the paragraph that states what it bounds, why a
  prompt's size is a per-turn cost, why the number sits above the render a throwaway project produces, and that `0` or
  an absent key is no bound.
  *Verified:* the case reads the key out of the file and compares the rendered prompt against it (`evidence/green.log`).
- [x] `tools/loop/child.sh`: `worker_prompt_bound_check` — `stat` the file, read the bound, return quietly when either
  is absent or non-numeric or the bound is `0`, and otherwise emit `worker-prompt-over-bound` naming the phase, the
  size and the bound on the run's own record and console. It reports and changes no verdict.
  *Verified:* the case's lowered configuration (1 000 B) gets the line naming both numbers, and the fixed tree's own
  bound produces none (`evidence/green.log`).
- [x] `tools/loop/events.tsv`: the `worker-prompt-over-bound` line, its fields and its text naming the key that bounds
  it. `tools/loop/shared.tsv`: `worker_prompt_bound_check child.sh run.sh`. `tools/loop/layout.tsv`: the function in
  `child.sh`'s row, so the ownership case reads one definition and one owner.
  *Verified:* `driver#anOwnedFunctionIsInItsOwnersFile` reports the same two base rows as on `ae2373cb` and no row for
  `worker_prompt_bound_check` (`evidence/green.log`).
- [x] `tools/loop/state.sh`: `cmd_init` records the key as `run.workerPromptMaxBytes` beside the three bounds of #815,
  `cmd_init`'s console line names it, and `cmd_show` prints it with them.
  *Verified:* the case asserts the recorded value equals the file's own number and that `show` prints `worker prompt
  <bound>` (`evidence/green.log`).
- [x] `tools/loop/report.sh`: the `bounds:` header line prints the worker-prompt bound beside the result bound and the
  two context bounds, with `?` for a state written before the record carried it.
  *Verified:* the case asserts the report names `worker prompt <bound>` (`evidence/green.log`).

## The cases and mutations beside it

- [x] `prompt#workerIsHandedTheMeasureBlock` and `prompt#theChangeChildCitesTheMeasureBlockAndRunsNoSuite`: both assert
  paragraphs that now live in the reference, so each reads the prompt and the reference together and fails when either
  file is missing.
  *Verified:* both pass in the `prompt` group (`evidence/green.log`).
- [x] `tools/loop/tests/loop-mutations.sh`: mutations `61.3` and `263.1` target `worker-reference.md` instead of
  `worker.md`, with the reason the paragraph moved, so a mutation still reddens the case that names it.
  *Verified:* `driver#aHunkOutsideEveryCaseBodyOwesEveryCase` passes, and
  `bash tools/loop/tests/loop-mutations-selftest.sh` exits 0 if the suite is present (`evidence/green.log`).

## The requirement, in the shipped spec

- [x] `openspec/specs/agent-issue-loop/spec.md`: "A child carries its own route, and not the project's whole document"
  gains two scenarios — "What the prompt only consults travels as one file it names" and "The composed prompt's size is
  a recorded bound" — each naming `run#theWorkerPromptFitsItsBudget` as the case that exercises it.
  *Verified:* both scenarios are in the file as the landed fix writes them, and the archive leaves the file untouched
  (`git status --porcelain openspec/specs/` is empty after it).
- [x] The scenarios were written **by this fix itself** rather than as a change-relative `specs/` delta, so this change
  carries none: `.openspec.yaml` declares `skip_specs: true` and the archive runs with `--skip-specs`. `proposal.md`,
  "Spec delta: none, and why", states it and names the precedent.
  *Verified:* `openspec validate fix-the-prompt-every-turn-resends-is-bounded --strict` prints its `skip_specs` INFO line
  and exits 0, and the archive prints `Skipping spec updates (--skip-specs flag provided).` — both in the landing report.

## The falsification

- [x] The mutation is the base's own source: the tree the case first ran in carried `23deb8c3`'s prompts, `run.sh`,
  `child.sh`, `state.sh` and `loop.conf`, and only the two case files were the fix's. Two reds fall out of it — the
  missing key, and, with the key added and the prompt unmoved, the 33 726 B measurement against 29 000 B.
  *Verified:* `evidence/red-on-base.log` **is** those runs, so no separate mutation is owed for the prompt's size.
- [x] Restore, then green: the fix applied (`git cherry-pick -n 681fbd3f`, whose staged tree equals the commit's —
  `git diff 681fbd3f -- ':!openspec/changes'` is 0 bytes), the same case passes.
  *Verified:* `evidence/green.log`, the first block.

## The focused runs, and what is excluded

- [x] `bash tools/loop/tests/loop-selftest.sh --case 'run#theWorkerPromptFitsItsBudget'` — `PASS`.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'run#reviewBudgetIsARecordedValue'` — `PASS`; the recorded-value
  pattern the new bound follows, and the case whose bounds line this change widens.
- [x] `bash tools/loop/tests/loop-selftest.sh --group prompt` — 22 cases, all `PASS`.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'policy#*'` — `PASS`; the prompt's refusal block names every verb
  the policy exports, and a policy case that stopped resolving would be the prompt teaching a rule the tree refuses.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'adapter#theChildIsStartedWithTheShellPolicy'` — `PASS`.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` and
  `--case 'driver#aHunkOutsideEveryCaseBodyOwesEveryCase'` — `PASS`.
- [x] `bash -n tools/loop/run.sh tools/loop/report.sh tools/loop/state.sh` — exits 0, and
  `bash tools/loop/tests/loop-mutations-selftest.sh` for the mutation suite the two moved mutations sit in.
- [x] Name the red this change carries: `driver#anOwnedFunctionIsInItsOwnersFile` is red at the base `ae2373cb` itself
  (`report.sh defines action_add and its row does not own it`, `state.sh defines cmd_publish_refused and its row does
  not own it`). This fix **does** touch `report.sh` and `state.sh`, so the case was re-run here and its text compared
  with the base's: neither file gains a function and `layout.tsv` gains none, so the same two rows are reported and no
  third appears.
  *Verified:* `evidence/green.log`, the section "The red this change carries, and the proof it did not grow", with the
  diff of `report.sh` and `state.sh` and the case's text on both trees.
- [x] The measured sizes: `wc -c tools/loop/prompts/*.md` before and after, the composed prompt rendered through the
  loop's own renderer over one value set before and after, the archived item's own 32 580 B prompt, and the case's
  fixture render at 33 726 B → 28 389 B against the 29 000 B bound.
  *Verified:* `evidence/measure.log`, verbatim.
- [x] Exclusions, named: no build and no full suite — the delta touches no compiled file, so the structural checks
  above stand in (`guidelines/Build.md` §2, the scope block), and the parent gates the branch once. No device
  verification: nothing here reaches a device. No archive-driven spec merge: `--skip-specs`, per "The requirement, in
  the shipped spec" above. The two figures the fix's own message and `loop.conf` comment print (27 073 / −5 507 and
  26 385 / 10 130) are not re-derived here and are recorded as a number to correct in a change of its own —
  `proposal.md`, "Two figures the fix's own text prints do not re-render".

## Archive

- [x] `openspec validate fix-the-prompt-every-turn-resends-is-bounded --strict`, then
  `openspec archive fix-the-prompt-every-turn-resends-is-bounded --yes --skip-specs`, which moves this directory to
  `openspec/changes/archive/2026-11-25-fix-the-prompt-every-turn-resends-is-bounded/` and leaves `openspec/specs/`
  untouched.
  *Verified:* `git status --porcelain openspec/specs/` is empty afterwards, and `evidence/` travels with the directory.
