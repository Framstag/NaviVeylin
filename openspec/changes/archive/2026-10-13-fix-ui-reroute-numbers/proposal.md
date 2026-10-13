# Proposal — fix-ui-reroute-numbers

## Why

Root cause: `guidelines/UI.md`'s phone/AA reroute-parity bullet is the guideline's only statement of the
reroute policy's numbers, and the 2026-10-12 latency change restated them in spec `reroute-trigger` and in the
engine's constants without touching that bullet — the change named `guidelines/Design.md` §3, which states none
of them — so the bullet kept the retired 50 m / 10 s pair.

Evidence:    `com.naviveylin.navigation.ReroutePolicyGuidelineParityTest#the reroute parity bullet states the policy the engine applies`
fails on HEAD — XML `tests="1" skipped="0" failures="1" errors="0"`, `timestamp="2026-10-13T19:25:47.532Z"`,
failure `java.lang.AssertionError: the bullet's fast path must be the engine's FAST_PATH_DISTANCE_M expected:<30.0> but was:<50.0>`;
the same run's `system-out` carries both policies —
`reroutePolicy guideline={fast path=50.0, confirmation floor=10.0, cooldown=25.0, accuracy guard=100.0, tunnel guard=30.0} engine={fast path=30.0, confirmation floor=4.0, cooldown=25.0, accuracy guard=100.0, tunnel guard=30.0}`
— retained as `evidence/TEST-reroute-policy-guideline-parity-red-on-HEAD.xml`.

Repro:       `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.navigation.ReroutePolicyGuidelineParityTest" -PforceTests --rerun-tasks`

Spec:        `reroute-trigger` / Fast first reroute trigger, Distance-based fast path   Guideline:
`guidelines/UI.md` §8 "Phone navigation overlay sizing (driver-seat readability)", bullet "Phone/AA reroute
parity" (`:870`, the numbers at `:872`)

Authority — the guideline is the stale side, on the two numbers it moved:

- **The code applies 30 m and 4 s.**
  `app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt:1141` `internal const val MIN_OFF_ROUTE_DURATION_MS = 4_000L`
  and `:1143` `internal const val FAST_PATH_DISTANCE_M = 30.0`; the engine builds its gate from them
  (`:206-211`), and `RerouteConfirmationGate.kt:24`/`:27` carries the same pair as the class default
  (`4_000L`, `30.0`).
- **The spec states both numbers.** `openspec/specs/reroute-trigger/spec.md:10` — "The engine's own confirmation
  floor SHALL be 4 seconds of continuous off-route reporting" — and `:25` — "the vehicle's distance from the
  active route polyline exceeds 30 meters". The same file's latency requirement stays "within 10 seconds", so
  the bullet's `10 s` names the confirmation floor, not that bound.
- **The change that moved them is archived and says so.**
  `openspec/changes/archive/2026-10-12-tune-reroute-trigger-latency/proposal.md:27` "Lower the engine's off-route
  confirmation floor `MIN_OFF_ROUTE_DURATION_MS` **10 000 → 4 000 ms**", `:29` "Lower the distance fast path
  `FAST_PATH_DISTANCE_M` **50 → 30 m**", and its `Capabilities` section syncs `reroute-trigger`. Nothing in that
  change or after it states 50 m / 10 s.
- **The three other numbers the bullet states already agree** with `:1138` (`MAX_REROUTE_ACCURACY = 100.0`),
  `:1142` (`REROUTE_COOLDOWN_MS = 25_000L`) and `:1144` (`TUNNEL_REROUTE_GUARD_MS = 30_000L`): the divergence is
  the pair the tuning change moved, not the whole bullet.
- **The guideline's own honesty rule declares this document the condensed record, so the fix updates the
  numbers instead of deleting them.**
  `guidelines/UI.md` "Keeping this document honest" (`:1266`, its bullets `:1268-1270`) — "Specs are the
  contract; this document is the condensed knowledge base" — and §8's sibling bullet (`:839-845`, the density
  rule) carries the numbers of a code-side constant and names the case that guards it. Deleting the numbers in
  favour of a reference is eliminated: this document is the condensed record a reader takes as current, which
  is the bullet's stated job.

## What Changes

- **`guidelines/UI.md` §8**, the "Phone/AA reroute parity" bullet (`:872`): the policy reads **30 m fast path,
  4 s confirmation floor**, 25 s cooldown, 100 m accuracy guard, 30 s tunnel guard — the three unchanged guards
  stay as they are — and the bullet names the engine constants it mirrors and the case that holds it to them.
  "Confirmation window" becomes "confirmation floor" because that is the term the spec uses for the 4 s and
  because the gate's window is a different number the bullet never named (`REROUTE_CONFIRM_WINDOW_MS`, 60 s, the
  episode window after which the count starts fresh).
  This is the change's single fix: the guideline sentence was the only artefact still stating the retired pair.
- **`app/src/test/java/com/naviveylin/navigation/ReroutePolicyGuidelineParityTest.kt`** (new): the conformance
  case that reads the bullet's five numbers and the engine's five constants and asserts they agree, printing
  both policies into the XML's `system-out`. It is the red case above and it stays as the guard: a policy move
  now has to update the bullet in the same change, which is the rule the guideline's honesty section asks for
  and which the tuning change (naming `Design.md` §3) had no pointer to.
- **No production code changes.** The engine's behaviour is what the spec, the gate's defaults and
  `RerouteConfirmationGateTest`/`NavigationEngineRerouteTest` already pin.
- **No test expectation moves**: the constants the change quotes are the ones those cases build from
  (`RerouteConfirmationGateTest.kt:31-36`), and neither case states the guideline's numbers.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

(none — the requirement ships and already states the new numbers: `reroute-trigger`'s "Fast first reroute
trigger" (`:10`) and "Distance-based fast path" (`:25`) were synced by the tuning change, and this change's
file roster is one guideline bullet and one test source. No requirement text changes, so this change declares
`skip_specs: true` — the same reasoning as the archived `2026-10-10-fix-shipped-spec-classification`
(`skip_specs: true`, "no requirement text changes"))

## Impact

Affected files:

- `guidelines/UI.md` — one bullet of §8: two numbers, `confirmation window` → `confirmation floor`, and the
  clause naming the constants and the guard case. Nothing else in the document moves.
- `app/src/test/java/com/naviveylin/navigation/ReroutePolicyGuidelineParityTest.kt` — new test class, one case.

Blast radius: the bullet is read by the §8 reader and by the routing table's phone-UI route; the case reads
`guidelines/UI.md` and the `NavigationEngine` companion through the `:app` unit-test working directory, so it
adds one class to the `:app` suite of **both** flavors (the file is shared source, no flavor input) and to
nothing else. No API, no persisted state, no layout, no manifest, no flavor definition, no native input.

Rollback: restore the bullet's two numbers and delete the test file — additive and reversible, one `git revert`
of the two files; no production behaviour to revert.

Not affected, named explicitly: `guidelines/Design.md` §3 (the tuning change named it; it states none of these
numbers — checked with the same grep the entry records), the spec files under `openspec/specs/`, and the
`reroute-trigger` cases.

**Not changed, deliberately**: the bullet's historical clause "the car's former 25 m / 15 s / 10 s-interval path
is gone" is accurate (`openspec/changes/archive/2026-09-27-one-navigation-engine/proposal.md:11,120-121` record that
car path),
and the ~9 s end-to-end figure the entry's fix candidate suggests adding is spec'd in `reroute-trigger`'s
latency requirement — adding a restatement of it here would be a second claim with no guard of its own.
