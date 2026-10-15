# Tasks — fix-routing-failure-reporting-classification

## 1. Red proof on HEAD

- [x] 1.1 Confirm the claim in this tree and record the red case:
      `openspec/specs/routing-failure-reporting/spec.md` exists while `tools/feature-list/specs.json` has no
      `routing-failure-reporting` key; run
      `./gradlew :app:testMobileDebugUnitTest --tests "com.naviveylin.featurelist.ShippedSpecClassificationTest"
      -PforceTests --rerun-tasks`, retain the JUnit XML and quote its tally.
      — ran 2026-10-15T17:38Z→17:42:05Z on HEAD `97fb38a`:
      `ShippedSpecClassificationTest > every shipped spec id is classified FAILED`, XML
      `tests="1" skipped="0" failures="1" errors="0"`, `expected:<[]> but was:<[routing-failure-reporting]>`,
      timestamp `2026-10-15T17:42:05.964Z`; retained as `evidence/TEST-ShippedSpecClassification-red-on-HEAD.xml`.
- [x] 1.2 Record the classification gate's own red run (the defect's repro and its `file:line` claim).
      — ran 2026-10-15T17:41Z: `bash tools/gen-feature-list.sh --check-classification` → exit **1**,
      `unclassified shipped spec id(s): routing-failure-reporting`, `specs read: 167 / classified: 166 /
      unclassified: 1 / stale: 0 / car-only areas: 1 / highlights: 19`; retained as
      `evidence/classification-gate-red-on-HEAD.txt`.

## 2. The fix — one classification line

- [x] 2.1 Add `"routing-failure-reporting": { "area": "routing", "userVisible": true, "surfaces": ["phone", "car"] }`
      to `tools/feature-list/specs.json` in the `routing` group, before `routing-max-speed` (design.md D1).
      Verify `jq` parses the file and the key carries the three values.
      — ran 2026-10-15T17:42Z: `jq -e '.specs["routing-failure-reporting"] == {…}'` → `true`;
      `git diff --stat tools/feature-list/specs.json` → 1 file changed, 1 insertion(+), 0 deletions.
- [x] 2.2 Verify the classification gate is green: `bash tools/gen-feature-list.sh --check-classification`
      exits 0 with `specs read: 167 / classified: 167 / unclassified: 0 / stale: 0`, and record the run in
      `evidence/`.
      — ran 2026-10-15T17:43Z: exit **0**, `specs read: 167 / classified: 167 / unclassified: 0 / stale: 0 /
      car-only areas: 1 / highlights: 19`; retained as `evidence/classification-gate-after-fix.txt`.

## 3. The guard case turns green (the change's declared case)

- [x] 3.1 Re-run the red case **forced** through the filter form —
      `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.featurelist.ShippedSpecClassificationTest" -PforceTests --rerun-tasks` — and quote the
      tally from its XML, with a timestamp after the fix. Copy the XML into `evidence/`.
      — ran 2026-10-15T17:44:03Z: XML `tests="1" skipped="0" failures="0" errors="0"`, timestamp
      `2026-10-15T17:44:03.364Z`, `system-out` `shipped=167 classified=167 unclassified=[]`; retained as
      `evidence/TEST-ShippedSpecClassification-green-mobile.xml`.
      The case's input is flavor-independent data (two files read by path), so the `:app` mobile flavor is the
      affected module and the pair `TEST-ShippedSpecClassification-red-on-HEAD.xml` /
      `TEST-ShippedSpecClassification-green-mobile.xml` is the change's before/after evidence.

## 4. Recurrence — the CI check `TODO.md` §157 asks for

- [x] 4.1 Add the model-free step to `.github/workflows/build.yml` beside **Check OpenSpec artifact hygiene**:
      `bash tools/gen-feature-list.sh --check-classification`, failing with an `::error` annotation that names
      the tool's own message. Verify the workflow parses (`python3 -c "import yaml,sys; yaml.safe_load(open(...))"`
      or `yq`) and that the step's command is green on this tree's data (task 2.2's run is that evidence).
      — ran 2026-10-15T17:45Z: `yq '.jobs.build.steps[].name' .github/workflows/build.yml` lists the new step;
      its command exits 0 against the fixed `specs.json` and exited 1 against HEAD's (tasks 1.2 / 2.2), which is
      the whole point of landing it.
- [x] 4.2 Record the instance in `TODO.md` §157 as an **Instance, 2026-10-15** bullet: this third occurrence of
      the same defect, and that §157's first fix candidate (the CI check) is now landed. §157 stays `open` — its
      second candidate (running the check inside the archive step) is a change to the archiving workflow, which
      this change does not modify.
      — recorded 2026-10-15; `grep -c '^## ' TODO.md` and `grep -c '^\*\*id:\*\*' TODO.md` still agree (no id
      added, no id renumbered).

## 5. Scope, evidence and bookkeeping

- [x] 5.1 State the scope-block justification (`guidelines/Build.md` §2, "Scope — what the changed files owe"):
      this change adds one data line to `tools/feature-list/specs.json`, one CI step, and one backlog bullet,
      and changes no production or test source, so no build and no suite is owed — the affected case of task 3.1
      and the classification gate of tasks 1.2 / 2.2 stand in for the gate.
      — recorded 2026-10-15: the verification form applied is the scope block's *no compiled file changed /
      data-only* case, with the affected case run through the module filter instead of the suite.
      **Exclusions named:** the `:app` suites (both flavors), `:auto`, `:core` and the JNI suite were not run —
      nothing they compile is touched by this delta, and the red case plus the classification gate are the whole
      effect. A whole-suite forced gate is **not** claimed as evidence here; it is red on this repository for
      reasons unrelated to this delta (Compose suites time out in the whole-suite gate, `TODO.md` §148), so it
      would not settle this change either way.
- [ ] 5.2 Copy every XML this change cites into `evidence/`, run
      `.pi/skills/fix-loop/scripts/evidence-check.sh <change-dir>` in live mode, then archive and re-run it in
      archived mode; quote the drift count. Commit with a `fix:` subject; do not push.
