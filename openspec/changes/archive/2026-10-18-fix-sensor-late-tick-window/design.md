# Design — fix-sensor-late-tick-window

## Context

Motivation, the root cause and the measurements are in `proposal.md` (`evidence/red-on-head.log`). The state
this design works within, read from the tree at `4bd0504` (2026-10-18):

- `tools/sensors/lib.sh:241`'s `sensors_cron_due <cron> <epoch>` asks one question — does this five-field
  expression match *this* minute — with the fields of that single epoch read by `sensors_time_fields`
  (`date -u -d @…`, with the BSD `-r` fallback) and matched by one `awk` pass. Return 2 = a malformed
  expression; `tools/sensors/run.sh:100` treats anything but 0 as "not due" (`|| continue`).
- `.github/workflows/sensors.yml` schedules `0,30 * * * *` and the registry
  (`tools/sensors/sensors.json`) declares seven rows whose minutes are 0 or 30 (0 for `build-duration`,
  `package-size`, `coverage`, `broken-main` and `stale-citations`; 30 for `pr-conflicts`, hourly-bounded for
  `broken-main` and four-hourly for `pr-conflicts`). So a tick delayed past the top of its slot lands in a
  minute no row declares and the selection is empty — measured 7 minutes of delay on this host.
- The two row steps pass `--due --rows <list> "$SENSORS_MODE"`, gated on the record's presence (and, for the
  build's rows, on a `success` conclusion). The tick's minutes and the rows' minutes are asserted equal in
  effect by `tools/build-inputs-cases.sh`'s `workflow#tickMinutesCoverTheRegistryCrons`.
- The rows' `consecutive` counts (2 for the metric rows, 1 for the two conditions) are what a repeat
  measurement is spent against: a run that repeats the previous run's verdict neither files nor closes
  (`tools/sensors/run.sh`'s `sensors_outcome_clear` / `sensors_outcome_violation`, held by
  `run#hysteresisBothDirections`).
- The suites that own these files: `tools/sensor-selftest.sh` (43 cases after this change) and
  `tools/sensor-mutations.sh` (14 mutations), offline against the stub `gh`, plus
  `tools/build-inputs-selftest.sh`, which reads the workflow's tick cron. No Gradle, no token, no network.

## Goals / Non-Goals

**Goals**

- A tick GitHub fires late measures the rows it was scheduled for, so the pipeline's green means "measured"
  rather than "nothing was due".
- The bound is a declared value with one home, and it is the tick's own cadence — derived from the schedule
  by a case, not trusted to a literal.
- No row is measured twice for one of its cadence's minutes, and the punctual path is unchanged.

**Non-Goals**

- Changing the schedule's cadence, the registry's rows, their bounds or their `consecutive` counts: the fix is
  *when a row is selected*, not which checks exist or how patiently they close.
- Recovering a tick GitHub **skipped** entirely (a lost tick leaves its minute unmeasured): that would need
  state the pipeline does not have (see D1(c)), and the cost is bounded by the row's own cadence.
- Touching the archived `wire-sensor-inputs` artifacts, whose report recorded this measurement as issue #166:
  an archived change is the record of that change.
- A new engine concept (a state file, a memo of the last tick, a per-row window): the window is one predicate
  with one caller-supplied width.

## Decisions

### D1 — The selection is a window of minutes ending at the tick's own minute (the fix's shape)

`sensors_cron_due <cron> <epoch> [<minutes>]` returns 0 when the expression matches any of the `<minutes>`
minutes from `epoch` backwards; the single-minute body becomes `sensors_cron_minute`, unchanged, and the
two-argument call keeps its meaning (`<minutes>` defaults to 1). `run.sh`'s `--within <minutes>` supplies it.

- *Alternatives the evidence eliminated*:
  (a) **Make the schedule denser (`*/5 * * * *`, or `* * * * *`), so a late tick lands on a declared minute** —
  impossible: lateness is not bounded by the tick's granularity (7 minutes was measured against a 30-minute
  cadence), so a tick delayed past a slot's own minute still selects nothing, however dense the schedule is;
  the density only multiplies the guard and preflight a run pays, and a run every minute cannot be the
  guarantee the issue asks for.
  (b) **Drop `--due` and measure every row every tick** — impossible: the weekly row (`hotspot`, Monday 05:00)
  and the daily rows would be measured 48 times a day, and a row's bound would be compared against a
  measurement taken off its cadence, which is the one thing the registry's `cron` column exists to say; the
  issue's own "the cost is bounded per row by its cadence" would become "the cadence says nothing".
  (c) **Remember the previous tick in state and select the minutes since it** — impossible: a run is a fresh
  checkout with no shared state, and the spec's *Nothing is committed by a check* is why (the issue is the
  history, not a file), so the memo would have to live in the tracker or an artifact — and the first run, or
  one whose artifact is missing, would then have no lower bound at all, exactly when the selection matters.
