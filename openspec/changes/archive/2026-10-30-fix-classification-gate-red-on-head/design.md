# Design — fix-classification-gate-red-on-head

## Open decisions

none

The fix is one data line per capability plus one area id per task text, and the root cause decides every choice
below.

## Root cause, and the alternatives the evidence eliminated

The classification gate fails on HEAD because the classification file and the capability set disagree, and the
two ids it names are precisely the capability ids the two open changes' delta specs introduce. The gate's own
report names the id and the owning change, so no second hypothesis survives: nothing in the shipped set is
unclassified (the three `stale:` lines are the opposite kind of disagreement and are reported, not failed).

Under the config's bug-fix rule the design lists the alternatives the evidence *eliminated*, not viable options:

- **Wait for each open change to pay its own line (their task 6.4).** Impossible as a fix for a red-on-HEAD claim:
  both changes are entirely unapplied (`0` of `17` and `0` of `20` task markers checked), so HEAD stays red until
  both are finished, and every change that touches an `openspec/*` path inherits that red — the loop's gate maps
  any such path to `--check-classification` (`tools/loop/loop.conf`). Worse, both task texts name an area
  `areas.json` does not carry, so the line they would write fails the gate on the unknown area id
  (`tools/gen-feature-list.sh:199-231`): waiting reproduces the defect rather than paying it.
- **Add `specs-and-process` to `areas.json` so the two task texts become true.** Impossible without inventing a
  second internal area: `areas.json` carries `id`, `heading`, `order` and `carOnly` per area, `build-tooling` is
  already *the* area whose members are all `userVisible: false` ("An area whose members are all classified
  userVisible=false is rendered nowhere and therefore needs no heading of its own", `areas.json`'s own comment),
  and a second such area would render nothing while splitting the tooling capability set across two ids.
- **Leave the gate red and record it.** Impossible: the item's claim is exactly that the gate is red on HEAD, and
  a recorded red gate blocks every other change's `openspec/*` work — the state issue #128 exists to end.

## D1 — The two capabilities are internal tooling, area `build-tooling`

Both capabilities describe how the tree's own process works, not anything a person sees on the map, in a list or
in the car:

- `change-readiness` (`openspec/changes/change-readiness-gate/specs/change-readiness/spec.md`, "Purpose": what it
  means for a change to be ready to be worked by an unattended process) — an internal check over `design.md` and
  the scenarios a change owes.
- `shaping-inbox` (`openspec/changes/shaping-inbox/specs/shaping-inbox/spec.md`, "Purpose": the state an item
  carries when a run's verdict was that a person must reshape it) — an internal label and queue over the tracker.

Therefore `userVisible: false` and `surfaces: []`, which `specs.json`'s own comment legalises only for a spec that
is not a surface ("an empty list is legal only with userVisible=false, because the catalogue can render no tag for
it"), and the area is the one internal area the catalogue already has. Rejected: `settings-legal` and
`specs-and-process`. The first renders a heading for user-facing settings and legal text and would publish a
process rule as a user feature; the second does not exist, which is the second half of this defect.

## D2 — This change pays the line, and the two task texts are corrected with it

The line lives in one shared file, so it is the same one-line edit whoever commits it; committing it here is what
makes the tree green now, and it stays correct when the two changes archive, because the entries already name
them. The task texts are corrected in the same commit because leaving `specs-and-process` in place hands the next
applier of either change a step whose own verification fails — the correction is one word per file and is the
defect's second half, not a rewrite of another change's plan. Neither change's requirement text, design or
scenario is touched.

## D3 — Verification

No compiled file, no test source, no Gradle script is touched, so the scope block of `guidelines/Build.md` §2
applies: no build and no suite is owed, and the structural checks stand in.

- `bash tools/gen-feature-list.sh --check-classification` — exit 1 with two unclassified ids before, exit 0 after;
  both runs are the change's evidence (`evidence/classification-gate-red-on-HEAD.txt`,
  `evidence/classification-gate-green-after-fix.txt`).
- `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.featurelist.ShippedSpecClassificationTest"` — the
  Kotlin case that reads the same two artefacts, run through the module filter, so the classification file is
  still readable and still names every shipped spec (`evidence/TEST-ShippedSpecClassification-green.xml`). It does
  **not** read delta ids, so it cannot show the fix; it guards against a malformed JSON edit, which is the one way
  this change could break something.
- `openspec validate fix-classification-gate-red-on-head --strict` — exit 0.
- `tools/check-doc-routes.sh` — exit 0 (no routing row is added or repriced).

Exclusions named: the both-flavor forced gate, `:auto`, `:core` and the JNI suite are not run — nothing they
compile or read is touched by this delta. No on-device step: no surface changes, so no pixel or diagnostics
measurement can say anything about this change. Threading model: not applicable, no new component.

## Risks

- **A malformed `specs.json` breaks the catalogue build.** Bounded by the JSON parse at the top of
  `gen-feature-list.sh` (a non-parsing file exits 2 with a message) and by the Kotlin case above; both are in the
  task list.
- **The two capabilities' classification may later disagree with what the changes ship.** If either change is
  reshaped into a user-visible capability, its own delta is where the classification changes; it already owes that
  edit in task 6.4's verification.
