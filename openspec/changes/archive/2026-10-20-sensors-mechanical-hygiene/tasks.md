# Tasks

## 1. The red cases first (the gap, not the fix)

- [x] 1.1 `tools/issue-tooling-cases.sh`: nine `duplicates#` cases over a new fixture
      (`tools/issues/fixtures/scenarios/duplicates/`: one fingerprint shared by three open items — 81 and 82
      authorisable, 83 not —, one `nvid` claimed by 84 and 85, an open 225 whose number real history names, an
      open 9001 that no commit names, and 86 whose twin 87 is closed). Red on HEAD: the script did not exist
      (`bash: …/duplicates.sh: Datei oder Verzeichnis nicht gefunden: expected '0', got '127'`).
      **Deviation from the proposal, recorded:** the cases live in the existing suite rather than in a
      `duplicates-selftest.sh` of their own — that suite already owns the stub `gh`, the fixtures and the
      assertion helpers, and a second driver would be a second copy of them.
- [x] 1.2 The falsification cases: inverting the survivor rule (`sort_by(.issue)` → `… | reverse`) must make
      `duplicates#olderItemSurvives` fail; the record is `tools/duplicates-mutations.json` D1, and D2/D3 do the
      same for the authorisation rule and the candidate filter.

## 2. The sweep

- [x] 2.1 The fingerprint marker is read by `duplicates.sh` itself, not through a shared helper: `lib.sh` is
      read by the running loop's own children, and `create.sh` is the marker's only producer, so the pattern has
      exactly one writer and one other reader. The guard is behavioural — `duplicates#differentFingerprintsAreNotMerged`
      fails if a group is ever formed from anything but a shared marker, and `duplicates#cleanTrackerExitsZeroWithoutAWrite`
      fails if the pattern stops matching the tooling's own output.
- [x] 2.2 `tools/issues/duplicates.sh`: groups by fingerprint (older item survives), collisions by `nvid`
      (reported, never repaired), a candidate list read once from `git log` and never acted on, `--apply`,
      `--json`, `--limit`; closures through `close.sh --reason … --evidence … --not-planned`, the pointer for a
      non-authorised duplicate through `update.sh --comment`. Two defects were found while writing it and are
      fixed in the same commit: a per-item `git log | grep -q` fails under `pipefail` for a reason that has
      nothing to do with the item (the ids are read out of the log once now), and the engine named the profile
      values `eligible`/`open`, which `tools/check-portability.sh` refused — the variables are `authorised` and
      the prose is neutral now.
- [x] 2.3 `.github/workflows/sensors.yml`: one step after `resolve-stale.sh`, in `$SENSORS_MODE`, `--limit 25`,
      with the reasons written beside it. `tools/duplicates-mutations.json` is listed by the same CI step that
      lists the other mutation sets (see 4.2).

## 3. Spec deltas and the guideline

- [x] 3.1 `openspec/changes/sensors-mechanical-hygiene/specs/backlog-sensors/spec.md` and
      `…/specs/backlog-source-of-truth/spec.md`: the two ADDED requirements, every scenario naming a
      `duplicates#` case that exists. `openspec validate sensors-mechanical-hygiene --strict` → valid.
- [x] 3.2 `guidelines/Backlog.md` §9 `Checks`: the sweep joins the scheduled worker's repairs, with its two
      rules and the split stated plainly — what needs a judgement stays `cleanup-todo`, a skill a person runs.
      Verification: `check-doc-routes.sh` (89 references), `check-portability.sh --quiet` (44 engines).

## 4. Structural checks — the scope block (no compiled file is touched)

- [x] 4.1 `bash -n` on `tools/issues/duplicates.sh` and `tools/issue-tooling-cases.sh`;
      `bash tools/issue-tooling-selftest.sh` all 100 cases green; `bash tools/check-portability.sh --quiet` OK
      (44 engines); `check-doc-routes.sh` and `check-todo-references.sh` unchanged. Exclusion, named: no Gradle
      gate and no `./gradlew` suite — no Kotlin, Java, C++, manifest or resource file is touched
      (`guidelines/Build.md` §2, the scope block).
- [x] 4.2 Revert-check: `bash tools/mutate.sh --mutations tools/duplicates-mutations.json` — four mutations, each
      red with its mutation in place and green again after the restore, no "did not apply" and no "stays green".
- [x] 4.3 Evidence: the red run of 1.1, the green suite, the mutation set's transcript and the CI-side listing,
      recorded in `evidence/red-and-green.txt`.

## 5. After the change

- [ ] 5.1 One scheduled run on `main` writes what a dry run named: its job summary lists the duplicates it
      closed and the collisions that remain reported.
- [ ] 5.2 A `workflow_dispatch` without `apply` prints the same list and writes nothing — the dry run is the
      proof against the live backlog, and it costs no write.
