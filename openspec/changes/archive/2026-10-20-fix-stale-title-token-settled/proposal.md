# fix-stale-title-token-settled

## Why

The repair tool parks items it repaired on an earlier run. Reproduced on base `3211d4d` against the tracker's
own bodies (dry run, nothing written — `evidence/head-dryrun-real-items.txt`):

```
{"issue": 201, "repaired": false, "parked": true, "suffix": 1, "absent": 0,
 "unresolved": "- `create.sh` -> `tools/issues/create.sh` — the rewrite changed nothing"}
{"issue": 181, "repaired": false, "parked": true, "suffix": 1, "absent": 0,
 "unresolved": "- `close.sh` -> `tools/issues/close.sh` — the rewrite changed nothing"}
{"issue": 182, "repaired": false, "parked": true, "suffix": 1, "absent": 0,
 "unresolved": "- `update.sh` -> `tools/issues/update.sh` — the rewrite changed nothing"}
```

`read=3 due=3 repaired=0 parked=3`. All three bodies cite the **full tracked path** and every citation in them
resolves; the bare `create.sh` / `update.sh` / `close.sh` that re-classifies as `suffix` stands in each item's
**title** (`tools/issues/resolve-stale.sh:184` builds the matrix from `"$title\n$body"`, the pair the
pre-screen asks). The suffix loop then rewrites the token in the **body only** (`:211`–`:215`), where it occurs
as nothing but the tail of the canonical path — `rewrite.awk` refuses that occurrence by design (`standalone=1`,
the #200 rule) — so the body comes out byte-identical, and the no-op is appended to the same `unresolved`
accumulator a genuinely unresolvable citation feeds (`:258`). The park branch (`:292`–`:304`) then sets
`status/on-hold` on a record that is already correct.

This is the second half of #226's report: the run before this change repaired `#201`, `#182`, `#181` (each body
carried a doubled directory prefix, reported repaired) and the next run parked all three. A park is not a no-op:
the loop's pre-screen skips a parked item, so a repair freezes the work it just fixed and hides the reason
behind a label. It also falsifies the header's own claim (`tools/issues/resolve-stale.sh:27`): after a repair, a
second run is supposed to find nothing to do and post nothing.

**Root cause, one sentence.** The resolver classifies citations in the title and the body together but writes
the body alone, so a token that stands only in the title produces a body-identical rewrite whose no-op is
counted as an unresolved citation and parks the item.

**Red tests on HEAD** (`3211d4d`, added to `tools/resolve-stale-cases.sh`, each failing for its own reason,
2026-10-20T20:40:27Z, `evidence/red.txt`):

- `resolver#titleSuffixBesideItsCanonicalPathIsSettled` — a title naming `` `alpha.sh` `` beside a body citing
  `/alpha.sh`'s full `tools/alpha/alpha.sh`:
  `an item whose body already holds the canonical path is not parked: expected '0', got '1'` (exit 1).
- `resolver#titleAbsentCitationIsParkedWhereItStands` — a title citing a file the tree does not hold, beside a
  repairable body citation:
  `and the reason names where it is: 'the citation is in the title' not found in: gh issue edit 7 --add-label
  status/on-hold --remove-label status/open … - \`Ghost.kt\` — no tracked file, no rename record, no single
  surviving declaration` (exit 1). The item *must* still be parked — that is the guard against reading the fix as
  "skip what the body does not mention".

**Owning capability:** `stale-citation-resolution`, requirement "The resolver runs on its own, bounded and
idempotent" (its clause "SHALL be idempotent" and the scenario "A second run changes nothing") — the defect is
that a repaired item is not settled, so the next run acts on it again. **Owning guideline section:**
`guidelines/Backlog.md` §7 "The loop" (the paragraph stating what the resolver repairs and what it parks). The
gate is `tools/resolve-stale-selftest.sh`, the command the CI step "Check stale citation resolver" runs;
`.pi/skills/cleanup-todo` is the pass that met it.

## What Changes

- `tools/issues/resolve-stale.sh`: the body's own citation set is read once per item (`body_matrix`), and a
  helper `body_cites` answers whether a token stands in the body at all. The suffix loop skips a token the body
  does not carry — the class is not stale and the body already reads as it should, so the item is not due and
  nothing is written. The absent loop skips the rewrite for such a token too, but records it as unresolved with
  a reason that names the title, because the item *is* stale there (the pre-screen asks the title) and this tool
  cannot rewrite a title; parking it stays the record.
- `tools/resolve-stale-cases.sh`: the two red cases above.
- `tools/resolve-stale-mutations.json`: mutation `5.15`, the skip in `body_cites` — with it removed the settled
  case goes red again.
- `guidelines/Backlog.md` §7: one clause stating that a citation standing only in an item's title is never
  rewritten — one whose tracked path the body already carries is settled, and one the tree does not hold is
  parked — because the tooling writes bodies.

## Spec

`openspec/specs/stale-citation-resolution/spec.md`, requirement "The resolver runs on its own, bounded and
idempotent": MODIFIED — the requirement states that a token the body does not carry is never rewritten: a
resolving one leaves the item settled and unwritten, costing no slot, and a stale one is parked with the title
named and costs the slot a park costs. Two scenarios are added, one per red case; the five existing scenarios
each name their case.

## Cases and evidence

- The two red cases — red on HEAD, green after the fix (`evidence/red.txt`).
- `bash tools/resolve-stale-selftest.sh` — 27 of 27, offline, no token, no Gradle (`evidence/green.txt`).
- Mutation `5.15` — red with the skip removed, green after the restore (`evidence/revert-checks.md`); the whole
  set's tally in `evidence/mutations-full.txt`, with the pre-existing `5.7` named as an exclusion.
- The real-tracker reproduction, dry run: `evidence/head-dryrun-real-items.txt` (`parked=3`, HEAD) and
  `evidence/green-dryrun-real-items.txt` (`due=0 parked=0`, after the fix).

## Impact

`tools/issues/resolve-stale.sh`, `tools/resolve-stale-cases.sh`, `tools/resolve-stale-mutations.json`,
`guidelines/Backlog.md` §7 and the spec above. No compiled file is touched, so no Gradle gate applies
(`guidelines/Build.md` §2, the scope block); the structural gate is `tools/resolve-stale-selftest.sh`.

Behavioural change: an item whose only stale-shaped token stands in its title, beside the canonical path in its
body, is no longer due — it moves from `parked` to `skipped` and is not written. An item whose title cites a
file the tree does not hold is still parked, with a reason that says so. Every item that has a body token to
rewrite behaves exactly as before.

Rollback: revert the commit. The item is parked again on the run after its repair, and the reason reads "the
rewrite changed nothing" again — the base behaviour, with no tracker state to undo, because this change writes
nothing to the tracker by itself.
