# Tasks — fix-source-size-skips-the-change-tree

- [x] 1. Add `sensors#sourceSizeSkipsTheChangeTree` to `tools/sensor-cases.sh` (the list, the dispatch arm and
      the function). **Verified:** `bash tools/sensor-selftest.sh --case sensors#sourceSizeSkipsTheChangeTree`
      exits 1 against HEAD's engine with `'Head.kt' unexpectedly present`, and `evidence/red-on-head.xml` records
      `tests="1" failures="1"` (1.164 s, 2026-10-26T21:22:18Z).
- [x] 2. Exclude the change tree in `tools/sensors/metrics/source-size.sh`, and report a recorded condition on an
      unmeasured path as `outside-scope` rather than a line count nobody took. **Verified:** the case exits 0
      (`evidence/case-fixed.xml`, `tests="1" failures="0"`, 1.243 s, 2026-10-26T22:07:13Z); the real tree's run
      reports the row-level clear where it reported one violation (`evidence/sensor-after-fix.log`), and the
      recorded condition on the frozen copy clears as `"measurement":"outside-scope"` on that same tree
      (`evidence/sensor-condition-clears.log`, 2026-10-26T22:07:21Z).
- [x] 3. Falsify the new case: mutation `8.28` in `tools/sensor-mutations.sh` (drop the exclusion).
      **Verified:** `bash tools/sensor-mutations.sh --only 8.28` printed `MUTATION 8.28: …
      red under the mutation, green after the restore` (`evidence/mutation-8.28.log`).
- [x] 4. Name the exclusion where the row's unit and scope are stated, in `guidelines/Backlog.md` §9.
      **Verified:** `bash tools/check-doc-routes.sh` exits 0 — `all 154 section references resolve`.
- [x] 5. Write the capability delta and validate it. **Verified:** `openspec validate
      fix-source-size-skips-the-change-tree --strict` printed `Change … is valid`; the `MODIFIED` requirement
      repeats all four shipped scenarios verbatim beside the new one.
- [x] 6. Record the measurements and the condition's clearing half in `evidence/`. **Verified:**
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-26-fix-source-size-skips-the-change-tree --root .`
      reports no dangling pointer. This is the path a correction round re-checks: item 2's real-tree record
      (`evidence/sensor-after-fix.log`) was regenerated with the shipped engine, because the copy it carried before
      reported the frozen copy as `0 lines`, which is the fabricated count the change removes.
- [ ] 7. Run the project's gate once and quote its tallies. **Ran:** `./tools/loop/gate.sh` (its aggregate summary is `evidence/gate-summary.txt`), and it ended red on
      four cases, all in `tools/loop-selftest.sh` — its own tally `tests="240" failures="4"` under
      `evidence/gate-tallies/`; the sensor tooling's own suite was green (`tests="62" failures="0"`, this
      change's case included). **Not checked:** the gate did not end with zero failures, so this item stays
      unchecked rather than talking the run green (`guidelines/Build.md` §4d).
- [x] 8. Attribute the gate's four failures rather than blaming them. **Verified:**
      `evidence/gate-red-attribution.log` — the same four cases each pass alone (`PASS`, exit 0,
      2026-10-26T22:01:39Z to 22:02:09Z), and none of the four names a path this delta touches. The residual is
      left in the evidence for a person; this worker does not write the tracker.
- [x] 9. Archive the change and confirm the delta merged into `openspec/specs/backlog-sensors/spec.md`.
      **Verified:** `openspec/changes/archive/2026-10-26-fix-source-size-skips-the-change-tree/` exists and
      `grep -c 'The change tree is not the source tree' openspec/specs/backlog-sensors/spec.md` is 1.
