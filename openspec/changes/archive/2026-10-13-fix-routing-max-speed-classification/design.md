# Design — fix-routing-max-speed-classification

## D1 — Root cause (measured, not inferred)

`openspec/specs/routing-max-speed/spec.md:1` ships (archived by `be53cdd
feat: vehicle-max-speed-settings`), while `tools/feature-list/specs.json` carries no `routing-max-speed` key:
at HEAD `fa6151c` the gate counts 166 shipped spec ids and 165 classified ones
(`evidence/classification-gate-red-on-HEAD.txt`), and the only unclassified id is `routing-max-speed`. The
capability was published without the classification line `guidelines/FeatureList.md` §9 owes to the same
change ("Archiving a capability spec: add its classification line (`specs.json`) in that same change").

Consequence, measured: `spec-feature-index`'s requirement "An unclassified spec id stops the run" is
contradicted by the repository's own data — `bash tools/gen-feature-list.sh --check-classification` exits 1
and names the id, and `ShippedSpecClassificationTest#every shipped spec id is classified`
(`app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt:40`) fails, which reddens every
`:app` gate run in the tree until the line exists.

## D2 — The single fix

Add one entry to `tools/feature-list/specs.json`, in the `routing` group between `route-summary-dialog` and
`routing-summary`:

```json
    "routing-max-speed": { "area": "routing", "userVisible": true, "surfaces": ["phone", "car"] },
```

Each of the three values is read off the shipped spec and the catalogue's own rules, not chosen:

- **`area: "routing"`** — `openspec/specs/routing-max-speed/spec.md`'s Purpose is the contract between the
  stored per-vehicle maximum speed and the routing profile, and four of its six requirements are routing
  behaviour (what the profile carries, the per-vehicle semantics, the fallback, that a change applies to the
  next calculation). Its siblings in the same area are the profile's consumers and producers —
  `navigation-engine`, `route-panel-ui`, `route-calculation-feedback`, `auto-navigation-hints`. The
  *editor* UI the user touches is specified separately, in `location-options-ui` (classified
  `map-interaction`), so the settings surface is not what this spec owns.
- **`userVisible: true`** — a capability the user configures: `guidelines/UI.md` documents the three sliders
  (car 30-160, bicycle 5-40, pedestrian 2-15 km/h; defaults 120 / 15 / 5) as a phone-editable setting whose
  value the car reads. `specs.json`'s comment defines `userVisible` as whether the spec's capabilities are
  claims the catalogue may make; this one may be claimed. It is also a precondition of publishing: the prose
  gate refuses a bullet whose key names a spec that is not user-visible (`tools/feature-list/gate.awk`).
- **`surfaces: ["phone", "car"]`** — `specs.json`'s comment defines `surfaces` as the platforms the capability
  reaches. The spec's second requirement is "The setting is shared by the phone and the car" with the
  scenario "Car routing uses the phone's car value" (a destination acquired on the car uses the stored value
  through `NavigationEngine`), and the consuming spec `navigation-engine` is classified `phone, car`. The
  *editor* is phone-only, which is why `location-options-ui` is classified `phone`; the value's reach is not.

## D3 — Alternatives the evidence eliminated

1. *Leave the classification red and fix something else.* Impossible: the missing line is the only
   unclassified id (`unclassified: 1`, `stale: 0`), and the requirement names an unclassified id as a defect,
   so no other repair makes the gate green.
2. *Drop or rename the shipped spec so the directory matches the file.* Impossible: the capability is shipped,
   referenced by `location-options-ui` and `route-panel-ui`, and documented in `guidelines/UI.md`; removing a
   shipped spec is a product change, not a data repair, and it would strand those references.
3. *`userVisible: false`.* Eliminated by the spec's own subject and by the gate: the setting is user-facing
   (`guidelines/UI.md`), and `gate.awk` fails a bullet whose source key names a non-user-visible spec, so an
   internal classification would make the capability permanently unpublishable and silently dropped — exactly
   what §4 refuses ("no user-visible spec is dropped silently").
