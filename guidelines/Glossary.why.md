# Glossary — the rationale

This is the rationale layer of `guidelines/Glossary.md`. An anchor here names a section number the base
document carries, so the rule and the measurement that set it are read together.

## §1 The four numbers, and why a green run is the census

A definition written without proof is a plausible invention. This repository measured that failure once
already: `tools/check-change-readiness.sh` graded a body-reading rule's verdicts 48 wrong out of 49. So the glossary states no word it cannot
count, and every count comes from the run that reads the tree rather than from a table in a document.

Measured 2026-11-09 over `app/src/main`, `auto/src/main` and `core/src/main` — 462 Kotlin and Java files:

| measured | figure |
|---|---|
| `Basemap` / `BaseMap` / `baseMap` as a whole-identifier prefix | 256 / 0 / 0, the 256 split `core` 39, `app` 209, `auto` 8 |
| `speedKmH`-family vs `…Kmh`-family | 278 vs 144 — short of the ten-to-one majority `overruled` requires, so the code cannot settle it |
| `: Boolean` without `is`/`has`/`can` | 0 — the census reports `bare-booleans 0` |
| vacuous suffixes on a declared type | 36 |
| `favourite` vs `favorite` | 16 vs 790 |

The same run, on 2026-11-11, over the seed: rows 53, anchored 53, kinds attribute/entity/operation/value,
files 469 — 462 sources plus the 7 skeleton types the projection emits.

## §2 Why the key carries a kind

Two rows of one word would otherwise share one exclusion rule, and a refusal could not say which role failed.
The key shape `(<scope>/)<term>#<kind>` is the repository's existing capability key, `<spec-id>#<Requirement
name>` — one key identifies one thing — and the scope is written only for the 20 % of words that carry two
meanings.

## §3 Why an anchor, and what it costs

A scenario carries a `**Case**:` line or it is no anchor, and `guidelines/` names only some of its
rules. Requiring a case for every row would have made the words without one unwritable and grown this
change into a test-writing change; requiring nothing would have made a definition indistinguishable from a
guess. The seed is therefore the intersection of a code surface with an anchor, and a term with a surface and
no anchor is an item whose work is "write the case that would fail if this definition were wrong". The bound is
real: `overrun` carries no anchor today.

## §4 Why the concept kind is the only global scope

Four words are genuinely app-wide — `diagnostics`, `settings`, `unit`, `locale` — and the structure
declaration already has the kind for them: `concept` depends on nothing of ours. Popularity would have been
the wrong test: `Theme` is used in one package, `Log` in five, and neither count is a meaning. A third scope
value would have given "belongs to no feature" two ways to say so.

## §5 Why fourteen refusals and not a rule of taste

The corpus defines nothing — `"basemap is …"` and `"epoch is …"` have zero hits across `guidelines/` — so the
refusals are the only thing that can hold a definition to the tree. A closed vocabulary is what keeps a second
spelling of a finding from appearing, and it is what the mutation set can be written against: one mutation per
refusal, each naming the case that must fail when the guard is removed.

## §6 Why the projection is additive, and why no Gradle task runs it

The definition that lives only in `tools/glossary/` is a definition nobody reads while editing the type it
describes, so it is written where the type is. It never rewrites an existing block, because a generator that
rewrites a hand-written comment deletes what a person wrote; and the build does not run it, because a build
that writes production sources makes every incremental build a possible working-tree change. Hand-editing a
generated block is a finding under `--check`, not a regeneration.

## §7 Why the row's identity is the refusal

A drift family can hold fifty terms at once. With the term as the fingerprint, one bad night files fifty
issues and the one thing a person needs to see — one class of refusal, fifty terms in it — is the one thing the
tracker cannot show. `maxFilings: 2`, `consecutive: 2` and the first-run report keep a bad day at two or three
issues, and past three recurrences the class goes to a person. This is the choice `stale-citations` already
made with `fingerprint: {fields: ["kind"]}`.
