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

No entry moves between routes and no route is added or removed: 27 globs before and after, 576 named rules reached
before and after (`evidence/derived-sets-after.txt`). The shell `case` semantics the check documents — `*` crosses
`/`, so `guidelines/Logging.*` is the three files of that concern — are what make the globs express this.

## Blast radius

| who reads the map | what it sees now |
|---|---|
| `tools/check-rule-routes.sh --diff` (the reviewer's reading set, `tools/loop/prompts/reviewer.md:70`) | the edited document's route instead of the corpus |
| `tools/check-rule-routes.sh` (the two invariants) | unchanged: every glob matches a tracked path, every named rule is reached |
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
- **Make the check refuse a corpus-wide glob as a third invariant.** A glob's breadth is not what makes it wrong —
  `app/src/main/java/**` is broad on purpose. The defect is that a *guideline* glob was broader than the concern it
  routes, which is a property of one map, asserted here by a case that derives from this tree's own map. Adding a
  third invariant would put a project-shaped judgement into an engine that "names no project value".
- **Derive per document in the check (`--path`), so the case need not commit.** A new mode is new surface on a tool
  the reviewer already calls with `--diff`; the case instead drives the interface the review actually uses, over a
  throwaway git tree built from this tree's map and corpus.

## Rollback

Revert the twelve globs in `tools/doc-routes.json`; `routes#aGuidelineEditDerivesItsOwnDocument` goes red again
(measured: `evidence/red-on-HEAD.log`), and the mutation record `route-names-its-document` restores the coarse glob
in place, so the revert is a property the harness already exercises. No data migration, no consumer change.

## Risks

- **A new document arrives under a name no `.*` glob matches** — e.g. a `BuildX.md` concern. It derives nothing for
  its own edits, which is the pre-existing behaviour for every companion and would be visible at once: every
  `**Rule:` it states must still be reached by a glob, so `tools/check-rule-routes.sh` refuses the addition until a
  route names the document. The failure mode is a red check, not a silent over-read.
- **The case reads this tree's own map**, unlike every other case in that file. It copies the map and corpus into a
  temporary git tree, so it writes nothing into the repository; its cost is one `cp -R guidelines/` (about 670 kB)
  and three commits.