4. *`surfaces: ["phone"]`.* Eliminated by the spec's requirement "The setting is shared by the phone and the
   car" and its car scenario, and by the consuming `navigation-engine` being `phone, car`.
5. *`area: "settings-legal"`.* Eliminated by what that area holds — `about-dialog`, `compass-settings`,
   `auto/preferences`, `settings-persistence`: the app's legal and settings *surfaces*. This spec contains no
   settings-screen claim (that is `location-options-ui`), so classifying it there would put a routing-profile
   contract under *Settings, Data & Legal* and leave the routing area's profile contract incomplete.
6. *Add a highlight to `highlights.json` and regenerate `FEATURES.md`.* Eliminated as unnecessary and
   out of scope: coverage is measured over highlights, not specs (`tools/gen-feature-list.sh:691-723` checks
   that every highlight is reached and that every cited spec is shortlisted), so no gate requires a spec to
   have a highlight; adding one would dirty the routing area and demand a phrasing run for a document whose
   "is it worth telling a user about" judgement §9 records as a separate, editorial step.
7. *Invent a spec delta.* Eliminated: the requirement already exists — `spec-feature-index` / "An unclassified
   spec id stops the run", scenario "A classified spec id passes" — and the repository's data is what fails
   it. Identical requirement text is not a delta, `openspec` refuses a zero-delta change without
   `skip_specs: true`, and the config forbids inventing a requirement to satisfy validation. The archived
   `2026-10-10-fix-shipped-spec-classification` took the same shape for the same class of gap.

No alternative leaves a viable option to weigh, which is why the entry passed the loop's gate condition 3.

## D4 — Blast radius

`tools/feature-list/specs.json` is read by `tools/gen-feature-list.sh` and by
`ShippedSpecClassificationTest`; it is not a Gradle input and nothing compiles it. The added entry:

- flips `--check-classification` from exit 1 to exit 0 and `unclassified: 1` to `0`;
- adds `routing-max-speed` to the shipped-set index of the `routing` area (166 classified ids);
- changes no existing entry, no area id, no heading and no order in `areas.json`.

## D5 — Rollback

Revert the two files of the change: delete the `routing-max-speed` line from
`tools/feature-list/specs.json` and the §189 bookkeeping in `TODO.md`. Both are data; there is no migration, no
stored state, no API and no on-device artefact. The classification gate returns to the exact red the evidence
files record.

## D6 — Verification

The change adds no invariant beyond the one the requirement already states (nothing compiled, no new
behaviour), so there is no revert-check task: the red-before/green-after pair on the same guard case is the
falsification, and the mutation it would have used (deleting the added line) is literally the HEAD state the
red XML records.

Nothing compiled changes and no test source changes, so `guidelines/Build.md` §2's scope block applies: no
suite is owed. The verification is:

1. structural — `jq` parses `specs.json` and the key is present with the three values;
2. `bash tools/gen-feature-list.sh --check-classification` exits 0 with
   `specs read: 166 / classified: 166 / unclassified: 0 / stale: 0`;
3. the guard case re-run **forced** through the filter form — `./gradlew :app:testMobileDebugUnitTest --tests
   "com.naviveylin.featurelist.ShippedSpecClassificationTest" -PforceTests --rerun-tasks` — because it is the
   case the red XML belongs to and its verdict is what the change exists to flip. The case reads its two
   inputs by path, so its verdict is flavor-independent data; the affected module is `:app`, whose mobile
   flavor supplies the XML the red run came from.

The suite gate is **not** run and not claimed: nothing compiled changes and no test source changes, so the
scope block owes none, and a whole-suite forced gate is red on this repository for reasons unrelated to this
delta (Compose suites time out in the whole-suite gate), so it could not settle this change either way. The
`:app` suites, `:auto`, `:core` and the JNI suite are the named exclusions.
