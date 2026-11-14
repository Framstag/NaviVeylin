# Tasks

## 1. Measure before patching, and let the number gate the rest (design D3)

- [x] 1.1 Read the census on the driven route: plan Dortmund (`51.57246 N 7.46489 E`) → Essen
      (`51.45343 N 6.99432 E`) on the phone and read the line with the `ROUTE route census:` recipe in
      `guidelines/BuildNative.md` §10k. Verify: `motorway=`, `motorway_dest=` and `dest_empty=` are quoted
      verbatim in the change's evidence note. (Spec: `osmscout-jni` — A motorway manoeuvre's step carries the
      signposted destination.)
- [x] 1.2 Settle the gate: `dest_empty > 0` starts task group 2; `dest_empty = 0` stops the change here, and the
      evidence note states that the record reaches every manoeuvre and the loss is therefore the surface, not
      the model (design D3). Verify: the note carries the numbers and the verdict. (No spec citation — this is
      the premise check the archived `motorway-destination-hint` task 1.2 owed.)
      **The written gate did not settle; the owner released it.** Measured, both builds, `evidence/device-census.txt`:
      the issue's route (Dortmund → Essen) reads `motorway=4 motorway_dest=3 dest_empty=0` on the pin *and* on the
      patch, and a probed route (Dortmund → Duisburg) reads `motorway=5 motorway_dest=3 dest_empty=1` on both —
      its empty step, `Keep straight onto A 3`, has no destination tag in the extract and gains no clause from the
      patch. The key cannot tell a lost record from a missing tag, so the two numbers settle neither way; the
      change proceeded on the code-path case (`evidence/red-on-pin.log`) and the deviation was put to the owner,
      who answered on 2026-11-14 that the gate is released for this change: the displacement D1 names is real in
      the description, the census did not exercise it, and the surface observation keeps its own finding
      (`evidence/evidence.md` §5).

## 2. Write the case that is red on the pin (submodule)

- [x] 2.1 Add the case "A motorway change through a link carries the destination of the way it leads onto" to
      `app/src/main/cpp/libosmscout/Tests/src/RouteStepModelTest.cpp`: a description whose destination record
      sits on the node before a `highway_motorway_link` and whose `MotorwayChangeDescription` sits on the link
      node yields a step whose `destination` is the record. Verify: the case is red on the current submodule pin
      and its tests/failures tally is quoted. (Spec scenario: *A change through a motorway link carries the
      record of the node it was decided at*.)
- [x] 2.2 Add the case "A motorway step keeps its own destination when its node carries one": node and the node
      before it carry different records, the step carries its own. Verify: the case is green before and after
      the fix. (Spec scenario: *A step that carries its own record keeps it*.)
- [x] 2.3 Confirm the no-record case still holds: `RouteStepModelTest` "An absent description is absent" is
      green and asserts `destination` absent. Verify: the case is green, quoted. (Spec scenario: *No record on
      the node or the one it was decided at*.)
- [x] 2.4 Pin the scenario on a **motorway** step, which 2.3's street route does not build — review round 1's
      third note: `MotorwayChangeThroughLink` takes `carriesDestinationRecord` and "A motorway change carries no
      destination when no node carries a record" runs the same change with no tag on any way, placing the
      manoeuvre and asserting `!change->destination.has_value()`. Verify: green on the pin (17 cases / 16 passed /
      1 failed, only the change case red) and on the patch (17 cases, 149 assertions), both stamped in
      `evidence/red-on-pin-with-absence-case.log`. (Spec scenario: *No record on the node or the one it was
      decided at*.)

## 3. The fix: carry the record at the manoeuvre placement (design D1, D2, D4)

- [x] 3.1 Add one private helper to `InstructionPostprocessor`
      (`app/src/main/cpp/libosmscout/libosmscout/src/osmscout/routing/RoutePostprocessor.cpp`, declaration in
      `app/src/main/cpp/libosmscout/libosmscout/include/osmscout/routing/RoutePostprocessor.h`): given the node
      that receives the manoeuvre and the node it was decided at, attach the decided-at node's
      `DestinationDescription` when the receiving node carries none. Verify: the header and the translation unit
      compile in the submodule's own target. (Spec: `osmscout-jni` — A motorway manoeuvre's step carries the
      signposted destination.)
- [x] 3.2 Call it from `HandleMotorwayLink`'s change branch and from the direct-change branch, for enter and
      change only, never for leave (design D4). Verify: 2.1 is green, 2.2 and 2.3 stay green. (Spec scenarios:
      the three above.)
- [x] 3.3 Revert-check the own-record invariant (design D2): attach unconditionally (drop the "carries none"
      guard), re-run `RouteStepModelTest` and confirm 2.2 fails, restore, then re-run forced green. Verify: the
      red run and the restored green run are quoted with timestamps. (`guidelines/Build.md` §2, the revert-check
      discipline.)

## 4. Build and the suites the patch can reach

- [x] 4.1 Native compile through the app: `./gradlew :app:assembleMobileDebug` (the bridge must still link
      against the patched description code), or the native configure of `guidelines/Build.md` §12 when the ABI
      set is unchanged. Verify: the build succeeds, and a native failure is reported with its own log line
      rather than patched around.
- [x] 4.2 Green the app suites that read the record, and name any exclusion: `:core` `ManeuverFromStepTest`,
      `ManeuverLabelTest`; `:auto` `NavigationTemplateMapperTest`; `:app` `NextTurnOverlayTest`,
      `CarHintContentTest`, `RoutePanelDiagnosticsTest`. Verify: the focused run is green and the module filters
      are named in the evidence. (Spec: the same requirement; parity is unchanged because one step feeds both
      surfaces.)
- [x] 4.3 Run the gate once over the both-flavor set (`guidelines/BuildEvidence.md` §4b) and record the verdict
      per §4a. Verify: the verdict line, the flavors and the suites are quoted; no native target other than the
      patched description code changed.

## 5. Land it where it is allowed to land, then measure on the device

- [x] 5.1 Apply the patch **inside the submodule**, commit it there, and bump the gitlink in this repository as
      its own commit (`guidelines/Build.md` §12; `guidelines/Design.md` §5 — one place, never both). Verify: the
      superproject diff of the bump is the gitlink alone, and the submodule commit's subject names the change.
- [x] 5.2 Read the census again on the same route and record both lines. Verify: `dest_empty` dropped for the
      manoeuvres that lost a record, `motorway_dest` rose by the same count, or the divergence is explained in
      the note. — **Explained:** both lines are identical between the builds on both routes, and the reason is
      section 2 of `evidence/evidence.md`: no manoeuvre on these routes lost a record before the patch.
- [ ] 5.3 Confirm the driver-visible result on the route's first motorway change: the phone overlay's second
      line and the car panel's target street both read the motorway with its destination. Verify: both strings
      are quoted; a surface that does not show it is reported as its own finding, not folded into this change
      (design D3 — that would be the surface cause, and this change stops rather than guessing).
      **Phone half measured:** `Keep slightly right onto A 40 towards Essen` (row 10 of 21, route A, patched
      build; the same wording on the pin — see task 1.2). **Car half owed:** no head unit and no car session;
      the projection binary needs `lean-ctx allow desktop-head-unit` and a human at the DHU window
      (`guidelines/BuildNative.md` §2b, §10a).
- [x] 5.4 State whether any guideline sentence is superseded. Verify: the note names
      `guidelines/DesignNative.md` §5a, §5b and `guidelines/BuildNative.md` §12 as unchanged, or the edited line is
      listed.
