# Proposal — a `tail`'s range read of the tree is refused

## Why

**Root cause.** `refusesSegment` (`tools/pi-shell-refusal.mjs`) returned early for `tail` whenever `isRangeRead()`
held, and that predicate tests the verb's flags (`-n`, `-c`, `-n<N>`, `-<N>`) and never the path it is handed. The
exemption was therefore a fact about the verb and not about the file, one verb over from the rule issue #1006
established for `sed`: a range read of a **tracked file** is a read of the tree and is refused, while the log outside
it, the item artefact, the pipe's filter and the write stay. `tail` was the same case one verb over —
`tail -n +688 tools/loop/prescreen.sh` read a tracked file the policy allowed, in one call the tools answer — and
the change that fixed `sed` deferred it deliberately, as "a separate verb with its own measurement"
(`openspec/changes/archive/2026-11-22-fix-a-range-read-of-the-tree-is-refused/design.md`, "Alternatives the evidence
eliminated"). This change is that measurement, taken.

**Evidence** (issue #1083, over the 8 newest items): `tail` 155 calls, **58 of them naming a tracked file**, beside
`sed` 688 (225 naming one) and `grep` 276 (190), 1.79 M characters across the three — about **20 % of read calls** a
single `ctx_read path=<path> offset=<N> limit=<M>` or one `tools/look.sh '<regex>'` answers.

**The red, on the base.** `policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot`
(`tools/loop/tests/loop-cases.sh`) on `ae2373cb` with only that case file checked out over it:

```
policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot|   a range  of a tracked file is refused (exit 0): ALLOWED: expected '3', got '0'
--- policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot[0] (exit 1)
FAIL policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot
loop-selftest: 1 of 1 cases failed
```

(`evidence/red-on-base.log`, the whole run; the two spaces are the case's own message, see that file.)

**The fix** ➜ `isRangeRead()` is deleted and with it `refusesSegment`'s `tail` early return, so a range `tail` is
classified by the file it names, exactly as `sed`'s quiet arm is. `refusalReason`'s `sed` arm becomes
`sed`'s-or-`tail`'s and names `ctx_read path=<path> offset=<N> limit=<M>` with the ~2 000-line / 50 kB cap that call
stops at, so a child whose range is wider keeps the shell; the reason's trailing list no longer offers `tail -n`.
`operands()` skips the count a range flag takes as its own word, because `head` and `tail` are the two verbs that
write that count as a word — without the skip, `tail -n 40 <log>` would name a second "file" (`40`) and the skip's
absence would refuse every log recipe the exemption exists for. Another verb's numeric word stays an operand
(`grep 5 <file>` is still refused).

**What the change keeps intact** — the exemptions the rule is about, each asserted in the new case: a range read of
a log outside the tree (`/tmp/build-app.log`, `ki_processing_failures.log`), a report under a module's generated
`build/`, a file named from inside an item directory, and a `tail` standing at the tail of a pipe, which reads the
command before it rather than the tree.

**The requirement this defect contradicts** is `agent-issue-loop`'s "A range read of the tree is refused and a log's
is not" (`openspec/specs/agent-issue-loop/spec.md`): it named `sed`'s quiet arm only, and it now names `tail`'s range
flag beside it and carries a scenario for this case.

**The bound it does not claim.** Moving ~20 % of read calls onto the bounded reads does not claim those calls
disappear, and it takes no position on the reads already refused — a refusal is a turn of its own, priced by
`LOOP_REPEAT_READ_REFUSALS` and not by this policy. The item's other half, rewriting the on-device recipes in
`guidelines/BuildNative.recipes.md` to the sanctioned read, is not in this commit (`dc1fd9da` touches no such file);
this change neither claims nor decides it.

## What Changes

- **`tools/pi-shell-refusal.mjs`** — `isRangeRead()` deleted with its call, `operands()` skips a range count it
  takes as its own word, `refusalReason`'s `sed` arm serves `tail` too, and the two comments that stated the verb
  allowance now state the file rule (the header's bullet and `inTheTree`'s log clause).
- **`tools/loop/tests/loop-cases.sh`** — one case,
  `policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot`, its row in `LOOP_SHELL_CASES` and its dispatch
  arm: the refusal in both spellings (`-n <N>`, `-N`), its reason and the cap it names, `head -n 20 AGENTS.md` as the
  control that a count excuses the read under no verb, `grep 5 /tmp/build-app.log` as the control that a numeric
  pattern does not swallow the path beside it, and five allowed arms.
- **`tools/loop/prompts/{worker,worker-gate,worker-tail}.md`** — the shell-policy bullet said "a `tail -n` of any
  file" is not refused; it now says a quiet `sed`'s line range and a `tail`'s range over a file of the tree are
  refused with the tree read, and lists the exemptions as before.
- **`guidelines/BuildGate.md`** §2a — the paragraph that owns the rule names `tail`'s range flag beside `sed`'s quiet
  arm, drops `tail -n <N>` from what stands, and cites issues #1006 and #1083.
- **`tools/pi-child-events.sh`** — the adapter's comment over the policy line carries the same sentence.
- **`openspec/specs/agent-issue-loop/spec.md`** — the requirement above and its new scenario, in the shipped spec.

## Spec delta: none, and why

The shipped requirement's text and its new scenario were edited **by the fix commit itself**, inside
`openspec/specs/agent-issue-loop/spec.md`, and not as a change-relative `specs/` delta. Recording the same sentences
a second time as a `MODIFIED`/`ADDED` block would merge nothing new at archive and risk a stale copy of text the
spec already carries, so this change carries no `specs/` directory, declares `skip_specs: true` in its
`.openspec.yaml` — the declaration without which `openspec validate --strict` refuses a change with no delta — and
archives with `--skip-specs`, the shape `openspec/changes/archive/2026-11-24-fix-the-reds-the-main-carried` landed in
(its own `.openspec.yaml` carries the same declaration). The archive is then only the move of this directory and the
record it holds, and the shipped spec is untouched by it.

## Capabilities

`agent-issue-loop` — the requirement "A range read of the tree is refused and a log's is not", its scenario "A
`tail`'s range read of a tracked file is refused and a log's is not", and the case
`policy#aTailRangeReadOfATrackedFileIsRefusedAndALogTailIsNot` that exercises it. No other capability is touched: the
policy is the loop tooling's own and no product requirement reads it.

## Impact

Tooling only — no app, auto or core source, no native source, no manifest, no resource, no string, no Gradle input.
The change is **additive in effect and breaking in nothing**: it narrows one allowance, and every arm the allowance
existed for is asserted to still stand. Files, 8 (`git show --stat dc1fd9da`): `tools/pi-shell-refusal.mjs`,
`tools/loop/tests/loop-cases.sh`, the three `tools/loop/prompts/*.md` skeletons, `guidelines/BuildGate.md`,
`tools/pi-child-events.sh`, `openspec/specs/agent-issue-loop/spec.md` — 99 insertions, 35 deletions, no compiled
file.

**Rollback** is one `git revert`: putting `isRangeRead()` back, with its early return, restores the base's behaviour
exactly, and the new case goes red with it — which is also the falsification of the rule (see `tasks.md`).

## Verification

The case is red on the base and green after (`evidence/red-on-base.log`, `evidence/green.log`); the focused
selections are `policy#*`, `adapter#theChildIsStartedWithTheShellPolicy` and
`driver#everyCaseDeclaresTheFilesItCanReach`, plus `node --check` on the policy and `bash -n` on the adapter and the
case file. The rule's behaviour is re-derived through its CLI in `evidence/measure.log`. No build and no full suite:
no compiled file is touched, so the structural checks of the files edited stand in (`guidelines/Build.md` §2, the
scope block), and the parent gates the branch once. The one red the base carries,
`driver#anOwnedFunctionIsInItsOwnersFile` (two rows: `report.sh defines action_add`, `state.sh defines
cmd_publish_refused`), is red at `ae2373cb` before this change and is untouched by it.
