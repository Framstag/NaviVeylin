# Stale citation resolution

An issue's citation is now checked by one rule and repaired automatically where the repository states the
answer: **one definition of what a citation is** (`tools/issues/lib.sh`, rules in
`tools/issues/citations.json`) asked by the loop's pre-screen *and* by `tools/issues/resolve-stale.sh`, a
mechanical ladder that rewrites a citation whose target moved, a quarantine for what nothing accounts for, and
a bounded scheduled run with a declared row watching the residue.

Measured on this tracker, 2026-10-18 — before, and after:

| | before | after |
|---|---|---|
| the 72 authorised open, non-feature items | **23 queued, 49 excluded `stale`** | **68 queued, 4 excluded `stale`** |
| the 49 recorded `stale` verdicts, re-decided | — | 25 resolve entirely, 22 repaired, **2 parked** |
| whole tracker | — | read 165 · due 60 · repaired 49 · parked 8 · ambiguous 6 |

The extraction it replaces was wrong about 48 of 49 verdicts (#164), and the exclusion was recorded nowhere
(#163) — both are fixed here.

## What landed

| path | what it is |
|---|---|
| `tools/issues/lib.sh` | the classifier: candidates, path rules, the six classes, the index, the suffix/submodule/rename lookups |
| `tools/issues/citations.json` | the citation rules as **data** — extensions, the header rule, the mention-only extensions, the external namespaces, the generated artefacts |
| `tools/issues/citations.awk`, `tools/issues/rewrite.awk` | boundary-checked extraction, and a literal boundary-checked token rewrite |
| `tools/issues/resolve-stale.sh` | the resolver: the ladder, the quarantine, one comment per item per run, `--max-items`, dry run by default, idempotent |
| `tools/loop/prescreen.sh` | asks the classifier instead of extracting tokens for itself |
| `tools/sensors/metrics/stale-citations.sh`, the `stale-citations` row | the residue as a measurement |
| `.github/workflows/sensors.yml` | one repair step in the pipeline that owns tracker writes |
| `.github/workflows/build.yml` | one `Check stale citation resolver` step |
| `tools/resolve-stale-selftest.sh`, `tools/resolve-stale-cases.sh` | the gate: 20 cases, offline |
| `tools/resolve-stale-mutations.json` | the nine revert-checks, runnable with `tools/mutate.sh` |
| `guidelines/Backlog.md` | §5 the resolver as a named writer, §6/§7 the shared rule, §9 the residue row |
| `AGENTS.md`, `.pi/skills/cleanup-todo` | the route, and the recipe |
| `tools/feature-list/specs.json` | the classification line |

## Gate results

| gate | result |
|---|---|
| `tools/resolve-stale-selftest.sh` | **20 of 20 cases passed**, exit 0 (JDK 27, offline, no token, no Gradle) |
| `tools/mutate.sh --mutations tools/resolve-stale-mutations.json` | **9 mutations, 9 red, 9 restored green**, exit 0 |
| `tools/mutate-selftest.sh` | 27 of 27, exit 0 (unchanged) |
| `tools/check-portability.sh` + self-test | exit 0 — the two new `*.awk` engine files join the checked set |
| `tools/loop-selftest.sh` | 45 of 45, exit 0 — the pre-screen change needed no case edit |
| `tools/check-todo-references.sh` | exit 0 |
| `tools/check-doc-routes.sh` + self-test | exit 0 |
| `tools/gen-feature-list.sh --check-classification` | exit 0 |
| `openspec validate stale-citation-resolver --type change` | valid |

**No Gradle build and no Gradle suite was run, and none is required** (the change edits shell, JSON, markdown
and two workflow steps). The change's gate is `tools/resolve-stale-selftest.sh`.

## What it may write, and what stays human

The resolver writes exactly three things: a **citation token** in a body, `status/on-hold` to park an item, and
**one comment** per item per run. It never writes `agent/*`, never classifies, never prioritises, never closes
and never files — and every write goes through `tools/issues/update.sh`, so the reserved-label refusal and the
closed vocabulary apply to it as to a person. `resolver#neverWritesTheAuthorisation` asserts that against the
recorded `gh` invocations.

"The claim is no longer valid" stays a person's decision (`close.sh --not-planned`, which the reopen path
refuses to reverse). The resolver's job is to make that decision cheap: the residue is small, named, ranked,
and visible in the tracker rather than in a machine-local file.

## Known residue

The 8 parked items are honest: `navigation/AANavigationController.kt`, `NavigationStateProvider.kt`,
`core/NavigationStopRequests.kt` (symbols fully removed, no rename recorded), `Test.kt` / `Combine.kt` /
`shift.py` / `luan.sh` (a name that exists nowhere), and `fix-open-database-path-validation/design.md` (a file
the in-flight change directory does not contain). Four of them are inside the authorised set — that is the
whole of the remaining `stale` exclusions, and each carries a comment naming what the ladder tried.
