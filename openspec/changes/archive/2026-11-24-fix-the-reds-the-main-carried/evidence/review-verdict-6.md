# Round 6 — independent read-only review of HEAD `5e8ebbd6`

Reviewer run `4988ff96-5c51-48f3-aeb7-13c148d1195a`, revived as `1cd0a7a1-70cd-44f7-a1b9-a7cb0f320544` for the
truncated reprint. Read-only: no gate, no suite, no network; every green figure quoted from an evidence file by
path. Filed by the parent because the reviewer's sandbox denies writes.

## Verdict

```
VERDICT: FAIL (record-only: the code, the greens and all seven causes hold; one measurement in the change's own record is wrong)
LOOP-BLOCKING: 1
```

## Cause lines (round 5's CORRECTs re-confirmed against code + evidence)

1. **CORRECT** — `~/.m2/repository` cached (build.yml:249, sonar.yml:108), key `m2-v1` (build.yml:250,
   sonar.yml:109), warm step build.yml:273–307; `evidence/gate-workflows.txt` quotes it.
2. **CORRECT** — `tools/doc-routes.json`:1011 + :141 (§8d, two routes), :709 (§2b), :309 + :638 (§13, two routes);
   `bash tools/check-rule-routes.sh` → "all 658 named rule(s) are reached", EXIT=0.
3. **CORRECT** — `tools/loop/tests/loop-cases.sh:19516` (sort from first reader) + `:19548` (declaration
   normalised), case `:488`/`:20251`; live `sha256sum` = `880af467…e6cf` = tasks.md §3.3's re-measured value.
4. **CORRECT** — `app/src/test/.../SettingsSurfaceTest.kt:1061`
   `.also { it.ioDispatcher = mainDispatcherRule.dispatcher }`, `awaitStored` drains per poll (`:998`/`:1004`);
   app-mobile XML `tests=2321 failures=0 errors=0`.
5. **CORRECT** — `tools/sensors/sensors.json:50` `"threshold": 2000000`; every comment figure holds (loop-cases.sh
   1 685 967 live; 1 419 357 at `77973d53`; +266 610; 314 033 headroom; churn 7×400 000 → 2 800 378,
   `sensor-cases.sh:2028`/`:2036`); mutation 8.31 (`sensor-mutations.sh:60`) now searches 2000000 → 1000000.
6. **CORRECT** — `tools/gradlew-retry.sh` 58 lines; wired at build.yml:364 and sonar.yml:160; T1–T5 in
   `evidence/gate-gradle-retry.txt` match the script's contract.
7. **CORRECT** — `DiagnosticsLog.kt:710`/`:713` `oneLine`; XMLs `DiagnosticsLogTest tests="8"`,
   `StartupScreensTest tests="7"`; red-first log at `app/build/scratch-cause7/core-red.log`,
   `expected:<1> but was:<3>` quoted as claimed.

## Round-5 items — RESOLVED

- **B7 RESOLVED** — `proposal.md:237` "new, 58 lines"; `wc -l tools/gradlew-retry.sh` → 58;
  `evidence/gate-gradle-retry.txt` "The wrapper" quotes the same.
- **B8 RESOLVED** — `yq -r '.jobs.build.steps[] | select(.name=="Warm the Robolectric SDK jars") | .run'
  .github/workflows/build.yml | wc -l` → 34; `awk 'NR>=275&&NR<=307' .github/workflows/build.yml | wc -l` → 33;
  matches `tasks.md:33` ("34 lines as `yq -r` emits it").
- **B9 RESOLVED** — `grep -n 'gradlew-retry' .github/workflows/build.yml .github/workflows/sonar.yml` →
  build.yml:358/364, sonar.yml:155/160, byte-identical to the block quoted in `evidence/gate-gradle-retry.txt`.
- **B10 RESOLVED** — `.openspec.yaml` (9 lines) equals the block quoted in `evidence/gate-change.txt:20–30`
  (the diff shows only the quoted block's trailing blank separator); seven checks, retry wrapper, fold in the
  write seam all present.

## LOOP-BLOCKING (1)

- **B11 — the gate totals omit two modules the gate itself ran.**
  - *Quotes:* `evidence/gate-full.txt:13–14` "result files: 825 / tests: 6299"; repeated at `tasks.md:145` and
    `design.md:251`.
  - *What the run actually holds:* the same run's XMLs hold **860 files / 6 449 tests**. The quoted figures equal
    exactly `:app` (mobile 2321 + automotive 2321 tests; 328+328 files) + `:auto` (979; 99) + `:core` (678; 70)
    = 825 files / 6 299 tests, and drop `:osmscout-client-java` (23 files, 112 tests, mtime 11:45:22) and
    `:architecture` (12 files, 38 tests, mtime 11:45:29); both executed in the run —
    `app/build/scratch-cause7/gate.log:160` (`> Task :osmscout-client-java:test`) and `:172`
    (`> Task :architecture:test`), no `UP-TO-DATE`.
  - *Severity:* all 35 omitted files carry `failures="0" errors="0"`, so the gate's verdict stands; only the count
    is wrong.
  - *Smallest fix:* write `860` / `6 449` at `evidence/gate-full.txt:13–14` and in the same sentence at
    `tasks.md:145` and `design.md:251` (or name the two excluded modules).

## NOTE

- **N8** — `sensors.json:57` "264 commits since then" is right at authoring
  (`git rev-list --count 77973d53..82305af6` = 264), now 268 at HEAD `5e8ebbd6`; no case reads the comment,
  re-pin at landing.

## Resolution applied by the parent

- **B11 RESOLVED.** Re-counted the run's own XMLs with one `find` over all five modules:
  `find app auto core osmscout-client-java architecture -path '*/test-results/*' -name 'TEST-*.xml' -newermt
  '2026-11-24 11:30' -print | wc -l` → **860**; the same `find` piped to `xargs grep -ho 'tests="[0-9]*"' | awk
  -F'"' '{s+=$2} END {print s}'` → **6449**; `failures="[0-9]*"` → **0**; `errors="[0-9]*"` → **0**.
  `evidence/gate-full.txt` now states 860 / 6449 with the per-module split (app 656/4642, auto 99/979,
  core 70/678, osmscout-client-java 23/112, architecture 12/38) and records that the earlier 825/6299 figure
  summed four modules and dropped the last two; `tasks.md:145` and `design.md:251` state the same. The verdict
  (`BUILD SUCCESSFUL`, 0 failures, 0 errors) is unchanged.
- **N8 recorded, not re-pinned.** `evidence/gate-sensors.txt` now states the 264 is measured at `82305af6`, that
  it is 268 at HEAD, and that no follow-up commit touched `loop-cases.sh`
  (`git diff --stat 82305af6..HEAD -- tools/loop/tests/loop-cases.sh` is empty, size still 1 685 967), so the
  size figures stand and no case reads the count. Re-pinning the committed comment is a landing task, not a
  change to this evidence.
