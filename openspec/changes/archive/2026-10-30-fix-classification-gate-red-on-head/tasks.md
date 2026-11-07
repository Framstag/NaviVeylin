# Tasks — fix-classification-gate-red-on-head

Spec: `spec-feature-index` "An unclassified spec id stops the run" (`guidelines/FeatureList.md` §4). This change
declares no delta (`.openspec.yaml` `skip_specs: true`): it changes the tree's classification data, not a
capability's contract.

## 1. Reproduce the defect on HEAD

- [x] 1.1 Record the red run of the classification gate on HEAD `ead55b39` with a clean tree, so the defect is a
      measurement rather than a claim: `bash tools/gen-feature-list.sh --check-classification` → exit 1,
      `unclassified: 2` (`change-readiness`, `shaping-inbox`), each line naming its owning open change.
      Verification: `evidence/classification-gate-red-on-HEAD.txt` carries the command, the output, `exit=1` and
      the HEAD it was taken on.
- [x] 1.2 Confirm the second half of the defect is real rather than assumed: `grep -n '"id"' tools/feature-list/areas.json`
      carries no `specs-and-process`, while the area check at `tools/gen-feature-list.sh:199-231` fails an unknown
      id — so the two open changes' task 6.4 as written cannot pass.
      Verification: the ids in `areas.json` are read against the text of
      `openspec/changes/change-readiness-gate/tasks.md` 6.4 and `openspec/changes/shaping-inbox/tasks.md` 6.4, and
      the gate's area rule is quoted in `design.md` "Root cause, and the alternatives the evidence eliminated".

## 2. The fix (data only)

- [x] 2.1 `tools/feature-list/specs.json` gains `"change-readiness": { "area": "build-tooling", "userVisible": false,
      "surfaces": [] }` in its alphabetical place (line 52), the classification the open change
      `change-readiness-gate` owes for its delta capability.
      Verification: the gate names `change-readiness` as unclassified before the edit and not after.
- [x] 2.2 `tools/feature-list/specs.json` gains `"shaping-inbox": { "area": "build-tooling", "userVisible": false,
      "surfaces": [] }` in its alphabetical place (line 171).
      Verification: the same gate run names neither id.
- [x] 2.3 The two open changes' task 6.4 names the area it means: `specs-and-process` → `build-tooling`
      (`openspec/changes/change-readiness-gate/tasks.md`,
      `openspec/changes/shaping-inbox/tasks.md`). Nothing else in either change is edited — not a requirement, not
      a scenario, not a design line.
      Verification: `grep -n 'build-tooling' openspec/changes/change-readiness-gate/tasks.md
      openspec/changes/shaping-inbox/tasks.md` finds the area in each task 6.4, and no task 6.4 in either file
      names a second area id.

## 3. Verify

- [x] 3.1 No compiled file, no test source and no Gradle script is touched, so `guidelines/Build.md` §2's scope
      block applies: the structural checks below stand in for the gate, and the exclusion is named in
      `design.md` "D3 — Verification".
      Verification: `git diff --stat` lists `tools/feature-list/specs.json` alone — the two `tasks.md` live under
      `openspec/changes/*/`, which `.gitignore` keeps out of version control, so their edit is a working-tree
      correction that ships with each change's own archive.
- [x] 3.2 `bash tools/gen-feature-list.sh --check-classification` → exit 0, `unclassified: 0`, `classified: 185`
      over `specs read: 180`. The five `stale:` lines it prints are reported, never failed (`guidelines/FeatureList.md`
      §4) — the three older ones are other open changes' pre-archive lines, and the two this change adds read stale
      for the same reason: their capabilities ship when their changes archive.
      Verification: `evidence/classification-gate-green-after-fix.txt` carries the command, the output and `exit=0`.
- [x] 3.3 The Kotlin case that reads the same two artefacts still passes, so the JSON edit is known-good rather than
      merely parseable: `./gradlew :app:testMobileDebugUnitTest --tests
      "com.naviveylin.featurelist.ShippedSpecClassificationTest" -PforceTests --no-build-cache` → `BUILD SUCCESSFUL`,
      XML `tests="1" skipped="0" failures="0" errors="0"`, `system-out` `shipped=180 classified=185 unclassified=[]`.
      Verification: `evidence/TEST-ShippedSpecClassification-green.xml`.
- [x] 3.4 `openspec validate fix-classification-gate-red-on-head --strict` → exit 0, and
      `tools/check-doc-routes.sh` → exit 0 (`all 169 section references resolve, all 4 skill references resolve`).
      Verification: both commands quoted in the apply report; neither writes a file.
- [x] 3.5 `bash tools/loop/evidence-check.sh openspec/changes/fix-classification-gate-red-on-head --root .` resolves
      every pointer this change's `tasks.md` writes, in live mode before the archive and in archived mode after it.
      Verification: exit 0, and the drift count quoted in the archive commit's body.

## 4. Bookkeeping

- [x] 4.1 Archive the change (`openspec archive fix-classification-gate-red-on-head`), which renames it under
      `openspec/changes/archive/2026-10-30-…` and re-runs the archived-mode evidence check; nothing ships into
      `openspec/specs/`, because this change declares no delta.
      Verification: `openspec/specs/` is unchanged by the archive (`git status --porcelain openspec/specs` empty).
- [x] 4.2 Issue #128 (§194) carries `status/fixed-by` naming the archived change and is closed with the gate's
      before/after runs as evidence. The issue's own example id, `routing-max-speed`, was already classified by
      `ca4a5f1c` (#123); what this change closes is the part still true, the red gate on HEAD.
      Verification: `tools/issues/close.sh` output in the session report; `tools/issues/list.sh --id 194
      --include-closed` shows the label and the closed state.
