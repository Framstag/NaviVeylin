# Tasks — a continuation is handed the ledger of what its first process read

No compiled file is touched: the delta is one engine file, one prompt skeleton, one case file, one guideline sentence
and two scenarios already in the shipped spec. The structural checks of the files edited stand in for a build, and the
focused runs stand in for the suite, which the parent runs once over the branch (`guidelines/Build.md` §2, the scope
block).

## The case, first — red on the unfixed source before any production change

- [x] `tools/loop/tests/loop-cases.sh`: the case `run#theContinuationIsToldWhatItsFirstWorkerRead` — its body, its row
  in `LOOP_SHELL_CASES` and its dispatch arm — with the `# files:` declaration its first line names
  (`tools/loop/tests/fixtures/pi tools/loop/loop.conf tools/loop/child.sh tools/loop/evidence.sh tools/loop/leftovers.sh
  tools/loop/run.sh tools/loop/worktree.sh tools/loop/prompts/ tools/pi-child-events.sh tools/tooling.json`).
  *Verified:* `driver#everyCaseDeclaresTheFilesItCanReach` — the case that refuses a declaration narrower than the
  body — prints `PASS` with the declaration as written (`evidence/green.log`).
- [x] The case checked out alone over the unfixed source — `git checkout 8386579a -- tools/loop/tests/loop-cases.sh` on
  `adb79f6f`, whose sources outside `openspec/changes` are `dc1fd9da`'s — and run: `bash
  tools/loop/tests/loop-selftest.sh --case 'run#theContinuationIsToldWhatItsFirstWorkerRead'` prints the assertion
  `the continuation is told the ledger of what the first process read, so it starts from that instead of reading the
  tree again: '…/items/21/tool-result-ledger.tsv' not found in: # Worker, continued — one item, the work a bound
  ended`, then `FAIL`, `loop-selftest: 1 of 1 cases failed`, exit 1. The case file was then put back the way the fix
  leaves it — `git checkout -- tools/loop/tests/loop-cases.sh` restores it from the index, which still held
  `8386579a`'s copy — and the fix was applied on top of it.
  *Verified:* `evidence/red-on-base.log`, the whole run, verbatim.
- [x] The case reads the ledger path out of the environment the adapter handed the **first child** rather than typing
  it: its stub child writes `"${LOOP_TOOL_RESULT_LEDGER:-}"` to `first-ledger.txt`, and the case asserts that path is
  the item's own (`/items/21/tool-result-ledger.tsv`) before asserting the prompt carries it — so a ledger renamed in
  `tools/pi-child-events.sh` is a red case and not a prompt naming a file nothing writes.
  *Verified:* the case's own second assertion passes on the fixed source and the first names the adapter's path
  (`evidence/green.log`).
- [x] The case asserts the second half of the bullet too: `worker-report.md` beside the ledger, and no `{{` left
  anywhere in the rendered skeleton.
  *Verified:* both assertions are in the case and pass (`evidence/green.log`).
- [x] `run#aPhaseThatWroteNothingIsAskedOnceMore`: the retry renders the same skeleton at a second site, so the case
  gains the same two assertions there (no `{{` left, the ledger named), and `tools/loop/prompts/` is added to its
  `# files:` declaration.
  *Verified:* `PASS run#aPhaseThatWroteNothingIsAskedOnceMore` (`evidence/green.log`).

## The ledger, named beside the item and rendered at both sites

- [x] `tools/loop/run.sh`: `item_ledger="$item_dir/tool-result-ledger.tsv"` named beside `events_file`, with the
  comment stating that the adapter puts the file there and hands every child the same path as
  `LOOP_TOOL_RESULT_LEDGER`, and why a continuation — a new session — is what needs it.
  *Verified:* the path is the one the case's stub child recorded from the adapter's own environment
  (`evidence/green.log`), so the two spellings of one file are proved one.
- [x] `tools/loop/run.sh`: `"LEDGER=$item_ledger"` added to **both** `render_prompt` calls that render
  `tools/loop/prompts/worker-continuation.md` — the split's continuation (`run.sh:1289`) and the retry of a phase that
  answered nothing (`run.sh:1418`).
  *Verified:* the placeholder is filled at both sites — `run#theContinuationIsToldWhatItsFirstWorkerRead` and
  `run#aPhaseThatWroteNothingIsAskedOnceMore` each assert no `{{` stands in the prompt they read
  (`evidence/green.log`).
- [x] `tools/loop/run.sh` still passes `bash -n`.
  *Verified:* `bash -n tools/loop/run.sh` exits 0 (`evidence/green.log`).

## The bullet, and the rule it states

- [x] `tools/loop/prompts/worker-continuation.md`: one bullet naming `{{LEDGER}}` — the item's own tool-result ledger,
  one line per path this item's children were handed, the five columns, the session column being the name of the
  prompt the reading child was given — and the sentence that not one of those paths is in this session's context, so
  the child asks for a passage rather than a file whole.
- [x] The bullet states the **item-ledger** rule and not the per-child one: one line naming the turn, the size and the
  range call for a path unchanged since it was read, a bounded head plus `[LOOP-REPEAT-EDIT: …]` for one that changed.
  *Verified:* the two arms are re-derived through the trim CLI over one ledger across two sessions
  (`evidence/measure.log`, "The rule the bullet states"); the per-child rule cannot fire there at all — its tally is
  `read.child`, one process deep — which is why the bullet does not name it.
- [x] The bullet points at the report the first process was ended with, `{{EVIDENCE_DIR}}/worker-report.md`, for what the
  ledger cannot carry, and the scratch bullet now says the first process's own notes still stand in that directory.
  *Verified:* the case asserts the report's path in the rendered prompt (`evidence/green.log`).

## The prose that enumerates what a continuation is handed

- [x] `guidelines/Backlog.recipes.md` §7d "Refusals, records and the landing", the sentence that says what the
  checkpoint prompt already names: "the tree,
  the leftover, the item's own ledger of what its children read and what is owed".
  *Verified:* the sentence is in the file as the fix writes it, in the paragraph that owns the retry of a phase that
  answered nothing — the second of the two sites `LEDGER=` is now rendered at, so the sentence and the code name the
  same prompt.
- [x] `openspec/specs/agent-issue-loop/spec.md`: "A worker that reaches the checkpoint is continued before the bound"
  and "The checkpoint is recorded where the split pays" both say the continuation child is started on the work package,
  the patch **and the item's own tool-result ledger of what its children read**.
  *Verified:* both lines are in the shipped spec as the fix writes them, and the archive leaves the file untouched —
  `git status --porcelain openspec/specs/` shows only what the cherry-pick staged.
- [x] The requirement's text was updated **by this fix itself** rather than as a change-relative `specs/` delta, so this
  change carries none: `.openspec.yaml` declares `skip_specs: true` and the archive runs with `--skip-specs`.
  `proposal.md`, "Spec delta: none, and why", states it and names the precedent
  (`2026-11-25-fix-a-tail-range-read-of-the-tree-is-refused`).
  *Verified:* `openspec validate fix-a-continuation-is-handed-what-its-first-read --strict` prints
  `Change 'fix-a-continuation-is-handed-what-its-first-read' is valid` and exits 0, and the archive prints
  `Skipping spec updates (--skip-specs flag provided).` — both in the landing report.

## The falsification

- [x] The mutation is the base's own source: the tree the case first ran in held `dc1fd9da`'s `tools/loop/run.sh` and
  its skeleton — no `item_ledger`, no `LEDGER=`, no bullet — and only the case file was the fix's. The case reddens on
  it. `evidence/red-on-base.log` **is** that run, so no separate mutation is owed: any mutation of the fix back to the
  base is byte-identical to it.
- [x] Restore, then green: the fix applied (`git cherry-pick -n 8386579a`, whose tree equals the reviewed commit's —
  `git diff 8386579a -- ':!openspec/changes' | wc -c` is 0), the same case passes.

## The focused runs, and what is excluded

- [x] `bash tools/loop/tests/loop-selftest.sh --case 'run#theContinuationIsToldWhatItsFirstWorkerRead'` — `PASS`.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'run#aContinuationRunsToTheStop'` — `PASS`, the split path the
  case sits in.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'run#aChainOfZeroContinuationsEndsAtTheCheckpoint'` — `PASS`.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'run#aContinuationEndedOnTheSameCeilingStopsTooWide'` — `PASS`.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'run#aPhaseThatWroteNothingIsAskedOnceMore'` — `PASS`, the retry
  site.
- [x] `bash tools/loop/tests/loop-selftest.sh --case 'driver#everyCaseDeclaresTheFilesItCanReach'` — `PASS`, the
  declaration of the case this change adds and of the one it extends.
- [x] `bash -n tools/loop/run.sh` and `bash -n tools/loop/tests/loop-cases.sh` — each exits 0.
  *Verified:* all of the above in `evidence/green.log`, one block per command.
- [x] Name the red this change carries: `driver#anOwnedFunctionIsInItsOwnersFile` is red at the base `ae2373cb` itself
  (`report.sh defines action_add and its row does not own it`, `state.sh defines cmd_publish_refused and its row does
  not own it`); `git show ae2373cb:tools/loop/layout.tsv` owns neither function, and the diff touches none of that
  case's inputs.
  *Verified:* `evidence/green.log`, the section "The red this change carries and does not cause".
- [x] The measured matrix the bullet is read through: a real item's ledger's columns and its two sessions beside two
  reports (`evidence/measure.log`, "The real ledger's shape"), and the rule's two arms re-derived across a session
  boundary through `node tools/pi-trim-result.mjs --ledger …` (`evidence/measure.log`, "The rule the bullet states").
- [x] Exclusions, named: no build and no full suite — the delta touches no compiled file (`guidelines/Build.md` §2, the
  scope block), and the parent gates the branch once. No device verification: nothing here reaches a device. No
  archive-driven spec merge: `--skip-specs`, per "The prose that enumerates what a continuation is handed" above. The
  saving the issue bounds at 1–3 Mtok per split item is not re-measured here — the run that would measure it is the
  next split item's, and this change neither claims nor decides it.

## Archive

- [x] `openspec validate fix-a-continuation-is-handed-what-its-first-read --strict`, then `openspec archive
  fix-a-continuation-is-handed-what-its-first-read --yes --skip-specs`, which moves this directory to
  `openspec/changes/archive/2026-11-25-fix-a-continuation-is-handed-what-its-first-read/` and leaves `openspec/specs/`
  untouched.
  *Verified:* `git status --porcelain openspec/specs/` names only the spec the cherry-pick staged afterwards, and
  `evidence/` travels with the directory.
