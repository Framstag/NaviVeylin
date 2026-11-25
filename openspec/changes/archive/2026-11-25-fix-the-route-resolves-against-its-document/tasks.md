# Tasks — a route resolves against the section its own document carries

- [x] 1. The red case first, taken on the base engine as it stands: a probe change whose markdown carries
      `LOOP-ROUTE: guidelines/Backlog.md §7` — an id the probe's stub document does not carry — is read `evidence-check: PASS`
      (exit 0) by the tool committed at `fa8c6aa`, and the same fixture against this change's engine exits 1 and names
      the sections the document does carry. The base's self-test prints **13** `ok` arms and none of them is about a
      route. Evidence: `evidence/red-on-base-probe.txt`, `evidence/red-on-HEAD-selftest.txt`.

- [x] 2. C6 in `run_checks` (`tools/loop/evidence-check.sh:185`): the anchored route lines of the change's markdown,
      each line's document under `LOOP_GUIDELINE_DIR` and existing, and its `§<id>` carried by one of that document's
      own `^## ` headings. Verified against a real document, not a fixture: `LOOP-ROUTE: guidelines/Backlog.md §99`
      is refused in one line that lists the sections the document does carry (`1 2 3 … 7c … 14`), and the same check reads the change's own
      route by section 7c as resolved. Evidence: `evidence/false-route-refused.log`.

- [x] 3. C1 learns the route is not a migration reference (`tools/loop/evidence-check.sh:70`): a line carrying a route
      is skipped by the table check, because its id names a section of a guideline document and not a row of the
      table. Verified by the selftest's route arms, whose fixtures carry an id `99` that no table row names and are not
      and are not refused by C1. Evidence: `evidence/selftest-after.log`.

- [x] 4. The two configuration keys (`tools/loop/loop.conf:992`): `LOOP_GUIDELINE_DIR="guidelines"` and
      `LOOP_EVIDENCE_ROUTE="warn"`, the mode the first run after the rule landed reads. Verified by the two mode arms
      — one report with no route line is a `WARN` and exit 0 under `warn`, and exit 1 under `fail`, so the
      configuration and not a second rule in the tool is what decides. Evidence: `evidence/selftest-after.log`.

- [x] 5. Ten `--selftest` arms: eight named by a `- **Case**` of this delta — `a route resolves against the document's
      own heading`, `a § the document does not have is a false pointer`, `the refusal names the document's section
      list`, `a dotted sub-number heading carries no id`, `a heading with neither shape separator carries no id`,
      `a report with no route line warns rather than failing`, `and the same report fails once the project reads the
      route as required`, `a paragraph quoting the form is not a route line` — and two assertions inside those arm
      groups that no case names: `the missing route is a WARN` and `the dotted sub-number is not in the document's
      section list`. The suite prints **23** `ok` arms against the base's 13 and `evidence-selftest: PASS`. Evidence:
      `evidence/selftest-after.log`.

- [x] 6. The bound row (`tools/loop/layout.tsv:22`) moves from 368 to 517 — the file's measured 413 lines plus a
      quarter, rounded up, which is what the table's own rule asks of a row whose file grew. Verified by the case that
      owns the table: `PASS driver#noEngineFileExceedsItsBound`. Evidence: `evidence/layout-case.log`.

- [x] 7. The landing recipe states the obligation the check enforces (`guidelines/Backlog.recipes.md:1160`, section 7c): one
      `LOOP-ROUTE` line a change names its section with, resolved against the document's own headings, a section the
      document does not have a FAIL, a missing line a WARN until `LOOP_EVIDENCE_ROUTE` reads it as `fail`.

- [x] 8. Revert-check, one mutation: `|| true` added to C6's guideline-directory guard
      (`tools/loop/evidence-check.sh:194`) so the check can never resolve a route. The suite reddens on the named arms
      — `a § the document does not have is a false pointer`, `the refusal names the document's section list`, `a dotted
      sub-number heading carries no id`, `the dotted sub-number is not in the document's section list`, `a heading with
      neither shape separator carries no id`, `the missing route is a WARN` and `and the same report fails once the
      project reads the route as required` — the file is restored **byte for byte**
      (`sha256 176413030dd0…`) and the same suite is green again. Evidence: `evidence/revert-check-c6-red.log`,
      `evidence/revert-check-c6-green.log`.

- [x] 9. Structural checks on the change itself, run before the gate: from the change root,
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-11-25-fix-the-route-resolves-against-its-document
      --root .` reads the change's own route as resolved — `1 of 1 route line(s) resolved` — and passes every check,
      so the change holds itself to the rule it adds. The delta it carries is the requirement
      `openspec validate agent-issue-loop --type spec --strict` reads with no error (only the tree's
      long-requirement warnings). Evidence: `evidence/evidence-check.log`.

- [x] 10. Revert-check of the id rule, one mutation (round 2, the reviewer's finding): the id rule of
      `tools/loop/evidence-check.sh:218` mutated back to the form shipped in round 1, so a dotted sub-number
      (`## 3.6 …`) carries the id `3` and a heading with neither shape separator (`## 9 Nine`) carries the id `9`.
      The suite reddens on the named arms — `a dotted sub-number heading carries no id`, `the dotted sub-number is not
      in the document's section list` and `a heading with neither shape separator carries no id` — the file is restored
      **byte for byte** (`sha256 176413030dd0…`) and the same suite is green again. Evidence:
      `evidence/revert-check-c6-shapes-red.log`, `evidence/revert-check-c6-shapes-green.log`.