- *Why this one*: it is one predicate, no state, and the bound is a value the caller declares; the punctual
  case is the same call with the default width, so nothing that worked stops working.

### D2 — The window is as wide as the tick's cadence and ends at the tick's own minute

The window is `<minutes>` minutes wide **including the tick's own minute** (`00:30 --within 30` covers
00:01–00:30), so consecutive ticks' windows tile the hour exactly: `00:00` covers 23:31–00:00 and `00:30`
covers 00:01–00:30. A row's minute therefore belongs to one tick's window, which is what "no double measure"
means here. A tick delayed by up to `<minutes> - 1` minutes still covers its own minute; a tick delayed
*further* reaches into its successor's window, and the repeat that causes is absorbed by the registry's
`consecutive` hysteresis — the second measurement is the same verdict, so nothing is filed twice and no
`consecutive: 2` row is closed on one run.

- *Alternatives the evidence eliminated*:
  (a) **A window wider than the cadence** (e.g. 60 minutes, so even a tick lost entirely is covered) —
  impossible: the tiling above is the whole reason the registry's `consecutive` counts mean what they say; at
  60 minutes every tick would select a minute-0 row twice an hour, and a `consecutive: 2` row would reach its
  close or its recurrence bound in half the time its declaration says.
  (b) **A window narrower than the cadence** (e.g. 15 minutes) — impossible: it does not cover "any minute
  since the previous tick"; the measured 7-minute delay would fit and a 20-minute one would not, so the bound
  would be an arbitrary tolerance rather than the cadence the schedule already declares.
  (c) **A half-open window** (`(now - cadence, now]`, spelled `--within` = the cadence + 1 minute) — not
  eliminated by evidence, only rejected as a spelling: it makes the same selection with an off-by-one the
  caller has to remember, and the case that proves the tiling would then assert a number no file names.
- *Why this one*: the value the pipeline passes is the value its own schedule already states (30 for
  `0,30 * * * *`), the tiling is provable from it, and the only repeat it can cause is one the hysteresis
  already absorbs.

### D3 — The bound is declared by the caller; the pipeline declares its cadence; the engine's default is one minute

`run.sh --due` gains `--within <minutes>` (default 1). `.github/workflows/sensors.yml` names `--within 30` on
both row steps, and `workflow#theTickDeclaresItsWindow` reads the schedule's minute field out of the same
file, derives the cadence from it (the shortest distance between the minutes it names, an hour wrapping) and
asserts that every scheduled `run.sh --due` invocation passes exactly that value — so a cadence change moves
the declared window with it, and a step that goes back to asking about one minute fails the case.

