# Design

## D1 — The decision: `Locale.US` on the phone, `Locale.ROOT` on the car

The parked question was exactly this pick (issue #44, loop verdict 2026-10-09: "Nothing in the evidence picks
one, and the pick is the whole change"). Decided by the operator on 2026-10-24, and the reasons are in the
tree rather than in taste:

1. **The two constants are byte-identical on these patterns.** Measured 2026-10-09 with a single-file
   `java -Duser.language=de -Duser.country=DE LocaleProbe.java`: on `%.1f`/`%.2f`/`%.0f`, `Locale.ROOT` and
   `Locale.US` print the same digits and the same dot. The choice therefore buys no output — it buys a rule
   that can be stated once and a constant that is not the forbidden one.
2. **`i18n-l10n` names `Locale.ROOT` as the constant numeric formatting must not use** ("No user-facing
   numeric formatting SHALL use `Locale.ROOT` or a fixed locale", and `guidelines/UI.md` §10 "Do NOT use
   `Locale.ROOT` for display formatting"). A phone-side `Locale.ROOT` would need a carve-out on every future
   reading, and the re-center line is genuinely read as text in the diagnostics view (it is file-backed).
3. **`:app` and `:core` already have a fixed-locale idiom for machine-parseable text** — `Locale.US` in
   `DiagnosticsLog` (the timestamp/parse pair), `CoordinateFormat`, `NavigationEngine` — and no `Locale.ROOT`
   numeric site at all. The phone half of this rule follows the module it lands in; the car half keeps the
   constant its own module already chose, and neither module needs to know the other's.

Rejected: a `:core` `DiagnosticNumbers` helper. It would decide the constant in one place for both modules,
but it would also put a shared module in the middle of two surfaces whose lines differ in every field, and
the phone's helper is four call sites in one package.

## D2 — Why the `follow` line becomes a builder

The other three sites are asserted where they are written: the GPS-fix line through a Robolectric capture of
logcat, the re-center line through the class that already owns its trigger. The `follow` line has no such
home — `grep -rn 'MapCanvasScreen(' app/src/test/` is empty, and reaching it would need a rendered bitmap, a
render viewport and a non-empty canvas inside the frame-loop `LaunchedEffect` — so the line moves into
`followDiagnosticLine`, the shape the car change chose for its own line. Field order and the `-` placeholders
are preserved verbatim, so the recipe's grep is unaffected.

## D3 — What is deliberately not changed

- The display formatters in `ui/mapmanager/` (`"%.1f MB"`) keep the device locale: they are display text, and
  `i18n-l10n` requires locale-correct separators for them.
- `avg`, `eff` and `savg` keep printing `NaN` for an unknown value instead of gaining a `-` placeholder: that
  is a behaviour change to the line, not a locale change, and the recipes do not read those three as flags.
- The 500-character requirement-body limit: the log rule lands as its **own** requirement rather than as a
  fourth paragraph of *Locale-aware number formatting*, whose body was already over the limit (830 characters
  at `a0ca0f35`) — this change adds to that debt nothing.
