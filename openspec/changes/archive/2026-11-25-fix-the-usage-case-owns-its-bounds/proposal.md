# Proposal: fix-the-usage-case-owns-its-bounds

## Why

**Root cause.** `run#childUsageIsRecordedPerItem` stages a child whose own event stream stands at 150 000 tokens —
the compaction's `tokensBefore` in the fixture the case writes (`tools/loop/tests/loop-cases.sh:12983`) — and the case
inherits the profile's checkpoint with it: `LOOP_ITEM_CONTEXT_WARN=90000` (`tools/loop/loop.conf:269`). Since #856 the
run **ends** the item's first worker at that crossing, once it has left work in the tree
(`tools/loop/child.sh:552`), and continues it in a child of its own (`tools/loop/run.sh:1282`, the prompt
`worker-prompt-continuation.md`). Whether the crossing is taken is decided by **scheduling and not by the tree**: the
reading is taken once per whole second of the child's life (`tools/loop/child.sh:427`, the `last_second` guard), so a
stub child that ends inside that first second counts the phase's two children and one the host holds for longer counts
three.

**Observed** ✗: the gate of item #1018's change recorded `expected '2', got '3'` on the count assertion
(`tools/loop/tests/loop-cases.sh`, the `grep -c '^invocation'` line this change replaces with the prompt list at
`:13029`) inside `LOOP-GATE: tests=1256 failures=1 errors=0` (that gate's XML is not retained), while the same case
passed alone twice and the whole `--from-git` selection then passed `539/539` on the same tree — the item landed on a
re-run, so the item's verdict and the tree's are no longer the same reading. Reproduced deterministically for this
change by holding the stub child past one reading (`evidence/red-on-HEAD.log`): `sleep 3` after the fixture's events
in `tools/loop/tests/fixtures/pi` makes the same case fail with the same line, and the stub restored byte for byte
(`sha256 c06d45a1…`) makes it pass again.

**Fix** ➜: the case records, in a profile copy of its own, the two bound values that decide whether a child of it is
split — the checkpoint off (`0`, the documented "off" of both context keys) and the stop at the recorded 200 000,
which stays above the fixture's 150 000 — exactly as the context-bound cases record the bound they enforce
(`tools/loop/tests/loop-cases.sh:23133`, `LOOP_CONF` being the self-test's own seam, `tools/loop/run.sh:90`). The
count of children then belongs to the phase and to nothing else. The count assertion itself becomes the **list of
prompts** the stub recorded: a list of two names is the same fact as a count of two with the one word a reader needs
kept, and a third child is named — `worker-prompt-continuation.md` — instead of reported as `got '3'` (issue #1023).

**Not the fix**: raising `LOOP_ITEM_CONTEXT_WARN` above 150 000, or raising the fixture below it. The checkpoint's
value is a measurement (`.pi/issue-loop/items/1003/split-model.py`, 1 055 Mtok as-is against 866 at 90 000 — 18 % below, archive
`2026-11-22-fix-the-checkpoint-is-the-splits-reading`), so a case may not price the profile; and a fixture that leans
on the profile's current number puts the flake back the next time the checkpoint moves.

surface: 1 case changed (its `# files:` declaration, its profile copy, its child assertion), 1 mutation note
re-read, 1 requirement added, 1 guideline section — `tools/look.sh 'the worker phase is two children'` (3 hits in
`tools/loop/tests/loop-cases.sh`, all in this case's body) and `tools/look.sh 'childUsageIsRecordedPerItem'` (5 hits in
`openspec/specs/agent-issue-loop/spec.md`, the scenarios that name the case).

## What Changes

- `tools/loop/tests/loop-cases.sh` — `run#childUsageIsRecordedPerItem` records the two context keys in a profile copy of
  its own and exports `LOOP_CONF`; its count assertion becomes the list of prompts the stub recorded; its `# files:`
  declaration gains `tools/loop/loop.conf`.
- `guidelines/Backlog.recipes.md` — one paragraph in the section that owns the suite's cases: a case whose count a
  bound can decide records that bound itself and names its children.
- `openspec/specs/agent-issue-loop/spec.md` — the requirement "A case that counts a phase's children owns the bounds
  that decide them", with two scenarios, both naming `run#childUsageIsRecordedPerItem` as their case.

## Evidence

- **Red on HEAD**, deterministically: the stub child held past one reading of the wait fails the case with
  `the worker phase is two children, not one: expected '2', got '3'` (`evidence/red-on-HEAD.log`,
  `loop-selftest: 1 of 1 cases failed`).
- **Green with the fix under the same hold**: the same mutation, the case as changed — `PASS`
  (`evidence/green-with-the-hold.log`), i.e. the third child was the checkpoint's and not the phase's.
- **Green focused, twice**: `--case run#childUsageIsRecordedPerItem` on the changed tree
  (`evidence/case-focused.log`).
- **The declaration this delta widens**: `--case driver#everyCaseDeclaresTheFilesItCanReach`
  (`evidence/declaration-case.log`), because the case now reads `tools/loop/loop.conf`.
