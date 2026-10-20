# design — fix-foreign-fixture-loop-conf

## Root cause

`run.sh` resolves the review-round budget from the configuration and refuses it unless it is a positive
integer:

```sh
tools/loop/run.sh:73   : "${max_review_rounds:=$(loop_conf_value LOOP_MAX_REVIEW_ROUNDS_DEFAULT)}"
tools/loop/run.sh:78-80  # Zero rounds is not a cheap review, it is a review that never runs …
                         case "$max_review_rounds" in ''|*[!0-9]*|0) loop_die "run: --max-review-rounds must be a positive integer" ;; esac
```

`loop_conf_value` prints the sourced configuration variable or nothing, so a configuration that does not
declare the key yields an empty string, which the check refuses. The synthetic foreign project's configuration
declares the run's other five budget keys and not this one:

```
tools/fixtures/foreign-project/tools/loop/loop.conf:9-17
  LOOP_ITEMS_DEFAULT=1 · LOOP_MINUTES_DEFAULT=30 · LOOP_REVIEW_DEFAULT=off
  LOOP_MAY_CLOSE_DEFAULT=yes · LOOP_MAX_DECISIONS_DEFAULT=1 · LOOP_ITEM_FLOOR_MINUTES=5
```

Every path that starts a run in that tree therefore exits 2 before anything else happens: the foreign-project
case's dry run (`tools/portability-cases.sh`, `case_foreign_toolchainRunsEndToEnd`), the empty-gate run
(`case_foreign_emptyGateCommandStillRunsThePhases`), and the recipe's own step
`bash tools/loop/run.sh --dry-run --items 0 --minutes 0` (`porting#everyCommandInTheRecipeRuns`).

The key arrived with the loop's review-round budget (`tools/loop/run.sh`, the item-review change of
2026-10-19); the fixture, which is test data rather than engine code, was not updated with it. The engine and
the check are consistent with each other and inconsistent with their own worked example.

## The single fix

1. `tools/fixtures/foreign-project/tools/loop/loop.conf` records `LOOP_MAX_REVIEW_ROUNDS_DEFAULT=2`.
2. `tools/portability-cases.sh` gains `foreign#fixtureDeclaresEveryBudgetDefault`, which derives the key names
   from the engine (`grep -ho 'loop_conf_value LOOP_[A-Z_]*_DEFAULT' tools/loop/*.sh`) and asserts the fixture
   declares every one, so the next `_DEFAULT` key the engine gains cannot be forgotten in the fixture again.

The bound is derived, not listed: a list of keys kept beside the engine is the same drift with a shorter
latency. The key set is read from every script under `tools/loop/`, so a budget read by `state.sh` counts as
well as one read by `run.sh`.

## Alternatives the evidence eliminated

- **Make the engine fall back to a built-in round count when the key is absent.** Impossible: `loop.conf`'s own
  contract is that every budget a run takes is recorded there ("Every flag `run.sh` accepts is recorded here,
  and an omitted flag takes this value"), and `run.sh:78-80` states why a zero budget is refused rather than
  substituted. A fallback would invent the review budget of a project that recorded none — the reversal of a
  recorded owner decision, which this change may not make. The two keys that do fall back
  (`LOOP_CHILD_MAX_SECONDS`, `LOOP_MIN_FREE_MIB`) are host facts whose fallback is a safe reading, not a policy
  budget.
- **Relax `run.sh` so an absent key means "review off".** Impossible for the same reason and one more: `review`
  and the round budget are separate flags, and a run with `--review on` but no recorded rounds would then
  silently never review — the failure mode the fatal check exists to prevent.
- **Leave the engine and the fixture alone and change the three cases to pass `--max-review-rounds 2`.** This
  makes the symptom green while the foreign project's own command line stays broken: `PORTING.md`'s recipe step
  and the empty-gate case both invoke `run.sh` with the configuration's defaults, and a case that supplies the
  missing value tests something the porter never runs.
- **Add the key to the fixture and nothing else.** Rejected as a fix that leaves the class open: it was a
  *silent* gap for a day, and the same edit is needed at every future budget key. The derived case is the
  smallest bound that closes it.

## Blast radius

| file | change | risk |
|---|---|---|
| `tools/fixtures/foreign-project/tools/loop/loop.conf` | one key plus its comment | test data only; the fixture is built fresh per case and never committed to a tracker |
| `tools/portability-cases.sh` | one helper, one case, two registrations | the suite is offline and runs in ~30 s; a wrong key derivation shows as a red case, not as a silent pass (the case fails closed when the engine reads no key at all) |
| `AGENTS.md`, `.pi/skills/portability-check/SKILL.md` | 26 → 27 | documentation only |

Nothing in `app/`, `auto/`, `core/`, `osmscout-client-java/`, `buildSrc/`, the native tree or the manifests is
touched; no Gradle task, ABI, resource or stylesheet changes. Threading and lifecycle: none — the change adds a
line to a shell configuration and a case to a shell suite, and neither runs on Android.

## Verification

- Red on HEAD: the three named cases in a clean clone of `f2e57ee` (`evidence/red/on-head.txt`), and the new
  bound case in the same tree with only the case added (`evidence/red/fixtureDeclaresEveryBudgetDefault.txt`).
- Green: `bash tools/check-portability-selftest.sh` in a clean clone of the fixed tree — `all 27 cases passed`,
  plus `bash tools/check-portability.sh` — `OK — 43 engines and 2 templates name no project value`
  (`evidence/green/`).
- Falsification (`revert-check`): remove the added key from the fixed copy — 4 of 27 fail, the new case first —
  restore, re-run green (`evidence/green/revert-check.txt`).
- No device and no Gradle run is owed: the delta touches no compiled file, so the gate is the structural check
  of the files it edits (`guidelines/Build.md` §2, the scope block).
- The CI `Check portability` step runs exactly these two commands, so the green suite is the evidence that the
  `build` job can reach its Gradle steps again. Whether the APK step then succeeds is not this change's claim;
  a fresh failure there is a new condition with its own identity.