- *Alternatives the evidence eliminated*:
  (a) **The engine derives the cadence itself** (read `.github/workflows/sensors.yml` from `lib.sh`/`run.sh`)
  — impossible: the engine names no project value — the invariant is
  `sensors#engineNamesNoProjectValue` and `tools/check-portability.sh` ("42 engines and 2 templates name no
  project value") — and a second project adapts `tools/sensors/sensors.json` and `tools/tooling.json` and
  edits no engine file; a path to *this* repository's workflow inside the engine is the leak that case and
  that check exist to catch.
  (b) **A hard-coded default of 30 in the engine** — impossible for the same reason: 30 is this project's
  cadence, read out of its own workflow, and a default that names another project's cadence is a project value
  in an engine. The engine's default has to be the value that means "the minute the caller meant", which is
  one.
  (c) **A `within` column in the registry** — eliminated as out of the direction this change was given and
  wrong in kind: the window is a property of the *tick*, shared by every row the tick considers, while the
  registry's columns are a check's own definition ("A row is the whole definition"); a per-row window would
  let one row declare a window that overlaps its neighbour's and would put a pipeline decision in the file a
  second project adapts.
- *Why this one*: one declared value, in the file that also declares the cadence it comes from, with a case
  that derives rather than trusts it — and the engine gains no project value.

### D4 — A `--within` that is not a count of minutes is refused, not narrowed

`run.sh` refuses a `--within` that is not a whole number of minutes, or is 0, with exit 2 before anything is
read; `sensors_cron_due` answers 2 (cannot tell) for the same input, keeping the library's contract with a
caller that uses it directly.

- *Alternatives the evidence eliminated*:
  (a) **Treat an unusable value as the punctual minute** — impossible: that reproduces the defect inside its
  own fix. The measured defect is "the run is green, nothing is measured, and a repeatedly delayed tick is
  indistinguishable from a run with nothing due"; a typo in the pipeline's flag would restore exactly that,
  invisibly. The issue's consequence sentence is what the refusal answers.
  (b) **Refuse it at the workflow's own level (a YAML type or a value list)** — impossible: the workflow's
  steps are shell strings, and the guard the issue wants is a property of the runner's interface, so a manual
  `--within` gets the same answer as the pipeline's.
- *Why this one*: the bound is the caller's to declare, so a value that cannot be used is a usage refusal with
  a message, which is what `run.sh` already does for an unreadable `--now` (exit 2).

## Risks / Trade-offs

- *A tick delayed beyond its own cadence reaches into the next tick's window* → accepted and stated (D2): the
  repeat is a second measurement of the same verdict, spent against the row's `consecutive` count, which
  neither files nor closes on one run (`run#hysteresisBothDirections` still holds). A tick lost entirely still
  leaves its minute unmeasured; the cost is bounded by the row's cadence, which is the bound the issue itself
  names.
- *A dispatch at an arbitrary minute now measures rows up to 29 minutes older than its own minute* → intended:
  a dispatch is a tick, the window is the same declared value, and a `--due` run at 03:29 measuring the 03:00
  row is "the rows it was scheduled for" under the bound the pipeline declares. A dispatch that wants one
  minute passes `--within 1`.
- *The window costs `30 × rows` minute conversions per run* → measured on this host: `run.sh --due` 4.04 s
  without the window and 4.47 s with it (both dominated by the run's own preflight), and the selection is 7
  rows × 30 minutes of one `date` plus one `awk` each. Below a second, in a job measured in minutes.
- *A cadence coarser than an hour cannot be read from the minute field alone* → the case's derivation wraps at
  60, so a daily tick (`0 3 * * *`) would be read as a 60-minute cadence and a delay beyond an hour would not
  be covered. The schedule here is `0,30 * * * *` (within the hour), and the workflow's header states the
  value and its reason; a future cadence coarser than the hour is a decision for the change that makes it, and
  the case's message names the cron it derived from.
- *`--within` is an interface the pipeline now depends on* → held by `workflow#theTickDeclaresItsWindow`
  (every scheduled `--due` invocation carries `--within <derived cadence>`) and by mutations `8.12`, `8.13`
  and `8.14`, which falsify the window, the declaration and the refusal in turn.
- *Nothing here is a component with a lifecycle* → no threading model, no dispatcher, no main/background pair:
  this change is shell, a workflow file and a spec, with no Android API, manifest, resource or Gradle file.

## Migration Plan

1. Land the change (the window in `lib.sh`, `--within` in `run.sh`, `--within 30` in `sensors.yml`, the three
   cases and three mutations), with the sensor suite, its mutation harness and the structural checks green and
   `openspec validate fix-sensor-late-tick-window --type change` at 0.
2. Archive: sync the delta into `openspec/specs/backlog-sensors/spec.md`, move the change under
   `openspec/changes/archive/<date>-fix-sensor-late-tick-window/`.
3. Observation after the merge: the next scheduled tick on `main` is the end-to-end record that a tick with a
   declared window selects its rows; a *late* tick cannot be produced on demand (the same reason the offline
   cases drive a fixture clock), and `tools/sensors/guard.sh` refuses every ref but `main`, so a dispatch from
   a branch is not the probe. Nothing is owed here beyond reading that run's selection line.
4. Rollback: revert this change's commit — `--within` disappears, `sensors_cron_due` asks about one minute
   again, and the state in `evidence/red-on-head.log` §1 reproduces.

## Open Questions

None that change this design. Whether a future cadence wider than an hour needs a wider window is the
question D3's risk records; it is answered by the change that declares such a cadence, not by a guess here.
