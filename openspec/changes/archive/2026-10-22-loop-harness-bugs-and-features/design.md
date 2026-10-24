# Design

## D1 — The category lead is narrowed to defects, not deleted

A defect in the tooling that runs, measures, gates or reports the loop is paid again by every item after it.
An improvement in the same category is not: it makes the tooling better without making the run's own report
false. The profile therefore names the classes the leading categories lead on (`firstCategoryClasses`), and
this project names `bug`. A project that wants the old behaviour names every worked class there; an empty list
turns the category lead off.

## D2 — The mixing rate is a rank interval, not a run-time quota

`firstCategoryEvery: 4` inserts one leading-category non-defect after every four other items in the queue. A
run-time quota would need the wrapper to count what it has taken, and the pre-screen would then no longer be
able to state the queue as one total order — the state file and the dry run would each need a second rule.
With an interval the queue stays a total order, the run still takes its prefix, and "a few per loop" follows
from the rank rather than from a counter. `0` switches the mixing off and leaves those items in the class
order; a non-number the engine invented is never read (the profile is validated by `loop_roles_load`).

## D3 — The leading categories' other classes are not dropped

The mixed stream is ordered by the same class order and issue number as the rest, and its leftovers follow
the mixed run in that order. It is a rank, never a gate: a held harness improvement is excluded exactly as
any other held item.

## D4 — A feature is proposed before it is applied

A feature has no failing current behaviour, so the defect gates cannot be kept literally. The propose-first
path keeps the loop's shape: the acceptance scenario is the red case (it fails on HEAD because the capability
is absent), the plan is an OpenSpec change named `add-<slug>` written before production code, and the same
apply phase runs the project's gate once. Conditions 2 and 4 are read per class (one missing capability; the
acceptance scenario is false on HEAD); condition 7 admits one small requirement for a feature.

The `class/feature` refusal remains a profile capability (`refusedClasses`): this project stops using it, and a
foreign project can keep it. The branch prefix stays `fix/issue-*` because renaming it would touch the
worktree, the adoption path, the report and every case for no behavioural gain.

## D5 — The class is a work input, not a label the child infers

`run.sh` already reads the issue JSON; it now reads the single `class/*` label and renders it as
`{{CLASS}}`. The worker prompt branches on that value instead of asking the model to classify the item, and a
missing class is the empty string — the prompt then says neither path, because the pre-screen's
`workedClasses` already refused the item.
