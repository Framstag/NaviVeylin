# Tasks — fix-unbounded-agent-context

## 1. The red cases

- [x] 1.1 Add `sensors#routedSectionSizeRow`, `sensors#routedSectionSizeSkipsUnrouted` and
  `sensors#entryDocumentSizeRow` to `tools/sensor-cases.sh` (array and dispatcher). The first reads the section
  row's bound, comparison, fingerprint, class, scope and the entry document it derives the pairs from, then
  drives the real script over a fixture tree: a routed section past the bound is a violation whose measurement is
  the section's own `wc -c` byte count (its lines carry an em dash, so a character count would fail), a routed
  section inside the bound is no condition, a recorded open condition now inside the bound clears under its
  identity, a table inside the bound prints the row-level clear, and an entry document with no routing table is
  the third outcome. The second asserts that a section, and a document, the table does not route is never a
  condition while the routed section beside them still is. The third does the same for the entry document under
  its own 32 000-byte bound, and that a routed section is not its condition. Verify: on HEAD the suite reports
  `sensor-selftest: 3 of 70 cases failed` with the three messages "the registry declares no … row"
  (`evidence/red-suite-on-head.log`, tally `evidence/red-on-head.xml`, `tests=70 failures=3 errors=0`).

## 2. The measurement and the rows

- [x] 2.1 Write `tools/sensors/metrics/doc-context.sh`: the routing table read from the entry document's
  `## Documentation Map`, the `(document, section)` pairs derived from it, the documents taken from
  `guidelines/*.md` at run time, each named section measured as the byte count of its lines from the `## ` heading
  through the line before the next one (`LC_ALL=C awk`, so the count is bytes and not characters), the line count
  carried beside it, the quoted heading text used to disambiguate two alike-numbered headings, a pair the
  document does not carry left unmeasured, one violation per section past `SENSORS_THRESHOLD`, one clear per
  recorded open condition now inside the bound (or `outside-scope` when the table no longer routes it), one
  row-level clear when it printed nothing, and one `error` finding when the entry document or its table cannot be
  read. Verify: the three cases of task 1.1 pass, and `bash -n` parses the script.
- [x] 2.2 Declare the rows in `tools/sensors/sensors.json` — `routed-section-size` (`threshold 12000`, `scope
  routed-section`, `entry AGENTS.md`, fingerprint `["document","section"]`, `cron "0 11 * * *"`) and
  `entry-document-size` (`threshold 32000`, `scope entry-document`, `entry AGENTS.md`, fingerprint `["path"]`,
  `cron "30 11 * * *"`), both `comparison greater-than`, `consecutive 2`, `class improvement`, `category
  specs-and-process`, `lifecycle file+close`. Verify: case `Registry#everyRowIsComplete` passes, both minutes are
  the pipeline tick's own (0 and 30), and `bash tools/build-inputs.sh --check` still accepts a registry whose rows
  declare no unpublished input.
- [x] 2.3 Add both row ids to the second row step's `--rows` list in `.github/workflows/sensors.yml` and to that
  step's comment. Verify: case `workflow#aRedRecordRunsNoRecordRow` passes, which asserts the two lists together
  are exactly the registry's ids, selected once each.

## 3. The doctrine and the spec

- [x] 3.1 Add the two rows' paragraph to `guidelines/Backlog.md` §9: what they measure, the byte unit and the
  ruler, the line count and the 80-byte conversion beside it, the exclusion of what the table does not route, the
  identity, the two bounds as the owner's recorded decision, and the eight sections the bound convicts on this
  tree. Verify: `bash tools/check-doc-routes.sh` resolves every section reference of the edited document.
- [x] 3.2 Modify the requirement "The routing cost is measured" in `documentation-ownership` — the bound covers a
  section of any guideline the table routes and the entry document, the clause deferring the other guidelines to
  issue #197 is removed, and three scenarios are added, each naming one of the three cases. Verify:
  `openspec validate 2026-10-29-fix-unbounded-agent-context --type change` reports the change valid, and
  `bash tools/check-openspec-hygiene.sh` passes (the MODIFIED block carries every scenario the shipped
  requirement still has).

## 4. Falsification

- [x] 4.1 Add mutation 8.32 to `tools/sensor-mutations.sh` (the section comparison inverted) and 8.33 (the entry
  document's comparison inverted). Verify: `bash tools/sensor-mutations.sh --only 8.32` and `--only 8.33` each
  report the named case red under the mutation and green after the restore.

## 5. The gate and the evidence

- [x] 5.1 Run the gate the changed pathspec asks for and copy its result into this change's `evidence/`:
  `./tools/loop/gate.sh` — no compiled path, so the build's own gate is not owed (`guidelines/Build.md` §2, scope
  block) and its suites are the sensor tooling's, the route check's, the portability check's, the feature list's
  and the openspec hygiene's. Verify: every tally it writes is green and each is copied beside the log.
- [x] 5.2 Measure today's tree with the shipping script and keep the command beside the output
  (`evidence/measure-the-tree.sh`, `evidence/measure-the-tree.txt`): the eight section conditions and the entry
  document's clear are the numbers the proposal and §9 state. Verify: `bash
  tools/loop/evidence-check.sh openspec/changes/2026-10-29-fix-unbounded-agent-context --root .` resolves every
  pointer this change writes.

## 6. After the merge

- [ ] 6.1 Owed after the merge, by the owner: the first scheduled run files the eight section conditions, and
  `Backlog.md` §9 is one of them — the split that section needs is a change of its own. Not runnable here: the
  pipeline's guard refuses every ref but `main`, so a dispatch from this worktree writes nothing by design.
