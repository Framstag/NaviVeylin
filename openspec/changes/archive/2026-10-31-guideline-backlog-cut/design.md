# Design — `guideline-backlog-cut`

## The problem, measured

`guidelines/Backlog.md` is **35 887 B** against a **32 000 B** bound (`guideline-layers-mechanism` D4, task
7.7) — the last document over it. Its 14 sections fall into three groups:

| group | sections | bytes |
|---|---|---|
| the concept | §1–§8 (including the `§7a`–`§7e` index) | 23 208 |
| the decisions and the honesty rules | §12, §13, §14 | 2 549 |
| the tooling over the tracker | §9, §10, §11 | 10 130 |

Moving **§9 alone** would be enough for the bound: 35 887 − 5 823 = 30 064 B. That is the cheap cut and D1
rejects it — see below.

## D1 — the cut is the document's own subject line, not the arithmetic minimum

**Chosen: move all three of §9, §10 and §11.**

The cheap cut leaves a document that states a concept and also owns the check registry — the largest single
section in the file — while its two neighbours in the same subject (portability, where the agent runs) stay
behind. §11 "Where an agent runs" is about the *process*, not the backlog; §10 "Portability" is about the
*tooling*, not the backlog. A reader routed to "the backlog itself" would then get one of the tooling's three
sections and have to find the other two, which is the drift `documentation-ownership` exists to prevent.

**Rejected A — move §9 only (30 064 B).** Fits the bound and splits one subject across two documents.

**Rejected B — move §9 and §10, keep §11 (25 757 + 1 966 = 27 723 B).** "Where an agent runs" is the least
conceptual of the three: it is a statement about the loop's processes, and it is the section the loop's own
change (`agent-issue-loop`) treats as an operational fact beside its budget.

**Rejected C — a third document for §12–§14.** 2 549 B, and §14 is the maintenance rule *for this document*;
a maintenance section that lives in another file is the defect the companion rule forbids.

The name: `BacklogTooling.md`. It is the tooling over the backlog — the checks, its portability, and where it
runs — and it keeps the `Backlog` prefix the existing companions already carry, so a reader who knows
`Backlog.recipes.md` finds the sibling.

## D2 — the numbers do not move

§9 stays §9, §10 stays §10, §11 stays §11. A citation is repaired by naming the new file, never by
renumbering — the whole set's rule, and the reason the routing table gains a row instead of the sections
gaining prefixes.

## D3 — the companion split is by anchor, and it is forced

`guidelines/Backlog.recipes.md` (72 633 B) carries `## §9 The outcomes a run of a check writes, and the
declared rows` and `## §10 Portability — the engine and the project`. A companion anchors to a section of
**its base document** (`documentation-ownership`, "A companion anchors to a section that exists"), so the
moment §9 and §10 leave `Backlog.md` those two anchors dangle and `tools/check-doc-routes.sh` fails. Both
blocks move to `guidelines/BacklogTooling.recipes.md` under the same anchors; `Backlog.recipes.md` keeps
`## §2`, `## §7a`–`## §7e` and `## §8`.

`guidelines/Backlog.why.md` carries one anchor for a moving section — `## §9 Checks — why` — and splits the
same way: that block becomes `guidelines/BacklogTooling.why.md`, while `## §5`, `## §6`, `## §12` and `## §13`
stay. This design first asserted that `Backlog.why.md` carried no §9 anchor, from a grep that had read only
`Backlog.recipes.md`; `check-doc-routes.sh` reported `the anchor §9 names no section of its base document` and
the claim was corrected. A companion exists when it holds material, never because a base document gained a
sibling — and no companion is left with zero anchors.

## D4 — `Backlog.md` keeps §14, and the new document points at it

§14 "Keeping this document honest" is the rule that keeps *both* honest: status is a section, every claim has
a home, a reversal is recorded, the routing table must name the document, a section reference resolves. Two
copies would be two normative homes. `BacklogTooling.md`'s preamble names `Backlog.md` §14 instead.

## D5 — the internal bare references become named ones

Five places inside `Backlog.md` name a moved section by bare number:

| where | now | becomes |
|---|---|---|
| §3, the `ownership` row of the tracker table | `(§9)` | `(`BacklogTooling.md` §9)` |
| §3, "one profile" | `(§10)` | `(`BacklogTooling.md` §10)` |
| §5, the security note | `(§11)` | `(`BacklogTooling.md` §11)` |
| §1, change 3's row | "§7 and §11 of this document" | "§7 of this document and §11 of `BacklogTooling.md`" |
| §9's two references to `Backlog.recipes.md` §9 | `Backlog.recipes.md` §9 | `BacklogTooling.recipes.md` §9 |

A bare number is left alone only where the document still carries that section. These five are not, so they
are named — the same repair `guideline-ui-cut` made for a bare `(§1)` in the section that moved there.

## D6 — a stale path in a script header is retargeted too

Eight prose references outside the guidelines name a moved section: two in `tools/issues/lib.sh`, one each in
`tools/issues/check-metadata.sh`, `tools/sensor-cases.sh`, `tools/check-portability.sh`, and the two skills
`.pi/skills/loop-spend` (§9) and `.pi/skills/portability-check` (§10, twice). `tools/check-doc-routes.sh`
reads only `AGENTS.md` and `guidelines/*.md`, so nothing *fails* if they are left — but a file that says "the
rule is `guidelines/Backlog.md` §9" after §9 has moved is a wrong pointer in the place a maintainer looks
first. They are retargeted by document name, and no statement in them is changed.

## D7 — what the cut costs, and the conditions

`Backlog.md` 25 757 B of sections plus a preamble that grows by pointing at the sibling; `BacklogTooling.md`
10 130 B plus its own preamble. Both far under 32 000; the largest section in either is §9 at 5 823 B, under
the 12 000 B section bound. The corpus grows by the second preamble, as it did in the three cuts before this
one — the price is stated in the change's evidence rather than hidden in a total.

The `document-size` condition goes **1 → 0**: this is the last document over the bound, so the sensor's next
run reports no violation at all.

## Risks / Trade-offs

- **The tooling document is small (10 130 B).** → Deliberate: it is one subject, and a small document that a
  reader can finish beats a large one whose third part is another subject. Room to grow is the point of a
  bound.
- **`Backlog.recipes.md` at 72 633 B stays large.** → It is a companion, not a normative document; no bound
  applies to it, and splitting it by section would fragment the transcripts it holds.
- **Moving §10 breaks the reference `tools/check-portability.sh` writes in its own header.** → Retargeted in
  the same change (D6), and the change's evidence records the line.

## Risks that were checked and are not risks

- **The rule count.** Every moved rule keeps its name and its section number; only the document name in
  `tools/doc-routes.json` changes. The count must stay at 575 — verified, not assumed.
- **The routing table.** `documentation-ownership` fails an unrouted document, so the new document needs a row
  in the same change: the backlog row splits into the concept and the tooling.
- **The three companions.** Not a risk once measured: `recipes` carries §9 and §10, `why` carries §9, and the
  check names any anchor whose section did not follow it.
