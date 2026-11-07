# Design — a coordinate check decides on the record, not on the line

## Root cause

One mechanism explains both reddenable cases: the assertion's subject is the whole line the reader returns,
and that line carries numbers that are not positions — the `[yyyy-MM-dd HH:mm:ss.SSS]` stamp
(`DiagnosticsLog.kt:156`, whose `ss.SSS` field reads `13.412` at second `:13`) and the record's own
measurements (`duration=13ms`; the reroute record's decimal `deviation_m=`, which is `13.45` for a 13.45 m
deviation). The check says "no coordinate", the input says "a clock reading and a measurement", and which one
the assertion sees is decided by when the test happened to run.

## The single fix

Decide on the record's own fields, which is what the spec sentence is about ("the **entry** SHALL NOT contain a
position or a coordinate-formatted string", `route-calculation-feedback` — "No coordinates in the entry"):

- `recordFields(line, marker)` splits the record after the marker its own subject wrote into `key=value`
  fields. A token without `=` keeps its text as the key, so a bare coordinate appended to a record is a key
  the schema does not name instead of being silently dropped.
- Each case asserts its record's **key set** (a `lat=`/`lon=` field cannot appear) and that **no field value
  is coordinate-shaped** — 1–3 integer digits and 4+ decimals, the shape `auto-diagnostics` names and
  `DeepLinkPrivacyTest.kt:32` already encodes. A stamp's three decimals and a millisecond count cannot reach
  that shape.
- The calculation case additionally pins `duration` to `\d+ms`, so the item's own claim — a duration read as a
  coordinate — is refused by the shape of the field rather than by luck.

## Alternatives the evidence eliminated

- **Remove only the stamp prefix** (`entry.substringAfter("] ")`). Eliminated: the reroute record's
  `deviation_m=13.45` still satisfies the check (measured: the line above matches `13\.45`), so the fix would
  leave the second case exactly as fragile as it is.
- **Drop the `duration=` field from a copy of the entry** (the item's first suggestion). Eliminated for the same
  reason: `duration` is already an integer count of milliseconds (`RouteCalculationDiagnostics.kt:29-40`), so it
  was never a reachable coordinate; the text that reddens the case is the stamp, and in the reroute case a
  distance. Removing a field that cannot collide costs a rewrite and fixes nothing.
- **Lengthen the substrings** (search for `52.5200` / `13.4050`). Eliminated: it moves the collision instead of
  ending it — the reroute record's `deviation_m=` is a free decimal, so some geometry produces any 4-decimal
  text the case then searches for.

## Blast radius

Two cases and one new test-source file. No production file changes; no spec requirement changes (the delta under
`specs/` adds the assertion rule these cases follow). No other case reads `recordFields` or
`coordinateShapedToken`, so a revert is the two hunks plus the file.

## Rollback

`git revert` of the change's commit restores the two whole-line assertions and removes the helper; both cases
are green on the restored tree with the same totals (they were green before, apart from the load-dependent red
this fixes).

## Verification

- Revert-check A (the mechanism, on HEAD): one production mutation pins the stamp in
  `DiagnosticsLog.timestamped` to `[2026-11-03 13:41:13.412]`; the case must fail at
  `NavigationEngineCalculationStateTest.kt:231` — it does, for the assertion's own message.
- Revert-check B (the guard, after the fix): one production mutation appends `lat=52.5200` to
  `routeCalculationDoneLine`; the rewritten case must fail on the record's own fields — it does.
- Forced green after each restore, and the gate once for the change.
