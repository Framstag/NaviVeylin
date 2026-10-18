# Report — `tooling-portability`

The tooling is provably usable in another project now. An engine names no project value, and a check on the
commit that introduces one says so; the whole toolchain — filing, selecting, updating, closing, reopening, a
declared check, a prioritisation pass and a loop run — runs in a synthetic project whose repository,
allowlist, vocabulary and bands all differ; discovery is asserted where it actually breaks; and the porting
recipe's commands are executed by the self-test, so the document cannot drift.

This is the sixth of the seven changes `guidelines/Backlog.md` describes.

## What landed

| path | what it is |
|---|---|
| `tools/check-portability.sh` | the check: the value set read at run time from `tools/tooling.json`, `tools/sensors/sensors.json` and `tools/loop/loop.conf`; the engine set and the exclusions printed on every run |
| `tools/check-portability-selftest.sh`, `tools/portability-cases.sh` | the gate: 21 cases, offline, in throwaway git repositories under the gitignored `.pi/tmp/` |
| `tools/mutate.sh`, `tools/mutate-selftest.sh`, `tools/portability-mutations.json` | the seven revert-checks as a versioned set (eight records — 5.3 names two cases): one command re-runs them, `--list` is printed by the CI step, and the runner's four guards and its exact restore are proven on a toy tree |
| `.pi/skills/portability-check/`, `.pi/skills/mutation-runner/` | the two skills the change's own guidance asked for: how to run and extend the check, and how to write and run a mutation set |
| `tools/fixtures/foreign-project/` | the synthetic project (`acme/widget`, `ada`, `core`/`docs`/`infra`, bands p0–p2, TTL 30) with its three findings, its own sensor row, its empty gate command and its stub `gh` answers |
| `PORTING.md` | the recipe: what to copy, the three files to edit, the adapter to delete, the assumptions, what an update costs — its commands in `sh porting-step` fences executed by the self-test |
| `.github/workflows/build.yml` | one `Check portability` step: the check and its self-test, beside the other token-free gates |
| `AGENTS.md` | one row in the Documentation Map naming `PORTING.md` |
| `guidelines/Backlog.md` | §1 row 6 applied and the status prose corrected; §10 now names the check, the fixture and the recipe; two §12 rows for the decisions this change lands |
| `tools/feature-list/specs.json` | the classification line for `tooling-portability` (build-tooling, not user-visible) |
| `openspec/changes/tooling-portability/evidence/` | `gate-report.md` (6.1–6.4), `revert-checks.md` (5.1–5.7), `traceability.md` (6.6) |

## The rule, precisely

`tools/check-portability.sh` forbids two kinds of value, because they are not the same kind of thing:

- **literal** (a fixed substring on every line, comments included): the repository, the allowlisted authors,
  the project name, the label spellings the category vocabulary would produce, the commands and inputs the
  sensor registry declares, its escalation labels, and the gate command from `loop.conf`;
- **named** (a word-bounded token inside a quote, as a label, or as a flag's argument; comment lines
  skipped): the category values themselves and the worker skills from `loop.conf`.

