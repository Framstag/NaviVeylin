# Design

Shell only: no dispatcher, no lifecycle, no thread. Every component here is a process the run starts at an item
boundary (`state.sh cmd_next`), so the threading model of `guidelines/Design.md` §4 has nothing to bind.

## Decision 1 — the two threshold values

```
                      line today   line end of week
A  reserve 10 (now)      59.9 %         90 % burned
B  reserve 0             66.5 %        100 % burned
C  reserve 5 + session   63.7 %         95 % burned
   floor 5
```

Those four figures are arithmetic from the two recorded numbers (the window's burned share and the elapsed share of
its week); the only held quantity is A's, the eight holds the item's `run.json` records totalling 17 278 s of an
11.1 h run. B and C were never run, so this table claims no hold count for them.

- **A — leave it.** Rejected: the 90 % cap and the 5 % floor never meet, so five weekly points cannot be spent by
  the loop in any week, and each of A's eight holds was bought with 0.1–0.5 points of line — hours of an 11.1 h run
  spent asleep.
- **B — reserve 0.** Rejected: it removes the only allowance kept back for the operator's own use of the same
  quota, and the line then reaches 100 % burned, i.e. it promises a spend the 5 % floor still refuses.
- **C — chosen.** 5 % reserve and a 5 % session floor — the floor the tree already carries (commit `07c3612c`,
  `session-floor-history-r2.txt`), so the number that moves is the reserve. The line's end point (95 % burned) now
  coincides with the weekly floor (5 % remaining), so every point the line gives is a point the floor allows: the two
  bounds agree by construction rather than by luck. The session floor is not the one a run loses its sleep to — the
  item's `run.json` records all eight of A's holds against the pacing line rather than the session window — so a 5 %
  floor there is not the exposure it would be on the long window: an item that starts with 6 % of the short window
  left risks that window running out mid-item, and the short window refills within hours.

Risk: the session floor at 5 % lets an item start with less margin than before and exhaust the short window
mid-item. Accepted, because the short window is the one that returns.

## Decision 2 — where the reader's cases live

- **A — a group in `tools/loop/tests/loop-cases.sh`.** Rejected: every case there builds a throwaway project and
  drives the engine; the reader is asked through `LOOP_QUOTA_CMD` and the file's own comment says so — "a stub
  written into the throwaway project answers with the status and the lines a real reader would". A case that
  imported the real reader into that harness would test the fixture, not the reader.
- **B — a new `tools/loop/tests/loop-quota-selftest.sh`, mirroring `tools/loop/tests/loop-spend-selftest.sh`.**
  Chosen: the precedent is exact. That file tests `tools/loop-spend.py` — also a reader that lives outside
  `tools/loop/` — with its own `spend#<id>` ids, its own `--case`, fixtures it writes itself, an offline contract,
  and one `LOOP_GATE_SUITES` row mapping the tool's path to its suite. `quota#<id>` ids, `LOOP_QUOTA_JSON` fixtures
  in the seam the reader already documents for exactly this, and one more row of the same shape.
- **C — fold the cases into `loop-selftest.sh` so `--list` reports them.** Rejected: `LOOP_SUITE_CASES_CMD` is
  `loop-selftest.sh --list` and is quoted as *the engine's declared case count* by the loop's prompts; adding
  another script's cases to it would change a number that measures something else — and the spend suite does not
  do it either.

## Decision 3 — how an unparsable reset instant surfaces

The defect: `fromdateiso8601` accepts only `%Y-%m-%dT%H:%M:%SZ`; anything else becomes `0` through `catch 0`, and
the `($wr > 0)` guard then skips the pacing comparison with no line anywhere. Pacing is the guard that matters, so
its silent disappearance is worse than a hold.

- **A — keep the silent skip.** Rejected: a provider that changes its timestamp spelling converts the loop from
  paced to floor-only without one log line, and the reader's own comment claims the seam that would have caught it.
