# `Backlog.md` is cut at the line between the concept and the tooling over it

## Why

The document bound is **32 000 bytes** (retuned 2026-10-31; `guideline-layers-mechanism` D4 and its task
7.7). `guidelines/Backlog.md` measures **35 887 B** and owes a cut — it is the **last** row over the bound,
so this change takes the `document-size` condition from 1 to 0.

Measured with `tools/check-doc-routes.sh --sizes`:

| cluster | sections | bytes |
|---|---|---|
| the concept — status, participants, what the tracker holds, the invariants, who may write what, how an item becomes work, the loop index and its sub-sections `§7a`–`§7e` | §1–§8 | **23 208** |
| the decisions and the honesty rules | §12, §13, §14 | **2 549** |
| the **tooling over the tracker** — the checks, using the tooling in another project, where an agent runs | §9, §10, §11 | **10 130** |

Two subjects, not three: the backlog is a **concept** (what an item is, what the tracker holds, who may
write what, how it becomes work), and the **tooling** is the machinery that keeps it honest and portable —
the check registry, the leak check's boundary, and where the agent process runs. The document's own §14
already says "the concept is not the implementation"; this cut is that sentence applied to a file.

**Acceptance scenario.** After the cut, `Backlog.md` and `BacklogTooling.md` are both under 32 000 B with
every section under 12 000, no section number moves, the named-rule count is unchanged, every citation in
the measured referring set resolves, and the `document-size` conditions drop from **1 to 0**.

**Owning guideline.** `guidelines/Backlog.md` §1 "Status, and what this document owns" states what this
document owns and what it deliberately does not; the bound is in
`openspec/specs/documentation-ownership/spec.md`, "The routing cost is measured".

**Surface.** `wc -c guidelines/Backlog.md` → `35887`; the three clusters above; `document-size` conditions
before the cut: **1** (`Backlog.md`; `UI.md` and `MapRendering.md` were cut by the changes before this one).

surface: 0 cases added, 1 document becomes 2 normative documents (1 new document: `BacklogTooling.md`), 0 rules renamed, 1 routing row + 1 new row, 0 fixtures — `wc -c guidelines/Backlog.md && tools/check-doc-routes.sh --sizes | grep '^Backlog'`

## What Changes

- **`Backlog.md`** keeps §1–§8 and §12–§14 — 25 757 B plus a preamble that points at the sibling.
- **`BacklogTooling.md`** (new) takes §9 "Checks", §10 "Portability" and §11 "Where an agent runs", carrying
  their own numbers — 10 130 B plus its own preamble.
- **Companions**: `Backlog.recipes.md` (72 633 B) carries `## §9` and `## §10` anchors, which name sections
  of *its base document*; both blocks move to `BacklogTooling.recipes.md`. `Backlog.recipes.md` keeps §2,
  §7a–§7e and §8. `Backlog.why.md` carries no §9/§10/§11 anchor and does not split.
- **`Backlog.md`'s internal references**: three bare numbers inside §3 and §5 (`§9`, `§10`, §11), one
  sentence in §1's change table naming "§7 and §11 of this document", and two references to
  `Backlog.recipes.md` §9 — each becomes a named reference, because the bare form now names a section the
  document does not carry.
- **`AGENTS.md`**: the backlog row splits into the concept and the tooling.
- **`tools/doc-routes.json`**: the `§9`/`§10` section and rule entries move to the new document.
- **Citations** naming a moved section are retargeted by document name — the referring set is measured, not
  guessed: `AGENTS.md`, `.pi/skills/loop-spend` (§9), `.pi/skills/portability-check` (§10 ×2),
  `tools/issues/lib.sh` (§9 ×2), `tools/issues/check-metadata.sh` (§9), `tools/sensor-cases.sh` (§9),
  `tools/check-portability.sh` (§10).

No requirement changes and no rule changes: `skip_specs: true`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — text moves; no behavior changes.

## Impact

| path | what changes |
|---|---|
| `guidelines/Backlog.md` | keeps the concept, the decisions and the honesty rules |
| `guidelines/BacklogTooling.md` | new — §9, §10, §11 |
| `guidelines/Backlog.recipes.md` | loses its `## §9` and `## §10` blocks |
| `guidelines/BacklogTooling.recipes.md` | new — those two blocks |
| `AGENTS.md`, `tools/doc-routes.json` | the backlog row splits; globs and rule entries follow |
| `.pi/skills/{loop-spend,portability-check}/SKILL.md`, `tools/**/*.sh` | name-only citation retargets |

No C++, Kotlin, CMake, workflow or fixture is touched. Scope: **documentation**.

**Rollback path.** Revert the commit; `BacklogTooling.md` and its companion are new files and every line
returns to `Backlog.md`.

## Not in this change

- **The three cuts before it**: `guideline-uiphone-cut`, `guideline-maprendering-cut` and `guideline-ui-cut`
  landed.
- **Renumbering.** A section that changes document keeps its number; a citation is repaired by naming the
  new file.
- **The two stale `"UI.md §7"` entries in `tools/doc-routes.json`** (lines 14 and 373), which
  `guideline-uiphone-cut` left behind. They are recorded in that change's sibling's notes and belong to it.
