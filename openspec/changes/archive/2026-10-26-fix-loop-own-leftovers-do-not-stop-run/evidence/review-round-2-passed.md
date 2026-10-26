# Review rounds — fix-loop-own-leftovers-do-not-stop-run (#664)

Three read-only rounds, each by a reviewer that did not write the change. The prompts asked for a verdict, the
findings with `file:line`, and what must change before it lands.

## Round 1 — refused, 2 findings

Reviewed the round-1 tree (the retry alone, as the cap-ended child's run left it).

1. **P1** — the retried refusal overwrote the patch it had just relied on: the attribution called `keep_leftovers`
   unconditionally, and that writes its patch with `>`, so a discard that half-succeeded (a residual inside the
   submodule) replaced a record of real work with a header and the residual.
2. **P1** — a retry refused for another reason was reported as dirt: `start-item`'s line was printed and dropped, so
   a start refused for its branch stopped the run with `the worktree is not clean …` and an empty list of files, on a
   tree the run had just cleaned itself.
3. **P2** — the spec's "a retry refused again SHALL be attributed" had no scenario; the patch path was triplicated;
   the repair printed `reset-worktree:` mid-run without that flag; a `setsid` survivor is unchecked.

## Round 2 — refused, 1 required finding

Reviewed the corrected tree (both P1s fixed, new case `run#aRetriedStartStopsWithItsOwnReason`, mutation `13.11`).
Everything the round checked read correct; the required finding was evidence only:

1. **P1** — `tasks.md` and `design.md` claimed `13.11` reddens the new case, but no artifact recorded it:
   `evidence/focused-mutations.log` selected 7 of 176 records and held no `13.11`. Required: run it, keep the log,
   cite it.

Notes it recorded without requiring them, and what was done with each: the patch guard had no mutation record (a
`13.12` would report `not-noticed` — measured, `evidence/guard-removal-probe.txt`); the naming line prints after the
discard's own lines; a retry answering *carries commits the base does not* stops where the first start would adopt —
both kept in `design.md`, "Left open", as a change of their own.

Fixed before round 3: `evidence/focused-mutations-round2.log` (`selected 9 of 177`, `RED 13.11`, `not-noticed: 0`),
its citations in `tasks.md` §7 and `design.md`, the refreshed counts (`238` cases, `177` records) in `proposal.md`
and `evidence/measure-summary.txt`, `evidence/gate-final-summary.xml` so the round-2 gate's `tests=605` has a tally
`evidence-check.sh` can read (C2 was red on that, alone), and `evidence/guard-removal-probe.txt`.

## Round 3 — passed

Re-checked the four points the round-2 finding named, and reproduced the guard-removal measurement in a scratch copy:

```
PASS    evidence/focused-mutations-round2.log: selected 9 of 177, RED 13.11, red-when-mutated: 9 · not-noticed: 0
PASS    tasks.md §7, design.md Verification and Round 2 cite that log for 13.11
PASS    counts match the tree: 177 mutation records, 238 selftest cases
PASS    guard removal leaves the case green (evidence/guard-removal-probe.txt), worktree untouched
Merge verdict: OK.
```

One note of that round, taken as given rather than fought: the guard-removal measurement had no artifact of its own
until `evidence/guard-removal-probe.txt` was written; it is one file, and the round asked for one line.
