# `UI.md` becomes a cross-variant document and a phone document, each layered

## Why

`UI.md` costs 110 793 bytes and the routing table already treats it as two documents: the phone rows name §7, §7a,
§8, §8a, §9, §10, §11 and §10a, the car rows name §2, §3, §3b, §3c, §4 and §6b, and the parity row names §1 and §6.
A reader sent to the phone's overlay sizing opens §8, 20 271 bytes, inside a 110 793-byte file.

Three sections are past the 12 000-byte section bound — §1 "Cross-variant UI parity" 26 015, §8 "Phone navigation
overlay sizing" 20 271, §7a "Phone map re-center button" 13 867 — and the document is 2.3× the document bound. Three
`### Why …` rationale headings are already written by hand inside §1 and belong in a companion:
`Why the phone is not locked during a car session`, `Why favorite, group and starred reordering are phone-only`,
`Why the license list is phone-only`.

By cluster, what the bytes are:

| cluster | sections | bytes | what it is |
|---|---|---|---|
| cross-variant parity | §1, §6 | 27 066 | rules, plus three `### Why …` blocks and parity-decision history |
| car host constraints and templates | §2, §3, §3a, §3b, §3c, §4, §6b | 19 131 | normative — what the host permits, and the guarded seam |
| phone surfaces | §5, §6a, §7, §7a, §8, §8a, §10a, §11 | 51 208 | normative rules wrapped around measured pixel tables |
| shared | §9 dark mode, §10 i18n | 11 833 | normative, both surfaces |
| unnumbered | "Keeping this document honest" | 1 728 | a maintenance rule |

The phone cluster is 51 208 bytes and the car-plus-parity cluster is 46 197 — one document cannot be both under the
bound, and the routing table has already drawn that line. The sections' **numbers** are what the tree cites, so the
split keeps them: a citation that said `UI.md §8` says `UIPhone.md §8`.

**Acceptance scenario.** After the change, `UI.md` and `UIPhone.md` are each under 48 000 bytes with every section
under 12 000, their rules are named, their rationale is in companions, the three `### Why …` blocks are read from
`UI.why.md §1`, and every section number is what it is today.

**Owning guideline.** `guidelines/UI.md` §1 "Cross-variant UI parity (general rule)" is the section that owns the
parity rule this split preserves; the layer rule is `openspec/specs/documentation-ownership/spec.md`, "A guideline
document carries one kind of text".

**Surface.** Measured with `tools/check-doc-routes.sh --sizes`:

```
UI.md today               110 793 B, 19 sections, 3 past the 12 000 bound
document-size conditions it carries              1
rule names it carries today                      0
unnamed rules it carries (`- **…**` bullet / `SHALL`+`MUST` lines)  1 / 39
hand-written rationale headings                  3 (### Why …, inside §1)
```

surface: 0 cases added (the guards land in `guideline-layers-mechanism`), 1 document cut into 2 normative documents
plus companions, 0 fixtures realigned — `tools/check-doc-routes.sh --sizes | grep '^UI'`

## What Changes

- **`UI.md`** keeps the cross-variant and car material: §1, §2, §3, §3a, §3b, §3c, §4, §6, §6b, §9 and §10, and its
  maintenance section.
- **`UIPhone.md`** takes the phone surfaces: §5, §6a, §7, §7a, §8, §8a, §10a and §11 — carrying their own numbers.
- **§1 is split into lettered sub-sections** (`§1a`…`§1f`) because its parity rules, its parity decisions and its
  three `### Why …` blocks are three different things under one number today; the number still resolves.
- **§8 and §7a are split** the same way where the measured pixel tables push them past the bound.
- **Companions**: `UI.why.md` (the three `### Why …` blocks and each section's rationale), `UIPhone.why.md` (why a
  control is phone-only, the measured sizes a bound came from), `UI.recipes.md` and `UIPhone.recipes.md` (UI-dump
  geometry recipes, `pixel-check` steps, the number tables).
- **Every rule is named** in both normative documents, `**Rule: <name>**`, unique across `guidelines/*.md`.
- **`AGENTS.md`** retargets its phone rows to `UIPhone.md`, keeps its parity and car rows on `UI.md`, and adds one
  row per new document.

No requirement changes. `skip_specs: true` — the layer rule and the guards landed in `guideline-layers-mechanism`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — no behavior changes. The parity requirement ("same labels/hierarchy, or the platform constraint that forces
the deviation") keeps its text; this change moves it into the surface's own document and names its rules.

## Impact

| path | what changes |
|---|---|
| `guidelines/UI.md` | reduced to parity, car and shared surfaces; §1 split; rules named |
| `guidelines/UIPhone.md` | new — §5, §6a, §7, §7a, §8, §8a, §10a, §11 |
| `guidelines/UI.why.md`, `UI.recipes.md`, `UIPhone.why.md`, `UIPhone.recipes.md` | new companions |
| `AGENTS.md` | phone rows retargeted, two rows added |

No Compose source, manifest, resource, string table or build file is touched: this is six text files and the routing
rows. Scope: **documentation** — and no deliberate deviation from parity is introduced or removed, because nothing
but location changes.

**Rollback path.** Revert the commit; the companion and `UIPhone.md` are new files and the text returns to `UI.md`.

## Not in this change

- **The other five documents** and the four open loop changes.
- **Merging the phone and car documents back together** — the routing table drew that line before this change did.
- **A generated companion index**, as in the other layering changes.
