# `Design.md` is cut into the four kinds of change that read it

## Why

`Design.md` costs 74 205 bytes over 13 sections. Two sections are past the 12 000-byte section bound — §5 "Native
boundary" 12 781 and §8 "Android Auto & cross-variant" 12 058 — and the document is 1.5× the document bound. Two
`## Appendix` blocks (2 281 B) are unrouted, and §13 "Superseded decisions" (4 967 B) is pure rationale sitting in
the middle of the document.

The document is addressed by four different kinds of change at once, which is why the routing table sends four
different readers to it:

| cluster | sections | bytes | what it is |
|---|---|---|---|
| layering, state, threading | §1, §3, §4, §12 | 23 025 | normative — the rules a new screen, a ViewModel or a dispatcher obeys |
| the stack and its recipes | §2 | 4 737 | normative rules plus the recipes for adding a dependency, a screen, a sheet, a native function |
| the native boundary | §5 | 12 781 | normative — both sides of the JNI bridge, the two database-open entries, the submodule discipline |
| rendering, follow, car, data, build, testing | §6, §7, §8, §9, §10, §11 | 32 803 | normative seams into the other documents, plus §8's own cross-variant rules |
| rationale | §13, Appendix B | 5 796 | why an earlier decision was reversed |
| procedure | Appendix A | 1 452 | the apply-phase checklist |

The native boundary alone is 12 781 bytes for one subject that no other section shares, and §8's cross-variant rules
are a subject the routing table already routes separately. **The numbers stay** — `Design.md §5` becomes
`DesignNative.md §5`, and a citation is repaired by naming a different document.

**Acceptance scenario.** After the change, four design documents are each under 48 000 bytes with every section
under 12 000 — `Design.md`, `DesignStack.md`, `DesignNative.md` and `DesignCrossVariant.md` — their rules are named,
the superseded decisions and the provenance are read from `Design.why.md`, the apply checklist is read from
`Design.recipes.md`, and every section number is what it is today.

**Owning guideline.** `guidelines/Design.md` §1 "Module layering" and §4 "Threading" are the rules this cut must
carry unharmed — a change that touches DI, state or a dispatcher reads exactly those. The layer rule is
`openspec/specs/documentation-ownership/spec.md`, "A guideline document carries one kind of text".

**Surface.** Measured with `tools/check-doc-routes.sh --sizes`:

```
Design.md today            74 205 B, 13 numbered sections, 2 past the 12 000 bound
unnumbered, unrouted       3 blocks (2 appendices, 1 preamble)
document-size conditions it carries              1
rule names it carries today                      0
unnamed rules it carries (`- **Never/Do not …**`) 38
"Adding to the stack" recipes    inside §2 — the routing row names them as the place to look
"Quick checklist (apply phase)"  Appendix A, unrouted
```

surface: 0 cases added (the guards land in `guideline-layers-mechanism`), 1 document becomes 4 normative documents
(3 new) plus companions, 0 named rules carried forward and 38 unnamed rules it must name, 0 fixtures realigned —
`tools/check-doc-routes.sh --sizes | grep '^Design'`

## What Changes

- **`Design.md`** keeps layering, state and the engineering principles: §1, §3, §4, §12.
- **`DesignStack.md`** takes §2 "Stack & UI patterns" and §9 "Data, persistence & search" — the stack and the data
  rules a screen is built against.
- **`DesignNative.md`** takes §5, split into `§5a` and `§5b` so the section itself is under the bound.
- **`DesignCrossVariant.md`** takes §6, §7, §8 (split into `§8a`/`§8b`), §10 and §11 — the seams between the two
  variants and the build/test rules that follow from them.
- **Companions**: `Design.why.md` (§13 "Superseded decisions" and Appendix B "Provenance" move here whole, plus each
  section's rationale), `Design.recipes.md` (Appendix A's apply checklist and "Adding to the stack" — the recipes the
  `AGENTS.md` row already sends a reader to), `DesignNative.why.md` (why the two database-open entries differ and what
  each cost).
- **Every rule is named** in all four, `**Rule: <name>**`, unique across `guidelines/*.md`.
- **`AGENTS.md`** retargets the rows that named `Design.md` for a section that moved — including the row that names
  §2's "Adding to the stack" recipes, which now names `Design.recipes.md` — and adds a row per new document.

No requirement changes. `skip_specs: true` — the layer rule and the guards landed in `guideline-layers-mechanism`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — no behavior changes. The layering, threading and native-boundary rules keep their text; they move and gain
names.

## Impact

| path | what changes |
|---|---|
| `guidelines/Design.md` | reduced to layering, state, principles; rules named |
| `guidelines/DesignStack.md` | new — §2, §9 |
| `guidelines/DesignNative.md` | new — §5, split |
| `guidelines/DesignCrossVariant.md` | new — §6, §7, §8 (split), §10, §11 |
| `guidelines/Design.why.md`, `Design.recipes.md`, `DesignNative.why.md` | new companions |
| `AGENTS.md` | rows retargeted, rows added |

No Kotlin, C++, JNI signature, Gradle file, manifest or Hilt module is touched. Scope: **documentation**.

**Rollback path.** Revert the commit; the new documents are new files and every line returns to `Design.md`.

## Not in this change

- **The other five documents** and the four open loop changes.
- **Moving the seams into the documents they point at.** §6, §7 and §9 reference `MapRendering.md` and `UI.md`; their
  rules stay here as the architecture's view of them, because a change to layering is what reads them.
- **A generated companion index**, as in the other layering changes.
