# Proposal

## Why

`tools/issues/create.sh` collects every route a label can arrive through — the finding's own `.labels[]`, each
`--label`, and `--status <value>` composed as `status<sep><value>` — into one `labels` array and then runs the
single guarded check (`issues_check_labels`, `tools/issues/create.sh:96-108`) over it. That check asks the
profile whether each value exists and is not reserved; nothing asks whether the list names **two values of one
namespace**. `update.sh` grew exactly that rule for the state namespace (`fix-status-label-replacement`,
D5/D8); the filing path has no equivalent. A finding whose labels already carry `status/fixed-by`, filed with
`--status open`, is therefore born carrying both `status/fixed-by` and `status/open`, and no state selector
reads it correctly.

Root cause and evidence:

- **Root cause**: the merge at `tools/issues/create.sh:81-92` produces one flat label list and the only guard
  over it is the per-value vocabulary check (`issues_check_labels` → `Policy labels`,
  `tools/issues/core/Policy.java:163-167`). No code on the filing path compares two labels of one namespace, so
  two values of the state namespace (or of any other) pass unchecked into `gh issue create`.
- **Reproduction** (offline, stub `gh` first on `PATH`; recorded in `evidence/red-on-head.log`): a finding with
  `labels: ["class/bug","category/ui","status/fixed-by"]` filed with `--status open` exits `0` and records
  `gh issue create … --label status/fixed-by --label status/open` — **two** `status/*` labels on one item.
- **The invariant broken**: an item carries exactly one value of a namespace (`AGENTS.md`, "Backlog
  maintenance"; `guidelines/Backlog.md` §4, I16). At filing time there is nothing to displace, so the rule is a
  refusal, not a replacement.

The single fix follows from the root cause: the filing path refuses a merged label set that names two values of
one namespace, before any `gh issue create`. The requirement the defect contradicts is `issue-backlog` →
"Labels carry the item's kind, area, state and provenance" ("exactly one value per namespace where the item has
that fact"). There is one defensible fix, so the item is loop-eligible.

## What Changes

- `tools/issues/create.sh`: after the vocabulary gate, the merged label set is refused with exit 2 and a reason
  naming both values when two labels share a namespace prefix. The check is per namespace, not status-only,
  because the profile's namespace model (a declared name plus a separator plus a value set) makes the same rule
  hold for every namespace — the invariant is one value per namespace, and `create.sh` is the path where the
  item is born. The state named only through `--status`, or only through the labels, keeps working.
- The refusal happens **before** any `gh issue create`, so a contradictory request writes nothing to the
  tracker — the same shape (`exit 2`, a message naming both values) `update.sh` uses for the same mistake
  (`fix-status-label-replacement`, D5).
- The stub `gh` is **not** changed: the real `gh` does not refuse a duplicate-namespace label set — measured
  during this change's triage, a real `gh issue create` accepted `--label status/fixed-by --label status/open`
  and created the item — so a stricter stub would assert a behaviour the client does not have and hide that the
  refusal must live in `create.sh`.
- Test: `tools/issue-tooling-cases.sh` gains `create#refusesTwoValuesOfOneNamespace`, red on HEAD and green
  after the fix (offline, stub `gh` with `GH_STUB_RECORD`); the mutation set `tools/create-mutations.json`
  (registered in `.github/workflows/build.yml`, "Check issue tooling") falsifies the new invariant.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `issue-backlog`: the requirement "Labels carry the item's kind, area, state and provenance" gains the filing
  path's share of the one-value-per-namespace invariant — a filing whose merged label set names two values of one
  namespace is refused before any write, with a reason naming both. The delta is MODIFIED over that one
  requirement, so no unrelated text is rewritten and no existing scenario is dropped.

## Impact

- `tools/issues/create.sh` — the fix.
- `tools/issue-tooling-cases.sh` — the red case.
- `tools/create-mutations.json`, `.github/workflows/build.yml` — the new invariant's falsification set and its
  CI listing.
- `guidelines/Backlog.md` §4 — unchanged: I16 already states the one-value-per-namespace rule; §5 ("Who may
  write what") is unchanged, since no new writer appears.
- `tools/issues/fixtures/gh` — deliberately unchanged; the reason is under "What Changes".
- No caller breaks: `import.sh` passes no `--status` and its findings name one value per namespace;
  `tools/sensors/run.sh` passes `--sensor` beside a finding with no sensor label; `tools/loop/labels.sh` drives
  `update.sh`, not this path. This is additive in intent: a filing that named one value per namespace is
  unaffected.
- Not breaking a data format: no persisted schema, no migration, no new dependency. Rollback is reverting the
  one guard and the delta; nothing reads the tool's internals.
- No native, JNI, submodule or Android surface is touched; this is tracker tooling only.
