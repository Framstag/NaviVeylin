# Tasks — fix-shipped-spec-classification

## 1. Red proof: the repository's classification is incomplete on HEAD

- [x] 1.1 Add `app/src/test/java/com/naviveylin/featurelist/ShippedSpecClassificationTest.kt` — a conformance
      case that reads the shipped-spec directory (`openspec/specs/**/spec.md`, nested ids included) and the
      classification (`tools/feature-list/specs.json`'s `specs` keys) through the `:app` unit-test working
      directory (`File("..")`, the `FavAutoZoomClampRangeTest` convention) and asserts every shipped id is
      classified, printing both counts and the unclassified ids. Verify it compiles and runs.
      — ran 2026-10-10T07:02:18Z: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.featurelist.ShippedSpecClassificationTest" -PforceTests --no-build-cache` compiled and
      executed the class (1 test).
- [x] 1.2 Record the red run of `every shipped spec id is classified` on HEAD (the test added, the data not
      yet fixed) and retain its JUnit XML in `evidence/`. Verify the XML carries `tests="1" failures="1"` and a
      timestamp after the test file's creation.
      — ran 2026-10-10T07:05:30.400Z: XML `tests="1" failures="1"`,
      `expected:<[]> but was:<[highlight-measurement, map-repository-source, map-source-selection,
      render-projection-dpi, route-calculation-feedback, starred-ordering]>`; retained as
      `evidence/TEST-ShippedSpecClassification-red-on-HEAD.xml`. The gate repro on HEAD:
      `bash tools/gen-feature-list.sh --check-classification` → exit 1, `specs read: 163 / classified: 157 /
      unclassified: 6`, 2026-10-10T07:02:13Z.

## 2. The fix: complete the classification data

- [x] 2.1 Add the six entries D2 of `design.md` names to `tools/feature-list/specs.json`
      (`highlight-measurement` build-tooling/internal/—, `map-repository-source` offline-maps/internal/phone,
      `map-source-selection` offline-maps/user-visible/phone, `render-projection-dpi`
      map-appearance/internal/phone+car, `route-calculation-feedback` routing/user-visible/phone+car,
      `starred-ordering` favorites/user-visible/phone). Verify `jq -e '.specs' tools/feature-list/specs.json`
      parses and all six keys are present.
      — ran 2026-10-10T07:06:28Z: `jq -e '.specs | has("highlight-measurement") and … and
      has("starred-ordering")'` → `true`; `git diff --stat tools/feature-list/specs.json` → 1 file changed,
      6 insertions(+), 0 deletions.
- [x] 2.2 Verify the classification gate is green: `bash tools/gen-feature-list.sh --check-classification`
      exits 0 with `unclassified: 0` and `stale: 0`. Quote the tally.
      — ran 2026-10-10T07:06:28Z: exit **0**, `specs read: 163 / classified: 163 / unclassified: 0 / stale: 0 /
      car-only areas: 1 / highlights: 19`; retained as `evidence/classification-gate-after-fix.txt`.

## 3. Guard and revert-check

- [x] 3.1 Revert-check the data invariant the fix restores (`spec-feature-index` — "A classified spec id
      passes"): delete one added entry (e.g. `starred-ordering`) from `tools/feature-list/specs.json`,
      re-run the case, confirm it fails naming that id, restore the entry, and force the case green.
      — ran 2026-10-10T07:09:56.709Z: `sed -i '/"starred-ordering":/d' tools/feature-list/specs.json` →
      `ShippedSpecClassificationTest > every shipped spec id is classified FAILED`, XML `tests="1"
      failures="1"`, `expected:<[]> but was:<[starred-ordering]>`; retained as
      `evidence/TEST-ShippedSpecClassification-revert-check-mutation.xml`. Restored the pre-mutation file;
      `git diff --stat tools/feature-list/specs.json` → 6 insertions, 0 deletions; forced green in task 4.1
      (mobile 2026-10-10T07:14:36.955Z, automotive 2026-10-10T07:16:40.633Z).

## 4. Gate shape

- [x] 4.1 Run one forced both-flavor gate — `./gradlew test -PforceTests --rerun-tasks`. Justify the shape:
      the change touches test source (`app/src/test`) and `tools/feature-list/specs.json` (not a Gradle
      input), so both `:app` flavors run. Quote the per-module `tests/failures` tallies from the XMLs.
      Attribute the known `MapCanvasViewModelModeTest` flake (TODO.md §148) if it appears: run the victim
      alone and then in the suite; do not pass it on a re-run alone.
      — ran 2026-10-10T07:10:05Z → `BUILD SUCCESSFUL in 8m 44s`, 189 actionable tasks: 189 executed;
      `:app mobile 248/1871/0`, `:app automotive 248/1871/0`, `:auto 78/793/0`, `:core 51/532/0`,
      `:osmscout-client-java 4/33/0`; retained as `evidence/gate-after-fix.txt`. `MapCanvasViewModelModeTest`
      did not fail, so no flake attribution was needed.

## 5. Bookkeeping and evidence

- [x] 5.1 `TODO.md` §161: set `**status:** fixed-by \`fix-shipped-spec-classification\`` and add a bullet
      recording the verdict. Re-run the structure checks (`##` count == `**id:**` count, every heading
      followed by its metadata line, no id collision). The entry is deleted only if the run allows removal;
      otherwise it stays marked.
      — ran 2026-10-10: §161 `status: fixed-by fix-shipped-spec-classification`, a `**Loop verdict**` bullet
      records the six ids and the eliminated asymmetry; `grep -c '^## '` = `grep -c '^\*\*id:\*\*'` = 107.
- [x] 5.2 Copy the cited XMLs into `openspec/changes/fix-shipped-spec-classification/evidence/`, run
      `.pi/skills/fix-loop/scripts/evidence-check.sh` in live mode, then archive and re-run it in
      archived mode; quote the drift count.
      — ran 2026-10-10: live `evidence-check: PASS`; archived re-run `evidence-check: PASS` with 0 drift rows.
- [x] 5.3 Commit on the loop branch: `fix: shipped-spec-classification (§161)` for the fix and
      `fix: shipped-spec-classification (§161) — archive with its evidence` for the archive. Do not push.
      — ran 2026-10-10: two commits, not pushed.
