# Corrections — round 3 (review 2 refusal, corrections-only)

Reviewer verdict on round 2: `LOOP-BLOCKING: 2`, `VERDICT: FAIL`, `LOOP-CORRECTIONS-ONLY: yes`,
`LOOP-DECISION` recommended option (2) — re-measure `proposal.md`'s Surface file count and re-point
`tasks.md` 4.2 at the artifacts that actually carry `477` and `5768`. Both are applied. No case, assertion,
workflow line or guideline sentence changed; this round touches two `.md` files of the change and its
evidence only.

## 1. `proposal.md` Surface — the first line did not reproduce its own command

Before this round the block read:

```
git grep -l "org\.gradle\.java\.home" -- .: 20 versioned files, 12 of them archived changes
```

Measured from the change root on 2026-10-22, under the command the line quotes:

```
$ git grep -l "org\.gradle\.java\.home" -- . | wc -l
30
$ git grep -l "org\.gradle\.java\.home" -- . | grep -c openspec/changes/archive
20
$ git grep -l "org\.gradle\.java\.home" -- . \
    | grep -v fix-versioned-gradle-java-home-guard \
    | grep -v VersionedGradleJavaHomeTest | wc -l
21
$ git grep -l "org\.gradle\.java\.home" -- . \
    | grep -v fix-versioned-gradle-java-home-guard \
    | grep -v VersionedGradleJavaHomeTest | grep -c openspec/changes/archive
12
```

- **merged tree** (this change archived in it): 30 files, 20 of them under `openspec/changes/archive`.
- **base tree** (this change's 8 files and its new `VersionedGradleJavaHomeTest` excluded): 21 files, 12
  archived. The 9-file gap is exactly this change.
- The reviewer's `22 / 12` reading of the same base comes from `grep -rl --exclude-dir=build`: that flag also
  drops the test class's package directory `com/naviveylin/build/`, and `grep -r` counts the untracked
  `local.properties`, which `git grep` does not (`0 + 1 - 0` on the base: 22 vs 21).

The line now carries both trees, the gap and the reason the two command forms disagree, so no reading of it
is stale against the tree it describes.

## 2. `tasks.md` 4.2 — the verification named an artifact that does not carry the number

Round 2's item read "the eight shell tallies in `evidence/gate-tallies.txt` sum to 477". That file carries no
per-suite shell tallies. What it does carry:

```
$ grep LOOP-GATE evidence/gate.log
LOOP-GATE: tests=477 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh, tools/loop-mutations-selftest.sh, tools/issue-tooling-selftest.sh, tools/sensor-selftest.sh, tools/check-action-runtimes-selftest.sh, tools/gen-feature-list-selftest.sh, bash tools/gen-feature-list.sh --check-classification, tools/check-doc-routes.sh)

$ grep -E '^(app|auto|core|osmscout|TOTAL)' evidence/gate-tallies.txt
app:testAutomotiveDebugUnitTest: tests=2101 failures=0
app:testMobileDebugUnitTest: tests=2101 failures=0
auto:testDebugUnitTest: tests=876 failures=0
core:testDebugUnitTest: tests=642 failures=0
osmscout-client-java:test: tests=48 failures=0
TOTAL 6245 0
```

- `477` is `evidence/gate.log`'s aggregate `LOOP-GATE` line.
- `5768` is the five module tallies in `evidence/gate-tallies.txt` (`2101 + 2101 + 876 + 642 + 48`).
- The per-suite split behind `477` (`10 + 0 + 115 + 117 + 34 + 156 + 45 + 0`) is read from the gate's own
  per-suite output, which `gate.sh` writes under `app/build/test-results/loop-gate/` and does not archive;
  only the aggregate line is archived. The item now says so instead of pointing at a file that lacks them.

Not re-cut, and recorded as open: the per-module JUnit XMLs that would let the five module tallies be
re-derived also live only in a build tree (`app/build/test-results/` per module) that the next build
overwrites — the same gap the reviewer notes in §6, unchanged by this round.

## 3. What this round did not do, and why

- **No gate re-run.** The corrections touch no compiled file, so the structural check stands in per the
  loop's scope rule. The round-1 gate result (`exit 0`, `tests=477 failures=0`, the new class 3/0 in both app
  flavours at `2026-10-22T08:50:12.730Z` and `2026-10-22T08:51:54.115Z`) is the evidence and is not
  re-created; `evidence/gate.log` and `evidence/gate-tallies.txt` are unchanged this round.
- **No case, assertion or workflow line touched.** `.github/workflows/build.yml` is byte-identical to the
  tree round 2's revert-check recorded, so its revert-check evidence still applies.
- **`evidence/corrections-round2.md` is left as written.** It is the round-2 record; its §2 per-suite split is
  now qualified where it is used (`tasks.md` 4.2) rather than rewritten in the record that produced it.
- **The property-source half is still not falsified against the real file** — the path policy denies
  `gradle.properties` to this process, the limitation rounds 1 and 2 recorded.

## 4. Structural check

```
$ bash tools/check-doc-routes.sh
check-doc-routes: all 120 section references resolve, all 5 skill references resolve
exit=0

$ openspec validate --specs
Totals: 177 passed, 0 failed (177 items)
```

Both appended to `evidence/structural-checks.txt` as its `## round 3` section; the round-1 and round-2
sections above it are intact.
