# `UIPhone.md` is cut in two: the phone map, and the phone's surfaces

## Why

The document bound is **32 000 bytes** (retuned 2026-10-31 — see `guideline-layers-mechanism` design D4 and its task 7.7). `guidelines/UIPhone.md` measures **47 926 B** and is the largest normative document in the tree, so it owes a cut.

Measured with `tools/check-doc-routes.sh --sizes`, its cost is almost entirely one cluster of map material and one cluster of surface material:

| cluster | sections | bytes |
|---|---|---|
| the phone **map** — modes, the re-centre control in three modes, overlay sizing, the layout bands, layering, the surface while a car session is active | §7, §7a, §7b, §8, §8a, §8b, §8c, §10a, §11 | **28 632** |
| the phone's **surfaces** — the details dialog, the search surface, and the promoted favourites/search affordances of the old `UI.md` §1 | §5, §6a, §12–§19 | **19 294** |

Split that way both documents are comfortably under 32 000, the boundary is the routing table's own line (a phone-*map* change reads one document, a phone-*surface* change reads the other), and — because a citation is repaired by naming a different file and never by looking up a new number — **no section number moves**.

**Acceptance scenario.** After the cut, `UIPhoneMap.md` and `UIPhone.md` are each under 32 000 B with every section under 12 000, their rules are named and reached by globs, their companions anchor to sections that exist, the heading-number set is the one they had before, and the `document-size` conditions drop from 4 to 3.

**Owning guideline.** `guidelines/UIPhone.md` itself, whose design record is `guideline-ui-layers`; the bound it is measured against is stated in `openspec/specs/documentation-ownership/spec.md`, "The routing cost is measured".

**Surface.** `wc -c guidelines/UIPhone.md` → `47926`; the two clusters above; `document-size` conditions before the cut: **4** (UIPhone, MapRendering, UI, Backlog).

surface: 0 cases added (the guards exist), 1 document becomes 2 normative documents (1 new), 0 rules added or renamed, 1 new routing row in `AGENTS.md`, 1 new glob group in `tools/doc-routes.json` — `wc -c guidelines/UIPhone.md && tools/check-doc-routes.sh --sizes | grep UIPhone`

## What Changes

- **`UIPhoneMap.md`** (new) takes §7, §7a, §7b, §8, §8a, §8b, §8c, §10a and §11, carrying their own numbers.
- **`UIPhone.md`** keeps §5, §6a and §12–§19.
- **`UIPhone.why.md` and `UIPhone.recipes.md`** are split with their documents, each half anchoring to the sections it explains: a companion belongs beside its base document and may not anchor to a section of another one.
- **`AGENTS.md`** gains one row for `UIPhoneMap.md` and its existing phone row is narrowed to `UIPhone.md`'s sections.
- **`tools/doc-routes.json`** gains the glob group for the new document's rules.

No requirement changes and no rule changes: `skip_specs: true`. The bound this cut serves is in `guideline-layers-mechanism`'s delta, which is still unarchived.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — no behavior changes; text moves and gets a second document.

## Impact

| path | what changes |
|---|---|
| `guidelines/UIPhoneMap.md` | new — §7, §7a, §7b, §8, §8a, §8b, §8c, §10a, §11 |
| `guidelines/UIPhone.md` | keeps §5, §6a, §12–§19; rules named and unchanged |
| `guidelines/UIPhone.why.md`, `UIPhone.recipes.md` | split beside their documents; `UIPhoneMap.why.md` created if the map half carries rationale |
| `AGENTS.md` | one row added, one narrowed |
| `tools/doc-routes.json` | the new document's rule globs |

No Compose source, resource, manifest or build file is touched. Scope: **documentation**.

**Rollback path.** Revert the commit; the new document is a new file and every line returns to `UIPhone.md`.

## Not in this change

- **The other three cuts** the retune forces apart: `guideline-maprendering-cut`, `guideline-ui-cut`, `guideline-backlog-cut`.
- **Retuning the bound again.** One value, one place: the registry row and the delta sentence.
- **Renaming any section.** A cut that moves a section's *file* retargets its citations; it does not renumber it.