- **B — exit non-zero so the run stops.** Rejected twice: it violates the reader's stated protocol (only `3` is a
  hold, and "an unreadable balance is **go**: a check that cannot be made is not a refusal"), and it would spend the
  run's budget on the provider's formatting.
- **C — print one line and go.** Chosen: `state.sh cmd_next` already relays a zero-exit reader's stdout —
  `[ -z "$quota_out" ] || loop_note "state: $quota_out"` — so the note reaches the run's notes with no engine
  change, and the reader's exit stays `0`.

**Where the note travels, and why not further.** The note is printed on that exit-0 path only. A hold's stdout stays
its reason and `resume-at`, because the engine reads the reason as *the first line that is not `resume-at `*
(`tools/loop/run.sh:2284`); a note printed ahead of a hold would therefore be recorded as the reason the run paused,
displacing the window's own words in `run.holds[]`. The consequence is recorded rather than hidden: a floor hold
taken while an instant is unreadable prints the floor's reason alone, and the missing pacing comparison is named on
the next go. `--json` prints its decision object without the notes, so the structured reading stays what it was.

## Decision 4 — whether the values become spec requirements

- **A — enumerate 5 / 5 / 5 in the spec.** Rejected: the spec would then pin a project's tunable, and the next
  operator who wants 10 % would be editing a requirement to change a configuration value, against
  `openspec/specs/documentation-ownership/spec.md`'s division between a contract and a project value.
- **B — no requirement at all, configuration only.** Rejected: that is the state that produced this change — a
  bound no case proves.
- **C — a requirement on the comparisons, the values in `loop.conf`.** Chosen: the spec says what the reader
  decides and that each comparison is exercised; `loop.conf` says with which numbers.

## Verification

| claim | settled by |
|---|---|
| each floor holds and names its reset | `quota#aWindowUnderItsFloorHoldsUntilItResets` |
| the line is `elapsed × (100 − reserve) / 100`, both sides | `quota#thePacingLineIsTheElapsedShareLessTheReserve` |
| a line hold names the instant the line arrives, not the reset | `quota#aHoldOnTheLineNamesWhenTheLineArrives` |
| an unparsable instant is named on the go path, floors still compared | `quota#anInstantItCannotParseIsNamedNotDropped` (and its `--json` shape) |
| an unreadable balance is a go with a note | `quota#aBalanceItCannotReadIsAGoWithANote` |
| the values in `loop.conf` are the ones the cases assume | one case reads `loop.conf` and asserts the three values |
| nothing else moved | `tools/loop/tests/loop-selftest.sh` (the engine's suite, unchanged) |

Red on HEAD: `quota#anInstantItCannotParseIsNamedNotDropped` and `quota#aBalanceItCannotReadIsAGoWithANote` fail on
HEAD's reader with this suite in place (`0|0||` — the note is absent), and `quota#theRecordedValuesAreTheOnesTheseCasesAssume`
fails on HEAD's `loop.conf` (`reserve=10`): 6 cases, 3 failed, 2026-11-16T00:56:02Z
(`quota-selftest-red-on-head-r2.txt`). No on-device measurement: no compiled file, no pixels, no surface.

## Open decisions

The item's recorded decision settles the reader's exit protocol — "Still 0 go, 3 hold, 2 usage; the note travels on
stdout with exit 0, which `state.sh cmd_next` already relays to `loop_note`" — and it does not say where the note goes
when the reader holds. The engine's reader of that stdout (`tools/loop/run.sh:2284`) takes the first line that is not
`resume-at ` as the hold's reason, so printing the note ahead of a hold would record the note instead of the window
that was short. This round closes the fork the recorded decision's own words close: the note prints on the exit-0
path only, and a hold is left byte for byte as it was. The remaining alternative — carrying the note on a hold too —
needs the engine to read the reason as the last-but-one line, with its own case and its own gate, and is not this
change.
