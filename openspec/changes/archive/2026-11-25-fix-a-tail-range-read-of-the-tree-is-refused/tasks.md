# Tasks — a `tail`'s range read of the tree is refused

No compiled file is touched: the delta is one rule file, one case file, three prompt skeletons, one guideline
paragraph, one adapter comment and one requirement already in the shipped spec. The structural checks of the files
edited stand in for a build, and the focused selections stand in for the suite, which the parent runs once over the
branch (`guidelines/Build.md` §2, the scope block).

## The case, first — red on the base before any production change

- [x] `tools/loop/tests/loop-cases.sh`: the case
  `policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot` — its body, its row in `LOOP_SHELL_CASES` and its
  dispatch arm — beside `policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot` and
  `policy#aShellTreeSearchIsRefusedAndARecipeIsNot`, with the `# files:` declaration its first line names
  (`tools/loop/tests/loop-cases.sh tools/loop/tests/loop-selftest.sh tools/pi-shell-refusal.mjs`).
  *Verified:* `driver#everyCaseDeclaresTheFilesItCanReach` prints `PASS` (`evidence/green.log`) — the case that refuses
  a declaration narrower than the body.
- [x] The case checked out alone over the base — `git checkout dc1fd9da -- tools/loop/tests/loop-cases.sh` — and run:
  `bash tools/loop/tests/loop-selftest.sh --case
  'policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot'` prints
  `a range  of a tracked file is refused (exit 0): ALLOWED: expected '3', got '0'`,
  `loop-selftest: 1 of 1 cases failed`, exit 1.
  *Verified:* `evidence/red-on-base.log`, the whole run, verbatim. The case file was then restored to the base.
- [x] The case's assertions cover both directions: the refusal of `tail -n +688 guidelines/Build.md` and of
  `tail -688 guidelines/Build.md` with the reason, `ctx_read path=<path> offset=<N> limit=<M>` and the `2 000 lines`
  cap in its text, and `tail -n` no longer offered as how the tree is read; the controls `head -n 20 AGENTS.md`
  (refused — a count excuses the read under no other verb) and `grep 5 /tmp/build-app.log` (allowed — a numeric pattern
  does not swallow the path beside it); and five allowed arms — a report under a module's `build/`,
  `ki_processing_failures.log`, `/tmp/build-app.log`, an item's own `gate.log`, and `adb logcat -d | tail -n 40`.
  *Verified:* all of the case's assertions pass on the fixed source (`evidence/green.log`).

## The policy change

- [x] `tools/pi-shell-refusal.mjs`: `isRangeRead()` deleted and `refusesSegment`'s `tail` early return with it, so a
  range `tail` is classified by the file it names — the rule issue #1006 established for `sed`'s quiet arm.
  *Verified:* `node tools/pi-shell-refusal.mjs --command 'tail -n +688 guidelines/Build.md'` exits 3 and
  `… --command 'tail -n 5 ki_processing_failures.log'` exits 0 (`evidence/measure.log`).
- [x] `tools/pi-shell-refusal.mjs`: `operands()` skips the count a range flag takes as its own word, for `head` and
  `tail` only, with the comment stating why the skip is what keeps log recipes standing and why another verb's
  numeric word stays an operand.
  *Verified:* `tail -n 40 /tmp/build-app.log` and `tail -n 100 .pi/issue-loop/items/1083/gate.log` are `ALLOWED`
  while `head -n 20 AGENTS.md` is refused (`evidence/measure.log`), and `node --check tools/pi-shell-refusal.mjs`
  exits 0 (`evidence/green.log`).
- [x] `tools/pi-shell-refusal.mjs`: `refusalReason`'s `sed` arm serves `tail` too — the reason names
  `ctx_read path=<path> offset=<N> limit=<M>` and the ~2 000-line / 50 kB cap that call stops at — and the reason's
  trailing list of what stays the shell's drops `tail -n`.
  *Verified:* the refusal's text in `evidence/measure.log` carries both the call and the cap, and the case's
  `assert_not_contains` on the old allowance passes (`evidence/green.log`).
- [x] The file's two comments that stated the verb allowance — the header's range-read bullet and `inTheTree`'s log
  clause — state the file rule instead, naming this issue's 155-and-58 measurement.
  *Verified:* `tools/look.sh 'same case one verb over'` finds the header bullet in `tools/pi-shell-refusal.mjs`,
  and `tools/look.sh 'both left standing'` finds the rewritten log clause there.

## The prose the rule is taught by

- [x] `tools/loop/prompts/worker.md`, `worker-gate.md` and `worker-tail.md`: the shell-policy bullet no longer says
  "a `tail -n` of any file" is not refused; it says a quiet `sed`'s line range and a `tail`'s range over a file of the
  tree are that same read and are refused with it, and lists the log, item-artefact, substitute/write and
  pipe-filter exemptions as before.
  *Verified:* `tools/look.sh "a \`tail\`'s range over a file of the tree" --path tools/loop/prompts` finds the
  sentence once in each of the three files and nowhere else; the gate-poll recipes that still spell `tail -n`
  (`worker-gate.md:121,126`, `gate-own.md:28,32`) read `{{EVIDENCE_DIR}}/gate.log`, an item artefact, which the case
  asserts is allowed — so they are the exemption and not the allowance the change removes.
