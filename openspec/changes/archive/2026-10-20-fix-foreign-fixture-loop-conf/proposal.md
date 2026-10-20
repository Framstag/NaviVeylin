# fix-foreign-fixture-loop-conf

## Why

The main build is red (issue #198): CI's `build` job fails at **Check portability**, so the job never reaches
`Build debug APK`, and `tools/build-inputs.sh` records `{"job":"build","case":"apk","result":"failure"}` — the
missing package is the shadow of a shell check, not a Gradle failure. Runs 39380255316 (base `93ea31b`),
39384065781 (base `a04ef9b`) and 39309801982 (2026-10-19) all fail there; the last green build is 39146805429
from 2026-10-18, so nothing has compiled in CI since.

The failing step is `bash tools/check-portability-selftest.sh`, which prints

```
PASS preconditions#refusesUnderMachineLocalState
PASS foreign#profileDiffersFromOurs
PASS foreign#noProjectNameInTheStub
  the dry-run loop failed in the foreign project:
FAIL foreign#toolchainRunsEndToEnd
  the run completes with an empty gate command: loop: run: --max-review-rounds must be a positive integer: expected '0', got '2'
FAIL foreign#emptyGateCommandStillRunsThePhases
  the recipe's step 'bash tools/loop/run.sh --dry-run --items 0 --minutes 0' exits 0 (output: loop: run: --max-review-rounds must be a positive integer): expected '0', got '2'
FAIL porting#everyCommandInTheRecipeRuns
check-portability-selftest: 3 of 26 cases failed
```

Reproduced on HEAD `f2e57ee` in a clean clone, offline, at 2026-10-20T10:31:26+02:00: `3 of 26 cases failed`,
the same three names and the same message (`evidence/red/on-head.txt`).

**Root cause, one sentence:** `tools/loop/run.sh:73` resolves an omitted `--max-review-rounds` to
`$(loop_conf_value LOOP_MAX_REVIEW_ROUNDS_DEFAULT)`, `tools/loop/run.sh:80` refuses an empty or zero value with
`--max-review-rounds must be a positive integer`, and the synthetic foreign project's own configuration
(`tools/fixtures/foreign-project/tools/loop/loop.conf:11-17`) declares five of the six `_DEFAULT` budget keys
the engine reads — every one but that key — so the loop cannot start in the foreign project at all.

**The single fix:** declare `LOOP_MAX_REVIEW_ROUNDS_DEFAULT` in the foreign fixture's `loop.conf`, and bound
the class with one case that reads the `_DEFAULT` keys out of the engine and asserts the fixture declares each
of them, so the next budget key the engine gains fails here instead of on the runner.

The alternative that the evidence eliminates: making the engine fall back to a built-in round count when the
key is absent (`run.sh`'s two host-fact keys, `LOOP_CHILD_MAX_SECONDS` and `LOOP_MIN_FREE_MIB`, do exactly
that). It is impossible here because the configuration file's own contract is that every budget a run takes is
*recorded* — `tools/loop/loop.conf` opens with "Every flag `run.sh` accepts is recorded here, and an omitted
flag takes this value", and `run.sh:78-80` names why a zero budget is refused rather than substituted ("a
review that never runs: the item would be blocked on a round that never happened"). A fallback would invent the
review budget of a project that forgot to record one, which is the owner decision this change may not reverse.

**Spec requirement the defect contradicts:** `tooling-portability`, "The synthetic project is a fixture, not a
second project" — the fixture is the worked example a copying project reads, and it must run the toolchain
with its own configuration. The delta adds one scenario to that requirement and re-words none of the others.

## What Changes

- `tools/fixtures/foreign-project/tools/loop/loop.conf` — records `LOOP_MAX_REVIEW_ROUNDS_DEFAULT=2`, with the
  one line that says why the engine refuses an absent one.
- `tools/portability-cases.sh` — a new case `foreign#fixtureDeclaresEveryBudgetDefault`, registered in both
  `PORTABILITY_CASES` and the dispatch map, plus the helper `engine_budget_keys` that reads the key names out
  of `tools/loop/*.sh` (so the bound follows the engine rather than a list kept beside it).
- `AGENTS.md`, `.pi/skills/portability-check/SKILL.md` — the suite's case count, 26 → 27.

## Impact

Affected files, all of them the loop's own tooling and its documentation — no app, native or Gradle file:

| file | module / area |
|---|---|
| `tools/fixtures/foreign-project/tools/loop/loop.conf` | the synthetic foreign project's run defaults (test data) |
| `tools/portability-cases.sh` | the portability check's offline shell cases |
| `AGENTS.md` | the Documentation Map's route to the portability check |
| `.pi/skills/portability-check/SKILL.md` | the `portability-check` skill's command table |

No Android module, manifest, resource, native source or JNI bridge is touched; the `submodule patch` /
`local override` decision does not arise. `tools/check-portability.sh` is unchanged and still passes (43
engines, 2 templates, 103 values).

## Previous specifications changed

- `openspec/specs/tooling-portability/spec.md` — **MODIFIED** the requirement "The synthetic project is a
  fixture, not a second project", adding one scenario ("The fixture declares every budget the engine reads")
  and restating its three existing scenarios verbatim. No other requirement or scenario is touched.

## Additive or breaking, and rollback

**Additive.** One key is added to a fixture configuration and one case is added to an offline suite; no engine,
command, exit code, option or report line changes, and the foreign project's behaviour with a complete
configuration is exactly what it was.

Rollback: revert this commit. The engine is unchanged, so the suite returns to 26 cases and three of them fail
again on the same line — the state of HEAD before the change. Nothing is written to the tracker, no branch is
created and no run state is touched by this change.
