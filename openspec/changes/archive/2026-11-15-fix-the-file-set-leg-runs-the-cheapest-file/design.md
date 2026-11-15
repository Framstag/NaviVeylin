# Design — the file-set leg runs the cheapest file's records

## The defect, in one mechanism

`tools/loop/tests/loop-mutations-selftest.sh` proves #235's bound with a real file set: leg 7 derives the records the
file `scoped_file` names out of the declaration (`:141-143`), lists them (`:145`), runs them through an engine copy
(`:152`) and asserts the four facts the leg exists for — the selection is the owned set, the exit is 0, the count line
names the declared set, and the copy is byte-identical afterwards (`:154-159`). `scoped_file` is a constant
(`:140`), and it is `tools/loop/state.sh`, which owns **39 of the 401** declared records
(`evidence/per-file-counts.log`). Each record is run twice by the harness — the mutation and the restore — so the leg
pays 78 case runs under `timeout 900` and the bound is spent before the selection finishes: exit 124, a partial count
and a red step for every delta under `tools/loop/*`.

The constant is the whole defect. Nothing in the leg reads the size of what it scoped by: the four assertions compare
against `$owned_count`, which is derived from the declaration at `:143`, so the leg asserts exactly the same four
facts whichever file it names.

## The fix, and only that

Two hunks in the self-test file and one case:

- `tools/loop/tests/loop-mutations-selftest.sh:140` — `scoped_file="tools/loop/phase.sh"`, the file the declaration
  gives the fewest records to among the files that own at least two (`2 tools/loop/phase.sh`, and the tie with this
  change's own record on the self-test is broken by the path sorting first; `3 ../look.sh` and `3 tools/loop/labels.sh`
  come next, `evidence/per-file-counts.log`). A set of one would not be a set, which is why the pick is not simply the
  cheapest owner: 8 of the 28 files the declaration names own one record each.
- the leg's comment above it says which rule the pick follows, that the pick is held to the declaration by a case, and
  what the leg cost before it (`issue #934`).
- `tools/loop/tests/loop-cases.sh` — the case `mutation#theFileSetLegScopesByTheCheapestFile` reads `scoped_file` out
  of the self-test, counts the declaration per file the same way the leg does (`awk -F'@'` over the `MUTATIONS=(`
  block), and asserts the pick is the cheapest file owning at least two. It also refuses, with its own message, a pick
  that the declaration gives no two records to at all — the shape a record moving off the file leaves behind.

The choice stays a constant rather than a pipeline evaluated inside the leg: a value the leg computes is a value no
case can read, so the rule would be asserted by nothing. Held this way, the constant is checked against the
declaration on every gate run, and a record that moves off `tools/loop/phase.sh` reds the case and names the file to
point the leg at — the shape leg 6 already has for `declared` (`:132-135`).

## Blast radius

`tools/loop/tests/loop-mutations-selftest.sh` (one value, its comment), `tools/loop/tests/loop-cases.sh` (the case,
its `LOOP_SHELL_CASES` entry and its dispatcher arm), `tools/loop/tests/loop-mutations.sh` (records `934.1` and
`934.2`), the delta spec and this change. The declared case count goes from 460 to 461 and the declared mutation set
from 401 records to 403; both are the case's own additions. No engine script, no `loop.conf` key, no prompt and no
guideline moves: the step that owes the self-test is the same entry and the same command.

## Rollback

`git revert` of the one commit: the four hunks are independent of every other file, and the leg's own assertions were
not touched, so a revert restores the base's leg exactly — 39 records and the 900 s bound it spends — with nothing
else to undo.

## The alternatives the evidence eliminated

- **Keep the 39-record set and price `timeout 900` off the harness's per-record wall.** Eliminated: the leg's bound is
  not what is wrong, the selection is. A derived bound would still pay every record twice on every delta (the item
  measured the same selection at 9 min 04 s and about 15 min on one host, so the figure it derived would be a
  statement about the host's load), and it would buy no assertion the leg does not already make.
- **Drop leg 9's and leg 10's use of `$scoped_file` along with the pick.** Eliminated: those two legs use the name to
  prove their own facts (a `--from-git` selection, and the refusal of a copy with no sibling gate-log reader), and each
  is as cheap under `tools/loop/phase.sh` as it was under `tools/loop/state.sh` — leg 9 builds the file in a synthetic
  repository and leg 10 refuses before any record runs.
- **Make the leg list the set instead of running it.** Eliminated: the leg is the only case that runs a file set
  through an engine copy, and a listing-only leg would assert the selection while the refusal-by-doing-nothing it
  exists for went unproven (`:154-159`).
- **Derive the pick inside the self-test.** Eliminated: a computed value cannot be read by a case, so the rule would be
  asserted by nothing — the class of check the item was filed against (a check the harness prints and no driver case
  resolves).