- [x] `guidelines/BuildGate.md` §2a, the paragraph that owns the rule: `tail`'s range flag named beside `sed`'s quiet
  arm, `tail -n <N>` dropped from what stands, and the sentence citing issues #1006 and #1083.
  *Verified:* `tools/look.sh 'range flag is that search' --path guidelines/BuildGate.md` finds the paragraph and
  `tools/look.sh 'tail -n' --path guidelines/BuildGate.md` finds nothing; `bash tools/check-doc-routes.sh` reports
  `all 356 section references resolve` and exits 0 (`evidence/green.log`).
- [x] `tools/pi-child-events.sh`: the adapter's comment over the policy line carries the same sentence, so the seam
  that starts every child states the rule the child is handed.
  *Verified:* `bash -n tools/pi-child-events.sh` exits 0 and
  `adapter#theChildIsStartedWithTheShellPolicy` — the case that reads that line and the policy file it loads — prints
  `PASS` (`evidence/green.log`).

## The requirement, in the shipped spec

- [x] `openspec/specs/agent-issue-loop/spec.md`: "A range read of the tree is refused and a log's is not" names a
  `tail` whose range flag (`-n`, `-c`) carries the range beside `sed`'s quiet arm, keeps every exemption it named, and
  gains the scenario "A `tail`'s range read of a tracked file is refused and a log's is not" with the case
  `policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot` as the one that exercises it.
  *Verified:* this sentence and the scenario are in the file as the landed fix writes them, and the archive leaves the
  file untouched — `git status --porcelain openspec/specs/` is empty after it.
- [x] The requirement's text was updated **by this fix itself** rather than as a change-relative `specs/` delta, so
  this change carries none: `.openspec.yaml` declares `skip_specs: true` (the declaration without which
  `--strict` refuses a change with no delta) and the archive runs with `--skip-specs`. `proposal.md`, "Spec delta:
  none, and why", states it and names the precedent (`2026-11-24-fix-the-reds-the-main-carried`).
  *Verified:* `openspec validate fix-a-tail-range-read-of-the-tree-is-refused --strict` prints
  `Change 'fix-a-tail-range-read-of-the-tree-is-refused' is valid` with its `skip_specs` INFO line and exits 0, and
the archive prints `Skipping spec updates (--skip-specs flag provided).` — both in the landing report.

## The falsification

- [x] The mutation is the base's own source: the tree the case first ran in carried `ae2373cb`'s
  `tools/pi-shell-refusal.mjs` — `isRangeRead()` and its early return, which is the whole mechanism this change
  removes — and only the case file was the fix's. The case reddens on it: `expected '3', got '0'`,
  `1 of 1 cases failed`, exit 1.
  *Verified:* `evidence/red-on-base.log` **is** that run, so no separate mutation is owed: the case was written
  against the base's file before the fix was applied, and any mutation of the fix's file back to the base is
  byte-identical to it.
- [x] Restore, then green: the fix applied (`git cherry-pick -n dc1fd9da`, whose
  `git patch-id` equals the commit's), the same case passes.
  *Verified:* `evidence/green.log`, the first block.

## The focused runs, and what is excluded

- [x] `bash tools/loop/tests/loop-selftest.sh --case 'policy#*'` — 3 cases, all `PASS`.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'adapter#theChildIsStartedWithTheShellPolicy'` — `PASS`.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` — `PASS`.
- [x] `node --check tools/pi-shell-refusal.mjs`, `bash -n tools/pi-child-events.sh`,
  `bash -n tools/loop/tests/loop-cases.sh` — each exits 0.
- [x] `bash tools/check-doc-routes.sh` — the section references the guideline paragraph and the spec scenario rely on
  resolve.
  *Verified:* all of the above in `evidence/green.log`, one block per command.
- [x] Name the red this change carries: `driver#anOwnedFunctionIsInItsOwnersFile` is red at the base `ae2373cb`
  itself, with two rows (`report.sh defines action_add and its row does not own it`, `state.sh defines
  cmd_publish_refused and its row does not own it`); `git show ae2373cb:tools/loop/layout.tsv` owns neither function,
  and this change touches no `tools/loop/*.sh` and no `layout.tsv`.
  *Verified:* `evidence/green.log`, the section "The red this change carries and does not cause".
- [x] The measured matrix the rule is read through: `tail -n +688 guidelines/Build.md` and
  `tail -688 guidelines/Build.md` refused (exit 3), `tail -n 5 ki_processing_failures.log`,
  `tail -n 5 /tmp/build-app.log`, `tail -n 5 .pi/issue-loop/items/1083/gate.log` and `adb logcat -d | tail -n 40`
  allowed (exit 0), `head -n 20 AGENTS.md` refused (exit 3).
  *Verified:* `evidence/measure.log`, verbatim.
- [x] Exclusions, named: no build and no full suite — the delta touches no compiled file, so the structural checks
  above stand in (`guidelines/Build.md` §2, the scope block), and the parent gates the branch once. No device
  verification: nothing here reaches a device. No archive-driven spec merge: `--skip-specs`, per "The requirement, in
  the shipped spec" above. The issue's other half — rewriting the on-device recipes in
  `guidelines/BuildNative.recipes.md` — is not in this fix and is neither claimed nor decided here.

## Archive

- [x] `openspec validate fix-a-tail-range-read-of-the-tree-is-refused --strict`, then
  `openspec archive fix-a-tail-range-read-of-the-tree-is-refused --yes --skip-specs`, which moves this directory to
  `openspec/changes/archive/2026-11-25-fix-a-tail-range-read-of-the-tree-is-refused/` and leaves `openspec/specs/`
  untouched.
  *Verified:* `git status --porcelain openspec/specs/` is empty afterwards, and `evidence/` travels with the
  directory.
