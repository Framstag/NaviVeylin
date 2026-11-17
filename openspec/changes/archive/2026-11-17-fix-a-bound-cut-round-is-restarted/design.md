# Design — a restart, and not a derived bound

## The two arms the item named, and the one taken

Issue #918 names two: *derive the reviewer's bound from the diff it must read* — the files the pre-gate round named
and their per-file ceiling — or *restart a cut round on the pre-gate diff with the earlier findings handed to it*.
The second is taken.

**Why not the first.** A derived bound has to answer "how many turns does a reading of *n* bytes take", and that
figure is a measured reading rate per turn. This archive does not carry one: the records price a turn in tokens
(`input + cacheRead`), and the bytes a turn reads are a property of the child's tool calls, not of the bound. A
number invented here would be the kind of constant this project's bounds are not allowed to be — the reviewer's
110 is the p95-or-above of 234 measured rounds, and the file it sits in says so. And the derivation would be moot
after #924: the input a round is handed is capped at three parts of 32 000 B, so a bound derived from the input's
size would answer the same figure for every round and be the constant in other clothes.

**Why the second is also the cheap half.** The bound is not, in the case the item measured, a runaway: #861's round
had refused nothing in 110 turns. What it ran out of is the *shape* of a long reading — every turn re-reads the
whole conversation, so the 110th turn costs the context the 110th turn has grown to. `run_child` starts a session,
so a restart begins near zero context and re-reads the input the cap already bounded (about 24 000 tokens for three
parts at 4 B a token). It is the same trade `#851` already makes for the item's own worker at the context bound,
one level up: a fresh session instead of the re-read of a grown one.

## The mechanism

**Where it sits.** Two arms, one rule: the round before the gate and every round after it. The pre-gate arm restarts
where the deadline and the bound are already read; the post-gate round gained an **attempts loop**, because the
round's retry (the transport's cut, `#224`) and its restart are two different answers to two different endings and
both are "start the child again with a prompt of the same round". The loop hoists exactly three things — the
attempt's prompt and report names, the restart section, and the flag that says the round has nothing to carry
below — and the digest `before` is taken before the first attempt and read after the last, so a write by any attempt
is the round's write and not only the final child's.

**Attempt names.** `<round>.md`, `<round>-retry.md`, `<round>-restart.md`, `<round>-restart-retry.md`. Every one of
them is `reviewer*`, so `child_role` reads them all as the reviewer's role and the turns, the context bound and the
wall clock apply as they did; and a reader of the item's directory can tell a transport's second ask from a bound's.

**What is handed over.** `review_restart_scope` composes the section the restarted prompt carries through the
`{{REVIEW_RESTART}}` placeholder `prompts/reviewer.md` gained (substituted first, beside `CORRECTION_SCOPE` and
`PRE_GATE`, for the reason those are: the section is text the round reads, not text a later substitution may
rewrite). It names the round, quotes the bound's own `LOOP-TIMEOUT` line, names the file the cut round wrote and
quotes its `BLOCKING:`/`NOTE:` lines at the input cap, and names the round before the gate's report when one ran.
A cut round that wrote no finding line says so in one sentence instead of quoting nothing.

**What is not a restart.** The run's deadline (a restart begun past the clock is ended on its first second and the
reason a reader then gets is the clock's), a round the transport cut twice (no bound ended it), an item's own child
(`#851`'s continuation), and a second bound — the item's `cut`, with `bound` and the round's key, exactly as #898
made it.

**What the record keeps.** `review_restart_note` composes one line per restart — `round 1 ·
LOOP_CHILD_TURNS_MAX_REVIEWER=110 · reviewer-report-1.md`, or `the round before the gate · …` — joined with `; `
across rounds, handed to `state.sh done --review-restart` and stored as `metrics.reviewRestart` beside
`metrics.reviewInput`. `report.sh` prints it on a line of its own, `none` for a run whose rounds no bound cut. The
reason is #936's shape one field over: the verdict, the round count and the spend are the round's own either way, so
without this a PASS after a restart and a first-pass PASS are the same record.

## What it does not settle

- No case drives the deadline arm of the guard: `review_restart_before_deadline` is asserted through the case that
  restarts (the deadline is 90 minutes out) and through `918.1`, which disables the guard. A case that sets the
  deadline a second away and asserts no restart would be a case about the clock, not about the restart, and the
  arm it would cover is the one `#851` already has.
- The restart is per **round**, not per item: `review_restarts_used` is reset where a round starts. A round cut
  in round 2 after a round-1 restart therefore gets its one too, and what the key bounds is what a run pays to
  finish **one** round it cannot answer — a round that takes two restarts is the item's cut.
