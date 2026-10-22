# Corrections — round 3 (the review of round 2's refusal)

The review's verdict was `FAIL` with one BLOCKING finding and `LOOP-CORRECTIONS-ONLY: yes`. This round changes
exactly what it named, and adds no claim.

## BLOCKING — scenario 6 was not exercised by the case it names

- **The finding.** "The build refuses an unrecorded deviation" named `NativeOverrideParityGuardTest`'s case of
  the same name, but that case asserts only the two `script.contains(…)` string checks — the gate is registered
  and wired into `preBuild` — while the THEN claimed `:app:checkNativeParity` *fails the build through
  `preBuild`*, naming the file and the list line. Nothing added a native and watched the refusal.
- **The repair (the review's option 2, subtractive).** Both copies of the delta — the change's
  `specs/osmscout-jni/spec.md` and the shipped `openspec/specs/osmscout-jni/spec.md`, still byte-identical —
  now carry:

  ```
  #### Scenario: The app build registers the parity gate and wires it into preBuild

  - **WHEN** the app build script is read as the project's own file
  - **THEN** it registers `checkNativeParity` over the two Java sides and the recorded list, and makes `preBuild`
    depend on it, so the scan runs in every app build instead of waiting for the call site's `NoSuchMethodError`
  - **Case**: `app: NativeOverrideParityGuardTest` "the app build registers the parity gate and wires it into preBuild"
  ```

  and the test method is renamed to the claim it asserts. The refusal keeps its own measurements: the scan's
  five fixture cases, and the two mutation runs re-executed with a date this round
  (`revert-check-mutation-1-…-round3.log`, `revert-check-mutation-2-…-round3.log`), each followed by the
  restored-tree green run the round-1 logs lacked (`revert-check-green-after-restore.log`).
- **No new case, no Gradle TestKit harness** — option 3 of the review would have been a new round for a claim
  the evidence already carries.

## The review's NOTEs, and what each got

| note | what was done |
|---|---|
| 3.2's "(same file, last lines)" names content its files do not carry | replaced with the run that actually rules a leftover marker out (`gate-round2.log`), plus the mutation pair re-executed with a restored-tree green section (4.5) |
| 3.3's times 0.235/0.008 point at `declared-cases.txt`, which reads 0.013/0.014 | re-pointed: both pairs are now quoted with the file that carries each (`structural-checks.txt` for 0.235/0.008) |
| 4.1's restore evidence (`grep -c` → 0, `git diff --stat` → 45) has no capture | replaced by the gate-green argument and by the re-measured `inputs.dir` probe (`inputs-dir-missing.log`) |
| `structural-checks.txt` opens with a failed `./openspec` invocation above its result | `structural-checks-round3.txt`: the three checks re-run with the documented commands, no failed line (4.8) |
| the four non-parity modules' XMLs are not copied | stated in 3.5: the sums are `gate-tallies.txt`'s, the XMLs stay where the gate wrote them |
| the mutation logs carry no timestamp | both round-3 logs open with a UTC date line |
| §8 UNPROVEN: the `--configuration-cache` 13-problem count | captured (`configuration-cache-problems.log`); it is 13, so `guidelines/Build.md` §2 is unchanged |
| §8 UNPROVEN: the `inputs.dir` throwaway-project measurement | captured with the probe's own `build.gradle.kts` quoted above its output (`inputs-dir-missing.log`) |
| §8 UNPROVEN: "no `buildSrc` source changed since" | dropped from the claim: `gate-tallies.txt` records Gradle's `UP-TO-DATE` word, and 3.5 now says exactly that |
| §8 UNPROVEN: `cancelSearch`'s absence is not visible in the quoted probe output | the probe re-run, dated, its ten lines printed (`parity-probe-round3.txt`) |
| the third hand-maintained copy of the five filenames; the return-type blind spot | recorded in `design.md`, "What this change does not close" — follow-up items, not a widening of this change |
| scenario 7 hardcodes 5/5 rather than reading the two trees | **not** changed: the review marked it not required, and the gate executed on the final tree |

## Scope of this round's verification

One test method renamed, no compiled source otherwise: the affected case (4.4, `tests=2 failures=0`,
`2026-10-22T12:13:00.165Z`) plus the structural checks (4.8), not a gate re-run. The gate's result stands as
round 2 recorded it: `LOOP-GATE: tests=481 failures=0` (`gate-round2.log`, `gate-tallies.txt`).
