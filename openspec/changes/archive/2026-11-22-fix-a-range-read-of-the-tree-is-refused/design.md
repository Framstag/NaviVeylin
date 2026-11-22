# Design — one arm of `sed`, and the file the exemption is about

## The root cause

The policy refuses a *shell search of the tree* by classifying the path a segment names: `refusesSegment`
(`tools/pi-shell-refusal.mjs:186` at the base commit) returns false for any verb `REFUSED_VERBS` does not carry, and
`sed` was deliberately not in that list (`tools/pi-shell-refusal.mjs:50`) because `sed -n 'N,Mp'` is how
`guidelines/Build.md` §10 reads a build log back by range. That exemption is about **which file** the range is taken
from, and the list it was expressed in never asks: the path classification the module already owns is reached only
after the verb test, so a range read of a tracked suite and a range read of `/tmp/build-app.log` were one command and
both stood.

The cost is the item's own measurement (issue #1006): 3 545 `sed` calls over `.pi/issue-loop/items` (33.7 % of 10 506
shell calls) for 10.78 M characters at 3 041 per call, against `ctx_read`'s 6 284 calls at 1.5 k, with the
`sed -> sed` pair the archive's top adjacent pair in 46 of 47 items. The `grep` counter-experiment is the same file:
4 997 calls before #687 refused it and 1 718 after.

## The single fix

Three clauses in `tools/pi-shell-refusal.mjs`, and the surfaces that assert the rule follow the module:

1. `REFUSED_VERBS` carries `sed`, in the policy's own spelling, so the prompt's block and the case that reads the
   export (`prompt#thePhasePromptNamesWhatItsShellRefuses`) see it the way they see every other verb.
2. `isRangePrint` is the one arm that reads a range: a quiet flag (`-n`, `-rn`, `--quiet`) **and** a script that is a
   line range (`100,130p`, `1,20p`). A `sed -n 's/x/y/p'`, a `sed -e`, a `sed -i` and a plain `sed 's/x/y/'` are
   filters and writes, whose output is not a range and which keep standing — the same way `awk`'s pipe arms stand.
3. `refusesSegment` reads `sed`'s first operand as its **script**, not a path (as it already reads `grep`'s first
   operand as the pattern), and a `sed` that names no path at all reads the pipe it stands in, so it is allowed. The
   path classification is then the module's own: `inTheTree` puts `/tmp`, a generated tree, a submodule and an item
   artefact outside the tree, and this change adds nothing to it — except the one relative token that names no file of
   the tree because the child already stands inside its item directory (`inTheTree`'s `ITEM_ARTEFACT` clause).

`refusalReason`'s `sed` arm names `ctx_read path=<path> offset=<N> limit=<M>` and states the cap that call stops at
(about 2 000 lines or 50 kB). A range wider than the cap is the shell's, and a refusal that pretended otherwise would
cost the child the turn it just spent.

## Alternatives the evidence eliminated

- **Refusing every `sed`.** `.pi/skills/update-to-current-libosmscout-master/SKILL.md:185` filters a Java source with
  `sed -n 's/…/\1/p'`, and a substitution prints the matches it prints, not a range: a policy that refuses it spends a
  turn to save nothing. Narrowing to the range script is what keeps that recipe alive.
- **Moving `tail` in the same change.** `tail -n <N> <file>` is the second half of the base's exemption and it is a
  separate verb with its own measurement; the item's own bound says a wrong classification costs more than the call it
  saves, so this change leaves it and says so in the block.
- **Refusing by path alone, naming no tool.** The refusal is the only place the alternative can be handed to a child,
  and the item's fix names the call and its bound.
- **A prompt that stops offering `sed`.** The `grep` fall in the same archive (4 997 → 1 718 once #687 refused it) is
  the measurement against it: the mix moves where the policy is, not where a prompt asks.

## Blast radius

`tools/pi-shell-refusal.mjs` (the verb list, one export, two clauses of `refusesSegment`, one arm of
`refusalReason`), `tools/loop/tests/loop-cases.sh` (one case, its row and its dispatch arm), the three skeletons
`tools/loop/prompts/{worker,worker-gate,worker-tail}.md` (the verb list and the sentence that stated the exemption),
`guidelines/BuildGate.md` §2a (the paragraph that owns the rule), the adapter's comment in
`tools/pi-child-events.sh:68-70` and the two `AGENTS.md` routing rows that state §2a's cost (`1.4–7.7 kB` → `1.4–7.9 kB`,
because the check compares a row's figure with the section's bytes). No compiled file and no profile key: the gate is
the loop's own suites over the module and its cases.

## Rollback

Reverting the eight files restores the base's policy exactly: `sed` out of the verb list, no `isRangePrint`, the block's
old sentence and the guideline's old pair. The case goes red against that tree on its first assertion (`exit 3`, and
`ALLOWED` printed instead), which is the signal the arm is gone; every other case stands, because the other verbs'
classification is untouched.

## Verification

- `policy#aRangeReadOfATrackedFileIsRefusedAndALogIsNot` green with the change.
- The case the delta reaches through the export: `prompt#thePhasePromptNamesWhatItsShellRefuses`, red before the
  block's verb list gained `sed` and green after it.
- The structural checks the delta owes: `bash -n` of `tools/pi-child-events.sh`, `bash -n` of
  `tools/loop/tests/loop-cases.sh`, `node --check` of `tools/pi-shell-refusal.mjs` and `tools/check-doc-values.sh`.
- The gate is not this session's: the change is left unarchived, and the gate child completes and measures it.

## Open decisions

None. The item's class (`improvement`) settles the shape — the policy gains one arm and the surfaces that assert the
rule follow it — and the two arms the item says must not move (an item artefact, a log outside the tree) are recorded
in the delta's requirement, not chosen here.
