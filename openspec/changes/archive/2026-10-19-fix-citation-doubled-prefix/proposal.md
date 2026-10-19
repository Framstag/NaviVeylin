# fix-citation-doubled-prefix

## Why

An agent-written body can carry a token whose directory prefix is said twice —
`tools/issues/tools/issues/close.sh` for `tools/issues/close.sh`. Nothing on disk resolves the doubled form,
so the classifier answers `absent` (correctly: the token does not exist), the repair ladder in
`resolve-stale.sh` had no rule for that shape, and the run excluded the item as `stale` without repairing
or parking it. Four of the eight issues filed on 2026-10-19 carried one, and the run of that morning
excluded #118, #98, #73, #45 and #124 the same way (#189).

## What changed

- `tools/issues/lib.sh`: `issues_citation_collapsed` — the token with its repeated leading directory
  removed, or nothing. One definition, beside the classifier it depends on.
- `tools/issues/resolve-stale.sh`: the ladder tries the collapse **before** the rename and symbol steps,
  because it is a fact about the token rather than an inference from the tree. The repair comment says so:
  "the citation repeats its own directory: the collapsed path is the tracked one".
- `tools/issues/create.sh`: the filing path refuses the shape and names the tracked path, so a finding is
  not born stale. Only this shape is refused — a body may legitimately cite something the change itself
  creates, and that stays the repair path's business.

## Spec

No delta: the classifier's contract (`stale-citation-resolution`, D1–D4) already says that a citation the
repository states uniquely is repairable; the ladder lacked the step, and the filing path lacked the check.

## Cases and evidence

`resolver#doubledPrefixIsCollapsed` — a tree whose file name is not a symbol in its own content, so only the
collapse can answer (red before the fix: the item was parked and the body untouched) — and
`create#refusesADoubledCitation` (exit 2, names `tools/issues/close.sh`, no `gh issue create` recorded).
Mutations 5.10 in `tools/resolve-stale-mutations.json` and 1.2 in `tools/create-mutations.json`.

- `evidence/green-on-fix.txt`, `evidence/revert-check.txt`, `evidence/gate.txt`

## Impact

`tools/issues/lib.sh`, `tools/issues/resolve-stale.sh`, `tools/issues/create.sh`,
`tools/resolve-stale-cases.sh`, `tools/issue-tooling-cases.sh`, both mutation sets.

## Corrected on the tracker

The item's first comment claimed a bare filename in a *title* was an unrepairable stale citation. That was
a probe artifact: without `ISSUES_CITATION_INDEX` the classifier answers `absent` where the pre-screen's
indexed call answers `suffix`, and with the index #181, #182, #184, #185 and #189 itself classify clean. No
title write was needed and none was added.

## Open

Nothing rewires the run to call `resolve-stale.sh` automatically; five known stale items (#118, #98, #73,
#45, #124) stay excluded until someone runs the repair. That is a separate decision.
