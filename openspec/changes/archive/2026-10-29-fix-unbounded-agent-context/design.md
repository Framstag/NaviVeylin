# Design — fix-unbounded-agent-context

## The root cause

The checks' registry measures a build output, the tracker, a source file and the loop's archive, and none of its
rows reads a markdown document: `source-size` counts `git ls-files -z '*.kt' '*.java'`
(`tools/sensors/metrics/source-size.sh:31`) and `hotspot` multiplies a file's size by how often it changed. The
two tools that do touch the documentation resolve a reference — `tools/check-doc-routes.sh:172` resolves every
`<File>.md` §N against the heading it names — and neither weighs it, so the 12 000-byte bound the requirement
already states for `guidelines/Build.md` is enforced by whoever happens to count. One mechanism, and it is the
absence of a measurement over the documents, not a fault in an existing row.

## The single fix

Two declared rows over one measurement:

- `tools/sensors/metrics/doc-context.sh` reads the routing table out of the entry document's
  `## Documentation Map` region and derives the `(document, section)` pairs from it, then measures each named
  section as the byte count of its lines from the `## ` heading through the line before the next one — the ruler
  the requirement states. The documents come from `guidelines/*.md` at run time, exactly as the route check takes
  them, so a document added later is covered without editing the script, and a document no row of the table names
  is simply not in the pair set. The heading text a table cell quotes (`MapRendering.md` §14 "Android Auto
  renderer") disambiguates the one document that numbers two headings alike; a pair whose number the document
  does not carry gets no measurement rather than a guessed one, because reporting a dangling route is the route
  check's job.
- `sensors.json` declares the rows: `routed-section-size` (`threshold 12000`, fingerprint
  `["document","section"]`, `scope routed-section`) and `entry-document-size` (`threshold 32000`, fingerprint
  `["path"]`, `scope entry-document`), both `comparison greater-than`, `consecutive 2`, `class improvement`,
  `category specs-and-process`, `lifecycle file+close`, at the minutes 0 and 30 the pipeline's tick covers.
- `.github/workflows/sensors.yml` runs both in the second row step, the half that runs for a record a build did
  not complete successfully, because they read the repository and not a build output. The two steps' `--rows`
  lists still cover the registry exactly once.
- The script emits four outcomes: a violation per routed section (or for the entry document) past the bound, a
  clear per recorded open condition now inside it — the identity read back from the issue's own record, so a
  closure names the identity the issue carries — with `outside-scope` when the table no longer routes that pair,
  one row-level clear when it printed nothing at all (`{"document":"none","section":"none"}` /
  `{"path":"none"}`, as `source-size` does, because the runner reads a row that printed nothing as a failed
  check), and one `error` finding when the entry document or its routing table cannot be read.

Scope, unit and bound are the owner's recorded decision of 2026-10-27: bytes; 12 000 per routed section; 32 000 for
the entry document; unrouted sections excluded because a session does not read them before it works. The
amendment on the same item — that normal size talk for these documents is lines — is honoured by reporting the
line count beside the byte cost and by stating the conversion at this tree's mean line width (80 bytes/line:
626 432 bytes over 7 793 lines of the entry document and the nine guidelines, `evidence/measure-the-tree.txt`),
while the verdict stays on the measured bytes so no bound rests on an estimate. Each bound is its own row's
`threshold`, so retuning one is one edit in the registry.

## Blast radius

- One new script under `tools/sensors/metrics/`; two rows; one list entry and one comment line in the workflow;
  one paragraph in `guidelines/Backlog.md` §9; three cases and two mutations; and one MODIFIED requirement with
  three added scenarios in `documentation-ownership`. Eight files.
- Nothing compiled: no Kotlin, Java, C++, Gradle, manifest or resource. No `:app` behaviour changes.
- **The self-referential risk, named:** §9 is 12 894 bytes before this change's paragraph, so it is already one of
  the eight conditions the bound convicts, and the paragraph that documents the check grows it further. The
  alternative — documenting the rows somewhere other than the section that owns the checks' doctrine — would
  split one doctrine across two homes to keep a number under a bound, which is the erosion this row exists to
  make visible. The split §9 needs (lettered sub-sections, the route row retargeted, every `Backlog.md` §9
  citation kept resolving) is a change of its own, and the row will file it.
- The first run of the two rows files eight section conditions on today's tree and nothing for the entry
  document, each measured in `evidence/measure-the-tree.txt`: `UI.md` §1 (25 206 bytes), `MapRendering.md` §1
  (22 644), `UI.md` §8 (19 681), `UI.md`'s "Phone map re-center button" section (13 102), `Backlog.md` §9 (12 894
  before this change), `Design.md` §5 (12 781), `Design.md` §8 (12 058), and `Build.md`'s "Code coverage" section
  (12 052). Filing is not authorising: they carry no `agent/eligible`, and each closes when its own section
  shrinks.
