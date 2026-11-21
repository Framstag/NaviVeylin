# Proposal: fix-a-stop-reads-the-child-it-names

## Why

**Root cause.** `item_outside_write_clause` (`tools/loop/evidence.sh:419`) answers its clause from the first reading
of a fixed order — `worker_write_note` (the change child's), then the gate child's, then the tail child's — and the
run's own "the child could not start" stop (`tools/loop/run.sh:1962`) appends that clause to a sentence that names one
child explicitly: `the child could not start — the $last_child exited …`. When the child the sentence names is the gate
child or the tail worker, the clause is still the change child's reading, so the reason says "the run nevertheless
recorded a difference in the project's own tree while that child ran" over a reading taken around a child the sentence
does not speak of, and prints that other child's paths.

**Evidence** ⊨ (verified in the change root at base `50b05f46`):

- The two notes the arm can read are taken where the change child ends (`tools/loop/run.sh:1298`,
  `worker_write_note`) and where the gate child ends (`tools/loop/run.sh:1724`, `gate_write_note`), and
  `outside_write_note` is re-set by every `run_child` (`tools/loop/child.sh:627`), so it is the last child's own.
- The arm is `tools/loop/run.sh:1952-1965`: it fires only over `head_now == base`, no verdict claim and a spent retry
  budget, which is why it carried no coverage — it is the one site where "that child" can be the wrong child
  (`openspec/changes/archive/2026-11-21-fix-an-empty-tree-names-the-outside-write/evidence/review-round-1-report.md:200-210`,
  finding 5 of #983).
- Red on HEAD, first case (`evidence/red-on-HEAD-991.log`): the stop names `the gate worker` and the clause prints the
  change child's `demo-owned.txt` instead of the gate child's own path.
- Red on HEAD, second case: a clause is written at all where the child the stop names wrote nothing outside its tree.

**Consequence** ⊨: a run's own stop is the record a person reads to learn that an item was not worked and where its
work went (#983's own subject). Under this arm it names the wrong child's paths — the gate child is credited with what
the change child wrote, and whatever the gate child did write is named nowhere.

## What changes

- `tools/loop/evidence.sh`: `item_outside_write_clause` takes the reading of the child a reason names. An argument is
  read as that reading and nothing else; an empty argument means the child the sentence names wrote nothing, which is
  no clause at all. No argument keeps today's order, which is what the two arms that name no child read.
- `tools/loop/run.sh:1962`: the stop hands it `"${outside_write_note:-}"`, the last `run_child`'s own reading.
- `tools/loop/tests/loop-cases.sh`: `run#aStopThatNamesAChildReadsThatChildsOwnWrite` and
  `run#aStopWhoseChildWroteNothingGetsNoClause` — one per half of the rule.
- `tools/loop/tests/loop-mutations.sh`: records `991.1` (drops the argument) and `991.2` (makes a passed reading fall
  back to the item's other readings).
- `guidelines/Backlog.recipes.md:1225` "Refusals, records and the landing": the paragraph that owns the clause
  states which reading each site reads.
- `openspec/specs/agent-issue-loop/spec.md`: the new requirement "A reason that names one child carries that
  child's reading" states the rule the code now implements, one scenario per case, and #983's requirement for an empty
  tree is left as it stands.

surface: 2 cases added, 2 mutations added, 1 requirement modified, 1 guideline section amended, 0 fixtures —
`tools/look.sh 'item_outside_write_clause'` (25 matches in 15 files) and `tools/look.sh "caller's tree"` (54 matches
in 16 files), both taken on this tree.

## What does not change

The arm, its sentence, its retry budget and the verdict it writes; the clause's wording; the two arms that name no
child; and any item's verdict — this is a sentence, not evidence.
