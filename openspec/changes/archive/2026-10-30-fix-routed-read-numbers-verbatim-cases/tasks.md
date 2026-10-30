# Tasks — fix-routed-read-numbers-verbatim-cases

The change touches no compiled file, so no build task exists here: the structural check that stands in is
`openspec validate --strict` over the change plus the documentation suites `tools/loop/gate.sh` selects for
`*.md`, `openspec/**` and `tools/check-doc-routes*` (`tools/loop/loop.conf`, `LOOP_GATE_SUITES`). The archive
comes before the gate because `openspec/changes/*/` is gitignored while a change is open, so the gate's pathspec
owes nothing until the change sits under `openspec/changes/archive/`.

## 1. Red case

- [x] 1.1 Write `evidence/red-probe.sh`, which reads the two scenario blocks of *The routing cost is measured*
      from `openspec/specs/documentation-ownership/spec.md`, requires `routed-read#numbers` to be declared and to
      pass in `tools/check-doc-routes-selftest.sh`, and requires the verbatim Case line to name no archived
      probe and to say no harness asserts it. Run it on HEAD. Verify: `tests=1 failures=1`, the case
      `routed-read#the-two-case-lines-resolve` naming the undeclared case, its failed run and the archived
      pointer (`evidence/red-on-head.xml`, `evidence/red-on-head.log`, 2026-10-30T07:22:14Z).
      *(spec: `documentation-ownership` — The routing cost is measured, scenarios "A split section keeps its
      number and its citations" and "The split moves text instead of rewriting it")*

## 2. The case and its falsification

- [x] 2.1 Add `case_routed_read_numbers` and its `echo_case routed-read#numbers` registration to
      `tools/check-doc-routes-selftest.sh`: a fixture keeps `## 1.` beside `## 1a.` and `## 1b.`, its body cites
      all three, the routing table routes the two sub-sections, and the route check accepts the set; removing the
      parent heading makes the `§1` citation fail, and removing a sub-section heading makes its citation fail.
      Update the suite's header comments to name the third routed-read case. Verify:
      `bash tools/check-doc-routes-selftest.sh --case routed-read#numbers` prints `PASS  routed-read#numbers`,
      and the whole suite prints `all 22 cases passed`.
      *(spec: `documentation-ownership` — The routing cost is measured, scenario "A split section keeps its
      number and its citations")*
- [x] 2.2 Revert-check: add one mutation record to `tools/check-doc-routes-mutations.json` — label
      `guideline-body-reference`, case `routed-read#numbers`, the body-extraction line in
      `tools/check-doc-routes.sh` replaced by a no-op. Run
      `bash tools/mutate.sh --mutations tools/check-doc-routes-mutations.json --only guideline-body-reference`.
      Verify: `RED(exit 1)` for `routed-read#numbers` under the mutation, `GREEN(exit 0)` after the restore, and
      `0 problem(s)` (`evidence/revert-check.log`).
      *(spec: `documentation-ownership` — The routing cost is measured; the case must be capable of failing)*

## 3. The two Case lines

- [x] 3.1 Restate the two scenarios in the delta `specs/documentation-ownership/spec.md`: `#numbers` names the
      fixture case and its three clauses; `#verbatim` says the split's diff is a one-off judgement recorded by the
      change that made it, that no harness in the tree can assert it over a later tree, and names no archived
      file. Verify: the delta's requirement block carries the requirement whole — the description and all seven
      other scenarios byte-identical to `openspec/specs/documentation-ownership/spec.md:117`–`:198`, with only
      the two Case lines changed (`diff -u` of the two requirement blocks shows two hunks).
      *(spec: `documentation-ownership` — The routing cost is measured; A modified requirement carries the
      scenarios its spec still has)*

## 4. Structural verification and archive

- [x] 4.1 Validate the change: `openspec validate fix-routed-read-numbers-verbatim-cases --strict`. Verify:
      exit 0 and `evidence/openspec-validate.log` carries
      `Change 'fix-routed-read-numbers-verbatim-cases' is valid`.
- [x] 4.2 Archive the change: `openspec archive fix-routed-read-numbers-verbatim-cases --yes`. Verify: the
      directory exists under `openspec/changes/archive/` as
      `2026-10-30-fix-routed-read-numbers-verbatim-cases`, the delta has merged into
      `openspec/specs/documentation-ownership/spec.md` with the two restated Case lines, and the probe reports
      `tests=1 failures=0` over the merged spec (`evidence/green-after-archive.xml`).
- [x] 4.3 Run the project's gate once, `./tools/loop/gate.sh`, from the change root after the archive. Verify:
      `evidence/gate.log` ends in `LOOP-GATE: tests=28 failures=0 errors=0` over the seven documentation suites
      the pathspec owes, the per-suite tallies are in `evidence/gate-tallies/` and their sum in
      `evidence/gate-summary.xml`, and no step reports a failure.
      *(spec: `documentation-ownership` — A route resolves to a section that exists)*

## 5. Evidence

- [x] 5.1 Copy every cited XML into the change's `evidence/` directory and into
      `/home/tim/projects/NaviVeylin/.pi/issue-loop/items/710`, then run
      `bash tools/loop/evidence-check.sh openspec/changes/archive/2026-10-30-fix-routed-read-numbers-verbatim-cases --root .`.
      Verify: `evidence-check: PASS` with every `file:line` and every quoted test tally resolving
      (`evidence/evidence-check.log`).
- [x] 5.2 Leave the tree as the change leaves it: no commit, no push, no version control, no tracker write — the
      run's script owns those.
