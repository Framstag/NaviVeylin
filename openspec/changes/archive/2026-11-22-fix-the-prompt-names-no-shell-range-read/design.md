# Design: fix-the-prompt-names-no-shell-range-read

## Root cause

One sentence in the block that states what a child's shell refuses hands the refused search's alternative back in the
shell it was just refused from, and the notice a cut result carries repeats the same string — so the child's cheapest
read of its own prompt is the shell call the block spent four bullets refusing.

## The single fix

The range is named as `ctx_read`'s own call in both places, and nowhere else:

- the refusal bullet's carve-out (`worker.md:76-77`, and its two copies in `worker-gate.md:68` and
  `worker-tail.md:72`) loses `sed -n 'N,Mp'` and `tail -n`, and the tool list it hands the child says the range is
  `ctx_read`'s own `offset` and `limit`;
- `trimNotice` (`tools/pi-trim-result.mjs:101-104`) emits `ranges (ctx_read mode="lines:N-M")`, and the four skeletons
  that quote the notice — `worker.md:124`, `worker-gate.md:44`, `worker-tail.md:48`, `reviewer.md:35` — quote that
  string, so the prompt and the notice a child actually reads are one string.

No second option: the item's own bound says this is the advice half of a pair (issue #1006 owns the refusal policy,
#1005 the wrapper's `--section`), and there is no trade-off to decide — a prompt that names one call instead of two is
either the string the module emits or it is not.

## Blast radius

- `tools/pi-trim-result.mjs` — `trimNotice` only. `repeatNotice`/`repeatResult` already carry
  `ctx_read mode="lines:N-M"`, so the two notices converge and no other export moves.
- `tools/loop/prompts/` — four skeletons. `gate-own.md`, `gate-deferred.md`, `pre-gate-review.md` and
  `worker-continuation.md` carry neither sentence and are untouched.
- `tools/loop/tests/loop-cases.sh` — one case extended, one rewritten. `repeat#aRepeatReadIsNamedNotHandedOverAgain`
  already asserts `ctx_read mode="lines:N-M"` and stays as it is.
- `tools/loop/tests/loop-mutations.sh` — four records: the alternative back in `worker.md`'s bullet, back in
  `trimNotice`, back in the reviewer's quote of the notice, and back in the guideline sentence the bullet renders.
- `guidelines/BuildGate.md` §2, "answer a tree question with one call" — the rule the block renders. Its sentence
  named `sed -n 'N,Mp'` and `tail -n` as the range answer; it now names `ctx_read`'s own `offset` and `limit`, and
  keeps the true half — neither verb is refused, because §10 of `Build.md` reads a build's log with them — as
  "not how a tree question is answered". `AGENTS.md`'s two routing rows for `BuildGate.md` §2's sections carry the
  section's measured size, so both figures move with the rule (1.4–7.4 kB → 1.4–7.7 kB).

## What the diff does not touch, and why

- **`tools/pi-shell-refusal.mjs`** is not edited: `sed` is deliberately absent from `REFUSED_VERBS`
  (`tools/pi-shell-refusal.mjs:49-50`) and `tail` with a range flag is allowed on purpose, so the `sed -n` recipes in
  `guidelines/Build.md` §10 keep working. Making it refused is issue #1006's change, not this one. The refusal
  message the module returns (`:207-210`) names the verbs it did *not* refuse because it answers a call that has
  already been attempted — it is the record of what is permitted, not the prompt's advice about how to read a tree,
  and it is left standing with `sed`: a child refused a `grep` over the tree is told the truth about what remains
  allowed, while the block and the guideline it renders say what a range of a known file is read with.
- **`guidelines/Backlog.recipes.md`, the loop's procedure section 7a,** owns that procedure and quotes the notice elided —
  `[LOOP-TRIMMED: … read the rest in ranges …]` (`guidelines/Backlog.recipes.md:253`) — so it names no call the fix
  changes and owes no edit. It also states "the call and arguments that page the rest until the last line", which is
  `ctx_read`'s `offset` and `limit`, so the section already says what the prompt now says.

## Alternatives the evidence eliminated

- **Refusing `sed` here** — eliminated: it is a policy change (`tools/pi-shell-refusal.mjs`, its verb list, its case,
  its mutations and the recipes in `guidelines/Build.md` §10 that use it), it is filed as #1006, and the item states
  the pair must not be worked as this half alone.
- **Adding a sentence asking the child to prefer `ctx_read`** — eliminated: the prompt has asked for a section read
  since #246 and the archive shows the mix moving only where a policy landed (`grep` 4 997 → 1 718 after #687). A
  second sentence beside the alternative is the shape that failed.
- **Removing the notice's "how to read the rest" arm** — eliminated: the requirement "A child's tool results are
  bounded and a trim is named" makes the notice name how to read the rest, and `trim#aResultAboveTheBoundIsTrimmedAndNamed`
  holds it.

## Rollback

Revert the commit: four prompt skeletons and one exported function, no engine path, no profile value, no schema.

## Verification

- the red case on base `5e77d751`: `prompt#thePhasePromptNamesWhatItsShellRefuses` fails with
  `'sed -n' should not appear in: # Worker — one item, phases A–D` (`evidence/red-on-HEAD-1007.log`).
- the same case with the fix in place, `PASS prompt#thePhasePromptNamesWhatItsShellRefuses` (`evidence/green.log`), and
  `trim#aResultAboveTheBoundIsTrimmedAndNamed` rewritten to the call the notice now names
  (`evidence/trim-green.log`).
- `bash tools/loop/tests/loop-mutations.sh --check --files tools/loop/prompts/worker.md --files
  tools/loop/prompts/reviewer.md --files tools/pi-trim-result.mjs` — `selected 13 of 445 records`, `all 13 cases passed`,
  so each of the three new records applies to the copy of the file it names (`evidence/mutations-check.log`).
- `driver#everyCaseDeclaresTheFilesItCanReach` — the extended case reads `tools/pi-trim-result.mjs` and
  `guidelines/BuildGate.md`, so its declaration carries both paths (the gate's own first check for a delta that
  writes a case; `evidence/declaration.log`).
- `bash tools/loop/tests/loop-mutations.sh --only "1007.1 1007.2 1007.3 1007.4"` — `red-when-mutated: 4 ·
  not-noticed: 0 · restore-failures: 0`, so each of the four records reddens the case and its restore goes green
  (`evidence/mutations-red.log`).
- `tools/check-doc-routes.sh` — `all 357 section references resolve`, the figure the two `AGENTS.md` rows state for
  `BuildGate.md` §2's sections matching the measurement the guideline edit moved (`evidence/structural-checks.log`).
