# The structural checks this slice ran, and the two that are red for reasons that predate it

Ran 2026-11-15 on the slice's tree after the gate.

| check | exit | result |
|---|---|---|
| `tools/check-openspec-hygiene.sh` | 0 | 41 open changes carry task markers, the operation guidance is quoted, every capability spec states its Purpose |
| `tools/check-doc-routes.sh` | 0 | all 341 section references resolve, all 7 skill references resolve |
| `tools/check-doc-values.sh` | 0 | 6 roles, prose matches config |
| `tools/check-todo-references.sh` | 0 | 67 citations resolve through the recorded table |
| `tools/gen-feature-list.sh --check-classification` | **1** | `unclassified: 2`, `stale: 5` — both red for reasons that predate this slice, below |
| `tools/check-rule-routes.sh` | **1** | 7 rules reached by no glob — **also red at `1295939c` before this slice**: the same 7 errors, byte-identical output (`diff` empty). Rules in `BuildEvidence.md`, `BuildTests.md`, `UICar.md` and `UIPhone.md`; none of them this slice's subject |

`tools/gen-feature-list.sh --check-classification` fails on two findings, neither this slice's:

- **`unclassified: 2`** — the spec ids of the unrelated, locally present open change
  `settings-taxonomy-and-surface` (`settings-surface`, `settings-taxonomy`), which is untracked; a clean clone
  has no such directory and the gate reports `unclassified: 0` there.
- **`stale: 5`** — `architecture-test-suite`, `search-name-matching`, `search-result-row-icon`,
  `start-map-selection` and `store-assets`: classification entries for specs the tree no longer holds. The first
  is the capability #867 deleted; the other four are older debt. This slice adds no classification entry and
  removes none.

## Why the corpus changed, and what was left out of it

`openspec/specs/package-structure/spec.md` gained the two feature requirements, because a rule that cites a
requirement must point at a requirement a shipped spec states. Two things deliberately did not move:

- **The requirements of tasks that have not landed.** The change's delta carried seven requirements — the
  platform mirror, the shell hosting, the scope, the hook, the gate, the suppression rule, the fail-closed
  rule — whose cases (`PlatformRuleTest`, `ShellRuleTest`, `StructureScopeTest`, `StructureHookTest`,
  `GateSuiteListTest`, `StructureDocumentationTest`) do not exist in the tree. This archive's deltas keep only
  what landed: the two feature requirements and, in `architecture-test-suite`, the one about a violation's
  message. 5 scenarios stand here; the plan's other 25 stay with the open tasks.
- **`openspec/specs/architecture-test-suite/`.** #867 deleted that capability, and none of its requirements
  exists in the corpus today — including the baseline and report requirements the sibling archive
  `2026-11-14-package-structure-as-five-kinds` shipped, which its delta alone records. This slice follows that
  shape rather than recreating a capability a landed change removed; the gap is the earlier slice's, not this
  one's.