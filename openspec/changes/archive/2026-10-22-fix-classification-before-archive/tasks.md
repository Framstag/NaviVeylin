# Tasks — fix-classification-before-archive

## 1. The rule, stated where a change author reads it

- [x] 1.1 MODIFY `spec-feature-index`'s requirement "An unclassified spec id stops the run" so that an open
  change's delta specs are part of what the gate demands, and add the scenarios the new cases name.
  **Verify**: `openspec validate fix-classification-before-archive --type change --strict` → "Change
  'fix-classification-before-archive' is valid"; every `#### Scenario:` carries a `- **Case**:` naming a case that
  runs in `tools/gen-feature-list-selftest.sh` (9 of 9 resolved by name against the suite's own case list).
- [x] 1.2 `guidelines/FeatureList.md` §4 records the widened rule and the stale reading a pre-archive line gets;
  §2 records that the *catalogue* still reads only `openspec/specs/`; §9 states what a change now owes;
  `README.md`'s `--check-classification` line says what it reads.
  **Verify**: `bash tools/check-doc-routes.sh` → `all 120 section references resolve, all 5 skill references
  resolve`, exit 0 (`evidence/gate.log`, suite `tools/check-doc-routes.sh`).

## 2. The gate reads an open change's delta

- [x] 2.1 Write the cases first, on the base commit, and record them red:
  `gate: a capability an open change introduces must be classified`, `gate: the unclassified open capability is
  named`, `gate: the open change that owes the line is named` fail; `gen-feature-list-selftest: 112 passed, 3
  failed`, exit 1.
  **Verify**: `evidence/red-HEAD.log` and `evidence/red-HEAD.xml` (`tests=3 failures=3`, run at
  `2026-10-22T01:07:48Z`, base commit `80df3a3`).
- [x] 2.2 `tools/gen-feature-list.sh`: the classification gate's input set becomes shipped ∪ the ids an open
  change's delta specs introduce (`openspec/changes/*/specs/*/spec.md`, archive excluded), and the failure names
  each id with its origin — the open change that owes the line, or `shipped`. `stale` stays over the shipped set.
  **Verify**: the three red cases pass; `bash tools/gen-feature-list.sh --check-classification` on this tree exits
  0 and prints `delta spec ids: 2` (this change's delta and the other open change's), `unclassified: 0`.
- [x] 2.3 Realign the one existing case the widened rule touches — `keys: an in-flight change contributes no key`
  (its fixture delta now carries a classification line; the assertion about the *index* is unchanged).
  **Verify**: `bash tools/gen-feature-list-selftest.sh` → `all 115 cases passed`, exit 0
  (`evidence/focused-gen-feature-list-selftest.txt`).
- [x] 2.4 Falsify the new invariant: the same throwaway root driven by the **base** tool exits 0, and driven by
  this tree's tool exits 1 naming the id and the change; the real tree is then green again.
  **Verify**: `evidence/mutation-delta-id.txt` — base `80df3a3` tool `EXIT=0 unclassified: 0`; this tree
  `EXIT=1`, `newcap (introduced by the open change probe-change — classify it in that change, before it
  archives)`; restored pair `EXIT=0`.
- [x] 2.5 `tools/gen-feature-list-selftest.sh` prints a gate-shaped summary (`all N cases passed` /
  `M of N cases failed`) beside its own line, which the gate's own summary regex reads
  (`tools/loop/gate.sh:114-118`, its shape asserted by `tools/loop-cases.sh`). No gate run under this change's
  profile was made, so the tally is a consequence of that regex and its case, not something a gate log shows.
  **Verify**: `bash tools/gen-feature-list-selftest.sh | grep 'all 115 cases passed'` →
  `gen-feature-list-selftest: all 115 cases passed`, exit 0 (`evidence/focused-gen-feature-list-selftest.txt`).

## 3. The check runs at the moments that matter

- [x] 3.1 `tools/loop/loop.conf`: `tools/gen-feature-list*.sh` and `tools/feature-list/*` owe
  `tools/gen-feature-list-selftest.sh`, and `openspec/*` owes
  `bash tools/gen-feature-list.sh --check-classification`.
  **Verify**: `LOOP_CONF=tools/loop/loop.conf ./tools/loop/gate.sh --plan` lists both commands
  (`evidence/gate-profile-probe.txt`). The run's own gate inherited `LOOP_CONF=/home/tim/projects/NaviVeylin/tools/
  loop/loop.conf` — the project tree's copy, which does not carry this change yet — so the single gate run owed
  the build and the four suites the old profile maps, and the feature-list suite was run focused beside it
  (`evidence/focused-gen-feature-list-selftest.txt`); both profiles' plans are recorded in the same probe.
- [x] 3.2 `.github/workflows/build.yml`: the classification step's message says the line is owed before the
  archive, and a new step runs the tool's suite — which ran in no CI step until now.
  **Verify**: `bash tools/check-action-runtimes-selftest.sh` (10 cases) and `bash tools/loop-selftest.sh`
  (155 cases) green in `evidence/tools_check-action-runtimes-selftest.sh.xml` and
  `evidence/tools_loop-selftest.sh.xml`.

## 4. Verification of the change as a whole

- [x] 4.1 The real tree's gate is green: `bash tools/gen-feature-list.sh --check-classification` → `classified:
  177`, `unclassified: 0`, `delta spec ids: 2`, `stale: 0`, exit 0.
- [x] 4.2 One gate run, exactly as the run configures it: `./tools/loop/gate.sh` → exit 0,
  `LOOP-GATE: tests=210 failures=0 (./gradlew test -PforceTests --no-build-cache, tools/loop-selftest.sh,
  tools/sensor-selftest.sh, tools/check-action-runtimes-selftest.sh, tools/check-doc-routes.sh)`, with `:app`'s
  two flavours at 2097 cases each, 0 failures, summed in `evidence/gradle-suite-tally.xml` — that tally covers
  `:app` alone, not the build's other modules. `evidence/gate.log`, `evidence/tools_*.xml`.
- [x] 4.3 No compiled file changed, so no *new* Gradle task was owed: `guidelines/Build.md` §2's scope rule — the
  build in that run came from a compiled path another change left in the shared worktree
  (`app/src/test/.../MapManagerUpdatesQueueTest.kt`), not from this change, and the four shell suites plus the
  classification gate are this change's own verification.
- [x] 4.4 Evidence copied into this change's `evidence/` directory (the versioned half) and into the run's item
  directory.
- [x] 4.5 `openspec archive fix-classification-before-archive`, confirming the delta merged into
  `openspec/specs/spec-feature-index/spec.md`.
  **Verify**: `evidence/post-archive.txt`.

## Verification results (quoted, not promised)

| what | line |
|---|---|
| red on base `80df3a3` | `gen-feature-list-selftest: 112 passed, 3 failed`, exit 1, `2026-10-22T01:07:48Z` |
| green focused | `gen-feature-list-selftest: all 115 cases passed`, exit 0 |
| falsification | base tool `EXIT=0`, this tree's tool `EXIT=1` naming `newcap` and `probe-change` |
| real tree | `classified: 177 / unclassified: 0 / stale: 0 / delta spec ids: 2`, exit 0 |
| gate | `LOOP-GATE: tests=210 failures=0`, exit 0, plus `:app`'s two flavours at 2097 cases each, 0 failures |
