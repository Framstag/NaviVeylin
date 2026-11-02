# Design — the map's globs name their document

## Root cause

`tools/check-rule-routes.sh --diff` derives a reading set as the **union** of every route whose glob matches a
touched path (`tools/check-rule-routes.sh:143-153`). The map (`tools/doc-routes.json`) carried six routes whose glob
was `guidelines/**`, and six whose glob was one document's file name. Under a union, a corpus-wide glob makes those
six routes unconditional for **every** guideline path — measured 270 entries naming 10 of 25 normative documents for
one `guidelines/Logging.md` edit — while an exact-file glob matches no companion layer, so `guidelines/Logging.recipes.md`
derived nine *other* documents and not its own (`evidence/baseline-derived-sets.txt`).

One mechanism explains both halves: the glob is not the name of what the route owns. The map's engine side already
does what the guideline side does not — `app/src/main/java/**` names the subtree it reaches.

## The single fix

Rewrite the twelve `guidelines/` globs so each names what its route carries:

- `<Doc>.*` where the route owns one document — the document plus the two companion layers the layers mechanism
  gave it (`guidelines/Logging.*`, `guidelines/BuildGate.*`, `guidelines/BuildEvidence.*`, `guidelines/BuildTests.*`,
  `guidelines/BuildNative.*`, `guidelines/Build.*`, `guidelines/MapRendering.*`, `guidelines/FeatureList.*`,
  `guidelines/Metrics.*`, `guidelines/Regulatory.*`). `.*` rather than `.md` because `check-doc-routes.sh` already
  treats `<Doc>.why.md` and `<Doc>.recipes.md` as that document's companions; a route that names the document must
  reach them or a procedure edit derives nothing.
- the concern family where the route owns one — `guidelines/Design*` (Design, DesignStack, DesignNative,
  DesignCrossVariant), `guidelines/Backlog.*` (Backlog, BacklogTooling). `guidelines/UI*.md` is already this shape and
  is unchanged.

No entry moves between routes in the twelve rewritten ones and no route is removed: 27 globs before the correction
round. Under the union a derived set is what the matching routes carry, so the change is a rewrite of what a
guideline path matches, not of what any route owns. The shell `case` semantics the check documents — `*` crosses
`/`, so `guidelines/Logging.*` is the three files of that concern — are what make the globs express this.

## The correction round — renaming a glob does not re-home its entries

The first review round measured the consequence this section did not: the twelve rewritten routes kept their
entries, and six documents (`MapCar`, `MapFollow`, `MapFrames`, `MapRoute`, `MapStyling`, `BacklogTooling`) had no
glob left that matched them, because their entries live in a **code** route (`app/src/main/java/**`) and in the
`guidelines/Backlog.*` route respectively. A `guidelines/MapCar.md` edit went from deriving 258 entries naming nine
other documents to deriving nothing (`evidence/checks-on-HEAD.log`, and `evidence/baseline-derived-sets.txt`'s
six-document block).

The fix has two halves, and the second is the one that matters for the next document:

1. **Six routes named for the six documents** — `guidelines/MapCar.*`, `guidelines/MapFollow.*`,
   `guidelines/MapFrames.*`, `guidelines/MapRoute.*`, `guidelines/MapStyling.*`, `guidelines/BacklogTooling.*`,
   each carrying every entry anywhere in the map that names its document, and `guidelines/Backlog.*` keeping only
   the entries naming `Backlog.md`. 19 `guidelines/` globs, 33 routes. Nothing is removed from a code route, so no
   code path's derived set shrinks.
2. **Two invariants in `tools/check-rule-routes.sh`**, so the class of defect is refused rather than reviewed: a
   normative document under `guidelines/` that no glob matches, and a route whose glob sits under `guidelines/`
   carrying a document its glob does not match.

Why the second half had to be built rather than argued: the existing reached-ness invariant reads
`.routes[].rules[]` with no regard to which glob matched, so `MapCar.md`'s sixteen rules were all "reached" — under
`app/src/main/java/**` — while no glob matched a `MapCar.md` edit. The first round's Risks section claimed the check
would refuse a new document until a route named it; it would not, and the check printed "every glob matches the tree
and all 576 named rule(s) are reached" over the orphaned map. That claim is replaced by the two invariants, which
are refusals with cases and mutations behind them.

