# Design — a label the queue honours, and nothing more

## The mechanism

There is one place the queue is ordered: the ranking `jq` program in `tools/loop/prescreen.sh`, which sorts the
eligible candidates into `$lead` (a leading category's defect), `$mix` (a leading category's other classes, mixed in
one after every `firstCategoryEvery` other item) and `$rest`, and joins them into `$order`. The fix is one stage
after that join: the records carrying the profile's next-up label are taken out, sorted by the same keys every other
stream uses, and put in front.

Three properties decide the shape:

- **the label is read from the profile, never spelled in an engine** (`tools/check-portability.sh` gates it, D16), so
  the role key is `roles.nextUpAgent` and a project that declares it empty pins nothing;
- **the key is optional**, like `shapingAgent` and `shapingVerdicts`, so a profile written before this capability
  starts a run instead of stopping on a key it never declared (`tools/loop/lib.sh`);
- **it is a rank, not a gate**: the flag is read beside the class and the category and decides nothing about the
  item's verdict, so the existing gate runs first and a next-up item that is on hold, in flight or unauthorised is
  excluded exactly as any other.

## The label's value

`agent/pin` was the obvious value and is unusable: `pin` is an ordinary word in this tree — the submodule pin every
worktree checkout compares (`tools/loop/worktree.sh`), the metric row that reads it
(`tools/sensors/metrics/libosmscout-lag.sh`) — and `tools/check-portability.sh` reports 8 leaks for it, because a
declared value may not occur as a word in an engine. `next-up` collides with nothing: the same check reports
`OK — 56 engines and 7 templates name no project value`.

## What it does not do

It does not read the `prio/*` band. The band is the metadata pass's score over a whole backlog (`prioritise.sh`
is its only writer), and the pre-screen deliberately reads labels only for the decisions a script can make cheaply;
wiring the band into the queue would make every run's order depend on that pass having run, and would let a stale
band outlive the basis it was computed from. A per-item override is the smaller, honest mechanism: it says "this
one, next", not "this one scores 7.4".

## Open decisions

none
