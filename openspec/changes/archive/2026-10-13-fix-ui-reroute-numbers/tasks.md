# Tasks — fix-ui-reroute-numbers

## 1. Red proof on HEAD

- [x] 1.1 Add `app/src/test/java/com/naviveylin/navigation/ReroutePolicyGuidelineParityTest.kt` — the
      conformance case that reads the "Phone/AA reroute parity" bullet of `guidelines/UI.md` through the
      `:app` unit-test working directory (`File("..")`, the `FavAutoZoomClampRangeTest` convention) and compares
      its five policy numbers to `NavigationEngine`'s constants, printing both into the XML's `system-out`.
      Verify it compiles and runs.
      — ran 2026-10-13T19:24:31Z→19:25:47Z: `ReroutePolicyGuidelineParityTest > the reroute parity bullet states
      the policy the engine applies FAILED`, i.e. the class compiled and the case executed on HEAD `417adf7`.
- [x] 1.2 Record the red run and retain its JUnit XML in `evidence/`. Verify the XML carries `tests="1"
      failures="1"` and a timestamp after the test file's creation.
      — ran 2026-10-13T19:25:47.532Z: XML `tests="1" skipped="0" failures="1" errors="0"`, failure
      `java.lang.AssertionError: the bullet's fast path must be the engine's FAST_PATH_DISTANCE_M
      expected:<30.0> but was:<50.0>`; `system-out`
      `reroutePolicy guideline={fast path=50.0, confirmation floor=10.0, cooldown=25.0, accuracy guard=100.0,
      tunnel guard=30.0} engine={fast path=30.0, confirmation floor=4.0, cooldown=25.0, accuracy guard=100.0,
      tunnel guard=30.0}`; retained as `evidence/TEST-reroute-policy-guideline-parity-red-on-HEAD.xml`.
