# Proposal — fix-unbounded-agent-context

## Why

**Observed (#197, verified in the tree today).** The start-of-session cost of this project is the entry
document a session is handed plus the guideline sections it is then sent to, and no check measures either.
`tools/sensors/sensors.json` declares ten rows and none of them reads a markdown document: `source-size`
measures `git ls-files -z '*.kt' '*.java'` (`tools/sensors/metrics/source-size.sh:31`) and `hotspot` scores
size × change count, so prose is outside every row's unit. `tools/check-doc-routes.sh` resolves the references
(`tools/check-doc-routes.sh:172`, "7. resolve the section references") and never measures the resolved section:
its header states what it checks — that a routed section exists, and that its heading number and text match.

**Consequence.** The budget erodes silently. Every new subsection is locally justified and the sum becomes the
reason a session compacts or misses a rule, and the failure mode is invisible in CI — which is exactly where a
bound belongs. The only size facts in the tree are prose measurements written by hand: `AGENTS.md`'s row for
`guidelines/FeatureList.md` records character counts and a date, so a section that doubles breaks nothing and
announces nothing.

**Root cause (one).** The checks' registry has no metric over the project's own documents — every row measures
a build output, the tracker, a source file or the loop's archive, and the two measurements that touch the
documentation (`check-doc-routes.sh`, and the route-cost prose in `AGENTS.md`) resolve and describe a reference
without weighing it. So "this section costs 25 206 bytes" is not expressible as a condition by any row that
exists, and the bound that `documentation-ownership` already states for `guidelines/Build.md` is enforced by
whoever happens to count.

**The red test.** Three cases in `tools/sensor-cases.sh`, run on HEAD with only the cases in the tree
(2026-10-29T10:18:08Z):

```
$ bash tools/sensor-selftest.sh
…
  the registry declares no routed-section-size row
  the registry declares no routed-section-size row
  the registry declares no entry-document-size row
FAIL sensors#routedSectionSizeRow
FAIL sensors#routedSectionSizeSkipsUnrouted
FAIL sensors#entryDocumentSizeRow
sensor-selftest: 3 of 70 cases failed
```

The suite's own summary line is the tally the gate derives its JUnit record from, so the red case is
`tests=70 failures=3 errors=0` (`evidence/red-on-head.xml`, log `evidence/red-suite-on-head.log`). The cases
assert the two rows' bound, comparison, fingerprint, class and declared scope; then each drives the real script
over a fixture tree whose routing table is a fixture `AGENTS.md`: a routed section past the bound is a violation
whose measurement is the section's own `wc -c` byte count (the fixture lines carry an em dash, so a character
count would fail), a routed section inside the bound is no condition, a section or a document the table does not
route is never a condition, the entry document is its own condition under its own bound and is not measured by
the section row, a recorded open condition inside the bound clears under the identity the issue holds, a table
inside the bound prints the row-level clear, and an entry document with no routing table is the third outcome.

**The recorded decision is settled (2026-10-27, `ITEM_DECISION`).** The unit is **bytes** — the ruler
`documentation-ownership`, "The routing cost is measured", already states — with **12 000 bytes per routed
section** and **32 000 bytes for the entry document**, and **unrouted sections excluded** because a session does
not read them before it works. The owner's amendment is that a document's size is *read* in lines, so the
measurement reports the line count beside the byte cost and states the conversion at the tree's own mean line
width (80 bytes/line: 626 432 bytes over 7 793 lines of `AGENTS.md` and the nine guidelines,
`evidence/measure-the-tree.txt`);
the bound stays in bytes so no verdict rests on an estimate. Nothing here re-opens that decision.

**The owning requirement and its guideline section.** Capability `documentation-ownership`, "The routing cost
is measured" — whose last sentence, "Bounding the other guidelines is issue #197's", is this item; guideline
`guidelines/Backlog.md` §9 "Checks", which states that a check is a declared row and already carries a paragraph
per row.

## What Changes

- **`tools/sensors/metrics/doc-context.sh`** (new) — the two rows' measurement. It reads the routing table out of
  the entry document's `## Documentation Map` region, derives the (document, section) pairs from it, takes the
  documents from `guidelines/*.md` at run time exactly as `tools/check-doc-routes.sh` does, and measures each
  named section as the byte count of its lines from the `## ` heading through the line before the next one. The
  registry's `scope` field selects the half the row bounds: `routed-section` (one condition per pair) or
  `entry-document` (one condition for the entry document, identified by its path). It emits the violation, a
  clear per recorded open condition now inside the bound (or `outside-scope` when the table no longer routes
  that pair, so the item closes instead of staying open forever), one row-level clear when it printed nothing at
  all — because the runner reads a row that printed nothing as a failed check — and one `error` finding when the
  entry document or its routing table cannot be read.
