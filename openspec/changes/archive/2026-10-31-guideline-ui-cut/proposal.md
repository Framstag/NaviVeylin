# `UI.md` is cut by variant: the car's surface and the rules both variants share

## Why

The document bound is **32 000 bytes** (retuned 2026-10-31; `guideline-layers-mechanism` D4 and task 7.7). `guidelines/UI.md` measures **45 327 B** and owes a cut.

The routing table already draws this line — it has a row for car screens and a row for parity, and they name disjoint sections. Measured with `tools/check-doc-routes.sh --sizes`:

| cluster | sections | bytes |
|---|---|---|
| the **car's** surface — action glyphs, the host's template constraints, renderer startup, the settings screen, the wait notice, the car search template, the details attribute list | §2, §3, §3a, §3b, §3c, §4, §6b | **20 651** |
| **shared and parity** — the parity rule, the shared resolution rule, dark mode, internationalisation, label wrapping, and the document's own maintenance section | §1, §6, §9, §10, §20 + maintenance | **19 500** |

Two documents, each one subject, both comfortably under the bound and **no section number moved**.

**Acceptance scenario.** After the cut, `UICar.md` and `UI.md` are each under 32 000 B with every section under 12 000, no section number moves, the named-rule count is unchanged, every citation in the measured referring set resolves, and the `document-size` conditions drop from 2 to 1.

**Owning guideline.** `guidelines/UI.md` §1 "Cross-variant UI parity" is the rule this cut must not disturb — it is the document's reason to exist and it stays. The bound is in `openspec/specs/documentation-ownership/spec.md`, "The routing cost is measured".

**Surface.** `wc -c guidelines/UI.md` → `45327`; the two clusters above; `document-size` conditions before the cut: **2** (UI, Backlog).

surface: 0 cases added, 1 document becomes 2 normative documents (1 new), 0 rules renamed, 1 routing row, 0 fixtures — `wc -c guidelines/UI.md && tools/check-doc-routes.sh --sizes | grep '^UI'`

## What Changes

- **`UICar.md`** (new) takes §2, §3, §3a, §3b, §3c, §4 and §6b, carrying their own numbers.
- **`UI.md`** keeps §1, §6, §9, §10 and §20, and its maintenance section.
- **Companions split beside their halves** — `UI.why.md` (15 551 B) and `UI.recipes.md` carry anchors into `UI.md`'s numbers, so the car half's rationale moves to `UICar.why.md` **only if that half holds it**; a companion that would anchor to no section is not created.
- **`AGENTS.md`**: the car rows are retargeted to `UICar.md`, the parity and shared rows stay on `UI.md`, and one row is added. Lettered sub-sections a row names stay priced.
- **`tools/doc-routes.json`** gains the new document's rule globs.
- **Citations** naming a moved section are retargeted name-only. The referring set is **measured**, not assumed: `grep -rln 'UI\.md.*§' guidelines/ AGENTS.md .pi/skills/` → `AGENTS.md`, `guidelines/Backlog.recipes.md`, `guidelines/Design.md`, `guidelines/UIPhone.md` (and `guidelines/UIPhoneMap.md`, created by `guideline-uiphone-cut`, if it cites one).

No requirement changes and no rule changes: `skip_specs: true`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — no behavior changes. Nothing here deviates from parity and nothing stops deviating.

## Impact

| path | what changes |
|---|---|
| `guidelines/UICar.md` | new — §2, §3, §3a, §3b, §3c, §4, §6b |
| `guidelines/UI.md` | keeps §1, §6, §9, §10, §20 and its maintenance section |
| `guidelines/UI.why.md`, `UI.recipes.md` | split; `UICar.why.md` / `UICar.recipes.md` only if that half holds the material |
| `AGENTS.md`, `tools/doc-routes.json` | car rows retargeted, one row added; globs |
| `guidelines/Backlog.recipes.md`, `Design.md`, `UIPhone.md` | name-only citation retargets |

No Compose source, string table, resource or manifest is touched. Scope: **documentation**.

**Rollback path.** Revert the commit; the new document is a new file and every line returns to `UI.md`.

## Not in this change

- **The last cut** the retune forces: `guideline-backlog-cut`.
- **A third document for the shared material.** §9 dark mode and §10 internationalisation apply to both variants, which is exactly what `UI.md` owns after the car leaves it — they are the parity document's own substance.
- **Renumbering.** A citation is repaired by naming the new file.
