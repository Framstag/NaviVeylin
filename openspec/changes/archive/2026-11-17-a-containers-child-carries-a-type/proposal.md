# Proposal — a container's child carries a type, and the pass says when it does not

## Why

Measured 2026-11-14, on the two epics in this tracker:

```
#838  (Epic)  children 867 868 869 870 871 872 873   all issueType = Task     <- visible in the epic panel
#895  (Epic)  children 890..909 (nine)              all issueType = null     <- invisible in it
```

The sub-issue link was correct in both cases: `parent = 895` and `subIssues.totalCount = 9` were confirmed twice. What
was missing was the child's **type**, and GitHub groups a container's sub-issues by type — so nine children of a
finished epic did not appear under it, and the epic looked empty. An author reported it; nothing in the tooling would
have.

`tools/issues/create.sh` accepts `--parent` and `--type` as two separate flags and neither implies the other, and
`tools/issues/check-metadata.sh` checks five per-item invariants — the band is in the vocabulary, at most one band,
marker and label both present and agreeing, and the settled-state label — **plus nothing about the type or the
container**. The axis that decides whether an item is work at all (the pre-screen refuses a container by
`roles.containerType`) is the one axis with no reader.

## What Changes

- **`tools/issues/check-metadata.sh`** gains a sixth per-item invariant, in both of its per-item modes (`--live` and
  `--issues`): an item whose `.parent` is set SHALL carry a declared issue type, and an item whose type is the
  profile's `containerType` SHALL be a container others point at — a violation is named with the child, its container
  and the type it lacks.
- **The rule is "carries a type", not "carries a particular type"**: a child may be any type the account declares.
  The failure being fixed is *no* type, which is what made the children invisible.
- **The comments that cite a non-existent capability are corrected**: nine sites in eight files named `issue-types` as
  the owning capability (`create.sh`, `update.sh`, `lib.sh`, `loop/lib.sh`, `loop/prescreen.sh` twice,
  `loop/tests/loop-cases.sh`, the cases' header, the stub's header), and no spec under `openspec/specs/` and no
  guideline carries that id — the axis is `issue-backlog`'s, "The tracker's own classification is a second axis".
- **Scope**: the issue tooling, the one sensor fixture helper whose tracker now carries the field the invariant
  reads, and the documents that route the rule. No app code, no build change, no dependency, no label vocabulary
  change.
- **Not this change**: making `create.sh --parent` imply a type. A default would be the tooling deciding what a child
  *is*, and a child's kind is the filer's judgement.

## Capabilities

### New Capabilities

None. The axis already has an owner — `issue-backlog`, "The tracker's own classification is a second axis" — so a new
capability would create a second home for one rule (and owe a row in `tools/feature-list/specs.json`).

### Modified Capabilities

None. `issue-backlog` gains two requirements, both new: a container's child carries a type, and the metadata pass
reports one that does not. The shipped "second axis" requirement is untouched — it says how a type is written and read,
not that a child must have one.

## Impact

Code:

- `tools/issues/check-metadata.sh` — the sixth invariant, in the `--live` and `--issues` paths, with a `--json` field
  and a human line naming the child, its container and the missing type.
- `tools/issues/create.sh`, `update.sh`, `lib.sh`, `tools/loop/lib.sh`, `tools/loop/prescreen.sh` and the two test
  headers — eight comments: the owning capability, which is `issue-backlog` and not the `issue-types` they named.
- `tools/issues/tests/issue-tooling-cases.sh` — the six `metadata#` cases, the single entry point a case name
  resolves through.
- `tools/issues/tests/fixtures/metadata/` — two new arrays (a container with a typed and an untyped child, and a
  document carrying no type field at all) and the seven existing arrays, realigned with the field the pass reads.
- `tools/sensors/tests/sensor-cases.sh` — `issues_fixture` gives every fixture item the type key, so the stub tracker
  answers the axis the way the real client does.

Guidelines and documents:

- `guidelines/Backlog.md` §3 (what the tracker holds) — a container's children carry a type, and the pass is where
  that is checked.
- `guidelines/BacklogTooling.recipes.md` §9 (the declared rows) — the `metadata-invariant` row gains the sixth
  invariant; `BacklogTooling.md` §9 points at that table and states no row itself.

Rollback: revert the commit. One check, six cases, the fixtures they read and prose; nothing reads the invariant at
run time except the pass and the row that runs it.

Costs to prove by measurement rather than assume:

- **the number of untyped children in the tracker**, from the check itself over `--live`: today the nine children of
  #895 are the only ones in the two epics, and re-taking the figure before the fix is what makes the case's fixture
  real rather than invented;
- **the pass's own cost**, from one `--live` run: the claim is that one more field read per issue does not change it.

surface: 2 requirements and 6 scenarios added to 1 delta file, 0 requirement bodies rewritten, 1 invariant added to one
check in two modes, 6 `metadata#` cases, 7 fixtures realigned and 2 added, 9 capability comments corrected in 8 files — counted
with `grep -c '^#### Scenario:' openspec/changes/a-containers-child-carries-a-type/specs/issue-backlog/spec.md` (6),
`grep -c '^### Requirement:' openspec/changes/a-containers-child-carries-a-type/specs/issue-backlog/spec.md` (2), and
`tools/look.sh 'untyped-child|type-axis-unreadable' --files` (the check and the cases that drive it)