- **`tools/sensors/sensors.json`** — two rows: `routed-section-size` (`threshold 12000`, `scope
  routed-section`, `entry AGENTS.md`, fingerprint `["document","section"]`, `cron "0 11 * * *"`) and
  `entry-document-size` (`threshold 32000`, `scope entry-document`, fingerprint `["path"]`, `cron "30 11 * * *"`),
  both `comparison greater-than`, `consecutive 2`, `class improvement`, `category specs-and-process`,
  `lifecycle file+close`. The bounds live in the rows, never in the script, so retuning one is one edit here.
- **`.github/workflows/sensors.yml`** — both rows join the second row step's `--rows` list (the half that runs
  for any record, because they read the repository and not a build output); the two lists still cover the
  registry exactly once, which `workflow#aRedRecordRunsNoRecordRow` proves.
- **`guidelines/Backlog.md` §9** — one paragraph: what the two rows measure, the unit and the ruler, the line
  count beside it, the exclusion of what the table does not route, the identity, the two bounds as the owner's
  recorded decision, and the eight sections the bound convicts on this tree.
- **`documentation-ownership`** — the requirement "The routing cost is measured" is MODIFIED: the bound now
  covers a section of any guideline the table routes and the entry document, the clause that deferred the other
  guidelines to this item is gone, and three scenarios are added, each naming one case.
- **Cases and falsification** — `tools/sensor-cases.sh` gains `sensors#routedSectionSizeRow`,
  `sensors#routedSectionSizeSkipsUnrouted` and `sensors#entryDocumentSizeRow` (registered in the array and the
  dispatcher); `tools/sensor-mutations.sh` gains 8.32 and 8.33, each inverting one row's comparison.
- **Additive, no breaking change.** No compiled file, no Kotlin, no C++, no Gradle logic, no manifest, no
  resource. No owner decision is reversed: the change implements the one recorded on the item. Rollback path:
  revert this change's commit — the script, both rows, the workflow entry, the §9 paragraph and the spec delta go
  together, and the tracker keeps the items the rows filed.
- **Not a native/JNI change**: no submodule patch, no `:osmscout-client-java` override. No compiled file at all,
  so no Gradle build is owed (`guidelines/Build.md` §2, scope block); verification is the sensor tooling's offline
  suite, its mutation harness, and the structural checks named in `tasks.md`.

surface: 3 cases added, 0 rewritten, 0 fixtures realigned, 2 rows added, 1 metric script, 1 guideline paragraph, 1 workflow list entry, 2 mutations, 1 MODIFIED requirement — 8 files touched, against 1 shipped file (3 lines in `openspec/specs/documentation-ownership/spec.md`) asserting the bound before and 0 cases naming it — `git grep -n '12 000 bytes' HEAD -- openspec/specs guidelines | wc -l` (3 lines, one file) and `git grep -c 'routed-section-size\|entry-document-size\|doc-context' HEAD -- tools .github guidelines openspec/specs | wc -l` (0 files).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `documentation-ownership`: the requirement "The routing cost is measured" is MODIFIED — every routed section
  and the entry document are bounded, the deferral of the other guidelines to this item is removed, and three
  scenarios are added. No shipped scenario is dropped, including the two that carry no case in the shipped
  requirement ("Numbers for representative changes", "An unmeasured saving is not claimed"): a MODIFIED block
  keeps them, so the delta counts nine scenarios against seven cases, and the two unnamed ones are the shipped
  pair the change inherits untouched. The delta lives at `specs/documentation-ownership/spec.md`.

## Impact

Tooling (the checks' engine and its pipeline)
- `tools/sensors/metrics/doc-context.sh` — the new measurement (new file)
- `tools/sensors/sensors.json` — the `routed-section-size` and `entry-document-size` rows
- `.github/workflows/sensors.yml` — both rows in the second row step's list

Cases and their harness
- `tools/sensor-cases.sh` — the three cases and their registration
- `tools/sensor-mutations.sh` — mutations 8.32 and 8.33

Docs and specs
- `guidelines/Backlog.md` — §9, the two rows' paragraph
- `openspec/specs/documentation-ownership/spec.md` — the MODIFIED requirement, applied when this change archives

Structural checks (no compiled file, no Gradle)
- `bash tools/sensor-selftest.sh` — the sensor tooling's gate (70 cases)
- `bash tools/sensor-mutations.sh --only 8.32` and `--only 8.33` — each bound's falsification
- `bash tools/check-portability.sh` — the new script names no project value
- `bash tools/check-doc-routes.sh` — the edited guideline resolves every section reference
- `bash tools/gen-feature-list.sh --check-classification` — the delta's capability is classified
- `bash tools/check-openspec-hygiene.sh` — the change artifacts are complete