The second mode exists because a project's category can be an ordinary word. The synthetic project proves it:
its categories are `core`, `docs` and `infra`, and `core` occurs throughout the issue tooling inside
identifiers (`ISSUES_CORE_DIR`), paths (`$LIB_DIR/core`) and English sentences ("the Java core does not
compile"). A plain word-boundary scan reported 3 false positives on the foreign fixture and 14 on this tree;
a rule like that is a rule nobody keeps, and it would have made the check useless for exactly the project it
is meant to serve.

## Gate results

| gate | result |
|---|---|
| `tools/check-portability-selftest.sh` | **21 of 21 cases passed**, exit 0 (recorded with its output and timestamp in `evidence/gate-report.md`) |
| `tools/check-portability.sh` (real tree) | exit 0, 38 engines and 2 templates, 132 values read from the project |
| `tools/mutate.sh --mutations tools/portability-mutations.json` | **8 mutations, 8 red and 8 restores green**, exit 0 (`evidence/revert-checks.md`) |
| `tools/mutate-selftest.sh` | 27 of 27 checks pass, exit 0 — the runner's own guards (`MUTATION-DID-NOT-APPLY`, `STAYS-GREEN`, `MUTATION-FILE-MISSING`, an exact restore) each made to fail on a toy tree |
| `tools/check-portability.sh` under `env -i PATH="$PATH"` | exit 0 |
| the seven revert-checks (5.1–5.7) | each named case **red (exit 1)**, each file restored, each case green again (`evidence/revert-checks.md`) |
| `tools/check-doc-routes.sh` + its self-test | 89 references resolve, exit 0; 10 of 10 self-test cases |
| `tools/gen-feature-list.sh --check-classification` | exit 0 now with `stale: 1` for `tooling-portability` (the entry is load-bearing once the spec ships — verified by temporarily shipping the spec: with the line exit 0, with the line removed **exit 1 naming `tooling-portability`**) |
| `git diff --name-only` | four modified files, all documentation/config; **no engine file, no compiled file** |

**No Gradle build and no Gradle suite was run, and none is required** (6.4 above). The change's gate is
`tools/check-portability-selftest.sh`.

## What the check does not cover (issue #162)

The value set is read from the project, and it is the *category* vocabulary rather than every namespace,
plus the values the per-tool configs declare. Three facts in the loop engine sit outside it, measured in
code: `tools/loop/worktree.sh:71` names the submodule path `app/src/main/cpp/libosmscout`;
`tools/loop/prescreen.sh:49-56` names `class` and `status` values literally to detect them after reading the
profile; `tools/loop/labels.sh:55-97` names `agent` and `status` values literally. Widening the check to
every namespace would report all three, and this change's scope block forbids touching an engine file — so
the measurement is recorded and filed as **issue #162** rather than narrowed away. The `agent/eligible`
constant in `tools/issues/lib.sh` is the documented exception (read-only, no write path).

## After the tasks — the falsification pass got a versioned form

The tasks' own guidance asks for a skill for repetitive work, and the seven revert-checks were exactly that:
the pass was hand-rolled in a scratch script (`.pi/tmp/revert-check.sh`), so the seven mutations existed only
in this session. Two things were added after the tasks were complete, by that pass:

- **`tools/mutate.sh`** — the generic runner for a shell gate (`--list`, `--only`, `--out`, `--timeout`), with
the two silent lies reported as failures (`MUTATION-DID-NOT-APPLY`: the search no longer matches the line, so
a green case is not a falsification; `STAYS-GREEN`: the mutation landed and the case did not notice), the
restore verified by content **and** permission bit, and an EXIT trap that restores a killed run's file;
- **`tools/portability-mutations.json`** — the seven tasks' mutations as the versioned set (eight records,
because 5.3 names two cases), so `bash tools/mutate.sh --mutations tools/portability-mutations.json`
reproduces the change's `evidence/revert-checks.md` in one command, and `--list` is printed by the CI step so
a set that no longer parses fails the pipeline instead of rotting.

`tools/mutate-selftest.sh` proves the runner's own guards on a toy tree (27 checks: the four failure modes,
`--list`, `--only` selecting nothing, the pristine tree after every run, and the portability set's eight
labels), and the two new skills (`.pi/skills/portability-check`, `.pi/skills/mutation-runner`) carry the
recipes; `guidelines/Build.md` §1 lists them, and `.pi/skills/revert-check` points at the generic runner.
Nothing in the change's own artefacts changed: the same seven mutations, in the same files, with the same
cases.

## Notes for the next change

- The `Check portability` CI step fails the pipeline on the commit that introduces a leak, so a later change
  has no reason to think about portability to keep it true — which is the point of putting it there rather
  than only in the self-test.
- Every command in `PORTING.md` is executed by `porting#everyCommandInTheRecipeRuns`; adding a step means
  adding a fenced `sh porting-step` block, and a stale one turns the self-test red.
- The fixture's `README.md` lists what it does not model (a non-GitHub tracker, another shell or no `jq`, a
  different build system). A difference a real adopting project has that the fixture does not model is a
  defect to add to the fixture, not a reason to weaken a case.
