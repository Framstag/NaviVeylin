# Proposal — fix-ui-navigation-content-zero-distance

## Why

**The condition, verified in the tree at base `42d3e00` (2026-10-29).** `guidelines/UI.md:231` opens §1's
*Ongoing navigation notification (background driving)* NAVIGATION content bullet, and `:231`–`:236` names the
destination, the manoeuvre instruction, the distance-to-turn, arrival/remaining time, remaining distance and the
stop action. The bullet carries no zero-versus-unknown distance rule: `sed -n '231,236p' guidelines/UI.md` reads
the field list alone. The shipped contract states the rule at
`openspec/specs/navigation-ongoing-notification/spec.md:156` — requirement *A known zero distance is not an
unknown distance*: a live step's `remainingDistance` of `0.0` renders `0 m`, while an absent or `NaN` distance
renders `--`, and the phone stats line and the car hint's secondary text follow the same rule.

**Root cause, one sentence.** `guidelines/UI.md` §1's NAVIGATION content bullet was written from the earlier
content requirements and never gained the zero-versus-unknown rendering rule that
`2026-10-28-fix-zero-distance-reads-zero-not-unknown` added to the contract, so the document `AGENTS.md` routes
a notification-content change to lists the fields without the rule that distinguishes their edge values.

**Red test** (`evidence/red-on-head.xml`, `evidence/red-on-head.log`, `evidence/red-probe.sh`, run on the base
commit's content at 2026-10-29T07:30:05Z): the probe reads the NAVIGATION content bullet and requires the rule's
wording; on HEAD it reports `tests=1 failures=1`:

```
FAIL ui-navigation-content#zero-versus-unknown — the NAVIGATION content bullet does not state:
  [known zero] [`0 m`] [absent or `NaN`] [`--`] [stats line] [hint's secondary text]
  [navigation-ongoing-notification]
```

**Surface.** `tools/look.sh 'zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown|known zero distance|A known
zero distance is not an unknown distance' --files` reports 19 matches in 10 files; of those, exactly one shipped
Gradle case asserts the behaviour (`NavigationNotificationContentFormatterTest`
"zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown") and exactly one shipped spec scenario names it (the
requirement above). This change adds one evidence probe case and edits one guideline bullet; it adds no Gradle
case, rewrites no case and realigns no fixture.

surface: 1 guideline bullet edited, 1 evidence probe case added, 0 Gradle cases added, 0 cases rewritten,
0 fixtures realigned — `tools/look.sh 'zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown|known zero
distance|A known zero distance is not an unknown distance' --files` (19 matches in 10 files; the only shipped
case is `NavigationNotificationContentFormatterTest` "zeroDistanceReadsZeroWhileAnUnknownDistanceStaysUnknown").

**Owning capability and guideline section.** The behaviour is owned by
`openspec/specs/navigation-ongoing-notification/spec.md` — *A known zero distance is not an unknown distance*.
The restatement belongs in `guidelines/UI.md` §1 "Cross-variant UI parity (general rule)", subsection "Ongoing
navigation notification (background driving)", which `AGENTS.md`'s parity row names. `guidelines/UI.md`
"Keeping this document honest" requires the guideline to be updated when a spec requirement changes what a
surface reads. This is a documentation-only change: it states no new behaviour, so `.openspec.yaml` sets
`skip_specs: true` and no capability delta is invented.

## What Changes

- Append one clause to the NAVIGATION content bullet in `guidelines/UI.md`: a known zero distance renders
  `0 m`, an absent or `NaN` distance renders `--`, and both the phone stats line and the car hint's secondary
  text follow that rule, citing `navigation-ongoing-notification` — *A known zero distance is not an unknown
  distance*.
- Add the change's evidence probe (`evidence/red-probe.sh`), which reads that bullet and fails on HEAD and passes
  after the edit.
- No production code, no test source and no capability spec changes; no Gradle build or device run is owed.

## Capabilities

### New Capabilities

- None — this is a documentation correction, not a new system behaviour.

### Modified Capabilities

- None — the requirement *A known zero distance is not an unknown distance* already ships; the guideline was
  missing its restatement. `skip_specs: true` records that no spec-level behaviour changes.

## Impact

- `guidelines/UI.md` — one clause in §1's NAVIGATION content bullet.
- `openspec/changes/fix-ui-navigation-content-zero-distance/**` — the planning artifacts and evidence.
- No module, resource, API, dependency or test source is touched.