- `check-portability.sh` is the one structural risk: the new script is an engine and must name no project value.
  It names no module, repository, label or allowlist; the document directory and the entry document's name are
  the documentation convention and the registry's own `entry` field. Verified: `check-portability: OK`.
- No threading model: a shell measurement the runner executes to completion, with no async work, no lifecycle and
  nothing to dispatch (`guidelines/Design.md` §4 does not apply to a tooling script).

## Rollback

Revert the commit: the script, both rows, the workflow entry, the §9 paragraph and the spec delta go together. The
tracker keeps the items the rows filed; they stay valid findings and simply stop being measured, exactly as
before the change.

## The alternatives the evidence eliminated

- **Reuse `hotspot`.** Impossible: its measurement is `size × change count` per whole path, bounded above the
  largest file it watches so that one change of a file it merely contains is not a condition (issue #665). A
  routed section is a *slice* of a document, and a section that nobody edits scores zero however long it grows —
  the factor the row is built on removes exactly the signal wanted here. A different quantity, not a different
  bound.
- **Add the size check to `tools/check-doc-routes.sh`.** Impossible as a check: it is the route resolver run as a
  build step, it exits non-zero and files nothing, and it has no row, bound, identity or closure. A check is a
  declared row (`backlog-sensors`, "A check is a declared row, not a script"), and a resolver that also filed
  items would be the second thing in one step that `Backlog.md` §9 keeps apart.
- **One row carrying both bounds.** Impossible under the registry's own rule: a row has one `threshold` and the
  bound SHALL be the row's, so a metric applying a second, different bound from inside the script would put a
  bound where retuning it is no longer one edit. Two bounds are two rows; they share the measurement, which is
  what the `scope` field selects.
- **A ratio to the reading budget, or tokens.** Eliminated by the recorded decision (its options 3 and 4): a
  ratio moves with a token estimate that moves with prose, and a token count needs a tokenizer and is
  model-dependent — neither is reproducible in a scheduled job without a model.
- **Characters as the unit.** Eliminated by the recorded decision (its option 2, rejected): it is deterministic
  but is a second unit beside the bytes the requirement's ruler is stated in. The numbers the item quotes for
  `UI.md` §1 and `MapRendering.md` §1 (24 750 and 22 510) are character counts; as bytes they are 25 206 and
  22 644 today, and the byte count is what the row reports.
- **Bound only the sections.** Eliminated by the decision, which names the 32 000-byte bound for the entry
  document, and by the cost model: the entry document is the one every session reads before it reads anything
  else, so it is the first place the budget erodes.
- **Report the cost in lines instead of bytes.** Eliminated by the decision's unit, and unnecessary: the line
  count is reported beside the byte cost and the conversion is stated, so a reader who thinks in lines gets the
  number without the verdict moving off the measured bytes.
- **Read a published artifact instead of measuring the tree (an `input` row).** Impossible without a second
  producer: no pipeline publishes a document's size, and `tools/build-inputs.sh --check` refuses a row that
  declares an input no producer writes. A repository row needs no input, as `hotspot` and `source-size` show.

## Verification

- The red cases `sensors#routedSectionSizeRow`, `sensors#routedSectionSizeSkipsUnrouted` and
  `sensors#entryDocumentSizeRow` fail on HEAD (`tests=70 failures=3 errors=0`, `evidence/red-on-head.xml`) and pass
  after the change.
- They drive all four outcomes on both scopes: the violation measured in bytes (the fixture's lines carry a
  multibyte character, so a character count would fail the assertion), the section inside the bound, the section
  and the document the table does not route, the entry document under its own bound and not under the section
  row's, the clear of a recorded condition under the identity the issue holds, the row-level clear, and the
  `error` of a table that is not there.
- `bash tools/sensor-selftest.sh` — the sensor tooling's gate, 70 cases.
- `bash tools/sensor-mutations.sh --only 8.32` and `--only 8.33` — each bound inverted reddens its case, restore,
  green.
- `bash tools/check-portability.sh`, `bash tools/check-doc-routes.sh`,
  `bash tools/gen-feature-list.sh --check-classification`, `bash tools/check-openspec-hygiene.sh`.
- No Gradle gate: no compiled file is touched (`guidelines/Build.md` §2, scope block). The loop's own
  `./tools/loop/gate.sh` selects the suites the changed pathspec owes and reports their tallies.
