# Design — where the cut goes, and what holds it there

## Context

See `proposal.md` — Why. Constraints this design works inside:

- **The move must change no behaviour.** 502 cases already pin what `run.sh` does; a split that needs a case
  changed for a reason other than its `# files:` declaration is a rewrite wearing a move's clothes.
- **The suite selects by declared files**, and the declaration is deliberately over-approximated (a missed red
  case costs a landing; a surplus case costs seconds). The split only pays if the declarations get *narrower*,
  which means the file names must follow behaviour.
- **Globals are the real coupling.** 78 functions share dozens of names today; `state.sh` is called as a
  subprocess (so nothing is shared with it), but `lib.sh`'s helpers and the run's own variables are one
  namespace. A split that ignores this produces four files that all assign everything.
- **`tools/loop/**` may name no project value** — `tools/check-portability.sh` reads every `*.sh` under
  `tools/loop`, so a layout table is engine data and its rows carry no project fact.
- **The record from `run-event-stream-is-typed` gives the seam**: an event kind belongs to the phase that emits
  it, so "which file may emit which kind" is answerable from data rather than from taste.

## Goals / Non-Goals

Goals: one owner per file, a declared and *enforced* size bound, shared names declared once, a source order that
cannot be violated silently, and case declarations that follow the boundary.

Non-goals: splitting `state.sh`/`worktree.sh`/`gate.sh`/`prescreen.sh`/`report.sh` (they are already single-owner
files and only enter the bound table); a plugin or sourcing framework; moving anything into Java (that is
`loop-protocol-core-in-java`, and it needs this boundary to exist first).

## Decisions

### D1 — Five files, cut at the run's own activity, not at line parity

Alternatives:

- **Equal-sized chunks** (rejected): a cut at a line count puts half a function's callers in one file and the
  function in another, and it has to be redone the next time the file grows.
- **One file per phase** (rejected): seven phases, each tiny, and the helpers every phase uses (`project_state`,
  `outside_write_*`, `report_field`) dangle in a seventh file with no owner.
- **One file per function** (rejected): 78 files and 78 sources; the suite's case declarations would name a file
  per assertion.

Chosen: five, because the engine has exactly five activities that own state — the entry and the item loop, the
child's supervision, the evidence reading, the leftovers, and the names they share — and because the record's
kinds partition along the same lines, so a file's owned kinds are a fact of the vocabulary rather than a taste.

### D2 — The bound is per file and declared in a table, not a total

Alternatives:

- **One total bound** (rejected): the total is already violated and a total lets growth hide in the file nobody
  is reading — which is exactly what happened to `run.sh`.
- **No bound** (rejected): that is today, and today's curve is +120 lines/day.
- **A bound per function** (rejected): `run_child` legitimately needs its 393 lines; a function bound would push
  the same logic into more, smaller functions with more shared state — the coupling the split is meant to reduce.

Chosen: `tools/loop/layout.tsv` carries `file`, `bound`, `owner functions`, `owned kinds`. The bounds start at
what each file is and a headroom of 25 %, so the table records today's size and refuses only growth.

### D3 — Shared names are declared as data in `shared.tsv`

Alternatives:

- **`declare -g` at each assignment** (rejected): it marks nothing about *who else* reads the name.
- **`set -u` and hope** (rejected): `set -u` catches a read of an unset name, not a name assigned by two files
  whose values fight — and the run already runs under `set -u`.
- **Export everything** (rejected): every child inherits the run's whole namespace, and the engine's D3 ("the
  child gets a prepared package and nothing else") is what that would break.

Chosen: one line per name — `name<TAB>assigned by<TAB>read by …` — and two cases: a name assigned in two files
fails, and a name read by a file that does not list it as reader fails. The declaration is the contract; a
`local` needs no row.

### D4 — `run.sh` sources the four files in a declared order

Alternatives:

- **Each file sources its own dependencies** (rejected): a guard variable per file, and the load order becomes a
  property of the current call graph rather than of the program.
- **A `set -a` environment** (rejected): subshell behaviour and the children's environment (see D3).

Chosen: `run.sh` sources in one declared sequence, and the layout table's `order` rows carry that sequence, so a
case can assert (a) the order `run.sh` uses equals the table's, and (b) every function a file calls is defined by
a file earlier in the order — which is the only failure mode a split introduces that the suite would otherwise
find at run time, on a device, at the cost of an item.

### D5 — The case declaration follows the file, and a stale name is an error

Alternatives:

- **Leave every case declaring `tools/loop/`** (rejected): selection stays as wide as it is, so the split buys
  nothing at the gate — the measurement that justifies this change would fail.
- **Declare both the old and the new name** (rejected): `run.sh` remains a match for every case, and the stale
  name silently restores the wide selection.

Chosen: each case declares the file its behaviour now lives in; the layout table's file list is the vocabulary a
declaration may use, and a case naming a file the table does not carry fails — so the old name cannot linger.

## Risks / Trade-offs

- **A move that quietly changes behaviour** → the whole suite is the proof, and the tasks move one file per task
  so the diff that broke something is one commit wide. A case changed for any reason other than its `# files:`
  declaration is a review finding.
- **Load order dependence the suite does not reach** (a function used only on a path no case drives) → D4's
  second assertion is static: it compares *defined* against *called* names per file, so it does not depend on a
  path being driven.
- **The bound becomes a ritual** (raise the number, move on) → the bound is a recorded value in a committed
  table, so raising it is a visible diff with a reason, and the census change
  (`loop-guards-with-no-open-patron`) reports the number a run reached.
- **A shared name that is genuinely shared by three files** → the table's `read by` column has room for it; the
  declaration grows a line, which is the intended cost.
- **Case declarations drift as behaviour moves** → D5's vocabulary check fails a declaration naming a file the
  table does not carry, so the drift is red in the suite rather than invisible.

## Migration Plan

1. `shared.tsv` and `layout.tsv` first, with the current file as the only row (bound = today + 25 %), and the
   shared-name declaration harvested from a read of the function bodies. The suite must be green with nothing
   moved: the declaration describes today.
2. Move one owner per task — leftovers, evidence, child, then the entry stays behind — each task ending green.
3. Re-point the case declarations, then tighten the bound per file to what each now is plus headroom.

Rollback: revert the commit. Each step is green on its own, so the rollback point is any task boundary.

## Open decisions

none