**The duplication this buys, stated.** A document's entries exist twice when a code route carries them too: 100
entries across the six new routes (`jq -r '[.routes[]|select(.glob|test("^guidelines/(MapCar|MapFollow|MapFrames|MapRoute|MapStyling|BacklogTooling)\\."))]|map((.sections|length)+(.rules|length))|add'`).
The map has no include mechanism, and the alternative — multiple globs per route — is a schema change to the file
every reader of the map parses (`proposal.md`, "Not in this change"). The invariants check the copies' *documents*,
not their contents, so a later edit that adds an entry to one copy only is a review matter, not a silent one.

## Blast radius

| who reads the map | what it sees now |
|---|---|
| `tools/check-rule-routes.sh --diff` (the reviewer's reading set, `tools/loop/prompts/reviewer.md:70`) | the edited document's route instead of the corpus |
| `tools/check-rule-routes.sh` (the four invariants) | two more refusals: a document no glob matches, and a `guidelines/` route carrying a document its glob does not match; the first two are unchanged |
| `tools/check-doc-routes.sh` (AGENTS.md's rows) | nothing — it reads the entry document, never the map |
| `tools/loop/loop.conf` `LOOP_GATE_SUITES` | the same three rows fire for `tools/doc-routes.json`: the check, its self-test, and the mutation set's `--list` |

The map is data and no code branches on a glob's shape, so the change cannot alter anything but the derived set.

## Alternatives the evidence eliminated

- **Split `tools/**` per directory as the item's fix candidate proposed.** Eliminated by measurement, not by
  preference: `tools/loop/state.sh` derives 4 entries, `tools/issues/lib.sh` 1, `tools/sensors/run.sh` 1
  (`evidence/baseline-derived-sets.txt`), and `tools/loop/**` and `tools/sensors/**` are routes of their own
  already. Narrowing `tools/**` would remove the sensors-check section from the loop tooling's set — an under-read —
  to save at most one entry.
- **Split `guidelines/Design*` and `guidelines/UI*.md` per document.** Those routes exist because a Design or UI edit
  reads its siblings (the parity rules, the shared surface rules); splitting them would under-read, and 107 and 191
  entries are the honest size of those concerns.
- **Make the check refuse a corpus-wide glob as a third invariant.** Rejected as *breadth*: a glob's width is not
  what makes it wrong — `app/src/main/java/**` is broad on purpose — and a project-shaped judgement about how wide is
  wide does not belong in an engine that names no project value. **What was taken instead**, after the first review
  round, is the same invariant read on the map's own data rather than on a glob's shape: a normative document that
  no glob matches, and a `guidelines/` route carrying a document its glob does not match. Both are refusals a second
  project inherits for free, and neither mentions a project fact.
- **Derive per document in the check (`--path`), so the case need not commit.** A new mode is new surface on a tool
  the reviewer already calls with `--diff`; the case instead drives the interface the review actually uses, over a
  throwaway git tree built from this tree's map and corpus.

## Rollback

Revert the twelve rewritten globs and the six added routes in `tools/doc-routes.json`; the map-reading case goes red
again (measured: `evidence/red-on-HEAD.log`), both new invariants fire on the old map
(`evidence/checks-on-HEAD.log`), and the mutation records restore the coarse glob and each refusal in place, so the
revert is a property the harness already exercises. No data migration, no consumer change.

## Risks

- **A new document arrives under a name no `.*` glob matches** — e.g. a `BuildX.md` concern. It is refused: the
  coverage invariant reports every normative document no glob matches, so the tree cannot carry a document whose
  own edits derive nothing. The failure mode is a red check, not a silent under-read — the guarantee the first round
  only claimed.
- **The case reads this tree's own map**, unlike every other case in that file. It copies the map and corpus into a
  temporary git tree, so it writes nothing into the repository; its cost is one `cp -R guidelines/` (measured:
  776 kB, 56 files) and five commits.
