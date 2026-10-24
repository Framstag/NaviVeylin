# Design — the mutation run is scoped by file

## Root cause

`selected()` (`tools/loop-mutations.sh:211` on HEAD `11123fb`) compares `--only` against the record's first
`@`-separated field, the label, and nothing else. The same record carries the file it mutates in its third
field (`tools/loop-mutations.sh:63`, declared at `:67`), so the harness already holds the information a change
needs to bound its falsification pass; it just never exposes it. The consequence is the measured one: bound the
pass by hand-picked labels, derived by grepping the record list, or pay the whole set (81 records × two case
runs, 21 min per pass on the host that measured #232).

## The single fix

`selected()` takes the record's file as well as its label, and a file set gathered from `--files` and
`--from-git` is the second filter. Everything else follows from that one predicate:

- `--files <paths>` — repeatable, and space-separated inside one value, symmetric with the existing `--only`.
- `--from-git <base>` — the selection's paths come from `git diff --name-only <base>` **plus**
  `git status --porcelain`, the same two sources `tools/loop/gate.sh` uses for "the paths the change touches",
  so the harness and the gate agree about what a change touched.
- the count is printed against the declared set (`selected N of M records`) before the first record runs, so a
  caller sees it ran a subset;
- a selection of zero records exits 2 before a scratch directory is created, so nothing can be mutated. This
  also covers `--only`, which could previously report exit 0 for a label list that matched nothing.
- `--list` applies the same filter, because the listing is how a caller derives and checks the set.
- a probe ignores the file set: `--probe` is the harness's own guard, not a record of the set, and a file set
  must not switch it off silently.

## Alternatives the evidence eliminated

- **Keep deriving the labels by hand** (`--only "12.5 12.6 …"`, the status quo of #232): the list was derived
  twice by hand for one change, is stale the moment a record is added, and the pass it avoids costs 21 minutes
  whenever it is skipped. Not a bound; a guess.
- **Let `--only` accept file names as labels**: two meanings behind one flag make a typo silent — the harness
  cannot tell a mistyped label from a mistyped path, and a label list is exactly what the harness must refuse
  to interpret loosely.
- **Filter inside `one_mutation` only**: the refusal and the count have to be decided before anything is
  mutated, and `--list` has to show the same set as a run; a per-record early return can do neither.
- **Match by basename instead of repository path**: two engine files can share a basename, and a diff hands the
  harness paths, so a basename rule would select a record the change did not touch.
- **Scope `tools/mutate.sh` instead** (the generic JSON runner): its sets are small (13 records for
  `portability-mutations.json`) and it is not the pass the item measured. The measured 21-minute pass is this
  harness's 81 records, so a change there would not bound what the item measured — and the same predicate
  would then exist twice.
- **Make the whole set cheaper** (run records in parallel): it keeps the pressure to skip the pass and puts two
  mutations in one tree at once, which contradicts the harness's own single-tree restore rule (a mutation in
  the tree is what the next record's case would see). The item asks for a bound, not a faster full pass; the
  full set stays the pass before a change that owns `tools/loop/**` is archived.

## Blast radius

- `tools/loop-mutations.sh` — the selection, the count line, the refusal, the help text. No record changes: no
  label, file, case or sed expression is touched.
- `tools/loop-mutations-selftest.sh` — checks 7–9, `ok`/`bad`/`contains` helpers and a tally line
  (`all N cases passed`), so the loop's gate reads numbers from the suite instead of none.
- `tools/loop/loop.conf` — both harness paths map to the harness's own self-test, so a change to either file is
  gated by the suite that proves it.
- `.pi/skills/revert-check/SKILL.md` — the usage block of the harness gains the two flags and the count line.
- `openspec/specs/agent-issue-loop/spec.md` — one ADDED requirement (after the archive).
- The engine under `tools/loop/*.sh` is otherwise untouched; no Kotlin, Java or C++ file changes.

## Rollback

Revert the five files. No state file, no migration, no flag has to be unlearned: a caller that passes no filter
gets the whole set as before, and nothing outside the harness reads the new flags.

## Threading and lifecycle

None: the harness is one shell process, its EXIT trap and its restore path are unchanged, and the refusal
happens before the scratch directory exists.

## Verification

- Red on HEAD: `tools/loop-mutations-selftest.sh` — **9 of 34 cases failed**, exit 1,
  `2026-10-22T07:08:27Z` (`.pi/issue-loop/items/235/red-on-HEAD.log`; the new cases answer `unknown argument
  --files` / `--from-git`).
- Green: the same suite — `all 34 cases passed`, exit 0, `2026-10-22T07:07:38Z` (`green-selftest.log`).
- Falsification of the new invariants (`falsification.log`, sha256
  `fe8b15fb22de966f23ec1ee7a9ff97237ade8b132e52c0b788bf32ece188c911` before and after): with the
  empty-selection refusal removed (line 263) the suite is **2 of 34 cases failed**; with the selection line
  removed (line 268) it is **3 of 34 cases failed**; each restore is `cmp`-identical and the restored suite is
  `all 34 cases passed`.
- Gate: `./tools/loop/gate.sh` once, quoted in `tasks.md` task 4.2 with its `LOOP-GATE` line.
