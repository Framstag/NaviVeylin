# `Build.md` is cut into layers, and into one normative document per subject

## Why

`Build.md` costs 166 383 bytes and is the largest file in the tree. The routing table prices its sections — a
reader is sent to §4f, 7 016 bytes — while the file they open is 24× that. Two of its sections are already past the
12 000-byte section bound (§7 at 12 052 B, and §9 is 9 143 B with the transcripts inside it), and the document
itself is 3.5× the 48 000-byte document bound the mechanism landed.

Measured, what the 166 383 bytes actually are:

| cluster | sections | bytes | what it is |
|---|---|---|---|
| the gate and its rules | §2, §2a–§2e | 26 520 | normative — the iteration protocol, the liveness guard, the JVM, the action runtimes |
| verdicts and evidence | §4, §4a–§4f | 28 275 | mixed — the rule about a verdict, wrapped around tallies and logs |
| test constraints | §6, §6a–§6h | 23 659 | normative — the constraints a test must obey |
| device recipes | §10, §10a–§10k | 37 656 | procedure — AVD boot, logcat, screenshots, route numbers |
| release, licence, SBOM | §5, §8, §9 | 14 344 | normative + policy detail |
| native build | §1, §12 | 14 067 | normative + vcpkg commands and transcripts |
| commands, coverage, measurement | §3, §7, §11 | 20 070 | a command table, a coverage rule, a measurement recipe |

Four clusters are normative and none of them belongs to the same subject as another: the gate is not the test suite,
the test suite is not the native build, and the device recipes are neither. So this document does not merely need a
rationale companion and a procedure companion — **its normative body is ~120 000 bytes and must become more than one
document**, or the bound it is measured against cannot be met at all.

**Acceptance scenario.** After the change, every document this change names is under 48 000 bytes with every section
under 12 000, each carries its rules named, its rationale in a `<Doc>.why.md` companion, its procedure in a
`<Doc>.recipes.md` companion, and every section number stays what it is today — a citation that said
`Build.md §6a` now says `BuildTests.md §6a`, and the number is unchanged.

**Owning guideline.** `guidelines/Build.md` itself: §2 "Common behavior" is where a reader starts, and
§1 "Skills" is where this document says which skills wrap which commands. The layer rule it is cut under is
`openspec/specs/documentation-ownership/spec.md` "A guideline document carries one kind of text".

**Surface.** Measured with `tools/check-doc-routes.sh --sizes`:

```
Build.md today            166 383 B, 41 sections, 8 of them lettered clusters
sections past 12 000 B                           2 (§7 12 052, §9 9 143 as a routed read once transcripts stay)
document-size conditions it carries              1
rule names it carries today                      0
unnamed rules it carries (`- **Never …**`)      3
```

surface: 0 cases added (the guards land in `guideline-layers-mechanism`), 1 document cut into normative documents
plus companions, 0 named rules carried forward and 3 unnamed rules it must name, 0 fixtures realigned —
`tools/check-doc-routes.sh --sizes | grep '^Build'`

## What Changes

- **Normative documents, each carrying the numbers it takes.** `Build.md` keeps §1, §3, §5, §8 and §9; `BuildGate.md`
  takes §2 and §2a–§2e; `BuildEvidence.md` takes §4, §4a–§4f, §7 and §11; `BuildTests.md` takes §6 and §6a–§6h;
  `BuildNative.md` takes §10, §10f, §10j and §12. **The numbers do not change** — only the document that carries
  them — so a citation is repaired by naming a different file and never by looking up a new section.
- **Companions.** `Build.recipes.md` (the command table's transcripts, vcpkg commands, licence-policy detail),
  `BuildEvidence.why.md` (the gate-timing history, the JVM criteria rationale, the falsification case histories),
  `BuildTests.why.md` (the trap each constraint was written for), `BuildNative.recipes.md` (§10a–§10k's device
  recipes and §12's vcpkg transcripts), `BuildGate.why.md` (why the iteration protocol exists, what it cost).
- **§9 and §7 fall out of the section bound** by the rationale and transcripts leaving them, not by a lettered
  split.
- **Every rule is named** in the five normative documents, `**Rule: <name>**`, unique across `guidelines/*.md`.
- **`AGENTS.md`** retargets the rows that name `Build.md` for the sections that moved, adds a row per new document,
  and keeps `Agent iteration loop (measure first)` (3 351 B) as one sentence plus its route, moved into
  `BuildGate.md` §2a — which is the section the block was always a copy of.

No requirement changes. `skip_specs: true`: the layer rule, the guards and the bounds this cut is judged by landed
in `guideline-layers-mechanism`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — no behavior changes; text moves, and a rule keeps its meaning while gaining a name.

## Impact

| path | what changes |
|---|---|
| `guidelines/Build.md` | reduced to its own subject; rules named |
| `guidelines/BuildGate.md` | new — §2, §2a–§2e |
| `guidelines/BuildEvidence.md` | new — §4, §4a–§4f, §7, §11 |
| `guidelines/BuildTests.md` | new — §6, §6a–§6h |
| `guidelines/BuildNative.md` | new — §10, §10f, §10j, §12 |
| `guidelines/Build.recipes.md`, `BuildGate.why.md`, `BuildEvidence.why.md`, `BuildTests.why.md`, `BuildNative.recipes.md` | new companions |
| `AGENTS.md` | rows retargeted, rows added, `Agent iteration loop` reduced and moved |

No code, no Gradle file, no check and no sensor row is touched. Scope: **documentation** — neither phone nor
Android Auto.

**Rollback path.** Revert the commit; the new files are removed and the text returns to `Build.md`.

## Not in this change

- **The other five documents** and the four open loop changes, which cite this document three times between them.
- **Merging the five normative documents back into fewer.** If the bound permits two rather than five, the tasks
  measure it and the design records the tolerance; the numbers are what force the count, not a taste for small
  files.
- **A generated companion index** — worth its own change once three documents carry one.
