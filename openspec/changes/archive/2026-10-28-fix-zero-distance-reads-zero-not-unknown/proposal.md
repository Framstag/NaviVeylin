# Proposal

## Why

A live step standing on the announced node publishes `remainingMeters = 0.0`, and the shared notification
formatter renders it as the unknown-distance placeholder. `formatDistanceCompact` at
`app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt:253` returns `"--"` for every
`meters <= 0.0`, so the phone notification's stats line reads `in --` and the car hint's secondary text reads
`-- · --:--` at the one moment the wording matters most. The two facts are not the same: `null`/`NaN` is
unknown, zero is a known distance.

The red case is `zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown`
(`app/src/test/java/com/naviveylin/service/NavigationNotificationContentFormatterTest.kt`), run on HEAD
(`c29691865337325c0b293b57b72068d0468540da`) on 2026-10-28:

```
NavigationNotificationContentFormatterTest > zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown FAILED
java.lang.AssertionError: zero distance must render 0 m in the notification: in -- · arrive 14:32 · 0 min · 12.4 km
```

XML (`evidence/red-zero-distance.xml`): `tests="1" skipped="0" failures="1" errors="0"
timestamp="2026-10-28T18:08:55.848Z"`.

The defect contradicts the requirement `navigation-ongoing-notification` — "Surface parity": the phone
shade and the car hint are produced by the same formatter and must agree on the distance value. The owning
guideline is `guidelines/UI.md` §1, "Ongoing navigation notification (background driving)" — one formatter
produces the phone text and the car hint.

## What Changes

- **The formatter distinguishes unknown from zero.** A `null` or `NaN` distance keeps `"--"`; a known zero
  renders the shipped numeric form `0 m`. Negative values, which can only be a rounding artefact of a passed
  manoeuvre, clamp to zero rather than inventing a negative distance.
- **The missing-step call site stays unknown.** `navigationContent` passes `step?.remainingMeters` rather than
  substituting `0.0`, so no next step still reads `in --` instead of a fabricated `in 0 m`.
- **One test case covers both surfaces.** The case asserts the phone notification's stats line and the car
  hint's secondary text, plus the unchanged `--` for a `NaN` distance.
- **No new string, no new locale resource.** `0 m` uses the existing `formatDistanceNumber` and the already
  shipped unit suffix; `"in now"` would need a new resource and read ungrammatically in the stats line.

surface: 1 case added, 0 rewritten, 0 fixtures realigned —
`grep -rn 'zeroDistance\|in 0 m\|in --' app/src/test --include=*.kt`.

Not in scope: a localized "now" wording, the on-screen next-turn overlay and car session surface (they
already render `0 m` through `formatDistanceNumber`), and any change to the post/dedup buckets.

## Capabilities

`navigation-ongoing-notification` — the surface-parity requirement names the one formatter both surfaces
read; the delta adds the zero/unknown requirement next to it, because the existing requirement's wording does
not settle what a known zero means.

## Impact

- `app/src/main/java/com/naviveylin/service/NavigationNotificationContent.kt` — `formatDistanceCompact` takes
  `Double?`, and the two call sites pass a nullable distance.
- `app/src/test/java/com/naviveylin/service/NavigationNotificationContentFormatterTest.kt` — one new case
  and the resolver's English form of `nav_hint_distance_eta`.
- No resource, manifest, native, dependency or build-script change.

Rollback: restore the `meters <= 0.0` placeholder branch and the `?: 0.0` call-site fallback; the defect
returns and the new case fails.

Guidelines: `guidelines/UI.md` §1 (the shared notification/car-hint formatter and its parity rule);
`guidelines/Build.md` §2a (test-only iteration) and §4 (gate evidence).
