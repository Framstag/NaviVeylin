# Report — the case-name traceability check

Answers `#896` (epic `#895`). Shipped: `tools/check-case-names.sh`, `tools/case-harnesses.json`, 12 self-test cases
with 4 mutations, a CI step, a `checks` entry in `tools/tooling.json`, two guideline paragraphs, seven corrected
shipped specs and one renamed sensors case. Numbers and their commands:
`evidence/traceability-baseline.md`.

## What the check decides

| verdict | meaning | exit |
|---|---|---|
| `unresolved` | a `**Case**:` line names a case no declared harness runs; the line names the spec, the line, the scenario and the name | 1 |
| `duplicates` | two harnesses list one identical full name | 1 |
| `rows` | a registry row's case list is absent or its command fails | 1 |
| `undeclared` | a row emits a `group#name` in a group it never claimed | 1 |
| `hinges` | a row's `exclude` hides a name no other row declares — an exclusion is a delegation, not a hiding place | 1 |
| `unclaimed` | a number, printed in the verdict line and listed in `--json`; **never** a refusal | 0 |

The check compares identifiers against declared lists. It reads no sentence for meaning, so it cannot say whether a
case exercises its scenario — that remains the reviewer's judgement, and `tools/check-change-readiness.sh` owns the
`**Case**: none — <reason>` form this check only counts.

## Why 41 rows and not the four the proposal planned

The 2026-11-17 comment on `#896` proves a four-row registry cannot resolve the tree: 176 of 779 `group#case` names
stay unresolved, and about 100 names are Gradle forms (`Class#method`, `module: Class.method`) that a four-row
grammar cannot express. So the registry carries one row per suite a shipped spec cites — 41 of them, including the
Gradle and Catch2 trees, which one `classes` grammar covers. A suite with no row is not "trusted": its names simply
do not resolve, and the check refuses them with the spec, the line and the scenario in hand.

## Not in the loop's gate, on purpose

A specification-wide invariant must not redden one backlog item's gate, and the loop's selection names no spec. The
check runs in `.github/workflows/build.yml`, beside the artifact-hygiene step, and is reachable by name through
`tools/tooling.json`'s `checks` map (`caseNames`).

## What the first green run repaired

Seven shipped specs named a case no harness ran under that name — stale or invented identifiers, each corrected in
the spec to the case that actually runs (or to `**Case**: none — <reason>` with its reason). The sensors suite held
the one latent duplicate (`run#dryRunTouchesNothing`, shared with the loop's `run#dryRunTouchesNothing`); it was
renamed to `run#aDryRunWritesNothingToTheTracker` rather than declared, because one identical full name in two
suites is the one shape the registry refuses. The per-spec table is in the evidence.

Two structural checks were red on the tree before this change began — `check-rule-routes` (three rules left unrouted
by `#938` and `#894`, now routed) and `check-openspec-hygiene` (26 open changes with no `x-issue:` field; this
change carries `x-issue: 896`, and the rest are filed as `#999` rather than repaired under the sessions that hold
them).

## No capability, no build

No capability was created — this change adds a check and a registry to `change-readiness`'s neighbourhood, so
`tools/feature-list/specs.json` needs no row and `bash tools/gen-feature-list.sh` is unchanged. No compiled file
changed, so no Gradle build is owed; no loop case was renamed, so `tools/loop/tests/loop-selftest.sh` was not re-run.