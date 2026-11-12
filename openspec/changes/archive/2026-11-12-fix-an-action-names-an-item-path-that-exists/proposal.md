# An action names the item path the tree holds, from either state

## Why

`tools/loop/report.sh:631` reads the directory the state file sits in — `state_dir=$(dirname "$state_file")` — and the
two arms that print an item path build it as `$state_dir/items/$issue` (`report.sh:687` for an item whose gate never
completed, `report.sh:701` for an item a bound cut). That expression is right for one of the two shapes the state takes
and wrong for the other:

- the state in place is `<project>/.pi/issue-loop/run.json`, so `$state_dir/items/<n>` is
  `<project>/.pi/issue-loop/items/<n>` and resolves;
- the archived state is `<project>/.pi/issue-loop/runs/<stamp>.json` — the shape `--state runs/<stamp>.json` exists for
  (`report.sh:39`, `report.sh:74`), and the only shape a *previous* run's report can be printed from, because
  `state.sh init` replaces the state in place and keeps the one it replaced under `runs/` (`state.sh:183-199`,
  `keep_replaced_state`, whose `RUNS_DIR` is `<state dir>/runs` at `state.sh:109`) — so the same
  expression is `<project>/.pi/issue-loop/runs/items/<n>`, and the item directories never moved under `runs/`.

Measured on this tree, `2026-11-12`: `tools/loop/report.sh --state
.pi/issue-loop/runs/2026-11-11T11:37:08Z.json` prints 15 actions, and 2 of them — the `resume #847` and `resume #839`
lines — end `transcript /home/tim/projects/NaviVeylin/.pi/issue-loop/runs/items/847` and `…/runs/items/839`;
`.pi/issue-loop/runs/items/` does not exist, while `.pi/issue-loop/items/847/` holds the item's 34 files.

**Root cause, in one sentence:** the item root is derived from the directory the state file happens to sit in instead of
from the loop root that directory belongs to, so the one derivation that holds for the live state names a directory the
tree does not have once the state is the archived one.

**The second defect on the same two lines.** The resume line appends `${patch:+ · its work is kept at $patch}`
(`report.sh:701`) to a note `run.sh:3433` has already appended that same sentence to, so each of the two measured lines
prints `its work is kept at <patch>` twice — the duplication is visible in the same two actions above.

**The red test.** `report#anActionNamesAnItemPathThatExists`, in the script of `report#archivedStateIsReported`
(`loop-cases.sh:11990` — the case that already reports on an archived state) and the state shapes
`report#aCutItemIsNamedOnceAndItsKeptWorkWithIt` (`loop-cases.sh:7530`) writes: a run whose item 22 never completed a
gate and whose item 21 was cut at a bound with a kept patch, the item directories present where the tree keeps them,
and the report printed twice — from the state in place and from the state a later `init` archived under `runs/`. It
asserts that both lines' item paths exist in both reports and that the kept patch is named once. On HEAD `ff3a402a` it
fails first on the in-place report: the duplicated `its work is kept at` sentence, `expected '1', got '2'`
(`evidence/red-on-HEAD.log`, `tests=1 failures=1`, 2026-11-12T14:55:37Z) — the archived branch never ran there. The
archived-path red — `…/runs/items/21` does not exist — is carried by mutation 277.1's message
(`evidence/mutations-277.log`, 2026-11-12T14:56:41Z) and by the worker's pre-fix run (events, actions 13/14 naming
`…/runs/items/847`).

**Surface**, counted before the plan was fixed:

- `./tools/look.sh 'state_dir/items'` — 2 matches in `tools/loop/report.sh` (`:687`, `:701`, HEAD's numbering; the
  two arms this change rewrites), and 1 in an archived change's `evidence/red-on-head.log` (evidence, not code).
- `./tools/look.sh 'its work is kept at'` — `tools/loop/report.sh:701` (the appended phrase; `report.sh:713` is the
  recovery line's own sentence, unchanged), `tools/loop/run.sh:3433` and `:3499` (the note's sentence, unchanged), and
  `tools/loop/tests/loop-cases.sh:7392` (asserts the note carries it, unchanged).

surface: 1 case added, 0 rewritten, 0 fixtures realigned, 1 guideline paragraph and 1 spec requirement — the two
`look.sh` calls above.

## What changes

1. `tools/loop/report.sh` — the item root is derived from the loop root the state file belongs to (`report.sh:638-639`):
   `$state_dir/items` when that directory exists, and its parent's `items` when the state is the archived one under
   `runs/`. The two arms name the derived root (`item_root`, `:695` and `:717`), not `$state_dir/items`.
2. `tools/loop/report.sh:710-717` — the resume line appends the kept patch only when the note it prints does not already
   name it, so `run.sh`'s own sentence is not printed twice.
3. `tools/loop/tests/loop-cases.sh` — one case, `report#anActionNamesAnItemPathThatExists`, registered in the case list
   and the dispatcher.
4. `tools/loop/tests/loop-mutations.sh` — two records (`277.1`, `277.2`), one per invariant.
5. `guidelines/Backlog.recipes.md:1410-1421` "a named action list" — the paragraph that names the action list says where
   an item path is read from and that the sentence a note already carries is not repeated.
6. The `agent-issue-loop` delta — one requirement with one scenario, naming the case above.

## What this does not change

Which line names an item, what each line says, and the shapes the arms select: no state field, no `loop.conf` key, no
engine script other than `report.sh`. The item's open question — whether the two arms should name the item directory or
the file inside it — is settled by the red case's own requirement, a path that exists, and by what each arm's reader
needs: the gate arm names `worker-report.md` because the reader opens a transcript, the resume arm names the item's
directory because the next run is handed the item. Both keep the shape `report.sh` has today.

## Blast radius

One engine file (`tools/loop/report.sh`: the derivation, two arms, the appended phrase, the comments above them), one
case in `tools/loop/tests/loop-cases.sh`, two mutation records in `tools/loop/tests/loop-mutations.sh`, one paragraph of
`guidelines/Backlog.recipes.md`, one requirement in the change's spec delta. No compiled file is touched, so no build is
owed; the gate for this delta is the loop's own suite over the paths it changes.

## Provenance

Issue #855, filed from the run of `2026-11-11T11:37:08Z` — the run whose own report is the measurement above. The
direction it carries (derive the item root from the loop root the state file belongs to) is implemented as the state's
two shapes state it; the question it leaves open is answered in "What this does not change".
