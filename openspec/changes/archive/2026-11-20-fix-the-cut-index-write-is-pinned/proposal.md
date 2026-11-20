# Proposal — the cut index's write is pinned by the case that names it

## Why

**Root cause.** The one case that names the bounded review round reached its index assertions never: the message
beside its `[ -f "$diff" ]` check read `fail 'the round's diff was not written'`, whose apostrophe closes the
quoted string, so the shell folded every line from the `[ -f "$cut" ]` existence check to the state assertions into
that one command's argument list — and because the check that guarded the call is the one line the run passes, the
folded list was never executed either; one line further, the assertion that names the cap read the prompt for a
sentence only the index carries (`tools/loop/run.sh:1826` writes it into the index, `:1841` into the prompt), so even
a body that ran would have failed on a claim it aimed at the wrong file.

**The arm the item was filed on.** Mutation record `924.2` redirects the index's own write
(`tools/loop/run.sh:1837`, `} > "$cut_file"` → `} > /dev/null`). Over the case as the base carried it, the harness
reported it unnoticed:

```
GREEN  924.2  prompt#reviewerInputsAreBoundedAndTheCutIsNamed   <-- the case did not notice the mutation
red-when-mutated: 0 · not-noticed: 1 · restore-failures: 0
```

(`evidence/red-924.2-on-base-case.log`, taken on the run's base `fcdc7ea8` with the tree's own case text restored
under the record.) So a regression that stops writing the round's index, or writes it nowhere the round can read,
left the suite green and handed the reviewer a prompt pointing at a file that was never written — the input
`290c3d8c` was spent to remove.

**What the item's own record said.** The change `bound-the-review-rounds-input-and-name-the-cut` carried the arm as
`924.2` in its case table and recorded that it was **not** carried: one mutation record exists (`924.1`, the cap
removed) and the index half was pinned by no assertion anything could fail. That table entry is what this change
retires.

**The single fix.** Two lines of `tools/loop/tests/loop-cases.sh`:

- `:4864` — the message is a double-quoted string, so the apostrophe in `round's` is a letter and the block's lines
  are commands again: the existence check on the index, the head marker, the cap line and the state record all
  execute.
- `:4871` — the cap-figure assertion reads the index it is about, not the prompt: `assert_contains "$(cat "$cut")"
  'Bounded at 64 bytes a part'`, the sentence `run.sh:1826` writes into `review-1.cut.md`.

No engine line moves: the write, the index's content and the prompt's bound section are the tree's as they were, and
what changes is that the case can now fail on them.

**Falsification, after.** The same record, over the repaired case:

```
RED    924.1  prompt#reviewerInputsAreBoundedAndTheCutIsNamed   (the cap itself, …)
RED    924.2  prompt#reviewerInputsAreBoundedAndTheCutIsNamed   (the index the round reads by path, …)
restore 924.1 … green again
restore 924.2 … green again
red-when-mutated: 2 · not-noticed: 0 · restore-failures: 0
```

(`evidence/red-924.2-after-fix.log`.) The focused case is green on the tree it names
(`evidence/focused-case-after-fix.log`), the harness's own three cases are green (`evidence/mutation-cases.log`), and
every one of the 429 declared records still applies to the file it names (`evidence/mutation-check-all.log`).

**The owning rule.** `guidelines/BuildEvidence.md` §4e "Falsification, flakes and scheduling traps" — *a new
regression test must fail against the unfixed revision first; one that passes against the bug is worse than none* —
and §4f, *one mutation per revert check*. The section already states the rule, so this change adds no guideline text:
what it adds is the tree in which the rule holds for this case.

surface: 1 case repaired (2 lines of `tools/loop/tests/loop-cases.sh`, +1 comment), 1 mutation record added
(`924.2`), 1 requirement delta (1 requirement modified, 1 scenario added) — `tools/look.sh
'reviewerInputsAreBoundedAndTheCutIsNamed' --path tools/` → `LOOK: 5 matches in 2 files`, `loop-cases.sh` 3 (the
case list, the dispatcher row, the body) and `loop-mutations.sh` 2 (the two records); no engine path moves, so no
other case is owed but the three that declare `loop-mutations.sh`.

## What is deliberately not here

A driver-level refusal of the *shape* — a case body whose quoted message folds the lines under it — is the guard
that would have caught this before an item was spent on it. It is a case in the driver, not a repair of this case,
and it is left out: this item's bounded files are the case and its record. It is named in the report as a `NOTE:`
so the reviewer meets it already named rather than paying a round to find it.
