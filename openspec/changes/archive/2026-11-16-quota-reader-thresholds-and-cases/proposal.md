# Proposal

## Why

The loop's quota reader (`tools/loop-quota.sh`) is the only thing that decides whether a run may start another
item, and nothing exercises its arithmetic: `LOOP_QUOTA_JSON` is documented as "the self-test's seam" and no case
reads it — the engine's four quota cases (`state#aQuotaWindowIsAHoldNotAStop`, `state#aQuotaCheckThatCannotBeMadeIsNotARefusal`,
`run#aQuotaHoldPausesTheRunAndResumesIt`, `run#theQuotaSwitchIsExplicit`) all stub the reader, so a wrong floor, a
wrong pacing line or a wrong resume instant is caught by nobody.

The cost is measurable today. Run `2026-11-13T05:47:11Z` recorded eight holds totalling 17 278 s of an 11.1 h run,
each bought with 0.1–0.5 points of a weekly window that stood at 60.1 % burned against a 59.9 % pacing line — the
loop is pinned to the line and sleeps to stay there. The window's own reading was correct (the operator's
dashboard says 60 % used); what is unproven is the arithmetic that turns it into "hold" and into an instant.

Two bounds also disagree with the reading they were written for. `LOOP_QUOTA_PACING_RESERVE_PERCENT=10` leaves five
weekly points permanently unreachable — the line's end point is 90 % burned while the floor is 5 % remaining, so
the two never meet — while the session floor was already lowered to the same 5 % (commit `07c3612c`), so the two
numbers the configuration holds apart are the reserve and the weekly floor, and only the reserve is still 10.

A defect rides along: the reader parses `resets_at` with `fromdateiso8601`, which accepts exactly
`%Y-%m-%dT%H:%M:%SZ`. Any other spelling — an offset, fractional seconds, an absent field — becomes `0` through
`catch 0`, and the `($wr > 0)` guard then skips the pacing comparison **entirely and silently**, leaving only the
15 %/5 % floors. A provider that changes its timestamp format turns the pacing guard off with no line in the run.

## What Changes

- **The thresholds**: `LOOP_QUOTA_PACING_RESERVE_PERCENT` 10 → 5, with the comments that justify it rewritten. The
  session floor is **already 5** at this base (commit `07c3612c`, `session-floor-history-r2.txt`), so this change keeps
  it rather than moving it — the reserve is what moves to meet it. `LOOP_QUOTA_WEEKLY_MIN_PERCENT` stays 5.
- **The line and the floor meet**: with a 5 % reserve the pacing line ends at 95 % burned, which is exactly the
  weekly floor's 5 % remaining, so no weekly quota is left unreachable by construction.
- **Cases for the reader**: a new `tools/loop/tests/loop-quota-selftest.sh` in the shape of
  `tools/loop/tests/loop-spend-selftest.sh` — `quota#<name>` ids, `--case`, its own fixtures through
  `LOOP_QUOTA_JSON`, offline, no token, no network — one case per comparison: each floor, the pacing line on both
  sides, the solved resume instant, and the unreadable instants.
- **The silent degradation becomes a note**: a balance whose session or weekly instant is absent or not ISO-8601
  makes the reader print one line naming what it could not parse, so the run relays it (`state.sh cmd_next` passes
  a zero-exit reader's output to `loop_note`) instead of burning quota blind. The line travels on that exit-0 path
  only: a hold's stdout stays its reason and `resume-at`, the pair the engine reads (`tools/loop/run.sh:2284` takes
  the first line that is not `resume-at `), so no note can displace the window whose floor held.
- **A gate row**: `tools/loop-quota*` maps to the new selftest in `LOOP_GATE_SUITES`, exactly as `tools/loop-spend*`
  maps to its own, so a change to the reader gates the cases.

Not breaking: the reader's protocol (exit 0 / 3 / 2, `resume-at <epoch>` as the last line) is untouched, so
`state.sh` and `run.sh` need no change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `agent-issue-loop`: adds the requirement `The quota reader's comparisons are its contract` — that the reader
  decides from the balance alone (a per-window floor and a weekly pacing line), that an instant it cannot parse is
  named rather than silently dropping the line, and that a case exercises each comparison from a recorded balance.
  The existing requirement `A spent quota pauses the run instead of ending it` is unchanged: it owns the engine's
  protocol, which this change does not touch.

## Impact

| path | what |
|---|---|
| `tools/loop/loop.conf` | `LOOP_QUOTA_PACING_RESERVE_PERCENT` 10→5, two comments rewritten (`LOOP_QUOTA_SESSION_MIN_PERCENT` already 5 at this base), one `LOOP_GATE_SUITES` row added |
| `tools/loop-quota.sh` | one note line on an absent or unparsable reset instant, printed on the exit-0 path only; the three comparisons otherwise unchanged |
| `tools/loop/tests/loop-quota-selftest.sh` | new: the reader's cases, fixtures through `LOOP_QUOTA_JSON` |
| `openspec/specs/agent-issue-loop/spec.md` | one added requirement with its scenarios (via this change's delta) |

No Android module, no compiled file, no Gradle configuration, no JNI — the delta touches `tools/**` and `openspec/**`
only, so the gate is the shell suites named by `LOOP_GATE_SUITES` plus the doc/tooling checks, not a Gradle build
(`guidelines/Build.md` §2, the scope block).

Rollback: restore the two values and their comments, delete the selftest and its gate row, revert the note. The
protocol and every consumer of it are untouched, so no caller changes either way.

surface: 2 values and 2 comments in one configuration file + 1 new case file + 1 note in the reader + 1 requirement
with 5 scenarios — `grep -c '^#### Scenario:' openspec/changes/quota-reader-thresholds-and-cases/specs/agent-issue-loop/spec.md`
