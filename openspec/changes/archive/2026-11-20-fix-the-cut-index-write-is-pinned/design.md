# Design — the cut index's write is pinned by the case that names it

Change: `fix-the-cut-index-write-is-pinned`. Spec delta: `agent-issue-loop` (one requirement modified, one scenario
added). Item: #924.

## Root cause

One mechanism, and it is in the case rather than in the engine: `case_prompt_reviewerInputsAreBoundedAndTheCutIsNamed`
(`tools/loop/tests/loop-cases.sh:4822`) stopped executing its assertions at its own `[ -f "$diff" ]` line, so the
existence of the round's cut index, its cap line, its entries and the item's recorded scale were asserted by nothing —
and mutation record `924.2` (`tools/loop/tests/loop-mutations.sh:1370`), which removes the index's write
(`tools/loop/run.sh:1837`), therefore reported *not noticed*.

The two lines that carry it:

1. `:4864` was `[ -f "$diff" ] || fail 'the round's diff was not written'`. The apostrophe in `round's` closes the
   single-quoted string, so the token stream continues unquoted and the next `'` (on `:4865`) opens a string that runs
   over the following lines: from `[ -f "$cut" ]` to `sf=$(state_file "$p")` every line is one argument of the `fail`
   call on `:4864`. Since that call runs only when the diff file is missing — and the stub run always writes it — the
   folded lines never ran as commands at all. The item's own scratch dir carries the extracted body that shows the
   folded list (`.pi/issue-loop/items/924/scratch/region-probe.sh`, in the project tree and not in this worktree);
   `bash -n` over the file is clean, because the quotes balance again before the function ends, which is why no syntax
   check could refuse it.
2. `:4869` (now `:4871`) asserted `'Bounded at 64 bytes a part'` against `$text`, the reviewer's prompt. That sentence
   is written into the index (`tools/loop/run.sh:1826`); the prompt is handed the scale line instead
   (`run.sh:1841`), which says `over the 64 B cap`. So the assertion named the index's own sentence and read the other
   file: closing the fold alone made the case red, on a claim that aimed at the wrong artifact.

## The single fix

- `loop-cases.sh:4864` — the message becomes a double-quoted string, so the apostrophe is a letter and the block below
  it is commands again.
- `loop-cases.sh:4871` — the cap assertion reads the file the sentence lives in: `assert_contains "$(cat "$cut")"
  'Bounded at 64 bytes a part'`, with a two-line comment saying why the index is the artifact that states the figure.
- `loop-mutations.sh:1370` — record `924.2` (written by the item's first process) stays, and its comment
  (`:1361`–`:1368`) now names both mechanisms and the two measurements rather than the open question.

No line of `tools/loop/run.sh` moves. The bound, the head, the index and the prompt are the tree's as they were; what
changes is that the case can fail on them.

## Blast radius

- `tools/loop/tests/loop-cases.sh` — one case body, two lines and one comment. The case's `# files:` declaration is
  unchanged, because the hunk reaches no path the body did not already declare.
- `tools/loop/tests/loop-mutations.sh` — one record added inside the `MUTATIONS=( … )` list plus its comment; the list
  is read by three cases, all green: `mutation#everyDeclaredRecordApplies`, `mutation#aStaleRecordIsNamed`,
  `mutation#theFileSetLegScopesByTheCheapestFile`.
- No engine file, no compiled file, no fixture, no prompt, no configuration. `tools/check-portability.sh` is owed
  nothing: no path is added to a tool.

## The owning guideline section

`guidelines/BuildEvidence.md` §4e "Falsification, flakes and scheduling traps" — *a new regression test must fail
against the unfixed revision first; one that passes against the bug is worse than none* — and §4f, *one mutation per
revert check*. Both already state the rule, so this change owes no guideline edit and adds none: what it adds is the
tree in which the rule holds for this case, which §4e asks of the change and not of the document. The routing row is
`AGENTS.md`'s build/evidence row, and the two sections were read there, not in full.

## Rollback

Revert the two lines in `loop-cases.sh` and delete the `924.2` record with its comment. The tree returns to the base,
with the index's write unpinned again and `924.2` un-noticed — the state the item describes, and no other behaviour
moves either way.

## Alternatives the evidence eliminated

- **A second case beside the first** (the item's wording, "one case beside `tools/loop/tests/loop-cases.sh:4696`"). The
  assertions the item asks for — the index path handed to the child, the head's bytes under the bound, the index beside
  the diff, the index's content — are already written in this body. A second case would be a second reading of the same
  scenario, and it would inherit the same fold if its messages were written the same way; the evidence that it was not
  worth a case is that a case already said all of it and nothing failed. Repairing the body costs two lines.
- **Asserting the index's byte size under the cap** instead of its existence and content. The index is not a part the
  cap bounds — it is the list of what the cap left out — so a cap on it would be a second bound nothing in the design
  asks for. What the item's "byte size under the bound" names is the head's size, and `:4876` asserts it (`< 1000`
  bytes for a 64-byte cap).
- **A driver-level refusal of the folded shape** — a check that no case body's quoted message folds the lines under it.
  It is the general guard this item's defect argues for, and it is a case in `tools/loop/tests/loop-selftest.sh`'s
  driver group, outside this change's bounded files. Named as a `NOTE:` in the report; not built here.

## Open decisions

none
