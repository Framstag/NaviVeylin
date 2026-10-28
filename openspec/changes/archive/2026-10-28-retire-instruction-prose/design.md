# Design — retire the instruction prose path

## Context

After the two preceding changes the prose path has no reader. What remains is mechanical: the bridge's Java
projection of `RouteDescription` (both route-calculation paths) and its per-position instruction builder, plus
the Java class and the requirements that describe them.

## D1 — When the prose goes

| Option | Cost | Risk |
|---|---|---|
| **A. Remove it after JavaScout's adaptation lands** (chosen) | one waiting period, tracked by an issue | the ordering is explicit, so a slip is visible rather than discovered by a consumer |
| B. Remove it now and let JavaScout adapt afterwards | no waiting | the sibling consumer loses both its route list and its guidance source at once, from an unrelated repository's change |
| C. Keep it and mark it deprecated | nothing | a second model with a reader-shaped temptation stays in the API; the next consumer reads English text again |

Chosen A: this change is a deletion, and a deletion is only safe when nothing reads the thing.

## D2 — How the arrays go

| Option | Cost | Risk |
|---|---|---|
| **A. Remove them together with the instruction type, in one pin** (chosen) | one compile-time break for a consumer that still reads them | a consumer learns it from the compiler |
| B. Publish `null` for one release first | a migration window | `null` cannot be told apart from "this route produced no values", which the same field legitimately means |
| C. Keep the arrays and drop only `descriptions` | half the code stays | the arrays exist to be index-aligned with description lines; without the lines they describe nothing |

Chosen A. The alignment invariant between lines and arrays is what makes either half meaningless alone, so they
go together — and `openspec` records the requirement removal with its migration path (`Per-step leg values on a
calculated route`).

## D3 — `TurnType` stays

`TurnType` is shared: `RouteStep.Direction.turn`/`curve`, `MotorwayChange.move` and `MotorwayLeave.move` are
`RouteDescription::DirectionDescription::Move` values bridged by the same name. Alternatives: remove it and
introduce a separate `Move` type (a churn across bridge, DTOs and every app surface with no behavioural gain),
or keep a private copy in the Java DTO (two enums with one meaning — the defect class this work is removing).

Chosen: keep `TurnType` for the step model, delete only `RouteInstruction`.

## D4 — Spec prose the deltas cannot carry

A delta's `## Purpose` is read only when a capability is created, so `turn-instruction-localization`'s and
`i18n-l10n`'s Purpose paragraphs — which still say the native layer generates English text — cannot be fixed by a
delta block. Alternatives: leave them (the documentation would state the opposite of the code, which
`documentation-ownership` forbids), or delete and recreate the capabilities (loses requirement identity, breaks
every `cap:` key in `FEATURES.md` and every cross-reference in other specs). Chosen: repair the two paragraphs in
the spec files themselves in this change, and verify them by reading the files plus `openspec validate` (a
precedent exists: the `TBD - created by archiving …` placeholders were repaired by hand).

## D5 — What is deleted and what stays

Deleted: the bridge's Java projection (both `DescCallback` copies, `JavaRouteInstructionBuilder`, `CollectCallback`,
`MoveToDescription`, `MoveToTurnType`), `RouteInstruction.java`, and the field tests that assert its text.

Kept: `osmscout::RouteDescription` and `RouteDescriptionPostprocessor` in libosmscout — Qt, the other clients and
JavaScout-after-adaptation read them; `TurnType`; `RouteDescription::DirectionDescription::Move`.

Alternative rejected: removing the description model upstream (breaks every other client, and this repository's
mandate is the Android app).

## D6 — Proving a deletion

| Option | Cost | Risk |
|---|---|---|
| **A. Grep gates over the trees plus the compiler** (chosen) | one command per tree, recorded as evidence | a string check, not a behavioural one |
| B. A unit test that asserts absence | a test that fails on a comment or a doc string | a source scan in a test suite is maintenance without behaviour |
| C. Review only | nothing | the thing came back once already in this project's history |

Chosen A, with the gates named in the tasks: no `RouteInstruction`, `descriptions`, `instructionLats`,
`instructionLons`, `instructionDistances` or `instructionTimes` in `app`, `auto`, `core`, `osmscout-client-java`
or the submodule's `libosmscout-client-java`, and none in `openspec/specs`.

## Threading and lifecycle

No change. The deletion removes code from the position dispatch that already runs under the existing guard; no
thread, dispatcher or lifecycle owner is added or moved.

## Risks

| Risk | Mitigation |
|---|---|
| A consumer outside this repository reads `RouteEntry.descriptions` | the pin commit and the release notes state the removal; JavaScout's adaptation is the known one and is gated in task 1.1 |
| The removal is reverted halfway, leaving arrays without lines | the removal is one pin commit; the grep gate fails on a half state |
| `FEATURES.md` loses a capability key that still has a requirement | the catalogue gate (`tools/gen-feature-list.sh`) runs as a task, and an unclassified requirement stops it |
| A stale spec paragraph survives and contradicts the code | task 3.2 reads both Purpose paragraphs after the edit; `openspec validate --strict` runs over the open set |

## Verification

- **Build**: both flavors, all three ABIs; the submodule's own suite (`RouteStepModelTest`, `NavigationAgentTest`)
  green.
- **Grep gates**: the six names above, over five trees.
- **Catalogue**: `tools/gen-feature-list.sh` green and `FEATURES.md` diff reviewed against the retired keys.
- **Behaviour unchanged**: re-run the route-list case and the live-guidance device case from the two preceding
  changes after the pin bump — a deletion that changes behaviour is a defect, and those two cases are the ones
  that would notice.
- **No new revert-check**: this change introduces no invariant; it removes code and requirements. The two
  preceding changes own the invariants, and their revert-checks stay valid.
