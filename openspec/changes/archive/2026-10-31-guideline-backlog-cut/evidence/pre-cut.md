# Pre-cut state — guideline-backlog-cut

Measured 2026-10-31 in the parent, before any edit (`/tmp/backlog-sections.py` over `guidelines/Backlog.md`).

Baseline: **35 887 B**, 393 lines, 19 numbered sections (`§1`–`§8` including the `§7a`–`§7e` index, then
`§9`–`§14`).

| section | bytes | lines | group |
|---|---|---|---|
| preamble | 415 | 1–8 | — |
| §1 Status, and what this document owns | 7 050 | 9–53 | concept |
| §2 The participants and the flow | 310 | 54–57 | concept |
| §3 What the tracker holds | 2 066 | 58–83 | concept |
| §4 The invariants | 5 059 | 84–110 | concept |
| §5 Who may write what | 5 070 | 111–170 | concept |
| §6 How an item becomes work | 2 783 | 171–210 | concept |
| §7 The loop | 396 | 211–216 | concept |
| §7a Running one item | 165 | 217–220 | concept |
| §7b What an item costs | 177 | 221–225 | concept |
| §7c The gate and its suite | 154 | 226–230 | concept |
| §7d Refusals, records and the landing | 195 | 231–235 | concept |
| §7e Leftovers, the report and the queue | 140 | 236–239 | concept |
| §8 The human lane | 188 | 240–243 | concept |
| §9 Checks | 5 823 | 244–311 | **tooling** |
| §10 Portability | 2 341 | 312–343 | **tooling** |
| §11 Where an agent runs | 1 966 | 344–371 | **tooling** |
| §12 The decisions, and why | 120 | 372–374 | decisions |
| §13 Deferred, and why | 104 | 375–377 | decisions |
| §14 Keeping this document honest | 1 365 | 378–393 | decisions |

Concept group (19 sections minus the three tooling and three decision sections): **23 208 B**.
Tooling group: **10 130 B**. Decisions group: **2 549 B**. 23 208 + 10 130 + 2 549 = 35 887 B.

## The cut

`BacklogTooling.md` (new) takes §9, §10 and §11 — 10 130 B plus its own preamble.
`Backlog.md` keeps the concept, §12–§14, and its preamble.

Moving §9 alone would have satisfied the bound (35 887 − 5 823 = 30 064 B). Rejected by `design.md` D1: §10
and §11 are about the tooling, not the backlog, and a document that owns one of the tooling's three sections
while its neighbours live elsewhere is the drift the ownership rule exists to prevent.

## Companions, measured before the cut

| companion | bytes | anchors |
|---|---|---|
| `Backlog.recipes.md` | 72 633 | `## §2`, `## §7a`, `## §7b`, `## §7c`, `## §7d`, `## §7e`, `## §8`, **`## §9`**, **`## §10`** |
| `Backlog.why.md` | 10 818 | `## §12`, `## §13`, `## §5`, `## §6`, **`## §9 Checks — why`** |

A companion anchors to a section its **base document** carries, so both companions split: the `§9`/`§10` blocks
to `BacklogTooling.recipes.md`, the `§9` block to `BacklogTooling.why.md`. `design.md` first asserted that
`Backlog.why.md` carried no §9 anchor — a grep that had read only the recipes companion — and
`check-doc-routes.sh` caught it. See `notes.md`.
