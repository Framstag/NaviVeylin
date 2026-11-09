# Design — one budget beside the clocks, and the never-started stop spends it

## The root cause

The arm that reads "the child could not start" (`tools/loop/run.sh:2598`) decides the shape from the item's own
evidence — no verdict line, the tree at the run's base commit — and records it as the run's own stop. The reading is
right about the item and wrong about the window: a provider that refuses every request, an adapter that refuses the
child's argv, a lock or a transient refusal is a statement about one attempt, and the run ends every item after it on
the first occurrence. The run prices an item, a turn and a token and prices no retry, so there is no budget it could
spend instead of stopping.

## The single fix

Record the retry beside the run's other clocks and spend it at the one arm whose class the item's evidence names:

1. `tools/loop/loop.conf`: `LOOP_RETRY_MAX=3` and `LOOP_RETRY_DELAYS="30 120 300"`. One budget, one delay per retry,
   spent once per transient class per run. `LOOP_RETRY_MAX=0` is a project that records no retry, so the value is a
   cap a person edits rather than a constant an engine carries.
2. `tools/loop/run.sh`: read both keys beside the clocks before the item loop, and at the never-started arm spend the
   class's budget before the stop. A retry re-runs the item from the queue (`continue`), because what failed is the
   item's whole phase pipeline and the failed attempt recorded nothing — the prompt is rendered again and the branch
   is reused, so the retry has the same work package the first attempt had. The line it prints and the stop reason it
   leaves name each attempt, the delay it waited and the keys it read. The run's `state.sh` stops are untouched: the
   arm below the retry is the same stop it always was.

No prompt changes: the retry is engine behaviour, and `tools/look.sh 'could not start|LOOP_RETRY' --path
tools/loop/prompts` → `0 matches`, so no child is told a rule about the run's budget.

## Blast radius

| file | what changes |
|---|---|
| `tools/loop/loop.conf` | two keys and their paragraph |
| `tools/loop/run.sh` | the retry budget block before the item loop, and the arm that spends it |
| `tools/loop/tests/loop-cases.sh` | two cases added and registered; two stop cases name `LOOP_RETRY_MAX=0` |
| `tools/loop/tests/loop-mutations.sh` | two records, `264.1` and `264.2` |
| `guidelines/Backlog.recipes.md` §7d | one paragraph: the rule, the measurement, the two cases, the two records |
| `openspec/specs/agent-issue-loop/spec.md` | one ADDED requirement, two scenarios, one case each |

Nothing compiled, nothing shipped, no Gradle task, no device: the loop's own self-test is the gate, and its `run`
group is what these cases join.

## Why the class is the never-started one

The class has to be recognised from evidence the run already holds, or a retry re-asks a refusal that will always be
refused. The never-started shape is exactly that evidence: the report carries no verdict line and the tree is at the
base, so no part of the item was read and nothing the retry re-runs could have changed the outcome but the transient
cause. A deterministic refusal — a base that moved, a branch another item owns, a worktree the run itself must not
discard — is a different class and stays a stop; the cap (`LOOP_RETRY_MAX`) is what keeps a class that is *not*
transient from looping, because a class that fails the cap is an answer about the run.

## Alternatives the evidence eliminated

- **Retry every stop** — eliminated by what a stop is: `state.sh`'s item cap, deadline, empty queue and floor are
  verdicts about the budget, and re-asking them spends the window the run stopped to protect. The item's own
  direction says so, and the requirement states it.
- **An unbounded retry** — eliminated by the deterministic refusals above: a base that moved (`run.sh:2068`), a
  branch that carries another item's work (`:2163`) and a start the run must not discard are refused for a cause no
  wait changes, so a retry without a cap turns one refusal into a loop that outlasts its child bound.
- **Retry inside `run_child`** — eliminated because `run_child` does not hold the two facts the class is read from
  (the tree at the base and the item's own report): it returns a status, and a status is what the arm above already
  refuses to trust. It is also where the run's own wall-clock and turn bounds end a child, which are not failures to
  retry.
- **A second retry counter shared by every class** — eliminated because one class spending another's budget is a
  retry that cannot be reasoned about; the budget is per class, and the arm owns its own count.

## Verification

- The two cases, red on the base and green on the change (`evidence/red-on-HEAD.log`, `evidence/green-on-change.log`),
  each named by exactly one scenario of the delta.
- One mutation per invariant (`264.1`, `264.2`): the named case goes red for the named reason and green again on the
  restore (`evidence/mutation-264.log`).
- Every record the change's paths select still applies: `--check --from-git f11595fa`
  (`evidence/mutation-check.log`).
- The four never-started cases together (`green-on-change.log`) and the whole `run` group (`evidence/run-group.log`).
- The structural checks of the shell files (`bash -n`), the documentation routes the §7d edit owes and the change's
  readiness (`evidence/structural-checks.log`, `evidence/readiness.log`).
- The gate itself — `tools/loop/gate.sh`'s plan for this delta and the selection it names — is this item's one gate
  and is run by the gate child that follows the diff review, which is the rule this change states.

## Rollback

Revert the four files' hunks. The two cases return to red, `tools/loop/tests/loop-mutations.sh --check` reports `264.1` and
`264.2` as `MUTATION-DID-NOT-APPLY`, and the guideline paragraph is the only prose left to delete with them. No
state, no data, no build output.

## Open decisions

none

The direction is the item's own stated one (the retry is a recorded value beside the clocks, spent on the transient
classes once per class per run, and the run's own budget stops stay final), and the item's `ITEM_DECISION` block
records that nobody left a decision on it.
