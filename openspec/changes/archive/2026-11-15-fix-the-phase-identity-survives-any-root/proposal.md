# The phase answer keeps its identity on a root of any depth

## Why

`phase#oneCallPrintsThePhaseState` (`tools/loop/tests/loop-cases.sh:18301`) passed in the project tree and failed in
every worktree the loop ran in (issue #927). The failure was not the case's fixture: on a worktree root 92 characters
long the phase call's own bound landed inside the `PACKAGE:` line — `PACKAGE:
/home/tim/projects/worktrees/NaviVeylin/pi-worktree-2a3ba89` — so `fingerprint 0123456789abcdef`, the first assertion
the case makes about the package, never appeared in the answer.

The cause is what the bound was spent on. `tools/loop/phase.sh` prints one body and keeps its head at
`LOOP_TOOL_RESULT_MAX_CHARS` (`tools/loop/loop.conf:463`, `4000`), by design: the tail it takes is the item's own
text, which a child can re-read at the path the answer names. But the head was dominated by absolute paths — the
`CHECKLIST:` line carrying the prompt's path, the `GATE:` line carrying the item's `events.jsonl` path (about 230
characters), and two `PATHS:` lines. Every character of a root was paid for three times before the package's identity
was reached, so the deeper the tree, the less of the identity survived, and the identity is the half a child cannot
re-derive. Raising the bound is not the repair: the next deeper root walks the same figure again.

The issue's reading has also moved since it was filed: it cites `tools/loop/loop.conf:450` for the bound, and the
figure is at `tools/loop/loop.conf:463` in this tree (`evidence/readings.md`, R1). The measurement the issue reports
is unchanged — the same case, the same cut inside `PACKAGE:`, the same missing fingerprint.

## What Changes

1. **A path as this answer prints it** (`tools/loop/phase.sh:87-92`): `phase_path_from_root` prints a path relative to
   the run root when it lies inside it and leaves a path outside the root in its absolute form, because the `ROOT:`
   line is what makes the relative form resolvable — a path outside it would otherwise become a name nobody can
   follow.
2. **The root once** (`:129`): the answer's second line is the one `ROOT:` line, and every path under it is printed
   relative to it. Nine callers of `phase_path_from_root` follow (`:145`, `:148`, `:150`, `:174`, `:189`, `:191`,
   `:196`, `:207`, `:211`), so a root of any length costs the answer one line instead of one prefix per path.
3. **The order a cut leaves whole** (`:121-224`): phase line (`:123`, `:125`), `ROOT:` (`:129`), item (`:131`,
   `:134`), then the package's identity — `PACKAGE:` with its fingerprint and provenance (`:145`) and `ACCEPTANCE:`
   with the title the package is accepted against (`:148`) — then the tree (`:163`, `:168`, `:170`), the last gate
   verdict (`:188`, `:191`), the paths the phase writes (`:206`, `:207`), the `CHECKLIST:` line with its section
   (`:211-217`) and, last, the item's own text inside its delimiters (`:222`). The cut notice (`:227-235`) names the
   directory the whole package is in, so what the bound takes is a block the reader can re-read by path.
4. **The declaration the portability check reads** (`tools/loop/shared.tsv:79`): `phase.sh` joins the `LOOP_ROOT` row,
   because the script now reads the variable. No configuration value changes: the bound stays `4000`.
5. **The case that owns the cut** (`tools/loop/tests/loop-cases.sh:18461`):
   `phase#theIdentitySurvivesTheBoundOnAnyRoot` pins its own bound (`pinned=2000`, `:18466`) rather than inheriting the
   recorded figure, asks the identity of the suite's own fixture root and of a root over 120 characters long
   (`:18498-18502`), and then asks the recorded bound on the deep root (`:18506-18512`). It also asserts the root is
   printed once (`:18488-18489`). Registry entries at `:454` and `:1195`.
6. **The case that asserts the whole answer** (`:18301`): `phase#oneCallPrintsThePhaseState` now runs with the bound
   off (`export LOOP_TOOL_RESULT_MAX_CHARS=0`, `:18306-18310`), because a cut it did not ask for left its verdict to
   the length of the fixture's root — the defect the new case owns. Its assertions are unchanged.
7. **Two mutation records** (`tools/loop/tests/loop-mutations.sh:1342-1343`): `927.1` prints the checklist where the
   identity was printed, so a cut takes the fingerprint, the provenance and the path that names the package; `927.2`
   prints every path under the root with the root again, so the bound is spent on a prefix the reader already holds.
   Each reddens `phase#theIdentitySurvivesTheBoundOnAnyRoot` and restores green.
8. **The requirement** (`openspec/specs/agent-issue-loop/spec.md:4964`): "A bounded phase answer keeps the state and
   the package's head" gains the printed order and the relative form as its second paragraph, and the case rule that
   a case which asks what a cut keeps pins its bound and its root depth as its third, with two scenarios beside the
   one it already holds. The shipped paragraph and its scenario are carried verbatim.

## Impact

- `tools/loop/phase.sh` — the relative-path helper, the `ROOT:` line and the reordered body. `tools/loop/shared.tsv` —
  one row's file list. `tools/loop/tests/loop-cases.sh` — one new case, one existing case's bound, two registry
  entries. `tools/loop/tests/loop-mutations.sh` — two records.
- `guidelines/Backlog.recipes.md` is untouched by this half (it carries issue #928's reading), and no Android module,
  native boundary or UI surface is involved. Rollback is the revert of the four files; the printed lines a child
  reads are the same facts in a shorter form, so nothing downstream depends on the absolute paths.
- The bound is not widened and no configuration value moves: the repair is the shape of what is printed first, which
  is why a root of any depth costs the same answer.

## Evidence

- The implementation: commit `072ca76c`, merged into the base as `31750871`. The change was written against the
  shipped tree, so the implementation landed first and this change names it.
- Cases: `phase#theIdentitySurvivesTheBoundOnAnyRoot`, `phase#oneCallPrintsThePhaseState`.
- Mutations: `927.1`, `927.2` (in `tools/loop/tests/loop-mutations.sh`, the records the gate's mutation suite runs).
- Readings, anchors and the gate verdict this change can and cannot produce: `evidence/readings.md`.
