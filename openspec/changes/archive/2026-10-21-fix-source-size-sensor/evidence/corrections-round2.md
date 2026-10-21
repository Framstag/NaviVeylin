# Review round 1 → the corrections, and what each one rests on

The reviewer refused the change on one blocking finding and five notes. Nothing shipped was reverted; the
production row, the measurement and its behaviour are unchanged. The corrections are prose plus five case
assertions.

## Blocking — the delta claimed a contract the row does not declare

Spec said "The unit, the language scope and the bound SHALL be the row's — declared in the registry, never in the
measurement". The row (`tools/sensors/sensors.json`) carries `threshold` and `comparison` only:

```
$ jq -r '.rows[] | select(.id=="source-size") | keys[]' tools/sensors/sensors.json
category class cmd comparison consecutive cron fingerprint id lifecycle threshold
```

The unit (`awk`'s `FNR` record number, i.e. lines) and the scope (`git ls-files -z '*.kt' '*.java'`) are the
measurement's own constants (`tools/sensors/metrics/source-size.sh:9-13`, 28), fixed by the item's recorded
decision of 2026-10-21 (1 500 lines per file, Kotlin and Java, tests included, file level, C++ out).

Correction — the requirement and its scenarios now say exactly that, which is also what `guidelines/Backlog.md`
§9 already said: "The number, the unit and the scope are the owner's recorded decision …; the bound is the row's,
so retuning it is one edit in `sensors.json`." The old scenario 2 ("*The bound, the unit and the scope are the
row's*") is now "*The bound is the row's, the unit and the scope are the measurement's*", scenario 1 and 3 say
"the measured languages" instead of "a declared language", and `proposal.md` and `design.md` carry the same split.
The delta is edited in both places it lives: the archived change and the merged
`openspec/specs/backlog-sensors/spec.md`.

## Note — the clear's identity was asserted nowhere

Scenario 3's second clause ("a clear naming its own identity") and design D4's closure claim ("a clear per
recorded open condition … a closure names the identity the issue carries") were code-read only. Both are now
case-asserted in `sensors#sourceSizeRow`:

- the row-level clear is asserted to carry `"path":"none"`;
- a second run feeds a recorded OPEN condition for `app/src/main/java/Small.java` through `SENSORS_CONDITIONS`
  and asserts a `clear` naming that path with `"measurement":"10"`, and that it is not the row-level `none`.

That is why the delta now has a fourth scenario, "*A recorded condition inside the bound clears under its own
path*", naming the same case.

## Note — task 2.2's third verification clause was not run

`tools/build-inputs.sh --check` was run on the shipping tree: `build-inputs: every declared input has a producer
(4 rows checked)` (`evidence/build-inputs-check.log`). The earlier `evidence/build-inputs.log` is the selftest
suite and is kept as it was.

## Note — an unproven parenthetical in `design.md`

"…would need three thousand commits in the 200-commit window to reach any bound…" carried no measurement. It is
deleted; the elimination now rests on the argument that already stood two clauses earlier — the score is
`size × changes`, so a file with no changes scores zero at any bound.

## Note — the mutation run predates the final edit of `source-size.sh`

The round-1 mutation run (22:48) predated the last edit of the measurement. Mutation 8.17 was therefore re-run on
the shipping bytes at 23:03: `MUTATION 8.17: sensors#sourceSizeRow red under the mutation, green after the
restore — a file past the row bound is a violation` / `sensor-mutations: 1 behaved, 0 did not`
(`evidence/mutation-8.17-round2.log`), and the restore leaves `source-size.sh` unmodified (`git status` clean for
that path). The round-1 log is kept beside it.

## The gate

`./tools/loop/gate.sh`, run once on the corrected tree (22:55:40 → 23:02:11):
`LOOP-GATE: tests=45 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/sensor-selftest.sh,
tools/check-doc-routes.sh)` — `evidence/gate.log`, tallies `evidence/gate-round2-*.xml`. The Gradle half, which
gate.sh's tally does not count, passed 2096 tests per flavour, 0 failures (`evidence/gradle-flavours.log`).
