# Readings — the phase answer keeps its identity on any root

Every anchor below was taken from this tree, on the branch that carries commit `072ca76c` (merge `31750871`). The
change was written after the implementation landed, so these are readings of shipped code and of the shipped cases,
not a plan.

## R1 — the bound

- `tools/loop/loop.conf:463` records `LOOP_TOOL_RESULT_MAX_CHARS=4000` — unchanged by this change.
- The issue cites `tools/loop/loop.conf:450` for the same figure, which is the line the file held when the issue was
  filed; the figure moved down the file as the profile grew. The value is the same and this change touches neither.

## R2 — the order the answer is printed in

`tools/loop/phase.sh`, the body the call prints (`:121-224`), in the order the code writes it:

| line | what it prints |
|---|---|
| `:123`, `:125` | `LOOP-PHASE:` with the heading the phase's own checklist carries, or the phase alone when its prompt has no section |
| `:129` | the one `ROOT:` line, and the sentence that a path under it is printed relative to it |
| `:131`, `:134` | `ITEM:` with the issue, the change, the phase the state recorded and the base, or that no item is in progress |
| `:145`, `:148` | `PACKAGE:` with the path, the fingerprint and the provenance, then `ACCEPTANCE:` with the title |
| `:150` | `PACKAGE: none —` with the path the run would have written, when there is no package |
| `:163`, `:168`, `:170` | `GIT:` with branch, head, base, ahead and behind; `DIRTY:` with the count and up to eight paths |
| `:188`, `:191` | `GATE:` the last verdict the item's stream records, or that it records none yet |
| `:206`, `:207` | `PATHS:` the write surface of the phase, and the evidence directory for every phase |
| `:211` | `CHECKLIST:` the prompt whose section is printed below it |
| `:213`, `:215` | the phase's section, or the sentence that the prompt carries none for it |
| `:222` | the item's own text, between its delimiters |

The cut (`:226-235`) keeps this body's head and appends the `[PHASE-BOUND: cut at <n> characters … ]` notice, whose
`:230` line names the directory the whole package is in.

## R3 — the relative form

- `tools/loop/phase.sh:87-92` — `phase_path_from_root`: a path under `LOOP_ROOT` is printed without that prefix, a path
  outside it is printed as it stands. The comment at `:84-86` states the reason: the `ROOT:` line is what makes the
  relative form resolvable, so an absolute path is kept for anything that root does not cover.
- The ten call sites: `:145`, `:148`, `:150`, `:174`, `:189`, `:191`, `:196`, `:207`, `:211`, `:228`.
- `tools/loop/shared.tsv:79` — the `LOOP_ROOT` row now lists `phase.sh`, so the portability check has the declaration
  it reads (`tools/check-portability.sh`, verdict in `structural-checks.txt`).

## R4 — the case that owns the cut

`tools/loop/tests/loop-cases.sh:18461` — `case_phase_theIdentitySurvivesTheBoundOnAnyRoot`:

- `:18466` pins its own bound (`pinned=2000`) instead of reading the recorded figure, so the case's verdict is not a
  function of a configuration value it did not set.
- `:18475-18476` asserts the answer's size against that bound; `:18477` asserts it says it was cut.
- `:18478-18480` assert the identity a cut must not take: the fingerprint, the provenance, the acceptance title.
- `:18481` asserts the package's path in its relative form.
- `:18482-18484` assert the last gate verdict and the stream path it names, relative to the root.
- `:18485` asserts the evidence directory, relative to the root.
- `:18486-18487` assert the one `ROOT:` line; `:18488-18489` assert that exactly one line of the answer carries the
  root at all.
- `:18493-18494` ask it on the suite's own fixture root; `:18498-18502` on a root over 120 characters long;
  `:18506-18512` at the recorded bound on that long root.

## R5 — the case that asserts the whole answer

`tools/loop/tests/loop-cases.sh:18301` — `case_phase_oneCallPrintsThePhaseState`:

- `:18306-18310` state why the case runs with the bound off and export `LOOP_TOOL_RESULT_MAX_CHARS=0` for its own
  invocations. A cut this case did not ask for is the defect the case above owns.
- `:18314-18337` assert the answer's content: the phase with its heading, the item and the change, the recorded phase,
  the checklist's own items and no other phase's, the tree's branch, head, ahead/behind and dirty paths, the last gate
  verdict and not an earlier one, the fingerprint, the provenance, the item's text inside its delimiters, and the write
  surface of the phase.
- `:18339-18379` assert the same for phases C, F, E, a phase whose prompt carries no section, a phase outside the set
  and a run that wrote no state.

## R6 — the falsification the suite holds

`tools/loop/tests/loop-mutations.sh`, the two records this half adds, verbatim:

```
'927.1@-@tools/loop/phase.sh@phase#theIdentitySurvivesTheBoundOnAnyRoot@the order that prints the package'"'"'s identity before the checklist, so a cut takes the fingerprint and the path that names the package and the answer names no work package (issue #927)@s|^  printf '"'"'ROOT: %s · a path under it is printed relative to it\\n'"'"' "\$LOOP_ROOT"$|  printf '"'"'%s\\n'"'"' "$section"|'
'927.2@-@tools/loop/phase.sh@phase#theIdentitySurvivesTheBoundOnAnyRoot@the relative form of a path under the root, so every path carries the root again and the bound is spent on a prefix the reader already holds (issue #927)@s|\${1#"$LOOP_ROOT"/}|$1|'
```

Both name `phase#theIdentitySurvivesTheBoundOnAnyRoot` as the case they must red — one record per half, the order and
the relative form — and the suite's `--check` mode reads the same records against the tree.

## R7 — the pre-fix red, where it is

- The issue's record: `gh issue view 927`, whose observed section quotes the failure as
  `fingerprint 0123456789abcdef' not found in: …` and pointed at a `/tmp` log of the case's output at the cut — that
  pointer is not retained, so this change cannot resolve it and does not claim the log.
- The same case was reported red on two independent children's HEAD worktrees (#921, #923) and green in the project
  tree, which is what tells a root-length defect apart from a change's own regression.

## R8 — the gate verdict

The gate that landed `072ca76c` is the loop's own run. Its tally is **not retained** in this checkout: the run record
is gitignored and machine-local, and this change was written after the run had ended. Nothing here quotes a figure it
cannot show — `tools/loop/evidence-check.sh`'s C2 refuses exactly that — and the two case bodies, the two mutation
records and the printed order above are the readings a reader can re-take.
