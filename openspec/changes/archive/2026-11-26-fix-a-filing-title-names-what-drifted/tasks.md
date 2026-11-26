# Tasks — three titles that name their own drift

Spec: `backlog-sensors` (delta `specs/backlog-sensors/spec.md`). Gate: `bash tools/sensors/tests/sensor-selftest.sh`
— the suite `tools/loop/loop.conf` maps to `tools/sensors/*`, whose `--list` companion
(`tools/sensors/tests/sensor-mutations.sh --list`) is the other half. No Gradle build is owed: no compiled file
changes (`guidelines/Build.md` §2, the scope block).

Route:

LOOP-ROUTE: guidelines/Backlog.md §4

## 1. The cases, red

- [x] 1.1 `tools/sensors/tests/sensor-cases.sh`: `create_titles` (the titles a run's `gh issue create` actually
  carried, read from the stub's record) plus three cases — `sensors#twoDriftedItemsFileUnderTwoTitles`,
  `sensors#twoOversizedFilesFileUnderTwoTitles`, `sensors#twoFailingCasesFileUnderTwoTitles` — each asserting
  that two conditions of one row file two items under two titles naming their own drift, and registered in
  `SENSOR_CASES` and the dispatch.
  Verify: `bash tools/sensors/tests/sensor-selftest.sh --list` names all three — done, and the suite grew from
  94 to 97 cases (`evidence/gate-sensor-selftest.xml`, `tests="97"`).
- [x] 1.2 Each of the three, run on the base, fails for the right reason — the constant title, quoted.
  Verify: `bash tools/sensors/tests/sensor-selftest.sh --case sensors#twoDriftedItemsFileUnderTwoTitles` (and the
  other two) → `FAIL …: '<composed title>' not found in: <the constant title>`
  (`evidence/red-on-HEAD-titles.log`).

## 2. The three titles

- [x] 2.1 `tools/sensors/metrics/metadata-invariant.sh:70`: the title becomes `"#${issue}: ${code}"`.
  Verify: `sensors#twoDriftedItemsFileUnderTwoTitles` → `PASS`, and `sensors#metadataDriftRow` still `PASS`
  (`evidence/green-titles.log`).
- [x] 2.2 `tools/sensors/metrics/source-size.sh:59`: the title becomes `"${path}: ${lines} lines"`.
  Verify: `sensors#twoOversizedFilesFileUnderTwoTitles` → `PASS`, and `sensors#sourceSizeRow` still `PASS`.
- [x] 2.3 `tools/sensors/conditions/broken-main.sh:47`: the title becomes
  `"the build is red: ${job}/${case_name}"`.
  Verify: `sensors#twoFailingCasesFileUnderTwoTitles` → `PASS`, and
  `sensors#brokenMainFingerprintDistinguishesCauses` still `PASS`.

## 3. The gate

- [x] 3.1 `./tools/loop/gate.sh` green, its tally copied beside this change: the whole sensor suite plus the
  mutations list, which is what `tools/sensors/*` owes.
  Verify: the gate's summary line in `evidence/gate.log` — green, 316 tests, 0 failures, 0 errors — and the
  sensor suite's own tally `tests="97" failures="0" errors="0"` (`evidence/gate-sensor-selftest.xml`);
  `tools/sensors/tests/sensor-mutations.sh --list`
  unchanged in shape — records `8.17`, `8.19` and `8.28` quote lines this change does not move
  (`evidence/gate-sensor-mutations-list.xml`).
- [x] 3.2 `openspec validate fix-a-filing-title-names-what-drifted --strict` passes — one warning only (the
  requirement text is past 500 characters), no error.
