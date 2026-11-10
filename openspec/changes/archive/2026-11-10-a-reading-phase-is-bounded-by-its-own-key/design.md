# Design — a reading phase's context bound, and a rate that counts every cut

## D1 The lever: a role's own key, not a higher item bound

Three levers move the same number, and the fourth moves nothing:

1. **Raise `LOOP_ITEM_CONTEXT_MAX` to 250 000.** This is exactly #836, closed `NOT_PLANNED`, and #833 before it: the
   run it was judged on recorded three items past the bound whose **kept patches** carry 553 973, 509 913 and 512 102
   bytes, so a wider bound buys more of the tail a cut is made of, not the item the cut interrupted. It also raises
   the reading budget of the four phases the refusal *was* measuring — the worker's diagnosis and the gate's suite —
   where a turn at 250 000 costs 2.3× the archive's median turn.
2. **Implement the checkpoint split** — end the session at `LOOP_ITEM_CONTEXT_WARN` and hand the change and its
   evidence to a fresh child, the shape `tools/loop/prompts/worker-tail.md` already has. This is the cheaper half the
   configuration names, but the run's control over a child is process-level: it can end a session, not ask one to hand
   over mid-turn. A split that ends the child at the checkpoint is a cut under a friendlier name, and it is the same
   work as the resume below plus a prompt.
3. **A role's own key.** What today's measurement shows is a *population* problem: the stop ended sessions in the
   role whose work **is** reading the item — 4 reworks, 3 review rounds — and left the six that finished 3.6 % under
   it. The project already reads per-role values for turns (`LOOP_CHILD_TURNS_MAX_<ROLE>`, #784/#804), for the same
   reason and through the same helper shape, so the seam exists and is proven.
4. **Nothing but the resume patch** (which landed as #754, `8e3c8bca`). Measured 2026-11-10: 4 recorded resumes, 4
   `no-apply` — a moved base does not take a patch whose untracked half was written as `diff -u /dev/null`. Relying on
   it alone is what the day did, and each attempt paid the reading again.

**Chosen: 3.** It moves nothing for the phases the refusal measured, it is the same seam the turn bounds use, and it
is worth nothing to a project that records no key. **Cost, stated:** a reading role may spend up to 250 000 tokens a
turn — 2.3× the archive's median largest context — and a runaway session in those roles is ended later than before.
The bound stays a runaway bound: it is above the archive's observed maximum (244 764), which is the one thing this
passage says a stop bound must never fall below.

## D2 The values: 250 000 for the two reading roles

`LOOP_ITEM_CONTEXT_MAX_WORKER_REWORK` and `..._REVIEWER` record 250 000 — the archive's own maximum largest context
(244 764 over 300 item records, §7b of `guidelines/Backlog.recipes.md`) rounded up to the next figure a person
records. Alternatives:

- **220 000** — above today's eight cuts (211 475 max) but below the archive's observed maximum, so a session the
  archive has already seen would be cut at a number the archive has gone past. Rejected.
- **0 (unbounded) for both roles** — a reading phase ends when it converges; but a non-converging reviewer is the
  most expensive shape the loop has (reviewer turns are the archive's second-largest spend), and a bound of `0`
  records nothing a later reader can audit. Rejected.
- **The item's 200 000 left to the four work phases** — kept. `WORKER`, `WORKER_GATE`, `WORKER_TAIL` and
  `REVIEWER_PRE_GATE` record no key and inherit the item's, which is what the #833/#836 refusal measured.

## D3 The rate: the result, not one mechanism field

`cut` is the fifth result and it exists for exactly one reason — a bound the run enforced ended a phase of the item
(`state.sh`'s own words, issue #785). `stopReason: bound` is narrower by a shipped decision: a round the run ended
carries none, because no *item child* was ended there. Alternatives:

- **Write `stopReason: bound` on a round-cut item.** This contradicts the shipped requirement "An item the run's own
  bound cut is recorded `cut`, not `blocked`" in the same change, for a measurement's sake. Rejected.
- **Count the mechanism field and add a second clause for `reviewVerdict: cut-short`.** Two predicates for one fact,
  and the next arm that ends a phase owes a third. Rejected.
- **Count the result and the legacy pair** (`blocked` + `bound`) so an archive written before `cut` existed is read
  the same way. Chosen: one fact, one predicate, and the archive's denominator becomes every record — 438 in this
  worktree's archive, 31 hits — instead of the 247 records that happen to name a mechanism.

## D4 The classification rows travel in the same change

`tools/gen-feature-list.sh --check-classification` is red on HEAD for four delta spec ids the open changes
`package-structure-as-five-kinds` and `domain-vocabulary-is-declared` owe. The gate step runs for every item that
touches `openspec/**`, so on 2026-11-10 it reddened 4 of 4 gates, forced a hand-written waiver (#843: 5 evidence files
and a prose attribution) and fed a 2-of-2 review FAIL (#845). Alternatives:

- **Leave it and let each item waive it.** This is today's state and it costs every item a paragraph of argument,
  which is what the waiver rule exists to make visible rather than routine. Rejected.
- **Wait for those two changes to add the rows themselves.** They are open, unworked, and one of them is blocked on
  the same red. Rejected.
- **Add the four rows here, in the file that owns the classification.** Chosen — a data repair of the shape
  `b514b7b8`/`5c180aac` already record (`fix: classify the two spec ids the classification gate owed`).

The rows are `build-tooling`, not user-visible, no surface: a structure declaration, its test suite, a vocabulary
declaration and its projection are claims about the code, not about the app.

## Verification

- `tools/loop/tests/loop-selftest.sh` — the two cases this change's files declare
  (`run#eachRoleIsBoundedByItsOwnContext` new, `report#theBoundRateIsNamed` extended), plus the whole
  `tools/loop/**` file set the gate runs.
- `tools/loop/tests/loop-mutations.sh` — `270.1`–`270.4` red on their own case, restored, green.
- `tools/gen-feature-list.sh --check-classification` — exit 0 with `unclassified: 0`.
- No Android module, no native boundary, no UI surface: no Gradle build, no device run. The delta touches no compiled
  file, so the gate's scope rule (`guidelines/Build.md` §2) is the structural checks above.
