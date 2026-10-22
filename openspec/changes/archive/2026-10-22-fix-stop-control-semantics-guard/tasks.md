# Tasks — fix-stop-control-semantics-guard

## 1. The case

- [x] `NavigationStateOverlayComposeTest.stopControlIsTheOnlyActionableTargetOverItsOwnBox`: the merged node
      tagged `stopNavigation` carries a click action and `R.string.stop_navigation`, and exactly one actionable
      node overlaps its bounds.
      Verify: `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.ui.navigation.NavigationStateOverlayComposeTest"`
      → `evidence/head-green.xml`, tests=13 failures=0 (2026-10-22T03:06:56Z).

## 2. The falsification the case owes

- [x] Mutate the production invariant once — `.clickable(onClick = onClick)` on the card
      (`NavigationStateOverlay.kt:71`) — the new case must fail and the other 12 must stay green.
      Verify: `evidence/revert-check-mutated.xml`, tests=13 failures=1 (2026-10-22T03:07:12Z), the failure being
      `only the stop control may be actionable over its own box (found [SemanticsNode@…, SemanticsNode@…])`.
- [x] Restore the mutation; `git diff` on `NavigationStateOverlay.kt` is empty.

## 3. The spec delta

- [x] `openspec/specs/navigation-status-details/spec.md`: the "Routing status card is clickable" requirement
      gains the semantics clause, and the scenario "The control is the only target over its own box" names the
      case.
      Verify: `openspec validate navigation-status-details --type spec` and `openspec doctor` — neither of which
      looks at a scenario's body, which is why this box could be ticked while round 1 shipped an empty one —
      plus the scenario-body check of task 6: `evidence/scenario-body-check-before.log` (exit 1, the two
      headings it names are the defect) and `evidence/scenario-body-check-after.log` (exit 0, no finding).

## 4. The guideline rule

- [x] `guidelines/UI.md` §8, "Tap targets (phone overlays)": the rule that no actionable target other than the
      control may cover its box, the case that holds it, and why reading the two details regions was not enough.
      Verify: `tools/check-doc-routes.sh` (no new route, existing anchors still resolve).

## 5. The gate

- [x] `./tools/loop/gate.sh` — the pathspec carries a compiled test source and an `openspec/` delta, so the run
      is the Gradle gate plus the documentation/classification checks.
      Verify: `evidence/gate.log`, its tallies copied from `app/build/test-results/loop-gate/`.

## 6. Correction round 2 — the scenario body the delta dropped

Round 1's review found that the delta block silently dropped the body of the pre-existing scenario "The
expanded view's stop control is its own target too", and that `archive` merged the loss: the capability's
requirement lost an assertion this change never set out to touch, and `tasks.md` 3's verification could not
see it.

- [x] Restore the four WHEN/AND/THEN/AND lines in this change's delta and in
      `openspec/specs/navigation-status-details/spec.md`, and replace the one-off `Held by …` line with the
      `- **Case**:` form the sibling scenarios use (`openspec/specs/map-download-infrastructure/spec.md`,
      `openspec/specs/auto-speed-zoom/spec.md`).
      Verify: `evidence/scenario-body-check-after.log` (exit 0, no finding over `openspec/specs/**` and this
      delta), against `evidence/scenario-body-check-before.log` (exit 1, naming exactly the two headings).
      The check, repeatable:

      ```
      for f in $(find openspec/specs \
                     openspec/changes/archive/2026-10-22-fix-stop-control-semantics-guard -name spec.md); do
        awk -v F="$f" 'function flush(){ if (inh && !body) {
                 printf "%s:%d: %s — no WHEN/THEN body\n", F, hln, htxt; bad=1 } }
               /^#### Scenario:/ { flush(); inh=1; body=0; hln=FNR; htxt=$0; next }
               /^#{1,3} / { flush(); inh=0; next }
               { if (inh && $0 ~ /^- /) body=1 }
               END { flush(); exit bad?1:0 }' "$f" || bad=1
      done; echo "EXIT=$bad"
      ```

- [x] Re-read the delta block against the requirement it MODIFIES, so a MODIFIED block restates the whole
      requirement.
      Verify: `evidence/delta-vs-base-requirement.diff` — the delta is the requirement at `80df3a3` plus one
      appended clause and one inserted scenario block, and nothing else.

- [x] Run the suites this correction's pathspec owes (`openspec/*` and `*.md` in `tools/loop/loop.conf`'s
      `LOOP_GATE_SUITES`): `bash tools/gen-feature-list.sh --check-classification` and `tools/check-doc-routes.sh`.
      Verify: `evidence/correction-suites.log`. The change's gate is round 1's and is not re-run — the
      correction touches no compiled path.

- [x] Repair the proposal's and the design's pointers (`evidence/gate.xml` does not exist; the mutation site is
      `NavigationStateOverlay.kt:71`), and the two claims round 1 could not support.
      Verify: `evidence/delta-vs-base-requirement.diff` and the case's own lines `:246-247`, `:257` quoted by
      `design.md` from `evidence/revert-check-mutated.xml`.