- [x] 1.3 Verify the claim on this tree and the authority of the code side: `guidelines/UI.md:872` states "50 m
      fast path, 10 s confirmation window"; `NavigationEngine.kt:1141` `MIN_OFF_ROUTE_DURATION_MS = 4_000L` and
      `:1143` `FAST_PATH_DISTANCE_M = 30.0`; `openspec/specs/reroute-trigger/spec.md:10,25`; and the archived
      tuning change's `openspec/changes/archive/2026-10-12-tune-reroute-trigger-latency/proposal.md:27,29` records
      `10 000 → 4 000 ms` and `50 → 30 m`. Verify by quoting each
      line (done — see `proposal.md`'s "Authority" list).
      — ran 2026-10-13: all four quotes carry the numbers the proposal states;
      `grep -n "reroute" guidelines/UI.md` → `:870` is the document's only policy bullet, and
      `guidelines/Design.md` states none of these numbers.

## 2. The fix — the bullet states the policy the engine applies

- [x] 2.1 Edit the bullet at `guidelines/UI.md:872`: `50 m fast path, 10 s confirmation window` → `30 m fast
      path, 4 s confirmation floor`, and name the constants it mirrors (`FAST_PATH_DISTANCE_M`,
      `MIN_OFF_ROUTE_DURATION_MS`, `REROUTE_COOLDOWN_MS`, `MAX_REROUTE_ACCURACY`, `TUNNEL_REROUTE_GUARD_MS`)
      and the guard case (`ReroutePolicyGuidelineParityTest`). Leave the three agreeing guards and the
      historical car-path clause as they are. Verify with `git diff --stat guidelines/UI.md` (1 file, no other
      line) and `sed -n '870,880p' guidelines/UI.md`.
      — ran 2026-10-13T19:29Z: `git diff --stat guidelines/UI.md` → `1 file changed, 5 insertions(+), 1
      deletion(-)`; the bullet (`:870-881`) now reads the five policy numbers, the five constant names and the
      guard case, with the car-path clause untouched.
- [x] 2.2 Verify no stale policy wording survives: `grep -nE "50 m|10 s confirmation" guidelines/UI.md` →
      only unrelated hits if any, none in the reroute bullet.
      — ran 2026-10-13T19:29Z: `grep -nE "50 m|10 s confirmation" guidelines/UI.md` → no output (exit 1).
- [x] 2.3 Run the docs delta's own structural checks (`guidelines/Build.md` §2, the scope block) — no build and
      no suite is owed by a `guidelines/**` + test-source delta, so the checks CI already runs for it stand in:
      `bash tools/check-doc-routes.sh` exits 0, and the OpenSpec artifact-hygiene loop over
      `openspec/changes/*/tasks.md` (`^- \[[ x]\]` markers, no bare `1. [x]`) reports nothing for this change.
      Verify each exits 0 and quote its output.
      — ran 2026-10-13T19:29Z: `bash tools/check-doc-routes.sh` → exit 0, `check-doc-routes: all 82 section
      references resolve`; the hygiene loop (the CI step's inline `grep`/`awk` bodies) → exit 0, no error line;
      `openspec validate fix-ui-reroute-numbers` → `Change 'fix-ui-reroute-numbers' is valid`; `openspec doctor`
      → `OpenSpec root: ok`.

## 3. The guard case turns green

- [x] 3.1 Re-run the case **forced** through the filter form —
      `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.navigation.ReroutePolicyGuidelineParityTest" -PforceTests --rerun-tasks` — and quote the
      tally from its XML, with a timestamp after the guideline edit. Copy that XML into `evidence/` as
      `TEST-reroute-policy-guideline-parity-green-mobile.xml`.
      — ran 2026-10-13T19:30:25.181Z: `BUILD SUCCESSFUL in 1m 12s`, XML
      `tests="1" skipped="0" failures="0" errors="0"`, `system-out`
      `reroutePolicy guideline={fast path=30.0, confirmation floor=4.0, cooldown=25.0, accuracy guard=100.0,
      tunnel guard=30.0} engine={…}` — the two policies equal; XML retained as the evidence file named above.
- [x] 3.2 Run the same case through the other `:app` flavor —
      `./gradlew :app:testAutomotiveDebugUnitTest --tests
      "com.naviveylin.navigation.ReroutePolicyGuidelineParityTest" -PforceTests --rerun-tasks` — and retain its
      XML as `evidence/TEST-reroute-policy-guideline-parity-green-automotive.xml`. The test file and the
      guideline are shared source, so both flavors must execute it.
      — ran 2026-10-13T19:33:08.185Z: `BUILD SUCCESSFUL in 1m 14s`, XML
      `tests="1" skipped="0" failures="0" errors="0"`, `system-out` carries both policies equal; XML retained as
      the evidence file named above.

## 4. Revert-check — the mutation targets the code constant

- [x] 4.1 Mutate the production file, not the test: change `NavigationEngine.kt`'s `FAST_PATH_DISTANCE_M` from
      `30.0` back to `50.0`, re-run the case forced, and confirm it fails for the right reason — the failure
      message names `FAST_PATH_DISTANCE_M` and the guideline's value — and quote the message plus its
      timestamp. Retain the mutation run's XML as
      `evidence/TEST-reroute-policy-guideline-parity-revert-check-mutation.xml`. Restore the constant and verify
      `git diff app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` prints nothing.
      — ran 2026-10-13T19:34:49.987Z, mutation (marker `// REVERT-CHECK MUTATION (task 4.1)`)
      `internal const val FAST_PATH_DISTANCE_M = 50.0`: the case failed at its own assertion —
      `java.lang.AssertionError: the bullet's fast path must be the engine's FAST_PATH_DISTANCE_M
      expected:<50.0> but was:<30.0>`, `system-out`
      `guideline={fast path=30.0, …} engine={fast path=50.0, …}` — XML `tests="1" skipped="0" failures="1"
      errors="0"`; `BUILD FAILED in 1m 9s`; retained as the evidence file
      named above. Restored the
      line, removed the marker: `grep -rn 'REVERT-CHECK MUTATION' app/src/` → no hit, and `git diff
      app/src/main/java/com/naviveylin/navigation/NavigationEngine.kt` → empty.
- [x] 4.2 Force the case green again after the restore and quote the tally with a timestamp after the mutation
      run, so the pair (red under mutation, green restored) is the change's revert-check record.
      — ran 2026-10-13T19:37:18.549Z (case) / 19:37:18.427Z (`RerouteConfirmationGateTest`): both forced in one
      run — `BUILD SUCCESSFUL in 1m 4s`, XML
      `tests="1" failures="0"` and `tests="14" failures="0"`; the case's XML retained as
      `evidence/TEST-reroute-policy-guideline-parity-green-after-revert-check-mobile.xml` and the class's as
      `evidence/TEST-reroute-confirmation-gate-green-after-revert-check-mobile.xml`.

## 5. Gate shape — why the affected cases, not the suite

- [x] 5.1 State the scope-block justification (`guidelines/Build.md` §2, "Scope — what the changed files owe"):
      this delta adds one test source under `app/src/test/**` and edits one guideline file, so the form applied
      is the *only test sources changed* case — the new case plus the affected cases through the `:app` module
      filter, in both flavors (tasks 3.1/3.2/4.1/4.2) — and the guideline's own structural checks (task 2.3)
      stand in for the rest. **Exclusions named:** the `:app` suites of both flavors, `:auto`, `:core` and the
      JNI suite were not run — the delta compiles no production Kotlin and changes no flavor input, and the
      guideline edit is not a Gradle input. The known pre-existing `MapCanvasViewModelModeTest` flake
      (`TODO.md` §148) is not in scope of a filtered run; if it appears, it is attributed, never passed on a
      re-run.
      — recorded 2026-10-13: the filtered runs above are the change's whole gate (case red on HEAD, green in
      both flavors, red under the mutation, green after the restore). Before them, the suite was grepped for
      the constants the guard reads — `grep -rn "FAST_PATH_DISTANCE_M\|MIN_OFF_ROUTE_DURATION_MS\|REROUTE_COOLDOWN_MS\|MAX_REROUTE_ACCURACY\|TUNNEL_REROUTE_GUARD_MS" app/src/test app/src/androidTest`
      → only `RerouteConfirmationGateTest.kt` (which builds the gate from those constants and keeps its
      behaviour) and the new case itself, so **no test expectation moves**; `grep -rn 'File("\.\./guidelines'
      app/src/test` → only the new case reads a guideline file. `MapCanvasViewModelModeTest` did not run and no
      flake was attributed.

## 6. Bookkeeping (the loop's later phases, not this apply pass)

- [x] 6.1 `TODO.md` §187: `status: open` → `status: fixed-by fix-ui-reroute-numbers`, plus one `**Loop
      verdict**` bullet naming the red case, the corrected pair and the eliminated alternative (deleting the
      numbers in favour of a reference). Re-run the structure checks (`##` count == `**id:**` count, a metadata
      line under every heading, no id collision). Correction while there: the entry's anchor
      (`guidelines/UI.md:838`) is off by 34 — the bullet's numbers are at `:872`, in §8, not the §3 the entry
      names.
      — done 2026-10-13: `**status:** fixed-by \`fix-ui-reroute-numbers\``, one `**Loop verdict**` bullet, and
      the entry's own anchor corrected (`:838`/§3 → `:872`/§8); the §200 entry this change filed was added to
      the **Clusters** index (`specs-and-process` — improvement), which is a jump-target index, not a claim.
- [x] 6.2 Copy every XML and tally this change cites into
      `openspec/changes/fix-ui-reroute-numbers/evidence/`, run
      `.pi/skills/fix-loop/scripts/evidence-check.sh <change-dir>` in live mode, then archive and re-run it in
      archived mode; quote the drift count. Commit on the loop branch (`fix:` for the fix, `fix: … — archive
      with its evidence` for the archive); do not push.
      — ran 2026-10-13 before archive, live mode: `evidence-check: PASS` — C1 `29 checked, 0 exempted`, C2
      `6 pairs confirmed, 0 exempted (not retained), 0 unparsable, 0 drift`, C3 `none cited`, C4 no
      `REVERT-CHECK MUTATION` marker under `app/src`, C5 `360 resolved and in range, 0 unresolved or ambiguous`.
      The archived-mode re-run against `openspec/changes/archive/2026-10-13-fix-ui-reroute-numbers` is appended
      below.
      — ran 2026-10-13 after the archive, archived mode (C1/C2 report-only): `evidence-check: PASS` — C1
      `29 checked, 0 exempted`, C2 `6 pairs confirmed, 0 exempted (not retained), 0 unparsable, 0 drift`, C3
      `none cited`, C4 no `REVERT-CHECK MUTATION` marker under `app/src`, C5 `365 resolved and in range,
      0 unresolved or ambiguous`. **Drift: 0.**
