# Tasks — the prompt names no shell range read

- [x] 1. Extend the red case first, in `tools/loop/tests/loop-cases.sh`: the rendered worker prompt names no `sed -n`
      and does name the read that pages a range, the four skeletons quote the notice `trimNotice` emits, and the
      module's own output names `ctx_read`'s range call alone; rewrite
      `trim#aResultAboveTheBoundIsTrimmedAndNamed` from `sed -n` to that call, and declare
      `tools/pi-trim-result.mjs` and `guidelines/BuildGate.md` on the extended case's `# files:` line. Red proof on
      the base commit `5e77d751`:
      `bash tools/loop/tests/loop-selftest.sh --case 'prompt#thePhasePromptNamesWhatItsShellRefuses'` — exit 1,
      `loop-selftest: 1 of 1 cases failed`, first failure
      `the block names no shell range read beside the tools it hands the child (issue #1007): 'sed -n' should not
      appear in: # Worker — one item, phases A–D` (`evidence/red-on-HEAD-1007.log`). A shell suite carries no JUnit
      XML of its own (`.pi/skills/revert-check`, "When the gate is a shell self-test, not Gradle"): the tally is the
      suite's own line. The upstream case `trim#aResultBetweenTheNewBoundAndTheOldOneArrivesCut`, added after this plan
      was written, asserted the notice's old arm (`'sed -n'`); it is rewritten to the same call and its absence, and is
      green (`evidence/adjacent-cases.log`).
- [x] 2. Make the range `ctx_read`'s own call, in the block and in the notice: `tools/pi-trim-result.mjs:101-104`
      (`trimNotice` drops `, or sed -n 'N,Mp'`), the notice's four quotes (`worker.md:124`, `worker-gate.md:44`,
      `worker-tail.md:48`, `reviewer.md:35`), and the refusal bullet's carve-out in `worker.md:76-77`,
      `worker-gate.md:67-68` and `worker-tail.md:71-72`. Verified by task 1's case, green —
      `PASS prompt#thePhasePromptNamesWhatItsShellRefuses` (`evidence/green.log`) — and by the rewritten trim case —
      `PASS trim#aResultAboveTheBoundIsTrimmedAndNamed` (`evidence/trim-green.log`). The cases the delta reaches, which
      quote the same notice or render the same skeletons, stay green: `prompt#thePhasePromptsCarryTheirOwnRoute`,
      `repeat#aRepeatReadIsNamedNotHandedOverAgain`, `trim#aResultUnderTheBoundIsHandedOverWhole`,
      `trim#theTrimIsRecorded` (`evidence/adjacent-cases.log`).
- [x] 3. Make the rule the block renders say the same thing (`guidelines/BuildGate.md` §2): the sentence naming
      `sed -n 'N,Mp'` and `tail -n` as how a range of a known file is read is replaced by `ctx_read`'s own `offset`
      and `limit`, keeping the true half — neither verb is refused, because §10 of `Build.md` reads a build's log with
      them. The section's measured size moves with it, so `AGENTS.md`'s two routing rows for §2's sub-sections state
      `1.4–7.7 kB` and `tools/check-doc-routes.sh` resolves all 357 references with no figure left unequal to the
      measurement (`evidence/structural-checks.log`).
- [x] 4. Add the four mutation records that hold the diff in place — `1007.1` (the bullet's arm back), `1007.2`
      (`trimNotice`'s arm back), `1007.3` (the reviewer's quote back), `1007.4` (the guideline sentence back) — and
      check they apply: `bash tools/loop/tests/loop-mutations.sh --check --files tools/loop/prompts/worker.md --files
      tools/loop/prompts/reviewer.md --files ../pi-trim-result.mjs --files ../../guidelines/BuildGate.md` —
      `selected 30 of 446 records`, `all 30 cases passed` (`evidence/mutations-check.log`). The records are named in
      the harness's own form for a file outside the engine (`../pi-trim-result.mjs`, `../../guidelines/BuildGate.md`),
      the form its sixteen `../` records already use; `--from-git`'s repository-relative paths do not select that form,
      so the gate's mutation run reaches them only through `--check`'s file list, which is why the reddening run was
      taken here: `bash tools/loop/tests/loop-mutations.sh --only "1007.1 1007.2 1007.3 1007.4"` —
      `red-when-mutated: 4 · not-noticed: 0 · restore-failures: 0` (`evidence/mutations-red.log`).
- [x] 5. Run the plan's first line, which a delta that writes a case owes first:
      `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` — PASS, the
      extended case declares the files it now reads (`evidence/declaration.log`).
- [x] 6. Structural check of the files this change edits, in place of a build (the delta edits no compiled path):
      `bash -n tools/loop/tests/loop-cases.sh`, `bash -n
      tools/loop/tests/loop-mutations.sh`, `node --check tools/pi-trim-result.mjs`, `tools/check-doc-routes.sh` and
      `tools/check-doc-values.sh` — all exit 0 (`evidence/structural-checks.log`). `openspec validate --strict
      fix-the-prompt-names-no-shell-range-read` parses the delta and exits 1 on one warning — the requirement
      "A child's tool results are bounded and a trim is named" is over 500 characters — which the sibling change
      `fix-a-childs-bound-reads-its-own-session` reproduces on a requirement this change never touches, so it is the
      capability's own shape and not this delta (`evidence/openspec-validate.log`).
- [x] 7. Count the surface before the count is claimed: `git grep -c 'sed -n' 5e77d751 -- tools/loop/prompts
      tools/pi-trim-result.mjs guidelines/BuildGate.md` — 7 lines in 4 skeletons, 1 module line and 1 guideline line;
      the prompts are at 0 after the change, the guideline's one mention stays on purpose, as the clause that says
      neither verb is refused, and the module's two remaining lines are `rangeRead`'s own recognition of the shell form
      (issue #1009) and not the notice a child reads (`evidence/surface.log`). The proposal carries that count on its
      `surface:` line.
- [x] 8. Take the measuring command's block for this change and keep it beside the evidence:
      `./tools/loop/measure.sh --change fix-the-prompt-names-no-shell-range-read` — `scenarios=6 cases=6 unnamed=0`
      (`evidence/measure.log`).
- [x] 9. Name the delta: the two MODIFIED requirements in `specs/agent-issue-loop/spec.md` — the block hands the child
      `ctx_read`'s own range call and names no shell range read beside it, and the notice a cut result carries is that
      call alone — and their six scenarios each name their case.
- [x] 10. This change's own session completes it and runs the item's one gate: the pointers above re-read against the
      tree (`tools/loop/evidence-check.sh` — `evidence/evidence-check.log`), the evidence copied into `evidence/`, the
      delta archived with `openspec archive --yes`, and only then `./tools/loop/gate.sh`. The gate is this session's
      (it is the last thing it does), so the tally it prints is quoted in the report and nothing is written to the
      change after it (issue #837: a change directory written after the gate is a tree the gate never measured).
