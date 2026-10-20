# fix-stale-repair-verification

## Why

The scheduled repair reports repairs the tracker does not hold. Measured on the applying run of 2026-10-19
(`bash tools/issues/resolve-stale.sh --apply --max-items 200`, `due=22 repaired=14 parked=3`), **4 of the 14
"repaired" items were still classified `absent` afterwards**: #181 and #182 (the doubled form of
`tools/issues/close.sh` and of `tools/issues/update.sh`), #196 (the doubled form of
`tools/sensors/metrics/coverage.sh`) and #16 (a doubled `guidelines/` prefix). #181's timeline carries the
`citation repaired for the repository's own record` comment and **no `edited` event** — the body was never
written.

**Root cause.** A repair is counted, and commented, from the fact that a rewrite was *attempted*, never from the
record changing: `tools/issues/resolve-stale.sh:255` (base `648d2bc`) does `repaired=$((repaired + 1))` on
`[ -n "$repairs" ]` alone, `:253` counts the write `gh` is handed, and the comment at `:250` is posted from the
same variable — no comparison of the produced body with the input, no re-classification of the token. The
basename half makes the attempt reachable as a no-op: the suffix loop (`:208`) rewrites a bare citation
**everywhere**, including inside a doubled token, because `tools/issues/rewrite.awk:18` treats `/` as a
boundary and so rewrites `close.sh` at the tail of `tools/issues/tools/issues/close.sh`. The token gains a third
copy of its directory, and the absent loop (`:224`) then collapses the doubled form — which still occurs inside
that triple — back to exactly what it was. The two rewrites cancel: the body comes out of the loop byte-identical
to its input, and the run reports a repair for it.

**Red tests on HEAD** (`648d2bc`), added to `tools/resolve-stale-cases.sh` and each failing for its own reason
(2026-10-20T10:15:00Z, `evidence/red.txt`):

- `resolver#basenameRepairLeavesALongerPathAlone` — a body citing the doubled `tools/alpha/tools/alpha/alpha.sh`
  and the bare `alpha.sh`; the written body is still `the writer is \`tools/alpha/tools/alpha/alpha.sh\` and the
  bare name is \`tools/alpha/alpha.sh\``, i.e. the doubled citation survived:
  `and the doubled citation is collapsed, not re-doubled by the basename repair: 'the writer is
  \`tools/alpha/alpha.sh\`' not found in: …` (exit 1).
- `resolver#repairWithoutAChangeIsNotReported` — the same no-op in its purest form (one citation at
  `\`/alpha.sh\``, so the basename repair may not touch it and the body is unchanged):
  `no repair is counted when the body did not change: expected '0', got '1'` (exit 1).
- `resolver#repairIsVerifiedByReclassification` — the rename map names a successor that was deleted afterwards:
  `a target the history names but the tree does not hold is not a repair: expected '0', got '1'` (exit 1).

**Owning capability:** `stale-citation-resolution`, requirement "A citation is repaired when the repair is
mechanical" — its clause "A repair SHALL rewrite the citation token and nothing else" and its scenario "A
repair is recorded". **Owning guideline section:** `guidelines/Backlog.md` §7 "The loop" (the paragraph stating
what the resolver repairs and parks); the gate is `tools/resolve-stale-selftest.sh`, the CI step "Check stale
citation resolver".

## What Changes

- `tools/issues/resolve-stale.sh`: the two rewrites record a **pair** (`old` → `new`) instead of a bullet; after
  both loops every pair is verified — the produced body must differ from the input, the target must classify as a
  citation the tree holds, and the old token must not survive in the new body as an `absent` citation. Only
  verified pairs are composed into `repairs` (the bullets the comment and the report use), and a pair that fails
  becomes an `unresolved` line, so the item is parked with the reason instead of reported repaired. The `due`
  guard keeps its place: it now counts verified repairs.
- `tools/issues/rewrite.awk`: a pair rewrite may pass `standalone=1`, which refuses an occurrence preceded by
  `/` — a basename repair may not rewrite the tail of a longer path. The suffix loop passes it; the absent loop
  (which writes full paths) keeps the current boundary rule.
- `tools/resolve-stale-cases.sh`: the three red cases above, plus the `absent_in` helper they re-classify with.
- `tools/resolve-stale-mutations.json`: mutation `5.5`'s search string follows the changed suffix-loop line, and
  three records are added — `5.12` (the `standalone` rule), `5.13` (the changed-body guard) and `5.14` (the
  re-classification guard) — so each new invariant has its revert-check.

## Spec

`openspec/specs/stale-citation-resolution/spec.md`, requirement "A citation is repaired when the repair is
mechanical": MODIFIED — the requirement states that a repair is a change to the record, counted and commented
only when the body differs from the input and the rewritten citation classifies as no longer `absent`, and that
a basename repair does not rewrite an occurrence that stands inside a longer path. Three scenarios are added
(one per red case); the five existing scenarios each name their case.

## Cases and evidence

- The three red cases above — red on HEAD, green after the fix.
- `bash tools/resolve-stale-selftest.sh` — 25 of 25, offline, no token, no Gradle (`evidence/green.txt`).
- Mutations `5.12`, `5.13` and `5.14` — red with the rule removed, green again after the restore
  (`evidence/revert-checks.md`); the whole set reports 13 of 14 falsified, the one problem being the pre-existing
  `5.7` whose search predates the profile's hold status (`evidence/mutations-full.txt`).
- `evidence/red.txt`, `evidence/diff.txt`, `evidence/green.txt`, `evidence/issue-tooling-selftest.txt`,
  `evidence/portability.txt`, `evidence/loop-selftest.txt`.

## Impact

`tools/issues/resolve-stale.sh`, `tools/issues/rewrite.awk`, `tools/resolve-stale-cases.sh`,
`tools/resolve-stale-mutations.json` and the spec above. No compiled file is touched, so no Gradle gate applies
(`guidelines/Build.md` §2, the scope block); the structural gate is `tools/resolve-stale-selftest.sh`, which CI
runs.

Behavioural change, visible in the run's own line: an item whose rewrite nets out, or whose target the tree does
not hold, is no longer counted `repaired` and is no longer commented as repaired — it is parked with the reason
(`unresolved`), which moves it from `repaired` to `parked` for exactly those items and leaves every other item
untouched. Items whose citations do repair are unaffected in body, comment and count.

Rollback: revert the commit. A no-op then counts as a repair again — exactly the old behaviour, with no tracker
state to undo, because this change writes nothing to the tracker by itself.
