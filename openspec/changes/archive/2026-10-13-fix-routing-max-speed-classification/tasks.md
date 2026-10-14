# Tasks — fix-routing-max-speed-classification

## 1. Red proof on HEAD

- [x] 1.1 Confirm the claim in this tree and record the red case: `openspec/specs/routing-max-speed/spec.md`
      exists while `tools/feature-list/specs.json` has no `routing-max-speed` key; run
      `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.featurelist.ShippedSpecClassificationTest"
      -PforceTests --rerun-tasks`, retain the JUnit XML and quote its tally.
      — ran 2026-10-13T18:49:36Z→18:56:47Z on HEAD `fa6151c`:
      `ShippedSpecClassificationTest > every shipped spec id is classified FAILED`, XML
      `tests="1" skipped="0" failures="1" errors="0"`, `expected:<[]> but was:<[routing-max-speed]>`,
      timestamp `2026-10-13T18:56:47.467Z`; retained as `evidence/TEST-ShippedSpecClassification-red-on-HEAD.xml`.
- [x] 1.2 Record the classification gate's own red run (the defect's repro and its `file:line` claim).
      — ran 2026-10-13T18:58:26Z: `bash tools/gen-feature-list.sh --check-classification` → exit **1**,
      `unclassified shipped spec id(s): routing-max-speed`, `specs read: 166 / classified: 165 /
      unclassified: 1 / stale: 0 / car-only areas: 1 / highlights: 19`; retained as
      `evidence/classification-gate-red-on-HEAD.txt`.

## 2. The fix — one classification line

- [x] 2.1 Add `"routing-max-speed": { "area": "routing", "userVisible": true, "surfaces": ["phone", "car"] }`
      to `tools/feature-list/specs.json` in the `routing` group (design.md D2). Verify `jq` parses the file and
      the key carries the three values.
      — ran 2026-10-13T18:59Z: `jq -e '.specs["routing-max-speed"] == {…}'` → `true`;
      `git diff --stat tools/feature-list/specs.json` → 1 file changed, 1 insertion(+), 0 deletions.
- [x] 2.2 Verify the classification gate is green: `bash tools/gen-feature-list.sh --check-classification`
      exits 0 with `specs read: 166 / classified: 166 / unclassified: 0 / stale: 0`, and record the run in
      `evidence/`.
      — ran 2026-10-13T18:59:10Z: exit **0**, `specs read: 166 / classified: 166 / unclassified: 0 /
      stale: 0 / car-only areas: 1 / highlights: 19`; retained as `evidence/classification-gate-after-fix.txt`.

## 3. The guard case turns green (the change's declared case)

- [x] 3.1 Re-run the red case **forced** through the filter form —
      `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.featurelist.ShippedSpecClassificationTest" -PforceTests --rerun-tasks` — and quote the
      tally from its XML, with a timestamp after the fix. Copy the XML into `evidence/`.
      — ran 2026-10-13T19:16:23Z: XML `tests="1" skipped="0" failures="0"
      errors="0"`, timestamp `2026-10-13T19:16:23.242Z`, `system-out` `shipped=166 classified=166
      unclassified=[]`; retained as `evidence/TEST-ShippedSpecClassification-green-mobile.xml`.
      The case's input is flavor-independent data (two files read by path), so the `:app` mobile flavor is the
      affected module and the pair `TEST-ShippedSpecClassification-red-on-HEAD.xml` /
      `TEST-ShippedSpecClassification-green-mobile.xml` is the change's before/after evidence.

## 4. Gate shape — why no suite

- [x] 4.1 State the scope-block justification (`guidelines/Build.md` §2, "Scope — what the changed files owe"):
      this change adds one data line to `tools/feature-list/specs.json` (not a Gradle input, not compiled) and
      one backlog line, and changes no production or test source, so no build and no suite is owed — the
      affected case of task 3.1 and the structural gate of task 2.2 stand in for the gate.
      — recorded 2026-10-13: the verification form applied is the scope block's *no compiled file changed /
       data-only* case, with the affected case run through the module filter instead of the suite.
      **Exclusions named:** the `:app` suites (both flavors), `:auto`, `:core` and the JNI suite were not run —
      nothing they compile is touched by this delta, and the red case plus the classification gate are the
      whole effect. A whole-suite forced gate is **not** claimed as evidence here; if one is wanted for this
      change it is a pending follow-up, and it is red on this repository for reasons unrelated to this delta
      (Compose suites time out in the whole-suite gate), so it would not settle this change either way.

## 5. Bookkeeping (the loop's phase F, not this apply pass)

- [ ] 5.1 `TODO.md` §189: `status: open` → `status: fixed-by fix-routing-max-speed-classification` and one
      `**Loop verdict**` bullet naming the added entry and the eliminated alternatives. Re-run the structure
      checks (`##` count == `**id:**` count, metadata line under every heading, no id collision). No new
      `TODO.md` entry is filed by this change.
- [ ] 5.2 Copy every XML this change cites into `evidence/`, run
      `.pi/skills/fix-loop/scripts/evidence-check.sh <change-dir>` in live mode, then archive and re-run it in
      archived mode; quote the drift count. Commit on the loop branch (`fix:` for the fix, `fix: … — archive
      with its evidence` for the archive); do not push.
