# Design — count first, retire second, and stop if the count is healthy

## Context

See `proposal.md` — Why. What constrains the design:

- **The engine's guards already carry their patron.** 128 distinct issue numbers are cited in `run.sh`'s
  comments today, in the one form the engine uses everywhere (`issue #882`, `(issue #851)`). The anchor exists;
  nothing reads it.
- **Nothing maps a guard to a case.** Cases are selected by declared files and named by behaviour; a guard is a
  branch. A static "which case exercises this branch" is not decidable in shell with acceptable confidence, so
  the census must not pretend to it.
- **A child may not hold a tracker credential** (the loop's own D3: "a prepared work package and a working
  directory, and nothing else — not a tracker credential"). So the census cannot be a run phase, and a run must
  not depend on it.
- **The suite is offline** and stubs `gh`; the census needs the real tracker only when a person runs it, so its
  cases run against the stub fixtures.
- **The threshold must not become configuration.** A census that a profile can tune is a number an operator can
  make say yes.

## Goals / Non-Goals

Goals: three counts a person can act on, a retirement that is recorded, and a change that ends if the engine is
not actually carrying dead rules.

Non-goals: deciding which case exercises which guard (the census reports *no* case naming the refusal's phrase,
which is a weaker and honest claim); retiring a guard whose requirement a case asserts; a run-time gate.

## Decisions

### D1 — A separate tool, not a run phase and not a core verb

Alternatives:

- **A run phase** (rejected): it needs a tracker credential in the run's process, which the child contract
  forbids, and it would let a census change a run's verdict.
- **A verb in the Java core** (rejected): the core answers questions about the state, the verdicts and the
  numbers; a tracker query is not one of them, and a core that speaks to the tracker is a second pre-screen.
- **A sensor row in `tools/sensors/sensors.json`** (considered, rejected here): a sensor files items on a
  schedule and carries bounds and consecutive counts; the census's answer is a number a person reads before a
  cleanup, not a recurring failure to file. It may become a sensor later, once the retiring is done and the
  number needs watching — that is a follow-up, and naming it here keeps it from being invented twice.

Chosen: `tools/loop/census.sh`, one tool, `--json`, no state, no tracker write.

### D2 — The patron is the issue number the comment already cites

Alternatives:

- **A new annotation syntax** (`patron: #882`) (rejected): it would have to be added to 128 comments by hand, and
  a half-applied annotation is worse than none.
- **A manifest mapping guards to issues** (rejected): a second place to keep in step with the code, which is the
  drift this change is about.

Chosen: read the numbers the comments carry. A guard whose comment cites none is its own bucket ("no patron
written down"), which is a finding rather than an error.

### D3 — "Unheld" is two numbers, not a verdict

Alternatives:

- **A static branch-to-case map** (rejected): shell branches and shell cases do not map by parsing, and a wrong
  map would retire a load-bearing guard — the one outcome this change must not have.
- **A coverage run** (`bash -x` over the suite, collect the guard lines taken) (considered, rejected for now): it
  is the honest answer, it needs every case to run under an instrumented engine, and the suite's own cost (723 s
  for 190 cases, measured 2026-10-23) makes it a project of its own. Named as the follow-up if the two numbers
  prove too blunt.

Chosen: the census reports (a) guards whose patron item is closed, (b) guards whose refusal phrase appears in no
case body, (c) guards citing no issue. A retirement needs **(a) and (b) together** and a person's read of the
diff; the census never retires anything itself.

### D4 — The retirement is a committed table, one row per removed guard (the successor's decision)

This change retires nothing, so it writes neither the table nor the case that bounds it: `tools/loop/retired.tsv`
stands in no tree today. The decision is recorded here because it is what this change's number is read for, and it is
`the-unheld-guards-are-retired` that carries it into the tree.

Alternatives:

- **The commit message only** (rejected): a later reader looking for why a rule vanished searches 148 commits.
- **A comment left in place** (rejected): the guard is what is gone; a comment about a rule that no longer exists
  is the accumulation this change is meant to reverse.

Chosen: `tools/loop/retired.tsv` — `date`, `issue`, `phrase`, `where it stood`, `why` — and a case asserts every
retired row names an issue and a phrase, so the table cannot become a list of opinions. Both the table and that case
are `the-unheld-guards-are-retired`'s tasks; nothing in this change's diff creates them.

### D5 — The threshold is text in this change, and the change stops on it

Alternatives:

- **A `loop.conf` key** (rejected): the census is not a run's business, and a tunable threshold makes the answer
  negotiable.
- **No threshold, retire what the census names** (rejected): a cleanup campaign on a healthy engine removes rules
  that are earning their place, and the suite cannot see the difference — a retired guard's case goes with it.

Chosen: the 10 % figure is stated in `proposal.md`; task 1 records the measured numbers beside it, and if the
share of guards that are unheld is below it, the change ends with the census as its deliverable and says so in its
report. The decision is then a number a person can check, not a judgement a worker makes.

## Risks / Trade-offs

- **The census's (b) number is noisy** (a phrase paraphrased in the case) → the retirement needs (a) *and* (b) and
  a person's read; a case that fails after a retirement is the net.
- **A retired guard was load-bearing on a path no case drives** → the full suite is the only net, so the
  retirement is one task per group and the group's diff is reviewable; a case whose assertion has to change is a
  finding, not a retirement (D3's rule).
- **The tracker query is rate-limited or fails** → the tool prints the two counts it can compute offline and says
  the patron count is unread, so a network failure is never an empty answer (the shape #882 taught).
- **`retired.tsv` becomes a graveyard of its own** → a case bounds its rows' required fields, and the census's (c)
  count keeps the pressure on new guards to cite a patron.

## Migration Plan

1. The census tool and its stub-`gh` cases, with no guard touched: the change's first number.
2. Read the number against the 10 % threshold. Below it: stop, report, archive as not-needed. Above it: continue.
3. Retire by group (the same five owners `run-sh-is-split-along-the-stream` creates), one task each, each ending
   with the full suite green and a `retired.tsv` row per guard.

Rollback: revert the commit. A retirement reverts with its commit; the census tool is additive and reverts to
nothing.

## Open decisions

none
