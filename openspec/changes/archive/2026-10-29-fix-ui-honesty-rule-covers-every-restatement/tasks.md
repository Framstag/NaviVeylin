# Tasks — fix-ui-honesty-rule-covers-every-restatement

The change touches no compiled file, so no Gradle task exists here: the structural check that stands in is
`bash tools/check-doc-routes.sh` plus the documentation suites `tools/loop/gate.sh` selects for `*.md` and
`openspec/**` (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`).

## 1. Red case

- [x] 1.1 Write `evidence/red-probe.sh` with its three cases over `guidelines/UI.md` "Keeping this document
      honest": the section states the restatement obligation and declares every case the document's body
      names (`ui-honesty#a-restatement-names-its-guard`), every declared guard resolves to a test source
      (`ui-honesty#a-listed-guard-is-a-case-in-the-tree`), and every declared section resolves by number and
      heading text (`ui-honesty#a-listed-section-resolves`). Run it on HEAD. Verify: `tests="3"
      failures="1"`, the failing case naming the two missing obligation tokens and the ten undeclared guards
      (`evidence/red-on-head.xml`, `evidence/red-on-head.log`, `2026-10-29T11:00:30Z`).
      *(spec: `documentation-ownership` — A rule has one normative home; docs-only change declares
      `skip_specs: true`)*

## 2. The honesty rule

- [x] 2.1 Widen the second bullet of `guidelines/UI.md` "Keeping this document honest": the obligation now
      covers every value a spec requirement or a code constant owns — a label, a style, a hierarchy or a
      number — and every restatement the next bullet lists, not the two tables alone; define a restatement as
      a section that writes down a value another artefact owns, that artefact staying the normative home.
      Verify: `sed -n '1383,1392p' guidelines/UI.md` shows the widened bullet and the definition, and the
      probe's first case no longer reports the missing `[restates]` token.
      *(spec: `documentation-ownership` — A rule has one normative home)*
- [x] 2.2 Add the third bullet: a section that restates such a value SHALL name the case that holds the two
      together, or the requirement that owns it, and a restatement with no guard is a defect fixed or removed
      in the change that finds it — with the §187 instance cited as the reason. Then declare the six
      restatement sections and the ten cases, each section cited by number **and** heading text (sections 1,
      7a, 8, 8a, 10, 11). Verify: `bash openspec/changes/fix-ui-honesty-rule-covers-every-restatement/evidence/red-probe.sh`
      reports `PASS` for all three cases (`evidence/green-after-fix.xml`, `evidence/green-after-fix.log`,
      `2026-10-29T11:00:31Z`), and `bash tools/check-doc-routes.sh` exits 0 with
      `all 155 section references resolve`.
      *(spec: `documentation-ownership` — A rule has one normative home)*
- [x] 2.3 Check the restatement against the rule it now states: every case the list names is a shipped
      `*Test` in the tree and every section it names is a `## ` heading of the document. Verify: the probe's
      second and third cases pass, and the list adds no guard the tree does not carry.
      *(spec: `documentation-ownership` — A route resolves to a section that exists)*

## 3. Structural verification

- [x] 3.1 Run every suite the reviewed pathspec owes once through the project's gate, `./tools/loop/gate.sh`,
      from the change root. Verify: its summary line is quoted in `evidence/gate.log`, every owed suite
      passes, and the JUnit tallies are copied into `evidence/gate-tallies/`; no Gradle task is owed.
      *(the change touches no compiled file — the structural check of §2 stands in for the build)*
- [x] 3.2 Validate the change: `openspec validate fix-ui-honesty-rule-covers-every-restatement --strict`.
      Verify: the command exits 0 and reports the change valid; record its output in
      `evidence/openspec-validate.log`.
- [x] 3.3 Re-run the probe after the edit and copy its XML into `evidence/green-after-fix.xml`; confirm
      `tests="3" failures="0"` and a timestamp after the edit. Verify: the probe's three `PASS` lines are in
      `evidence/green-after-fix.log`.

## 4. Evidence and archive

- [x] 4.1 Copy every cited XML into the change's `evidence/` directory and the item's evidence directory
      `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/134`, then run
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-29-fix-ui-honesty-rule-covers-every-restatement --root .`
      after the archive (`.log` = pre-archive run, `-archived.log` = post-archive run). Verify: every
      `file:line` and every quoted tally resolves; record the PASS line in
      `evidence/evidence-check-archived.log`.
- [x] 4.2 Archive the change, confirm the directory exists under `openspec/changes/archive/`, and confirm no
      capability spec changed (`.openspec.yaml` declares `skip_specs: true`). Verify: the archive directory
      carries `proposal.md`, `design.md`, `tasks.md`, `.openspec.yaml` and `evidence/`, and
      `openspec/changes/fix-ui-honesty-rule-covers-every-restatement` no longer exists.
- [x] 4.3 Leave the tree as the change leaves it: no commit, no push, no version control, no tracker write —
      the run's script owns those.
